package io.github.rkaushikdev.jeepdolly;

/**
 * How the office writes back what somebody wrote: quoted in a refusal, checked for being a number,
 * or capitalised as a place name.
 *
 * <p>A refusal letter says {@code cannot read 'about fourteen feet' as a size}, with the hauler's
 * own words in quotes, so the hauler can see exactly what the office was handed. The quoting is
 * the one the office's letters have always used: single quotes, unless the text holds a single
 * quote and no double one, with control characters spelled out so a stray tab is visible.
 */
public final class Written {

    private Written() {}

    /** The text in quotes, exactly as a refusal letter writes what it was handed. */
    public static String quoted(String text) {
        char quote = text.indexOf('\'') >= 0 && text.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder out = new StringBuilder(text.length() + 2).append(quote);
        for (int index = 0; index < text.length(); index++) {
            char one = text.charAt(index);
            switch (one) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (one == quote) {
                        out.append('\\').append(one);
                    } else if (one < 0x20 || one == 0x7f) {
                        out.append(String.format("\\x%02x", (int) one));
                    } else {
                        out.append(one);
                    }
                }
            }
        }
        return out.append(quote).toString();
    }

    /** Whether the text is one or more digits and nothing else, not even a sign or a space. */
    public static boolean isDigits(String text) {
        return !text.isEmpty() && text.chars().allMatch(one -> one >= '0' && one <= '9');
    }

    /**
     * A place name capitalised the way a road sign prints it: the first letter of every word up,
     * the rest down, where a word is a run of letters. {@code o'brien} reads {@code O'Brien}.
     */
    public static String title(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inWord = false;
        for (int index = 0; index < text.length(); index++) {
            char one = text.charAt(index);
            boolean cased = Character.isUpperCase(one) || Character.isLowerCase(one) || Character.isTitleCase(one);
            out.append(cased && !inWord ? Character.toTitleCase(one) : Character.toLowerCase(one));
            inWord = cased;
        }
        return out.toString();
    }
}
