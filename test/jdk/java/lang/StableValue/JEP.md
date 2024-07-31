# Stable Values (Preview)

## Summary

Introduce a _Stable Values API_, which provides performant immutable value holders where elements
are initialized _at most once_. Stable Values offer the performance and safety benefits of
final fields, while offering greater flexibility as to the timing of initialization. This is a [preview API](https://openjdk.org/jeps/12).

## Goals

- Provide an easy and intuitive API to describe value holders that can change at most once.
- Decouple declaration from initialization without significant footprint or performance penalties.
- Reduce the amount of static initializer and/or field initialization code.
- Uphold integrity and consistency, even in a multithreaded environment.

## Non-goals

- It is not a goal to provide additional language support for expressing lazy computation.
This might be the subject of a future JEP.
- It is not a goal to prevent or deprecate existing idioms for expressing lazy initialization.

## Motivation

Java allows developers to control whether fields are mutable or not. 

* Mutable fields can be updated multiple times, and from any arbitrary position in the code.
* Immutable fields (i.e. `final` fields), can only be updated _once_, and only in very specific places: the
  class initializer (for a static immutable field) or the class constructor(for an instance immutable field). 
 
Unfortunately, in Java there is no way to define a field that can be updated _at most once_ (i.e. fields that are
either not updated at all or are updated exactly once) and from _any_ arbitrary position in the code:

| Field kind         | #Updates | Code update location              |
|--------------------|----------|-----------------------------------|
| Mutable            | [0, ∞)   | Anywhere                          |
| `final`            | 1        | Constructor or static initializer |
| at-most-once (N/A) | [0, 1]   | Anywhere                          |

_Table 1, showing properties of mutable, immutable, and at-most-once (currently not available) fields._

"At-most-once fields" would be essential to expensive cache computations associated with method calls, so that they can
be reused across multiple calls. For instance, creating a logger or reading application configurations from an external
database. Furthermore, if the VM is made aware, a field is an "at-most-once field" and it is set, it may
[constant-fold](https://en.wikipedia.org/wiki/Constant_folding) the field value, thereby providing crucial performance
and energy efficiency gains. 

It is also important to stress a method called to compute an "at-most-once field" might have intended or
unintended side effects and therefore, it would be vital to also guarantee the method is invoked at most once, even in
a multithreaded environment.

Another property of "at-most-once fields" would be, they are written to at most once but
are likely read at many occasions. Hence, updating the field would not be so time-critical whereas every effort
should be made to make reading the field performant. 

Using existing Java semantics, it is possible to devise solutions that _partially_ emulates an "at-most-once field".

Here is how a naïve `Logger` cache backed by a mutable field could look like:

```
// A naïve cache. Do not use this solution!
public class Cache {

    private Logger logger;

    public Logger get() {
        if (logger == null) {
            logger = Logger.getLogger("com.company.Foo");
        }
        return logger;
    }
}
```

This solution does not work in a multithreaded environment. One of many problems is, updates made by one thread to the
`logger` may  not be visible to other threads, thereby allowing the `logger` variable to be updated several times and
consequently the `Logger::getLogger` method can be called several times.

Here is how thread safety can be added together with a guarantee, `logger` is only updated at most once
(and thereby `Logger::getLogger` is called at most once):

```
// A field protected by synchonization. Do not use this solution!
public class Cache {

    private Logger logger;

    public synchronized Logger get() {
        if (logger == null) {
            logger = Logger.getLogger("com.company.Foo");
        }
        return logger;
    }
}
```

While this works, acquiring the `synhronized` monitor is slow and prevents multiple threads from accessing the cached
`Logger` instance simultaneously once computed.

The solution above can be modified to use the
[*double-checked locking idiom*](https://en.wikipedia.org/wiki/Double-checked_locking) which would improve the
situation a bit:

```
// A field protected by double-checked locking. Do not use this solution!
public class Cache {

    private volatile Logger logger;

    public Logger logger() {
        // Use a local variable to save a volatile read if `logger` is non-null
        Logger v = logger;
        if (v == null) {
            synchronized (this) {
                // Re-read the cached value under synchronization
                v = logger;
                if (v == null) {
                    // Assign both `logger` and `v` in one line
                    logger = v = Logger.getLogger("com.company.Foo");
                }
            }
        }
        return v;
    }
}
```
While the solution above is an improvement over the previous one, the double-checked locking idiom is brittle and easy
to get subtly wrong (see *Java Concurrency in Practice*, 16.2.4, by Brian Goetz). For example, a common error is forgetting
to declare the field `volatile` resulting in the risk of observing incomplete objects. Another issue is that synchronization
is made on the `Cache` instance itself, potentially opening up for deadlock situations. Furthermore, every access to the
cached value is made using `volatile` semantics which may be slow on some platforms.

Now, further imagine a situation where there are several loggers to be cached and where we want to reference the cached
values using an `int` index (i.e. 0 -> "com.company.Foo0", 1 -> "com.company.Foo1", etc.):

```
// A an array of values protected by double-checked locking. Do not use this solution!
public class Cache {

    private static final VarHandle ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(Logger[].class);
    private static final int SIZE = 10;
    private final Logger[] loggers = new Logger[SIZE];

    public Logger logger(int i) {
        Logger v = (Logger) ARRAY_HANDLE.getVolatile(loggers, i);
        if (v == null) {
            synchronized (this) {
                v = loggers[i];
                if (v == null) {
                    ARRAY_HANDLE.setVolatile(loggers, i, v = Logger.getLogger("com.company.Foo" + i));
                }
            }
        }
        return v;
    }
}
```

There is no built-in semantics for declaring an array's _elements_ should be accessed using `volatile` semantics in
Java and so, volatile access has to be made explicit in the user code, for example via the supported API of
`VarHandle`. This solution is also plagued with the problem that it synchronizes on `this` and, in addition to exposing
itself for deadlocks, prevents any of the cached values from being computed simultaneously by distinct threads.

While some of the problems described above might be solved (e.g. by synchronizing on internal mutex fields) they
become increasingly complex, error-prone, and hard to maintain.

A more fundamental problem with all the solutions above is that access to the cached values cannot be adequately
optimized by just-in-time compilers, as they cannot reliably assume that field values will, in fact, change at most
once. Alas, the solutions do not express the intent of the programmer; neither to just-in-time compilers nor to
readers of the code.

What we are missing -- in all cases -- is a *safe* way to *promise* that a constant will be initialized by the
time it is used, with a value that is computed at most once. Such a mechanism would give the Java runtime maximum
opportunity to stage and optimize its computation, thus avoiding the penalties (static footprint, loss of runtime
optimizations, and brittleness) that plague the workarounds shown above and below. Moreover, such a mechanism
should gracefully scale to handle collections of constant values, while retaining efficient computer resource
management.

It would be advantageous if "compute-at-most-once fields" could be expresses something along these lines where
`Foo`, `Bar`, and `Baz` indicates some Java class and the methods `Foo::xxxx`, `Bar::yyyy`, `Baz::zzzz` represents
some method with a yet-to-determine name:

```
// Declare a cache that can hold a single Logger instance
Foo<Logger> cache = ... Logger.getLogger("com.company.Foo") ...;
...
// Just returns the value if set. Otherwise computes, sets, and returns the value.
// If another thread is computing a value, wait until it has completed.
Logger logger = cache.xxxx();
```

and for several compute-at-most-once values indexed by an `int`:

```
// Declare a cache that can hold 10 Logger instance
Bar<Logger> cache = ... 10 ... i -> Logger.getLogger("com.company.Foo" + i) ...
...
// Just returns the value at the provided index if set. Otherwise computes, sets, and returns the value.
// If another thread is computing a value at the provided index, wait until it has completed.
// Values can be computed in parallel by distinct threads.
Logger logger = cache.yyyy(7);
```

and even for several compute-at-most-once values associated with some key of arbitrary type `K` (where we use Strings
as the key type in the example below):

```
// Declare a cache that can hold a finite number of Logger instance
// associated with the same finite number of `keys` Strings.
Baz<String, Logger> cache = ... keys ... Logger::getLogger...
...
// Just returns the value associated with the provided string if set. Otherwise computes, sets, and returns the value.
// If another thread is computing a value for the provided String, wait until it has completed.
// Values can be computed in parallel by distinct threads.
Logger logger = cache.zzzz("com.company.Foo");
```

For interoperability with legacy code, it would also be desirable if some of the standard collection types could be
expressed as compute-at-most-once constructs in a similar fashion:

```
// Declare a List whose elements are lazily computed upon being first accessed via List::get
List<Logger> lazyList = ... 10 ... i -> Logger.getLogger("com.company.Foo" + i) ...

// Declare a Map whose values are lazily computed upon being first accessed via Map::get
Map<String, Logger> lazyMap = ... keys ... Logger::getLogger...
```

A final note should be made about static fields. Initialization of `static` and `final` fields can be broken up by
leveraging the laziness already built into class loading. Often referred to as the
[*initialization-on-demand_holder_idiom*](https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom), this
technique moves lazily initialized state into a helper class which is then loaded on-demand, so its initialization is
only performed when the data is actually needed, rather than unconditionally initializing constants when a class is
first referenced:

```
// Ordinary static initialization
private static final Logger LOGGER = Logger.getLogger("com.company.Foo");
...
LOGGER.log(Level.DEBUG, ...);
```

we can defer initialization until we actually need it, like so:

```
// Initialization-on-demand holder idiom
Logger logger() {
    class Holder {
         static final Logger LOGGER = Logger.getLogger("com.company.Foo");
    }
    return Holder.LOGGER;
}
...
logger().log(Level.DEBUG, ...);
```

The code above ensures that the `Logger` object is created only when actually required. The (possibly expensive)
initializer for the logger lives in the nested `Holder` class, which will only be initialized when the `logger`
method accesses the `LOGGER` field. While this idiom works well, its reliance on the class loading process comes
with significant drawbacks. First, each constant whose computation needs to be deferred generally requires its own
holder class, thus introducing a significant static footprint cost. Second, this idiom is only really applicable
if the field initialization is suitably isolated, not relying on any other parts of the object state.

## Description

The Stable Values API defines an interface so that client code in libraries and applications can

- Define and use a stable value:
    - [`StableValue.newInstance()`](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/java/lang/StableValue.html#newInstance())
- Define various _caching_ functions:
    - [`StableValue.newCachedSupplier()`](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/java/lang/StableValue.html#newCachingSupplier(java.util.function.Supplier,java.util.concurrent.ThreadFactory))
    - [`StableValue.newCachedIntFunction()`](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/java/lang/StableValue.html#newCachingIntFunction(int,java.util.function.IntFunction,java.util.concurrent.ThreadFactory))
    - [`StableValue.newCachedFunction()`](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/java/lang/StableValue.html#newCachingFunction(java.util.Set,java.util.function.Function,java.util.concurrent.ThreadFactory))
- Define _lazy_ collections:
    - [`StableValue.lazyList(int size)`](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/java/lang/StableValue.html#lazyList(int,java.util.function.IntFunction))
    - [`StableValue.lazyMap(Set<K> keys)`](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/java/lang/StableValue.html#lazyMap(java.util.Set,java.util.function.Function))

The Stable Values API resides in the [java.lang](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/java/lang/package-summary.html) package of the [java.base](https://cr.openjdk.org/~pminborg/stable-values2/api/java.base/module-summary.html) module.

### Stable values

A _stable value_ is a thin, atomic, non-blocking, thread-safe, set-at-most-once, stable value holder
eligible for certain JVM optimizations if set to a value. It is expressed as an object of type
`java.lang.StableValue`, which, like `Future`, is a holder for some computation that may or may not have
occurred yet. Fresh (unset) `StableValue` instances are created via the factory method `StableValue::newInstance`:

```
class Foo {
    // 1. Declare a Stable field
    private static final StableValue<Logger> LOGGER = StableValue.newInstance();

    static Logger logger() {

        if (!LOGGER.isSet()) {
            // 2. Set the stable value _after_ the field was declared
            //    If another thread has already set a value, this is a
            //    no-op and we will continue to 3. and get that value.
            LOGGER.trySet(Logger.getLogger("com.company.Foo"));
        }

        // 3. Access the stable value with as-declared-final performance
        return LOGGER.orElseThrow();
    }
    ...
    logger().log(Level.DEBUG, ...);
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
on explicit memory barriers needed only during the single store operation rather than performing
volatile access on each retrieval operation.

In addition, stable values are eligible for constant folding optimizations by the JVM. In many
ways, this is similar to the holder-class idiom in the sense it offers the same
performance and constant-folding characteristics but, `StableValue` incurs a lower static
footprint since no additional class is required.

Looking at the basic example above, it becomes evident, that several threads may invoke the `Logger::getLogger`
method simultaneously if they call the `logger()` method at about the same time. Even though
`StableValue` will guarantee, that only one of these results will ever be exposed to the many
competing threads, there might be applications where it is a requirement, that a supplying method is
called *only once*. This brings us to the introduction of _cached functions_.

### Cached functions

So far, we have talked about the fundamental features of StableValue as s securely
wrapped `@Stable` value holder. However, it has become apparent, stable primitives are amenable
to composition with other constructs in order to create more high-level and powerful features.

[Cached (or Memoized) functions](https://en.wikipedia.org/wiki/Memoization) are functions where the output for a
particular input value is computed only once and is remembered such that remembered outputs can be
reused for subsequent calls with recurring input values.

In cases where the invoke-at-most-once property of a `Supplier` is important, the
Stable Values API offers a _cached supplier_ which is a caching, thread-safe, stable,
lazily computed `Supplier` that records the value of an _original_ `Supplier` upon being first
accessed via its `Supplier::get` method. In a multithreaded scenario, competing threads will block
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
    ...
    logger().log(Level.DEBUG, ...);
}
```

Note: the last `null` parameter signifies an optional thread factory that will be explained at the end
of this chapter.

In the example above, the original `Supplier` provided is invoked at most once per loading of the containing
class `Foo` (`Foo`, in turn, can be loaded at most once into any given `ClassLoader`) and it is backed
by a lazily computed stable value.

Analogous to how a `Supplier` can be cached using a backing stable value, a similar pattern
can be used for an `IntFunction` that will record its cached values in a backing array of
stable value elements. Here is an example where we manually map logger numbers
(0 -> "com.company.Foo" , 1 -> "com.company.Bar") to loggers:

```
class CachedNum {

    // 1. Centrally declare a cached IntFunction backed by a list of StableValue elements
    private static final IntFunction<Logger> LOGGERS =
            StableValue.newCachingIntFunction(2, CachedNum::fromNumber, null);

    // 2. Define a function that is to be called the first
    //    time a particular message number is referenced
    //    The given loggerNumber is manually mapped to loggers
    private static Logger fromNumber(int loggerNumber) {
        return switch (loggerNumber) {
            case 0 -> Logger.getLogger("com.company.Foo");
            case 1 -> Logger.getLogger("com.company.Bar");
            default -> throw new IllegalArgumentException();
        };
    }

    // 3. Access the cached element with as-declared-final performance
    //    (evaluation made before the first access)
    Logger logger = LOGGERS.apply(0);
    ...
    logger.log(Level.DEBUG, ...);
}
```

Note: Again, the last null parameter signifies an optional thread factory that will be explained at the end of this chapter.

As can be seen, manually mapping numbers to strings is a bit tedious. This brings us to the most general cached function
variant provided is a cached `Function` which, for example,  can make sure `Logger::getLogger` in one of the first examples
above is invoked at most once per input value (provided it executes successfully) in a multithreaded environment. Such a
cached `Function` is almost always faster and more resource efficient than a `ConcurrentHashMap`.

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
    ...
    logger.log(Level.DEBUG, ...);
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

As noted above, the cached-returning factories in the Stable Values API offers an optional
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
...
logger.log(Level.DEBUG, ...);
```

This can provide a best-of-several-worlds situation where the cached function can be quickly defined (as no
computation is made by the defining thread), the holder value is computed in background threads (thus neither
interfering significantly with the critical startup path nor with future accessing threads), and the threads actually
accessing the holder value can directly access the holder value with as-if-final performance and without having to compute
a holder value. This is true under the assumption, that the background threads can complete computation before accessing
threads requires a holder value. If this is not the case, at least some reduction of blocking time can be enjoyed as
the background threads have had a head start compared to the accessing threads.

### Stable collections

The Stable Values API also provides factories that allow the creation of new
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

Note how there's only one variable of type `List<E>` to initialize even though every
computation is performed independently of the other element of the list when accessed (i.e. no
blocking will occur across threads computing distinct elements simultaneously). Also, the
`IntSupplier` mapper provided at creation is only invoked at most once for each distinct input
value. The Stable Values API allows modeling this cleanly, while still preserving
good constant-folding guarantees and integrity of updates in the case of multithreaded access.

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
...
logger("com.company.Foo").log(Level.DEBUG, ...);
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

The Stable Values is a [preview API](https://openjdk.org/jeps/12), disabled by default.
To use the Stable Value APIs, the JVM flag `--enable-preview` must be passed in, as follows:

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

The work described here will likely enable subsequent work to provide pre-evaluated stable value fields at
compile, condensation, and/or runtime.
