import json, logging
from http.server import BaseHTTPRequestHandler, HTTPServer

logging.basicConfig(filename="antichud.log", level=logging.INFO)

class LogHandler(BaseHTTPRequestHandler):
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
                    self.end_headers()
                    self.wfile.write(b'{"status": "logged"}')
                    return
            except json.JSONDecodeError:
                pass
        
        # Bad Request / Not Found
        self.send_response(400)
        self.end_headers()

server = HTTPServer(("0.0.0.0", 8642), LogHandler)
print("Server listening on port 8642...")
server.serve_forever()