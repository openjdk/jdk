/*
 *  Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Optional;
import jdk.internal.foreign.SystemLookup;
import jdk.internal.foreign.abi.AbstractLinker;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

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
 * elements to a method in this class causes a {@link NullPointerException} to be thrown.</p>
 *
 * <h2><a id = "downcall-method-handles">Downcall method handles</a></h2>
 * <p>
 * {@linkplain #downcallHandle(FunctionDescriptor) Linking a foreign function} is a process which requires a function descriptor,
 * a set of memory layouts which, together, specify the signature of the foreign function to be linked, and returns,
 * when complete, a downcall method handle, that is, a method handle that can be used to invoke the target native function.
 * The Java {@linkplain java.lang.invoke.MethodType method type} associated with the returned method handle is
 * {@linkplain #downcallType(FunctionDescriptor) derived} from the argument and return layouts in the function descriptor.
 * More specifically, given each layout {@code L} in the function descriptor, a corresponding carrier {@code C} is inferred,
 * as described below:
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
 * The downcall method handle type, derived as above, might be decorated by additional leading parameters,
 * in the given order if both are present:
 * <ul>
 * <li>If the downcall method handle is created {@linkplain #downcallHandle(FunctionDescriptor) without specifying a target address},
 * the downcall method handle type features a leading parameter of type {@link Addressable}, from which the
 * address of the target native function can be derived.</li>
 * <li>If the function descriptor's return layout is a group layout, the resulting downcall method handle accepts
 * an additional leading parameter of type {@link SegmentAllocator}, which is used by the linker runtime to allocate the
 * memory region associated with the struct returned by the downcall method handle.</li>
 * </ul>
 * <p>Variadic functions, declared in C either with a trailing ellipses ({@code ...}) at the end of the formal parameter
 * list or with an empty formal parameter list, are not supported directly. However, it is possible to link a native
 * variadic function by using a {@linkplain FunctionDescriptor#asVariadic(MemoryLayout...) <em>variadic</em>} function descriptor,
 * in which the specialized signature of a given variable arity callsite is described in full. Alternatively,
 * if the foreign library allows it, clients might also be able to interact with variable arity methods
 * by passing a trailing parameter of type {@link VaList}.
 *
 * <h2><a id = "upcall-stubs">Upcall stubs</a></h2>
 *
 * {@linkplain #upcallStub(MethodHandle, FunctionDescriptor, MemorySession) Creating an upcall stub} requires a method
 * handle and a function descriptor; in this case, the set of memory layouts in the function descriptor
 * specify the signature of the function pointer associated with the upcall stub.
 * <p>
 * The type of the provided method handle has to match the Java {@linkplain java.lang.invoke.MethodType method type}
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
 * Upcall stubs are modelled by instances of type {@link MemorySegment}; upcall stubs can be passed by reference to other
 * downcall method handles (as {@link MemorySegment} implements the {@link Addressable} interface) and,
 * when no longer required, they can be {@linkplain MemorySession#close() released}, via their associated {@linkplain MemorySession session}.
 *
 * <h2>Safety considerations</h2>
 *
 * Creating a downcall method handle is intrinsically unsafe. A symbol in a native library does not, in general,
 * contain enough signature information (e.g. arity and types of native function parameters). As a consequence,
 * the linker runtime cannot validate linkage requests. When a client interacts with a downcall method handle obtained
 * through an invalid linkage request (e.g. by specifying a function descriptor featuring too many argument layouts),
 * the result of such interaction is unspecified and can lead to JVM crashes. On downcall handle invocation,
 * the linker runtime guarantees the following for any argument that is a memory resource {@code R} (of type {@link MemorySegment}
 * or {@link VaList}):
 * <ul>
 *     <li>The memory session of {@code R} is {@linkplain MemorySession#isAlive() alive}. Otherwise, the invocation throws
 *     {@link IllegalStateException};</li>
 *     <li>The invocation occurs in same thread as the one {@linkplain MemorySession#ownerThread() owning} the memory session of {@code R},</li>
 *     if said session is confined. Otherwise, the invocation throws {@link IllegalStateException}; and</li>
 *     <li>The memory session of {@code R} is {@linkplain MemorySession#whileAlive(Runnable) kept alive} (and cannot be closed) during the invocation.</li>
 *</ul>
 * <p>
 * When creating upcall stubs the linker runtime validates the type of the target method handle against the provided
 * function descriptor and report an error if any mismatch is detected. As for downcalls, JVM crashes might occur,
 * if the native code casts the function pointer associated with an upcall stub to a type
 * that is incompatible with the provided function descriptor. Moreover, if the target method
 * handle associated with an upcall stub returns a {@linkplain MemoryAddress native address}, clients must ensure
 * that this address cannot become invalid after the upcall completes. This can lead to unspecified behavior,
 * and even JVM crashes, since an upcall is typically executed in the context of a downcall method handle invocation.
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed interface CLinker permits AbstractLinker {

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
     * {@code --enable-native-access} is specified, but does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    static CLinker systemCLinker() {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), CLinker.class, "systemCLinker");
        return SharedUtils.getSystemLinker();
    }

    /**
     * Creates a method handle which can be used to call a target foreign function with the given signature and address.
     * <p>
     * If the provided method type's return type is {@code MemorySegment}, then the resulting method handle features
     * an additional prefix parameter, of type {@link SegmentAllocator}, which will be used by the linker runtime
     * to allocate structs returned by-value.
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang=java :
     * linker.downcallHandle(function).bindTo(symbol);
     * }
     *
     * @param symbol the address of the target function.
     * @param function the function descriptor of the target function.
     * @return a new downcall method handle. The method handle type is <a href="CLinker.html#downcall-method-handles"><em>inferred</em></a>
     * @throws IllegalArgumentException if the provided function descriptor contains either a sequence or a padding layout,
     * or if the symbol is {@link MemoryAddress#NULL}
     */
    default MethodHandle downcallHandle(Addressable symbol, FunctionDescriptor function) {
        SharedUtils.checkSymbol(symbol);
        return downcallHandle(function).bindTo(symbol);
    }

    /**
     * Creates a method handle which can be used to call a target foreign function with the given signature.
     * The resulting method handle features a prefix parameter (as the first parameter) corresponding to the foreign function
     * entry point, of type {@link Addressable}, which is used to specify the address of the target function
     * to be called.
     * <p>
     * If the provided function descriptor's return layout is a {@link GroupLayout}, then the resulting method handle features an
     * additional prefix parameter (inserted immediately after the address parameter), of type {@link SegmentAllocator}),
     * which will be used by the linker runtime to allocate structs returned by-value.
     * <p>
     * The returned method handle will throw an {@link IllegalArgumentException} if the {@link Addressable} parameter passed to it is
     * associated with the {@link MemoryAddress#NULL} address, or a {@link NullPointerException} if that parameter is {@code null}.
     *
     * @param function the function descriptor of the target function.
     * @return a new downcall method handle. The method handle type is <a href="CLinker.html#downcall-method-handles"><em>inferred</em></a>
     * from the provided function descriptor.
     * @throws IllegalArgumentException if the provided function descriptor contains either a sequence or a padding layout.
     */
    MethodHandle downcallHandle(FunctionDescriptor function);

    /**
     * Creates a native stub which can be passed to other foreign functions as a function pointer, with the given
     * memory session. Calling such a function pointer from native code will result in the execution of the provided
     * method handle.
     * <p>
     * The returned memory segment's base address points to the newly allocated native stub, and is associated with
     * the provided memory session. When such session is closed, the corresponding native stub will be deallocated.
     * <p>
     * The target method handle should not throw any exceptions. If the target method handle does throw an exception,
     * the VM will exit with a non-zero exit code. To avoid the VM aborting due to an uncaught exception, clients
     * could wrap all code in the target method handle in a try/catch block that catches any {@link Throwable}, for
     * instance by using the {@link java.lang.invoke.MethodHandles#catchException(MethodHandle, Class, MethodHandle)}
     * method handle combinator, and handle exceptions as desired in the corresponding catch block.
     *
     * @param target the target method handle.
     * @param function the upcall stub function descriptor.
     * @param session the upcall stub memory session.
     * @return a zero-length segment whose base address is the address of the native stub.
     * @throws IllegalArgumentException if the provided descriptor contains either a sequence or a padding layout,
     * or if it is determined that the target method handle can throw an exception, or if the target method handle
     * has a type that does not match the upcall stub <a href="CLinker.html#upcall-stubs"><em>inferred type</em></a>.
     * @throws IllegalStateException if {@code session} is not {@linkplain MemorySession#isAlive() alive}, or if access occurs from
     * a thread other than the thread {@linkplain MemorySession#ownerThread() owning} {@code session}.
     */
    MemorySegment upcallStub(MethodHandle target, FunctionDescriptor function, MemorySession session);

    /**
     * {@return the downcall method handle {@linkplain MethodType type} associated with the given function descriptor}
     * @param functionDescriptor a function descriptor.
     * @throws IllegalArgumentException if one or more layouts in the function descriptor are not supported
     * (e.g. if they are sequence layouts or padding layouts).
     */
    static MethodType downcallType(FunctionDescriptor functionDescriptor) {
        return SharedUtils.inferMethodType(functionDescriptor, false);
    }

    /**
     * {@return the method handle {@linkplain MethodType type} associated with an upcall stub with the given function descriptor}
     * @param functionDescriptor a function descriptor.
     * @throws IllegalArgumentException if one or more layouts in the function descriptor are not supported
     * (e.g. if they are sequence layouts or padding layouts).
     */
    static MethodType upcallType(FunctionDescriptor functionDescriptor) {
        return SharedUtils.inferMethodType(functionDescriptor, true);
    }
}
