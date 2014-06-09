/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.api.scripting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatter is a class to get the type conversion between javascript types and
 * java types for the format (sprintf) method working.
 *
 * <p>In javascript the type for numbers can be different from the format type
 * specifier. For format type '%d', '%o', '%x', '%X' double need to be
 * converted to integer. For format type 'e', 'E', 'f', 'g', 'G', 'a', 'A'
 * integer needs to be converted to double.
 *
 * <p>Format type "%c" and javascript string needs special handling.
 *
 * <p>The javascript date objects can be handled if they are type double (the
 * related javascript code will convert with Date.getTime() to double). So
 * double date types are converted to long.
 *
 * <p>Pattern and the logic for parameter position: java.util.Formatter
 *
 */
final class Formatter {

    private Formatter() {
    }

    /**
     * Method which converts javascript types to java types for the
     * String.format method (jrunscript function sprintf).
     *
     * @param format a format string
     * @param args arguments referenced by the format specifiers in format
     * @return a formatted string
     */
    static String format(final String format, final Object[] args) {
        final Matcher m = FS_PATTERN.matcher(format);
        int positionalParameter = 1;

        while (m.find()) {
            int index = index(m.group(1));
            final boolean previous = isPreviousArgument(m.group(2));
            final char conversion = m.group(6).charAt(0);

            // skip over some formats
            if (index < 0 || previous
                    || conversion == 'n' || conversion == '%') {
                continue;
            }

            // index 0 here means take a positional parameter
            if (index == 0) {
                index = positionalParameter++;
            }

            // out of index, String.format will handle
            if (index > args.length) {
                continue;
            }

            // current argument
            final Object arg = args[index - 1];

            // for date we convert double to long
            if (m.group(5) != null) {
                // convert double to long
                if (arg instanceof Double) {
                    args[index - 1] = ((Double) arg).longValue();
                }
            } else {
                // we have to convert some types
                switch (conversion) {
                    case 'd':
                    case 'o':
                    case 'x':
                    case 'X':
                        if (arg instanceof Double) {
                            // convert double to long
                            args[index - 1] = ((Double) arg).longValue();
                        } else if (arg instanceof String
                                && ((String) arg).length() > 0) {
                            // convert string (first character) to int
                            args[index - 1] = (int) ((String) arg).charAt(0);
                        }
                        break;
                    case 'e':
                    case 'E':
                    case 'f':
                    case 'g':
                    case 'G':
                    case 'a':
                    case 'A':
                        if (arg instanceof Integer) {
                            // convert integer to double
                            args[index - 1] = ((Integer) arg).doubleValue();
                        }
                        break;
                    case 'c':
                        if (arg instanceof Double) {
                            // convert double to integer
                            args[index - 1] = ((Double) arg).intValue();
                        } else if (arg instanceof String
                                && ((String) arg).length() > 0) {
                            // get the first character from string
                            args[index - 1] = (int) ((String) arg).charAt(0);
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        return String.format(format, args);
    }

    /**
     * Method to parse the integer of the argument index.
     *
     * @param s string to parse
     * @return -1 if parsing failed, 0 if string is null, > 0 integer
     */
    private static int index(final String s) {
        int index = -1;

        if (s != null) {
            try {
                index = Integer.parseInt(s.substring(0, s.length() - 1));
            } catch (final NumberFormatException e) {
                //ignored
            }
        } else {
            index = 0;
        }

        return index;
    }

    /**
     * Method to check if a string contains '&lt;'. This is used to find out if
     * previous parameter is used.
     *
     * @param s string to check
     * @return true if '&lt;' is in the string, else false
     */
    private static boolean isPreviousArgument(final String s) {
        return (s != null && s.indexOf('<') >= 0) ? true : false;
    }

    // %[argument_index$][flags][width][.precision][t]conversion
    private static final String formatSpecifier =
            "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";
    // compiled format string
    private static final Pattern FS_PATTERN;

    static {
        FS_PATTERN = Pattern.compile(formatSpecifier);
    }
}
