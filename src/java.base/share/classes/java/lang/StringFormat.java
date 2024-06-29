/*
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang;

import jdk.internal.math.DoubleToDecimal;
import jdk.internal.util.HexDigits;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Formatter;
import java.util.IllegalFormatConversionException;
import java.util.function.LongToIntFunction;
import java.util.function.ToIntFunction;

/**
 * Utility class for string format fastpath
 */
final class StringFormat {
    static final char DECIMAL_INTEGER           = 'd';
    static final char HEXADECIMAL_INTEGER       = 'x';
    static final char HEXADECIMAL_INTEGER_UPPER = 'X';
    static final char STRING                    = 's';

    static String format(String format, Object... args) {
        if (args != null) {
            String s = null;
            if (args.length == 1) {
                s = format1(format, args[0]);
            } else if (args.length == 2) {
                s = format2(format, args[0], args[1]);
            }
            if (s != null) {
                return s;
            }
        }

        return new Formatter().format(format, args).toString();
    }

    private static String format1(String format, Object arg) {
        int off = format.indexOf('%');
        if (off == -1) {
            // no formatting to be done
            return format;
        }

        int max = format.length();
        if (off + 1 == max) {
            return null;
        }

        if (off + 2 < max) {
            if (format.indexOf('%', off + 2) != -1) {
                return null;
            }
        }

        char conv = format.charAt(off + 1);
        int width = 0;
        if (conv >= '1' && conv <= '9') {
            width = conv - '0';
            if (off + 2 < max) {
                conv = format.charAt(off + 2);
            }
        }

        if (conv == STRING) {
            if (isBigInt(arg)) {
                conv = DECIMAL_INTEGER;
            } else {
                arg = String.valueOf(arg);
            }
        }

        int size = stringSize(conv, arg);
        if (size == -1) {
            return null;
        }
        return format1(format, off, conv, arg, width, size);
    }

    private static String format2(String format, Object arg0, Object arg1) {
        int off0 = format.indexOf('%');
        if (off0 == -1) {
            // no formatting to be done
            return format;
        }

        int max = format.length();
        if (off0 + 1 == max) {
            return null;
        }
        char conv0 = format.charAt(off0 + 1);
        int width0 = 0;
        if (conv0 >= '1' && conv0 <= '9') {
            width0 = conv0 - '0';
            if (off0 + 2 < max) {
                conv0 = format.charAt(off0 + 2);
            }
        }

        int off1 = format.indexOf('%', off0 + 1);
        if (off1 == -1 || off1 + 1 == max) {
            return null;
        }
        char conv1 = format.charAt(off1 + 1);
        int width1 = 0;
        if (conv1 >= '1' && conv1 <= '9') {
            width1 = conv1 - '0';
            if (off1 + 2 < max) {
                conv1 = format.charAt(off1 + 2);
            }
        }

        if (off1 + 2 < max) {
            if (format.indexOf('%', off1 + 2) != -1) {
                return null;
            }
        }

        String str;
        byte coder = format.coder();
        if (conv0 == STRING) {
            if (isBigInt(arg0)) {
                conv0 = DECIMAL_INTEGER;
            } else {
                str = String.valueOf(arg0);
                coder |= str.coder();
                arg0 = str;
            }
        }

        if (conv1 == STRING) {
            if (isBigInt(arg1)) {
                conv1 = DECIMAL_INTEGER;
            } else {
                str = String.valueOf(arg1);
                coder |= str.coder();
                arg1 = String.valueOf(str);
            }
        }

        int size0 = stringSize(conv0, arg0);
        if (size0 == -1) {
            return null;
        }
        int size1 = stringSize(conv1, arg1);
        if (size1 == -1) {
            return null;
        }

        return coder == String.LATIN1
                ? format2Latin1(format, off0, conv0, arg0, width0, size0, off1, conv1, arg1, width1, size1)
                : format2UTF16( format, off0, conv0, arg0, width0, size0, off1, conv1, arg1, width1, size1);
    }

    static int stringSize(char conv, Object arg) {
        int size = -1;
        if (isBigInt(arg)) {
            long longValue = ((Number) arg).longValue();
            if (conv == DECIMAL_INTEGER) {
                size = Long.stringSize(longValue);
            } else if (conv == HEXADECIMAL_INTEGER || conv == HEXADECIMAL_INTEGER_UPPER) {
                size = HexDigits.stringSize(longValue);
            }
        } else if (conv == STRING && arg instanceof String) {
            size = ((String) arg).length();
        }
        return size;
    }

    static boolean isBigInt(Object arg) {
        return arg instanceof Byte || arg instanceof Short || arg instanceof Integer || arg instanceof Long;
    }

