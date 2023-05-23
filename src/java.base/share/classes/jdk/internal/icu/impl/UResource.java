// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2015-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */
package jdk.internal.icu.impl;

import java.nio.ByteBuffer;

import jdk.internal.icu.util.UResourceBundle;
import jdk.internal.icu.util.UResourceTypeMismatchException;

// Class UResource is named consistently with the public class UResourceBundle,
// in case we want to make it public at some point.

/**
 * ICU resource bundle key and value types.
 */
public final class UResource {
    /**
     * Represents a resource bundle item's key string.
     * Avoids object creations as much as possible.
     * Mutable, not thread-safe.
     * For permanent storage, use clone() or toString().
     */
    public static final class Key implements CharSequence, Cloneable, Comparable<Key> {
        // Stores a reference to the resource bundle key string bytes array,
        // with an offset of the key, to avoid creating a String object
        // until one is really needed.
        // Alternatively, we could try to always just get the key String object,
        // and cache it in the reader, and see if that performs better or worse.
        private byte[] bytes;
        private int offset;
        private int length;
        private String s;

        /**
         * Constructs an empty resource key string object.
         */
        public Key() {
            s = "";
        }

        /**
         * Constructs a resource key object equal to the given string.
         */
        public Key(String s) {
            setString(s);
        }

        private Key(byte[] keyBytes, int keyOffset, int keyLength) {
            bytes = keyBytes;
            offset = keyOffset;
            length = keyLength;
        }

        /**
         * Mutates this key for a new NUL-terminated resource key string.
         * The corresponding ASCII-character bytes are not copied and
         * must not be changed during the lifetime of this key
         * (or until the next setBytes() call)
         * and lifetimes of subSequences created from this key.
         *
         * @param keyBytes new key string byte array
         * @param keyOffset new key string offset
         */
        public Key setBytes(byte[] keyBytes, int keyOffset) {
            bytes = keyBytes;
            offset = keyOffset;
            for (length = 0; keyBytes[keyOffset + length] != 0; ++length) {}
            s = null;
            return this;
        }

        /**
         * Mutates this key to an empty resource key string.
         */
        public Key setToEmpty() {
            bytes = null;
            offset = length = 0;
            s = "";
            return this;
        }

        /**
         * Mutates this key to be equal to the given string.
         */
        public Key setString(String s) {
            if (s.isEmpty()) {
                setToEmpty();
            } else {
                bytes = new byte[s.length()];
                offset = 0;
                length = s.length();
                for (int i = 0; i < length; ++i) {
                    char c = s.charAt(i);
                    if (c <= 0x7f) {
                        bytes[i] = (byte)c;
                    } else {
                        throw new IllegalArgumentException('\"' + s + "\" is not an ASCII string");
                    }
                }
                this.s = s;
            }
            return this;
        }

        /**
         * {@inheritDoc}
         * Does not clone the byte array.
         */
        @Override
        public Key clone() {
            try {
                return (Key)super.clone();
            } catch (CloneNotSupportedException cannotOccur) {
                return null;
            }
        }

