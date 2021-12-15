/*
 *  Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package jdk.incubator.foreign;

import jdk.internal.foreign.MemoryAddressImpl;
import jdk.internal.reflect.CallerSensitive;

import java.lang.invoke.MethodHandle;
import java.nio.ByteOrder;

/**
 * A memory address models a reference into a memory location. Memory addresses are typically obtained in one of the following ways:
 * <ul>
 *     <li>By calling {@link Addressable#address()} on an instance of type {@link Addressable} (e.g. a memory segment);</li>
 *     <li>By invoking a {@linkplain CLinker#downcallHandle(FunctionDescriptor) downcall method handle} which returns a pointer;</li>
 *     <li>By reading an address from memory, e.g. via {@link MemorySegment#get(ValueLayout.OfAddress, long)}.</li>
 *     <li>By the invocation of an {@linkplain CLinker#upcallStub(MethodHandle, FunctionDescriptor, ResourceScope) upcall stub} which accepts a pointer.
 * </ul>
 * A memory address is backed by a raw machine pointer, expressed as a {@linkplain #toRawLongValue() long value}.
 *
 * <h2>Dereference</h2>
 *
 * A memory address can be read or written using various methods provided in this class (e.g. {@link #get(ValueLayout.OfInt, long)}).
 * Each dereference method takes a {@linkplain jdk.incubator.foreign.ValueLayout value layout}, which specifies the size,
 * alignment constraints, byte order as well as the Java type associated with the dereference operation, and an offset.
 * For instance, to read an int from a segment, using {@link ByteOrder#nativeOrder() default endianness}, the following code can be used:
 * {@snippet lang=java :
 * MemoryAddress address = ...
 * int value = address.get(ValueLayout.JAVA_INT, 0);
 * }
 *
 * If the value to be read is stored in memory using {@link ByteOrder#BIG_ENDIAN big-endian} encoding, the dereference operation
 * can be expressed as follows:
 * {@snippet lang=java :
 * MemoryAddress address = ...
 * int value = address.get(ValueLayout.JAVA_INT.withOrder(BIG_ENDIAN), 0);
 * }
 *
 * All the dereference methods in this class are <a href="package-summary.html#restricted"><em>restricted</em></a>: since
 * a memory address does not feature temporal nor spatial bounds, the runtime has no way to check the correctness
 * of the memory dereference operation.
 * <p>
 * All implementations of this interface must be <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>;
 * programmers should treat instances that are {@linkplain #equals(Object) equal} as interchangeable and should not
 * use instances for synchronization, or unpredictable behavior may occur. For example, in a future release,
 * synchronization may fail. The {@code equals} method should be used for comparisons.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument, or an array argument containing one or more {@code null}
 * elements to a method in this class causes a {@link NullPointerException NullPointerException} to be thrown. </p>
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 */
public sealed interface MemoryAddress extends Addressable permits MemoryAddressImpl {

    /**
     * Returns the raw long value associated with this memory address.
     * @return The raw long value associated with this memory address.
     */
    long toRawLongValue();

    /**
     * Creates a new memory address with given offset (in bytes), which might be negative, from current one.
     * @param offset specified offset (in bytes), relative to this address, which should be used to create the new address.
     * @return a new memory address with given offset from current one.
     */
    MemoryAddress addOffset(long offset);

    /**
     * Reads a UTF-8 encoded, null-terminated string from this address and offset.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param offset offset in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + offset}.
     * @return a Java string constructed from the bytes read from the given starting address ({@code toRowLongValue() + offset})
     * up to (but not including) the first {@code '\0'} terminator character (assuming one is found).
     * @throws IllegalArgumentException if the size of the native string is greater than the largest string supported by the platform.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    String getUtf8String(long offset);

    /**
     * Writes the given string to this address at given offset, converting it to a null-terminated byte sequence using UTF-8 encoding.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     * @param offset offset in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + offset}.
     * @param str the Java string to be written at this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void setUtf8String(long offset, String str);

    /**
     * Compares the specified object with this address for equality. Returns {@code true} if and only if the specified
     * object is also an address, and it refers to the same memory location as this address.
     *
     * @param that the object to be compared for equality with this address.
     * @return {@code true} if the specified object is equal to this address.
     */
    @Override
    boolean equals(Object that);

    /**
     * Returns the hash code value for this address.
     * @return the hash code value for this address.
     */
    @Override
    int hashCode();

    /**
     * The native memory address instance modelling the {@code NULL} address.
     */
    MemoryAddress NULL = new MemoryAddressImpl(0L);

    /**
     * Obtain a native memory address instance from given long address.
     * @param value the long address.
     * @return the new memory address instance.
     */
    static MemoryAddress ofLong(long value) {
        return value == 0 ?
                NULL :
                new MemoryAddressImpl(value);
    }

