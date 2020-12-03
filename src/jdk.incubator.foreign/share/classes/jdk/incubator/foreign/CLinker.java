/*
 *  Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.foreign.NativeMemorySegmentImpl;
import jdk.internal.foreign.PlatformLayouts;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.SharedUtils;

import java.lang.constant.Constable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static jdk.internal.foreign.PlatformLayouts.*;

/**
 * A C linker implements the C Application Binary Interface (ABI) calling conventions.
 * Instances of this interface can be used to link foreign functions in native libraries that
 * follow the JVM's target platform C ABI.
 * <p>
 * Linking a foreign function is a process which requires two components: a method type, and
 * a function descriptor. The method type, consists of a set of <em>carrier</em> types, which, together,
 * specify the Java signature which clients must adhere to when calling the underlying foreign function.
 * The function descriptor contains a set of memory layouts which, together, specify the foreign function
 * signature and classification information (via a custom layout attributes, see {@link TypeKind}), so that linking can take place.
 * <p>
 * Clients of this API can build function descriptors using the predefined memory layout constants
 * (based on a subset of the built-in types provided by the C language), found in this interface; alternatively,
 * they can also decorate existing value layouts using the required {@link TypeKind} classification attribute
 * (this can be done using the {@link MemoryLayout#withAttribute(String, Constable)} method). A failure to do so might
 * result in linkage errors, given that linking requires additional classification information to determine, for instance,
 * how arguments should be loaded into registers during a foreign function call.
 * <p>
 * Implementations of this interface support the following primitive carrier types:
 * {@code byte}, {@code short}, {@code char}, {@code int}, {@code long}, {@code float},
 * and {@code double}, as well as {@link MemoryAddress} for passing pointers, and
 * {@link MemorySegment} for passing structs and unions. Finally, the {@link VaList}
 * carrier type can be used to match the native {@code va_list} type.
 * <p>
 * For the linking process to be successful, some requirements must be satisfied; if {@code M} and {@code F} are
 * the method type and the function descriptor, respectively, used during the linking process, then it must be that:
 * <ul>
 *     <li>The arity of {@code M} is the same as that of {@code F};</li>
 *     <li>If the return type of {@code M} is {@code void}, then {@code F} should have no return layout
 *     (see {@link FunctionDescriptor#ofVoid(MemoryLayout...)});</li>
 *     <li>for each pair of carrier type {@code C} and layout {@code L} in {@code M} and {@code F}, respectively,
 *     where {@code C} and {@code L} refer to the same argument, or to the return value, the following conditions must hold:
 *     <ul>
 *       <li>If {@code C} is a primitve type, then {@code L} must be a {@code ValueLayout}, and the size of the layout must match
 *       that of the carrier type (see {@link Integer#SIZE} and similar fields in other primitive wrapper classes);</li>
 *       <li>If {@code C} is {@code MemoryAddress.class}, then {@code L} must be a {@code ValueLayout}, and its size must match
 *       the platform's address size (see {@link MemoryLayouts#ADDRESS}). For this purpose, the {@link CLinker#C_POINTER} layout
 *       constant can  be used;</li>
 *       <li>If {@code C} is {@code MemorySegment.class}, then {@code L} must be a {@code GroupLayout}</li>
 *       <li>If {@code C} is {@code VaList.class}, then {@code L} must be {@link CLinker#C_VA_LIST}</li>
 *     </ul>
 *     </li>
 * </ul>
 *
 * <p>Variadic functions, declared in C either with a trailing ellipses ({@code ...}) at the end of the formal parameter
 * list or with an empty formal parameter list, are not supported directly. It is not possible to create a method handle
 * that takes a variable number of arguments, and neither is it possible to create an upcall stub wrapping a method
 * handle that accepts a variable number of arguments. However, for downcalls only, it is possible to link a native
 * variadic function by using a <em>specialized</em> method type and function descriptor: for each argument that is to be
 * passed as a variadic argument, an explicit, additional, carrier type and memory layout must be present in the method type and
 * function descriptor objects passed to the linker. Furthermore, as memory layouts corresponding to variadic arguments in
 * a function descriptor must contain additional classification information, it is required that
 * {@link #asVarArg(MemoryLayout)} is used to create the memory layouts for each parameter corresponding to a variadic
 * argument in a specialized function descriptor.
 *
 * <p>On unsupported platforms this class will fail to initialize with an {@link ExceptionInInitializerError}.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument, or an array argument containing one or more {@code null}
 * elements to a method in this class causes a {@link NullPointerException NullPointerException} to be thrown. </p>
 *
 * @apiNote In the future, if the Java language permits, {@link CLinker}
 * may become a {@code sealed} interface, which would prohibit subclassing except by
 * explicitly permitted types.
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 */
public interface CLinker {

