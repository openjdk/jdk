/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader.impl;

import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.utils.Levenshtein;

/**
 * Utility methods for LineReader implementations.
 * <p>
 * This class provides helper methods for working with LineReader variables and options.
 * It includes methods for retrieving variables of different types (string, boolean, integer,
 * long) with default values, checking if options are set, and calculating string distances
 * for completion matching.
 * <p>
 * These utilities are primarily used by the LineReader implementation classes to access
 * configuration values in a consistent way, with proper type conversion and default handling.
 *
 * @see LineReader#getVariable(String)
 * @see LineReader#isSet(LineReader.Option)
 */
public class ReaderUtils {

    private ReaderUtils() {}

    /**
     * Checks if a LineReader option is set.
     * <p>
     * This method safely handles null readers by returning false.
     *
     * @param reader the LineReader to check, may be null
     * @param option the option to check
     * @return true if the reader is not null and the option is set, false otherwise
     */
    public static boolean isSet(LineReader reader, LineReader.Option option) {
        return reader != null && reader.isSet(option);
    }

    /**
     * Gets a string variable from a LineReader with a default value.
     * <p>
     * This method safely handles null readers by returning the default value.
     *
     * @param reader the LineReader to get the variable from, may be null
     * @param name the name of the variable to get
     * @param def the default value to return if the variable is not set or the reader is null
     * @return the variable value as a string, or the default value
     */
    public static String getString(LineReader reader, String name, String def) {
        Object v = reader != null ? reader.getVariable(name) : null;
        return v != null ? v.toString() : def;
    }

    /**
     * Gets a boolean variable from a LineReader with a default value.
     * <p>
     * This method safely handles null readers by returning the default value.
     * String values are converted to boolean according to these rules:
     * <ul>
     *   <li>Empty string, "on", "1", and "true" (case-insensitive) are considered true</li>
     *   <li>All other strings are considered false</li>
     * </ul>
     *
     * @param reader the LineReader to get the variable from, may be null
     * @param name the name of the variable to get
     * @param def the default value to return if the variable is not set or the reader is null
     * @return the variable value as a boolean, or the default value
     */
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

    /**
     * Gets an integer variable from a LineReader with a default value.
     * <p>
     * This method safely handles null readers by returning the default value.
     * String values are parsed as integers, with a fallback to 0 if parsing fails.
     *
     * @param reader the LineReader to get the variable from, may be null
     * @param name the name of the variable to get
     * @param def the default value to return if the variable is not set or the reader is null
     * @return the variable value as an integer, or the default value
     */
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

    /**
     * Gets a long variable from a LineReader with a default value.
     * <p>
     * This method safely handles null readers by returning the default value.
     * String values are parsed as longs, with a fallback to 0 if parsing fails.
     *
     * @param reader the LineReader to get the variable from, may be null
     * @param name the name of the variable to get
     * @param def the default value to return if the variable is not set or the reader is null
     * @return the variable value as a long, or the default value
     */
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

    /**
     * Calculates the edit distance between a word and a candidate string.
     * <p>
     * This method is used for fuzzy matching in completion. It uses the Levenshtein
     * distance algorithm to determine how similar two strings are, with special handling
     * for candidates that are longer than the word being matched.
     *
     * @param word the word to match against
     * @param cand the candidate string to check
     * @return the edit distance between the strings (lower values indicate closer matches)
     */
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
