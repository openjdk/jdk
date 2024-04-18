# Stable Values & Collections (Preview)

## Summary

Introduce a _Stable Values & Collections_ API, which provides immutable value holders where elements are initialized
_at most once_. Stable Values & Collections offer the performance and safety benefits of final fields, while offering
greater flexibility as to the timing of initialization. This is a [preview API](https://openjdk.org/jeps/12).

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

Most Java developers have heard the advice "prefer immutability" (Effective
Java, Item 17). Immutability confers many advantages including:

* an immutable object can only be in one state
* the invariants of an immutable object can be enforced by its constructor
* immutable objects can be freely shared across threads
* immutability enables all manner of runtime optimizations.

Java's main tool for managing immutability is `final` fields (and more recently, `record` classes).
Unfortunately, `final` fields come with restrictions. Final instance fields must be set by the end of
the constructor, and `static final` fields during class initialization. Moreover, the order in which `final`
field initializers are executed is determined by the [textual order](https://docs.oracle.com/javase/specs/jls/se7/html/jls-13.html#jls-12.4.1)
and is then made explicit in the resulting class file. As such, the initialization of a `final`
field is fixed in time; it cannot be arbitrarily moved forward. In other words, developers
cannot cause specific constants to be initialized after the class or object is initialized.
This means that developers are forced to choose between finality and all its
benefits, and flexibility over the timing of initialization. Developers have
devised several strategies to ameliorate this imbalance, but none are
ideal.

For instance, monolithic class initializers can be broken up by leveraging the
laziness already built into class loading.  Often referred to as the
[_class-holder idiom_](https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom),
this technique moves lazily initialized state into a helper class which is then
loaded on-demand, so its initialization is only performed when the data is
actually needed, rather than unconditionally initializing constants when a class
is first referenced:
```
// ordinary static initialization
private static final Logger LOGGER = Logger.getLogger("com.foo.Bar");
...
LOGGER.log(Level.INFO, ...);
```
we can defer initialization until we actually need it:
```
// Initialization-on-demand holder idiom
Logger logger() {
    class Holder {
         static final Logger LOGGER = Logger.getLogger("com.foo.Bar");
    }
    return Holder.LOGGER;
}
...
LOGGER.log(Level.INFO, ...);
```
The code above ensures that the `Logger` object is created only when actually
required. The (possibly expensive) initializer for the logger lives in the
nested `Holder` class, which will only be initialized when the `logger` method
accesses the `LOGGER` field.  While this idiom works well, its reliance on the
class loading process comes with significant drawbacks.  First, each constant
whose computation needs to be deferred generally requires its own holder
class, thus introducing a significant static footprint cost.  Second, this idiom
is only really applicable if the field initialization is suitably isolated, not
relying on any other parts of the object state.

It should be noted that even though eventually outputting a message is slow compared to
obtaining the `Logger` instance itself, the `LOGGER::log`method starts with checking if
the selected `Level` is enabled or not. This latter check is a relatively fast operation
and so, in the case of disabled loggers, the `Logger` instance retrieval performance is
important. For example, logger output for `Level.INFO` is likely disabled in most production
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
                    logger = v = Logger.getLogger("com.foo.Bar");
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

Furthermore, the idiom shown above needs to be modified to properly handle `null` values, for example using
a [sentinel](https://en.wikipedia.org/wiki/Sentinel_value) value.

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
from a static array is slow, as said values cannot be [constant-folded](https://en.wikipedia.org/wiki/Constant_folding). Even worse, access to
the array is guarded by synchronization that is not only slow but will block access to the array for
all elements whenever one of the elements is under computation. Furthermore, the class holder idiom (see above)
is undoubtedly insufficient in this case, as the number of required holder classes is *statically unbounded* - it
depends on the value of the parameter `SIZE` which may change in future variants of the code.

What we are missing -- in all cases -- is a way to *promise* that a constant will be initialized
by the time it is used, with a value that is computed at most once. Such a mechanism would give
the Java runtime maximum opportunity to stage and optimize its computation, thus avoiding the penalties
(static footprint, loss of runtime optimizations) that plague the workarounds shown above. Moreover,
such a mechanism should gracefully scale to handle collections of constant values, while retaining
efficient computer resource management.

The attentive reader might have noticed the similarity between what is sought after here and the JDK internal
annotation`jdk.internal.vm.annotation.@Stable`. This annotation is used by *JDK code* to mark scalar and
array variables whose values or elements will change *at most once*. This annotation is powerful and often
crucial to achieving optimal performance, but it is also easy to misuse: further updating a `@Stable` field
after its initial update will result in undefined behavior, as the JIT compiler might have *already*
constant-folded the (now overwritten) field value. In other words, what we are after is a *safe* and *efficient*
wrapper around the `@Stable` mechanism - in the form of a new Java SE API that might be enjoyed by
_all_ client and 3rd-party Java code (and not the JDK alone).

## Description

### Preview feature

Stable Values & Collections is a [preview API](https://openjdk.org/jeps/12), disabled by default.
To use the Stable Value and Collections APIs, the JVM flag `--enable-preview` must be passed in, as follows:

- Compile the program with `javac --release 24 --enable-preview Main.java` and run it with `java --enable-preview Main`; or,

- When using the source code launcher, run the program with `java --source 24 --enable-preview Main.java`; or,

- When using `jshell`, start it with `jshell --enable-preview`.

### Outline

The Stable Values & Collections API defines functions and an interface so that client code in libraries and applications can

- Define and use stable (scalar) values:
  - [`StableValue`](https://cr.openjdk.org/~pminborg/stable-values/api/java.base/java/lang/StableValue.html)
- Define collections:
  - [`StableValue.ofList(int size)`](https://cr.openjdk.org/~pminborg/stable-values/api/java.base/java/lang/StableValue.html#ofList(int)),
  - [`StableValue.ofMap(Set<K> keys)`](https://cr.openjdk.org/~pminborg/stable-values/api/java.base/java/lang/StableValue.html#ofMap(java.util.Set))

The Stable Values & Collections API resides in the [java.lang](https://cr.openjdk.org/~pminborg/stable-values/api/java.base/java/lang/package-summary.html) package of the [java.base](https://cr.openjdk.org/~pminborg/stable-values/api/java.base/module-summary.html) module.

### Stable values

A _stable value_ is a holder object that is set at most once whereby it
goes from "unset" to "set". It is expressed as an object of type `jdk.internal.lang.StableValue`,
which, like `Future`, is a holder for some computation that may or may not have occurred yet.
Fresh (unset) `StableValue` instances are created via the factory method `StableValue::of`:

```
class Bar {
    // 1. Declare a Stable field
    private static final StableValue<Logger> LOGGER = StableValue.of();

    static Logger logger() {

        if (!LOGGER.isSet()) {
            // 2. Set the stable value _after_ the field was declared
            return LOGGER.setIfUnset(Logger.getLogger("com.foo.Bar"));
        }

        // 3. Access the stable value with as-declared-final performance
        return LOGGER.orThrow();
    }
}
```
Setting a stable value is an atomic, thread-safe operation, i.e. `StableValue::setIfUnset`,
either results in successfully initializing the `StableValue` to a value, or returns
an already set value. This is true regardless of whether the stable value is accessed by a single
thread, or concurrently, by multiple threads.

A stable value may be set to `null` which then will be considered its set value.
Null-averse applications can also use `StableValue<Optional<V>>`.

In many ways, this is similar to the holder-class idiom in the sense it offers the same
performance and constant-folding characteristics. It also incurs a lower static footprint
since no additional class is required.

However, there is _an important distinction_; several threads may invoke the `Logger::getLogger`
method simultaneously if they call the `logger()` method at about the same time. Even though `StableValue` will guarantee, that only
one of these results will ever be exposed to the many competing threads, there might be applications where
it is a requirement, that a supplying method is only called once.

In such cases, it is possible to compute and set an unset value on-demand as shown in this example in which case
`StableValue` will uphold the invoke-at-most-once invariant for the provided `Supplier`:

```
class Bar {
    // 1. Declare a stable field
    private static final StableValue<Logger> LOGGER = StableValue.of();

    static Logger logger() {
        // 2. Access the stable value with as-declared-final performance
        //    (single evaluation made before the first access)
        return LOGGER.computeIfUnset( () -> Logger.getLogger("com.foo.Bar") );
    }
}
```

When retrieving values, `StableValue` instances holding reference values are faster
than reference values managed via double-checked-idiom constructs as stable values rely
on explicit memory barriers rather than performing volatile access on each retrieval
operation. In addition, stable values are eligible for constant folding optimizations.

### Stable collections

While initializing a single field of type `StableValue` is cheap (remember, creating a new `StableValue`
object only creates the *holder* for the value), this (small) initialization cost has
to be paid for each field of type `StableValue` declared by the class. As a result, the class static
and/or instance initializer will keep growing with the number of `StableValue` fields, thus degrading performance.

To handle these cases, the Stable Values & Collections API provides constructs that allow the creation and handling of a
*`List` of stable elements*. Such a `List` is a list whose stable-value elements are created lazily on-demand when a particular element is accessed. Lists of lazily computed values are objects of type `List<StableValue<V>>`.
Consequently, each element in the list enjoys the same properties as a `StableValue` but may require fewer resources.

Like a `StableValue` object, a `List` of stable value elements is created via a factory method by providing the size
of the desired `List`:

```
static <V> List<StableValue<V>> StableValue.ofList(int size) { ... }
```

This allows for improving the handling of lists with stable values and enables a much better
implementation of the `ErrorMessages` class mentioned earlier. Here is an improved version
of the class which is now using the newly proposed API:

```
class ErrorMessages {

    private static final int SIZE = 8;

    // 1. Declare a stable list of default error pages to serve up
    private static final List<StableValue<String>> MESSAGES = StableValue.ofList(SIZE);

    // 2. Define a function that is to be called the first
    //    time a particular message number is referenced
    private static String readFromFile(int messageNumber) {
        try {
            return Files.readString(Path.of("message-" + messageNumber + ".html"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String errorPage(int messageNumber) {
        // 3. Access the stable list element with as-declared-final performance
        //    (evaluation made before the first access)
        return StableValue.computeIfUnset(MESSAGES, messageNumber, ErrorMessages::readFromFile);
    }

}
```

Just like before, we can perform retrieval of error pages like this:

```
String errorPage = ErrorMessages.errorPage(2);

// <!DOCTYPE html>
// <html lang="en">
//   <head><meta charset="utf-8"></head>
//   <body>Payment was denied: Insufficient funds.</body>
// </html>
```

Note how there's only one field of type `List<StableValue<String>>` to initialize even though every computation is
performed independently of the other element of the list when accessed (i.e. no blocking will occur across threads
computing distinct elements simultaneously). Also, the `IntSupplier` provided at computation is only invoked
at most once for each distinct index. The Stable Values & Collections API allows modeling this cleanly, while
still preserving good constant-folding guarantees and integrity of updates in the case of multi-threaded access.

It should be noted that even though a lazily computed list of stable elements might mutate its internal state
upon external access, it is _still shallowly immutable_ because _no first-level change can ever be observed by
an external observer_. This is similar to other immutable classes, such as `String` (which internally caches its
`hash` value), where they might rely on mutable internal states that are carefully kept internally and that never
shine through to the outside world.

Just as a `List` can be lazily computed, a `Map` of lazily computed stable values can also be defined and used similarly.
In the example below, we lazily compute a map's stable values for an enumerated collection of pre-defined keys:

```
class MapDemo {

    // 1. Declare a stable map of loggers with two allowable keys:
    //    "com.foo.Bar" and "com.foo.Baz"
    static final Map<String, StableValue<Logger>> LOGGERS =
            StableValue.ofMap(Set.of("com.foo.Bar", "com.foo.Baz"));

    // 2. Access the memoized map with as-declared-final performance
    //    (evaluation made before the first access)
    static Logger logger(String name) {
        return StableValue.computeIfUnset(LOGGERS, name, Logger::getLogger);
    }
}
```

This concept allows declaring a large number of stable values which can be easily retrieved using arbitrarily, but
pre-specified, keys in a resource-efficient and performant way. For example, high-performance, non-evicting caches
may now be easily and reliably realized.

Providing an `EnumSet<K>` to the `StableValue::ofMap` factory will unlock additional storage and performance
optimizations for the returned map with stable values.

It is worth remembering, that the stable collections all promise the function provided at computation
(used to lazily compute elements or values) is invoked at most once per index or key; even
though used from several threads.

### Memoized functions

So far, we have talked about the fundamental features of Stable Values & Collections as securely
wrapped `@Stable` value holders. However, it has become apparent, stable primitives are amenable
to composition with other constructs in order to create more high-level and powerful features.

[Memoized functions](https://en.wikipedia.org/wiki/Memoization) are functions where the output for a particular
input value is computed only once and is remembered such that remembered outputs can be reused for subsequent
calls with recurring input values. Here is how we could make sure `Logger.getLogger("com.foo.Bar")`
in one of the first examples above is invoked at most once (provided it executes successfully)
in a multi-threaded environment:

```
class Memoized {

    // 1. Declare a map with stable values
    private static final Map<String, StableValue<Logger>> MAP =
            StableValue.ofMap(Set.of("com.foo.Bar", "com.foo.Baz"));

    // 2. Declare a memoized (cached) function backed by the stable map
    private static final Function<String, Logger> LOGGERS =
            n -> StableValue.computeIfUnset(MAP, n, Logger::getLogger);

    ...

    private static final String NAME = "com.foo.Baz";

    // 3. Access the memoized value via the function with as-declared-final
    //    performance (evaluation made before the first access)
    Logger logger = LOGGERS.apply(NAME);
}
```

In the example above, for each key, the function is invoked at most once per
loading of the containing class `MapDemo` (`MapDemo`, in turn, can be loaded at
most once into any given `ClassLoader`) as it is backed by a `Map` with lazily
computed values which upholds the invoke-at-most-once-per-key invariant.

It should be noted that the enumerated collection of keys given at creation time
constitutes the only valid input keys for the memoized function.

Similarly to how a `Function` can be memoized using a backing lazily computed map, the same pattern
can be used for an `IntFunction` that will record its cached value in a backing _stable list_:

```
// 1. Declare a stable list of default error pages to serve up
private static final List<StableValue<String>> ERROR_PAGES =
        StableValue.ofList(SIZE);

// 2. Declare a memoized IntFunction backed by the stable list
private static final IntFunction<String> ERROR_FUNCTION =
        i -> StableValue.computeIfUnset(ERROR_PAGES, i, ListDemo::readFromFile);

// 3. Define a function that is to be called the first
//    time a particular message number is referenced
private static String readFromFile(int messageNumber) {
    try {
        return Files.readString(Path.of("message-" + messageNumber + ".html"));
    } catch (IOException e) {
        throw new UncheckedIOException(e);
    }
}

// 4. Access the memoized list element with as-declared-final performance
//    (evaluation made before the first access)
String msg =  ERROR_FUNCTION.apply(2);

// <!DOCTYPE html>
// <html lang="en">
//   <head><meta charset="utf-8"></head>
//   <body>Payment was denied: Insufficient funds.</body>
// </html>
```

The same paradigm can be used for creating a memoized `Supplier` (backed by a single `StableValue` instance) or
a memoized `Predicate`(backed by a lazily computed `Map<K, StableValue<Boolean>>`). An astute reader will be able to
write such constructs in a few lines.

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
