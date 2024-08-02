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

Java allows developers to control whether fields should be mutable or not. Mutable fields can be updated multiple times, and from any arbitrary position in the code and by any thread. As such, mutable fields are often used to model complex objects whose state can be updated several times throughout their lifetimes, such as the contents of a text field in a UI component. Conversely, immutable fields (i.e. `final` fields), must be updated exactly *once*, and only in very specific places: the class initializer (for a static immutable field) or the class constructor(for an instance immutable field). As such, `final` fields are typically used to model values that act as *constants* (albeit shallowly so) throughout the lifetime of a class (in the case of `static` fields) or of an instance (in the case of an instance field).

Most of the time deciding whether an object should feature mutable or immutable state is straightforward enough. There are however cases where a field is subject to *constrained mutation*. That is, a field's value is neither constant, nor can it be mutated at will. Consider a program that might want to mutate a password field at most three times before it becomes immutable, in order to reflect the three login attempts allowed for a user; further mutation would result in some kind of exception. Expressing this kind of constrained mutation is hard, and cannot be achieved without the help of advanced type-system [calculi](https://en.wikipedia.org/wiki/Dependent_type). However, one important and simpler case of constrained mutation is that of a field whose updated *at most once*. As we shall see, the lack of a mechanism to capture this specific kind of constrained mutation in the Java platform comes at a considerable cost of performance and expressiveness.

#### An example: memoization

