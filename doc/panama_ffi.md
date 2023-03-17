## State of foreign function support

**March 2023**

**Maurizio Cimadamore**

The Foreign Function & Memory API (FFM API in short) provides access to foreign functions through the `Linker` interface, which has been available as an [incubating](https://openjdk.java.net/jeps/11) API since Java [16](https://openjdk.java.net/jeps/389). A linker allows clients to construct *downcall* method handles — that is, method handles whose invocation targets a native function defined in some native library. In other words, FFM API's foreign function support is completely expressed in terms of Java code and no intermediate native code is required.

### Zero-length memory segments

Before we dive into the specifics of the foreign function support, it would be useful to briefly recap some of the main concepts we have learned when exploring the [foreign memory access support](panama_memaccess.md). The Foreign Memory Access API allows client to create and manipulate *memory segments*. A memory segment is a view over a memory source (either on- or off-heap) which is spatially bounded, temporally bounded and thread-confined. The guarantees ensure that dereferencing a segment that has been created by Java code is always *safe*, and can never result in a VM crash, or, worse, in silent memory corruption.

Now, in the case of memory segments, the above properties (spatial bounds, temporal bounds and confinement) can be known *in full* when the segment is created. But when we interact with native libraries we often receive *raw* pointers; such pointers have no spatial bounds (does a `char*` in C refer to one `char`, or a `char` array of a given size?), no notion of temporal bounds, nor thread-confinement. Raw addresses in the FFM API are modelled using *zero-length memory segments*.

To work with native zero-length memory segments, clients have several options, all of which are <em>unsafe</em>. First, clients can unsafely resize a zero-length memory segment by obtaining a memory segment with the same base address as the zero-length memory segment, but with the desired size, so that the resulting segment can then be accessed directly, as follows:

```java
MemorySegment foreign = someSegment.get(ValueLayout.ADDRESS, 0); // size = 0
                                   .reinterpret(4)               // size = 4
int x = foreign.get(ValueLayout.JAVA_INT, 0);                    // ok
```

In some cases, a client might additionally want to assign new temporal bounds to a zero-length memory segment. This can be done using another variant of the `MemorySegment::reinterpret` method, which returns a new native segment with the desired size and temporal bounds:

```java
MemorySegment foreign = null;
try (Arena arena = Arena.ofConfined()) {
      foreign = someSegment.get(ValueLayout.ADDRESS, 0)           // size = 0, scope = always alive
                           .reinterpret(4, arena, null);          // size = 4, scope = arena.scope()
      int x = foreign.get(ValueLayout.JAVA_INT, 0);               // ok
}
int x = foreign.get(ValueLayout.JAVA_INT, 0); // throws IllegalStateException
```

Note how the new segment behaves as if it was allocated in the provided arena: when the arena is closed, the new segment is no longer accessible.

Alternatively, if the size of the foreign segment is known statically, clients can associate a *target layout* with the address layout used to obtain the segment. When an access operation, or a function descriptor that is passed to a downcall method handle (see below), uses an address value layout with target layout `T`, the runtime will wrap any corresponding raw addresses as segments with size set to `T.byteSize()`:
```java
MemorySegment foreign = someSegment.get(ValueLayout.ADDRESS.withTargetLayout(JAVA_INT), 0); // size = 4
int x = foreign.get(ValueLayout.JAVA_INT, 0);                                               // ok
```

Which approach is taken largely depends on the information that a client has available when obtaining a memory segment wrapping a native pointer. For instance, if such pointer points to a C struct, the client might prefer to resize the segment unsafely, to match the size of the struct (so that out-of-bounds access will be detected by the API). If the size is known statically, using an address layout with the correct target layout might be preferable. In other instances, however, there will be no, or little information as to what spatial and/or temporal bounds should be associated with a given native pointer. In these cases using an unbounded address layout might be preferable.

> Note: Memory segments created using `MemorySegment::reinterpret`, or `OfAddress::withTargetLayout` are completely *unsafe*. There is no way for the runtime to verify that the provided address indeed points to a valid memory location, or that the size and temporal bounds of the memory region pointed by the address indeed conforms to the parameters provided by the client. For these reasons, these methods are *restricted method* in the FFM API. The first time a restricted method is invoked, a runtime warning is generated. Developers can get rid of warnings by specifying the set of modules that are allowed to call restricted methods. This is done by specifying the option `--enable-native-access=M`, where `M` is a module name. Multiple module names can be specified in a comma-separated list, where the special name `ALL-UNNAMED` is used to enable restricted access for all code on the class path. If the `--enable-native-access` option is specified, any attempt to call restricted operations from a module not listed in the option will fail with a runtime exception.

### Symbol lookups

The first ingredient of any foreign function support is a mechanism to lookup symbols in native libraries. In traditional Java/JNI, this is done via the `System::loadLibrary` and `System::load` methods. Unfortunately, these methods do not provide a way for clients to obtain the *address* associated with a given library symbol. For this reason, the Foreign Linker API introduces a new abstraction, namely `SymbolLookup` (similar in spirit to a method handle lookup), which provides capabilities to lookup named symbols; we can obtain a symbol lookup in 3 different ways:

* `SymbolLookup::libraryLookup(String, SegmentScope)` — creates a symbol lookup which can be used to search symbol in a library with the given name. The provided segment scope parameter controls the library lifecycle: that is, when the scope is no longer alive, the library referred to by the lookup will also be closed;
* `SymbolLookup::loaderLookup` — creates a symbol lookup which can be used to search symbols in all the libraries loaded by the caller's classloader (e.g. using `System::loadLibrary` or `System::load`)
* `Linker::defaultLookup` — returns the default symbol lookup associated with a `Linker` instance. For instance, the default lookup of the native linker (see `Linker::nativeLinker`) can be used to look up platform-specific symbols in the standard C library (such as `strlen`, or `getpid`).

Once a lookup has been obtained, a client can use it to retrieve handles to library symbols (either global variables or functions) using the `find(String)` method, which returns an `Optional<MemorySegment>`.  The memory segments returned by the `lookup` are zero-length segments, whose base address is the address of the function or variable in the library.

For instance, the following code can be used to look up the `clang_getClangVersion` function provided by the `clang` library; it does so by creating a *library lookup* whose lifecycle is associated to that of a confined arena.

```java
try (Arena arena = Arena.ofConfined()) {
    SymbolLookup libclang = SymbolLookup.libraryLookup("libclang.so", arena);
    MemorySegment clangVersion = libclang.find("clang_getClangVersion").get();
}
```

### Linker

At the core of the FFM API's foreign function support we find the `Linker` abstraction. This abstraction plays a dual role: first, for downcalls, it allows modelling foreign function calls as plain `MethodHandle` calls (see `Linker::downcallHandle`); second, for upcalls, it allows to convert an existing `MethodHandle` (which might point to some Java method) into a `MemorySegment` which could then be passed to foreign functions as a function pointer (see `Linker::upcallStub`):

```java
interface Linker {
    MethodHandle downcallHandle(MemorySegment symbol, FunctionDescriptor function);
    MemorySegment upcallStub(MethodHandle target, FunctionDescriptor function, SegmentScope scope);
    ... // some overloads omitted here

    static Linker nativeLinker() { ... }
}
```

Both functions take a `FunctionDescriptor` instance — essentially an aggregate of memory layouts which is used to describe the argument and return types of a foreign function in full. Supported layouts are *value layouts* (for scalars and pointers) and *group layouts* (for structs/unions). Each layout in a function descriptor is associated with a carrier Java type (see table below); together, all the carrier types associated with layouts in a function descriptor will determine a unique Java `MethodType` — that is, the Java signature that clients will be using when interacting with said downcall handles, or upcall stubs.

The `Linker::nativeLinker` factory is used to obtain a `Linker` implementation for the ABI associated with the OS and processor where the Java runtime is currently executing. As such, the native linker can be used to call C functions. The following table shows the mapping between C types, layouts and Java carriers under the Linux/macOS native linker implementation; note that the mappings can be platform dependent: on Windows/x64, the C type `long` is 32-bit, so the `JAVA_INT` layout (and the Java carrier `int.class`) would have to be used instead:

| C type                                                       | Layout                                                       | Java carrier    |
| ------------------------------------------------------------ | ------------------------------------------------------------ | --------------- |
| `bool`                                                       | `JAVA_BOOLEAN`                                               | `byte`          |
| `char`                                                       | `JAVA_BYTE`                                                  | `byte`          |
| `short`                                                      | `JAVA_SHORT`                                                 | `short`, `char` |
| `int`                                                        | `JAVA_INT`                                                   | `int`           |
| `long`                                                       | `JAVA_LONG`                                                  | `long`          |
| `long long`                                                  | `JAVA_LONG`                                                  | `long`          |
| `float`                                                      | `JAVA_FLOAT`                                                 | `float`         |
| `double`                                                     | `JAVA_DOUBLE`                                                | `double`        |
| `char*`<br />`int**`<br /> ...                               | `ADDRESS`                                                    | `MemorySegment` |
| `struct Point { int x; int y; };`<br />`union Choice { float a; int b; };`<br />... | `MemoryLayout.structLayout(...)`<br />`MemoryLayout.unionLayout(...)`<br /> | `MemorySegment` |

Both C structs/unions and pointers are modelled using the `MemorySegment` carrier type. However, C structs/unions are modelled in function descriptors with memory layouts of type `GroupLayout`, whereas pointers are modelled using the `ADDRESS` value layout constant (whose size is platform-specific). Moreover, the behavior of a downcall method handle returning a struct/union type is radically different from that of a downcall method handle returning a C pointer:

* downcall method handles returning C pointers will wrap the pointer address into a fresh zero-length memory segment (unless an unbounded address layout is specified);
* downcall method handles returning a C struct/union type will return a *new* segment, of given size (the size of the struct/union). The segment is allocated using a user-provided `SegmentAllocator`, which is provided using an additional prefix parameter inserted in the downcall method handle signature.

A tool, such as `jextract`, will generate all the required C layouts (for scalars and structs/unions) *automatically*, so that clients do not have to worry about platform-dependent details such as sizes, alignment constraints and padding.

### Downcalls

We will now look at how foreign functions can be called from Java using the native linker. Assume we wanted to call the following function from the standard C library:

```c
size_t strlen(const char *s);
```

In order to do that, we have to:

* lookup the `strlen` symbol
* describe the signature of the C function using a function descriptor

* create a *downcall* native method handle with the above information, using the native linker

Here's an example of how we might want to do that (a full listing of all the examples in this and subsequent sections will be provided in the [appendix](#appendix-full-source-code)):

```java
Linker linker = Linker.nativeLinker();
MethodHandle strlen = linker.downcallHandle(
        linker.defaultLookup().find("strlen").get(),
        FunctionDescriptor.of(JAVA_LONG, ADDRESS)
);
```

Note that, since the function `strlen` is part of the standard C library, which is loaded with the VM, we can just use the default lookup of the native linker to look it up. The rest is pretty straightforward — the only tricky detail is how to model `size_t`: typically this type has the size of a pointer, so we can use `JAVA_LONG` both Linux and Windows. On the Java side, we model the `size_t` using a `long` and the pointer is modelled using an `MemorySegment` parameter.

Once we have obtained the downcall method handle, we can just use it as any other method handle:

```java
try (Arena arena = Arena.ofConfined()) {
    long len = strlen.invokeExact(arena.allocateUtf8String("Hello")); // 5
}
```

Here we are using a confined arena to convert a Java string into an off-heap memory segment which contains a `NULL` terminated C string. We then pass that segment to the method handle and retrieve our result in a Java `long`. Note how all this is possible *without* any piece of intervening native code — all the interop code can be expressed in (low level) Java. Note also how we use an arena to control the lifecycle of the allocated C string, which ensures timely deallocation of the memory segment holding the native string.

The `Linker` interface also supports linking of native functions without an address known at link time; when that happens, an address (of type `MemorySegment`) must be provided when the method handle returned by the linker is invoked — this is very useful to support *virtual calls*. For instance, the above code can be rewritten as follows:

```java
MethodHandle strlen_virtual = linker.downcallHandle( // address parameter missing!
		FunctionDescriptor.of(JAVA_LONG, ADDRESS)
);

try (Arena arena = Arena.ofConfined()) {
    long len = strlen_virtual.invokeExact(
        linker.defaultLookup().find("strlen").get() // address provided here!
        arena.allocateUtf8String("Hello")
    ); // 5
}
```

It is important to note that, albeit the interop code is written in Java, the above code can *not* be considered 100% safe. There are many arbitrary decisions to be made when setting up downcall method handles such as the one above, some of which might be obvious to us (e.g. how many parameters does the function take), but which cannot ultimately be verified by the Java runtime. After all, a symbol in a dynamic library is nothing but a numeric offset and, unless we are using a shared library with debugging information, no type information is attached to a given library symbol. This means that the Java runtime has to *trust* the function descriptor passed in<a href="#1"><sup>1</sup></a>; for this reason, the `Linker::nativeLinker` factory is also a restricted method.

When working with shared arenas, it is always possible for the arena associated with a memory segment passed *by reference* to a native function to be closed (by another thread) *while* the native function is executing. When this happens, the native code is at risk of dereferencing already-freed memory, which might trigger a JVM crash, or even result in silent memory corruption. For this reason, the `Linker` API provides some basic temporal safety guarantees: any `MemorySegment` instance passed by reference to a downcall method handle will be *kept alive* for the entire duration of the call.

Performance-wise, the reader might ask how efficient calling a foreign function using a native method handle is; the answer is *very*. The JVM comes with some special support for native method handles, so that, if a give method handle is invoked many times (e.g, inside a *hot* loop), the JIT compiler might decide to generate a snippet of assembly code required to call the native function, and execute that directly. In most cases, invoking native function this way is as efficient as doing so through JNI.

### Upcalls

Sometimes, it is useful to pass Java code as a function pointer to some native function; we can achieve that by using foreign linker support for upcalls. To demonstrate this, let's consider the following function from the C standard library:

```c
void qsort(void *base, size_t nmemb, size_t size,
           int (*compar)(const void *, const void *));
```

The `qsort` function can be used to sort the contents of an array, using a custom comparator function — `compar` — which is passed as a function pointer. To be able to call the `qsort` function from Java we have first to create a downcall method handle for it:

```java
Linker linker = Linker.nativeLinker();
MethodHandle qsort = linker.downcallHandle(
		linker.defaultLookup().lookup("qsort").get(),
        FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS)
);
```

As before, we use `JAVA_LONG` and `long.class` to map the C `size_t` type, and `ADDRESS` for both the first pointer parameter (the array pointer) and the last parameter (the function pointer).

This time, in order to invoke the `qsort` downcall handle, we need a *function pointer* to be passed as the last parameter; this is where the upcall support in foreign linker comes in handy, as it allows us to create a function pointer out of an existing method handle. First, let's write a function that can compare two int elements (passed as pointers):

```java
class Qsort {
    static int qsortCompare(MemorySegment elem1, MemorySegmet elem2) {
        return elem1.get(JAVA_INT, 0) - elem2.get(JAVA_INT, 0);
    }
}
```

Here we can see that the function is performing some *unsafe* dereference of the pointer contents.

Now let's create a method handle pointing to the comparator function above:

```java
FunctionDescriptor comparDesc = FunctionDescriptor.of(JAVA_INT,
                                                      ADDRESS.withTargetLayout(JAVA_INT),
                                                      ADDRESS.withTargetLayout(JAVA_INT));
MethodHandle comparHandle = MethodHandles.lookup()
                                         .findStatic(Qsort.class, "qsortCompare",
                                                     comparDesc.toMethodType());
```

To do that, we first create a function descriptor for the function pointer type. This descriptor uses address layouts that have a `JAVA_INT` target layout, to allow access operations inside the upcall method handle. We use the `FunctionDescriptor::toMethodType` to turn that function descriptor into a suitable `MethodType` instance to be used in a method handle lookup. Now that we have a method handle for our Java comparator function, we finally have all the ingredients to create an upcall stub, and pass it to the `qsort` downcall handle:

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment comparFunc = linker.upcallStub(comparHandle, comparDesc, arena);
    MemorySegment array = session.allocateArray(0, 9, 3, 4, 6, 5, 1, 8, 2, 7);
    qsort.invokeExact(array, 10L, 4L, comparFunc);
    int[] sorted = array.toArray(JAVA_INT); // [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 ]
}
```

The above code creates an upcall stub — `comparFunc` — a function pointer that can be used to invoke our Java comparator function, of type `MemorySegment`. The upcall stub is associated with the provided segment scope instance; this means that the stub will be uninstalled when the arena is closed.

The snippet then creates an off-heap array from a Java array, which is then passed to the `qsort` handle, along with the comparator function we obtained from the foreign linker.  As a side effect, after the call, the contents of the off-heap array will be sorted (as instructed by our comparator function, written in Java). We can than extract a new Java array from the segment, which contains the sorted elements. This is a more advanced example, but one that shows how powerful the native interop support provided by the foreign linker abstraction is, allowing full bidirectional interop support between Java and native.

### Variadic calls

Some C functions are *variadic* and can take an arbitrary number of arguments. Perhaps the most common example of this is the `printf` function, defined in the C standard library:

```c
int printf(const char *format, ...);
```

This function takes a format string, which features zero or more *holes*, and then can take a number of additional arguments that is identical to the number of holes in the format string.

The foreign function support can support variadic calls, but with a caveat: the client must provide a specialized Java signature, and a specialized description of the C signature. For instance, let's say we wanted to model the following C call:

```c
printf("%d plus %d equals %d", 2, 2, 4);
```

To do this using the foreign function support provided by the FFM API we would have to build a *specialized* downcall handle for that call shape, using a linker option to specify the position of the first variadic layout, as follows:

```java
Linker linker = Linker.nativeLinker();
MethodHandle printf = linker.downcallHandle(
		linker.defaultLookup().lookup("printf").get(),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT)
        Linker.Option.firstVariadicArg(1) // first int is variadic
);
```

Then we can call the specialized downcall handle as usual:

```java
try (Arena arena = Arena.ofConfined()) {
    int res = (int)printf.invokeExact(arena.allocateUtf8String("%d plus %d equals %d"), 2, 2, 4); //prints "2 plus 2 equals 4"
}
```

While this works, and provides optimal performance, it has some limitations<a href="#2"><sup>2</sup></a>:

* If the variadic function needs to be called with many shapes, we have to create many downcall handles
* while this approach works for downcalls (since the Java code is in charge of determining which and how many arguments should be passed) it fails to scale to upcalls; in that case, the call comes from native code, so we have no way to guarantee that the shape of the upcall stub we have created will match that required by the native function.

### Appendix: full source code

The full source code containing most of the code shown throughout this document can be seen below:

```java
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.MemorySegment;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.*;

