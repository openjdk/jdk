# Stable Values & Collections (Preview)

## Summary

Introduce a _Stable Values & Collections API_, which provides performant immutable value holders where elements
are initialized _at most once_. Stable Values & Collections offer the performance and safety benefits of
final fields, while offering greater flexibility as to the timing of initialization. This is a [preview API](https://openjdk.org/jeps/12).

## Goals

- Provide an easy and intuitive API to describe value holders that can change at most once.
- Decouple declaration from initialization without significant footprint or performance penalties.
- Reduce the amount of static initializer and/or field initialization code.
- Uphold integrity and consistency, even in a multi-threaded environment.

## Non-goals

- It is not a goal to provide additional language support for expressing lazy computation.
This might be the subject of a future JEP.
- It is not a goal to prevent or deprecate existing idioms for expressing lazy initialization.

## Motivation

Some internal JDK classes are relying heavily on the annotation `jdk.internal.vm.annotation.@Stable`
to mark scalar and array fields whose values or elements will change *at most once*, thereby providing
crucial performance, energy efficiency, and flexibility benefits. 

Unfortunately, the powerful `@Stable` annotation cannot be used directly by client code thereby severely
restricting its applicability. The Stable Values & Collections API rectifies this imbalance
between internal and client code by providing safe wrappers around the `@Stable` annotation. Hence, all _the
important benefits of `@Stable` are now made available to regular Java developers and third-party 
library developers_.

One of the benefits with `@Stable` is it makes a marked field eligible for [constant-folding](https://en.wikipedia.org/wiki/Constant_folding). 
Publicly exposing `@Stable` without a safe API, like the Stable Values & Collections API, would have
rendered it unsafe as further updating a `@Stable` field after its initial update will result
in undefined behavior, as the JIT compiler might have *already* constant-folded the (now overwritten)
field value.

### Existing solutions

Most Java developers have heard the advice "prefer immutability" (Effective
Java, Third Edition, Item 17, by Joshua Bloch). Immutability confers many advantages including:

* an immutable object can only be in one state
* the invariants of an immutable object can be enforced by its constructor
* immutable objects can be freely shared across threads
* immutability enables all manner of runtime optimizations

Java's main tool for managing immutability is `final` fields (and more recently, `record` classes).
Unfortunately, `final` fields come with restrictions. Final instance fields must be set by the end of
the constructor, and `static final` fields during class initialization. Moreover, the order in which
`final` field initializers are executed is determined by the [textual order](https://docs.oracle.com/javase/specs/jls/se7/html/jls-13.html#jls-12.4.1)
and is then made explicit in the resulting class file. As such, the initialization of a `final`
field is fixed in time; it cannot be arbitrarily moved forward. In other words, developers
cannot cause specific constants to be initialized after the class or object is initialized.
This means that developers are forced to choose between finality and all its
benefits, and flexibility over the timing of initialization. Developers have
devised several strategies to ameliorate this imbalance, but none are
ideal.

For instance, monolithic class initializers can be broken up by leveraging the
laziness already built into class loading. Often referred to as the
[_class-holder idiom_](https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom),
this technique moves lazily initialized state into a helper class which is then
loaded on-demand, so its initialization is only performed when the data is
actually needed, rather than unconditionally initializing constants when a class
is first referenced:
```
// ordinary static initialization
private static final Logger LOGGER = Logger.getLogger("com.company.Foo");
...
LOGGER.log(Level.DEBUG, ...);
```
we can defer initialization until we actually need it:
```
// Initialization-on-demand holder idiom
Logger logger() {
    class Holder {
         static final Logger LOGGER = Logger.getLogger("com.company.Foo");
    }
    return Holder.LOGGER;
}
...
LOGGER.log(Level.DEBUG, ...);
```
The code above ensures that the `Logger` object is created only when actually
required. The (possibly expensive) initializer for the logger lives in the
nested `Holder` class, which will only be initialized when the `logger` method
accesses the `LOGGER` field. While this idiom works well, its reliance on the
class loading process comes with significant drawbacks. First, each constant
whose computation needs to be deferred generally requires its own holder
class, thus introducing a significant static footprint cost. Second, this idiom
is only really applicable if the field initialization is suitably isolated, not
relying on any other parts of the object state.

It should be noted that even though eventually outputting a message is slow compared to
obtaining the `Logger` instance itself, the `LOGGER::log`method starts with checking if
the selected `Level` is enabled or not. This latter check is a relatively fast operation
and so, in the case of disabled loggers, the `Logger` instance retrieval performance is
important. For example, logger output for `Level.DEBUG` is almost always disabled in production
environments.

Alternatively, the [_double-checked locking idiom_](https://en.wikipedia.org/wiki/Double-checked_locking), can also be used
for deferring the evaluation of field initializers. The idea is to optimistically
check if the field's value is non-null and if so, use that value directly; but
if the value observed is null, then the field must be initialized, which, to be
safe under multi-threaded access, requires acquiring a lock to ensure
correctness:
```
// Double-checked locking idiom
class Foo {
    private volatile Logger logger;
    public Logger logger() {
        Logger v = logger;
        if (v == null) {
            synchronized (this) {
                v = logger;
                if (v == null) {
                    logger = v = Logger.getLogger("com.company.Foo");
                }
            }
        }
        return v;
    }
}
```
The double-checked locking idiom is brittle and easy to get
subtly wrong (see _Java Concurrency in Practice_, 16.2.4.) For example, a common error
is forgetting to declare the field `volatile` resulting in the risk of observing incomplete objects.

While the double-checked locking idiom can be used for both class and instance
variables, its usage requires that the field subject to initialization is marked
as non-final. This is not ideal for several reasons:

* it would be possible for code to accidentally modify the field value, thus violating
the immutability assumption of the enclosing class.
* access to the field cannot be adequately optimized by just-in-time compilers, as they
cannot reliably assume that the field value will, in fact, never change. An example of
similar optimizations in existing Java implementations is when a `MethodHandle` is held
in a `static final` field, allowing the runtime to generate machine code that is competitive
with direct invocation of the corresponding method.

Furthermore, the idiom shown above needs to be modified to properly handle `null` values, for example
using a [sentinel](https://en.wikipedia.org/wiki/Sentinel_value) value.

The situation is even worse when clients need to operate on a _collection_ of immutable values.

An example of this is an array that holds HTML pages that correspond to an error code in the range [0, 7]
where each element is pulled in from the file system on-demand, once actually used:

```
class ErrorMessages {

    private static final int SIZE = 8;

    // 1. Declare an array of error pages to serve up
    private static final String[] MESSAGES = new String[SIZE];

    // 2. Define a function that is to be called the first
    //    time a particular message number is referenced
    private static String readFromFile(int messageNumber) {
        try {
            return Files.readString(Path.of("message-" + messageNumber + ".html"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static synchronized String message(int messageNumber) {
        // 3. Access the memoized array element under synchronization
        //    and compute-and-store if absent.
        String page = MESSAGES[messageNumber];
        if (page == null) {
            page = readFromFile(messageNumber);
            MESSAGES[messageNumber] = page;
        }
        return page;
    }

 }
```
We can now retrieve an error page like so:
```
String errorPage = ErrorMessages.errorPage(2);

// <!DOCTYPE html>
// <html lang="en">
//   <head><meta charset="utf-8"></head>
//   <body>Payment was denied: Insufficient funds.</body>
// </html>
```

Unfortunately, this approach provides a plethora of challenges. First, retrieving the values
from a static array is slow, as said values cannot be constant-folded. Even worse, access to the
array is guarded by synchronization that is not only slow but will block access to the array for
all elements whenever one of the elements is under computation. Furthermore, the class holder idiom
(see above) is undoubtedly insufficient in this case, as the number of required holder classes is 
*statically unbounded* - it depends on the value of the parameter `SIZE` which may change in future
variants of the code.

What we are missing -- in all cases -- is a way to *promise* that a constant will be initialized
by the time it is used, with a value that is computed at most once. Such a mechanism would give
the Java runtime maximum opportunity to stage and optimize its computation, thus avoiding the penalties
(static footprint, loss of runtime optimizations) that plague the workarounds shown above. Moreover, such
a mechanism should gracefully scale to handle collections of constant values, while retaining efficient
computer resource management.

## Description

The Stable Values & Collections API defines an interface so that client code in libraries and applications can

- Define and use stable (scalar) values:
    - [`StableValue.newInstance()`](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/java/lang/StableValue.html)
- Define various _cached_ functions:
    - [`StableValue.newCachedSupplier()`](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/java/lang/StableValue.html#newCachedSupplier(Supplier))
    - [`StableValue.newCachedIntFunction()`](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/java/lang/StableValue.html#newCachedIntFunction(int,IntFunction))
    - [`StableValue.newCachedFunction()`](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/java/lang/StableValue.html#newCachedFunction(Set,Function))
- Define _lazy_ collections:
    - [`StableValue.lazyList(int size)`](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/java/lang/StableValue.html#ofList(int))
    - [`StableValue.lazyMap(Set<K> keys)`](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/java/lang/StableValue.html#ofMap(java.util.Set))

The Stable Values & Collections API resides in the [java.lang](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/java/lang/package-summary.html) package of the [java.base](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/module-summary.html) module.

### Stable values

A _stable value_ is a thin, atomic, non-blocking, thread-safe, set-at-most-once, stable value holder
eligible for certain JVM optimizations if set to a value. It is expressed as an object of type 
`jdk.lang.StableValue`, which, like `Future`, is a holder for some computation that may or may not have
occurred yet. Fresh (unset) `StableValue` instances are created via the factory method `StableValue::newInstance`:

```
class Foo {
    // 1. Declare a Stable field
    private static final StableValue<Logger> LOGGER = StableValue.newInstance();

    static Logger logger() {

        if (!LOGGER.isSet()) {
            // 2. Set the stable value _after_ the field was declared
            LOGGER.trySet(Logger.getLogger("com.company.Foo"));
        }

        // 3. Access the stable value with as-declared-final performance
        return LOGGER.orElseThrow();
    }
}
```

Setting a stable value is an atomic, non-blocking, thread-safe operation, i.e. `StableValue::trySet`,
either results in successfully initializing the `StableValue` to a value, or retaining
an already set value. This is true regardless of whether the stable value is accessed by a single
thread, or concurrently, by multiple threads.

A stable value may be set to `null` which then will be considered its set value.
Null-averse applications can also use `StableValue<Optional<V>>`.

When retrieving values, `StableValue` instances holding reference values can be faster
than reference values managed via double-checked-idiom constructs as stable values rely
on explicit memory barriers rather than performing volatile access on each retrieval
operation. 

In addition, stable values are eligible for constant folding optimizations by the JVM. In many
ways, this is similar to the holder-class idiom in the sense it offers the same
performance and constant-folding characteristics but, `StableValue` incurs a lower static
footprint since no additional class is required.

Looking at the basic example above, it becomes evident, several threads may invoke the `Logger::getLogger`
method simultaneously if they call the `logger()` method at about the same time. Even though
`StableValue` will guarantee, that only one of these results will ever be exposed to the many
competing threads, there might be applications where it is a requirement, that a supplying method is
called *only once*. This brings us to the introduction of _cached functions_.  

### Cached functions

So far, we have talked about the fundamental features of StableValue as securely
wrapped `@Stable` value holders. However, it has become apparent, stable primitives are amenable
to composition with other constructs in order to create more high-level and powerful features.

[Cached (or Memoized) functions](https://en.wikipedia.org/wiki/Memoization) are functions where the output for a
particular input value is computed only once and is remembered such that remembered outputs can be
reused for subsequent calls with recurring input values.

In cases where the invoke-at-most-once property of a `Supplier` is important, the 
Stable Values & Collections API offers a _cached supplier_ which is a caching, thread-safe, stable,
lazily computed `Supplier` that records the value of an _original_ `Supplier` upon being first
accessed via its `Supplier::get` method. In a multi-threaded scenario, competing threads will block
until the first thread has computed a cached value. Unsurprisingly, the cached value is
stored internally in a stable value.

Here is how the code in the previous example can be improved using a cached supplier:

```
class Foo {

    // 1. Centrally declare a caching supplier and define how it should be computed
    private static final Supplier<Logger> LOGGER =
            StableValue.newCachingSupplier( () -> Logger.getLogger("com.company.Foo"), null);

    static Logger logger() {
        // 2. Access the cached value with as-declared-final performance
        //    (single evaluation made before the first access)
        return LOGGER.get();
    }
}
```

Note: the last `null` parameter signifies an optional thread factory that will be explained at the end
of this chapter.

In the example above, the original `Supplier` provided is invoked at most once per loading of the containing
class `Foo` (`Foo`, in turn, can be loaded at most once into any given `ClassLoader`) and it is backed
by a lazily computed stable value upholding the invoke-at-most-once invariant.

Similarly to how a `Supplier` can be cached using a backing stable value, a similar pattern
can be used for an `IntFunction` that will record its cached values in a backing list of 
stable value elements. Here is how the error message example above can be improved using
a caching `IntFunction`:

```
class ErrorMessages {

    private static final int SIZE = 8;
    
    // 1. Centrally declare a cached IntFunction backed by a list of StableValue elements
    private static final IntFunction<String> ERROR_FUNCTION =
            StableValue.newCachingIntFunction(SIZE, ErrorMessages::readFromFile, null);

    // 2. Define a function that is to be called the first
    //    time a particular message number is referenced
    private static String readFromFile(int messageNumber) {
        try {
            return Files.readString(Path.of("message-" + messageNumber + ".html"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    // 3. Access the cached element with as-declared-final performance
    //    (evaluation made before the first access)
    String msg = ERROR_FUNCTION.apply(2);
}
    
// <!DOCTYPE html>
// <html lang="en">
//   <head><meta charset="utf-8"></head>
//   <body>Payment was denied: Insufficient funds.</body>
// </html>
```

Finally, the most general cached function variant provided is a cached `Function` which, for example,
can make sure `Logger::getLogger` in one of the first examples above is invoked at most once
per input value (provided it executes successfully) in a multi-threaded environment. Such a cached
`Function` is almost always faster and more resource efficient than a `ConcurrentHashMap`.

Here is what a caching `Function` lazily holding two loggers could look like:

```
class Cahced {

    // 1. Centrally declare a cached function backed by a map of stable values
    private static final Function<String, Logger> LOGGERS =
            StableValue.newCachingFunction(Set.of("com.company.Foo", "com.company.Bar"),
            Logger::getLogger, null);

    private static final String NAME = "com.company.Foo";

    // 2. Access the cached value via the function with as-declared-final
    //    performance (evaluation made before the first access)
    Logger logger = LOGGERS.apply(NAME);
}
```

It should be noted that the enumerated set of valid inputs given at creation time constitutes the only valid
input keys for the cached function. Providing a non-valid input for a cached `Function` (or a cached `IntFunction`) 
would incur an `IllegalArgumentException`. 

An advantage with cached functions, compared to working directly with `StableValue` instances, is that the
initialization logic can be centralized and maintained in a single place, usually at the same place where
the cached function is defined.

Additional cached function types, such a cached `Predicate<K>` (backed by a lazily computed 
`Map<K, StableValue<Boolean>>`) or a cached `BiFunction` can be custom-made. An astute reader will be able
to write such constructs in a few lines.

#### Background threads

As noted above, the cached-returning factories in the Stable Values & Collections API offers an optional
tailing thread factory parameter from which new value-computing background threads will be created:

```
// 1. Centrally declare a cached function backed by a map of stable values
//    computed in the background by two distinct virtual threads.
private static final Function<String, Logger> LOGGERS =
        StableValue.newCachingFunction(Set.of("com.company.Foo", "com.company.Bar"),
        Logger::getLogger,
        // Create cheap virtual threads for background computation
        Thread.ofVirtual().factory()); 
        
... Somewhere else in the code        

private static final String NAME = "com.company.Foo";

// 2. Access the cached value via the function with as-declared-final
//    performance (evaluation made before the first access)
Logger logger = LOGGERS.apply(NAME);        
```

This can provide a best-of-several-worlds situation where the cached function can be quickly defined (as no
computation is made by the defining thread), the holder value is computed in background threads (thus neither
interfering significantly with the critical startup path nor with future accessing threads), and the threads actually
accessing the holder value can access the holder value with as-if-final performance and without having to compute
a holder value. This is true under the assumption, that the background threads can complete computation before accessing
threads requires a holder value. If this is not the case, at least some reduction of blocking time can be enjoyed as
the background threads have had a head start compared to the accessing threads.

### Stable collections

The Stable Values & Collections API also provides factories that allow the creation of new
collection variants that are lazy, shallowly immutable, and stable:

- `List` of stable elements 
- `Map` of stable values 

Lists of lazily computed stable elements are objects of type `List<E>` where each
element in such a list enjoys the same properties as a `StableValue`.

Like a `StableValue` object, a lazy `List` of stable value elements is created via a factory 
method while additionally providing the `size` of the desired `List` and a mapper of type 
`IntFunction<? extends E>` to be used when computing the lazy elements on demand:

```
List<E> lazyList = StableValue.lazyList(size, mapper);
```

Note how there's only one field of type `List<E>` to initialize even though every
computation is performed independently of the other element of the list when accessed (i.e. no
blocking will occur across threads computing distinct elements simultaneously). Also, the
`IntSupplier` mapper provided at creation is only invoked at most once for each distinct input
value. The Stable Values & Collections API allows modeling this cleanly, while still preserving 
good constant-folding guarantees and integrity of updates in the case of multi-threaded access.

It should be noted that even though a lazily computed list of stable elements might mutate its
internal state upon external access, it is _still shallowly immutable_ because _no first-level
change can ever be observed by an external observer_. This is similar to other immutable classes,
such as `String` (which internally caches its `hash` value), where they might rely on mutable
internal states that are carefully kept internally and that never shine through to the outside world.

Just as a lazy `List` can be created, a `Map` of lazily computed stable values can also be defined
and used similarly:

```
// 1. Declare a lazy stable map of loggers with two allowable keys:
//    "com.company.Foo" and "com.company.Bar"
static final Map<String, Logger> LOGGERS =
        StableValue.lazyMap(Set.of("com.company.Foo", "com.company.Bar"), Logger::getLogger);

// 2. Access the lazy map with as-declared-final performance
//    (evaluation made before the first access)
static Logger logger(String name) {
    return LOGGERS.get(name);
}
```

In the example above, only two input values were used. However, this concept allows declaring a 
large number of stable values which can be easily retrieved using arbitrarily, but pre-specified, 
keys in a resource-efficient and performant way. For example, high-performance, non-evicting caches
may now be easily and reliably realized.

Analogue to a lazy list, the lazy map guarantees the function provided at map creation
(used to lazily compute the map values) is invoked at most once per key (absent any Exceptions),
even though used from several threads.

Even though a cached `IntFunction` may sometimes replace a lazy `List` and a cached `Function` may be
used in place of a lazy `Map`, a lazy `List` or `Map` oftentimes provides better interoperability with
existing libraries. For example, if provided as a method parameter. 

### Preview feature

The Stable Values & Collections is a [preview API](https://openjdk.org/jeps/12), disabled by default.
To use the Stable Value & Collections APIs, the JVM flag `--enable-preview` must be passed in, as follows:

- Compile the program with `javac --release 24 --enable-preview Main.java` and run it with `java --enable-preview Main`; or,

- When using the source code launcher, run the program with `java --source 24 --enable-preview Main.java`; or,

- When using `jshell`, start it with `jshell --enable-preview`.

## Alternatives

There are other classes in the JDK that support lazy computation including `Map`, `AtomicReference`, `ClassValue`,
and `ThreadLocal` all of which, unfortunately, support arbitrary mutation and thus, hinder the JVM from reasoning
about constantness thereby preventing constant folding and other optimizations.

So, alternatives would be to keep using explicit double-checked locking, maps, holder classes, Atomic classes,
and third-party frameworks. Another alternative would be to add language support for immutable value holders.

## Risks and assumptions

Creating an API to provide thread-safe computed constant fields with an on-par performance with holder
classes efficiently is a non-trivial task. It is, however, assumed that the current JIT implementations
will likely suffice to reach the goals of this JEP.

## Dependencies

The work described here will likely enable subsequent work to provide pre-evaluated computed
constant fields at compile, condensation, and/or runtime.