    /**
     * Reads a byte from this address and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + offset}.
     * @return a byte value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    byte get(ValueLayout.OfByte layout, long offset);

    /**
     * Writes a byte to this address instance and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + offset}.
     * @param value the byte value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void set(ValueLayout.OfByte layout, long offset, byte value);

    /**
     * Reads a boolean from this address and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + offset}.
     * @return a boolean value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    boolean get(ValueLayout.OfBoolean layout, long offset);

    /**
     * Writes a boolean to this address instance and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + offset}.
     * @param value the boolean value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void set(ValueLayout.OfBoolean layout, long offset, boolean value);

    /**
     * Reads a char from this address and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + offset}.
     * @return a char value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    char get(ValueLayout.OfChar layout, long offset);

    /**
     * Writes a char to this address instance and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + offset}.
     * @param value the char value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void set(ValueLayout.OfChar layout, long offset, char value);

    /**
     * Reads a short from this address and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + offset}.
     * @return a short value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    short get(ValueLayout.OfShort layout, long offset);

    /**
     * Writes a short to this address instance and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + offset}.
     * @param value the short value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void set(ValueLayout.OfShort layout, long offset, short value);

    /**
     * Reads an int from this address and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + offset}.
     * @return an int value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    int get(ValueLayout.OfInt layout, long offset);

    /**
     * Writes an int to this address instance and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + offset}.
     * @param value the int value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void set(ValueLayout.OfInt layout, long offset, int value);

    /**
     * Reads a float from this address and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + offset}.
     * @return a float value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    float get(ValueLayout.OfFloat layout, long offset);

    /**
     * Writes a float to this address instance and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + offset}.
     * @param value the float value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void set(ValueLayout.OfFloat layout, long offset, float value);

    /**
     * Reads a long from this address and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + offset}.
     * @return a long value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    long get(ValueLayout.OfLong layout, long offset);

    /**
     * Writes a long to this address instance and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + offset}.
     * @param value the long value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void set(ValueLayout.OfLong layout, long offset, long value);

    /**
     * Reads a double from this address and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + offset}.
     * @return a double value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    double get(ValueLayout.OfDouble layout, long offset);

    /**
     * Writes a double to this address instance and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + offset}.
     * @param value the double value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void set(ValueLayout.OfDouble layout, long offset, double value);

    /**
     * Reads an address from this address and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param offset offset in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + offset}.
     * @return an address value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    MemoryAddress get(ValueLayout.OfAddress layout, long offset);

    /**
     * Writes an address to this address instance and offset with given layout.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param offset offset in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + offset}.
     * @param value the address value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void set(ValueLayout.OfAddress layout, long offset, Addressable value);

    /**
     * Reads a char from this address and index, scaled by given layout size.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + (index * layout.byteSize())}.
     * @return a char value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    char getAtIndex(ValueLayout.OfChar layout, long index);

    /**
     * Writes a char to this address instance and index, scaled by given layout size.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + (index * layout.byteSize())}.
     * @param value the char value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void setAtIndex(ValueLayout.OfChar layout, long index, char value);

    /**
     * Reads a short from this address and index, scaled by given layout size.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + (index * layout.byteSize())}.
     * @return a short value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    short getAtIndex(ValueLayout.OfShort layout, long index);

    /**
     * Writes a short to this address instance and index, scaled by given layout size.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + (index * layout.byteSize())}.
     * @param value the short value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void setAtIndex(ValueLayout.OfShort layout, long index, short value);

    /**
     * Reads an int from this address and index, scaled by given layout size.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + (index * layout.byteSize())}.
     * @return an int value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    int getAtIndex(ValueLayout.OfInt layout, long index);

    /**
     * Writes an int to this address instance and index, scaled by given layout size.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + (index * layout.byteSize())}.
     * @param value the int value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void setAtIndex(ValueLayout.OfInt layout, long index, int value);

    /**
     * Reads a float from this address and index, scaled by given layout size.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + (index * layout.byteSize())}.
     * @return a float value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    float getAtIndex(ValueLayout.OfFloat layout, long index);

    /**
     * Writes a float to this address instance and index, scaled by given layout size.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + (index * layout.byteSize())}.
     * @param value the float value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void setAtIndex(ValueLayout.OfFloat layout, long index, float value);

    /**
     * Reads a long from this address and index, scaled by given layout size.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + (index * layout.byteSize())}.
     * @return a long value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    long getAtIndex(ValueLayout.OfLong layout, long index);

    /**
     * Writes a long to this address instance and index, scaled by given layout size.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + (index * layout.byteSize())}.
     * @param value the long value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void setAtIndex(ValueLayout.OfLong layout, long index, long value);

    /**
     * Reads a double from this address and index, scaled by given layout size.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + (index * layout.byteSize())}.
     * @return a double value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    double getAtIndex(ValueLayout.OfDouble layout, long index);

    /**
     * Writes a double to this address instance and index, scaled by given layout size.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + (index * layout.byteSize())}.
     * @param value the double value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void setAtIndex(ValueLayout.OfDouble layout, long index, double value);

    /**
     * Reads an address from this address and index, scaled by given layout size.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be read.
     * @param index index in bytes (relative to this address). The final address of this read operation can be expressed as {@code toRowLongValue() + (index * layout.byteSize())}.
     * @return an address value read from this address.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    MemoryAddress getAtIndex(ValueLayout.OfAddress layout, long index);

    /**
     * Writes an address to this address instance and index, scaled by given layout size.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param layout the layout of the memory region to be written.
     * @param index index in bytes (relative to this address). The final address of this write operation can be expressed as {@code toRowLongValue() + (index * layout.byteSize())}.
     * @param value the address value to be written.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    void setAtIndex(ValueLayout.OfAddress layout, long index, Addressable value);
}