    static String format1(String format, int off, char conv, Object arg, int width, int size) {
        byte coder = format.coder();
        if (arg instanceof String) {
            coder |= ((String) arg).coder();
        }
        int specifierSize = 2 + (width != 0 ? 1 : 0);
        int length = format.length() + Math.max(width, size) - specifierSize;
        byte[] bytes = new byte[length << coder];
        if (off > 0) {
            format.getBytes(bytes, 0, 0, coder, off);
        }

        int index = coder == String.LATIN1
                ? getCharsLatin1(bytes, off, arg, conv, width, size)
                : getCharsUTF16(bytes, off, arg, conv, width, size);

        int rest = format.length() - off - specifierSize;
        if (rest > 0) {
            format.getBytes(bytes, off + specifierSize, index, coder, rest);
        }
        return new String(bytes, coder);
    }

    static String format2Latin1(
            String format,
            int off0, char conv0, Object arg0, int width0, int size0,
            int off1, char conv1, Object arg1, int width1, int size1
    ) {
        int specifierSize0 = 2 + (width0 != 0 ? 1 : 0);
        int specifierSize1 = 2 + (width1 != 0 ? 1 : 0);
        int length = format.length()
                + Math.max(width0, size0)
                + Math.max(width1, size1)
                - specifierSize0
                - specifierSize1;
        byte[] bytes = new byte[length];
        if (off0 > 0) {
            format.getBytes(bytes, 0, 0, String.LATIN1, off0);
        }

        int index = getCharsLatin1(bytes, off0, arg0, conv0, width0, size0);

        int middle = off1 - off0 - specifierSize0;
        if (middle > 0) {
            format.getBytes(bytes, off0 + specifierSize0, index, String.LATIN1, middle);
            index += middle;
        }

        index = getCharsLatin1(bytes, index, arg1, conv1, width1, size1);

        int rest = format.length() - off1 - specifierSize1;
        if (rest > 0) {
            format.getBytes(bytes, off1 + specifierSize1, index, String.LATIN1, rest);
        }
        return new String(bytes, String.LATIN1);
    }

    static String format2UTF16(
            String format,
            int off0, char conv0, Object arg0, int width0, int size0,
            int off1, char conv1, Object arg1, int width1, int size1
    ) {
        byte coder = String.UTF16;
        int specifierSize0 = 2 + (width0 != 0 ? 1 : 0);
        int specifierSize1 = 2 + (width1 != 0 ? 1 : 0);
        int length = format.length()
                + Math.max(width0, size0)
                + Math.max(width1, size1)
                - specifierSize0
                - specifierSize1;
        byte[] bytes = new byte[length << coder];
        if (off0 > 0) {
            format.getBytes(bytes, 0, 0, coder, off0);
        }

        int index = getCharsUTF16(bytes, off0, arg0, conv0, width0, size0);

        int middle = off1 - off0 - specifierSize0;
        if (middle > 0) {
            format.getBytes(bytes, off0 + specifierSize0, index, coder, middle);
            index += middle;
        }

        index = getCharsUTF16(bytes, index, arg1, conv1, width1, size1);

        int rest = format.length() - off1 - specifierSize1;
        if (rest > 0) {
            format.getBytes(bytes, off1 + specifierSize1, index, coder, rest);
        }
        return new String(bytes, coder);
    }

    private static int getCharsLatin1(byte[] bytes, int index, Object arg, char conv, int digit, int size) {
        if (size < digit) {
            for (int i = size; i < digit; i++) {
                bytes[index++] = ' ';
            }
        }

        if (conv == STRING) {
            String str = (String) arg;
            str.getBytes(bytes, index, String.LATIN1);
            return index + size;
        }

        long value = ((Number) arg).longValue();
        index += size;
        if (conv == HEXADECIMAL_INTEGER) {
            HexDigits.getCharsLatin1(value, index, bytes);
        } else if (conv == HEXADECIMAL_INTEGER_UPPER) {
            HexDigits.getCharsLatin1(value, index, bytes, true);
        } else {
            StringLatin1.getChars(value, index, bytes);
        }
        return index;
    }

    private static int getCharsUTF16(byte[] bytes, int index, Object arg, char conv, int digit, int size) {
        if (size < digit) {
            for (int i = size; i < digit; i++) {
                StringUTF16.putChar(bytes, index++, ' ');
            }
        }

        if (conv == STRING) {
            String str = (String) arg;
            str.getBytes(bytes, index, String.UTF16);
            return index + size;
        }

        long value = ((Number) arg).longValue();
        index += size;
        if (conv == HEXADECIMAL_INTEGER) {
            HexDigits.getCharsUTF16(value, index, bytes);
        } else if (conv == HEXADECIMAL_INTEGER_UPPER) {
            HexDigits.getCharsUTF16(value, index, bytes, true);
        } else {
            StringUTF16.getChars(value, index, bytes);
        }
        return index;
    }
}
