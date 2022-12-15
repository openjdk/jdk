package jdk.internal.org.commonmark.internal.util;

public class Parsing {

    private static final String TAGNAME = "[A-Za-z][A-Za-z0-9-]*";
    private static final String ATTRIBUTENAME = "[a-zA-Z_:][a-zA-Z0-9:._-]*";
    private static final String UNQUOTEDVALUE = "[^\"'=<>`\\x00-\\x20]+";
    private static final String SINGLEQUOTEDVALUE = "'[^']*'";
    private static final String DOUBLEQUOTEDVALUE = "\"[^\"]*\"";
    private static final String ATTRIBUTEVALUE = "(?:" + UNQUOTEDVALUE + "|" + SINGLEQUOTEDVALUE
            + "|" + DOUBLEQUOTEDVALUE + ")";
    private static final String ATTRIBUTEVALUESPEC = "(?:" + "\\s*=" + "\\s*" + ATTRIBUTEVALUE
            + ")";
    private static final String ATTRIBUTE = "(?:" + "\\s+" + ATTRIBUTENAME + ATTRIBUTEVALUESPEC
            + "?)";

    public static final String OPENTAG = "<" + TAGNAME + ATTRIBUTE + "*" + "\\s*/?>";
    public static final String CLOSETAG = "</" + TAGNAME + "\\s*[>]";

    public static int CODE_BLOCK_INDENT = 4;

    public static int columnsToNextTabStop(int column) {
        // Tab stop is 4
        return 4 - (column % 4);
    }

    public static int find(char c, CharSequence s, int startIndex) {
        int length = s.length();
        for (int i = startIndex; i < length; i++) {
            if (s.charAt(i) == c) {
                return i;
            }
        }
        return -1;
    }

    public static int findLineBreak(CharSequence s, int startIndex) {
        int length = s.length();
        for (int i = startIndex; i < length; i++) {
            switch (s.charAt(i)) {
                case '\n':
                case '\r':
                    return i;
            }
        }
        return -1;
    }

    public static boolean isBlank(CharSequence s) {
        return findNonSpace(s, 0) == -1;
    }

    public static boolean hasNonSpace(CharSequence s) {
        int length = s.length();
        int skipped = skip(' ', s, 0, length);
        return skipped != length;
    }

    public static boolean isLetter(CharSequence s, int index) {
        int codePoint = Character.codePointAt(s, index);
        return Character.isLetter(codePoint);
    }

    public static boolean isSpaceOrTab(CharSequence s, int index) {
        if (index < s.length()) {
            switch (s.charAt(index)) {
                case ' ':
                case '\t':
                    return true;
            }
        }
        return false;
    }

    public static boolean isEscapable(char c) {
        switch (c) {
            case '!':
            case '"':
            case '#':
            case '$':
            case '%':
            case '&':
            case '\'':
            case '(':
            case ')':
            case '*':
            case '+':
            case ',':
            case '-':
            case '.':
            case '/':
            case ':':
            case ';':
            case '<':
            case '=':
            case '>':
            case '?':
            case '@':
            case '[':
            case '\\':
            case ']':
            case '^':
            case '_':
            case '`':
            case '{':
            case '|':
            case '}':
            case '~':
                return true;
        }
        return false;
    }

    // See https://spec.commonmark.org/0.29/#punctuation-character
    public static boolean isPunctuationCodePoint(int codePoint) {
        switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION:
            case Character.DASH_PUNCTUATION:
            case Character.END_PUNCTUATION:
            case Character.FINAL_QUOTE_PUNCTUATION:
            case Character.INITIAL_QUOTE_PUNCTUATION:
            case Character.OTHER_PUNCTUATION:
            case Character.START_PUNCTUATION:
                return true;
            default:
                switch (codePoint) {
                    case '$':
                    case '+':
                    case '<':
                    case '=':
                    case '>':
                    case '^':
                    case '`':
                    case '|':
                    case '~':
                        return true;
                    default:
                        return false;
                }
        }
    }

    public static boolean isWhitespaceCodePoint(int codePoint) {
        switch (codePoint) {
            case ' ':
            case '\t':
            case '\r':
            case '\n':
            case '\f':
                return true;
            default:
                return Character.getType(codePoint) == Character.SPACE_SEPARATOR;
        }
    }

    /**
     * Prepares the input line replacing {@code \0}
     */
    public static CharSequence prepareLine(CharSequence line) {
        // Avoid building a new string in the majority of cases (no \0)
        StringBuilder sb = null;
        int length = line.length();
        for (int i = 0; i < length; i++) {
            char c = line.charAt(i);
            if (c == '\0') {
                if (sb == null) {
                    sb = new StringBuilder(length);
                    sb.append(line, 0, i);
                }
                sb.append('\uFFFD');
            } else {
                if (sb != null) {
                    sb.append(c);
                }
            }
        }

        if (sb != null) {
            return sb.toString();
        } else {
            return line;
        }
    }

    public static int skip(char skip, CharSequence s, int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            if (s.charAt(i) != skip) {
                return i;
            }
        }
        return endIndex;
    }

    public static int skipBackwards(char skip, CharSequence s, int startIndex, int lastIndex) {
        for (int i = startIndex; i >= lastIndex; i--) {
            if (s.charAt(i) != skip) {
                return i;
            }
        }
        return lastIndex - 1;
    }

    public static int skipSpaceTab(CharSequence s, int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            switch (s.charAt(i)) {
                case ' ':
                case '\t':
                    break;
                default:
                    return i;
            }
        }
        return endIndex;
    }

    public static int skipSpaceTabBackwards(CharSequence s, int startIndex, int lastIndex) {
        for (int i = startIndex; i >= lastIndex; i--) {
            switch (s.charAt(i)) {
                case ' ':
                case '\t':
                    break;
                default:
                    return i;
            }
        }
        return lastIndex - 1;
    }

    private static int findNonSpace(CharSequence s, int startIndex) {
        int length = s.length();
        for (int i = startIndex; i < length; i++) {
            switch (s.charAt(i)) {
                case ' ':
                case '\t':
                case '\n':
                case '\u000B':
                case '\f':
                case '\r':
                    break;
                default:
                    return i;
            }
        }
        return -1;
    }
}
