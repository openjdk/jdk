/*
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package jdk.nashorn.internal.runtime.regexp.joni;

import java.util.Arrays;
import jdk.nashorn.internal.runtime.regexp.joni.encoding.CharacterType;
import jdk.nashorn.internal.runtime.regexp.joni.encoding.IntHolder;

@SuppressWarnings("javadoc")
public final class EncodingHelper {

    final static int NEW_LINE            = 0x000a;
    final static int RETURN              = 0x000d;
    final static int LINE_SEPARATOR      = 0x2028;
    final static int PARAGRAPH_SEPARATOR = 0x2029;

    final static char[] EMPTYCHARS = new char[0];
    final static int[][] codeRanges = new int[15][];

    public static int digitVal(final int code) {
        return code - '0';
    }

    public static int odigitVal(final int code) {
        return digitVal(code);
    }

    public static boolean isXDigit(final int code) {
        return Character.isDigit(code) || (code >= 'a' && code <= 'f') || (code >= 'A' && code <= 'F');
    }

    public static int xdigitVal(final int code) {
        if (Character.isDigit(code)) {
            return code - '0';
        } else if (code >= 'a' && code <= 'f') {
            return code - 'a' + 10;
        } else {
            return code - 'A' + 10;
        }
    }

    public static boolean isDigit(final int code) {
        return code >= '0' && code <= '9';
    }

    public static boolean isWord(final int code) {
        // letter, digit, or '_'
        return (1 << Character.getType(code) & CharacterType.WORD_MASK) != 0;
    }

    public static boolean isNewLine(final int code) {
        return code == NEW_LINE || code == RETURN || code == LINE_SEPARATOR || code == PARAGRAPH_SEPARATOR;
    }

    public static boolean isNewLine(final char[] chars, final int p, final int end) {
        return p < end && isNewLine(chars[p]);
    }

    // Encoding.prevCharHead
    public static int prevCharHead(final int p, final int s) {
        return s <= p ? -1 : s - 1;
    }

    /* onigenc_get_right_adjust_char_head_with_prev */
    public static int rightAdjustCharHeadWithPrev(final int s, final IntHolder prev) {
        if (prev != null) {
            prev.value = -1; /* Sorry */
        }
        return s;
    }

    // Encoding.stepBack
    public static int stepBack(final int p, final int sp, final int np) {
        int s = sp, n = np;
        while (s != -1 && n-- > 0) {
           if (s <= p) {
            return -1;
        }
           s--;
       }
       return s;
    }

    public static int mbcodeStartPosition() {
        return 0x80;
    }

    public static char[] caseFoldCodesByString(final int flag, final char c) {
        char[] codes = EMPTYCHARS;
        final char upper = toUpperCase(c);

        if (upper != toLowerCase(upper)) {
            int count = 0;
            char ch = 0;

            do {
                final char u = toUpperCase(ch);
                if (u == upper && ch != c) {
                    // Almost all characters will return array of length 1, very few 2 or 3, so growing by one is fine.
                    codes = count == 0 ? new char[1] : Arrays.copyOf(codes, count + 1);
                    codes[count++] = ch;
                }
            } while (ch++ < 0xffff);
        }
        return codes;
    }

    public static void applyAllCaseFold(final int flag, final ApplyCaseFold fun, final Object arg) {
        for (int c = 0; c < 0xffff; c++) {
            if (Character.isLowerCase(c)) {
                final int upper = toUpperCase(c);

                if (upper != c) {
                    ApplyCaseFold.apply(c, upper, arg);
                }
            }
        }

        // Some characters have multiple lower case variants, hence we need to do a second run
        for (int c = 0; c < 0xffff; c++) {
            if (Character.isLowerCase(c)) {
                final int upper = toUpperCase(c);

                if (upper != c) {
                    ApplyCaseFold.apply(upper, c, arg);
                }
            }
        }
    }

    public static char toLowerCase(final char c) {
        return (char)toLowerCase((int)c);
    }

    public static int toLowerCase(final int c) {
        if (c < 128) {
            return ('A' <= c && c <= 'Z') ? (c + ('a' - 'A')) : c;
        }
        // Do not convert non-ASCII upper case character to ASCII lower case.
        final int lower = Character.toLowerCase(c);
        return (lower < 128) ? c : lower;

    }

    public static char toUpperCase(final char c) {
        return (char)toUpperCase((int)c);
    }

    public static int toUpperCase(final int c) {
        if (c < 128) {
            return ('a' <= c && c <= 'z') ? c + ('A' - 'a') : c;
        }
        // Do not convert non-ASCII lower case character to ASCII upper case.
        final int upper = Character.toUpperCase(c);
        return (upper < 128) ? c : upper;
    }

    public static int[] ctypeCodeRange(final int ctype, final IntHolder sbOut) {
        sbOut.value = 0x100; // use bitset for codes smaller than 256
        int[] range = null;

        if (ctype < codeRanges.length) {
            range = codeRanges[ctype];

            if (range == null) {
                // format: [numberOfRanges, rangeStart, rangeEnd, ...]
                range = new int[16];
                int rangeCount = 0;
                int lastCode = -2;

                for (int code = 0; code <= 0xffff; code++) {
                    if (isCodeCType(code, ctype)) {
                        if (lastCode < code -1) {
                            if (rangeCount * 2 + 2 >= range.length) {
                                range = Arrays.copyOf(range, range.length * 2);
                            }
                            range[rangeCount * 2 + 1] = code;
                            rangeCount++;
                        }
                        range[rangeCount * 2] = lastCode = code;
                    }
                }

                if (rangeCount * 2 + 1 < range.length) {
                    range = Arrays.copyOf(range, rangeCount * 2 + 1);
                }

                range[0] = rangeCount;
                codeRanges[ctype] = range;
            }
        }

        return range;
    }

    // CodeRange.isInCodeRange
    public static boolean isInCodeRange(final int[] p, final int offset, final int code) {
        int low = 0;
        final int n = p[offset];
        int high = n ;

        while (low < high) {
            final int x = (low + high) >> 1;
            if (code > p[(x << 1) + 2 + offset]) {
                low = x + 1;
            } else {
                high = x;
            }
        }
        return low < n && code >= p[(low << 1) + 1 + offset];
    }

    /**
     * @see <a href="http://www.geocities.jp/kosako3/oniguruma/doc/RE.txt">http://www.geocities.jp/kosako3/oniguruma/doc/RE.txt</a>
     *
     * @param code code
     * @param ctype ctype
     *
     * @return isCodeCType
     */
    public static boolean isCodeCType(final int code, final int ctype) {
        int type;
        switch (ctype) {
            case CharacterType.NEWLINE:
                return isNewLine(code);
            case CharacterType.ALPHA:
                return (1 << Character.getType(code) & CharacterType.ALPHA_MASK) != 0;
            case CharacterType.BLANK:
                return code == 0x09 || Character.getType(code) == Character.SPACE_SEPARATOR;
            case CharacterType.CNTRL:
                type = Character.getType(code);
                return (1 << type & CharacterType.CNTRL_MASK) != 0 || type == Character.UNASSIGNED;
            case CharacterType.DIGIT:
                return EncodingHelper.isDigit(code);
            case CharacterType.GRAPH:
                switch (code) {
                    case 0x09:
                    case 0x0a:
                    case 0x0b:
                    case 0x0c:
                    case 0x0d:
                        return false;
                    default:
                        type = Character.getType(code);
                        return (1 << type & CharacterType.GRAPH_MASK) == 0 && type != Character.UNASSIGNED;
                }
            case CharacterType.LOWER:
                return Character.isLowerCase(code);
            case CharacterType.PRINT:
                type = Character.getType(code);
                return (1 << type & CharacterType.PRINT_MASK) == 0 && type != Character.UNASSIGNED;
            case CharacterType.PUNCT:
                return (1 << Character.getType(code) & CharacterType.PUNCT_MASK) != 0;
            case CharacterType.SPACE:
                // ECMA 7.2 and 7.3
                switch (code) {
                    case 0x09:
                    case 0x0a:
                    case 0x0b:
                    case 0x0c:
                    case 0x0d:
                        return true;
                    default:
                        // true if Unicode separator or BOM or U+180E (see JDK-8138758)
                        return (1 << Character.getType(code) & CharacterType.SPACE_MASK) != 0
                                || code == 0xfeff || code == 0x180e;
                }
            case CharacterType.UPPER:
                return Character.isUpperCase(code);
            case CharacterType.XDIGIT:
                return EncodingHelper.isXDigit(code);
            case CharacterType.WORD:
                return (1 << Character.getType(code) & CharacterType.WORD_MASK) != 0;
            case CharacterType.ALNUM:
                return (1 << Character.getType(code) & CharacterType.ALNUM_MASK) != 0;
            case CharacterType.ASCII:
                return code < 0x80;
            default:
                throw new RuntimeException("illegal character type: " + ctype);
        }
    }
}

