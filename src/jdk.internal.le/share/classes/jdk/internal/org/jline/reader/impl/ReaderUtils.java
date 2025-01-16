/*
 * Copyright (c) 2002-2020, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader.impl;

import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.utils.Levenshtein;

public class ReaderUtils {

    private ReaderUtils() {}

    public static boolean isSet(LineReader reader, LineReader.Option option) {
        return reader != null && reader.isSet(option);
    }

    public static String getString(LineReader reader, String name, String def) {
        Object v = reader != null ? reader.getVariable(name) : null;
        return v != null ? v.toString() : def;
    }

    public static boolean getBoolean(LineReader reader, String name, boolean def) {
        Object v = reader != null ? reader.getVariable(name) : null;
        if (v instanceof Boolean) {
            return (Boolean) v;
        } else if (v != null) {
            String s = v.toString();
            return s.isEmpty() || s.equalsIgnoreCase("on") || s.equalsIgnoreCase("1") || s.equalsIgnoreCase("true");
        }
        return def;
    }

    public static int getInt(LineReader reader, String name, int def) {
        int nb = def;
        Object v = reader != null ? reader.getVariable(name) : null;
        if (v instanceof Number) {
            return ((Number) v).intValue();
        } else if (v != null) {
            nb = 0;
            try {
                nb = Integer.parseInt(v.toString());
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return nb;
    }

    public static long getLong(LineReader reader, String name, long def) {
        long nb = def;
        Object v = reader != null ? reader.getVariable(name) : null;
        if (v instanceof Number) {
            return ((Number) v).longValue();
        } else if (v != null) {
            nb = 0;
            try {
                nb = Long.parseLong(v.toString());
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return nb;
    }

    public static int distance(String word, String cand) {
        if (word.length() < cand.length()) {
            int d1 = Levenshtein.distance(word, cand.substring(0, Math.min(cand.length(), word.length())));
            int d2 = Levenshtein.distance(word, cand);
            return Math.min(d1, d2);
        } else {
            return Levenshtein.distance(word, cand);
        }
    }
}