public class Examples {

    static Linker LINKER = Linker.nativeLinker();
    static SymbolLookup STDLIB = LINKER.defaultLookup();

    public static void main(String[] args) throws Throwable {
        strlen();
        strlen_virtual();
        qsort();
        printf();
    }

    public static void strlen() throws Throwable {
        MethodHandle strlen = LINKER.downcallHandle(
                STDLIB.find("strlen").get(),
                FunctionDescriptor.of(JAVA_LONG, ADDRESS)
        );

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment hello = arena.allocateUtf8String("Hello");
            long len = (long) strlen.invokeExact(hello); // 5
            System.out.println(len);
        }
    }

    public static void strlen_virtual() throws Throwable {
        MethodHandle strlen_virtual = LINKER.downcallHandle(
                FunctionDescriptor.of(JAVA_LONG, ADDRESS)
        );

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment hello = arena.allocateUtf8String("Hello");
            long len = (long) strlen_virtual.invokeExact(
                STDLIB.find("strlen").get(),
                hello); // 5
            System.out.println(len);
        }
    }

    static class Qsort {
        static int qsortCompare(MemorySegment addr1, MemorySegment addr2) {
            return addr1.get(JAVA_INT, 0) - addr2.get(JAVA_INT, 0);
        }
    }

    public static void qsort() throws Throwable {
        MethodHandle qsort = LINKER.downcallHandle(
                STDLIB.find("qsort").get(),
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS)
        );
        FunctionDescriptor comparDesc = FunctionDescriptor.of(JAVA_INT,
                                                              ADDRESS.withTargetLayout(JAVA_INT),
                                                              ADDRESS.withTargetLayout(JAVA_INT));
        MethodHandle comparHandle = MethodHandles.lookup()
                                         .findStatic(Qsort.class, "qsortCompare",
                                                     comparDesc.toMethodType());

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment comparFunc = LINKER.upcallStub(
                comparHandle, comparDesc, arena);

            MemorySegment array = arena.allocateArray(JAVA_INT, 0, 9, 3, 4, 6, 5, 1, 8, 2, 7);
            qsort.invokeExact(array, 10L, 4L, comparFunc);
            int[] sorted = array.toArray(JAVA_INT); // [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 ]
            System.out.println(Arrays.toString(sorted));
        }
    }

    public static void printf() throws Throwable {
        MethodHandle printf = LINKER.downcallHandle(
                STDLIB.find("printf").get(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT),
                Linker.Option.firstVariadicArg(1) // first int is variadic
        );
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocateUtf8String("%d plus %d equals %d\n");
            int res = (int)printf.invokeExact(s, 2, 2, 4);
        }
    }
}
```



* <a id="1"/>(<sup>1</sup>):<small> In reality this is not entirely new; even in JNI, when you call a `native` method the VM trusts that the corresponding implementing function in C will feature compatible parameter types and return values; if not a crash might occur.</small>
* <a id="2"/>(<sup>2</sup>):<small> Previous iterations of the FFM API provided a `VaList` class that could be used to model a C `va_list`. This class was later dropped from the FFM API as too implementation specific. It is possible that a future version of the `jextract` tool might provide higher-level bindings for variadic calls. </small>
