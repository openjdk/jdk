/*
 * Copyright (c) 1999, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.util;

import com.sun.tools.javac.jvm.ClassFile;
import com.sun.tools.javac.jvm.PoolConstant;
import com.sun.tools.javac.jvm.PoolReader;
import com.sun.tools.javac.util.DefinedBy.Api;

/** An abstraction for internal compiler strings.
 *
 *  <p>
 *  Names are stored in a {@link Name.Table}, and are unique within that table.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public abstract class Name implements javax.lang.model.element.Name, PoolConstant, Comparable<Name> {

    /**
     * The {@link Table} that contains this instance.
     */
    public final Table table;

    /**
     * Constructor.
     *
     * @param table the {@link Table} that will contain this instance
     */
    protected Name(Table table) {
        this.table = table;
    }

// PoolConstant

    @Override
    public final int poolTag() {
        return ClassFile.CONSTANT_Utf8;
    }

// CharSequence

    @Override
    public int length() {
        return toString().length();
    }

    /**
     *  {@inheritDoc}
     *
     *  <p>
     *  The implementation in {@link Name} invokes {@link Object#toString}
     *  on this instance and then delegates to {@link String#charAt}.
     */
    @Override
    public char charAt(int index) {
        return toString().charAt(index);
    }

    /**
     *  {@inheritDoc}
     *
     *  <p>
     *  The implementation in {@link Name} invokes {@link Object#toString}
     *  on this instance and then delegates to {@link String#subSequence}.
     */
    @Override
    public CharSequence subSequence(int start, int end) {
        return toString().subSequence(start, end);
    }

    @Override
    public abstract String toString();

// javax.lang.model.element.Name

    /**
     *  {@inheritDoc}
     *
     *  <p>
     *  The implementation in {@link Name} invokes {@link Object#toString}
     *  on this instance and then delegates to {@link String#equals}.
     */
    @DefinedBy(Api.LANGUAGE_MODEL)
    @Override
    public boolean contentEquals(CharSequence cs) {
        return toString().equals(cs.toString());
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    @Override
    public final boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != getClass())
            return false;
        final Name that = (Name)obj;
        return table == that.table && nameEquals(that);
    }

    @DefinedBy(Api.LANGUAGE_MODEL)
    @Override
    public abstract int hashCode();

    /**
     * Subclass check for equality.
     *
     * <p>
     * This method can assume that the given instance is the same type
     * as this instance and is associated to the same {@link Table}.
     */
    protected abstract boolean nameEquals(Name that);

// Comparable

    /** Order names lexicographically.
     *
     *  <p>
     *  The ordering defined by this method must match the ordering
     *  defined by the corresponding {@link #toString()} values.
     *  @see String#compareTo
     */
    @Override
    public int compareTo(Name name) {
        return toString().compareTo(name.toString());
    }

