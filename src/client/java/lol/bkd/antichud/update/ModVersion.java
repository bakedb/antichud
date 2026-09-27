package lol.bkd.antichud.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small dependency free version type used to decide whether the installed antichud build is
 * older than the latest GitHub release.
 *
 * <p>It understands the shapes this mod realistically produces ({@code 0.1}, {@code v1.2.3},
 * {@code 1.0-SNAPSHOT}, {@code 0.2-beta3+build.7}) and orders them the same way the build
 * tool does: numeric components first, a final release always outranks a pre-release of the
 * same number, and build metadata is ignored.
 */
public record ModVersion(String raw, List<Integer> components, String qualifier) implements Comparable<ModVersion> {
    /** Leading numeric part (dotted) followed by everything else. */
    private static final Pattern PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)*)(.*)", Pattern.DOTALL);
    private static final Map<String, Integer> QUALIFIER_RANKS = Map.ofEntries(
            Map.entry("dev", 0),
            Map.entry("snapshot", 10),
            Map.entry("alpha", 20),
            Map.entry("a", 20),
            Map.entry("beta", 30),
            Map.entry("b", 30),
            Map.entry("milestone", 40),
            Map.entry("m", 40),
            Map.entry("rc", 50),
            Map.entry("cr", 50),
            Map.entry("final", 100),
            Map.entry("ga", 100),
            Map.entry("release", 100),
            Map.entry("stable", 100)
    );
    private static final int RELEASE_RANK = 100;
    private static final int UNKNOWN_RANK = 10;

    public ModVersion {
        components = List.copyOf(components);
    }

    /** Parses a version string, never throwing: unparsable input degrades to {@code 0}. */
    public static ModVersion parse(String raw) {
        String value = raw == null ? "" : raw.trim();

        int build = value.indexOf('+');
        if (build >= 0) {
            value = value.substring(0, build);
        }
        if (!value.isEmpty() && (value.charAt(0) == 'v' || value.charAt(0) == 'V')) {
            value = value.substring(1);
        }

        List<Integer> components = new ArrayList<>();
        String qualifier;
        Matcher matcher = PATTERN.matcher(value);
        if (matcher.matches()) {
            for (String part : matcher.group(1).split("\\.")) {
                components.add(parseComponent(part));
            }
            qualifier = stripSeparators(matcher.group(2));
        } else {
            qualifier = stripSeparators(value);
        }

        // 0.1 and 0.1.0 are the same release.
        while (components.size() > 1 && components.get(components.size() - 1) == 0) {
            components.remove(components.size() - 1);
        }
        if (components.isEmpty()) {
            components.add(0);
        }

        return new ModVersion(raw == null ? "" : raw, components, qualifier);
    }

    /** Compares two version strings, returning a negative value when {@code left} is older. */
    public static int compare(String left, String right) {
        return parse(left).compareTo(parse(right));
    }

    @Override
    public int compareTo(ModVersion other) {
        int length = Math.max(components.size(), other.components.size());
        for (int i = 0; i < length; i++) {
            int mine = i < components.size() ? components.get(i) : 0;
            int theirs = i < other.components.size() ? other.components.get(i) : 0;
            if (mine != theirs) {
                return Integer.compare(mine, theirs);
            }
        }

        int rank = rank(qualifier);
        int otherRank = rank(other.qualifier);
        if (rank != otherRank) {
            return Integer.compare(rank, otherRank);
        }
        return qualifier.compareToIgnoreCase(other.qualifier);
    }

    @Override
    public String toString() {
        return raw;
    }

    private static int rank(String qualifier) {
        if (qualifier.isEmpty()) {
            return RELEASE_RANK;
        }
        Integer known = QUALIFIER_RANKS.get(qualifier.toLowerCase(Locale.ROOT));
        return known != null ? known : UNKNOWN_RANK;
    }

    private static int parseComponent(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException tooLongForAnInt) {
            return 0;
        }
    }

    private static String stripSeparators(String value) {
        int start = 0;
        while (start < value.length()) {
            char c = value.charAt(start);
            if (c != '-' && c != '_' && c != '.' && !Character.isWhitespace(c)) {
                break;
            }
            start++;
        }
        return value.substring(start).trim();
    }
}