    /**
     * Returns the C linker for the current platform.
     * <p>
     * This method is <em>restricted</em>. Restricted method are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     * @return a linker for this system.
     * @throws IllegalAccessError if the runtime property {@code foreign.restricted} is not set to either
     * {@code permit}, {@code warn} or {@code debug} (the default value is set to {@code deny}).
     */
    static CLinker getInstance() {
        Utils.checkRestrictedAccess("CLinker.getInstance");
        return SharedUtils.getSystemLinker();
    }

    /**
     * Obtain a foreign method handle, with given type, which can be used to call a
     * target foreign function at a given address and featuring a given function descriptor.
     *
     * @see LibraryLookup#lookup(String)
     *
     * @param symbol   downcall symbol.
     * @param type     the method type.
     * @param function the function descriptor.
     * @return the downcall method handle.
     * @throws IllegalArgumentException in the case of a method type and function descriptor mismatch.
     */
    MethodHandle downcallHandle(Addressable symbol, MethodType type, FunctionDescriptor function);

    /**
     * Allocates a native segment whose base address (see {@link MemorySegment#address}) can be
     * passed to other foreign functions (as a function pointer); calling such a function pointer
     * from native code will result in the execution of the provided method handle.
     *
     * <p>The returned segment is <a href=MemorySegment.html#thread-confinement>shared</a>, and it only features
     * the {@link MemorySegment#CLOSE} access mode. When the returned segment is closed,
     * the corresponding native stub will be deallocated.</p>
     *
     * @param target   the target method handle.
     * @param function the function descriptor.
     * @return the native stub segment.
     * @throws IllegalArgumentException if the target's method type and the function descriptor mismatch.
     */
    MemorySegment upcallStub(MethodHandle target, FunctionDescriptor function);

    /**
     * The layout for the {@code char} C type
     */
    ValueLayout C_CHAR = pick(SysV.C_CHAR, Win64.C_CHAR, AArch64.C_CHAR);
    /**
     * The layout for the {@code short} C type
     */
    ValueLayout C_SHORT = pick(SysV.C_SHORT, Win64.C_SHORT, AArch64.C_SHORT);
    /**
     * The layout for the {@code int} C type
     */
    ValueLayout C_INT = pick(SysV.C_INT, Win64.C_INT, AArch64.C_INT);
    /**
     * The layout for the {@code long} C type
     */
    ValueLayout C_LONG = pick(SysV.C_LONG, Win64.C_LONG, AArch64.C_LONG);
    /**
     * The layout for the {@code long long} C type.
     */
    ValueLayout C_LONG_LONG = pick(SysV.C_LONG_LONG, Win64.C_LONG_LONG, AArch64.C_LONG_LONG);
    /**
     * The layout for the {@code float} C type
     */
    ValueLayout C_FLOAT = pick(SysV.C_FLOAT, Win64.C_FLOAT, AArch64.C_FLOAT);
    /**
     * The layout for the {@code double} C type
     */
    ValueLayout C_DOUBLE = pick(SysV.C_DOUBLE, Win64.C_DOUBLE, AArch64.C_DOUBLE);
    /**
     * The {@code T*} native type.
     */
    ValueLayout C_POINTER = pick(SysV.C_POINTER, Win64.C_POINTER, AArch64.C_POINTER);
    /**
     * The layout for the {@code va_list} C type
     */
    MemoryLayout C_VA_LIST = pick(SysV.C_VA_LIST, Win64.C_VA_LIST, AArch64.C_VA_LIST);

    /**
     * Returns a memory layout that is suitable to use as the layout for variadic arguments in a specialized
     * function descriptor.
     * @param <T> the memory layout type
     * @param layout the layout the adapt
     * @return a potentially newly created layout with the right attributes
     */
    @SuppressWarnings("unchecked")
    static <T extends MemoryLayout> T asVarArg(T layout) {
        Objects.requireNonNull(layout);
        return (T) PlatformLayouts.asVarArg(layout);
    }