// Other methods

    /** Return the concatenation of this name and the given name.
     *  The given name must come from the same table as this one.
     */
    public Name append(Name name) {
        return table.fromString(toString() + name.toString());
    }

    /** Return the concatenation of this name, the given ASCII
     *  character, and the given name.
     *  The given name must come from the same table as this one.
     */
    public Name append(char c, Name name) {
        return table.fromString(toString() + c + name.toString());
    }

    /** Determine if this is the empty name.
     *  <p>
     *  The implementation in {@link Name} compares {@link #length()} to zero.
     */
    public boolean isEmpty() {
        return length() == 0;
    }

    /** Returns last occurrence of the given ASCII character in this name, -1 if not found.
     *  <p>
     *  The implementation in {@link Name} converts this instance to {@link String}
     *  and then delegates to {@link String#lastIndexOf(int)}.
     */
    public int lastIndexOfAscii(char ch) {
        return toString().lastIndexOf(ch);
    }

    /** Determine whether this name has the given Name as a prefix.
     *  <p>
     *  The implementation in {@link Name} converts this and the given instance
     *  to {@link String} and then delegates to {@link String#startsWith}.
     */
    public boolean startsWith(Name prefix) {
        return toString().startsWith(prefix.toString());
    }

    /** Returns the sub-name extending between two character positions.
     *  <p>
     *  The implementation in {@link Name} converts this instance to {@link String},
     *  delegates to {@link String#substring(int, int)} and then {@link Table#fromString}.
     *  @param start starting character offset, inclusive
     *  @param end ending character offset, exclusive
     *  @throws IndexOutOfBoundsException if bounds are out of range or invalid
     */
    public Name subName(int start, int end) {
        return table.fromString(toString().substring(start, end));
    }

    /** Returns the suffix of this name starting at the given offset.
     *  <p>
     *  The implementation in {@link Name} converts this instance to {@link String},
     *  delegates to {@link String#substring(int)}, and then to {@link Table#fromString}.
     *  @param off starting character offset
     *  @throws IndexOutOfBoundsException if {@code off} is out of range or invalid
     */
    public Name subName(int off) {
        return table.fromString(toString().substring(off));
    }

    /** Return the Modified UTF-8 encoding of this name.
     *  <p>
     *  The implementation in {@link Name} populates a new byte array of length
     *  {@link #getUtf8Length} with data from {@link #getUtf8Bytes}.
     */
    public byte[] toUtf() {
        byte[] bs = new byte[getUtf8Length()];
        getUtf8Bytes(bs, 0);
        return bs;
    }

    /** Get the length of the Modified UTF-8 encoding of this name.
     */
    public abstract int getUtf8Length();

    /** Write the Modified UTF-8 encoding of this name into the given
     *  buffer starting at the specified offset.
     */
    public abstract void getUtf8Bytes(byte buf[], int off);

// Mapping

    /** Maps the Modified UTF-8 encoding of a {@link Name} to something.
     */
    @FunctionalInterface
    public interface NameMapper<X> {
        X map(byte[] bytes, int offset, int len);
    }

    /** Decode this name's Modified UTF-8 encoding into something.
     */
    public <X> X map(NameMapper<X> mapper) {
        byte[] buf = toUtf();
        return mapper.map(buf, 0, buf.length);
    }

// Table

    /** An abstraction for the hash table used to create unique {@link Name} instances.
     */
    public abstract static class Table {

        /** Standard name table.
         */
        public final Names names;

        protected Table(Names names) {
            this.names = names;
        }

        /** Get the unique {@link Name} corresponding to the given characters.
         *  @param buf character buffer
         *  @param off starting offset
         *  @param len number of characters
         *  @return the corresponding {@link Name}
         */
        public abstract Name fromChars(char[] cs, int off, int len);

        /** Get the unique {@link Name} corresponding to the given string.
         *  <p>
         *  The implementation in {@link Table} delegates to {@link String#toCharArray}
         *  and then {@link #fromChars}.
         *  @param s character string
         */
        public Name fromString(String s) {
            char[] cs = s.toCharArray();
            return fromChars(cs, 0, cs.length);
        }

        /** Get the unique {@link Name} corresponding to the given Modified UTF-8 encoding.
         *  <p>
         *  The implementation in {@link Table} delegates to {@link #fromUtf(byte[], int, int)}.
         *  @param buf character string
         *  @return the corresponding {@link Name}
         *  @throws IllegalArgumentException if the data is not valid Modified UTF-8
         */
        public Name fromUtf(byte[] cs) throws InvalidUtfException {
            return fromUtf(cs, 0, cs.length, Convert.Validation.STRICT);
        }

        /** Get the unique {@link Name} corresponding to the given Modified UTF-8 encoding.
         *  @param buf character buffer
         *  @param off starting offset
         *  @param len number of bytes
         *  @return the corresponding {@link Name}
         *  @throws IllegalArgumentException if the data is not valid Modified UTF-8
         *  @throws InvalidUtfException if invalid Modified UTF-8 is encountered
         */
        public abstract Name fromUtf(byte[] cs, int start, int len, Convert.Validation validation)
            throws InvalidUtfException;

        /** Release any resources used by this table.
         */
        public abstract void dispose();
    }
}