        @Override
        public char charAt(int i) {
            assert(0 <= i && i < length);
            return (char)bytes[offset + i];
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public Key subSequence(int start, int end) {
            assert(0 <= start && start < length);
            assert(start <= end && end <= length);
            return new Key(bytes, offset + start, end - start);
        }

        /**
         * Creates/caches/returns this resource key string as a Java String.
         */
        @Override
        public String toString() {
            if (s == null) {
                s = internalSubString(0, length);
            }
            return s;
        }

        private String internalSubString(int start, int end) {
            StringBuilder sb = new StringBuilder(end - start);
            for (int i = start; i < end; ++i) {
                sb.append((char)bytes[offset + i]);
            }
            return sb.toString();
        }

        /**
         * Creates a new Java String for a sub-sequence of this resource key string.
         */
        public String substring(int start) {
            assert(0 <= start && start < length);
            return internalSubString(start, length);
        }

        /**
         * Creates a new Java String for a sub-sequence of this resource key string.
         */
        public String substring(int start, int end) {
            assert(0 <= start && start < length);
            assert(start <= end && end <= length);
            return internalSubString(start, end);
        }

        private boolean regionMatches(byte[] otherBytes, int otherOffset, int n) {
            for (int i = 0; i < n; ++i) {
                if (bytes[offset + i] != otherBytes[otherOffset + i]) {
                    return false;
                }
            }
            return true;
        }

        private boolean regionMatches(int start, CharSequence cs, int n) {
            for (int i = 0; i < n; ++i) {
                if (bytes[offset + start + i] != cs.charAt(i)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            } else if (this == other) {
                return true;
            } else if (other instanceof Key) {
                Key otherKey = (Key)other;
                return length == otherKey.length &&
                        regionMatches(otherKey.bytes, otherKey.offset, length);
            } else {
                return false;
            }
        }

        public boolean contentEquals(CharSequence cs) {
            if (cs == null) {
                return false;
            }
            return this == cs || (cs.length() == length && regionMatches(0, cs, length));
        }

        public boolean startsWith(CharSequence cs) {
            int csLength = cs.length();
            return csLength <= length && regionMatches(0, cs, csLength);
        }

        public boolean endsWith(CharSequence cs) {
            int csLength = cs.length();
            return csLength <= length && regionMatches(length - csLength, cs, csLength);
        }

        /**
         * @return true if the substring of this key starting from the offset
         *         contains the same characters as the other sequence.
         */
        public boolean regionMatches(int start, CharSequence cs) {
            int csLength = cs.length();
            return csLength == (length - start) && regionMatches(start, cs, csLength);
        }

        @Override
        public int hashCode() {
            // Never return s.hashCode(), so that
            // Key.hashCode() is the same whether we have cached s or not.
            if (length == 0) {
                return 0;
            }

            int h = bytes[offset];
            for (int i = 1; i < length; ++i) {
                h = 37 * h + bytes[offset];
            }
            return h;
        }

        @Override
        public int compareTo(Key other) {
            return compareTo((CharSequence)other);
        }

        public int compareTo(CharSequence cs) {
            int csLength = cs.length();
            int minLength = length <= csLength ? length : csLength;
            for (int i = 0; i < minLength; ++i) {
                int diff = charAt(i) - cs.charAt(i);
                if (diff != 0) {
                    return diff;
                }
            }
            return length - csLength;
        }
    }

    /**
     * Interface for iterating over a resource bundle array resource.
     * Does not use Java Iterator to reduce object creations.
     */
    public interface Array {
        /**
         * @return The number of items in the array resource.
         */
        public int getSize();
        /**
         * @param i Array item index.
         * @param value Output-only, receives the value of the i'th item.
         * @return true if i is non-negative and less than getSize().
         */
        public boolean getValue(int i, Value value);
    }

    /**
     * Interface for iterating over a resource bundle table resource.
     * Does not use Java Iterator to reduce object creations.
     */
    public interface Table {
        /**
         * @return The number of items in the table resource.
         */
        public int getSize();
        /**
         * @param i Table item index.
         * @param key Output-only, receives the key of the i'th item.
         * @param value Output-only, receives the value of the i'th item.
         * @return true if i is non-negative and less than getSize().
         */
        public boolean getKeyAndValue(int i, Key key, Value value);
        /**
         * @param key Key string to find in the table.
         * @param value Output-only, receives the value of the item with that key.
         * @return true if the table contains the key.
         */
        public boolean findValue(CharSequence key, Value value);
    }

    /**
     * Represents a resource bundle item's value.
     * Avoids object creations as much as possible.
     * Mutable, not thread-safe.
     */
    public static abstract class Value {
        protected Value() {}

        /**
         * @return ICU resource type like {@link UResourceBundle#getType()},
         *     for example, {@link UResourceBundle#STRING}
         */
        public abstract int getType();

        /**
         * @see UResourceBundle#getString()
         * @throws UResourceTypeMismatchException if this is not a string resource
         */
        public abstract String getString();

        /**
         * @throws UResourceTypeMismatchException if this is not an alias resource
         */
        public abstract String getAliasString();

        /**
         * @see UResourceBundle#getInt()
         * @throws UResourceTypeMismatchException if this is not an integer resource
         */
        public abstract int getInt();

        /**
         * @see UResourceBundle#getUInt()
         * @throws UResourceTypeMismatchException if this is not an integer resource
         */
        public abstract int getUInt();

        /**
         * @see UResourceBundle#getIntVector()
         * @throws UResourceTypeMismatchException if this is not an intvector resource
         */
        public abstract int[] getIntVector();

        /**
         * @see UResourceBundle#getBinary()
         * @throws UResourceTypeMismatchException if this is not a binary-blob resource
         */
        public abstract ByteBuffer getBinary();

        /**
         * @throws UResourceTypeMismatchException if this is not an array resource
         */
        public abstract Array getArray();

        /**
         * @throws UResourceTypeMismatchException if this is not a table resource
         */
        public abstract Table getTable();

        /**
         * Is this a no-fallback/no-inheritance marker string?
         * Such a marker is used for CLDR no-fallback data values of "\u2205\u2205\u2205"
         * when enumerating tables with fallback from the specific resource bundle to root.
         *
         * @return true if this is a no-inheritance marker string
         */
        public abstract boolean isNoInheritanceMarker();

        /**
         * @return the array of strings in this array resource.
         * @see UResourceBundle#getStringArray()
         * @throws UResourceTypeMismatchException if this is not an array resource
         *     or if any of the array items is not a string
         */
        public abstract String[] getStringArray();

        /**
         * Same as
         * <pre>
         * if (getType() == STRING) {
         *     return new String[] { getString(); }
         * } else {
         *     return getStringArray();
         * }
         * </pre>
         *
         * @see #getString()
         * @see #getStringArray()
         * @throws UResourceTypeMismatchException if this is
         *     neither a string resource nor an array resource containing strings
         */
        public abstract String[] getStringArrayOrStringAsArray();

        /**
         * Same as
         * <pre>
         * if (getType() == STRING) {
         *     return getString();
         * } else {
         *     return getStringArray()[0];
         * }
         * </pre>
         *
         * @see #getString()
         * @see #getStringArray()
         * @throws UResourceTypeMismatchException if this is
         *     neither a string resource nor an array resource containing strings
         */
        public abstract String getStringOrFirstOfArray();

        /**
         * Only for debugging.
         */
        @Override
        public String toString() {
            switch(getType()) {
            case UResourceBundle.STRING:
                return getString();
            case UResourceBundle.INT:
                return Integer.toString(getInt());
            case UResourceBundle.INT_VECTOR:
                int[] iv = getIntVector();
                StringBuilder sb = new StringBuilder("[");
                sb.append(iv.length).append("]{");
                if (iv.length != 0) {
                    sb.append(iv[0]);
                    for (int i = 1; i < iv.length; ++i) {
                        sb.append(", ").append(iv[i]);
                    }
                }
                return sb.append('}').toString();
            case UResourceBundle.BINARY:
                return "(binary blob)";
            case UResourceBundle.ARRAY:
                return "(array)";
            case UResourceBundle.TABLE:
                return "(table)";
            default:  // should not occur
                return "???";
            }
        }
    }

    /**
     * Sink for ICU resource bundle contents.
     */
    public static abstract class Sink {
        /**
         * Called once for each bundle (child-parent-...-root).
         * The value is normally an array or table resource,
         * and implementations of this method normally iterate over the
         * tree of resource items stored there.
         *
         * @param key Initially the key string of the enumeration-start resource.
         *     Empty if the enumeration starts at the top level of the bundle.
         *     Reuse for output values from Array and Table getters.
         * @param value Call getArray() or getTable() as appropriate.
         *     Then reuse for output values from Array and Table getters.
         * @param noFallback true if the bundle has no parent;
         *     that is, its top-level table has the nofallback attribute,
         *     or it is the root bundle of a locale tree.
         */
        public abstract void put(Key key, Value value, boolean noFallback);
    }
}
