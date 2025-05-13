/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.math.FloatToDecimal;
import jdk.internal.util.DecimalDigits;

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Spliterator;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import jdk.internal.util.ArraysSupport;
import jdk.internal.util.Preconditions;

import static java.lang.String.COMPACT_STRINGS;
import static java.lang.String.UTF16;
import static java.lang.String.LATIN1;
import static java.lang.String.checkIndex;
import static java.lang.String.checkOffset;

/**
 * A mutable sequence of characters.
 * <p>
 * Implements a modifiable string. At any point in time it contains some
 * particular sequence of characters, but the length and content of the
 * sequence can be changed through certain method calls.
 *
 * <p>Unless otherwise noted, passing a {@code null} argument to a constructor
 * or method in this class will cause a {@link NullPointerException} to be
 * thrown.
 *
 * @author      Michael McCloskey
 * @author      Martin Buchholz
 * @author      Ulf Zibis
 * @since       1.5
 */
abstract sealed class AbstractStringBuilder implements Appendable, CharSequence
    permits StringBuilder, StringBuffer {
    /**
     * The value is used for character storage.
     */
    byte[] value;

    /**
     * The id of the encoding used to encode the bytes in {@code value}.
     */
    byte coder;

    /**
     *  The attribute indicates {@code value} might be compressible to LATIN1 if it is UTF16-encoded.
     *  An inflated byte array becomes compressible only when those non-latin1 chars are deleted.
     *  We simply set this attribute in all methods which may delete chars. Therefore, there are
     *  false positives. Subclasses and String need to handle it properly.
     */
    boolean maybeLatin1;

    /**
     * The count is the number of characters used.
     */
    int count;

    private static final byte[] EMPTYVALUE = new byte[0];

    /**
     * This no-arg constructor is necessary for serialization of subclasses.
     */
    AbstractStringBuilder() {
        value = EMPTYVALUE;
    }

    /**
     * Creates an AbstractStringBuilder of the specified capacity.
     */
    AbstractStringBuilder(int capacity) {
        if (COMPACT_STRINGS) {
            value = new byte[capacity];
            coder = LATIN1;
        } else {
            value = StringUTF16.newBytesFor(capacity);
            coder = UTF16;
        }
    }

    /**
     * Constructs an AbstractStringBuilder that contains the same characters
     * as the specified {@code String}. The initial capacity of
     * the string builder is {@code 16} plus the length of the
     * {@code String} argument.
     *
     * @param      str   the string to copy.
     */
    AbstractStringBuilder(String str) {
        int length = str.length();
        int capacity = (length < Integer.MAX_VALUE - 16)
                ? length + 16 : Integer.MAX_VALUE;
        final byte initCoder = str.coder();
        coder = initCoder;
        value = (isLatin1(coder))
                ? new byte[capacity] : StringUTF16.newBytesFor(capacity);
        append(str);
    }

    /**
     * Constructs an AbstractStringBuilder that contains the same characters
     * as the specified {@code CharSequence}. The initial capacity of
     * the string builder is {@code 16} plus the length of the
     * {@code CharSequence} argument.
     * <p>
     * The contents are unspecified if the {@code CharSequence}
     * is modified during string construction.
     *
     * @param      seq   the sequence to copy.
     */
    AbstractStringBuilder(CharSequence seq) {
        int length = seq.length();
        if (length < 0) {
            throw new NegativeArraySizeException("Negative length: " + length);
        }
        int capacity = (length < Integer.MAX_VALUE - 16)
                ? length + 16 : Integer.MAX_VALUE;

        final byte initCoder;
        if (COMPACT_STRINGS) {
            if (seq instanceof AbstractStringBuilder asb) {
                initCoder = asb.getCoder();
                maybeLatin1 |= asb.maybeLatin1;
            } else if (seq instanceof String s) {
                initCoder = s.coder();
            } else {
                initCoder = LATIN1;
            }
        } else {
            initCoder = UTF16;
        }

        coder = initCoder;
        value = (initCoder == LATIN1)
                ? new byte[capacity] : StringUTF16.newBytesFor(capacity);
        append(seq);
    }

    /**
     * Compares the objects of two AbstractStringBuilder implementations lexicographically.
     *
     * @since 11
     */
    int compareTo(AbstractStringBuilder another) {
        if (this == another) {
            return 0;
        }

        byte[] val1 = value;
        byte[] val2 = another.value;
        int count1 = this.count;
        int count2 = another.count;

        byte coder = this.coder;
        if (coder == another.coder) {
            return isLatin1(coder) ? StringLatin1.compareTo(val1, val2, count1, count2)
                    : StringUTF16.compareTo(val1, val2, count1, count2);
        }
        return isLatin1(coder) ? StringLatin1.compareToUTF16(val1, val2, count1, count2)
                : StringUTF16.compareToLatin1(val1, val2, count1, count2);
    }

    /**
     * Returns the length (character count).
     *
     * @return  the length of the sequence of characters currently
     *          represented by this object
     */
    @Override
    public int length() {
        return count;
    }

    /**
     * Returns the current capacity. The capacity is the number of characters
     * that can be stored (including already written characters), beyond which
     * an allocation will occur.
     *
     * @return  the current capacity
     */
    public int capacity() {
        return value.length >> coder;
    }

    /**
     * Ensures that the capacity is at least equal to the specified minimum.
     * If the current capacity is less than the argument, then a new internal
     * array is allocated with greater capacity. The new capacity is the
     * larger of:
     * <ul>
     * <li>The {@code minimumCapacity} argument.
     * <li>Twice the old capacity, plus {@code 2}.
     * </ul>
     * If the {@code minimumCapacity} argument is nonpositive, this
     * method takes no action and simply returns.
     * Note that subsequent operations on this object can reduce the
     * actual capacity below that requested here.
     *
     * @param   minimumCapacity   the minimum desired capacity.
     */
    public void ensureCapacity(int minimumCapacity) {
        if (minimumCapacity > 0) {
            value = ensureCapacitySameCoder(value, coder, minimumCapacity);
        }
    }

    /**
     * {@return true if the byte array should be replaced due to increased capacity or coder change}
     * <ul>
     *     <li>The new coder is different than the old coder
     *     <li>The new length is greater than to the current length
     *     <li>The new length is negative, as it might have overflowed due to an increment
     * </ul>
     *
     * @param value       a byte array
     * @param coder       old coder
     * @param newCapacity new capacity in characters
     * @param newCoder    new coder
     */
    private static boolean needsNewBuffer(byte[] value, byte coder, int newCapacity, byte newCoder) {
        long newLength = (long) newCapacity << newCoder;
        return coder != newCoder || newLength > value.length || 0 > newLength;
    }

    /**
     * {@return the value, with the requested coder, in a buffer with at least the minimum capacity}
     * If the coder matches and there is enough room, the same buffer is returned.
     * Otherwise, a new buffer is allocated and the string is copied or inflated to match the new coder.
     * For positive values of {@code minimumCapacity}, this method
     * behaves like the public {@linkplain #ensureCapacity}, however it is never synchronized.
     * If {@code minimumCapacity} is non-positive due to numeric
     * overflow, this method throws {@code OutOfMemoryError}.
     * @param value the current buffer
     * @param coder of the current buffer
     * @param count the count of chars in the current buffer
     * @param minimumCapacity the new minimum capacity
     * @param newCoder the desired new coder
     */
    private static byte[] ensureCapacityNewCoder(byte[] value, byte coder, int count,
                                                 int minimumCapacity, byte newCoder) {
        assert coder == newCoder || newCoder == UTF16 : "bad new coder UTF16 -> LATIN1";
        // overflow-conscious code
        // Compute the new larger size if growth is requested, otherwise keep the capacity the same
        int oldCapacity = value.length >> coder;
        int growth = minimumCapacity - oldCapacity;
        int newCapacity = (growth <= 0)
                ? oldCapacity               // Do not reduce capacity even if requested
                : newCapacity(value, newCoder, minimumCapacity);
        assert count <= newCapacity : "count exceeds new capacity";

        if (coder == newCoder) {
            if (newCapacity > oldCapacity) {
                // copy all bytes to new larger buffer
                value = Arrays.copyOf(value, newCapacity << newCoder);
            }
            return value;
        } else {
            // inflate (and grow if additional length is requested)
            byte[] newValue = StringUTF16.newBytesFor(newCapacity);
            StringLatin1.inflate(value, 0, newValue, 0, count);
            return newValue;
        }
    }

    /**
     * {@return the value buffer sufficient to hold the minimumCapactity and a copy of the contents}
     * There is no change to the coder.
     * The current value buffer is returned if the size is already sufficient.
     * For positive values of {@code minimumCapacity}, this method
     * behaves like {@code ensureCapacity}, however it is never synchronized.
     * If {@code minimumCapacity} is non-positive due to numeric
     * overflow, this method throws {@code OutOfMemoryError}.
     * @param value the current buffer
     * @param coder of the current buffer
     * @param minimumCapacity the new minimum capacity
     */
    private static byte[] ensureCapacitySameCoder(byte[] value, byte coder, int minimumCapacity) {
        // overflow-conscious code
        int oldCapacity = value.length >> coder;
        if (minimumCapacity - oldCapacity > 0) {
            value = Arrays.copyOf(value,
                    newCapacity(value, coder, minimumCapacity) << coder);
        }
        return value;
    }

    /**
     * Inflates the internal 8-bit latin1 storage to 16-bit <hi=0, low> pair storage.
     * @param value the current byte array buffer
     * @param count the number of latin1 characters to convert to UTF16
     * @return the new buffer, the caller is responsible for updates to the coder
     */
    private static byte[] inflateToUTF16(byte[] value, int count) {
        assert count <= value.length : "count > value.length";
        byte[] newValue = StringUTF16.newBytesFor(value.length);
        StringLatin1.inflate(value, 0, newValue, 0, count);
        return newValue;
    }

    /**
     * Returns a capacity at least as large as the given minimum capacity.
     * Returns the current capacity increased by the current length + 2 if that suffices.
     * Will not return a capacity greater than {@code (SOFT_MAX_ARRAY_LENGTH >> coder)}
     * unless the given minimum capacity is greater than that.
     *
     * @param value the current buffer
     * @param coder of the current buffer
     * @param  minCapacity the desired minimum capacity
     * @throws OutOfMemoryError if minCapacity is less than zero or
     *         greater than (Integer.MAX_VALUE >> coder)
     */
    private static int newCapacity(byte[] value, byte coder, int minCapacity) {
        int oldLength = value.length;
        int newLength = minCapacity << coder;
        int growth = newLength - oldLength;
        int length = ArraysSupport.newLength(oldLength, growth, oldLength + (2 << coder));
        if (length == Integer.MAX_VALUE) {
            throw new OutOfMemoryError("Required length exceeds implementation limit");
        }
        return length >> coder;
    }

    /**
     * Attempts to reduce storage used for the character sequence.
     * If the buffer is larger than necessary to hold its current sequence of
     * characters, then it may be resized to become more space efficient.
     * Calling this method may, but is not required to, affect the value
     * returned by a subsequent call to the {@link #capacity()} method.
     */
    public void trimToSize() {
        int length = count << coder;
        if (length < value.length) {
            value = Arrays.copyOf(value, length);
        }
    }

    /**
     * Sets the length of the character sequence.
     * The sequence is changed to a new character sequence
     * whose length is specified by the argument. For every nonnegative
     * index <i>k</i> less than {@code newLength}, the character at
     * index <i>k</i> in the new character sequence is the same as the
     * character at index <i>k</i> in the old sequence if <i>k</i> is less
     * than the length of the old character sequence; otherwise, it is the
     * null character {@code '\u005Cu0000'}.
     *
     * In other words, if the {@code newLength} argument is less than
     * the current length, the length is changed to the specified length.
     * <p>
     * If the {@code newLength} argument is greater than or equal
     * to the current length, sufficient null characters
     * ({@code '\u005Cu0000'}) are appended so that
     * length becomes the {@code newLength} argument.
     * <p>
     * The {@code newLength} argument must be greater than or equal
     * to {@code 0}.
     *
     * @param      newLength   the new length
     * @throws     IndexOutOfBoundsException  if the
     *               {@code newLength} argument is negative.
     */
    public void setLength(int newLength) {
        if (newLength < 0) {
            throw new StringIndexOutOfBoundsException(newLength);
        }
        byte coder = this.coder;
        int count = this.count;
        byte[] value = ensureCapacitySameCoder(this.value, coder, newLength);
        if (count < newLength) {
            Arrays.fill(value, count << coder, newLength << coder, (byte)0);
        } else if (count > newLength) {
            maybeLatin1 = true;
        }
        this.count = newLength;
        this.value = value;
    }

    /**
     * Returns the {@code char} value in this sequence at the specified index.
     * The first {@code char} value is at index {@code 0}, the next at index
     * {@code 1}, and so on, as in array indexing.
     * <p>
     * The index argument must be greater than or equal to
     * {@code 0}, and less than the length of this sequence.
     *
     * <p>If the {@code char} value specified by the index is a
     * <a href="Character.html#unicode">surrogate</a>, the surrogate
     * value is returned.
     *
     * @param      index   the index of the desired {@code char} value.
     * @return     the {@code char} value at the specified index.
     * @throws     IndexOutOfBoundsException  if {@code index} is
     *             negative or greater than or equal to {@code length()}.
     */
    @Override
    public char charAt(int index) {
        byte coder = this.coder;
        byte[] value = this.value;
        // Count should be less than or equal to capacity (racy reads and writes can produce inconsistent values)
        int count = Math.min(this.count, value.length >> coder);
        checkIndex(index, count);
        if (isLatin1(coder)) {
            return (char)(value[index] & 0xff);
        }
        return StringUTF16.getChar(value, index);
    }

    /**
     * Returns the character (Unicode code point) at the specified
     * index. The index refers to {@code char} values
     * (Unicode code units) and ranges from {@code 0} to
     * {@link #length()}{@code  - 1}.
     *
     * <p> If the {@code char} value specified at the given index
     * is in the high-surrogate range, the following index is less
     * than the length of this sequence, and the
     * {@code char} value at the following index is in the
     * low-surrogate range, then the supplementary code point
     * corresponding to this surrogate pair is returned. Otherwise,
     * the {@code char} value at the given index is returned.
     *
     * @param      index the index to the {@code char} values
     * @return     the code point value of the character at the
     *             {@code index}
     * @throws     IndexOutOfBoundsException  if the {@code index}
     *             argument is negative or not less than the length of this
     *             sequence.
     */
    public int codePointAt(int index) {
        byte coder = this.coder;
        int count = this.count;
        byte[] value = this.value;
        checkIndex(index, count);
        if (isLatin1(coder)) {
            return value[index] & 0xff;
        }
        return StringUTF16.codePointAtSB(value, index, count);
    }

    /**
     * Returns the character (Unicode code point) before the specified
     * index. The index refers to {@code char} values
     * (Unicode code units) and ranges from {@code 1} to {@link
     * #length()}.
     *
     * <p> If the {@code char} value at {@code (index - 1)}
     * is in the low-surrogate range, {@code (index - 2)} is not
     * negative, and the {@code char} value at {@code (index -
     * 2)} is in the high-surrogate range, then the
     * supplementary code point value of the surrogate pair is
     * returned. If the {@code char} value at {@code index -
     * 1} is an unpaired low-surrogate or a high-surrogate, the
     * surrogate value is returned.
     *
     * @param     index the index following the code point that should be returned
     * @return    the Unicode code point value before the given index.
     * @throws    IndexOutOfBoundsException if the {@code index}
     *            argument is less than 1 or greater than the length
     *            of this sequence.
     */
    public int codePointBefore(int index) {
        byte coder = this.coder;
        int count = this.count;
        byte[] value = this.value;
        int i = index - 1;
        checkIndex(i, count);
        if (isLatin1(coder)) {
            return value[i] & 0xff;
        }
        return StringUTF16.codePointBeforeSB(value, index);
    }

    /**
     * Returns the number of Unicode code points in the specified text
     * range of this sequence. The text range begins at the specified
     * {@code beginIndex} and extends to the {@code char} at
     * index {@code endIndex - 1}. Thus the length (in
     * {@code char}s) of the text range is
     * {@code endIndex-beginIndex}. Unpaired surrogates within
     * this sequence count as one code point each.
     *
     * @param beginIndex the index to the first {@code char} of
     * the text range.
     * @param endIndex the index after the last {@code char} of
     * the text range.
     * @return the number of Unicode code points in the specified text
     * range
     * @throws    IndexOutOfBoundsException if the
     * {@code beginIndex} is negative, or {@code endIndex}
     * is larger than the length of this sequence, or
     * {@code beginIndex} is larger than {@code endIndex}.
     */
    public int codePointCount(int beginIndex, int endIndex) {
        byte coder = this.coder;
        int count = this.count;
        byte[] value = this.value;
        Preconditions.checkFromToIndex(beginIndex, endIndex, count, null);
        if (isLatin1(coder)) {
            return endIndex - beginIndex;
        }
        return StringUTF16.codePointCountSB(value, beginIndex, endIndex);
    }

    /**
     * Returns the index within this sequence that is offset from the
     * given {@code index} by {@code codePointOffset} code
     * points. Unpaired surrogates within the text range given by
     * {@code index} and {@code codePointOffset} count as
     * one code point each.
     *
     * @param index the index to be offset
     * @param codePointOffset the offset in code points
     * @return the index within this sequence
     * @throws    IndexOutOfBoundsException if {@code index}
     *   is negative or larger than the length of this sequence,
     *   or if {@code codePointOffset} is positive and the subsequence
     *   starting with {@code index} has fewer than
     *   {@code codePointOffset} code points,
     *   or if {@code codePointOffset} is negative and the subsequence
     *   before {@code index} has fewer than the absolute value of
     *   {@code codePointOffset} code points.
     */
    public int offsetByCodePoints(int index, int codePointOffset) {
        if (index < 0 || index > count) {
            throw new IndexOutOfBoundsException();
        }
        return Character.offsetByCodePoints(this,
                                            index, codePointOffset);
    }

    /**
     * {@inheritDoc CharSequence}
     */
    @Override
    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin)
    {
        Preconditions.checkFromToIndex(srcBegin, srcEnd, count, Preconditions.SIOOBE_FORMATTER);  // compatible to old version
        int n = srcEnd - srcBegin;
        Preconditions.checkFromToIndex(dstBegin, dstBegin + n, dst.length, Preconditions.IOOBE_FORMATTER);
        if (isLatin1(coder)) {
            StringLatin1.getChars(value, srcBegin, srcEnd, dst, dstBegin);
        } else {
            StringUTF16.getChars(value, srcBegin, srcEnd, dst, dstBegin);
        }
    }

    /**
     * The character at the specified index is set to {@code ch}. This
     * sequence is altered to represent a new character sequence that is
     * identical to the old character sequence, except that it contains the
     * character {@code ch} at position {@code index}.
     * <p>
     * The index argument must be greater than or equal to
     * {@code 0}, and less than the length of this sequence.
     *
     * @param      index   the index of the character to modify.
     * @param      ch      the new character.
     * @throws     IndexOutOfBoundsException  if {@code index} is
     *             negative or greater than or equal to {@code length()}.
     */
    public void setCharAt(int index, char ch) {
        byte coder = this.coder;
        int count = this.count;
        checkIndex(index, count);
        byte[] value  = this.value;
        byte newCoder = (byte)(coder | StringLatin1.coderFromChar(ch));
        if (needsNewBuffer(value, coder, count, newCoder)) {
            this.value = value = ensureCapacityNewCoder(value, coder, count, count, newCoder);
            this.coder = coder = newCoder;
        }
        if (isLatin1(coder)) {
            value[index] = (byte)ch;
        } else {
            StringUTF16.putChar(value, index, ch);
            maybeLatin1 = true;
        }
    }

    /**
     * Appends the string representation of the {@code Object} argument.
     * <p>
     * The overall effect is exactly as if the argument were converted
     * to a string by the method {@link String#valueOf(Object)},
     * and the characters of that string were then
     * {@link #append(String) appended} to this character sequence.
     *
     * @param   obj   an {@code Object}.
     * @return  a reference to this object.
     */
    public AbstractStringBuilder append(Object obj) {
        return append(String.valueOf(obj));
    }

    /**
     * Appends the specified string to this character sequence.
     * <p>
     * The characters of the {@code String} argument are appended, in
     * order, increasing the length of this sequence by the length of the
     * argument. If {@code str} is {@code null}, then the four
     * characters {@code "null"} are appended.
     * <p>
     * Let <i>n</i> be the length of this character sequence just prior to
     * execution of the {@code append} method. Then the character at
     * index <i>k</i> in the new character sequence is equal to the character
     * at index <i>k</i> in the old character sequence, if <i>k</i> is less
     * than <i>n</i>; otherwise, it is equal to the character at index
     * <i>k-n</i> in the argument {@code str}.
     *
     * @param   str   a string.
     * @return  a reference to this object.
     */
    public AbstractStringBuilder append(String str) {
        if (str == null) {
            return appendNull();
        }
        byte coder = this.coder;
        int count = this.count;
        byte[] value = this.value;
        int len = str.length();
        byte newCoder = (byte)(coder | str.coder());
        if (needsNewBuffer(value, coder, count + len, newCoder)) {
            this.value = value = ensureCapacityNewCoder(value, coder, count, count + len, newCoder);
            this.coder = newCoder;
        }
        str.getBytes(value, count, newCoder);
        this.count = count + len;
        return this;
    }

    /**
     * Appends the specified {@code StringBuffer} to this sequence.
     *
     * @param   sb   the {@code StringBuffer} to append.
     * @return  a reference to this object.
     */
    public AbstractStringBuilder append(StringBuffer sb) {
        return this.append((AbstractStringBuilder)sb);
    }

    /**
     * @since 1.8
     */
    AbstractStringBuilder append(AbstractStringBuilder asb) {
        if (asb == null) {
            return appendNull();
        }
        int len = asb.length();
        byte coder = this.coder;
        int count = this.count;
        byte[] value = this.value;
        byte newCoder = (byte)(coder | asb.coder);
        if (needsNewBuffer(value, coder, count + len, newCoder)) {
            this.value = value = ensureCapacityNewCoder(value, coder, count, count + len, newCoder);
            this.coder = newCoder;
        }
        asb.getBytes(value, count, newCoder);
        this.count = count + len;
        maybeLatin1 |= asb.maybeLatin1;
        return this;
    }

    // Documentation in subclasses because of synchro difference
    @Override
    public AbstractStringBuilder append(CharSequence s) {
        if (s == null) {
            return appendNull();
        }
        if (s instanceof String str) {
            return this.append(str);
        }
        if (s instanceof AbstractStringBuilder asb) {
            return this.append(asb);
        }
        return this.append(s, 0, s.length());
    }

    private AbstractStringBuilder appendNull() {
        byte coder = this.coder;
        int count = this.count;
        int newCount = count + 4;
        byte[] value = ensureCapacitySameCoder(this.value, coder, newCount);
        if (isLatin1(coder))
            StringLatin1.putCharsAt(value, count, 'n', 'u', 'l', 'l');
        else
            StringUTF16.putCharsAt(value, count, 'n', 'u', 'l', 'l');
        this.count = newCount;
        this.value = value;
        return this;
    }

    /**
     * Appends a subsequence of the specified {@code CharSequence} to this
     * sequence.
     * <p>
     * Characters of the argument {@code s}, starting at
     * index {@code start}, are appended, in order, to the contents of
     * this sequence up to the (exclusive) index {@code end}. The length
     * of this sequence is increased by the value of {@code end - start}.
     * <p>
     * Let <i>n</i> be the length of this character sequence just prior to
     * execution of the {@code append} method. Then the character at
     * index <i>k</i> in this character sequence becomes equal to the
     * character at index <i>k</i> in this sequence, if <i>k</i> is less than
     * <i>n</i>; otherwise, it is equal to the character at index
     * <i>k+start-n</i> in the argument {@code s}.
     * <p>
     * If {@code s} is {@code null}, then this method appends
     * characters as if the s parameter was a sequence containing the four
     * characters {@code "null"}.
     * <p>
     * The contents are unspecified if the {@code CharSequence}
     * is modified during the method call or an exception is thrown
     * when accessing the {@code CharSequence}.
     *
     * @param   s the sequence to append.
     * @param   start   the starting index of the subsequence to be appended.
     * @param   end     the end index of the subsequence to be appended.
     * @return  a reference to this object.
     * @throws     IndexOutOfBoundsException if
     *             {@code start} is negative, or
     *             {@code start} is greater than {@code end} or
     *             {@code end} is greater than {@code s.length()}
     */
    @Override
    public AbstractStringBuilder append(CharSequence s, int start, int end) {
        if (s == null) {
            s = "null";
        }
        Preconditions.checkFromToIndex(start, end, s.length(), Preconditions.IOOBE_FORMATTER);
        int len = end - start;
        byte coder = this.coder;
        int count = this.count;
        byte[] currValue = ensureCapacitySameCoder(this.value, coder, count + len);
        byte[] value = (s instanceof String str)
            ? appendChars(currValue, coder, count, str, start, end)
            : appendChars(currValue, coder, count, s, start, end);
        if (currValue != value) {
            this.coder = UTF16;
        }
        this.count = count + len;
        this.value = value;
        return this;
    }


    /**
     * Appends the string representation of the {@code char} array
     * argument to this sequence.
     * <p>
     * The characters of the array argument are appended, in order, to
     * the contents of this sequence. The length of this sequence
     * increases by the length of the argument.
     * <p>
     * The overall effect is exactly as if the argument were converted
     * to a string by the method {@link String#valueOf(char[])},
     * and the characters of that string were then
     * {@link #append(String) appended} to this character sequence.
     *
     * @param   str   the characters to be appended.
     * @return  a reference to this object.
     */
    public AbstractStringBuilder append(char[] str) {
        int len = str.length;
        byte coder = this.coder;
        int count = this.count;
        byte[] currValue = ensureCapacitySameCoder(this.value, coder, count + len);
        byte[] value = appendChars(currValue, coder, count, str, 0, len);
        if (currValue != value) {
            this.coder = UTF16;
        }
        this.count = count + len;
        this.value = value;
        return this;
    }

    /**
     * Appends the string representation of a subarray of the
     * {@code char} array argument to this sequence.
     * <p>
     * Characters of the {@code char} array {@code str}, starting at
     * index {@code offset}, are appended, in order, to the contents
     * of this sequence. The length of this sequence increases
     * by the value of {@code len}.
     * <p>
     * The overall effect is exactly as if the arguments were converted
     * to a string by the method {@link String#valueOf(char[],int,int)},
     * and the characters of that string were then
     * {@link #append(String) appended} to this character sequence.
     *
     * @param   str      the characters to be appended.
     * @param   offset   the index of the first {@code char} to append.
     * @param   len      the number of {@code char}s to append.
     * @return  a reference to this object.
     * @throws IndexOutOfBoundsException
     *         if {@code offset < 0} or {@code len < 0}
     *         or {@code offset+len > str.length}
     */
    public AbstractStringBuilder append(char[] str, int offset, int len) {
        int end = offset + len;
        Preconditions.checkFromToIndex(offset, end, str.length, Preconditions.IOOBE_FORMATTER);
        byte coder = this.coder;
        int count = this.count;
        byte[] currValue = ensureCapacitySameCoder(value, coder, count + len);
        byte[] value = appendChars(currValue, coder, count, str, offset, end);
        if (currValue != value) {
            this.coder = UTF16;
        }
        this.count = count + len;
        this.value = value;
        return this;
    }

    /**
     * Appends the string representation of the {@code boolean}
     * argument to the sequence.
     * <p>
     * The overall effect is exactly as if the argument were converted
     * to a string by the method {@link String#valueOf(boolean)},
     * and the characters of that string were then
     * {@link #append(String) appended} to this character sequence.
     *
     * @param   b   a {@code boolean}.
     * @return  a reference to this object.
     */
    public AbstractStringBuilder append(boolean b) {
        byte coder = this.coder;
        int count = this.count;

        int newCount = count + (b ? 4 : 5);
        byte[] value = ensureCapacitySameCoder(this.value, coder, newCount);
        if (b) {
            if (isLatin1(coder))
                StringLatin1.putCharsAt(value, count, 't', 'r', 'u', 'e');
            else
                StringUTF16.putCharsAt(value, count, 't', 'r', 'u', 'e');
        } else {
            if (isLatin1(coder))
                StringLatin1.putCharsAt(value, count, 'f', 'a', 'l', 's', 'e');
            else
                StringUTF16.putCharsAt(value, count, 'f', 'a', 'l', 's', 'e');
        }
        this.value = value;
        this.count = newCount;
        return this;
    }

    /**
     * Appends the string representation of the {@code char}
     * argument to this sequence.
     * <p>
     * The argument is appended to the contents of this sequence.
     * The length of this sequence increases by {@code 1}.
     * <p>
     * The overall effect is exactly as if the argument were converted
     * to a string by the method {@link String#valueOf(char)},
     * and the character in that string were then
     * {@link #append(String) appended} to this character sequence.
     *
     * @param   c   a {@code char}.
     * @return  a reference to this object.
     */
    @Override
    public AbstractStringBuilder append(char c) {
        byte coder = this.coder;
        int count = this.count;
        byte[] value = this.value;
        byte newCoder = (byte) (coder | StringLatin1.coderFromChar(c));
        if (needsNewBuffer(value, coder, count + 1, newCoder)) {
            this.value = value = ensureCapacityNewCoder(value, coder, count, count + 1, newCoder);
            this.coder = coder = newCoder;
        }
        if (isLatin1(coder)) {
            value[count++] = (byte)c;
        } else {
            StringUTF16.putChar(value, count++, c);
        }
        this.count = count;
        return this;
    }

    /**
     * Appends the string representation of the {@code int}
     * argument to this sequence.
     * <p>
     * The overall effect is exactly as if the argument were converted
     * to a string by the method {@link String#valueOf(int)},
     * and the characters of that string were then
     * {@link #append(String) appended} to this character sequence.
     *
     * @param   i   an {@code int}.
     * @return  a reference to this object.
     */
    public AbstractStringBuilder append(int i) {
        byte coder = this.coder;
        int count = this.count;
        int spaceNeeded = count + DecimalDigits.stringSize(i);
        byte[] value = ensureCapacitySameCoder(this.value, coder, spaceNeeded);
        if (isLatin1(coder)) {
            DecimalDigits.getCharsLatin1(i, spaceNeeded, value);
        } else {
            DecimalDigits.getCharsUTF16(i, spaceNeeded, value);
        }
        this.value = value;
        this.count = spaceNeeded;
        return this;
    }

    /**
     * Appends the string representation of the {@code long}
     * argument to this sequence.
     * <p>
     * The overall effect is exactly as if the argument were converted
     * to a string by the method {@link String#valueOf(long)},
     * and the characters of that string were then
     * {@link #append(String) appended} to this character sequence.
     *
     * @param   l   a {@code long}.
     * @return  a reference to this object.
     */
    public AbstractStringBuilder append(long l) {
        byte coder = this.coder;
        int count = this.count;
        int spaceNeeded = count + DecimalDigits.stringSize(l);
        byte[] value = ensureCapacitySameCoder(this.value, coder, spaceNeeded);
        if (isLatin1(coder)) {
            DecimalDigits.getCharsLatin1(l, spaceNeeded, value);
        } else {
            DecimalDigits.getCharsUTF16(l, spaceNeeded, value);
        }
        this.value = value;
        this.count = spaceNeeded;
        return this;
    }

    /**
     * Appends the string representation of the {@code float}
     * argument to this sequence.
     * <p>
     * The overall effect is exactly as if the argument were converted
     * to a string by the method {@link String#valueOf(float)},
     * and the characters of that string were then
     * {@link #append(String) appended} to this character sequence.
     *
     * @param   f   a {@code float}.
     * @return  a reference to this object.
     */
    public AbstractStringBuilder append(float f) {
        byte coder = this.coder;
        int count = this.count;
        byte[] value = ensureCapacitySameCoder(this.value, coder,count + FloatToDecimal.MAX_CHARS);
        FloatToDecimal toDecimal = isLatin1(coder) ? FloatToDecimal.LATIN1 : FloatToDecimal.UTF16;
        this.count = toDecimal.putDecimal(value, count, f);
        this.value = value;
        return this;
    }

    /**
     * Appends the string representation of the {@code double}
     * argument to this sequence.
     * <p>
     * The overall effect is exactly as if the argument were converted
     * to a string by the method {@link String#valueOf(double)},
     * and the characters of that string were then
     * {@link #append(String) appended} to this character sequence.
     *
     * @param   d   a {@code double}.
     * @return  a reference to this object.
     */
    public AbstractStringBuilder append(double d) {
        byte coder = this.coder;
        int count = this.count;
        byte[] value = ensureCapacitySameCoder(this.value, coder,count + DoubleToDecimal.MAX_CHARS);
        DoubleToDecimal toDecimal = isLatin1(coder) ? DoubleToDecimal.LATIN1 : DoubleToDecimal.UTF16;
        this.count = toDecimal.putDecimal(value, count, d);
        this.value = value;
        return this;
    }

    /**
     * Removes the characters in a substring of this sequence.
     * The substring begins at the specified {@code start} and extends to
     * the character at index {@code end - 1} or to the end of the
     * sequence if no such character exists. If
     * {@code start} is equal to {@code end}, no changes are made.
     *
     * @param      start  The beginning index, inclusive.
     * @param      end    The ending index, exclusive.
     * @return     This object.
     * @throws     StringIndexOutOfBoundsException  if {@code start}
     *             is negative, greater than {@code length()}, or
     *             greater than {@code end}.
     */
    public AbstractStringBuilder delete(int start, int end) {
        int count = this.count;
        if (end > count) {
            end = count;
        }
        Preconditions.checkFromToIndex(start, end, count, Preconditions.SIOOBE_FORMATTER);
        int len = end - start;
        if (len > 0) {
            shift(value, coder, count, end, -len);
            this.count = count - len;
            maybeLatin1 = true;
        }
        return this;
    }

    /**
     * Appends the string representation of the {@code codePoint}
     * argument to this sequence.
     *
     * <p> The argument is appended to the contents of this sequence.
     * The length of this sequence increases by
     * {@link Character#charCount(int) Character.charCount(codePoint)}.
     *
     * <p> The overall effect is exactly as if the argument were
     * converted to a {@code char} array by the method
     * {@link Character#toChars(int)} and the character in that array
     * were then {@link #append(char[]) appended} to this character
     * sequence.
     *
     * @param   codePoint   a Unicode code point
     * @return  a reference to this object.
     * @throws    IllegalArgumentException if the specified
     * {@code codePoint} isn't a valid Unicode code point
     */
    public AbstractStringBuilder appendCodePoint(int codePoint) {
        if (Character.isBmpCodePoint(codePoint)) {
            return append((char)codePoint);
        }
        return append(Character.toChars(codePoint));
    }

    /**
     * Removes the {@code char} at the specified position in this
     * sequence. This sequence is shortened by one {@code char}.
     *
     * <p>Note: If the character at the given index is a supplementary
     * character, this method does not remove the entire character. If
     * correct handling of supplementary characters is required,
     * determine the number of {@code char}s to remove by calling
     * {@code Character.charCount(thisSequence.codePointAt(index))},
     * where {@code thisSequence} is this sequence.
     *
     * @param       index  Index of {@code char} to remove
     * @return      This object.
     * @throws      StringIndexOutOfBoundsException  if the {@code index}
     *              is negative or greater than or equal to
     *              {@code length()}.
     */
    public AbstractStringBuilder deleteCharAt(int index) {
        int count = this.count;
        checkIndex(index, count);
        shift(value, coder, count, index + 1, -1);
        this.count = count - 1;
        maybeLatin1 = true;
        return this;
    }

    /**
     * Replaces the characters in a substring of this sequence
     * with characters in the specified {@code String}. The substring
     * begins at the specified {@code start} and extends to the character
     * at index {@code end - 1} or to the end of the
     * sequence if no such character exists. First the
     * characters in the substring are removed and then the specified
     * {@code String} is inserted at {@code start}. (This
     * sequence will be lengthened to accommodate the
     * specified String if necessary.)
     *
     * @param      start    The beginning index, inclusive.
     * @param      end      The ending index, exclusive.
     * @param      str   String that will replace previous contents.
     * @return     This object.
     * @throws     StringIndexOutOfBoundsException  if {@code start}
     *             is negative, greater than {@code length()}, or
     *             greater than {@code end}.
     */
    public AbstractStringBuilder replace(int start, int end, String str) {
        byte coder = this.coder;
        int count = this.count;
        if (end > count) {
            end = count;
        }
        Preconditions.checkFromToIndex(start, end, count, Preconditions.SIOOBE_FORMATTER);
        int len = str.length();
        int newCount = count + len - (end - start);
        byte newCoder = (byte) (coder | str.coder());
        byte[] value = this.value;
        if (needsNewBuffer(value, coder, newCount, newCoder) ) {
            this.value = value = ensureCapacityNewCoder(value, coder, count, newCount, newCoder);
            this.coder = coder = newCoder;
        }
        shift(value, coder, count, end, newCount - count);
        str.getBytes(value, start, coder);
        this.count = newCount;
        maybeLatin1 = true;
        return this;
    }

    /**
     * Returns a {@code String} that contains a subsequence of
     * characters currently contained in this character sequence. The
     * substring begins at the specified index and extends to the end of
     * this sequence.
     *
     * @param      start    The beginning index, inclusive.
     * @return     A string containing the specified subsequence of characters.
     * @throws     StringIndexOutOfBoundsException  if {@code start} is
     *             less than zero, or greater than the length of this object.
     */
    public String substring(int start) {
        return substring(start, count);
    }

    /**
     * Returns a character sequence that is a subsequence of this sequence.
     *
     * <p> An invocation of this method of the form
     *
     * <pre>{@code
     * sb.subSequence(begin, end)}</pre>
     *
     * behaves in exactly the same way as the invocation
     *
     * <pre>{@code
     * sb.substring(begin, end)}</pre>
     *
     * This method is provided so that this class can
     * implement the {@link CharSequence} interface.
     *
     * @param      start   the start index, inclusive.
     * @param      end     the end index, exclusive.
     * @return     the specified subsequence.
     *
     * @throws  IndexOutOfBoundsException
     *          if {@code start} or {@code end} are negative,
     *          if {@code end} is greater than {@code length()},
     *          or if {@code start} is greater than {@code end}
     */
    @Override
    public CharSequence subSequence(int start, int end) {
        return substring(start, end);
    }

    /**
     * Returns a {@code String} that contains a subsequence of
     * characters currently contained in this sequence. The
     * substring begins at the specified {@code start} and
     * extends to the character at index {@code end - 1}.
     *
     * @param      start    The beginning index, inclusive.
     * @param      end      The ending index, exclusive.
     * @return     A string containing the specified subsequence of characters.
     * @throws     StringIndexOutOfBoundsException  if {@code start}
     *             or {@code end} are negative or greater than
     *             {@code length()}, or {@code start} is
     *             greater than {@code end}.
     */
    public String substring(int start, int end) {
        Preconditions.checkFromToIndex(start, end, count, Preconditions.SIOOBE_FORMATTER);
        if (isLatin1(coder)) {
            return StringLatin1.newString(value, start, end - start);
        }
        return StringUTF16.newString(value, start, end - start);
    }

    private static void shift(byte[] value, byte coder, int count, int offset, int n) {
        System.arraycopy(value, offset << coder,
                         value, (offset + n) << coder, (count - offset) << coder);
    }

    /**
     * Inserts the string representation of a subarray of the {@code str}
     * array argument into this sequence. The subarray begins at the
     * specified {@code offset} and extends {@code len} {@code char}s.
     * The characters of the subarray are inserted into this sequence at
     * the position indicated by {@code index}. The length of this
     * sequence increases by {@code len} {@code char}s.
     *
     * @param      index    position at which to insert subarray.
     * @param      str       A {@code char} array.
     * @param      offset   the index of the first {@code char} in subarray to
     *             be inserted.
     * @param      len      the number of {@code char}s in the subarray to
     *             be inserted.
     * @return     This object
     * @throws     StringIndexOutOfBoundsException  if {@code index}
     *             is negative or greater than {@code length()}, or
     *             {@code offset} or {@code len} are negative, or
     *             {@code (offset+len)} is greater than
     *             {@code str.length}.
     */
    public AbstractStringBuilder insert(int index, char[] str, int offset,
                                        int len)
    {
        byte coder = this.coder;
        int count = this.count;
        checkOffset(index, count);
        Preconditions.checkFromToIndex(offset, offset + len, str.length, Preconditions.SIOOBE_FORMATTER);
        byte[] value = ensureCapacitySameCoder(this.value, coder, count + len);
        shift(value, coder, count, index, len);
        count += len;
        byte[] newValue = putCharsAt(value, coder, count, index, str, offset, offset + len);
        if  (newValue != value) {
            this.coder = UTF16;
        }
        this.value = newValue;
        this.count = count;
        return this;
    }

    /**
     * Inserts the string representation of the {@code Object}
     * argument into this character sequence.
     * <p>
     * The overall effect is exactly as if the second argument were
     * converted to a string by the method {@link String#valueOf(Object)},
     * and the characters of that string were then
     * {@link #insert(int,String) inserted} into this character
     * sequence at the indicated offset.
     * <p>
     * The {@code offset} argument must be greater than or equal to
     * {@code 0}, and less than or equal to the {@linkplain #length() length}
     * of this sequence.
     *
     * @param      offset   the offset.
     * @param      obj      an {@code Object}.
     * @return     a reference to this object.
     * @throws     StringIndexOutOfBoundsException  if the offset is invalid.
     */
    public AbstractStringBuilder insert(int offset, Object obj) {
        return insert(offset, String.valueOf(obj));
    }

    /**
     * Inserts the string into this character sequence.
     * <p>
     * The characters of the {@code String} argument are inserted, in
     * order, into this sequence at the indicated offset, moving up any
     * characters originally above that position and increasing the length
     * of this sequence by the length of the argument. If
     * {@code str} is {@code null}, then the four characters
     * {@code "null"} are inserted into this sequence.
     * <p>
     * The character at index <i>k</i> in the new character sequence is
     * equal to:
     * <ul>
     * <li>the character at index <i>k</i> in the old character sequence, if
     * <i>k</i> is less than {@code offset}
     * <li>the character at index <i>k</i>{@code -offset} in the
     * argument {@code str}, if <i>k</i> is not less than
     * {@code offset} but is less than {@code offset+str.length()}
     * <li>the character at index <i>k</i>{@code -str.length()} in the
     * old character sequence, if <i>k</i> is not less than
     * {@code offset+str.length()}
     * </ul><p>
     * The {@code offset} argument must be greater than or equal to
     * {@code 0}, and less than or equal to the {@linkplain #length() length}
     * of this sequence.
     *
     * @param      offset   the offset.
     * @param      str      a string.
     * @return     a reference to this object.
     * @throws     StringIndexOutOfBoundsException  if the offset is invalid.
     */
    public AbstractStringBuilder insert(int offset, String str) {
        byte coder = this.coder;
        int count = this.count;
        checkOffset(offset, count);
        if (str == null) {
            str = "null";
        }
        int len = str.length();
        byte newCoder = (byte) (coder | str.coder());
        byte[] value = this.value;
        if (needsNewBuffer(value, coder, count + len, newCoder)) {
            this.value = value = ensureCapacityNewCoder(value, coder, count, count + len, newCoder);
            this.coder = coder = newCoder;
        }
        shift(value, coder, count, offset, len);
        this.count = count + len;
        str.getBytes(value, offset, coder);
        return this;
    }

    /**
     * Inserts the string representation of the {@code char} array
     * argument into this sequence.
     * <p>
     * The characters of the array argument are inserted into the
     * contents of this sequence at the position indicated by
     * {@code offset}. The length of this sequence increases by
     * the length of the argument.
     * <p>
     * The overall effect is exactly as if the second argument were
     * converted to a string by the method {@link String#valueOf(char[])},
     * and the characters of that string were then
     * {@link #insert(int,String) inserted} into this character
     * sequence at the indicated offset.
     * <p>
     * The {@code offset} argument must be greater than or equal to
     * {@code 0}, and less than or equal to the {@linkplain #length() length}
     * of this sequence.
     *
     * @param      offset   the offset.
     * @param      str      a character array.
     * @return     a reference to this object.
     * @throws     StringIndexOutOfBoundsException  if the offset is invalid.
     */
    public AbstractStringBuilder insert(int offset, char[] str) {
        byte coder = this.coder;
        int count = this.count;
        checkOffset(offset, count);
        int len = str.length;
        byte[] currValue = ensureCapacitySameCoder(this.value, coder, count + len);
        shift(currValue, coder, count, offset, len);
        count += len;
        byte[] newValue = putCharsAt(currValue, coder, count, offset, str, 0, len);
        if (currValue != newValue) {
            this.coder = UTF16;
        }
        this.count = count;
        this.value = newValue;
        return this;
    }

    /**
     * Inserts the specified {@code CharSequence} into this sequence.
     * <p>
     * The characters of the {@code CharSequence} argument are inserted,
     * in order, into this sequence at the indicated offset, moving up
     * any characters originally above that position and increasing the length
     * of this sequence by the length of the argument s.
     * <p>
     * The result of this method is exactly the same as if it were an
     * invocation of this object's
     * {@link #insert(int,CharSequence,int,int) insert}(dstOffset, s, 0, s.length())
     * method.
     * <p>
     * The contents are unspecified if the {@code CharSequence}
     * is modified during the method call or an exception is thrown
     * when accessing the {@code CharSequence}.
     *
     * <p>If {@code s} is {@code null}, then the four characters
     * {@code "null"} are inserted into this sequence.
     *
     * @param      dstOffset   the offset.
     * @param      s the sequence to be inserted
     * @return     a reference to this object.
     * @throws     IndexOutOfBoundsException  if the offset is invalid.
     */
    public AbstractStringBuilder insert(int dstOffset, CharSequence s) {
        if (s == null) {
            s = "null";
        }
        return this.insert(dstOffset, s, 0, s.length());
    }

    /**
     * Inserts a subsequence of the specified {@code CharSequence} into
     * this sequence.
     * <p>
     * The subsequence of the argument {@code s} specified by
     * {@code start} and {@code end} are inserted,
     * in order, into this sequence at the specified destination offset, moving
     * up any characters originally above that position. The length of this
     * sequence is increased by {@code end - start}.
     * <p>
     * The character at index <i>k</i> in this sequence becomes equal to:
     * <ul>
     * <li>the character at index <i>k</i> in this sequence, if
     * <i>k</i> is less than {@code dstOffset}
     * <li>the character at index <i>k</i>{@code +start-dstOffset} in
     * the argument {@code s}, if <i>k</i> is greater than or equal to
     * {@code dstOffset} but is less than {@code dstOffset+end-start}
     * <li>the character at index <i>k</i>{@code -(end-start)} in this
     * sequence, if <i>k</i> is greater than or equal to
     * {@code dstOffset+end-start}
     * </ul><p>
     * The {@code dstOffset} argument must be greater than or equal to
     * {@code 0}, and less than or equal to the {@linkplain #length() length}
     * of this sequence.
     * <p>The start argument must be non-negative, and not greater than
     * {@code end}.
     * <p>The end argument must be greater than or equal to
     * {@code start}, and less than or equal to the length of s.
     *
     * <p>If {@code s} is {@code null}, then this method inserts
     * characters as if the s parameter was a sequence containing the four
     * characters {@code "null"}.
     * <p>
     * The contents are unspecified if the {@code CharSequence}
     * is modified during the method call or an exception is thrown
     * when accessing the {@code CharSequence}.
     *
     * @param      dstOffset   the offset in this sequence.
     * @param      s       the sequence to be inserted.
     * @param      start   the starting index of the subsequence to be inserted.
     * @param      end     the end index of the subsequence to be inserted.
     * @return     a reference to this object.
     * @throws     IndexOutOfBoundsException  if {@code dstOffset}
     *             is negative or greater than {@code this.length()}, or
     *              {@code start} or {@code end} are negative, or
     *              {@code start} is greater than {@code end} or
     *              {@code end} is greater than {@code s.length()}
     */
    public AbstractStringBuilder insert(int dstOffset, CharSequence s,
                                        int start, int end)
    {
        if (s == null) {
            s = "null";
        }
        byte coder = this.coder;
        int count = this.count;
        checkOffset(dstOffset, count);
        Preconditions.checkFromToIndex(start, end, s.length(), Preconditions.IOOBE_FORMATTER);
        int len = end - start;
        byte[] currValue = ensureCapacitySameCoder(this.value, coder, count + len);
        shift(currValue, coder, count, dstOffset, len);
        count += len;
        // Coder of CharSequence may be a mismatch, requiring the value array to be inflated
        byte[] newValue = (s instanceof String str)
            ? putStringAt(currValue, coder, count, dstOffset, str, start, end)
            : putCharsAt(currValue, coder, count, dstOffset, s, start, end);
        if (currValue != newValue) {
            this.coder = UTF16;
        }
        this.value = newValue;
        this.count = count;
        return this;
    }

    /**
     * Inserts the string representation of the {@code boolean}
     * argument into this sequence.
     * <p>
     * The overall effect is exactly as if the second argument were
     * converted to a string by the method {@link String#valueOf(boolean)},
     * and the characters of that string were then
     * {@link #insert(int,String) inserted} into this character
     * sequence at the indicated offset.
     * <p>
     * The {@code offset} argument must be greater than or equal to
     * {@code 0}, and less than or equal to the {@linkplain #length() length}
     * of this sequence.
     *
     * @param      offset   the offset.
     * @param      b        a {@code boolean}.
     * @return     a reference to this object.
     * @throws     StringIndexOutOfBoundsException  if the offset is invalid.
     */
    public AbstractStringBuilder insert(int offset, boolean b) {
        return insert(offset, String.valueOf(b));
    }

    /**
     * Inserts the string representation of the {@code char}
     * argument into this sequence.
     * <p>
     * The overall effect is exactly as if the second argument were
     * converted to a string by the method {@link String#valueOf(char)},
     * and the character in that string were then
     * {@link #insert(int,String) inserted} into this character
     * sequence at the indicated offset.
     * <p>
     * The {@code offset} argument must be greater than or equal to
     * {@code 0}, and less than or equal to the {@linkplain #length() length}
     * of this sequence.
     *
     * @param      offset   the offset.
     * @param      c        a {@code char}.
     * @return     a reference to this object.
     * @throws     IndexOutOfBoundsException  if the offset is invalid.
     */
    public AbstractStringBuilder insert(int offset, char c) {
        byte coder = this.coder;
        int count = this.count;
        checkOffset(offset, count);
        byte newCoder = (byte)(coder | StringLatin1.coderFromChar(c));
        byte[] value = this.value;

        if (needsNewBuffer(value, coder, count + 1, newCoder)) {
            this.value = value = ensureCapacityNewCoder(value, coder, count, count + 1, newCoder);
            this.coder = coder = newCoder;
        }
        shift(value, coder, count, offset, 1);
        if (isLatin1(coder)) {
            value[offset] = (byte)c;
        } else {
            StringUTF16.putCharSB(value, offset, c);
        }
        this.count = count + 1;
        return this;
    }

    /**
     * Inserts the string representation of the second {@code int}
     * argument into this sequence.
     * <p>
     * The overall effect is exactly as if the second argument were
     * converted to a string by the method {@link String#valueOf(int)},
     * and the characters of that string were then
     * {@link #insert(int,String) inserted} into this character
     * sequence at the indicated offset.
     * <p>
     * The {@code offset} argument must be greater than or equal to
     * {@code 0}, and less than or equal to the {@linkplain #length() length}
     * of this sequence.
     *
     * @param      offset   the offset.
     * @param      i        an {@code int}.
     * @return     a reference to this object.
     * @throws     StringIndexOutOfBoundsException  if the offset is invalid.
     */
    public AbstractStringBuilder insert(int offset, int i) {
        return insert(offset, String.valueOf(i));
    }

    /**
     * Inserts the string representation of the {@code long}
     * argument into this sequence.
     * <p>
     * The overall effect is exactly as if the second argument were
     * converted to a string by the method {@link String#valueOf(long)},
     * and the characters of that string were then
     * {@link #insert(int,String) inserted} into this character
     * sequence at the indicated offset.
     * <p>
     * The {@code offset} argument must be greater than or equal to
     * {@code 0}, and less than or equal to the {@linkplain #length() length}
     * of this sequence.
     *
     * @param      offset   the offset.
     * @param      l        a {@code long}.
     * @return     a reference to this object.
     * @throws     StringIndexOutOfBoundsException  if the offset is invalid.
     */
    public AbstractStringBuilder insert(int offset, long l) {
        return insert(offset, String.valueOf(l));
    }

    /**
     * Inserts the string representation of the {@code float}
     * argument into this sequence.
     * <p>
     * The overall effect is exactly as if the second argument were
     * converted to a string by the method {@link String#valueOf(float)},
     * and the characters of that string were then
     * {@link #insert(int,String) inserted} into this character
     * sequence at the indicated offset.
     * <p>
     * The {@code offset} argument must be greater than or equal to
     * {@code 0}, and less than or equal to the {@linkplain #length() length}
     * of this sequence.
     *
     * @param      offset   the offset.
     * @param      f        a {@code float}.
     * @return     a reference to this object.
     * @throws     StringIndexOutOfBoundsException  if the offset is invalid.
     */
    public AbstractStringBuilder insert(int offset, float f) {
        return insert(offset, String.valueOf(f));
    }

    /**
     * Inserts the string representation of the {@code double}
     * argument into this sequence.
     * <p>
     * The overall effect is exactly as if the second argument were
     * converted to a string by the method {@link String#valueOf(double)},
     * and the characters of that string were then
     * {@link #insert(int,String) inserted} into this character
     * sequence at the indicated offset.
     * <p>
     * The {@code offset} argument must be greater than or equal to
     * {@code 0}, and less than or equal to the {@linkplain #length() length}
     * of this sequence.
     *
     * @param      offset   the offset.
     * @param      d        a {@code double}.
     * @return     a reference to this object.
     * @throws     StringIndexOutOfBoundsException  if the offset is invalid.
     */
    public AbstractStringBuilder insert(int offset, double d) {
        return insert(offset, String.valueOf(d));
    }

    /**
     * Returns the index within this string of the first occurrence of the
     * specified substring.
     *
     * <p>The returned index is the smallest value {@code k} for which:
     * <pre>{@code
     * this.toString().startsWith(str, k)
     * }</pre>
     * If no such value of {@code k} exists, then {@code -1} is returned.
     *
     * @param   str   the substring to search for.
     * @return  the index of the first occurrence of the specified substring,
     *          or {@code -1} if there is no such occurrence.
     */
    public int indexOf(String str) {
        return indexOf(str, 0);
    }

    /**
     * Returns the index within this string of the first occurrence of the
     * specified substring, starting at the specified index.
     *
     * <p>The returned index is the smallest value {@code k} for which:
     * <pre>{@code
     *     k >= Math.min(fromIndex, this.length()) &&
     *                   this.toString().startsWith(str, k)
     * }</pre>
     * If no such value of {@code k} exists, then {@code -1} is returned.
     *
     * @param   str         the substring to search for.
     * @param   fromIndex   the index from which to start the search.
     * @return  the index of the first occurrence of the specified substring,
     *          starting at the specified index,
     *          or {@code -1} if there is no such occurrence.
     */
    public int indexOf(String str, int fromIndex) {
        return String.indexOf(value, coder, count, str, fromIndex);
    }

    /**
     * Returns the index within this string of the last occurrence of the
     * specified substring.  The last occurrence of the empty string "" is
     * considered to occur at the index value {@code this.length()}.
     *
     * <p>The returned index is the largest value {@code k} for which:
     * <pre>{@code
     * this.toString().startsWith(str, k)
     * }</pre>
     * If no such value of {@code k} exists, then {@code -1} is returned.
     *
     * @param   str   the substring to search for.
     * @return  the index of the last occurrence of the specified substring,
     *          or {@code -1} if there is no such occurrence.
     */
    public int lastIndexOf(String str) {
        return lastIndexOf(str, count);
    }

    /**
     * Returns the index within this string of the last occurrence of the
     * specified substring, searching backward starting at the specified index.
     *
     * <p>The returned index is the largest value {@code k} for which:
     * <pre>{@code
     *     k <= Math.min(fromIndex, this.length()) &&
     *                   this.toString().startsWith(str, k)
     * }</pre>
     * If no such value of {@code k} exists, then {@code -1} is returned.
     *
     * @param   str         the substring to search for.
     * @param   fromIndex   the index to start the search from.
     * @return  the index of the last occurrence of the specified substring,
     *          searching backward from the specified index,
     *          or {@code -1} if there is no such occurrence.
     */
    public int lastIndexOf(String str, int fromIndex) {
        return String.lastIndexOf(value, coder, count, str, fromIndex);
    }

    /**
     * Causes this character sequence to be replaced by the reverse of
     * the sequence. If there are any surrogate pairs included in the
     * sequence, these are treated as single characters for the
     * reverse operation. Thus, the order of the high-low surrogates
     * is never reversed.
     *
     * Let <i>n</i> be the character length of this character sequence
     * (not the length in {@code char} values) just prior to
     * execution of the {@code reverse} method. Then the
     * character at index <i>k</i> in the new character sequence is
     * equal to the character at index <i>n-k-1</i> in the old
     * character sequence.
     *
     * <p>Note that the reverse operation may result in producing
     * surrogate pairs that were unpaired low-surrogates and
     * high-surrogates before the operation. For example, reversing
     * "\u005CuDC00\u005CuD800" produces "\u005CuD800\u005CuDC00" which is
     * a valid surrogate pair.
     *
     * @return  a reference to this object.
     */
    public AbstractStringBuilder reverse() {
        byte[] val = this.value;
        int count = this.count;
        int n = count - 1;
        if (isLatin1(this.coder)) {
            for (int j = (n-1) >> 1; j >= 0; j--) {
                int k = n - j;
                byte cj = val[j];
                val[j] = val[k];
                val[k] = cj;
            }
        } else {
            StringUTF16.reverse(val, count);
        }
        return this;
    }

    /**
     * Returns a string representing the data in this sequence.
     * The {@code String} object that is returned contains the character
     * sequence currently represented by this object. Subsequent
     * changes to this sequence do not affect the contents of the
     * returned {@code String}.
     *
     * @return  a string representation of this sequence of characters.
     */
    @Override
    public abstract String toString();

    /**
     * {@inheritDoc}
     * @since 9
     */
    @Override
    public IntStream chars() {
        // Reuse String-based spliterator. This requires a supplier to
        // capture the value and count when the terminal operation is executed
        return StreamSupport.intStream(
                () -> {
                    // The combined set of field reads are not atomic and thread
                    // safe but bounds checks will ensure no unsafe reads from
                    // the byte array
                    byte[] val = this.value;
                    int count = this.count;
                    byte coder = this.coder;
                    return coder == LATIN1
                           ? new StringLatin1.CharsSpliterator(val, 0, count, 0)
                           : new StringUTF16.CharsSpliterator(val, 0, count, 0);
                },
                Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED,
                false);
    }

    /**
     * {@inheritDoc}
     * @since 9
     */
    @Override
    public IntStream codePoints() {
        // Reuse String-based spliterator. This requires a supplier to
        // capture the value and count when the terminal operation is executed
        return StreamSupport.intStream(
                () -> {
                    // The combined set of field reads are not atomic and thread
                    // safe but bounds checks will ensure no unsafe reads from
                    // the byte array
                    byte[] val = this.value;
                    int count = this.count;
                    byte coder = this.coder;
                    return isLatin1(coder)
                           ? new StringLatin1.CharsSpliterator(val, 0, count, 0)
                           : new StringUTF16.CodePointsSpliterator(val, 0, count, 0);
                },
                Spliterator.ORDERED,
                false);
    }

    /**
     * Needed by {@code String} for the contentEquals method.
     */
    final byte[] getValue() {
        return value;
    }

    /*
     * Invoker guarantees it is in UTF16 (inflate itself for asb), if two
     * coders are different and the dstBegin has enough space
     *
     * @param dstBegin  the char index, not offset of byte[]
     * @param coder     the coder of dst[]
     */
    void getBytes(byte[] dst, int dstBegin, byte coder) {
        if (this.coder == coder) {
            System.arraycopy(value, 0, dst, dstBegin << coder, count << coder);
        } else {        // this.coder == LATIN && coder == UTF16
            StringLatin1.inflate(value, 0, dst, dstBegin, count);
        }
    }

    /* for readObject() */
    void initBytes(char[] value, int off, int len) {
        if (String.COMPACT_STRINGS) {
            byte[] val = StringUTF16.compress(value, off, len);
            this.coder = StringUTF16.coderFromArrayLen(val, len);
            this.value = val;
            return;
        }
        this.coder = UTF16;
        this.value = StringUTF16.toBytes(value, off, len);
    }

    final byte getCoder() {
        return COMPACT_STRINGS ? coder : UTF16;
    }

    // Package access for String and StringBuffer.
    final boolean isLatin1() {
        return isLatin1(coder);
    }

    private static boolean isLatin1(byte coder) {
        return COMPACT_STRINGS && coder == LATIN1;
    }

    /**
     * {@return Return the buffer containing the composed string and inserted characters}
     * If the buffer coder needs to support UTF16 and does not, it is inflated and a different
     * buffer is returned. The caller is responsible for setting the coder and updating the value ref
     * based solely on the difference in the buffer reference.
     *
     * @param value byte array destination for the string (if the coder matches)
     * @param coder the original buffer coder
     * @param count the count of characters in the original buffer
     * @param index the insertion point
     * @param s char[] array to insert from
     * @param off offset of the first character
     * @param end the offset of the last character (exclusive)
     */
    private static byte[] putCharsAt(byte[] value, byte coder, int count, int index, char[] s, int off, int end) {
        if (isLatin1(coder)) {
            int latin1Len = StringUTF16.compress(s, off, value, index, end - off);
            for (int i = off + latin1Len, j = index + latin1Len; i < end; i++) {
                char c = s[i];
                if (StringLatin1.canEncode(c)) {
                    value[j++] = (byte)c;
                } else {
                    value = inflateToUTF16(value, count);
                    // Store c to make sure sb has a UTF16 char
                    StringUTF16.putCharSB(value, j++, c);
                    i++;
                    StringUTF16.putCharsSB(value, j, s, i, end);
                    return value;
                }
            }
        } else {
            StringUTF16.putCharsSB(value, index, s, off, end);
        }
        return value;
    }

    /**
     * {@return Return the buffer containing the composed string and inserted characters}
     * If the buffer coder needs to support UTF16 and does not, it is inflated and a different
     * buffer is returned. The caller is responsible for setting the coder and updating the value ref
     * based solely on the difference in the buffer reference.
     *
     * @param value byte array destination for the string (if the coder matches)
     * @param coder the original buffer coder
     * @param count the count of characters in the original buffer
     * @param index the insertion point
     * @param s CharSequence to insert from
     * @param off offset of the first character
     * @param end the offset of the last character (exclusive)
     */
    private static byte[] putCharsAt(byte[] value, byte coder, int count, int index, CharSequence s, int off, int end) {
        if (isLatin1(coder)) {
            for (int i = off, j = index; i < end; i++) {
                char c = s.charAt(i);
                if (StringLatin1.canEncode(c)) {
                    value[j++] = (byte)c;
                } else {
                    value = inflateToUTF16(value, count);
                    // store c to make sure it has a UTF16 char
                    StringUTF16.putCharSB(value, j++, c);
                    i++;
                    StringUTF16.putCharsSB(value, j, s, i, end);
                    return value;
                }
            }
        } else {
            StringUTF16.putCharsSB(value, index, s, off, end);
        }
        return value;
    }

    private static byte[] inflateIfNeededFor(byte[] value, int count, byte coder, byte otherCoder) {
        if (COMPACT_STRINGS && (coder == LATIN1 && otherCoder == UTF16)) {
            return inflateToUTF16(value, count);
        }
        return value;
    }

    /**
     * {@return the buffer with the substring inserted}
     * If the substring contains UTF16 characters and the current coder is LATIN1, inflation occurs
     * into a new buffer and returned; the caller must update the coder to UTF16.
     * Since the contents are immutable, a simple copy of characters is sufficient.
     * @param value an existing buffer to insert into
     * @param coder the coder of the buffer
     * @param count the count of characters in the buffer
     * @param index the index to insert the string
     * @param str the string
     */
     private static byte[] putStringAt(byte[] value, byte coder, int count, int index, String str, int off, int end) {
        byte[] newValue = inflateIfNeededFor(value, count, coder, str.coder());
        coder = (newValue == value) ? coder : UTF16;
        str.getBytes(newValue, off, index, coder, end - off);
        return newValue;
    }

    /**
     * {@return buffer with new characters appended, possibly inflated}
     * The value buffer capacity must be large enough to hold the additional (end - off) characters
     * (assuming they are all latin1).
     * The buffer will be inflated if any character is UTF16 and the buffer is latin1.
     * If the returned buffer is different then passed in, the new coder is UTF16.
     * The caller is responsible for updating the count.
     * @param value the current buffer
     * @param coder the coder of the buffer
     * @param count the character count
     * @param s a char array
     * @param off the offset of the first character to append
     * @param end end last (exclusive) character to append
     */
    private static byte[] appendChars(byte[] value, byte coder, int count, char[] s, int off, int end) {
        if (isLatin1(coder)) {
            int latin1Len = StringUTF16.compress(s, off, value, count, end - off);
            for (int i = off + latin1Len, j = count + latin1Len; i < end; i++) {
                char c = s[i];
                if (StringLatin1.canEncode(c)) {
                    value[j++] = (byte)c;
                } else {
                    value = inflateToUTF16(value, j);
                    // Store c to make sure sb has a UTF16 char
                    StringUTF16.putCharSB(value, j++, c);
                    i++;
                    StringUTF16.putCharsSB(value, j, s, i, end);
                    return value;
                }
            }
        } else {
            StringUTF16.putCharsSB(value, count, s, off, end);
        }
        return value;
    }

    /**
     * {@return buffer with new characters appended, possibly inflated}
     * The value buffer capacity must be large enough to hold the additional (end - off) characters
     * (assuming they are all latin1).
     * The buffer will be inflated if any character is UTF16 and the buffer is latin1.
     * If the returned buffer is different then passed in, the new coder is UTF16.
     * The caller is responsible for updating the count.
     * @param value the current buffer
     * @param coder the coder of the buffer
     * @param count the character count
     * @param s a string
     * @param off the offset of the first character to append
     * @param end end last (exclusive) character to append
     */
    private static byte[] appendChars(byte[] value, byte coder, int count, String s, int off, int end) {
        if (isLatin1(coder)) {
            if (s.isLatin1()) {
                System.arraycopy(s.value(), off, value, count, end - off);
            } else {
                // We might need to inflate, but do it as late as possible since
                // the range of characters we're copying might all be latin1
                for (int i = off, j = count; i < end; i++) {
                    char c = s.charAt(i);
                    if (StringLatin1.canEncode(c)) {
                        value[j++] = (byte) c;
                    } else {
                        value = inflateToUTF16(value, j);
                        System.arraycopy(s.value(), i << UTF16, value, j << UTF16, (end - i) << UTF16);
                        return value;
                    }
                }
            }
        } else if (s.isLatin1()) {
            StringUTF16.putCharsSB(value, count, s, off, end);
        } else { // both UTF16
            System.arraycopy(s.value(), off << UTF16, value, count << UTF16, (end - off) << UTF16);
        }
        return value;
    }

    /**
     * {@return buffer with new characters appended, possibly inflated}
     * The value buffer capacity must be large enough to hold the additional (end - off) characters
     * (assuming they are all latin1).
     * The buffer will be inflated if any character is UTF16 and the buffer is latin1.
     * If the returned buffer is different then passed in, the new coder is UTF16.
     * The caller is responsible for updating the count.
     * @param value the current buffer
     * @param coder the coder of the buffer
     * @param count the character count
     * @param s CharSequence to append characters from
     * @param off the offset of the first character to append
     * @param end end last (exclusive) character to append
     */
    private static byte[] appendChars(byte[] value, byte coder, int count, CharSequence s, int off, int end) {
        if (isLatin1(coder)) {
            for (int i = off, j = count; i < end; i++) {
                char c = s.charAt(i);
                if (StringLatin1.canEncode(c)) {
                    value[j++] = (byte)c;
                } else {
                    value = inflateToUTF16(value, j);
                    // Store c to make sure sb has a UTF16 char
                    StringUTF16.putCharSB(value, j++, c);
                    i++;
                    StringUTF16.putCharsSB(value, j, s, i, end);
                    return value;
                }
            }
        } else {
            StringUTF16.putCharsSB(value, count, s, off, end);
        }
        return value;
    }

    /**
     * Used by StringConcatHelper via JLA. Adds the current builder count to the
     * accumulation of items being concatenated. If the coder for the builder is
     * UTF16 then upgrade the whole concatenation to UTF16.
     *
     * @param lengthCoder running accumulation of length and coder
     *
     * @return updated accumulation of length and coder
     */
    long mix(long lengthCoder) {
        return (lengthCoder + count) | ((long)coder << 32);
    }

    /**
     * Used by StringConcatHelper via JLA. Adds the characters in the builder value to the
     * concatenation buffer and then updates the running accumulation of length.
     *
     * @param lengthCoder running accumulation of length and coder
     * @param buffer      concatenation buffer
     *
     * @return running accumulation of length and coder minus the number of characters added
     */
    long prepend(long lengthCoder, byte[] buffer) {
        lengthCoder -= count;

        if (lengthCoder < ((long)UTF16 << 32)) {
            System.arraycopy(value, 0, buffer, (int)lengthCoder, count);
        } else if (isLatin1(coder)) {
            StringUTF16.inflate(value, 0, buffer, (int)lengthCoder, count);
        } else {
            System.arraycopy(value, 0, buffer, (int)lengthCoder << 1, count << 1);
        }

        return lengthCoder;
    }

    private AbstractStringBuilder repeat(char c, int count) {
        byte coder = this.coder;
        int prevCount = this.count;
        int limit = prevCount + count;
        byte[] value = this.value;
        byte newCoder = (byte) (coder | StringLatin1.coderFromChar(c));
        if (needsNewBuffer(value, coder, limit, newCoder)) {
            this.value = value = ensureCapacityNewCoder(value, coder, prevCount, limit, newCoder);
            this.coder = coder = newCoder;
        }
        if (isLatin1(coder)) {
            Arrays.fill(value, prevCount, limit, (byte)c);
        } else {
            for (int index = prevCount; index < limit; index++) {
                StringUTF16.putCharSB(value, index, c);
            }
        }
        this.count = limit;
        return this;
    }

    /**
     * Repeats {@code count} copies of the string representation of the
     * {@code codePoint} argument to this sequence.
     * <p>
     * The length of this sequence increases by {@code count} times the
     * string representation length.
     * <p>
     * It is usual to use {@code char} expressions for code points. For example:
     * {@snippet lang="java":
     * // insert 10 asterisks into the buffer
     * sb.repeat('*', 10);
     * }
     *
     * @param codePoint  code point to append
     * @param count      number of times to copy
     *
     * @return  a reference to this object.
     *
     * @throws IllegalArgumentException if the specified {@code codePoint}
     * is not a valid Unicode code point or if {@code count} is negative.
     *
     * @since 21
     */
    public AbstractStringBuilder repeat(int codePoint, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count is negative: " + count);
        } else if (count == 0) {
            return this;
        }
        if (Character.isBmpCodePoint(codePoint)) {
            repeat((char)codePoint, count);
        } else {
            repeat(CharBuffer.wrap(Character.toChars(codePoint)), count);
        }
        return this;
    }

    /**
     * Appends {@code count} copies of the specified {@code CharSequence} {@code cs}
     * to this sequence.
     * <p>
     * The length of this sequence increases by {@code count} times the
     * {@code CharSequence} length.
     * <p>
     * If {@code cs} is {@code null}, then the four characters
     * {@code "null"} are repeated into this sequence.
     * <p>
     * The contents are unspecified if the {@code CharSequence}
     * is modified during the method call or an exception is thrown
     * when accessing the {@code CharSequence}.
     *
     * @param cs     a {@code CharSequence}
     * @param count  number of times to copy
     *
     * @return  a reference to this object.
     *
     * @throws IllegalArgumentException  if {@code count} is negative
     *
     * @since 21
     */
    public AbstractStringBuilder repeat(CharSequence cs, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count is negative: " + count);
        } else if (count == 0) {
            return this;
        } else if (count == 1) {
            return append(cs);
        }
        if (cs == null) {
            cs = "null";
        }
        int length = cs.length();
        if (length == 0) {
            return this;
        } else if (length == 1) {
            return repeat(cs.charAt(0), count);
        }
        byte coder = this.coder;
        int offset = this.count;
        byte[] value = this.value;
        int valueLength = length << coder;
        if ((Integer.MAX_VALUE - offset) / count < valueLength) {
            throw new OutOfMemoryError("Required length exceeds implementation limit");
        }
        int total = count * length;
        int limit = offset + total;
        if (cs instanceof String str) {
            byte newCoder = (byte)(coder | str.coder());
            if (needsNewBuffer(value, coder, limit, newCoder)) {
                this.value = value = ensureCapacityNewCoder(value, coder, offset, limit, newCoder);
                this.coder = coder = newCoder;
            }
            str.getBytes(value, offset, newCoder);
        } else if (cs instanceof AbstractStringBuilder asb) {
            byte newCoder = (byte)(coder | asb.coder);
            if (needsNewBuffer(value, coder, limit, newCoder)) {
                this.value = value = ensureCapacityNewCoder(value, coder, offset, limit, newCoder);
                this.coder = coder = newCoder;
            }
            asb.getBytes(value, offset, newCoder);
        } else {
            byte[] currValue = ensureCapacitySameCoder(value, coder, limit);
            value = appendChars(currValue, coder, offset, cs, 0, length);
            if (currValue != value) {
                this.coder = coder = UTF16;
            }
            this.value = value;
        }
        String.repeatCopyRest(value, offset << coder, total << coder, length << coder);
        this.count = limit;
        return this;
    }
}
