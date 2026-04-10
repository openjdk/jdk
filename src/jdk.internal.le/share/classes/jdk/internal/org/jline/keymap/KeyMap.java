/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.keymap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.utils.Curses;
import jdk.internal.org.jline.utils.InfoCmp.Capability;

/**
 * The KeyMap class maps keyboard input sequences to operations or actions.
 * <p>
 * KeyMap is a core component of JLine's input handling system, providing the ability
 * to bind specific key sequences (like Ctrl+A, Alt+F, or multi-key sequences) to
 * arbitrary operations represented by objects of type T.
 * <p>
 * Key features include:
 * <ul>
 *   <li>Support for single-key and multi-key sequences</li>
 *   <li>Special handling for Unicode characters not explicitly bound</li>
 *   <li>Timeout handling for ambiguous bindings (e.g., Escape key vs. Alt combinations)</li>
 *   <li>Utility methods for creating common key sequences (Ctrl, Alt, etc.)</li>
 * </ul>
 * <p>
 * This class is used extensively by the {@link org.jline.reader.LineReader} to implement
 * customizable key bindings for line editing operations.
 *
 * @param <T> the type of objects to which key sequences are bound
 * @since 2.6
 */
public class KeyMap<T> {

    /**
     * The size of the direct mapping array for ASCII characters.
     * Characters with code points below this value are mapped directly.
     */
    public static final int KEYMAP_LENGTH = 128;

    /**
     * Default timeout in milliseconds for ambiguous bindings.
     * <p>
     * This is used when a prefix of a multi-character binding is also bound to an action.
     * For example, if both Escape and Escape+[A are bound, the reader will wait this
     * amount of time after receiving Escape to determine if it's a standalone Escape
     * or the beginning of the Escape+[A sequence.
     */
    public static final long DEFAULT_AMBIGUOUS_TIMEOUT = 1000L;

    private Object[] mapping = new Object[KEYMAP_LENGTH];
    private T anotherKey = null;
    private T unicode;
    private T nomatch;
    private long ambiguousTimeout = DEFAULT_AMBIGUOUS_TIMEOUT;

    /**
     * Creates a new KeyMap.
     */
    public KeyMap() {
        // Default constructor
    }

