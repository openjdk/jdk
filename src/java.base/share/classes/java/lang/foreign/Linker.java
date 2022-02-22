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

import jdk.internal.foreign.abi.AbstractLinker;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

/**
 * A linker provides access to foreign functions from Java code, and access to Java code from foreign functions.
 * <p>
 * Foreign functions typically reside in libraries that can be loaded on-demand. Each library conforms to
 * a specific ABI (Application Binary Interface). An ABI is a set of calling conventions and data types associated with
 * the compiler, OS, and processor where the library was built. For example, a C compiler on Linux/x64 usually
 * builds libraries that conform to the SystemV ABI.
 * <p>
 * A linker has detailed knowledge of the calling conventions and data types used by a specific ABI.
 * For any library which conforms to that ABI, the linker can mediate between Java code running
 * in the JVM and foreign functions in the library. In particular:
 * <ul>
 * <li>A linker allows Java code to link against foreign functions, via
 * {@linkplain #downcallHandle(Addressable, FunctionDescriptor) downcall method handles}; and</li>
 * <li>A linker allows foreign functions to call Java method handles,
 * via the generation of {@linkplain #upcallStub(MethodHandle, FunctionDescriptor, MemorySession) upcall stubs}.</li>
 * </ul>
 * In addition, a linker provides a way to look up foreign functions in libraries that conform to the ABI. Each linker
 * chooses a set of libraries that are commonly used on the OS and processor combination associated with the ABI.
 * For example, a linker for Linux/x64 might choose two libraries: {@code libc} and {@code libm}. The functions in these
 * libraries are exposed via a {@linkplain #defaultLookup() symbol lookup}.
 * <p>
 * The {@link #nativeLinker()} method provides a linker for the ABI associated with the OS and processor where the Java runtime
 * is currently executing. This linker also provides access, via its {@linkplain #defaultLookup() default lookup},
 * to the native libraries loaded with the Java runtime.
 *
 * <h2><a id = "downcall-method-handles">Downcall method handles</a></h2>
 *
 * {@linkplain #downcallHandle(FunctionDescriptor) Linking a foreign function} is a process which requires a function descriptor,
 * a set of memory layouts which, together, specify the signature of the foreign function to be linked, and returns,
 * when complete, a downcall method handle, that is, a method handle that can be used to invoke the target foreign function.
 * <p>
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
 * address of the target foreign function can be derived.</li>
 * <li>If the function descriptor's return layout is a group layout, the resulting downcall method handle accepts
 * an additional leading parameter of type {@link SegmentAllocator}, which is used by the linker runtime to allocate the
 * memory region associated with the struct returned by the downcall method handle.</li>
 * </ul>
 *
 * <h2><a id = "upcall-stubs">Upcall stubs</a></h2>
 *
 * {@linkplain #upcallStub(MethodHandle, FunctionDescriptor, MemorySession) Creating an upcall stub} requires a method
 * handle and a function descriptor; in this case, the set of memory layouts in the function descriptor
 * specify the signature of the function pointer associated with the upcall stub.
 * <p>
 * The type of the provided method handle has to {@linkplain #upcallType(FunctionDescriptor) match} the Java
 * {@linkplain java.lang.invoke.MethodType method type} associated with the upcall stub, which is derived from the argument
 * and return layouts in the function descriptor. More specifically, given each layout {@code L} in the function descriptor,
 * a corresponding carrier {@code C} is inferred, as described below:
 * <ul>
 * <li>If {@code L} is a {@link ValueLayout} with carrier {@code E} then there are two cases:
 *     <ul>
 *         <li>If {@code L} occurs in a return position and {@code E} is {@code MemoryAddress.class},
 *         then {@code C = Addressable.class};</li>
 *         <li>Otherwise, {@code C = E};
 *     </ul></li>
 * <li>Or, if {@code L} is a {@link GroupLayout}, then {@code C} is set to {@code MemorySegment.class}</li>
 * </ul>
 * Upcall stubs are modelled by instances of type {@link MemorySegment}; upcall stubs can be passed by reference to other
 * downcall method handles (as {@link MemorySegment} implements the {@link Addressable} interface) and,
 * when no longer required, they can be {@linkplain MemorySession#close() released}, via their associated {@linkplain MemorySession session}.
 *
 * <h2>Safety considerations</h2>
 *
 * Creating a downcall method handle is intrinsically unsafe. A symbol in a foreign library does not, in general,
 * contain enough signature information (e.g. arity and types of foreign function parameters). As a consequence,
 * the linker runtime cannot validate linkage requests. When a client interacts with a downcall method handle obtained
 * through an invalid linkage request (e.g. by specifying a function descriptor featuring too many argument layouts),
 * the result of such interaction is unspecified and can lead to JVM crashes. On downcall handle invocation,
 * the linker runtime guarantees the following for any argument that is a memory resource {@code R} (of type {@link MemorySegment}
 * or {@link VaList}):
 * <ul>
 *     <li>The memory session of {@code R} is {@linkplain MemorySession#isAlive() alive}. Otherwise, the invocation throws
 *     {@link IllegalStateException};</li>
 *     <li>The invocation occurs in same thread as the one {@linkplain MemorySession#ownerThread() owning} the memory session of {@code R},
 *     if said session is confined. Otherwise, the invocation throws {@link IllegalStateException}; and</li>
 *     <li>The memory session of {@code R} is {@linkplain MemorySession#whileAlive(Runnable) kept alive} (and cannot be closed) during the invocation.</li>
 *</ul>
 * <p>
 * When creating upcall stubs the linker runtime validates the type of the target method handle against the provided
 * function descriptor and report an error if any mismatch is detected. As for downcalls, JVM crashes might occur,
 * if the foreign code casts the function pointer associated with an upcall stub to a type
 * that is incompatible with the provided function descriptor. Moreover, if the target method
 * handle associated with an upcall stub returns a {@linkplain MemoryAddress memory address}, clients must ensure
 * that this address cannot become invalid after the upcall completes. This can lead to unspecified behavior,
 * and even JVM crashes, since an upcall is typically executed in the context of a downcall method handle invocation.
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed interface Linker permits AbstractLinker {

    /**
     * Returns a linker for the ABI associated with the underlying native platform. The underlying native platform
     * is the combination of OS and processor where the Java runtime is currently executing.
     * <p>
     * When interacting with the returned linker, clients must describe the signature of a foreign function using a
     * {@link FunctionDescriptor function descriptor} whose argument and return layouts are specified as follows:
     * <ul>
     *     <li>Scalar types are modelled by a {@linkplain ValueLayout value layout} instance of a suitable carrier. Example
     *     of scalar types in C are {@code int}, {@code long}, {@code size_t}, etc. The mapping between a scalar type
     *     and its corresponding layout is dependent on the ABI of the returned linker;
     *     <li>Composite types are modelled by a {@linkplain GroupLayout group layout}. Depending on the ABI of the
     *     returned linker, additional {@linkplain MemoryLayout#paddingLayout(long) padding} member layouts might be required to conform
     *     to the size and alignment constraints of a composite type definition in C (e.g. using {@code struct} or {@code union}); and</li>
     *     <li>Pointer types are modelled by a {@linkplain ValueLayout value layout} instance with carrier {@link MemoryAddress}.
     *     Examples of pointer types in C are {@code int**} and {@code int(*)(size_t*, size_t*)};</li>
     * </ul>
     * <p>
     * Any layout not listed above is <em>unsupported</em>; function descriptors containing unsupported layouts
     * will cause an {@link IllegalArgumentException} to be thrown, when used to create a
     * {@link #downcallHandle(Addressable, FunctionDescriptor) downcall method handle} or an
     * {@linkplain #upcallStub(MethodHandle, FunctionDescriptor, MemorySession) upcall stub}.
     * <p>
     * Variadic functions (e.g. a C function declared with a trailing ellipses {@code ...} at the end of the formal parameter
     * list or with an empty formal parameter list) are not supported directly. However, it is possible to link a
     * variadic function by using a {@linkplain FunctionDescriptor#asVariadic(MemoryLayout...) <em>variadic</em>}
     * function descriptor, in which the specialized signature of a given variable arity callsite is described in full.
     * Alternatively, where the foreign library allows it, clients might be able to interact with variadic functions by
     * passing a trailing parameter of type {@link VaList} (e.g. as in {@code vsprintf}).
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     *
     * @apiNote It is not currently possible to obtain a linker for a different combination of OS and processor.
     * @implNote The libraries exposed by the {@linkplain #defaultLookup() default lookup} associated with the returned
     * linker are the native libraries loaded in the process where the Java runtime is currently executing. For example,
     * on Linux, these libraries typically include {@code libc}, {@code libm} and {@code libdl}.
     *
     * @return a linker for the ABI associated with the OS and processor where the Java runtime is currently executing.
     * @throws UnsupportedOperationException if the underlying native platform is not supported.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is specified, but does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    static Linker nativeLinker() {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), Linker.class, "nativeLinker");
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
     * @return a downcall method handle. The method handle type is <a href="CLinker.html#downcall-method-handles"><em>inferred</em></a>
     * @throws IllegalArgumentException if the provided function descriptor is not supported by this linker.
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
     * @return a downcall method handle. The method handle type is <a href="CLinker.html#downcall-method-handles"><em>inferred</em></a>
     * from the provided function descriptor.
     * @throws IllegalArgumentException if the provided function descriptor is not supported by this linker.
     */
    MethodHandle downcallHandle(FunctionDescriptor function);

    /**
     * Creates a stub which can be passed to other foreign functions as a function pointer, with the given
     * memory session. Calling such a function pointer from foreign code will result in the execution of the provided
     * method handle.
     * <p>
     * The returned memory segment's base address points to the newly allocated upcall stub, and is associated with
     * the provided memory session. When such session is closed, the corresponding upcall stub will be deallocated.
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
     * @return a zero-length segment whose base address is the address of the upcall stub.
     * @throws IllegalArgumentException if the provided function descriptor is not supported by this linker.
     * @throws IllegalArgumentException if it is determined that the target method handle can throw an exception, or if the target method handle
     * has a type that does not match the upcall stub <a href="CLinker.html#upcall-stubs"><em>inferred type</em></a>.
     * @throws IllegalStateException if {@code session} is not {@linkplain MemorySession#isAlive() alive}, or if access occurs from
     * a thread other than the thread {@linkplain MemorySession#ownerThread() owning} {@code session}.
     */
    MemorySegment upcallStub(MethodHandle target, FunctionDescriptor function, MemorySession session);

    /**
     * Returns a symbol lookup for symbols in a set of commonly used libraries.
     * <p>
     * Each {@link Linker} is responsible for choosing libraries that are widely recognized as useful on the OS
     * and processor combination supported by the {@link Linker}. Accordingly, the precise set of symbols exposed by the
     * symbol lookup is unspecified; it varies from one {@link Linker} to another.
     * @implNote It is strongly recommended that the result of {@link #defaultLookup} exposes a set of symbols that is stable over time.
     * Clients of {@link #defaultLookup()} are likely to fail if a symbol that was previously exposed by the symbol lookup is no longer exposed.
     * <p>If an implementer provides {@link Linker} implementations for multiple OS and processor combinations, then it is strongly
     * recommended that the result of {@link #defaultLookup()} exposes, as much as possible, a consistent set of symbols
     * across all the OS and processor combinations.
     * @return a symbol lookup for symbols in a set of commonly used libraries.
     */
    SymbolLookup defaultLookup();

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