    /**
     * Converts a Java string into a null-terminated C string, using the
     * platform's default charset, storing the result into a new native memory segment.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement byte array.  The
     * {@link java.nio.charset.CharsetEncoder} class should be used when more
     * control over the encoding process is required.
     *
     * @param str the Java string to be converted into a C string.
     * @return a new native memory segment containing the converted C string.
     */
    static MemorySegment toCString(String str) {
        Objects.requireNonNull(str);
        return toCString(str.getBytes());
    }

    /**
     * Converts a Java string into a null-terminated C string, using the given {@link java.nio.charset.Charset charset},
     * storing the result into a new native memory segment.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement byte array.  The
     * {@link java.nio.charset.CharsetEncoder} class should be used when more
     * control over the encoding process is required.
     *
     * @param str the Java string to be converted into a C string.
     * @param charset The {@link java.nio.charset.Charset} to be used to compute the contents of the C string.
     * @return a new native memory segment containing the converted C string.
     */
    static MemorySegment toCString(String str, Charset charset) {
        Objects.requireNonNull(str);
        Objects.requireNonNull(charset);
        return toCString(str.getBytes(charset));
    }

    /**
     * Converts a Java string into a null-terminated C string, using the platform's default charset,
     * storing the result into a native memory segment allocated using the provided scope.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement byte array.  The
     * {@link java.nio.charset.CharsetEncoder} class should be used when more
     * control over the encoding process is required.
     *
     * @param str the Java string to be converted into a C string.
     * @param scope the scope to be used for the native segment allocation.
     * @return a new native memory segment containing the converted C string.
     */
    static MemorySegment toCString(String str, NativeScope scope) {
        Objects.requireNonNull(str);
        Objects.requireNonNull(scope);
        return toCString(str.getBytes(), scope);
    }

    /**
     * Converts a Java string into a null-terminated C string, using the given {@link java.nio.charset.Charset charset},
     * storing the result into a new native memory segment native memory segment allocated using the provided scope.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement byte array.  The
     * {@link java.nio.charset.CharsetEncoder} class should be used when more
     * control over the encoding process is required.
     *
     * @param str the Java string to be converted into a C string.
     * @param charset The {@link java.nio.charset.Charset} to be used to compute the contents of the C string.
     * @param scope the scope to be used for the native segment allocation.
     * @return a new native memory segment containing the converted C string.
     */
    static MemorySegment toCString(String str, Charset charset, NativeScope scope) {
        Objects.requireNonNull(str);
        Objects.requireNonNull(charset);
        Objects.requireNonNull(scope);
        return toCString(str.getBytes(charset), scope);
    }

    /**
     * Converts a null-terminated C string stored at given address into a Java string, using the platform's default charset.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     * <p>
     * This method is <em>restricted</em>. Restricted method are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     * @param addr the address at which the string is stored.
     * @return a Java string with the contents of the null-terminated C string at given address.
     * @throws IllegalArgumentException if the size of the native string is greater than the largest string supported by the platform.
     */
    static String toJavaStringRestricted(MemoryAddress addr) {
        Utils.checkRestrictedAccess("CLinker.toJavaStringRestricted");
        Objects.requireNonNull(addr);
        return SharedUtils.toJavaStringInternal(NativeMemorySegmentImpl.EVERYTHING, addr.toRawLongValue(), Charset.defaultCharset());
    }

    /**
     * Converts a null-terminated C string stored at given address into a Java string, using the given {@link java.nio.charset.Charset charset}.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     * <p>
     * This method is <em>restricted</em>. Restricted method are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     * @param addr the address at which the string is stored.
     * @param charset The {@link java.nio.charset.Charset} to be used to compute the contents of the Java string.
     * @return a Java string with the contents of the null-terminated C string at given address.
     * @throws IllegalArgumentException if the size of the native string is greater than the largest string supported by the platform.
     */
    static String toJavaStringRestricted(MemoryAddress addr, Charset charset) {
        Utils.checkRestrictedAccess("CLinker.toJavaStringRestricted");
        Objects.requireNonNull(addr);
        Objects.requireNonNull(charset);
        return SharedUtils.toJavaStringInternal(NativeMemorySegmentImpl.EVERYTHING, addr.toRawLongValue(), charset);
    }