Constrained mutation is essential to reliably cache the result of an expensive method call, so that it can be reused several times throughout the lifetime of an application (this technique is also known as [memoization](https://en.wikipedia.org/wiki/Memoization)). A nearly ubiquitous example of such an expensive method call is that to obtain a logger object through which an application's events can be reported. Obtaining a logger often entails expensive operations, such as reading and parsing configuration data, or prepare the backing storage where logging events will be recorded. Since these operations are expensive, an application will typically want to move them as much *forward in time* as possible: after all, an application might never need to log an event, so why paying the cost for this expensive initialization? Moreover, as some of these operation results in side effects - such as the creation of files and folders - it is crucial that they are executed _at most once_.

Combining mutable fields and encapsulation is a common way to approximate at-most-once update semantics. Consider the following example, where a logger object is created in the `Application::getLogger` method:

```
public class Application {

  private Logger logger;

  public Logger getLogger() {
    if (logger == null) {
      logger = Logger.create("com.company.Application");
    }
    return logger;
  }
}
```

As the `logger` field is private, the only way for clients to access it is to call the `getLogger` method. This method first tests whether a logger is already available, and if so that logger is returned. Otherwise, it proceeds to the creation of a *new* logger object, which is then stored in the `logger` field. In this way, we guarantee that the logger object is created at most once: the first time the `Application::getLogger` method is invoked.

Unfortunately, the above solution does not work in a multithreaded environment. For instance, updates to the `logger` field made by one thread may not be immediately visible to other threads. This condition might result in multiple concurrent calls to the `Logger::create` method, thereby violating the "at-most-once" update guarantee.

##### Thread-safety with double-checked locking

One possible way to achieve thread safety would be to serialize access to the `Application::getLogger` method - i.e. by marking that method as `synchronized`. However, doing so has a performance cost, as multiple threads cannot concurrently obtain the application's logger object, even *long after* this object has been computed, and safely stored in the `logger` field. In other words, using  `synchronized` amounts at applying a *permanent* performance tax on *all* logger accesses, in the rare event that a race occurs during the initial update of the `logger` field.

A more efficient solution is the so-called [class holder idiom](https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom), which achieves thread-safe "at-most-once" update guarantees by leaning on the lazy semantics of class initialization. However, for this approach to work correctly, the memoized data should be `static`, which is not the case here.

In order to achieve thread-safety without compromising performance, developers often resorts to the brittle [double-checked idiom](https://en.wikipedia.org/wiki/Double-checked_locking):

```
class Application {

    private volatile Logger logger;

    public Logger logger() {
        Logger v = logger;
        if (v == null) {
            synchronized (this) {
                v = logger;
                if (v == null) {
                    logger = v = Logger.create("com.company.Application");
                }
            }
        }
        return v;
    }
}
```

The basic idea behind double-checked locking is to reduce the chances for callers to enter a `synchronized` block. After all, in the common case, we expect the `logger` field to already contain a logger object, in which case we can just return that object, without any performance hit. In the rare event where `logger` is not set, we must enter a `synchronized` block, and check its value again (as such value might have changed upon entering the block). For the double-checked idiom to work correctly, it is necessary for the `logger` field is marked as `volatile`. This ensures that reading that field across multiple threads can result in one of two outcomes: the field either appears to be uninitialized (its value set to `null`), or initialized (its value set to the final logger object). That is, no *dirty reads* are possible.

##### Problems with double-checked locking

Unfortunately, double-checked locking has several inherent design flaws:

* *brittleness* - the convoluted nature of the code required to write a correct double-checked locking makes it all too easy for developers to make subtle mistakes. A very common one is forgetting to add the `volatile` keyword to the `logger` field.
* *lack of expressiveness* - even when written correctly, the double-checked idiom leaves a lot to be desired. The "at-most-once" mutation guarantee is not explicitly manifest in the code: after all the `logger` field is just a plain mutable field. This leaves important semantics gaps that is impossible to plug. For example, the `logger` field can be accidentally mutated in another method of the `Application` class. In another example, the field might be reflectively mutated using `setAccessible`. Avoiding these pitfalls is ultimately left to developers.
* *lack of optimizations* - as the `logger` field is updated at most once, one might expect the JVM to optimize access to this field accordingly, e.g. by [constant-folding](https://en.wikipedia.org/wiki/Constant_folding) access to an already-initialized `logger` field. Unfortunately, since `logger` is just a plan mutable field, the JVM cannot trust the field to never be updated again. As such, access to at-most-once fields, when realized with double-checked locking is not as efficient as it could be.
* *limited applicability* - double-checked locking fails to scale to more complex use cases where e.g. the client might need an *array* of values where each element can be updated at most once. In this case, marking the array field as `volatile` is not enough, as the `volatile` modifier doesn't apply to the array *elements* but to the array as a whole. Instead, clients would have to resort to even more complex solutions using where at-most-once array elements are accessed using `VarHandles`. Needless to say, such solutions are even more brittle and error-prone, and should be avoided at all costs.

##### At-most-once as a first class concept

At-most-once semantics is unquestionably critical to implement important use cases such as caches and memoized functions. Unfortunately, existing workarounds, such as double-checked locking, cannot be considered adequate replacements for *first-class* "at-most-once" support. What we are missing is a way to *promise* that a variable will be initialized by the time it is used, with a value that is computed at most once, and *safely* across multiple threads. Such a mechanism would give the Java runtime maximum opportunity to stage and optimize its computation, thus avoiding the penalties that plague the workarounds shown above. Moreover, such a mechanism should gracefully scale to handle *collections* of "at-most-once" variables, while retaining efficient computer resource management.

When fully realized, first-class support for "at-most-once" sematics would fill an important gap between mutable and immutable fields, as shown in the table below:


| Storage kind   | #Updates | Code update location              | Constant folding  | Concurrent updates       |
| -------------- |----------| --------------------------------- | ----------------- |--------------------------|
| non-`final`    | [0, ∞)   | Anywhere                          | no                | yes                      |
| `final`        | 1 [1]    | Constructor or static initializer | yes               | no [1]                   |
| "at-most-once" | [0, 1]   | Anywhere                          | yes, after update | yes, but only one "wins" |

[1]: https://docs.oracle.com/javase/specs/jls/se11/html/jls-17.html#jls-17.5 (JSL 17.5)

_Table 1: properties of mutable, immutable, and at-most-once variables_

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

### Caching functions

So far, we have talked about the fundamental features of StableValue as s securely
wrapped stable value holder. However, it has become apparent, stable primitives are amenable
to composition with other constructs in order to create more high-level and powerful features.

[Caching (or Memoized) functions](https://en.wikipedia.org/wiki/Memoization) are functions where the output for a
particular input value is computed only once and is remembered such that remembered outputs can be
reused for subsequent calls with recurring input values.

In cases where the invoke-at-most-once property of a `Supplier` is important, the
Stable Values API offers a _cached supplier_ which is a caching, thread-safe, stable,
lazily computed `Supplier` that records the value of an _original_ `Supplier` upon being first
accessed via its `Supplier::get` method. In a multithreaded scenario, competing threads will block
until the first thread has computed a cached value. Unsurprisingly, the cached value is
stored internally in a stable value.

Here is how the code in the previous example can be improved using a caching supplier:

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

    // 1. Centrally declare a caching IntFunction backed by a list of StableValue elements
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

As can be seen, manually mapping numbers to strings is a bit tedious. This brings us to the most general caching function
variant provided is a caching `Function` which, for example,  can make sure `Logger::getLogger` in one of the first examples
above is invoked at most once per input value (provided it executes successfully) in a multithreaded environment. Such a
caching `Function` is almost always faster and more resource efficient than a `ConcurrentHashMap`.

Here is what a caching `Function` lazily holding two loggers could look like:

```
class Cahced {

    // 1. Centrally declare a caching function backed by a map of stable values
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
input keys for the caching function. Providing a non-valid input for a caching `Function` (or a caching `IntFunction`)
would incur an `IllegalArgumentException`.

An advantage with caching functions, compared to working directly with `StableValue` instances, is that the
initialization logic can be centralized and maintained in a single place, usually at the same place where
the caching function is defined.

Additional caching function types, such a caching `Predicate<K>` (backed by a lazily computed
`Map<K, StableValue<Boolean>>`) or a caching `BiFunction` can be custom-made. An astute reader will be able
to write such constructs in a few lines.

#### Background threads

As noted above, the caching-returning factories in the Stable Values API offers an optional
tailing thread factory parameter from which new value-computing background threads will be created:

```
// 1. Centrally declare a caching function backed by a map of stable values
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

This can provide a best-of-several-worlds situation where the caching function can be quickly defined (as no
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

Even though a caching `IntFunction` may sometimes replace a lazy `List` and a caching `Function` may be
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
