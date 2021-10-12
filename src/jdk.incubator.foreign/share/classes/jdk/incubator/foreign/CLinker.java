/*
 *  Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.foreign.SystemLookup;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.foreign.abi.aarch64.linux.LinuxAArch64Linker;
import jdk.internal.foreign.abi.aarch64.macos.MacOsAArch64Linker;
import jdk.internal.foreign.abi.x64.sysv.SysVx64Linker;
import jdk.internal.foreign.abi.x64.windows.Windowsx64Linker;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

import java.lang.invoke.MethodHandle;
import java.util.Optional;

/**
 * A C linker implements the C Application Binary Interface (ABI) calling conventions.
 * Instances of this interface can be used to link foreign functions in native libraries that
 * follow the JVM's target platform C ABI. A C linker provides two main capabilities: first, it allows Java code
 * to <em>link</em> foreign functions into a so called <em>downcall method handle</em>; secondly, it allows
 * native code to call Java method handles via the generation of <em>upcall stubs</em>.
 * <p>
 * On unsupported platforms this class will fail to initialize with an {@link ExceptionInInitializerError}.
 * <p>
 * Unless otherwise specified, passing a {@code null} argument, or an array argument containing one or more {@code null}
 * elements to a method in this class causes a {@link NullPointerException NullPointerException} to be thrown. </p>
 *
 * <h2><a id = "downcall-method-handles">Downcall method handles</a></h2>
 * <p>
 * {@linkplain #downcallHandle(FunctionDescriptor) Linking a foreign function} is a process which requires a function descriptor,
 * a set of memory layouts which, together, specify the signature of the foreign function to be linked, and returns,
 * when complete, a downcall method handle, that is, a method handle that can be used to invoke the target native function.
 * The Java {@link java.lang.invoke.MethodType method type} associated with the returned method handle is derived from the
 * argument and return layouts in the function descriptor. More specifically, given each layout {@code L} in the
 * function descriptor, a corresponding carrier {@code C} is inferred, as described below:
 * <ul>
 * <li>if {@code L} is a {@link ValueLayout} with carrier {@code E} then there are two cases:
 *     <ul>
 *         <li>if {@code L} occurs in a parameter position and {@code E} is {@code MemoryAddress.class},
 *         then {@code C = Addressable.class};</li>
 *         <li>otherwise, {@code C = E};
 *     </ul></li>
 * <li>or, if {@code L} is a {@link GroupLayout}, then {@code C} is set to {@code MemorySegment.class}</li>
 * </ul>
 * <p>
 * Arguments of type {@link MemorySegment}, {@link VaList} and {@link NativeSymbol} passed by-reference to a downcall method handle
 * are {@linkplain ResourceScope#keepAlive(ResourceScope) kept alive} by the linker implementation. That is, the resource
 * scope associated with such arguments cannot be closed, either implicitly or {@linkplain ResourceScope#close() explicitly}
 * until the downcall method handle completes.
 * <p>
 * Furthermore, if the function descriptor's return layout is a group layout, the resulting downcall method handle accepts
 * an extra parameter of type {@link SegmentAllocator}, which is used by the linker runtime to allocate the
 * memory region associated with the struct returned by  the downcall method handle.
 * <p>
 * Finally, downcall method handles feature a leading parameter of type {@link NativeSymbol}, from which the
 * address of the target native function can be derived. The address, when known statically, can also be provided by
 * clients at link time. As for other by-reference parameters (see above) this leading parameter will be
 * {@linkplain ResourceScope#keepAlive(ResourceScope) kept alive} by the linker implementation.
 * <p>Variadic functions, declared in C either with a trailing ellipses ({@code ...}) at the end of the formal parameter
 * list or with an empty formal parameter list, are not supported directly. However, it is possible to link a native
 * variadic function by using a {@linkplain FunctionDescriptor#asVariadic(MemoryLayout...) <em>variadic</em>} function descriptor,
 * in which the specialized signature of a given variable arity callsite is described in full. Alternatively,
 * if the foreign library allows it, clients might also be able to interact with variable arity methods
 * using by passing a trailing parameter of type {@link VaList}.
 *
 * <h2><a id = "upcall-stubs">Upcall stubs</a></h2>
 *
 * {@linkplain #upcallStub(MethodHandle, FunctionDescriptor, ResourceScope) Creating an upcall stub} requires a method
 * handle and a function descriptor; in this case, the set of memory layouts in the function descriptor
 * specify the signature of the function pointer associated with the upcall stub.
 * <p>
 * The type of the provided method handle has to match the Java {@link java.lang.invoke.MethodType method type}
 * associated with the upcall stub, which is derived from the argument and return layouts in the function descriptor.
 * More specifically, given each layout {@code L} in the function descriptor, a corresponding carrier {@code C} is inferred, as described below:
 * <ul>
 * <li>if {@code L} is a {@link ValueLayout} with carrier {@code E} then there are two cases:
 *     <ul>
 *         <li>if {@code L} occurs in a return position and {@code E} is {@code MemoryAddress.class},
 *         then {@code C = Addressable.class};</li>
 *         <li>otherwise, {@code C = E};
 *     </ul></li>
 * <li>or, if {@code L} is a {@link GroupLayout}, then {@code C} is set to {@code MemorySegment.class}</li>
 * </ul>
 * Upcall stubs are modelled by instances of type {@link NativeSymbol}; upcall stubs can be passed by reference to other
 * downcall method handles (as {@link NativeSymbol} implements the {@link Addressable} interface) and,
 * when no longer required, they can be {@link ResourceScope#close() released}, via their {@linkplain NativeSymbol#scope() scope}.
 *
 * <h2>System lookup</h2>
 *
 * This class implements the {@link SymbolLookup} interface; as such clients can {@linkplain #lookup(String) lookup} symbols
 * in the standard libraries associated with this linker. The set of symbols available for lookup is unspecified,
 * as it depends on the platform and on the operating system.
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 */
public sealed interface CLinker extends SymbolLookup permits Windowsx64Linker, SysVx64Linker, LinuxAArch64Linker, MacOsAArch64Linker {