    /**
     * Converts a null-terminated C string stored at given address into a Java string, using the platform's default charset.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     * @param addr the address at which the string is stored.
     * @return a Java string with the contents of the null-terminated C string at given address.
     * @throws IllegalArgumentException if the size of the native string is greater than the largest string supported by the platform.
     * @throws IllegalStateException if the size of the native string is greater than the size of the segment
     * associated with {@code addr}, or if {@code addr} is associated with a segment that is <em>not alive</em>.
     */
    static String toJavaString(MemorySegment addr) {
        Objects.requireNonNull(addr);
        return SharedUtils.toJavaStringInternal(addr, 0L, Charset.defaultCharset());
    }

    /**
     * Converts a null-terminated C string stored at given address into a Java string, using the given {@link java.nio.charset.Charset charset}.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     * @param addr the address at which the string is stored.
     * @param charset The {@link java.nio.charset.Charset} to be used to compute the contents of the Java string.
     * @return a Java string with the contents of the null-terminated C string at given address.
     * @throws IllegalArgumentException if the size of the native string is greater than the largest string supported by the platform.
     * @throws IllegalStateException if the size of the native string is greater than the size of the segment
     * associated with {@code addr}, or if {@code addr} is associated with a segment that is <em>not alive</em>.
     */
    static String toJavaString(MemorySegment addr, Charset charset) {
        Objects.requireNonNull(addr);
        Objects.requireNonNull(charset);
        return SharedUtils.toJavaStringInternal(addr, 0L, charset);
    }

    private static void copy(MemorySegment addr, byte[] bytes) {
        var heapSegment = MemorySegment.ofArray(bytes);
        addr.copyFrom(heapSegment);
        MemoryAccess.setByteAtOffset(addr, bytes.length, (byte)0);
    }

    private static MemorySegment toCString(byte[] bytes) {
        MemorySegment segment = MemorySegment.allocateNative(bytes.length + 1, 1L);
        copy(segment, bytes);
        return segment;
    }

    private static MemorySegment toCString(byte[] bytes, NativeScope scope) {
        MemorySegment addr = scope.allocate(bytes.length + 1, 1L);
        copy(addr, bytes);
        return addr;
    }

    /**
     * Allocates memory of given size using malloc.
     * <p>
     * This method is <em>restricted</em>. Restricted method are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param size memory size to be allocated
     * @return addr memory address of the allocated memory
     * @throws OutOfMemoryError if malloc could not allocate the required amount of native memory.
     */
    static MemoryAddress allocateMemoryRestricted(long size) {
        Utils.checkRestrictedAccess("CLinker.allocateMemoryRestricted");
        MemoryAddress addr = SharedUtils.allocateMemoryInternal(size);
        if (addr.equals(MemoryAddress.NULL)) {
            throw new OutOfMemoryError();
        } else {
            return addr;
        }
    }

    /**
     * Frees the memory pointed by the given memory address.
     * <p>
     * This method is <em>restricted</em>. Restricted method are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param addr memory address of the native memory to be freed
     */
    static void freeMemoryRestricted(MemoryAddress addr) {
        Utils.checkRestrictedAccess("CLinker.freeMemoryRestricted");
        Objects.requireNonNull(addr);
        SharedUtils.freeMemoryInternal(addr);
    }

    /**
     * An interface that models a C {@code va_list}.
     * <p>
     * A va list is a stateful cursor used to iterate over a set of variadic arguments.
     * <p>
     * Per the C specification (see C standard 6.5.2.2 Function calls - item 6),
     * arguments to variadic calls are erased by way of 'default argument promotions',
     * which erases integral types by way of integer promotion (see C standard 6.3.1.1 - item 2),
     * and which erases all {@code float} arguments to {@code double}.
     * <p>
     * As such, this interface only supports reading {@code int}, {@code double},
     * and any other type that fits into a {@code long}.
     *
     * <p> Unless otherwise specified, passing a {@code null} argument, or an array argument containing one or more {@code null}
     * elements to a method in this class causes a {@link NullPointerException NullPointerException} to be thrown. </p>
     *
     * @apiNote In the future, if the Java language permits, {@link VaList}
     * may become a {@code sealed} interface, which would prohibit subclassing except by
     * explicitly permitted types.
     *
     */
    interface VaList extends Addressable, AutoCloseable {

        /**
         * Reads the next value as an {@code int} and advances this va list's position.
         *
         * @param layout the layout of the value
         * @return the value read as an {@code int}
         * @throws IllegalStateException if the C {@code va_list} associated with this instance is no longer valid
         * (see {@link #close()}).
         * @throws IllegalArgumentException if the given memory layout is not compatible with {@code int}
         */
        int vargAsInt(MemoryLayout layout);

