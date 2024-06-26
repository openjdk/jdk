/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.internal.util;

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Escaping {

    public static final String ESCAPABLE = "[!\"#$%&\'()*+,./:;<=>?@\\[\\\\\\]^_`{|}~-]";

    public static final String ENTITY = "&(?:#x[a-f0-9]{1,6}|#[0-9]{1,7}|[a-z][a-z0-9]{1,31});";

    private static final Pattern BACKSLASH_OR_AMP = Pattern.compile("[\\\\&]");

    private static final Pattern ENTITY_OR_ESCAPED_CHAR =
            Pattern.compile("\\\\" + ESCAPABLE + '|' + ENTITY, Pattern.CASE_INSENSITIVE);

    // From RFC 3986 (see "reserved", "unreserved") except don't escape '[' or ']' to be compatible with JS encodeURI
    private static final Pattern ESCAPE_IN_URI =
            Pattern.compile("(%[a-fA-F0-9]{0,2}|[^:/?#@!$&'()*+,;=a-zA-Z0-9\\-._~])");

    private static final char[] HEX_DIGITS =
            new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private static final Pattern WHITESPACE = Pattern.compile("[ \t\r\n]+");

    private static final Replacer UNESCAPE_REPLACER = new Replacer() {
        @Override
        public void replace(String input, StringBuilder sb) {
            if (input.charAt(0) == '\\') {
                sb.append(input, 1, input.length());
            } else {
                sb.append(Html5Entities.entityToString(input));
            }
        }
    };

    private static final Replacer URI_REPLACER = new Replacer() {
        @Override
        public void replace(String input, StringBuilder sb) {
            if (input.startsWith("%")) {
                if (input.length() == 3) {
                    // Already percent-encoded, preserve
                    sb.append(input);
                } else {
                    // %25 is the percent-encoding for %
                    sb.append("%25");
                    sb.append(input, 1, input.length());
                }
            } else {
                byte[] bytes = input.getBytes(Charset.forName("UTF-8"));
                for (byte b : bytes) {
                    sb.append('%');
                    sb.append(HEX_DIGITS[(b >> 4) & 0xF]);
                    sb.append(HEX_DIGITS[b & 0xF]);
                }
            }
        }
    };

    public static String escapeHtml(String input) {
        // Avoid building a new string in the majority of cases (nothing to escape)
        StringBuilder sb = null;

        loop:
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            String replacement;
            switch (c) {
                case '&':
                    replacement = "&amp;";
                    break;
                case '<':
                    replacement = "&lt;";
                    break;
                case '>':
                    replacement = "&gt;";
                    break;
                case '\"':
                    replacement = "&quot;";
                    break;
                default:
                    if (sb != null) {
                        sb.append(c);
                    }
                    continue loop;
            }
            if (sb == null) {
                sb = new StringBuilder();
                sb.append(input, 0, i);
            }
            sb.append(replacement);
        }

        return sb != null ? sb.toString() : input;
    }

    /**
     * Replace entities and backslash escapes with literal characters.
     */
    public static String unescapeString(String s) {
        if (BACKSLASH_OR_AMP.matcher(s).find()) {
            return replaceAll(ENTITY_OR_ESCAPED_CHAR, s, UNESCAPE_REPLACER);
        } else {
            return s;
        }
    }

    public static String percentEncodeUrl(String s) {
        return replaceAll(ESCAPE_IN_URI, s, URI_REPLACER);
    }

    public static String normalizeLabelContent(String input) {
        String trimmed = input.trim();

        // This is necessary to correctly case fold "\u1e9e" to "SS":
        // "\u1e9e".toLowerCase(Locale.ROOT)  -> "\u00df"
        // "\u00df".toUpperCase(Locale.ROOT)  -> "SS"
        // Note that doing upper first (or only upper without lower) wouldn't work because:
        // "\u1e9e".toUpperCase(Locale.ROOT)  -> "\u1e9e"
        String caseFolded = trimmed.toLowerCase(Locale.ROOT).toUpperCase(Locale.ROOT);

        return WHITESPACE.matcher(caseFolded).replaceAll(" ");
    }

    private static String replaceAll(Pattern p, String s, Replacer replacer) {
        Matcher matcher = p.matcher(s);

        if (!matcher.find()) {
            return s;
        }

        StringBuilder sb = new StringBuilder(s.length() + 16);
        int lastEnd = 0;
        do {
            sb.append(s, lastEnd, matcher.start());
            replacer.replace(matcher.group(), sb);
            lastEnd = matcher.end();
        } while (matcher.find());

        if (lastEnd != s.length()) {
            sb.append(s, lastEnd, s.length());
        }
        return sb.toString();
    }

    private interface Replacer {
        void replace(String input, StringBuilder sb);
    }
}