    /**
     * Returns the C linker for the current platform.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @return a linker for this system.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    static CLinker systemCLinker() {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        return SharedUtils.getSystemLinker();
    }

    /**
     * Lookup a symbol in the standard libraries associated with this linker.
     * The set of symbols available for lookup is unspecified, as it depends on the platform and on the operating system.
     * @return a linker-specific library lookup which is suitable to find symbols in the standard libraries associated with this linker.
     */
    @Override
    default Optional<NativeSymbol> lookup(String name) {
        return SystemLookup.getInstance().lookup(name);
    }

    /**
     * Obtains a foreign method handle, with the given type and featuring the given function descriptor,
     * which can be used to call a target foreign function at the address in the given native symbol.
     * <p>
     * If the provided method type's return type is {@code MemorySegment}, then the resulting method handle features
     * an additional prefix parameter, of type {@link SegmentAllocator}, which will be used by the linker runtime
     * to allocate structs returned by-value.
     * <p>
     * Calling this method is equivalent to the following code:
<blockquote><pre>{@code
    linker.downcallHandle(function).bindTo(symbol);
}</pre></blockquote>
     *
     * @param symbol   downcall symbol.
     * @param function the function descriptor.
     * @return the downcall method handle. The method handle type is <a href="CLinker.html#downcall-method-handles"><em>inferred</em></a>
     * @throws IllegalArgumentException if the provided descriptor contains either a sequence or a padding layout,
     * or if the symbol is {@link MemoryAddress#NULL}
     *
     * @see SymbolLookup
     */
    default MethodHandle downcallHandle(NativeSymbol symbol, FunctionDescriptor function) {
        SharedUtils.checkSymbol(symbol);
        return downcallHandle(function).bindTo(symbol);
    }

    /**
     * Obtains a foreign method handle, with the given type and featuring the given function descriptor, which can be
     * used to call a target foreign function at the address in a dynamically provided native symbol.
     * The resulting method handle features a prefix parameter (as the first parameter) corresponding to the foreign function
     * entry point, of type {@link NativeSymbol}.
     * <p>
     * If the provided function descriptor's return layout is a {@link GroupLayout}, then the resulting method handle features an
     * additional prefix parameter (inserted immediately after the address parameter), of type {@link SegmentAllocator}),
     * which will be used by the linker runtime to allocate structs returned by-value.
     * <p>
     * The returned method handle will throw an {@link IllegalArgumentException} if the native symbol passed to it is
     * associated with the {@link MemoryAddress#NULL} address, or a {@link NullPointerException} if the native symbol is {@code null}.
     *
     * @param function the function descriptor.
     * @return the downcall method handle. The method handle type is <a href="CLinker.html#downcall-method-handles"><em>inferred</em></a>
     * from the provided function descriptor.
     * @throws IllegalArgumentException if the provided descriptor contains either a sequence or a padding layout.
     *
     * @see SymbolLookup
     */
    MethodHandle downcallHandle(FunctionDescriptor function);

    /**
     * Allocates a native stub with given scope which can be passed to other foreign functions (as a function pointer);
     * calling such a function pointer from native code will result in the execution of the provided method handle.
     *
     * <p>
     * The returned function pointer is associated with the provided scope. When such scope is closed,
     * the corresponding native stub will be deallocated.
     * <p>
     * The target method handle should not throw any exceptions. If the target method handle does throw an exception,
     * the VM will exit with a non-zero exit code. To avoid the VM aborting due to an uncaught exception, clients
     * could wrap all code in the target method handle in a try/catch block that catches any {@link Throwable}, for
     * instance by using the {@link java.lang.invoke.MethodHandles#catchException(MethodHandle, Class, MethodHandle)}
     * method handle combinator, and handle exceptions as desired in the corresponding catch block.
     *
     * @param target   the target method handle.
     * @param function the function descriptor.
     * @param scope the upcall stub scope.
     * @return the native stub symbol.
     * @throws IllegalArgumentException if the provided descriptor contains either a sequence or a padding layout,
     * or if it is determined that the target method handle can throw an exception, or if the target method handle
     * has a type that does not match the upcall stub <a href="CLinker.html#upcall-stubs"><em>inferred type</em></a>.
     * @throws IllegalStateException if {@code scope} has been already closed, or if access occurs from a thread other
     * than the thread owning {@code scope}.
     */
    NativeSymbol upcallStub(MethodHandle target, FunctionDescriptor function, ResourceScope scope);
}
