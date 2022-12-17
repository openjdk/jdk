/*
 *  Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.foreign;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.foreign.abi.aarch64.linux.LinuxAArch64VaList;
import jdk.internal.foreign.abi.aarch64.macos.MacOsAArch64VaList;
import jdk.internal.foreign.abi.x64.sysv.SysVVaList;
import jdk.internal.foreign.abi.x64.windows.WinVaList;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

/**
 * Helper class to create and manipulate variable argument lists, similar in functionality to a C {@code va_list}.
 * <p>
 * A variable argument list can be created using the {@link #make(Consumer, SegmentScope)} factory, as follows:
 * {@snippet lang = java:
 * VaList vaList = VaList.make(builder ->
 *                                    builder.addVarg(C_INT, 42)
 *                                           .addVarg(C_DOUBLE, 3.8d));
 *}
 * Once created, clients can obtain the platform-dependent {@linkplain #segment() memory segment} associated with a variable
 * argument list, which can then be passed to {@linkplain Linker#downcallHandle(FunctionDescriptor, Linker.Option...) downcall method handles}
 * targeting native functions using the C {@code va_list} type.
 * <p>
 * The contents of a foreign memory segment modelling a variable argument list can be accessed by <em>unsafely</em> creating
 * a variable argument list, as follows:
 * {@snippet lang = java:
 * void upcall(int n, MemorySegment vaListSegment) {
 *    try (Arena arena = Arena.openConfined()) {
 *        VaList vaList = VaList.ofAddress(vaListSegment.address(), arena.scope());
 *        VaList copy = vaList.copy();
 *        int i = vaList.nextVarg(C_INT);
 *        double d = vaList.nextVarg(C_DOUBLE);
 *        // and again
 *        int i = copy.nextVarg(C_INT);
 *        double d = copy.nextVarg(C_DOUBLE);
 *     }
 * }
 *}
 * The above method receives a foreign segment modelling a variable argument list; the contents of the segment are accessed by creating
 * a new variable argument list, from the segment address. Note that the variable argument list is first copied into
 * a second list before any element is accessed: this will allow us to iterate through the elements twice. Elements in
 * the variable argument list are accessed using {@link #nextVarg(ValueLayout.OfInt)} and
 * {@link #nextVarg(ValueLayout.OfDouble)}. These methods (as well as other access methods in the {@link VaList} class)
 * take the layout of the element that needs to be accessed and perform all the necessary alignment checks as well
 * as endianness conversions.
 * <p>
 * Per the C specification (see C99 standard 6.5.2.2 Function calls - item 6),
 * arguments to variadic calls are erased by way of 'default argument promotions',
 * which erases integral types by way of integer promotion (see C99 standard 6.3.1.1 - item 2),
 * and which erases all {@code float} arguments to {@code double}.
 * <p>
 * As such, this interface only supports reading {@code int}, {@code double},
 * and any other type that fits into a {@code long}.
 * <h2 id="safety">Safety considerations</h2>
 * Accessing a value through a variable argument list using the wrong memory layout will result in undefined behavior.
 * For instance, if a variable argument list currently points at a C {@code int} value, then accessing it using
 * {@link #nextVarg(ValueLayout.OfLong)} is illegal. Similarly, accessing the variable argument list with
 * {@link #skip(MemoryLayout...)}, and providing a layout other than {@link ValueLayout.OfInt} is illegal.
 * Any such illegal accesses might not be detected by the implementation, and can corrupt the variable argument list,
 * so that the behavior of subsequent accesses is also undefined.
 * <p>
 * It is possible for clients to access elements outside the spatial bounds of a variable argument list.
 * Variable argument list implementations will try to detect out-of-bounds reads on a best-effort basis.
 * <p>
 * Whether this detection succeeds depends on the factory method used to create the variable argument list:
 * <ul>
 *     <li>Variable argument lists created <em>safely</em>, using {@link #make(Consumer, SegmentScope)} are capable of detecting out-of-bounds reads;</li>
 *     <li>Variable argument lists created <em>unsafely</em>, using {@link #ofAddress(long, SegmentScope)} are not capable of detecting out-of-bounds reads</li>
 * </ul>
 * <p>
 * This class is not thread safe, and all accesses should occur within a single thread
 * (regardless of the scope used to obtain the variable arity list).
 *
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed interface VaList permits WinVaList, SysVVaList, LinuxAArch64VaList, MacOsAArch64VaList, SharedUtils.EmptyVaList {

    /**
     * Reads the next value as an {@code int} and advances this variable argument list's position. The behavior of this
     * method is equivalent to the C {@code va_arg} function.
     *
     * @param layout the layout of the value to be read.
     * @return the {@code int} value read from this variable argument list.
     * @throws IllegalStateException if the scope associated with this variable argument list is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code segment().scope().isAccessibleBy(T) == false}.
     * @throws NoSuchElementException if an <a href=VaList.html#safety>out-of-bounds</a> read is detected.
     */
    int nextVarg(ValueLayout.OfInt layout);

    /**
     * Reads the next value as a {@code long} and advances this variable argument list's position. The behavior of this
     * method is equivalent to the C {@code va_arg} function.
     *
     * @param layout the layout of the value to be read.
     * @return the {@code long} value read from this variable argument list.
     * @throws IllegalStateException if the scope associated with this variable argument list is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code segment().scope().isAccessibleBy(T) == false}.
     * @throws NoSuchElementException if an <a href=VaList.html#safety>out-of-bounds</a> read is detected.
     */
    long nextVarg(ValueLayout.OfLong layout);

    /**
     * Reads the next value as a {@code double} and advances this variable argument list's position. The behavior of this
     * method is equivalent to the C {@code va_arg} function.
     *
     * @param layout the layout of the value
     * @return the {@code double} value read from this variable argument list.
     * @throws IllegalStateException if the scope associated with this variable argument list is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code segment().scope().isAccessibleBy(T) == false}.
     * @throws NoSuchElementException if an <a href=VaList.html#safety>out-of-bounds</a> read is detected.
     */
    double nextVarg(ValueLayout.OfDouble layout);

    /**
     * Reads the next address value, wraps it into a native segment, and advances this variable argument list's position.
     * The behavior of this method is equivalent to the C {@code va_arg} function. The returned segment's base
     * {@linkplain MemorySegment#address()} is set to the value read from the variable argument list, and the segment
     * is associated with the {@linkplain SegmentScope#global() global scope}. Under normal conditions, the size of the returned
     * segment is {@code 0}. However, if the provided layout is an {@linkplain ValueLayout.OfAddress#asUnbounded() unbounded}
     * address layout, then the size of the returned segment is {@code Long.MAX_VALUE}.
     *
     * @param layout the layout of the value to be read.
     * @return a native segment whose {@linkplain MemorySegment#address() address} is the value read from
     * this variable argument list.
     * @throws IllegalStateException if the scope associated with this variable argument list is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code segment().scope().isAccessibleBy(T) == false}.
     * @throws NoSuchElementException if an <a href=VaList.html#safety>out-of-bounds</a> read is detected.
     */
    MemorySegment nextVarg(ValueLayout.OfAddress layout);

    /**
     * Reads the next composite value into a new {@code MemorySegment}, allocated with the provided allocator,
     * and advances this variable argument list's position. The behavior of this method is equivalent to the C
     * {@code va_arg} function. The provided group layout must correspond to a C struct or union type.
     * <p>
     * How the value is read in the returned segment is ABI-dependent: calling this method on a group layout
     * with member layouts {@code L_1, L_2, ... L_n} is not guaranteed to be semantically equivalent to perform distinct
     * calls to {@code nextVarg} for each of the layouts in {@code L_1, L_2, ... L_n}.
     * <p>
     * The memory segment returned by this method will be allocated using the given {@link SegmentAllocator}.
     *
     * @param layout the layout of the value to be read.
     * @param allocator the allocator to be used to create a segment where the contents of the variable argument list
     *                  will be copied.
     * @return the {@code MemorySegment} value read from this variable argument list.
     * @throws IllegalStateException if the scope associated with this variable argument list is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code segment().scope().isAccessibleBy(T) == false}.
     * @throws NoSuchElementException if an <a href=VaList.html#safety>out-of-bounds</a> read is detected.
     */
    MemorySegment nextVarg(GroupLayout layout, SegmentAllocator allocator);

    /**
     * Skips a number of elements with the given memory layouts, and advances this variable argument list's position.
     *
     * @param layouts the layouts of the values to be skipped.
     * @throws IllegalStateException if the scope associated with this variable argument list is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code segment().scope().isAccessibleBy(T) == false}.
     * @throws NoSuchElementException if an <a href=VaList.html#safety>out-of-bounds</a> read is detected.
     */
    void skip(MemoryLayout... layouts);

    /**
     * Copies this variable argument list at its current position into a new variable argument list associated
     * with the same scope as this variable argument list. The behavior of this method is equivalent to the C
     * {@code va_copy} function.
     * <p>
     * Copying is useful to traverse the variable argument list elements, starting from the current position,
     * without affecting the state of the original variable argument list, essentially allowing the elements to be
     * traversed multiple times.
     *
     * @return a copy of this variable argument list.
     * @throws IllegalStateException if the scope associated with this variable argument list is not
     * {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code segment().scope().isAccessibleBy(T) == false}.
     */
    VaList copy();

    /**
     * Returns a zero-length {@linkplain MemorySegment memory segment} associated with this variable argument list.
     * The contents of the returned memory segment are platform-dependent. Whether and how the contents of
     * the returned segment are updated when iterating the contents of a variable argument list is also
     * platform-dependent.
     * @return a zero-length {@linkplain MemorySegment memory segment} associated with this variable argument list.
     */
    MemorySegment segment();

    /**
     * Creates a variable argument list from the give address value and scope. The address is typically obtained
     * by calling {@link MemorySegment#address()} on a foreign memory segment instance. The provided scope determines
     * the lifecycle of the returned variable argument list: the returned variable argument list will no longer be accessible,
     * and its associated off-heap memory region will be deallocated when the scope becomes not
     * {@linkplain SegmentScope#isAlive() alive}.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param address the address of the variable argument list.
     * @param scope the scope associated with the returned variable argument list.
     * @return a new variable argument list backed by an off-heap region of memory starting at the given address value.
     * @throws IllegalStateException         if {@code scope} is not {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException          if this method is called from a thread {@code T},
     *                                       such that {@code scope.isAccessibleBy(T) == false}.
     * @throws UnsupportedOperationException if the underlying native platform is not supported.
     * @throws IllegalCallerException If the caller is in a module that does not have native access enabled.
     */
    @CallerSensitive
    static VaList ofAddress(long address, SegmentScope scope) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), VaList.class, "ofAddress");
        Objects.requireNonNull(scope);
        return SharedUtils.newVaListOfAddress(address, scope);
    }

    /**
     * Creates a variable argument list using a builder (see {@link Builder}), with the given
     * scope. The provided scope determines the lifecycle of the returned variable argument list: the
     * returned variable argument list will no longer be accessible, and its associated off-heap memory region will be
     * deallocated when the scope becomes not {@linkplain SegmentScope#isAlive() alive}.
     * <p>
     * Note that when there are no elements added to the created va list,
     * this method will return the same as {@link #empty()}.
     *
     * @implNote variable argument lists created using this method can detect <a href=VaList.html#safety>out-of-bounds</a> reads.
     *
     * @param actions a consumer for a builder (see {@link Builder}) which can be used to specify the elements
     *                of the underlying variable argument list.
     * @param scope the scope to be associated with the new variable arity list.
     * @return a new variable argument list.
     * @throws UnsupportedOperationException if the underlying native platform is not supported.
     * @throws IllegalStateException if {@code scope} is not {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope.isAccessibleBy(T) == false}.
     */
    static VaList make(Consumer<Builder> actions, SegmentScope scope) {
        Objects.requireNonNull(actions);
        Objects.requireNonNull(scope);
        return SharedUtils.newVaList(actions, scope);
    }

    /**
     * Returns an empty variable argument list, associated with the {@linkplain SegmentScope#global() global scope}.
     * The resulting variable argument list does not contain any argument, and throws {@link UnsupportedOperationException}
     * on all operations, except for {@link VaList#segment()}, {@link VaList#copy()}.
     * @return an empty variable argument list.
     * @throws UnsupportedOperationException if the underlying native platform is not supported.
     */
    static VaList empty() {
        return SharedUtils.emptyVaList();
    }

    /**
     * A builder used to construct a {@linkplain VaList variable argument list}.
     *
     * @since 19
     */
    @PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
    sealed interface Builder permits WinVaList.Builder, SysVVaList.Builder, LinuxAArch64VaList.Builder, MacOsAArch64VaList.Builder {

        /**
         * Writes an {@code int} value to the variable argument list being constructed.
         *
         * @param layout the layout of the value to be written.
         * @param value the {@code int} value to be written.
         * @return this builder.
         */
        Builder addVarg(ValueLayout.OfInt layout, int value);

        /**
         * Writes a {@code long} value to the variable argument list being constructed.
         *
         * @param layout the layout of the value to be written.
         * @param value the {@code long} value to be written.
         * @return this builder.
         */
        Builder addVarg(ValueLayout.OfLong layout, long value);

        /**
         * Writes a {@code double} value to the variable argument list being constructed.
         *
         * @param layout the layout of the value to be written.
         * @param value the {@code double} value to be written.
         * @return this builder.
         */
        Builder addVarg(ValueLayout.OfDouble layout, double value);

        /**
         * Writes the {@linkplain MemorySegment#address() address} of the provided native segment
         * to the variable argument list being constructed.
         *
         * @param layout the layout of the value to be written.
         * @param segment the segment whose {@linkplain MemorySegment#address() address} is to be written.
         * @return this builder.
         */
        Builder addVarg(ValueLayout.OfAddress layout, MemorySegment segment);

        /**
         * Writes a {@code MemorySegment}, with the given layout, to the variable argument list being constructed.
         *
         * @param layout the layout of the value to be written.
         * @param value the {@code MemorySegment} whose contents will be copied.
         * @return this builder.
         */
        Builder addVarg(GroupLayout layout, MemorySegment value);
    }
}
