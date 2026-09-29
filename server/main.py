import hashlib
import json
import logging
import mimetypes
import os
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

logging.basicConfig(filename="antichud.log", level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")

# Backgrounds live here and are served as-is. Dropping a file in is the whole deploy process:
# the manifest below is generated from the directory listing, so adding or replacing an image
# never needs a mod release.
#
# Overridable with ANTICHUD_ASSET_DIR so a deployment can keep the images wherever it already
# puts them. The default is assets/startup next to this file, which is the layout in the repo.
ASSET_DIR = os.environ.get("ANTICHUD_ASSET_DIR") or os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "assets", "startup"
)

# Filenames are only ever these characters. Anything else is rejected rather than sanitised, so a
# request can never escape ASSET_DIR via "..", a slash, or a NUL byte.
SAFE_NAME = re.compile(r"^[A-Za-z0-9._-]+$")

# How long a client may cache a response without asking. "no-cache" does not mean "do not store",
# it means "revalidate before using" - the client keeps a copy and sends If-None-Match, so an
# unchanged asset costs a 304 with no body instead of another few hundred kilobytes.
CACHE_CONTROL = "no-cache"


def warn_if_misconfigured():
    """Complain loudly when the asset directory is missing or holds nothing servable.

    An empty directory is the single most likely way for this server to be wrong - a wrong
    ASSET_DIR, a deploy that copied main.py but not the images, a service running from the wrong
    working directory. Left silent it produces a perfectly valid empty manifest, every client
    concludes the player has no background, and the symptom is "the mod still looks the way it
    always did" with nothing in any log to explain it. So this is said at startup and repeated
    whenever the manifest is requested, where whoever is debugging will actually see it.
    """
    if not os.path.isdir(ASSET_DIR):
        logging.warning(
            "Startup asset directory does not exist: %s - serving no backgrounds. "
            "Set ANTICHUD_ASSET_DIR or create it.",
            ASSET_DIR,
        )
        return False
    if not asset_files():
        logging.warning(
            "Startup asset directory is empty: %s - serving no backgrounds.", ASSET_DIR
        )
        return False
    return True


def asset_files():
    """Every servable image, as (filename, path) sorted by name so the manifest is stable."""
    if not os.path.isdir(ASSET_DIR):
        return []
    found = []
    for name in sorted(os.listdir(ASSET_DIR)):
        path = os.path.join(ASSET_DIR, name)
        if os.path.isfile(path) and SAFE_NAME.match(name) and not name.startswith("."):
            found.append((name, path))
    return found


def file_etag(path):
    """Strong-ish validator from mtime and size.

    Content hashing would be more correct, but these are 300-700KB images edited a handful of
    times; stat is instant and an mtime bump on save is exactly the event that should invalidate.
    """
    stat = os.stat(path)
    return '"%x-%x"' % (stat.st_mtime_ns, stat.st_size)


def build_manifest():
    """Maps a lowercased player name to the file that serves it.

    Keyed by the basename without its extension so the client can ask for "daltzed" without
    knowing whether the file is a .png or a .jpg, and so a player who has both a .png and a .jpg
    cannot be ambiguous.
    """
    assets = {}
    for name, path in asset_files():
        key = os.path.splitext(name)[0].lower()
        assets[key] = {"file": name, "etag": file_etag(path)}
    return {"version": 1, "assets": assets}


def manifest_etag():
    """Validator covering the listing and every file's mtime/size, so any edit changes it."""
    digest = hashlib.sha256()
    for name, path in asset_files():
        stat = os.stat(path)
        digest.update(("%s|%d|%d\n" % (name, stat.st_mtime_ns, stat.st_size)).encode("utf-8"))
    return '"%s"' % digest.hexdigest()[:32]