    /**
     * Converts a key sequence to a displayable string representation.
     * <p>
     * This method formats control characters, escape sequences, and other special
     * characters in a readable way, similar to how they might be represented in
     * configuration files.
     *
     * @param key the key sequence to display
     * @return a readable string representation of the key sequence
     */
    public static String display(String key) {
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < 32) {
                sb.append('^');
                sb.append((char) (c + 'A' - 1));
            } else if (c == 127) {
                sb.append("^?");
            } else if (c == '^' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c >= 128) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    /**
     * Translates a string containing special escape sequences into the actual key sequence.
     * <p>
     * This method handles escape sequences like ^A (Ctrl+A), \e (Escape), \C-a (Ctrl+A),
     * \M-a (Alt+A), etc., converting them to the actual character sequences they represent.
     *
     * @param str the string with escape sequences to translate
     * @return the translated key sequence
     */
    public static String translate(String str) {
        int i;
        if (!str.isEmpty()) {
            char c = str.charAt(0);
            if ((c == '\'' || c == '"') && str.charAt(str.length() - 1) == c) {
                str = str.substring(1, str.length() - 1);
            }
        }
        StringBuilder keySeq = new StringBuilder();
        for (i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\') {
                if (++i >= str.length()) {
                    break;
                }
                c = str.charAt(i);
                switch (c) {
                    case 'a':
                        c = 0x07;
                        break;
                    case 'b':
                        c = '\b';
                        break;
                    case 'd':
                        c = 0x7f;
                        break;
                    case 'e':
                    case 'E':
                        c = 0x1b;
                        break;
                    case 'f':
                        c = '\f';
                        break;
                    case 'n':
                        c = '\n';
                        break;
                    case 'r':
                        c = '\r';
                        break;
                    case 't':
                        c = '\t';
                        break;
                    case 'v':
                        c = 0x0b;
                        break;
                    case '\\':
                        c = '\\';
                        break;
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                        c = 0;
                        for (int j = 0; j < 3; j++, i++) {
                            if (i >= str.length()) {
                                break;
                            }
                            int k = Character.digit(str.charAt(i), 8);
                            if (k < 0) {
                                break;
                            }
                            c = (char) (c * 8 + k);
                        }
                        i--;
                        c &= 0xFF;
                        break;
                    case 'x':
                        i++;
                        c = 0;
                        for (int j = 0; j < 2; j++, i++) {
                            if (i >= str.length()) {
                                break;
                            }
                            int k = Character.digit(str.charAt(i), 16);
                            if (k < 0) {
                                break;
                            }
                            c = (char) (c * 16 + k);
                        }
                        i--;
                        c &= 0xFF;
                        break;
                    case 'u':
                        i++;
                        c = 0;
                        for (int j = 0; j < 4; j++, i++) {
                            if (i >= str.length()) {
                                break;
                            }
                            int k = Character.digit(str.charAt(i), 16);
                            if (k < 0) {
                                break;
                            }
                            c = (char) (c * 16 + k);
                        }
                        break;
                    case 'C':
                        if (++i >= str.length()) {
                            break;
                        }
                        c = str.charAt(i);
                        if (c == '-') {
                            if (++i >= str.length()) {
                                break;
                            }
                            c = str.charAt(i);
                        }
                        c = c == '?' ? 0x7f : (char) (Character.toUpperCase(c) & 0x1f);
                        break;
                }
            } else if (c == '^') {
                if (++i >= str.length()) {
                    break;
                }
                c = str.charAt(i);
                if (c != '^') {
                    c = c == '?' ? 0x7f : (char) (Character.toUpperCase(c) & 0x1f);
                }
            }
            keySeq.append(c);
        }
        return keySeq.toString();
    }

    /**
     * Generates a collection of key sequences from a range specification.
     * <p>
     * This method takes a range specification like "a-z" or "\C-a-\C-z" and
     * returns a collection of all key sequences in that range.
     *
     * @param range the range specification
     * @return a collection of key sequences in the specified range, or null if the range is invalid
     */
    public static Collection<String> range(String range) {
        String[] keys = range.split("-");
        if (keys.length != 2) {
            return null;
        }
        keys[0] = translate(keys[0]);
        keys[1] = translate(keys[1]);
        if (keys[0].length() != keys[1].length()) {
            return null;
        }
        String pfx;
        if (keys[0].length() > 1) {
            pfx = keys[0].substring(0, keys[0].length() - 1);
            if (!keys[1].startsWith(pfx)) {
                return null;
            }
        } else {
            pfx = "";
        }
        char c0 = keys[0].charAt(keys[0].length() - 1);
        char c1 = keys[1].charAt(keys[1].length() - 1);
        if (c0 > c1) {
            return null;
        }
        Collection<String> seqs = new ArrayList<>();
        for (char c = c0; c <= c1; c++) {
            seqs.add(pfx + c);
        }
        return seqs;
    }

    /**
     * Returns the escape character as a string.
     *
     * @return the escape character ("\033")
     */
    public static String esc() {
        return "\033";
    }

    /**
     * Creates an Alt+key sequence for a single character.
     * <p>
     * This is equivalent to pressing the Alt key and a character key simultaneously.
     * Internally, this is represented as the escape character followed by the character.
     *
     * @param c the character to combine with Alt
     * @return the Alt+character key sequence
     */
    public static String alt(char c) {
        return "\033" + c;
    }

    /**
     * Creates an Alt+key sequence for a string.
     * <p>
     * This is equivalent to pressing the Alt key and typing a sequence of characters.
     * Internally, this is represented as the escape character followed by the string.
     *
     * @param c the string to combine with Alt
     * @return the Alt+string key sequence
     */
    public static String alt(String c) {
        return "\033" + c;
    }

    /**
     * Returns the delete character as a string.
     *
     * @return the delete character ("\177")
     */
    public static String del() {
        return "\177";
    }

    /**
     * Creates a Ctrl+key sequence for a character.
     * <p>
     * This is equivalent to pressing the Ctrl key and a character key simultaneously.
     * For example, Ctrl+A is represented as the character with code point 1.
     *
     * @param key the character to combine with Ctrl
     * @return the Ctrl+key sequence
     */
    public static String ctrl(char key) {
        return key == '?' ? del() : Character.toString((char) (Character.toUpperCase(key) & 0x1f));
    }

    /**
     * Returns the escape sequence for a terminal capability.
     * <p>
     * This method retrieves the escape sequence for special keys like arrow keys,
     * function keys, etc., based on the terminal's capabilities.
     *
     * @param terminal the terminal to query
     * @param capability the capability to retrieve
     * @return the escape sequence for the specified capability
     */
    public static String key(Terminal terminal, Capability capability) {
        return Curses.tputs(terminal.getStringCapability(capability));
    }

    /**
     * Comparator for sorting key sequences.
     * <p>
     * This comparator sorts key sequences first by length, then lexicographically.
     * It's useful for ensuring that longer key sequences are checked before their prefixes.
     */
    public static final Comparator<String> KEYSEQ_COMPARATOR = (s1, s2) -> {
        int len1 = s1.length();
        int len2 = s2.length();
        int lim = Math.min(len1, len2);
        int k = 0;
        while (k < lim) {
            char c1 = s1.charAt(k);
            char c2 = s2.charAt(k);
            if (c1 != c2) {
                int l = len1 - len2;
                return l != 0 ? l : c1 - c2;
            }
            k++;
        }
        return len1 - len2;
    };

    //
    // Methods
    //

    /**
     * Gets the binding for Unicode characters that don't have explicit bindings.
     * <p>
     * This is used as a fallback for characters that don't have specific bindings
     * in the keymap. Typically, this might be set to a function that inserts the
     * character into the line buffer.
     *
     * @return the binding for Unicode characters
     */
    public T getUnicode() {
        return unicode;
    }

    /**
     * Sets the binding for Unicode characters that don't have explicit bindings.
     *
     * @param unicode the binding for Unicode characters
     */
    public void setUnicode(T unicode) {
        this.unicode = unicode;
    }

    /**
     * Gets the binding for input sequences that don't match any known binding.
     * <p>
     * This is used as a fallback when an input sequence doesn't match any binding
     * in the keymap.
     *
     * @return the binding for non-matching sequences
     */
    public T getNomatch() {
        return nomatch;
    }

    /**
     * Sets the binding for input sequences that don't match any known binding.
     *
     * @param nomatch the binding for non-matching sequences
     */
    public void setNomatch(T nomatch) {
        this.nomatch = nomatch;
    }

    /**
     * Gets the timeout for ambiguous key bindings in milliseconds.
     * <p>
     * This timeout is used when a prefix of a multi-character binding is also bound
     * to an action. The reader will wait this amount of time after receiving the prefix
     * to determine if more characters are coming.
     *
     * @return the ambiguous binding timeout in milliseconds
     */
    public long getAmbiguousTimeout() {
        return ambiguousTimeout;
    }

    /**
     * Sets the timeout for ambiguous key bindings in milliseconds.
     *
     * @param ambiguousTimeout the ambiguous binding timeout in milliseconds
     */
    public void setAmbiguousTimeout(long ambiguousTimeout) {
        this.ambiguousTimeout = ambiguousTimeout;
    }

    /**
     * Gets the binding for the "another key" action.
     * <p>
     * This binding is returned when a key sequence doesn't match any binding
     * but is a prefix of a longer binding. It's typically used to implement
     * incremental search or other features that need to process each character
     * as it's typed.
     *
     * @return the "another key" binding
     */
    public T getAnotherKey() {
        return anotherKey;
    }

    /**
     * Returns a map of all bound key sequences and their associated bindings.
     * <p>
     * The map is sorted by key sequence using the {@link #KEYSEQ_COMPARATOR}.
     *
     * @return a map of bound key sequences to their bindings
     */
    public Map<String, T> getBoundKeys() {
        Map<String, T> bound = new TreeMap<>(KEYSEQ_COMPARATOR);
        doGetBoundKeys(this, "", bound);
        return bound;
    }

    @SuppressWarnings("unchecked")
    private static <T> void doGetBoundKeys(KeyMap<T> keyMap, String prefix, Map<String, T> bound) {
        if (keyMap.anotherKey != null) {
            bound.put(prefix, keyMap.anotherKey);
        }
        for (int c = 0; c < keyMap.mapping.length; c++) {
            if (keyMap.mapping[c] instanceof KeyMap) {
                doGetBoundKeys((KeyMap<T>) keyMap.mapping[c], prefix + (char) (c), bound);
            } else if (keyMap.mapping[c] != null) {
                bound.put(prefix + (char) (c), (T) keyMap.mapping[c]);
            }
        }
    }

    /**
     * Gets the binding for a key sequence, with information about remaining characters.
     * <p>
     * This method returns the binding for the given key sequence, if one exists.
     * It also provides information about how much of the key sequence was consumed
     * in the {@code remaining} parameter:
     * <ul>
     *   <li>If remaining[0] is -1, the entire sequence was consumed</li>
     *   <li>If remaining[0] is positive, that many characters at the end were not consumed</li>
     *   <li>If remaining[0] is 0, the sequence was consumed but no binding was found</li>
     * </ul>
     *
     * @param keySeq the key sequence to look up
     * @param remaining array of length at least 1 to receive information about remaining characters
     * @return the binding for the key sequence, or null if no binding was found
     */
    @SuppressWarnings("unchecked")
    public T getBound(CharSequence keySeq, int[] remaining) {
        remaining[0] = -1;
        if (keySeq != null && keySeq.length() > 0) {
            char c = keySeq.charAt(0);
            if (c >= mapping.length) {
                remaining[0] = Character.codePointCount(keySeq, 0, keySeq.length());
                return null;
            } else {
                if (mapping[c] instanceof KeyMap) {
                    CharSequence sub = keySeq.subSequence(1, keySeq.length());
                    return ((KeyMap<T>) mapping[c]).getBound(sub, remaining);
                } else if (mapping[c] != null) {
                    remaining[0] = keySeq.length() - 1;
                    return (T) mapping[c];
                } else {
                    remaining[0] = keySeq.length();
                    return anotherKey;
                }
            }
        } else {
            return anotherKey;
        }
    }

    /**
     * Gets the binding for a key sequence.
     * <p>
     * This method returns the binding for the given key sequence only if the entire
     * sequence is consumed. If the sequence is a prefix of a longer binding or doesn't
     * match any binding, null is returned.
     *
     * @param keySeq the key sequence to look up
     * @return the binding for the key sequence, or null if no binding was found or the sequence is a prefix
     */
    public T getBound(CharSequence keySeq) {
        int[] remaining = new int[1];
        T res = getBound(keySeq, remaining);
        return remaining[0] <= 0 ? res : null;
    }

    /**
     * Binds a function to a key sequence only if the key sequence is not already bound.
     * <p>
     * This method is useful for setting up default bindings that should not override
     * user-defined bindings.
     *
     * @param function the function to bind
     * @param keySeq the key sequence to bind to
     */
    public void bindIfNotBound(T function, CharSequence keySeq) {
        if (function != null && keySeq != null) {
            bind(this, keySeq, function, true);
        }
    }

    /**
     * Binds a function to multiple key sequences.
     * <p>
     * This is a convenience method that calls {@link #bind(Object, CharSequence)}
     * for each key sequence.
     *
     * @param function the function to bind
     * @param keySeqs the key sequences to bind to
     */
    public void bind(T function, CharSequence... keySeqs) {
        for (CharSequence keySeq : keySeqs) {
            bind(function, keySeq);
        }
    }

    /**
     * Binds a function to multiple key sequences.
     * <p>
     * This is a convenience method that calls {@link #bind(Object, CharSequence)}
     * for each key sequence in the iterable.
     *
     * @param function the function to bind
     * @param keySeqs the key sequences to bind to
     */
    public void bind(T function, Iterable<? extends CharSequence> keySeqs) {
        for (CharSequence keySeq : keySeqs) {
            bind(function, keySeq);
        }
    }

    /**
     * Binds a function to a key sequence.
     * <p>
     * If the function is null, the key sequence is unbound.
     *
     * @param function the function to bind, or null to unbind
     * @param keySeq the key sequence to bind to
     */
    public void bind(T function, CharSequence keySeq) {
        if (keySeq != null) {
            if (function == null) {
                unbind(keySeq);
            } else {
                bind(this, keySeq, function, false);
            }
        }
    }

    public void unbind(CharSequence... keySeqs) {
        for (CharSequence keySeq : keySeqs) {
            unbind(keySeq);
        }
    }

    /**
     * Unbinds a key sequence.
     * <p>
     * This removes any binding associated with the given key sequence.
     *
     * @param keySeq the key sequence to unbind
     */
    public void unbind(CharSequence keySeq) {
        if (keySeq != null) {
            unbind(this, keySeq);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T unbind(KeyMap<T> map, CharSequence keySeq) {
        KeyMap<T> prev = null;
        if (keySeq != null && keySeq.length() > 0) {
            for (int i = 0; i < keySeq.length() - 1; i++) {
                char c = keySeq.charAt(i);
                if (c >= map.mapping.length) {
                    return null;
                }
                if (!(map.mapping[c] instanceof KeyMap)) {
                    return null;
                }
                prev = map;
                map = (KeyMap<T>) map.mapping[c];
            }
            char c = keySeq.charAt(keySeq.length() - 1);
            if (c >= map.mapping.length) {
                return null;
            }
            if (map.mapping[c] instanceof KeyMap) {
                KeyMap<?> sub = (KeyMap) map.mapping[c];
                Object res = sub.anotherKey;
                sub.anotherKey = null;
                return (T) res;
            } else {
                Object res = map.mapping[c];
                map.mapping[c] = null;
                int nb = 0;
                for (int i = 0; i < map.mapping.length; i++) {
                    if (map.mapping[i] != null) {
                        nb++;
                    }
                }
                if (nb == 0 && prev != null) {
                    prev.mapping[keySeq.charAt(keySeq.length() - 2)] = map.anotherKey;
                }
                return (T) res;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> void bind(KeyMap<T> map, CharSequence keySeq, T function, boolean onlyIfNotBound) {
        if (keySeq != null && keySeq.length() > 0) {
            for (int i = 0; i < keySeq.length(); i++) {
                char c = keySeq.charAt(i);
                if (c >= map.mapping.length) {
                    return;
                }
                if (i < keySeq.length() - 1) {
                    if (!(map.mapping[c] instanceof KeyMap)) {
                        KeyMap<T> m = new KeyMap<>();
                        m.anotherKey = (T) map.mapping[c];
                        map.mapping[c] = m;
                    }
                    map = (KeyMap) map.mapping[c];
                } else {
                    if (map.mapping[c] instanceof KeyMap) {
                        ((KeyMap) map.mapping[c]).anotherKey = function;
                    } else {
                        Object op = map.mapping[c];
                        if (!onlyIfNotBound || op == null) {
                            map.mapping[c] = function;
                        }
                    }
                }
            }
        }
    }
}
