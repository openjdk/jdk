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

import jdk.nashorn.internal.runtime.regexp.joni.encoding.CharacterType;
import jdk.nashorn.internal.runtime.regexp.joni.encoding.IntHolder;

import java.util.Arrays;

public class EncodingHelper {

    public final static char NEW_LINE = 0xa;
    public final static char RETURN   = 0xd;

    final static char[] EMPTYCHARS = new char[0];
    final static int[][] codeRanges = new int[15][];

    public static int digitVal(int code) {
        return code - '0';
    }

    public static int odigitVal(int code) {
        return digitVal(code);
    }

    public static boolean isXDigit(int code) {
        return Character.isDigit(code) || (code >= 'a' && code <= 'f') || (code >= 'A' && code <= 'F');
    }

    public static int xdigitVal(int code) {
        if (Character.isDigit(code)) {
            return code - '0';
        } else if (code >= 'a' && code <= 'f') {
            return code - 'a' + 10;
        } else {
            return code - 'A' + 10;
        }
    }

    public static boolean isDigit(int code) {
        return code >= '0' && code <= '9';
    }

    public static boolean isWord(int code) {
        // letter, digit, or '_'
        return (1 << Character.getType(code) & CharacterType.WORD_MASK) != 0;
    }

    public static boolean isNewLine(int code) {
        return code == NEW_LINE;
    }

    public static boolean isNewLine(char[] chars, int p, int end) {
        return p < end && chars[p] == NEW_LINE;
    }

    public static boolean isCrnl(char[] chars, int p, int end) {
        return p + 1 < end && chars[p] == RETURN && chars[p + 1] == NEW_LINE;
    }

    // Encoding.prevCharHead
    public static int prevCharHead(int p, int s) {
        return s <= p ? -1 : s - 1;
    }

    /* onigenc_get_right_adjust_char_head_with_prev */
    public static int rightAdjustCharHeadWithPrev(int s, IntHolder prev) {
        if (prev != null) prev.value = -1; /* Sorry */
        return s;
    }

    // Encoding.stepBack
    public static int stepBack(int p, int s, int n) {
       while (s != -1 && n-- > 0) {
           if (s <= p) return -1;
           s--;
       }
       return s;
    }

    public static int mbcToCode(byte[] bytes, int p, int end) {
        int code = 0;
        for (int i = p; i < end; i++) {
            code = (code << 8) | (bytes[i] & 0xff);
        }
        return code;
    }

    public static int mbcodeStartPosition() {
        return 0x80;
    }

    public static char[] caseFoldCodesByString(int flag, char c) {
        if (Character.isUpperCase(c)) {
            return new char[] {Character.toLowerCase(c)};
        } else if (Character.isLowerCase(c)) {
            return new char[] {Character.toUpperCase(c)};
        } else {
            return EMPTYCHARS;
        }
    }

    public static void applyAllCaseFold(int flag, ApplyCaseFold fun, Object arg) {
        int[] code = new int[1];

        for (int c = 0; c < 0xffff; c++) {
            if (Character.getType(c) == Character.LOWERCASE_LETTER) {

                int upper = code[0] = Character.toUpperCase(c);
                fun.apply(c, code, 1, arg);

                code[0] = c;
                fun.apply(upper, code, 1, arg);
            }
        }
    }

    public static int[] ctypeCodeRange(int ctype, IntHolder sbOut) {
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
    public static boolean isInCodeRange(int[] p, int offset, int code) {
        int low = 0;
        int n = p[offset];
        int high = n ;

        while (low < high) {
            int x = (low + high) >> 1;
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
     */
    public static boolean isCodeCType(int code, int ctype) {
        int type;
        switch (ctype) {
            case CharacterType.NEWLINE:
                return code == EncodingHelper.NEW_LINE;
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
                        // true if Unicode separator or BOM
                        return (1 << Character.getType(code) & CharacterType.SPACE_MASK) != 0 || code == 0xfeff;
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