class AntichudHandler(BaseHTTPRequestHandler):
    # HTTP/1.1 keeps the connection open so the manifest request and the image request behind it
    # reuse one TLS handshake. It also means every response must carry an accurate
    # Content-Length, including the error ones below, or the client waits for a body that never
    # comes and the connection stalls.
    protocol_version = "HTTP/1.1"
    server_version = "antichud"
    sys_version = ""

    def log_message(self, fmt, *args):
        """Route access logs into antichud.log instead of stderr.

        Asset requests are the only GETs, and every player makes one per launch, so at the default
        BaseHTTPRequestHandler verbosity they would bury the ban and tamper reports that this log
        actually exists for. They go to DEBUG, which the handler above never emits.
        """
        logging.info("HTTP %s - %s", self.address_string(), fmt % args)

    def _send(self, status, body=b"", content_type="application/json", extra=None):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", CACHE_CONTROL)
        for key, value in (extra or {}).items():
            self.send_header(key, value)
        self.end_headers()
        if body:
            self.wfile.write(body)

    def _error(self, status, message):
        logging.info("Asset request %s -> %d %s", self.path, status, message)
        self._send(status, json.dumps({"error": message}).encode("utf-8"))

    def _not_modified(self, etag, last_modified):
        """304 when the client's cached copy is still current. No body, no Content-Length."""
        self.send_response(304)
        self.send_header("ETag", etag)
        if last_modified:
            self.send_header("Last-Modified", last_modified)
        self.send_header("Cache-Control", CACHE_CONTROL)
        self.end_headers()

    def _matches(self, etag, last_modified):
        """Honour both validators, preferring ETag as RFC 9110 requires."""
        if_none_match = self.headers.get("If-None-Match")
        if if_none_match:
            candidates = [tag.strip() for tag in if_none_match.split(",")]
            return etag in candidates or "*" in candidates
        if_modified_since = self.headers.get("If-Modified-Since")
        if if_modified_since:
            return last_modified == if_modified_since.strip()
        return False

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == "/assets/startup/manifest.json":
            self.serve_manifest()
        elif path.startswith("/assets/startup/"):
            self.serve_asset(path[len("/assets/startup/"):])
        else:
            self._error(404, "not found")

    def serve_manifest(self):
        # Checked on every request, not just at startup, because a directory that is empty now
        # may have been empty all along and the startup line is long gone from a terminal.
        warn_if_misconfigured()
        etag = manifest_etag()
        if self._matches(etag, None):
            # The common case: nothing has been added, removed or touched since the last
            # launch, so this is the whole cost of keeping the backgrounds current - a 304 with
            # no body, and no image request behind it.
            logging.debug("Manifest not modified (304)")
            self._not_modified(etag, None)
            return

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("ETag", etag)
        self.send_header("Cache-Control", CACHE_CONTROL)
        manifest = build_manifest()
        body = json.dumps(manifest, sort_keys=True).encode("utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        logging.debug("Manifest served, %d assets", len(manifest["assets"]))

    def serve_asset(self, name):
        # basename() drops any directory component, and the allowlist rejects everything that
        # could still be an escape. The resolved-path check is the belt to that braces.
        name = os.path.basename(name)
        if not name or not SAFE_NAME.match(name):
            self._error(400, "bad asset name")
            return

        path = os.path.join(ASSET_DIR, name)
        if os.path.realpath(os.path.dirname(path)) != os.path.realpath(ASSET_DIR):
            self._error(400, "bad asset name")
            return
        if not os.path.isfile(path):
            self._error(404, "no such asset")
            return

        stat = os.stat(path)
        etag = file_etag(path)
        last_modified = time.strftime("%a, %d %b %Y %H:%M:%S GMT", time.gmtime(stat.st_mtime))
        if self._matches(etag, last_modified):
            logging.debug("Asset %s not modified (304)", name)
            self._not_modified(etag, last_modified)
            return

        content_type = mimetypes.guess_type(name)[0] or "application/octet-stream"
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(stat.st_size))
        self.send_header("ETag", etag)
        self.send_header("Last-Modified", last_modified)
        self.send_header("Cache-Control", CACHE_CONTROL)
        self.end_headers()
        with open(path, "rb") as handle:
            self.wfile.write(handle.read())

    def do_POST(self):
        if self.path == "/log":
            content_length = int(self.headers["Content-Length"])
            post_data = self.rfile.read(content_length)

            try:
                # Parse and validate JSON
                payload = json.loads(post_data.decode("utf-8"))

                # Format check
                if "uuid" in payload and "username" in payload and "event_type" in payload:
                    logging.info(f"Received payload: {payload}")

                    self.send_response(200)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Content-Length", "20")
                    self.end_headers()
                    self.wfile.write(b'{"status": "logged"}')
                    return
            except json.JSONDecodeError:
                pass

        # Bad Request / Not Found
        self.send_response(400)
        self.send_header("Content-Length", "0")
        self.end_headers()


if __name__ == "__main__":
    # ThreadingHTTPServer rather than HTTPServer: a single player pulling a 700KB background would
    # otherwise block every other player's ban report for the duration of the download.
    server = ThreadingHTTPServer(("0.0.0.0", 8642), AntichudHandler)
    server.daemon_threads = True
    count = len(asset_files())
    if count:
        print(f"Server listening on port 8642, serving {count} startup backgrounds from {ASSET_DIR}")
    else:
        # Printed to stdout, not just the log, because a service that starts fine and then serves
        # nothing is invisible from the outside.
        print(f"WARNING: no startup backgrounds found in {ASSET_DIR} - every player will see the "
              f"fallback colour. Check the path or set ANTICHUD_ASSET_DIR.")
    warn_if_misconfigured()
    server.serve_forever()
