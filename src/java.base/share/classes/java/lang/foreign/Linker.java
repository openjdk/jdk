/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.foreign;

import jdk.internal.foreign.abi.AbstractLinker;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.CapturableState;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.javac.Restricted;
import jdk.internal.reflect.CallerSensitive;

import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A linker provides access to foreign functions from Java code, and access to Java code
 * from foreign functions.
 * <p>
 * Foreign functions typically reside in libraries that can be loaded on demand. Each
 * library conforms to a specific ABI (Application Binary Interface). An ABI is a set of
 * calling conventions and data types associated with the compiler, OS, and processor where
 * the library was built. For example, a C compiler on Linux/x64 usually builds libraries
 * that conform to the SystemV ABI.
 * <p>
 * A linker has detailed knowledge of the calling conventions and data types used by a
 * specific ABI. For any library that conforms to that ABI, the linker can mediate
 * between Java code running in the JVM and foreign functions in the library. In
 * particular:
 * <ul>
 * <li>A linker allows Java code to link against foreign functions, via
 * {@linkplain #downcallHandle(MemorySegment, FunctionDescriptor, Option...) downcall method handles};
 * and</li>
 * <li>A linker allows foreign functions to call Java method handles, via the generation
 * of {@linkplain #upcallStub(MethodHandle, FunctionDescriptor, Arena, Option...) upcall stubs}.</li>
 * </ul>
 * A linker provides a way to look up the <em>canonical layouts</em> associated with the
 * data types used by the ABI. For example, a linker implementing the C ABI might choose
 * to provide a canonical layout for the C {@code size_t} type. On 64-bit platforms,
 * this canonical layout might be equal to {@link ValueLayout#JAVA_LONG}. The canonical
 * layouts supported by a linker are exposed via the {@link #canonicalLayouts()} method,
 * which returns a map from type names to canonical layouts.
 * <p>
 * In addition, a linker provides a way to look up foreign functions in libraries that
 * conform to the ABI. Each linker chooses a set of libraries that are commonly used on
 * the OS and processor combination associated with the ABI. For example, a linker for
 * Linux/x64 might choose two libraries: {@code libc} and {@code libm}. The functions in
 * these libraries are exposed via a {@linkplain #defaultLookup() symbol lookup}.
 *
 * <h2 id="native-linker">Calling native functions</h2>
 *
 * The {@linkplain #nativeLinker() native linker} can be used to link against functions
 * defined in C libraries (native functions). Suppose we wish to downcall from Java to
 * the {@code strlen} function defined in the standard C library:
 * {@snippet lang = c:
 * size_t strlen(const char *s);
 * }
 * A downcall method handle that exposes {@code strlen} is obtained, using the native
 * linker, as follows:
 *
 * {@snippet lang = java:
 * Linker linker = Linker.nativeLinker();
 * MethodHandle strlen = linker.downcallHandle(
 *     linker.defaultLookup().findOrThrow("strlen"),
 *     FunctionDescriptor.of(JAVA_LONG, ADDRESS)
 * );
 * }
 *
 * Note how the native linker also provides access, via its {@linkplain #defaultLookup() default lookup},
 * to the native functions defined by the C libraries loaded with the Java runtime.
 * Above, the default lookup is used to search the address of the {@code strlen} native
 * function. That address is then passed, along with a <em>platform-dependent description</em>
 * of the signature of the function expressed as a {@link FunctionDescriptor} (more on
 * that below) to the native linker's {@link #downcallHandle(MemorySegment, FunctionDescriptor, Option...)}
 * method. The obtained downcall method handle is then invoked as follows:
 *
 * {@snippet lang = java:
 * try (Arena arena = Arena.ofConfined()) {
 *     MemorySegment str = arena.allocateFrom("Hello");
 *     long len = (long) strlen.invokeExact(str);  // 5
 * }
 *}
 * <h3 id="describing-c-sigs">Describing C signatures</h3>
 *
 * When interacting with the native linker, clients must provide a platform-dependent
 * description of the signature of the C function they wish to link against. This
 * description, a {@link FunctionDescriptor function descriptor}, defines the layouts
 * associated with the parameter types and return type (if any) of the C function.
 * <p>
 * Scalar C types such as {@code bool}, {@code int} are modeled as
 * {@linkplain ValueLayout value layouts} of a suitable carrier. The
 * {@linkplain #canonicalLayouts() mapping} between a scalar type and its corresponding
 * canonical layout is dependent on the ABI implemented by the native linker (see below).
 * <p>
 * Composite types are modeled as {@linkplain GroupLayout group layouts}. More
 * specifically, a C {@code struct} type maps to a {@linkplain StructLayout struct layout},
 * whereas a C {@code union} type maps to a {@link UnionLayout union layout}. When defining
 * a struct or union layout, clients must pay attention to the size and alignment constraint
 * of the corresponding composite type definition in C. For instance, padding between two
 * struct fields must be modeled explicitly, by adding an adequately sized
 * {@linkplain PaddingLayout padding layout} member to the resulting struct layout.
 * <p>
 * Finally, pointer types such as {@code int**} and {@code int(*)(size_t*, size_t*)}
 * are modeled as {@linkplain AddressLayout address layouts}. When the spatial bounds of
 * the pointer type are known statically, the address layout can be associated with a
 * {@linkplain AddressLayout#targetLayout() target layout}. For instance, a pointer that
 * is known to point to a C {@code int[2]} array can be modeled as an address layout
 * whose target layout is a sequence layout whose element count is 2, and whose
 * element type is {@link ValueLayout#JAVA_INT}.
 * <p>
 * All native linker implementations are guaranteed to provide canonical layouts for the
 * following set of types:
 * <ul>
 *     <li>{@code bool}</li>
 *     <li>{@code char}</li>
 *     <li>{@code short}</li>
 *     <li>{@code int}</li>
 *     <li>{@code long}</li>
 *     <li>{@code long long}</li>
 *     <li>{@code float}</li>
 *     <li>{@code double}</li>
 *     <li>{@code size_t}</li>
 *     <li>{@code wchar_t}</li>
 *     <li>{@code void*}</li>
 * </ul>
 * As noted above, the specific canonical layout associated with each type can vary,
 * depending on the data model supported by a given ABI. For instance, the C type
 * {@code long} maps to the layout constant {@link ValueLayout#JAVA_LONG} on Linux/x64,
 * but maps to the layout constant {@link ValueLayout#JAVA_INT} on Windows/x64.
 * Similarly, the C type {@code size_t} maps to the layout constant
 * {@link ValueLayout#JAVA_LONG} on 64-bit platforms, but maps to the layout constant
 * {@link ValueLayout#JAVA_INT} on 32-bit platforms.
 * <p>
 * A native linker typically does not provide canonical layouts for C's unsigned integral
 * types. Instead, they are modeled using the canonical layouts associated with their
 * corresponding signed integral types. For instance, the C type {@code unsigned long}
 * maps to the layout constant {@link ValueLayout#JAVA_LONG} on Linux/x64, but maps to
 * the layout constant {@link ValueLayout#JAVA_INT} on Windows/x64.
 * <p>
 * The following table shows some examples of how C types are modeled in Linux/x64
 * according to the "System V Application Binary Interface"
 * (all the examples provided here will assume these platform-dependent mappings):
 *
 * <blockquote><table class="plain">
 * <caption style="display:none">Mapping C types</caption>
 * <thead>
 * <tr>
 *     <th scope="col">C type</th>
 *     <th scope="col">Layout</th>
 *     <th scope="col">Java type</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row" style="font-weight:normal">{@code bool}</th>
 *     <td style="text-align:center;">{@link ValueLayout#JAVA_BOOLEAN}</td>
 *     <td style="text-align:center;">{@code boolean}</td>
 * <tr><th scope="row" style="font-weight:normal">{@code char} <br> {@code unsigned char}</th>
 *     <td style="text-align:center;">{@link ValueLayout#JAVA_BYTE}</td>
 *     <td style="text-align:center;">{@code byte}</td>
 * <tr><th scope="row" style="font-weight:normal">{@code short} <br> {@code unsigned short}</th>
 *     <td style="text-align:center;">{@link ValueLayout#JAVA_SHORT}</td>
 *     <td style="text-align:center;">{@code short}</td>
 * <tr><th scope="row" style="font-weight:normal">{@code int} <br> {@code unsigned int}</th>
 *     <td style="text-align:center;">{@link ValueLayout#JAVA_INT}</td>
 *     <td style="text-align:center;">{@code int}</td>
 * <tr><th scope="row" style="font-weight:normal">{@code long} <br> {@code unsigned long}</th>
 *     <td style="text-align:center;">{@link ValueLayout#JAVA_LONG}</td>
 *     <td style="text-align:center;">{@code long}</td>
 * <tr><th scope="row" style="font-weight:normal">{@code long long} <br> {@code unsigned long long}</th>
 *     <td style="text-align:center;">{@link ValueLayout#JAVA_LONG}</td>
 *     <td style="text-align:center;">{@code long}</td>
 * <tr><th scope="row" style="font-weight:normal">{@code float}</th>
 *     <td style="text-align:center;">{@link ValueLayout#JAVA_FLOAT}</td>
 *     <td style="text-align:center;">{@code float}</td>
 * <tr><th scope="row" style="font-weight:normal">{@code double}</th>
 *     <td style="text-align:center;">{@link ValueLayout#JAVA_DOUBLE}</td>
 *     <td style="text-align:center;">{@code double}</td>
 <tr><th scope="row" style="font-weight:normal">{@code size_t}</th>
 *     <td style="text-align:center;">{@link ValueLayout#JAVA_LONG}</td>
 *     <td style="text-align:center;">{@code long}</td>
 * <tr><th scope="row" style="font-weight:normal">{@code char*}, {@code int**}, {@code struct Point*}</th>
 *     <td style="text-align:center;">{@link ValueLayout#ADDRESS}</td>
 *     <td style="text-align:center;">{@link MemorySegment}</td>
 * <tr><th scope="row" style="font-weight:normal">{@code int (*ptr)[10]}</th>
 *     <td style="text-align:left;">
 * <pre>
 * ValueLayout.ADDRESS.withTargetLayout(
 *     MemoryLayout.sequenceLayout(10,
 *         ValueLayout.JAVA_INT)
 * );
 * </pre>
 *     <td style="text-align:center;">{@link MemorySegment}</td>
 * <tr><th scope="row" style="font-weight:normal"><code>struct Point { int x; long y; };</code></th>
 *     <td style="text-align:left;">
 * <pre>
 * MemoryLayout.structLayout(
 *     ValueLayout.JAVA_INT.withName("x"),
 *     MemoryLayout.paddingLayout(32),
 *     ValueLayout.JAVA_LONG.withName("y")
 * );
 * </pre>
 *     </td>
 *     <td style="text-align:center;">{@link MemorySegment}</td>
 * <tr><th scope="row" style="font-weight:normal"><code>union Choice { float a; int b; }</code></th>
 *     <td style="text-align:left;">
 * <pre>
 * MemoryLayout.unionLayout(
 *     ValueLayout.JAVA_FLOAT.withName("a"),
 *     ValueLayout.JAVA_INT.withName("b")
 * );
 * </pre>
 *     </td>
 *     <td style="text-align:center;">{@link MemorySegment}</td>
 * </tbody>
 * </table></blockquote>
 * <p>
 * All native linker implementations support a well-defined subset of layouts. More formally,
 * a layout {@code L} is supported by a native linker {@code NL} if:
 * <ul>
 * <li>{@code L} is a value layout {@code V} and {@code V.withoutName()} is a canonical layout</li>
 * <li>{@code L} is a sequence layout {@code S} and all the following conditions hold:
 * <ol>
 * <li>the alignment constraint of {@code S} is set to its
 *     <a href="MemoryLayout.html#layout-align">natural alignment</a>, and</li>
 * <li>{@code S.elementLayout()} is a layout supported by {@code NL}.</li>
 * </ol>
 * </li>
 * <li>{@code L} is a group layout {@code G} and all the following conditions hold:
 * <ol>
 * <li>the alignment constraint of {@code G} is set to its
 *     <a href="MemoryLayout.html#layout-align">natural alignment</a>;</li>
 * <li>the size of {@code G} is a multiple of its alignment constraint;</li>
 * <li>each member layout in {@code G.memberLayouts()} is either a padding layout or
 *     a layout supported by {@code NL}, and</li>
 * <li>{@code G} does not contain padding other than what is strictly required to align
 *      its non-padding layout elements, or to satisfy (2).</li>
 * </ol>
 * </li>
 * </ul>
 *
 * Linker implementations may optionally support additional layouts, such as
 * <em>packed</em> struct layouts. A packed struct is a struct in which there is
 * at least one member layout {@code L} that has an alignment constraint less strict
 * than its natural alignment. This allows to avoid padding between member layouts,
 * as well as avoiding padding at the end of the struct layout. For example:

 * {@snippet lang = java:
 * // No padding between the 2 element layouts:
 * MemoryLayout noFieldPadding = MemoryLayout.structLayout(
 *         ValueLayout.JAVA_INT,
 *         ValueLayout.JAVA_DOUBLE.withByteAlignment(4));
 *
 * // No padding at the end of the struct:
 * MemoryLayout noTrailingPadding = MemoryLayout.structLayout(
 *         ValueLayout.JAVA_DOUBLE.withByteAlignment(4),
 *         ValueLayout.JAVA_INT);
 * }
 * <p>
 * A native linker only supports function descriptors whose argument/return layouts are
 * layouts supported by that linker and are not sequence layouts.
 *
 * <h3 id="function-pointers">Function pointers</h3>
 *
 * Sometimes, it is useful to pass Java code as a function pointer to some native
 * function; this is achieved by using an
 * {@linkplain #upcallStub(MethodHandle, FunctionDescriptor, Arena, Option...) upcall stub}.
 * To demonstrate this, let's consider the following function from the C standard library:
 *
 * {@snippet lang = c:
 * void qsort(void *base, size_t nmemb, size_t size,
 *            int (*compar)(const void *, const void *));
 * }
 *
 * The {@code qsort} function can be used to sort the contents of an array, using a
 * custom comparator function which is passed as a function pointer
 * (the {@code compar} parameter). To be able to call the {@code qsort} function from
 * Java, we must first create a downcall method handle for it, as follows:
 *
 * {@snippet lang = java:
 * Linker linker = Linker.nativeLinker();
 * MethodHandle qsort = linker.downcallHandle(
 *     linker.defaultLookup().findOrThrow("qsort"),
 *         FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS)
 * );
 * }
 *
 * As before, we use {@link ValueLayout#JAVA_LONG} to map the C type {@code size_t} type,
 * and {@link ValueLayout#ADDRESS} for both the first pointer parameter (the array
 * pointer) and the last parameter (the function pointer).
 * <p>
 * To invoke the {@code qsort} downcall handle obtained above, we need a function pointer
 * to be passed as the last parameter. That is, we need to create a function pointer out
 * of an existing method handle. First, let's write a Java method that can compare two
 * int elements passed as pointers (i.e. as {@linkplain MemorySegment memory segments}):
 *
 * {@snippet lang = java:
 * class Qsort {
 *     static int qsortCompare(MemorySegment elem1, MemorySegment elem2) {
 *         return Integer.compare(elem1.get(JAVA_INT, 0), elem2.get(JAVA_INT, 0));
 *     }
 * }
 * }
 *
 * Now let's create a method handle for the comparator method defined above:
 *
 * {@snippet lang = java:
 * FunctionDescriptor comparDesc = FunctionDescriptor.of(JAVA_INT,
 *                                                       ADDRESS.withTargetLayout(JAVA_INT),
 *                                                       ADDRESS.withTargetLayout(JAVA_INT));
 * MethodHandle comparHandle = MethodHandles.lookup()
 *                                          .findStatic(Qsort.class, "qsortCompare",
 *                                                      comparDesc.toMethodType());
 * }
 *
 * First, we create a function descriptor for the function pointer type. Since we know
 * that the parameters passed to the comparator method will be pointers to elements of
 * a C {@code int[]} array, we can specify {@link ValueLayout#JAVA_INT} as the target
 * layout for the address layouts of both parameters. This will allow the comparator
 * method to access the contents of the array elements to be compared. We then
 * {@linkplain FunctionDescriptor#toMethodType() turn} that function descriptor into
 * a suitable {@linkplain java.lang.invoke.MethodType method type} which we then use to
 * look up the comparator method handle. We can now create an upcall stub that points to
 * that method, and pass it, as a function pointer, to the {@code qsort} downcall handle,
 * as follows:
 *
 * {@snippet lang = java:
 * try (Arena arena = Arena.ofConfined()) {
 *     MemorySegment comparFunc = linker.upcallStub(comparHandle, comparDesc, arena);
 *     MemorySegment array = arena.allocateFrom(JAVA_INT, 0, 9, 3, 4, 6, 5, 1, 8, 2, 7);
 *     qsort.invokeExact(array, 10L, 4L, comparFunc);
 *     int[] sorted = array.toArray(JAVA_INT); // [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 ]
 * }
 * }
 *
 * This code creates an off-heap array, copies the contents of a Java array into it, and
 * then passes the array to the {@code qsort} method handle along with the comparator
 * function we obtained from the native linker. After the invocation, the contents
 * of the off-heap array will be sorted according to our comparator function, written in
 * Java. We then extract a new Java array from the segment, which contains the sorted
 * elements.
 *
 * <h3 id="by-ref">Functions returning pointers</h3>
 *
 * When interacting with native functions, it is common for those functions to allocate
 * a region of memory and return a pointer to that region. Let's consider the following
 * function from the C standard library:
 *
 * {@snippet lang = c:
 * void *malloc(size_t size);
 * }
 *
 * The {@code malloc} function allocates a region of memory with the given size,
 * and returns a pointer to that region of memory, which is later deallocated using
 * another function from the C standard library:
 *
 * {@snippet lang = c:
 * void free(void *ptr);
 * }
 *
 * The {@code free} function takes a pointer to a region of memory and deallocates that
 * region. In this section we will show how to interact with these native functions,
 * with the aim of providing a <em>safe</em> allocation API (the approach outlined below
 * can of course be generalized to allocation functions other than {@code malloc} and
 * {@code free}).
 * <p>
 * First, we need to create the downcall method handles for {@code malloc} and
 * {@code free}, as follows:
 *
 * {@snippet lang = java:
 * Linker linker = Linker.nativeLinker();
 *
 * MethodHandle malloc = linker.downcallHandle(
 *     linker.defaultLookup().findOrThrow("malloc"),
 *     FunctionDescriptor.of(ADDRESS, JAVA_LONG)
 * );
 *
 * MethodHandle free = linker.downcallHandle(
 *     linker.defaultLookup().findOrThrow("free"),
 *     FunctionDescriptor.ofVoid(ADDRESS)
 * );
 * }
 *
 * When a native function returning a pointer (such as {@code malloc}) is invoked using
 * a downcall method handle, the Java runtime has no insight into the size or the
 * lifetime of the returned pointer. Consider the following code:
 *
 * {@snippet lang = java:
 * MemorySegment segment = (MemorySegment)malloc.invokeExact(100);
 * }
 *
 * The size of the segment returned by the {@code malloc} downcall method handle is
 * <a href="MemorySegment.html#wrapping-addresses">zero</a>. Moreover, the scope of the
 * returned segment is the global scope. To provide safe access to the segment, we must,
 * unsafely, resize the segment to the desired size (100, in this case). It might also
 * be desirable to attach the segment to some existing {@linkplain Arena arena}, so that
 * the lifetime of the region of memory backing the segment can be managed automatically,
 * as for any other native segment created directly from Java code. Both of these
 * operations are accomplished using the restricted method
 * {@link MemorySegment#reinterpret(long, Arena, Consumer)}, as follows:
 *
 * {@snippet lang = java:
 * MemorySegment allocateMemory(long byteSize, Arena arena) throws Throwable {
 *     MemorySegment segment = (MemorySegment) malloc.invokeExact(byteSize); // size = 0, scope = always alive
 *     return segment.reinterpret(byteSize, arena, s -> {
 *         try {
 *             free.invokeExact(s);
 *         } catch (Throwable e) {
 *             throw new RuntimeException(e);
 *         }
 *     });  // size = byteSize, scope = arena.scope()
 * }
 * }
 *
 * The {@code allocateMemory} method defined above accepts two parameters: a size and an
 * arena. The method calls the {@code malloc} downcall method handle, and unsafely
 * reinterprets the returned segment, by giving it a new size (the size passed to the
 * {@code allocateMemory} method) and a new scope (the scope of the provided arena).
 * The method also specifies a <em>cleanup action</em> to be executed when the provided
 * arena is closed. Unsurprisingly, the cleanup action passes the segment to the
 * {@code free} downcall method handle, to deallocate the underlying region of memory.
 * We can use the {@code allocateMemory} method as follows:
 *
 * {@snippet lang = java:
 * try (Arena arena = Arena.ofConfined()) {
 *     MemorySegment segment = allocateMemory(100, arena);
 * } // 'free' called here
 * }
 *
 * Note how the segment obtained from {@code allocateMemory} acts as any other segment
 * managed by the confined arena. More specifically, the obtained segment has the desired
 * size, can only be accessed by a single thread (the thread that created the confined
 * arena), and its lifetime is tied to the surrounding <em>try-with-resources</em> block.
 *
 * <h3 id="variadic-funcs">Variadic functions</h3>
 *
 * Variadic functions are C functions that can accept a variable number and type of
 * arguments. They are declared with a trailing ellipsis ({@code ...}) at the end of the
 * formal parameter list, such as: {@code void foo(int x, ...);}
 * The arguments passed in place of the ellipsis are called <em>variadic arguments</em>.
 * Variadic functions are, essentially, templates that can be <em>specialized</em> into
 * multiple non-variadic functions by replacing the {@code ...} with a list of
 * <em>variadic parameters</em> of a fixed number and type.
 * <p>
 * It should be noted that values passed as variadic arguments undergo default argument
 * promotion in C. For instance, the following argument promotions are applied:
 * <ul>
 * <li>{@code _Bool} -> {@code unsigned int}</li>
 * <li>{@code [signed] char} -> {@code [signed] int}</li>
 * <li>{@code [signed] short} -> {@code [signed] int}</li>
 * <li>{@code float} -> {@code double}</li>
 * </ul>
 * whereby the signed-ness of the source type corresponds to the signed-ness of the
 * promoted type. The complete process of default argument promotion is described in the
 * C specification. In effect, these promotions place limits on the types that can be
 * used to replace the {@code ...}, as the variadic parameters of the specialized form
 * of a variadic function will always have a promoted type.
 * <p>
 * The native linker only supports linking the specialized form of a variadic function.
 * A variadic function in its specialized form can be linked using a function descriptor
 * describing the specialized form. Additionally, the {@link Linker.Option#firstVariadicArg(int)}
 * linker option must be provided to indicate the first variadic parameter in the
 * parameter list. The corresponding argument layout (if any), and all following
 * argument layouts in the specialized function descriptor, are called
 * <em>variadic argument layouts</em>.
 * <p>
 * The native linker does not automatically perform default argument promotions. However,
 * since passing an argument of a non-promoted type as a variadic argument is not
 * supported in C, the native linker will reject an attempt to link a specialized
 * function descriptor with any variadic argument value layouts corresponding to a
 * non-promoted C type. Since the size of the C {@code int} type is platform-specific,
 * exactly which layouts will be rejected is platform-specific as well. As an example:
 * on Linux/x64 the layouts corresponding to the C types {@code _Bool},
 * {@code (unsigned) char}, {@code (unsigned) short}, and {@code float} (among others),
 * will be rejected by the linker. The {@link #canonicalLayouts()} method can be used to
 * find which layout corresponds to a particular C type.
 * <p>
 * A well-known variadic function is the {@code printf} function, defined in the
 * C standard library:
 *
 * {@snippet lang = c:
 * int printf(const char *format, ...);
 * }
 *
 * This function takes a format string, and a number of additional arguments (the number
 * of such arguments is dictated by the format string). Consider the following
 * variadic call:
 *
 * {@snippet lang = c:
 * printf("%d plus %d equals %d", 2, 2, 4);
 * }
 *
 * To perform an equivalent call using a downcall method handle we must create a function
 * descriptor which describes the specialized signature of the C function we want to
 * call. This descriptor must include an additional layout for each variadic argument we
 * intend to provide. In this case, the specialized signature of the C function is
 * {@code (char*, int, int, int)} as the format string accepts three integer parameters.
 * We then need to use a {@linkplain Linker.Option#firstVariadicArg(int) linker option}
 * to specify the position of the first variadic layout in the provided function
 * descriptor (starting from 0). In this case, since the first parameter is the format
 * string (a non-variadic argument), the first variadic index needs to be set to 1, as
 * follows:
 *
 * {@snippet lang = java:
 * Linker linker = Linker.nativeLinker();
 * MethodHandle printf = linker.downcallHandle(
 *     linker.defaultLookup().findOrThrow("printf"),
 *         FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT),
 *         Linker.Option.firstVariadicArg(1) // first int is variadic
 * );
 * }
 *
 * We can then call the specialized downcall handle as usual:
 *
 * {@snippet lang = java:
 * try (Arena arena = Arena.ofConfined()) {
 *     //prints "2 plus 2 equals 4"
 *     int res = (int)printf.invokeExact(arena.allocateFrom("%d plus %d equals %d"), 2, 2, 4);
 * }
 *}
 *
 * <h2 id="safety">Safety considerations</h2>
 *
 * Creating a downcall method handle is intrinsically unsafe. A symbol in a foreign
 * library does not, in general, contain enough signature information (e.g. arity and
 * types of foreign function parameters). As a consequence, the linker runtime cannot
 * validate linkage requests. When a client interacts with a downcall method handle
 * obtained through an invalid linkage request (e.g. by specifying a function descriptor
 * featuring too many argument layouts), the result of such interaction is unspecified
 * and can lead to JVM crashes.
 * <p>
 * When an upcall stub is passed to a foreign function, a JVM crash might occur, if the
 * foreign code casts the function pointer associated with the upcall stub to a type that
 * is incompatible with the type of the upcall stub, and then attempts to invoke the
 * function through the resulting function pointer. Moreover, if the method handle
 * associated with an upcall stub returns a {@linkplain MemorySegment memory segment},
 * clients must ensure that this address cannot become invalid after the upcall is
 * completed. This can lead to unspecified behavior, and even JVM crashes, since an
 * upcall is typically executed in the context of a downcall method handle invocation.
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and
 * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 22
 */
public sealed interface Linker permits AbstractLinker {

    /**
     * {@return a linker for the ABI associated with the underlying native platform}
     * <p>
     * The underlying native platform is the combination of OS and processor where the
     * Java runtime is currently executing.
     *
     * @apiNote It is not currently possible to obtain a linker for a different
     *          combination of OS and processor.
     * @implSpec A native linker implementation is guaranteed to provide canonical
     *           layouts for <a href="#describing-c-sigs">basic C types</a>.
     * @implNote The libraries exposed by the {@linkplain #defaultLookup() default lookup}
     *           associated with the returned linker are the native libraries loaded in
     *           the process where the Java runtime is currently executing. For example,
     *           on Linux, these libraries typically include {@code libc}, {@code libm}
     *           and {@code libdl}.
     */
    static Linker nativeLinker() {
        return SharedUtils.getSystemLinker();
    }

    /**
     * Creates a method handle that is used to call a foreign function with
     * the given signature and address.
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang=java :
     * linker.downcallHandle(function, options).bindTo(address);
     * }
     *
     * @param address  the native memory segment whose
     *                 {@linkplain MemorySegment#address() base address} is the address
     *                 of the target foreign function
     * @param function the function descriptor of the target foreign function
     * @param options  the linker options associated with this linkage request
     * @return a downcall method handle
     * @throws IllegalArgumentException if the provided function descriptor is not
     *         supported by this linker
     * @throws IllegalArgumentException if {@code !address.isNative()}, or if
     *         {@code address.equals(MemorySegment.NULL)}
     * @throws IllegalArgumentException if an invalid combination of linker options
     *         is given
     * @throws IllegalCallerException If the caller is in a module that does not have
     *         native access enabled
     *
     * @see SymbolLookup
     */
    @CallerSensitive
    @Restricted
    MethodHandle downcallHandle(MemorySegment address,
                                FunctionDescriptor function,
                                Option... options);

    /**
     * Creates a method handle that is used to call a foreign function with
     * the given signature.
     * <p>
     * The Java {@linkplain java.lang.invoke.MethodType method type} associated with the
     * returned method handle is {@linkplain FunctionDescriptor#toMethodType() derived}
     * from the argument and return layouts in the function descriptor, but features an
     * additional leading parameter of type {@link MemorySegment}, from which the address
     * of the target foreign function is derived. Moreover, if the function descriptor's
     * return layout is a group layout, the resulting downcall method handle accepts an
     * additional leading parameter of type {@link SegmentAllocator}, which is used by
     * the linker runtime to allocate the memory region associated with the struct
     * returned by the downcall method handle.
     * <p>
     * Upon invoking a downcall method handle, the linker provides the following
     * guarantees for any argument {@code A} of type {@link MemorySegment} whose
     * corresponding layout is an {@linkplain AddressLayout address layout}:
     * <ul>
     *     <li>{@code A.scope().isAlive() == true}. Otherwise, the invocation
     *         throws {@link IllegalStateException};</li>
     *     <li>The invocation occurs in a thread {@code T} such that
     *         {@code A.isAccessibleBy(T) == true}.
     *         Otherwise, the invocation throws {@link WrongThreadException}; and</li>
     *     <li>{@code A} is kept alive during the invocation. For instance,
     *         if {@code A} has been obtained using a {@linkplain Arena#ofShared() shared arena},
     *         any attempt to {@linkplain Arena#close() close} the arena while the
     *         downcall method handle is still executing will result in an
     *         {@link IllegalStateException}.</li>
     *</ul>
     * <p>
     * Moreover, if the provided function descriptor's return layout is an
     * {@linkplain AddressLayout address layout}, invoking the returned method handle
     * will return a native segment associated with the global scope. Under normal
     * conditions, the size of the returned segment is {@code 0}. However, if the
     * function descriptor's return layout has a
     * {@linkplain AddressLayout#targetLayout() target layout} {@code T}, then the size
     * of the returned segment is set to {@code T.byteSize()}.
     * <p>
     * The returned method handle will throw an {@link IllegalArgumentException} if the
     * {@link MemorySegment} representing the target address of the foreign function is
     * the {@link MemorySegment#NULL} address. If an argument is a {@link MemorySegment},
     * whose corresponding layout is a {@linkplain GroupLayout group layout}, the linker
     * might attempt to access the contents of the segment. As such, one of the
     * exceptions specified by the {@link MemorySegment#get(ValueLayout.OfByte, long)} or
     * the {@link MemorySegment#copy(MemorySegment, long, MemorySegment, long, long)}
     * methods may be thrown. If an argument is a {@link MemorySegment} whose
     * corresponding layout is an {@linkplain AddressLayout address layout}, the linker
     * will throw an {@link IllegalArgumentException} if the segment is a heap memory
     * segment, unless heap memory segments are explicitly allowed through the
     * {@link Linker.Option#critical(boolean)} linker option. The returned method handle
     * will additionally throw {@link NullPointerException} if any argument passed to it
     * is {@code null}.
     *
     * @param function the function descriptor of the target foreign function
     * @param options  the linker options associated with this linkage request
     * @return a downcall method handle
     * @throws IllegalArgumentException if the provided function descriptor is not
     *         supported by this linker
     * @throws IllegalArgumentException if an invalid combination of linker options
     *         is given
     * @throws IllegalCallerException If the caller is in a module that does not have
     *         native access enabled
     */
    @CallerSensitive
    @Restricted
    MethodHandle downcallHandle(FunctionDescriptor function, Option... options);

    /**
     * Creates an upcall stub which can be passed to other foreign functions as a
     * function pointer, associated with the given arena. Calling such a function
     * pointer from foreign code will result in the execution of the provided method
     * handle.
     * <p>
     * The returned memory segment's address points to the newly allocated upcall stub,
     * and is associated with the provided arena. As such, the lifetime of the returned
     * upcall stub segment is controlled by the provided arena. For instance, if the
     * provided arena is a confined arena, the returned upcall stub segment will be
     * deallocated when the provided confined arena is {@linkplain Arena#close() closed}.
     * <p>
     * An upcall stub argument whose corresponding layout is an
     * {@linkplain AddressLayout address layout} is a native segment associated with the
     * global scope. Under normal conditions, the size of this segment argument is
     * {@code 0}. However, if the address layout has a
     * {@linkplain AddressLayout#targetLayout() target layout} {@code T}, then the size
     * of the segment argument is set to {@code T.byteSize()}.
     * <p>
     * The target method handle should not throw any exceptions. If the target method
     * handle does throw an exception, the JVM will terminate abruptly. To avoid this,
     * clients should wrap the code in the target method handle in a try/catch block to
     * catch any unexpected exceptions. This can be done using the
     * {@link java.lang.invoke.MethodHandles#catchException(MethodHandle, Class, MethodHandle)}
     * method handle combinator, and handle exceptions as desired in the corresponding
     * catch block.
     *
     * @param target the target method handle
     * @param function the upcall stub function descriptor
     * @param arena the arena associated with the returned upcall stub segment
     * @param options  the linker options associated with this linkage request
     * @return a zero-length segment whose address is the address of the upcall stub
     * @throws IllegalArgumentException if the provided function descriptor is not
     *         supported by this linker
     * @throws IllegalArgumentException if the type of {@code target} is incompatible
     *         with the type {@linkplain FunctionDescriptor#toMethodType() derived}
     *         from {@code function}
     * @throws IllegalArgumentException if it is determined that the target method handle
     *         can throw an exception
     * @throws IllegalStateException if {@code arena.scope().isAlive() == false}
     * @throws WrongThreadException if {@code arena} is a confined arena, and this method
     *         is called from a thread {@code T}, other than the arena's owner thread
     * @throws IllegalCallerException If the caller is in a module that does not have
     *         native access enabled
     */
    @CallerSensitive
    @Restricted
    MemorySegment upcallStub(MethodHandle target,
                             FunctionDescriptor function,
                             Arena arena,
                             Linker.Option... options);

    /**
     * Returns a symbol lookup for symbols in a set of commonly used libraries.
     * <p>
     * Each {@link Linker} is responsible for choosing libraries that are widely
     * recognized as useful on the OS and processor combination supported by the
     * {@link Linker}. Accordingly, the precise set of symbols exposed by the symbol
     * lookup is unspecified; it varies from one {@link Linker} to another.
     *
     * @implNote It is strongly recommended that the result of {@link #defaultLookup}
     *           exposes a set of symbols that is stable over time. Clients of
     *           {@link #defaultLookup()} are likely to fail if a symbol that was
     *           previously exposed by the symbol lookup is no longer exposed.
     *           <p>If an implementer provides {@link Linker} implementations for
     *           multiple OS and processor combinations, then it is strongly
     *           recommended that the result of {@link #defaultLookup()} exposes, as much
     *           as possible, a consistent set of symbols across all the OS and processor
     *           combinations.
     *
     * @return a symbol lookup for symbols in a set of commonly used libraries
     */
    SymbolLookup defaultLookup();

    /**
     * {@return an unmodifiable mapping between the names of data types used by the ABI
     *          implemented by this linker and their <em>canonical layouts</em>}
     * <p>
     * Each {@link Linker} is responsible for choosing the data types that are widely
     * recognized as useful on the OS and processor combination supported by the
     * {@link Linker}. Accordingly, the precise set of data type names and canonical
     * layouts exposed by the linker are unspecified; they vary from one {@link Linker}
     * to another.
     *
     * @implNote It is strongly recommended that the result of {@link #canonicalLayouts()}
     *           exposes a set of symbols that is stable over time. Clients of
     *           {@link #canonicalLayouts()} are likely to fail if a data type that was
     *           previously exposed by the linker is no longer exposed, or if its
     *           canonical layout is updated.
     *           <p>If an implementer provides {@link Linker} implementations for multiple
     *           OS and processor combinations, then it is strongly recommended that the
     *           result of {@link #canonicalLayouts()} exposes, as much as possible,
     *           a consistent set of symbols across all the OS and processor combinations.
     */
    Map<String, MemoryLayout> canonicalLayouts();

    /**
     * A linker option is used to provide additional parameters to a linkage request.
     * @since 22
     */
    sealed interface Option
            permits LinkerOptions.LinkerOptionImpl {

        /**
         * {@return a linker option used to denote the index indicating the start of the
         *          variadic arguments passed to the function described by the function
         *          descriptor associated with a downcall linkage request}
         * <p>
         * The {@code index} value must conform to {@code 0 <= index <= N}, where
         * {@code N} is the number of argument layouts of the function descriptor used in
         * conjunction with this linker option. When the {@code index} is:
         * <ul>
         * <li>{@code 0}, all arguments passed to the function are passed as variadic
         *     arguments</li>
         * <li>{@code N}, none of the arguments passed to the function are passed as
         *     variadic arguments</li>
         * <li>{@code n}, where {@code 0 < m < N}, the arguments {@code m..N} are passed
         *     as variadic arguments</li>
         * </ul>
         * It is important to always use this linker option when linking a
         * <a href=Linker.html#variadic-funcs>variadic function</a>, even if no variadic
         * argument is passed (the second case in the list above), as this might still
         * affect the calling convention on certain platforms.
         *
         * @implNote The index value is validated when making a linkage request, which is
         *           when the function descriptor against which the index is validated is
         *           available.
         *
         * @param index the index of the first variadic argument layout in the function
         *             descriptor associated with a downcall linkage request
         */
        static Option firstVariadicArg(int index) {
            return new LinkerOptions.FirstVariadicArg(index);
        }

        /**
         * {@return a linker option used to save portions of the execution state
         *          immediately after calling a foreign function associated with a
         *          downcall method handle, before it can be overwritten by the Java
         *          runtime, or read through conventional means}
         * <p>
         * Execution state is captured by a downcall method handle on invocation, by
         * writing it to a native segment provided by the user to the downcall method
         * handle. For this purpose, a downcall method handle linked with this option
         * will feature an additional {@link MemorySegment} parameter directly following
         * the target address, and optional {@link SegmentAllocator} parameters. This
         * parameter, the <em>capture state segment</em>, represents the native segment
         * into which the captured state is written.
         * <p>
         * The capture state segment must have size and alignment compatible with the
         * layout returned by {@linkplain #captureStateLayout}. This layout is a struct
         * layout which has a named field for each captured value.
         * <p>
         * Captured state can be retrieved from the capture state segment by constructing
         * var handles from the {@linkplain #captureStateLayout capture state layout}.
         * <p>
         * The following example demonstrates the use of this linker option:
         * {@snippet lang = "java":
         * MemorySegment targetAddress = ...
         * Linker.Option ccs = Linker.Option.captureCallState("errno");
         * MethodHandle handle = Linker.nativeLinker().downcallHandle(targetAddress, FunctionDescriptor.ofVoid(), ccs);
         *
         * StructLayout capturedStateLayout = Linker.Option.captureStateLayout();
         * VarHandle errnoHandle = capturedStateLayout.varHandle(PathElement.groupElement("errno"));
         * try (Arena arena = Arena.ofConfined()) {
         *     MemorySegment capturedState = arena.allocate(capturedStateLayout);
         *     handle.invoke(capturedState);
         *     int errno = (int) errnoHandle.get(capturedState, 0L);
         *     // use errno
         * }
         * }
         * <p>
         * This linker option can not be combined with {@link #critical}.
         *
         * @param capturedState the names of the values to save
         * @throws IllegalArgumentException if at least one of the provided
         *         {@code capturedState} names is unsupported on the current platform
         * @see #captureStateLayout()
         */
        static Option captureCallState(String... capturedState) {
            Set<CapturableState> set = Stream.of(Objects.requireNonNull(capturedState))
                    .map(Objects::requireNonNull)
                    .map(CapturableState::forName)
                    .collect(Collectors.toSet());
            return new LinkerOptions.CaptureCallState(set);
        }

         /**
         * {@return a struct layout that represents the layout of the capture state
          *         segment that is passed to a downcall handle linked with
          *         {@link #captureCallState(String...)}}
         * <p>
         * The capture state layout is <em>platform-dependent</em> but is guaranteed to be
         * a {@linkplain StructLayout struct layout} containing only {@linkplain ValueLayout value layouts}
         * and possibly {@linkplain PaddingLayout padding layouts}.
         * As an example, on Windows, the returned layout might contain three value layouts named:
         * <ul>
         *     <li>GetLastError</li>
         *     <li>WSAGetLastError</li>
         *     <li>errno</li>
         * </ul>
         * <p>
         * Clients can obtain the names of the supported captured value layouts as follows:
         * {@snippet lang = java:
         *    List<String> capturedNames = Linker.Option.captureStateLayout().memberLayouts().stream()
         *        .map(MemoryLayout::name)
         *        .flatMap(Optional::stream)
         *        .toList();
         * }
         *
         * @see #captureCallState(String...)
         */
        static StructLayout captureStateLayout() {
            return CapturableState.LAYOUT;
        }

        /**
         * {@return a linker option used to mark a foreign function as <em>critical</em>}
         * <p>
         * A critical function is a function that has an extremely short running time in
         * all cases (similar to calling an empty function), and does not call back into
         * Java (e.g. using an upcall stub).
         * <p>
         * Using this linker option is a hint that some implementations may use to apply
         * optimizations that are only valid for critical functions.
         * <p>
         * Using this linker option when linking non-critical functions is likely to have
         * adverse effects, such as loss of performance or JVM crashes.
         * <p>
         * Critical functions can optionally allow access to the Java heap. This allows
         * clients to pass heap memory segments as addresses, where normally only off-heap
         * memory segments would be allowed. The memory region inside the Java heap is
         * exposed through a temporary native address that is valid for the duration of
         * the function call. Use of this mechanism is therefore only recommended when a
         * function needs to do short-lived access to Java heap memory, and copying the
         * relevant data to an off-heap memory segment would be prohibitive in terms of
         * performance.
         *
         * @param allowHeapAccess whether the linked function should allow access to the
         *                        Java heap.
         */
        static Option critical(boolean allowHeapAccess) {
            return allowHeapAccess
                ? LinkerOptions.Critical.ALLOW_HEAP
                : LinkerOptions.Critical.DONT_ALLOW_HEAP;
        }
    }
}