        /**
         * Reads the next value as a {@code long} and advances this va list's position.
         *
         * @param layout the layout of the value
         * @return the value read as an {@code long}
         * @throws IllegalStateException if the C {@code va_list} associated with this instance is no longer valid
         * (see {@link #close()}).
         * @throws IllegalArgumentException if the given memory layout is not compatible with {@code long}
         */
        long vargAsLong(MemoryLayout layout);

        /**
         * Reads the next value as a {@code double} and advances this va list's position.
         *
         * @param layout the layout of the value
         * @return the value read as an {@code double}
         * @throws IllegalStateException if the C {@code va_list} associated with this instance is no longer valid
         * (see {@link #close()}).
         * @throws IllegalArgumentException if the given memory layout is not compatible with {@code double}
         */
        double vargAsDouble(MemoryLayout layout);

        /**
         * Reads the next value as a {@code MemoryAddress} and advances this va list's position.
         *
         * @param layout the layout of the value
         * @return the value read as an {@code MemoryAddress}
         * @throws IllegalStateException if the C {@code va_list} associated with this instance is no longer valid
         * (see {@link #close()}).
         * @throws IllegalArgumentException if the given memory layout is not compatible with {@code MemoryAddress}
         */
        MemoryAddress vargAsAddress(MemoryLayout layout);

        /**
         * Reads the next value as a {@code MemorySegment}, and advances this va list's position.
         * <p>
         * The memory segment returned by this method will be allocated using
         * {@link MemorySegment#allocateNative(long, long)}, and will have to be closed separately.
         *
         * @param layout the layout of the value
         * @return the value read as an {@code MemorySegment}
         * @throws IllegalStateException if the C {@code va_list} associated with this instance is no longer valid
         * (see {@link #close()}).
         * @throws IllegalArgumentException if the given memory layout is not compatible with {@code MemorySegment}
         */
        MemorySegment vargAsSegment(MemoryLayout layout);

        /**
         * Reads the next value as a {@code MemorySegment}, and advances this va list's position.
         * <p>
         * The memory segment returned by this method will be allocated using the given {@code NativeScope}.
         *
         * @param layout the layout of the value
         * @param scope the scope to allocate the segment in
         * @return the value read as an {@code MemorySegment}
         * @throws IllegalStateException if the C {@code va_list} associated with this instance is no longer valid
         * (see {@link #close()}).
         * @throws IllegalArgumentException if the given memory layout is not compatible with {@code MemorySegment}
         */
        MemorySegment vargAsSegment(MemoryLayout layout, NativeScope scope);

        /**
         * Skips a number of elements with the given memory layouts, and advances this va list's position.
         *
         * @param layouts the layout of the value
         * @throws IllegalStateException if the C {@code va_list} associated with this instance is no longer valid
         * (see {@link #close()}).
         */
        void skip(MemoryLayout... layouts);

        /**
         * A predicate used to check if the memory associated with the C {@code va_list} modelled
         * by this instance is still valid to use.
         *
         * @return true, if the memory associated with the C {@code va_list} modelled by this instance is still valid
         * @see #close()
         */
        boolean isAlive();

        /**
         * Releases the underlying C {@code va_list} modelled by this instance, and any native memory that is attached
         * to this va list that holds its elements (see {@link VaList#make(Consumer)}).
         * <p>
         * After calling this method, {@link #isAlive()} will return {@code false} and further attempts to read values
         * from this va list will result in an exception.
         *
         * @see #isAlive()
         */
        void close();

        /**
         * Copies this C {@code va_list} at its current position. Copying is useful to traverse the va list's elements
         * starting from the current position, without affecting the state of the original va list, essentially
         * allowing the elements to be traversed multiple times.
         * <p>
         * If this method needs to allocate native memory for the copy, it will use
         * {@link MemorySegment#allocateNative(long, long)} to do so. {@link #close()} will have to be called on the
         * returned va list instance to release the allocated memory.
         * <p>
         * This method only copies the va list cursor itself and not the memory that may be attached to the
         * va list which holds its elements. That means that if this va list was created with the
         * {@link #make(Consumer)} method, closing this va list will also release the native memory that holds its
         * elements, making the copy unusable.
         *
         * @return a copy of this C {@code va_list}.
         * @throws IllegalStateException if the C {@code va_list} associated with this instance is no longer valid
         * (see {@link #close()}).
         */
        VaList copy();

