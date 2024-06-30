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

import java.text.DecimalFormatSymbols;
import java.util.Formatter;
import java.util.Locale;

import jdk.internal.util.HexDigits;

/**
 * Utility class for string format fastpath
 */
final class StringFormat {
    private static final char DECIMAL_INTEGER           = 'd';
    private static final char HEXADECIMAL_INTEGER       = 'x';
    private static final char HEXADECIMAL_INTEGER_UPPER = 'X';
    private static final char STRING                    = 's';

    static String format(String format, Object... args) {
        if (args != null) {
            int off = format.indexOf('%');
            if (off == -1) {
                // no formatting to be done
                return format;
            }

            int len = format.length();
            if (off + 1 != len) {
                int off1 = format.indexOf('%', off + 2);
                String s = null;
                if (args.length == 1) {
                    if (off1 == -1) {
                        s = format1(format, off, args[0]);
                    }
                } else if (args.length == 2) {
                    if (off1 != -1 && off1 + 1 != len) {
                        s = format2(format, off, off1, args[0], args[1]);
                    }
                }
                if (s != null) {
                    return s;
                }
            }
        }

        return new Formatter().format(format, args).toString();
    }

    private static String format1(String format, int off, Object arg) {
        int len = format.length();
        char conv = format.charAt(off + 1);
        int width = 0;
        if (conv >= '1' && conv <= '9') {
            width = conv - '0';
            if (off + 2 < len) {
                conv = format.charAt(off + 2);
            }
        }

        byte coder = format.coder();
        if (conv == STRING) {
            if (isLong(arg)) {
                conv = DECIMAL_INTEGER;
            } else {
                String str = String.valueOf(arg);
                coder |= str.coder();
                arg = str;
            }
        }

        int size = stringSize(conv, arg);
        if (size == -1) {
            return null;
        }
        return format1(format, coder, off, conv, arg, width, size);
    }

    private static String format2(String format, int off0, int off1, Object arg0, Object arg1) {
        final int len = format.length();
        char conv0 = format.charAt(off0 + 1);
        int width0 = 0;
        if (conv0 >= '1' && conv0 <= '9') {
            width0 = conv0 - '0';
            if (off0 + 2 < len) {
                conv0 = format.charAt(off0 + 2);
            }
        }

        char conv1 = format.charAt(off1 + 1);
        int width1 = 0;
        if (conv1 >= '1' && conv1 <= '9') {
            width1 = conv1 - '0';
            if (off1 + 2 < len) {
                conv1 = format.charAt(off1 + 2);
            }
        }

        if (off1 + 2 < len) {
            if (format.indexOf('%', off1 + 2) != -1) {
                return null;
            }
        }

        String str;
        byte coder = format.coder();
        if (conv0 == STRING) {
            if (isLong(arg0)) {
                conv0 = DECIMAL_INTEGER;
            } else {
                str = String.valueOf(arg0);
                coder |= str.coder();
                arg0 = str;
            }
        }

        if (conv1 == STRING) {
            if (isLong(arg1)) {
                conv1 = DECIMAL_INTEGER;
            } else {
                str = String.valueOf(arg1);
                coder |= str.coder();
                arg1 = str;
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

        int specifierSize0 = 2 + (width0 != 0 ? 1 : 0);
        int specifierSize1 = 2 + (width1 != 0 ? 1 : 0);

        int strlen = len
                + Math.max(width0, size0)
                + Math.max(width1, size1)
                - specifierSize0
                - specifierSize1;
        return coder == String.LATIN1
                ? format2Latin1(format, strlen, off0, conv0, arg0, width0, size0, specifierSize0,
                                                off1, conv1, arg1, width1, size1, specifierSize1)
                : format2UTF16( format, strlen, off0, conv0, arg0, width0, size0, specifierSize0,
                                                off1, conv1, arg1, width1, size1, specifierSize1);
    }

    private static int stringSize(char conv, Object arg) {
        int size = -1;
        if (isLong(arg)) {
            long longValue = ((Number) arg).longValue();
            if (conv == DECIMAL_INTEGER) {
                if (defaultLocaleDecimalSupport()) {
                    size = Long.stringSize(longValue);
                }
            } else if (conv == HEXADECIMAL_INTEGER || conv == HEXADECIMAL_INTEGER_UPPER) {
                size = HexDigits.stringSize(longValue);
            }
        } else if (conv == STRING && arg instanceof String) {
            size = ((String) arg).length();
        }
        return size;
    }

    private static boolean isLong(Object arg) {
        return arg instanceof Long
            || arg instanceof Integer
            || arg instanceof Short
            || arg instanceof Byte;
    }

    private static String format1(String format, byte coder, int off, char conv, Object arg, int width, int size) {
        int specifierSize = 2 + (width != 0 ? 1 : 0);
        int strlen = format.length() + Math.max(width, size) - specifierSize;
        byte[] bytes = new byte[strlen << coder];
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

    private static String format2Latin1(
            String format, int strlen,
            int off0, char conv0, Object arg0, int width0, int size0, int specifierSize0,
            int off1, char conv1, Object arg1, int width1, int size1, int specifierSize1
    ) {
        byte[] bytes = new byte[strlen];
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

    private static String format2UTF16(
            String format, int strlen,
            int off0, char conv0, Object arg0, int width0, int size0, int specifierSize0,
            int off1, char conv1, Object arg1, int width1, int size1, int specifierSize1
    ) {
        byte coder = String.UTF16;
        byte[] bytes = new byte[strlen << coder];
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

    private static int getCharsLatin1(byte[] bytes, int index, Object arg, char conv, int width, int size) {
        if (size < width) {
            for (int i = size; i < width; i++) {
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

    private static int getCharsUTF16(byte[] bytes, int index, Object arg, char conv, int width, int size) {
        if (size < width) {
            for (int i = size; i < width; i++) {
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

    private static boolean defaultLocaleDecimalSupport() {
        return Locale.getDefault(Locale.Category.FORMAT) == DecimalFormat.FAST_PATH_FORMAT_LOCALE;
    }

    private static class DecimalFormat {
        static final Locale FAST_PATH_FORMAT_LOCALE;
        static {
            Locale locale = Locale.getDefault(Locale.Category.FORMAT);

            boolean zero = false;

            //Avoid expensive initialization of DecimalFormatSymbols in the following languages
            String[] fast_path_languages = {"en", "fr", "de", "it", "ja", "ko", "zh"};
            for (String lange : fast_path_languages) {
                if (lange.equals(locale.getLanguage())) {
                    zero = true;
                    break;
                }
            }

            if (!zero) {
                DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(locale);
                zero = dfs.getZeroDigit() == '0';
            }

            FAST_PATH_FORMAT_LOCALE = zero ? locale : null;
        }
    }
}
