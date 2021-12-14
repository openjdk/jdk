/*
 *  Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.foreign.abi.aarch64.linux.LinuxAArch64VaList;
import jdk.internal.foreign.abi.aarch64.macos.MacOsAArch64VaList;
import jdk.internal.foreign.abi.x64.sysv.SysVVaList;
import jdk.internal.foreign.abi.x64.windows.WinVaList;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * An interface that models a variable argument list, similar in functionality to a C {@code va_list}.
 * <p>
 * A variable argument list is a stateful cursor used to iterate over a set of arguments. A variable argument list
 * can be passed by reference e.g. to a {@linkplain CLinker#downcallHandle(FunctionDescriptor) downcall method handle}.
 * <p>
 * Per the C specification (see C standard 6.5.2.2 Function calls - item 6),
 * arguments to variadic calls are erased by way of 'default argument promotions',
 * which erases integral types by way of integer promotion (see C standard 6.3.1.1 - item 2),
 * and which erases all {@code float} arguments to {@code double}.
 * <p>
 * As such, this interface only supports reading {@code int}, {@code double},
 * and any other type that fits into a {@code long}.
 *
 * This class is not thread safe, and all accesses should occur within a single thread
 * (regardless of the scope associated with the variable arity list).
 *
 * <p> Unless otherwise specified, passing a {@code null} argument, or an array argument containing one or more {@code null}
 * elements to a method in this class causes a {@link NullPointerException NullPointerException} to be thrown. </p>
 */
sealed public interface VaList extends Addressable permits WinVaList, SysVVaList, LinuxAArch64VaList, MacOsAArch64VaList, SharedUtils.EmptyVaList {

    /**
     * Reads the next value as an {@code int} and advances this variable argument list's position.
     *
     * @param layout the layout of the value to be read.
     * @return the {@code int} value read from this variable argument list.
     * @throws IllegalStateException if the scope associated with this variable argument list has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     */
    int nextVarg(ValueLayout.OfInt layout);

    /**
     * Reads the next value as a {@code long} and advances this variable argument list's position.
     *
     * @param layout the layout of the value to be read.
     * @return the {@code long} value read from this variable argument list.
     * @throws IllegalStateException if the scope associated with this variable argument list has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     */
    long nextVarg(ValueLayout.OfLong layout);

    /**
     * Reads the next value as a {@code double} and advances this variable argument list's position.
     *
     * @param layout the layout of the value
     * @return the {@code double} value read from this variable argument list.
     * @throws IllegalStateException if the scope associated with this variable argument list has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     */
    double nextVarg(ValueLayout.OfDouble layout);

    /**
     * Reads the next value as a {@code MemoryAddress} and advances this variable argument list's position.
     *
     * @param layout the layout of the value to be read.
     * @return the {@code MemoryAddress} value read from this variable argument list.
     * @throws IllegalStateException if the scope associated with this variable argument list has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     */
    MemoryAddress nextVarg(ValueLayout.OfAddress layout);

    /**
     * Reads the next value as a {@code MemorySegment}, and advances this variable argument list's position.
     * <p>
     * The memory segment returned by this method will be allocated using the given {@link SegmentAllocator}.
     *
     * @param layout the layout of the value to be read.
     * @param allocator the allocator to be used to create a segment where the contents of the variable argument list
     *                  will be copied.
     * @return the {@code MemorySegment} value read from this variable argument list.
     * @throws IllegalStateException if the scope associated with this variable argument list has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     */
    MemorySegment nextVarg(GroupLayout layout, SegmentAllocator allocator);

    /**
     * Skips a number of elements with the given memory layouts, and advances this variable argument list's position.
     *
     * @param layouts the layouts of the values to be skipped.
     * @throws IllegalStateException if the scope associated with this variable argument list has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     */
    void skip(MemoryLayout... layouts);

    /**
     * Returns the resource scope associated with this variable argument list.
     * @return the resource scope associated with this variable argument list.
     */
    ResourceScope scope();

    /**
     * Copies this variable argument list at its current position into a new variable argument list associated
     * with the same scope as this variable argument list. Copying is useful to
     * traverse the variable argument list elements, starting from the current position, without affecting the state
     * of the original variable argument list, essentially allowing the elements to be traversed multiple times.
     *
     * @return a copy of this variable argument list.
     * @throws IllegalStateException if the scope associated with this variable argument list has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     */
    VaList copy();

    /**
     * Returns the memory address associated with this variable argument list.
     * @throws IllegalStateException if the scope associated with this variable argument list has been closed, or if access occurs from
     * a thread other than the thread owning that scope.
     * @return The memory address associated with this variable argument list.
     */
    @Override
    MemoryAddress address();

    /**
     * Constructs a new variable argument list from a memory address pointing to an existing variable argument list,
     * with given resource scope.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @param address a memory address pointing to an existing variable argument list.
     * @param scope the resource scope to be associated with the returned variable argument list.
     * @return a new variable argument list backed by the memory region at {@code address}.
     * @throws IllegalStateException if {@code scope} has been already closed, or if access occurs from a thread other
     * than the thread owning {@code scope}.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    static VaList ofAddress(MemoryAddress address, ResourceScope scope) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Objects.requireNonNull(address);
        Objects.requireNonNull(scope);
        return SharedUtils.newVaListOfAddress(address, scope);
    }

    /**
     * Constructs a new variable argument list using a builder (see {@link Builder}), with a given resource scope.
     * <p>
     * If this method needs to allocate native memory, such memory will be managed by the given
     * {@linkplain ResourceScope resource scope}, and will be released when the resource scope is {@linkplain ResourceScope#close closed}.
     * <p>
     * Note that when there are no elements added to the created va list,
     * this method will return the same as {@link #empty()}.
     *
     * @param actions a consumer for a builder (see {@link Builder}) which can be used to specify the elements
     *                of the underlying variable argument list.
     * @param scope scope the scope to be associated with the new variable arity list.
     * @return a new variable argument list.
     * @throws IllegalStateException if the scope associated with {@code allocator} has been already closed,
     * or if access occurs from a thread other than the thread owning that scope.
     */
    static VaList make(Consumer<Builder> actions, ResourceScope scope) {
        Objects.requireNonNull(actions);
        Objects.requireNonNull(scope);
        return SharedUtils.newVaList(actions, scope);
    }

    /**
     * Returns an empty variable argument list, associated with the {@linkplain ResourceScope#globalScope() global}
     * scope. The resulting variable argument list does not contain any argument, and throws {@link UnsupportedOperationException}
     * on all operations, except for {@link #scope()}, {@link #copy()} and {@link #address()}.
     * @return an empty variable argument list.
     */
    static VaList empty() {
        return SharedUtils.emptyVaList();
    }

    /**
     * A builder interface used to construct a variable argument list.
     *
     * <p> Unless otherwise specified, passing a {@code null} argument, or an array argument containing one or more {@code null}
     * elements to a method in this class causes a {@link NullPointerException NullPointerException} to be thrown. </p>
     */
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
         * Writes an {@code Addressable} value to the variable argument list being constructed.
         *
         * @param layout the layout of the value to be written.
         * @param value the {@code Addressable} value to be written.
         * @return this builder.
         */
        Builder addVarg(ValueLayout.OfAddress layout, Addressable value);

        /**
         * Writes a {@code MemorySegment} value, with given layout, to the variable argument list being constructed.
         *
         * @param layout the layout of the value to be written.
         * @param value the {@code MemorySegment} whose contents will be copied.
         * @return this builder.
         */
        Builder addVarg(GroupLayout layout, MemorySegment value);
    }
}