        /**
         * Copies this C {@code va_list} at its current position. Copying is useful to traverse the va list's elements
         * starting from the current position, without affecting the state of the original va list, essentially
         * allowing the elements to be traversed multiple times.
         * <p>
         * If this method needs to allocate native memory for the copy, it will use
         * the given {@code NativeScope} to do so.
         * <p>
         * This method only copies the va list cursor itself and not the memory that may be attached to the
         * va list which holds its elements. That means that if this va list was created with the
         * {@link #make(Consumer)} method, closing this va list will also release the native memory that holds its
         * elements, making the copy unusable.
         *
         * @param scope the scope to allocate the copy in
         * @return a copy of this C {@code va_list}.
         * @throws IllegalStateException if the C {@code va_list} associated with this instance is no longer valid
         * (see {@link #close()}).
         */
        VaList copy(NativeScope scope);

        /**
         * Returns the memory address of the C {@code va_list} associated with this instance.
         *
         * @return the memory address of the C {@code va_list} associated with this instance.
         */
        @Override
        MemoryAddress address();

        /**
         * Constructs a new {@code VaList} instance out of a memory address pointing to an existing C {@code va_list}.
         * <p>
         * This method is <em>restricted</em>. Restricted method are unsafe, and, if used incorrectly, their use might crash
         * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
         * restricted methods, and use safe and supported functionalities, where possible.
         *
         * @param address a memory address pointing to an existing C {@code va_list}.
         * @return a new {@code VaList} instance backed by the C {@code va_list} at {@code address}.
         */
        static VaList ofAddressRestricted(MemoryAddress address) {
            Utils.checkRestrictedAccess("VaList.ofAddressRestricted");
            Objects.requireNonNull(address);
            return SharedUtils.newVaListOfAddress(address);
        }

        /**
         * Constructs a new {@code VaList} using a builder (see {@link Builder}).
         * <p>
         * If this method needs to allocate native memory for the va list, it will use
         * {@link MemorySegment#allocateNative(long, long)} to do so.
         * <p>
         * This method will allocate native memory to hold the elements in the va list. This memory
         * will be 'attached' to the returned va list instance, and will be released when {@link VaList#close()}
         * is called.
         * <p>
         * Note that when there are no elements added to the created va list,
         * this method will return the same as {@link #empty()}.
         *
         * @param actions a consumer for a builder (see {@link Builder}) which can be used to specify the elements
         *                of the underlying C {@code va_list}.
         * @return a new {@code VaList} instance backed by a fresh C {@code va_list}.
         */
        static VaList make(Consumer<Builder> actions) {
            Objects.requireNonNull(actions);
            return SharedUtils.newVaList(actions, MemorySegment::allocateNative);
        }

        /**
         * Constructs a new {@code VaList} using a builder (see {@link Builder}).
         * <p>
         * If this method needs to allocate native memory for the va list, it will use
         * the given {@code NativeScope} to do so.
         * <p>
         * This method will allocate native memory to hold the elements in the va list. This memory
         * will be managed by the given {@code NativeScope}, and will be released when the scope is closed.
         * <p>
         * Note that when there are no elements added to the created va list,
         * this method will return the same as {@link #empty()}.
         *
         * @param actions a consumer for a builder (see {@link Builder}) which can be used to specify the elements
         *                of the underlying C {@code va_list}.
         * @param scope the scope to be used for the valist allocation.
         * @return a new {@code VaList} instance backed by a fresh C {@code va_list}.
         */
        static VaList make(Consumer<Builder> actions, NativeScope scope) {
            Objects.requireNonNull(actions);
            Objects.requireNonNull(scope);
            return SharedUtils.newVaList(actions, SharedUtils.Allocator.ofScope(scope));
        }

        /**
         * Returns an empty C {@code va_list} constant.
         * <p>
         * The returned {@code VaList} can not be closed.
         *
         * @return a {@code VaList} modelling an empty C {@code va_list}.
         */
        static VaList empty() {
            return SharedUtils.emptyVaList();
        }

        /**
         * A builder interface used to construct a C {@code va_list}.
         *
         * <p> Unless otherwise specified, passing a {@code null} argument, or an array argument containing one or more {@code null}
         * elements to a method in this class causes a {@link NullPointerException NullPointerException} to be thrown. </p>
         *
         * @apiNote In the future, if the Java language permits, {@link Builder}
         * may become a {@code sealed} interface, which would prohibit subclassing except by
         * explicitly permitted types.
         *
         */
        interface Builder {

