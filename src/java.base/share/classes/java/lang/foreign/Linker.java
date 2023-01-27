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
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.CapturableState;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * {@linkplain #downcallHandle(MemorySegment, FunctionDescriptor, Option...) downcall method handles}; and</li>
 * <li>A linker allows foreign functions to call Java method handles,
 * via the generation of {@linkplain #upcallStub(MethodHandle, FunctionDescriptor, SegmentScope) upcall stubs}.</li>
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
 * <h2 id="downcall-method-handles">Downcall method handles</h2>
 *
 * {@linkplain #downcallHandle(FunctionDescriptor, Option...) Linking a foreign function} is a process which requires a function descriptor,
 * a set of memory layouts which, together, specify the signature of the foreign function to be linked, and returns,
 * when complete, a downcall method handle, that is, a method handle that can be used to invoke the target foreign function.
 * <p>
 * The Java {@linkplain java.lang.invoke.MethodType method type} associated with the returned method handle is
 * {@linkplain FunctionDescriptor#toMethodType() derived} from the argument and return layouts in the function descriptor.
 * The downcall method handle type, might then be decorated by additional leading parameters, in the given order if both are present:
 * <ul>
 * <li>If the downcall method handle is created {@linkplain #downcallHandle(FunctionDescriptor, Option...) without specifying a target address},
 * the downcall method handle type features a leading parameter of type {@link MemorySegment}, from which the
 * address of the target foreign function can be derived.</li>
 * <li>If the function descriptor's return layout is a group layout, the resulting downcall method handle accepts
 * an additional leading parameter of type {@link SegmentAllocator}, which is used by the linker runtime to allocate the
 * memory region associated with the struct returned by the downcall method handle.</li>
 * </ul>
 *
 * <h2 id="upcall-stubs">Upcall stubs</h2>
 *
 * {@linkplain #upcallStub(MethodHandle, FunctionDescriptor, SegmentScope) Creating an upcall stub} requires a method
 * handle and a function descriptor; in this case, the set of memory layouts in the function descriptor
 * specify the signature of the function pointer associated with the upcall stub.
 * <p>
 * The type of the provided method handle's type has to match the method type associated with the upcall stub,
 * which is {@linkplain FunctionDescriptor#toMethodType() derived} from the provided function descriptor.
 * <p>
 * Upcall stubs are modelled by instances of type {@link MemorySegment}; upcall stubs can be passed by reference to other
 * downcall method handles and, they are released via their associated {@linkplain SegmentScope scope}.
 *
 * <h2 id="safety">Safety considerations</h2>
 *
 * Creating a downcall method handle is intrinsically unsafe. A symbol in a foreign library does not, in general,
 * contain enough signature information (e.g. arity and types of foreign function parameters). As a consequence,
 * the linker runtime cannot validate linkage requests. When a client interacts with a downcall method handle obtained
 * through an invalid linkage request (e.g. by specifying a function descriptor featuring too many argument layouts),
 * the result of such interaction is unspecified and can lead to JVM crashes. On downcall handle invocation,
 * the linker runtime guarantees the following for any argument {@code A} of type {@link MemorySegment} whose corresponding
 * layout is {@link ValueLayout#ADDRESS}:
 * <ul>
 *     <li>The scope of {@code A} is {@linkplain SegmentScope#isAlive() alive}. Otherwise, the invocation throws
 *     {@link IllegalStateException};</li>
 *     <li>The invocation occurs in a thread {@code T} such that {@code A.scope().isAccessibleBy(T) == true}.
 *     Otherwise, the invocation throws {@link WrongThreadException}; and</li>
 *     <li>The scope of {@code A} is {@linkplain SegmentScope#whileAlive(Runnable) kept alive} during the invocation.</li>
 *</ul>
 * A downcall method handle created from a function descriptor whose return layout is an
 * {@linkplain ValueLayout.OfAddress address layout} returns a native segment associated with
 * the {@linkplain SegmentScope#global() global scope}. Under normal conditions, the size of the returned segment is {@code 0}.
 * However, if the return layout is an {@linkplain ValueLayout.OfAddress#asUnbounded() unbounded} address layout,
 * then the size of the returned segment is {@code Long.MAX_VALUE}.
 * <p>
 * When creating upcall stubs the linker runtime validates the type of the target method handle against the provided
 * function descriptor and report an error if any mismatch is detected. As for downcalls, JVM crashes might occur,
 * if the foreign code casts the function pointer associated with an upcall stub to a type
 * that is incompatible with the provided function descriptor. Moreover, if the target method
 * handle associated with an upcall stub returns a {@linkplain MemorySegment memory segment}, clients must ensure
 * that this address cannot become invalid after the upcall completes. This can lead to unspecified behavior,
 * and even JVM crashes, since an upcall is typically executed in the context of a downcall method handle invocation.
 * <p>
 * An upcall stub argument whose corresponding layout is an {@linkplain ValueLayout.OfAddress address layout}
 * is a native segment associated with the {@linkplain SegmentScope#global() global scope}.
 * Under normal conditions, the size of this segment argument is {@code 0}. However, if the layout associated with
 * the upcall stub argument is an {@linkplain ValueLayout.OfAddress#asUnbounded() unbounded} address layout,
 * then the size of the segment argument is {@code Long.MAX_VALUE}.
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
     *     to the size and alignment constraint of a composite type definition in C (e.g. using {@code struct} or {@code union}); and</li>
     *     <li>Pointer types are modelled by a {@linkplain ValueLayout value layout} instance with carrier {@link MemorySegment}.
     *     Examples of pointer types in C are {@code int**} and {@code int(*)(size_t*, size_t*)};</li>
     * </ul>
     * <p>
     * Any layout not listed above is <em>unsupported</em>; function descriptors containing unsupported layouts
     * will cause an {@link IllegalArgumentException} to be thrown, when used to create a
     * {@link #downcallHandle(MemorySegment, FunctionDescriptor, Option...) downcall method handle} or an
     * {@linkplain #upcallStub(MethodHandle, FunctionDescriptor, SegmentScope) upcall stub}.
     * <p>
     * Variadic functions (e.g. a C function declared with a trailing ellipses {@code ...} at the end of the formal parameter
     * list or with an empty formal parameter list) are not supported directly. However, it is possible to link a
     * variadic function by using {@linkplain Linker.Option#firstVariadicArg(int) a linker option} to indicate
     * the start of the list of variadic arguments, together with a specialized function descriptor describing a
     * given variable arity callsite. Alternatively, where the foreign library allows it, clients might be able to
     * interact with variadic functions by passing a trailing parameter of type {@link VaList} (e.g. as in {@code vsprintf}).
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
     * @throws IllegalCallerException If the caller is in a module that does not have native access enabled.
     */
    @CallerSensitive
    static Linker nativeLinker() {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), Linker.class, "nativeLinker");
        return SharedUtils.getSystemLinker();
    }

    /**
     * Creates a method handle which can be used to call a foreign function with the given signature and address.
     * <p>
     * If the provided method type's return type is {@code MemorySegment}, then the resulting method handle features
     * an additional prefix parameter, of type {@link SegmentAllocator}, which will be used by the linker to allocate
     * structs returned by-value.
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang=java :
     * linker.downcallHandle(function).bindTo(symbol);
     * }
     *
     * @param symbol   the address of the target function.
     * @param function the function descriptor of the target function.
     * @param options  any linker options.
     * @return a downcall method handle. The method handle type is <a href="Linker.html#downcall-method-handles"><em>inferred</em></a>
     * @throws IllegalArgumentException if the provided function descriptor is not supported by this linker.
     *                                  or if the symbol is {@link MemorySegment#NULL}
     * @throws IllegalArgumentException if an invalid combination of linker options is given.
     */
    default MethodHandle downcallHandle(MemorySegment symbol, FunctionDescriptor function, Option... options) {
        SharedUtils.checkSymbol(symbol);
        return downcallHandle(function, options).bindTo(symbol);
    }

    /**
     * Creates a method handle which can be used to call a foreign function with the given signature.
     * The resulting method handle features a prefix parameter (as the first parameter) corresponding to the foreign function
     * entry point, of type {@link MemorySegment}, which is used to specify the address of the target function
     * to be called.
     * <p>
     * If the provided function descriptor's return layout is a {@link GroupLayout}, then the resulting method handle features an
     * additional prefix parameter (inserted immediately after the address parameter), of type {@link SegmentAllocator}),
     * which will be used by the linker to allocate structs returned by-value.
     * <p>
     * The returned method handle will throw an {@link IllegalArgumentException} if the {@link MemorySegment} parameter passed to it is
     * associated with the {@link MemorySegment#NULL} address, or a {@link NullPointerException} if that parameter is {@code null}.
     *
     * @param function the function descriptor of the target function.
     * @param options  any linker options.
     * @return a downcall method handle. The method handle type is <a href="Linker.html#downcall-method-handles"><em>inferred</em></a>
     * from the provided function descriptor.
     * @throws IllegalArgumentException if the provided function descriptor is not supported by this linker.
     * @throws IllegalArgumentException if an invalid combination of linker options is given.
     */
    MethodHandle downcallHandle(FunctionDescriptor function, Option... options);

    /**
     * Creates a stub which can be passed to other foreign functions as a function pointer, associated with the given
     * scope. Calling such a function pointer from foreign code will result in the execution of the provided
     * method handle.
     * <p>
     * The returned memory segment's address points to the newly allocated upcall stub, and is associated with
     * the provided scope. As such, the corresponding upcall stub will be deallocated
     * when the scope becomes not {@linkplain SegmentScope#isAlive() alive}.
     * <p>
     * The target method handle should not throw any exceptions. If the target method handle does throw an exception,
     * the VM will exit with a non-zero exit code. To avoid the VM aborting due to an uncaught exception, clients
     * could wrap all code in the target method handle in a try/catch block that catches any {@link Throwable}, for
     * instance by using the {@link java.lang.invoke.MethodHandles#catchException(MethodHandle, Class, MethodHandle)}
     * method handle combinator, and handle exceptions as desired in the corresponding catch block.
     *
     * @param target the target method handle.
     * @param function the upcall stub function descriptor.
     * @param scope the scope associated with the returned upcall stub segment.
     * @return a zero-length segment whose address is the address of the upcall stub.
     * @throws IllegalArgumentException if the provided function descriptor is not supported by this linker.
     * @throws IllegalArgumentException if it is determined that the target method handle can throw an exception, or if the target method handle
     * has a type that does not match the upcall stub <a href="Linker.html#upcall-stubs"><em>inferred type</em></a>.
     * @throws IllegalStateException if {@code scope} is not {@linkplain SegmentScope#isAlive() alive}.
     * @throws WrongThreadException if this method is called from a thread {@code T},
     * such that {@code scope.isAccessibleBy(T) == false}.
     */
    MemorySegment upcallStub(MethodHandle target, FunctionDescriptor function, SegmentScope scope);

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
     * A linker option is used to indicate additional linking requirements to the linker,
     * besides what is described by a function descriptor.
     * @since 20
     */
    @PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
    sealed interface Option
            permits LinkerOptions.LinkerOptionImpl,
                    Option.CaptureCallState {

        /**
         * {@return a linker option used to denote the index of the first variadic argument layout in a
         *          foreign function call}
         * @param index the index of the first variadic argument in a downcall handle linkage request.
         */
        static Option firstVariadicArg(int index) {
            return new LinkerOptions.FirstVariadicArg(index);
        }

        /**
         * {@return A linker option used to save portions of the execution state immediately after
         *          calling a foreign function associated with a downcall method handle,
         *          before it can be overwritten by the Java runtime, or read through conventional means}
         * <p>
         * A downcall method handle linked with this option will feature an additional {@link MemorySegment}
         * parameter directly following the target address, and optional {@link SegmentAllocator} parameters.
         * This memory segment must be a native segment into which the captured state is written.
         *
         * @param capturedState the names of the values to save.
         * @see CaptureCallState#supported()
         */
        static CaptureCallState captureCallState(String... capturedState) {
            Set<CapturableState> set = Stream.of(capturedState)
                    .map(CapturableState::forName)
                    .collect(Collectors.toSet());
            return new LinkerOptions.CaptureCallStateImpl(set);
        }

        /**
         * A linker option for saving portions of the execution state immediately
         * after calling a foreign function associated with a downcall method handle,
         * before it can be overwritten by the runtime, or read through conventional means.
         * <p>
         * Execution state is captured by a downcall method handle on invocation, by writing it
         * to a native segment provided by the user to the downcall method handle.
         * For this purpose, a downcall method handle linked with the {@link #captureCallState(String[])}
         * option will feature an additional {@link MemorySegment} parameter directly
         * following the target address, and optional {@link SegmentAllocator} parameters.
         * This parameter represents the native segment into which the captured state is written.
         * <p>
         * The native segment should have the layout {@linkplain CaptureCallState#layout associated}
         * with the particular {@code CaptureCallState} instance used to link the downcall handle.
         * <p>
         * Captured state can be retrieved from this native segment by constructing var handles
         * from the {@linkplain #layout layout} associated with the {@code CaptureCallState} instance.
         * <p>
         * The following example demonstrates the use of this linker option:
         * {@snippet lang = "java":
         * MemorySegment targetAddress = ...
         * CaptureCallState ccs = Linker.Option.captureCallState("errno");
         * MethodHandle handle = Linker.nativeLinker().downcallHandle(targetAddress, FunctionDescriptor.ofVoid(), ccs);
         *
         * VarHandle errnoHandle = ccs.layout().varHandle(PathElement.groupElement("errno"));
         * try (Arena arena = Arena.openConfined()) {
         *     MemorySegment capturedState = arena.allocate(ccs.layout());
         *     handle.invoke(capturedState);
         *     int errno = errnoHandle.get(capturedState);
         *     // use errno
         * }
         * }
         */
        @PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
        sealed interface CaptureCallState extends Option
                                          permits LinkerOptions.CaptureCallStateImpl {
            /**
             * {@return A struct layout that represents the layout of the native segment passed
             *          to a downcall handle linked with this {@code CapturedCallState} instance}
             */
            StructLayout layout();

            /**
             * {@return the names of the state that can be capture by this implementation}
             */
            static Set<String> supported() {
                return Arrays.stream(CapturableState.values())
                             .map(CapturableState::stateName)
                             .collect(Collectors.toSet());
            }
        }
    }
}