            /**
             * Adds a native value represented as an {@code int} to the C {@code va_list} being constructed.
             *
             * @param layout the native layout of the value.
             * @param value the value, represented as an {@code int}.
             * @return this builder.
             * @throws IllegalArgumentException if the given memory layout is not compatible with {@code int}
             */
            Builder vargFromInt(ValueLayout layout, int value);

            /**
             * Adds a native value represented as a {@code long} to the C {@code va_list} being constructed.
             *
             * @param layout the native layout of the value.
             * @param value the value, represented as a {@code long}.
             * @return this builder.
             * @throws IllegalArgumentException if the given memory layout is not compatible with {@code long}
             */
            Builder vargFromLong(ValueLayout layout, long value);

            /**
             * Adds a native value represented as a {@code double} to the C {@code va_list} being constructed.
             *
             * @param layout the native layout of the value.
             * @param value the value, represented as a {@code double}.
             * @return this builder.
             * @throws IllegalArgumentException if the given memory layout is not compatible with {@code double}
             */
            Builder vargFromDouble(ValueLayout layout, double value);

            /**
             * Adds a native value represented as a {@code MemoryAddress} to the C {@code va_list} being constructed.
             *
             * @param layout the native layout of the value.
             * @param value the value, represented as a {@code Addressable}.
             * @return this builder.
             * @throws IllegalArgumentException if the given memory layout is not compatible with {@code MemoryAddress}
             */
            Builder vargFromAddress(ValueLayout layout, Addressable value);

            /**
             * Adds a native value represented as a {@code MemorySegment} to the C {@code va_list} being constructed.
             *
             * @param layout the native layout of the value.
             * @param value the value, represented as a {@code MemorySegment}.
             * @return this builder.
             * @throws IllegalArgumentException if the given memory layout is not compatible with {@code MemorySegment}
             */
            Builder vargFromSegment(GroupLayout layout, MemorySegment value);
        }
    }

    /**
     * A C type kind. Each kind corresponds to a particular C language builtin type, and can be attached to
     * {@link ValueLayout} instances using the {@link MemoryLayout#withAttribute(String, Constable)} in order
     * to obtain a layout which can be classified accordingly by {@link CLinker#downcallHandle(Addressable, MethodType, FunctionDescriptor)}
     * and {@link CLinker#upcallStub(MethodHandle, FunctionDescriptor)}.
     */
    enum TypeKind {
        /**
         * A kind corresponding to the <em>integral</em> C {@code char} type
         */
        CHAR(true),
        /**
         * A kind corresponding to the <em>integral</em> C {@code short} type
         */
        SHORT(true),
        /**
         * A kind corresponding to the <em>integral</em> C {@code int} type
         */
        INT(true),
        /**
         * A kind corresponding to the <em>integral</em> C {@code long} type
         */
        LONG(true),
        /**
         * A kind corresponding to the <em>integral</em> C {@code long long} type
         */
        LONG_LONG(true),
        /**
         * A kind corresponding to the <em>floating-point</em> C {@code float} type
         */
        FLOAT(false),
        /**
         * A kind corresponding to the <em>floating-point</em> C {@code double} type
         */
        DOUBLE(false),
        /**
         * A kind corresponding to the an <em>integral</em> C pointer type
         */
        POINTER(false);

        private final boolean isIntegral;

        TypeKind(boolean isIntegral) {
            this.isIntegral = isIntegral;
        }

        /**
         * Is this kind integral?
         *
         * @return true if this kind is integral
         */
        public boolean isIntegral() {
            return isIntegral;
        }

        /**
         * Is this kind a floating point type?
         *
         * @return true if this kind is a floating point type
         */
        public boolean isFloat() {
            return !isIntegral() && !isPointer();
        }

        /**
         * Is this kind a pointer kind?
         *
         * @return true if this kind is a pointer kind
         */
        public boolean isPointer() {
            return this == POINTER;
        }

        /**
         * The layout attribute name associated with this classification kind. Clients can retrieve the type kind
         * of a layout using the following code:
         * <blockquote><pre>{@code
        ValueLayout layout = ...
        TypeKind = layout.attribute(TypeKind.ATTR_NAME).orElse(null);
         * }</pre></blockquote>
         */
        public final static String ATTR_NAME = "abi/kind";
    }
}
