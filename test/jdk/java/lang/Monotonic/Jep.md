# Lazy Values & Collections (Preview)

## Summary

Introduce a _Lazy Values & Collections_ API, which provides immutable value holders where elements are initialized _at most once_.
Lazy Values & Collections offer the performance and safety benefits of final fields, while offering greater
flexibility as to the timing of initialization. This is a [preview API](https://openjdk.org/jeps/12).

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
devised a number of strategies to ameliorate this imbalance, but none are
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
LOGGER.log(...);
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
logger().log(...);
```
The code above ensures that the `Logger` object is created only when actually
required.  The (possibly expensive) initializer for the logger lives in the
nested `Holder` class, which will only be initialized when the `logger` method
accesses the `LOGGER` field.  While this idiom works well, its reliance on the
class loading process comes with significant drawbacks.  First, each constant
whose computation needs to be deferred generally requires its own holder
class, thus introducing a significant static footprint cost.  Second, this idiom
is only really applicable if the field initialization is suitably isolated, not
relying on any other parts of the object state.

Alternatively, the [_double-checked locking idiom_](https://en.wikipedia.org/wiki/Double-checked_locking), can also be used
for deferring evaluation of field initializers. The idea is to optimistically
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

Furthermore, the idiom does not work for `null` values.

The situation is even worse when clients need to operate on a _collection_ of immutable values.

An example of this is a `List` that holds HTML pages that corresponds to an error code in the range [0, 7]
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

Unfortunately, this approach provides a number of challenges. First, retrieving the values
from an array is slow, as said values cannot be [constant-folded](https://en.wikipedia.org/wiki/Constant_folding). Even worse, access to
the array is guarded by synchronization that is slow and will block access to the array for
all elements whenever one of the elements is under computation. Furthermore, the class holder idiom (see above)
is clearly insufficient in this case, as the number of required holder classes is *statically unbounded* - it 
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
wrapper around the `@Stable` mechanism - in the form of a new Java SE API which might be enjoyed by
_all_ client and 3rd party Java code (and not the JDK alone).

## Description

### Preview feature

Lazy Values & Collections is a [preview API](https://openjdk.org/jeps/12), disabled by default.
To use the Lazy Value and Collections APIs, the JVM flag `--enable-preview` must be passed in, as follows:

- Compile the program with `javac --release 23 --enable-preview Main.java` and run it with `java --enable-preview Main`; or,

- When using the source code launcher, run the program with `java --source 23 --enable-preview Main.java`; or,

- When using `jshell`, start it with `jshell --enable-preview`.

### Outline

The Values & Collections API define functions and an interface so that client code in libraries and applications can

- Define and use lazy (scalar) values: [`Lazy`](https://cr.openjdk.org/~pminborg/lazy/api/java.base/java/lang/Monotonic.html)
- Define and use lazy collections: 
  [`List.ofLazy`](https://cr.openjdk.org/~pminborg/lazy/api/java.base/java/util/List.html#ofLazy(int,java.util.function.IntFunction)), 
  [`Set.ofLazy(Set, Predicate)`](https://cr.openjdk.org/~pminborg/lazy/api/java.base/java/util/Set.html#ofLazy(java.util.Set,java.util.function.Predicate)), [`Set.ofLazy(Enum.class, Predicate`)](https://cr.openjdk.org/~pminborg/lazy/api/java.base/java/util/Set.html#ofLazy(java.lang.Class,java.util.function.Predicate)) and 
  [`Map.ofLazy(Set, Function)`](https://cr.openjdk.org/~pminborg/lazy/api/java.base/java/util/Map.html#ofLazy(java.util.Set,java.util.function.Function)), [`Map.ofLazy(Enum.class, Function)`](https://cr.openjdk.org/~pminborg/lazy/api/java.base/java/util/Map.html#ofLazy(java.lang.Class,java.util.function.Function))

The Lazy Values & Collections API resides in the `java.lang` and `java.util` packages of the `java.base` module.

### Lazy values

A _lazy value_ is a holder object that is bound at most once whereby it
goes from "unbound" to "bound". It is expressed as an object of type `Lazy`,
which, like `Future`, is a holder for some computation that may or may not have occurred yet.
Fresh (unbound) `Lazy` instances are created via the factory method `Lazy::of`:

```
class Bar {
    // 1. Declare a Lazy
    private static final Lazy<Logger> LOGGER = Lazy.of();
    
    static void init() {
        // 2. Bind the lazy value _after_ being declared
        LOGGER.bindOrThrow(Logger.getLogger("com.foo.Bar"));
    }
    
    static Logger logger() {
        // 3. Access the lazy value with as-declared-final performance
        return LOGGER.orThrow();
    }
}
```

This is similar to the holder-class idiom in the sense it offers the same
performance, constant-folding, and thread-safety characteristics, but is simpler
and incurs a lower static footprint since no additional class is required.

Binding a lazy value is an atomic, thread-safe, non-blocking operation, e.g. `Lazy::bindOrThrow`,
either results in successfully initializing the `Lazy` to a value, or fails
with an exception. This is true regardless of whether the lazy value is accessed by a single
thread, or concurrently, by multiple threads.

A lazy value may be bound to `null` which then will be considered its bound value.
Null-averse applications can also use `Lazy<Optional<V>>`.

In case a lazy value cannot be pre-bound as in the example above, it is possible
to compute and bind an unbound value on-demand as shown in this example:

```
class Bar {
    // 1. Declare a lazy value
    private static final Lazy<Logger> LOGGER = Lazy.of();
    
    static Logger logger() {
        // 2. Access the lazy value with as-declared-final performance
        //    (evaluation made before the first access)
        return LOGGER.computeIfUnbound( () -> Logger.getLogger("com.foo.Bar") );
    }
}
```
Calling `logger()` multiple times yields the same value from each invocation.
Even though `Lazy::computeIfUnbound` might invoke the value supplier several times if called
from a plurality of threads at about the same time, only one distinct witness value is 
ever exposed to the outside world. 

Lazy reference values are faster to retrieve than reference values managed via
double-checked-idiom constructs as lazy values rely on explicit memory barriers
rather than performing volatile access on each retrieval operation and in addition, they are eligible
for constant folding optimizations.

### Lazy collections

While initializing a single field of type `Lazy` is cheap (remember, creating a new `Lazy`
object only creates the *holder* for the value), this (small) initialization cost has
to be paid for each field of type `Lazy` declared by the class. As a result, the class static
and/or instance initializer will keep growing with the number of `Lazy` fields, thus degrading performance.

To handle these cases, the Lazy Values & Collections API provides constructs that allow the creation and handling of a
*`List` of lazily computed elements*. Such a `List` is a list whose elements are created lazily on demand
before a particular element is first accessed. Lists of lazily computed values are objects of type `List<E>`.
Consequently, each element in the list enjoys the same properties as a `Lazy` but may require less resources.

Like a `Lazy` object, a lazily computed `List` object is created via a factory method by providing the size
of the desired `List` and an `IntFunction` to be used to lazily compute its elements:

```
static <E> List<E> List.ofLazy(int size, IntFunction<? extends E> mapper) { ... }
```

This allows for improving the handling of lists with lazily computed values and enables a much better
implementation of the `ErrorMessages` class mentioned earlier. Here is a new version
of the class which is now using the newly proposed API:

```
class ErrorMessages {

    private static final int SIZE = 8;

    // 1. Declare a lazy list of default error pages to serve up
    private static final List<String> MESSAGES = 
            List.ofLazy(SIZE, ErrorMessages::readFromFile);

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
        // 3. Access the memoized list element with as-declared-final performance
        //    (evaluation made before the first access)
        return MESSAGES.get(messageNumber);
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

Note how there's only one field of type `List<String>` to initialize even though every computation is
performed independently of the other element of the list when accessed (i.e. no blocking will occur across threads 
computing distinct elements simultaneously). The Lazy Values & Collections API allows modeling this cleanly, while
still preserving good constant-folding guarantees and integrity of updates in the case of multi-threaded access.

It should be noted that even though a lazily computed list might mutate its internal state upon external access, it 
is _still immutable_ because _no change can ever be observed by an external entity_. This is similar to other
immutable classes, such as `String` (which internally caches its `hash` value), where they might rely on mutable
internal states that are carefully kept internal and that never shine through to the outside world.

Just as a `List` can be lazily computed, a `Map` of lazily computed values can also be defined and used similarly.
In the example below, we lazily compute a map's values for an enumerated collection of pre-defined keys:

```
class MapDemo {

    static final Map<String, Logger> LOGGERS =
            Map.ofLazy(Set.of("com.foo.Bar", "com.foo.Baz"), Logger::getLogger);

    static Logger logger(String name) {
        return LOGGERS.get(name);
    }
}
```

Finally, a `Set` of lazily computed elements can be defined and used. In the example below, the well known problem of
efficiently determining and acting on if a logger will actually output something for a certain level is solved using
a lazily computed `Set`. This allows constant folding of the code path and might even enable the JVM to totally eliminate
unused code paths depending on dynamic logger properties determined when first accessed:

```
 class SetDemo {
 
    static final Set<String> INFO_LOGGABLE =
            Set.ofLazy(Set.of("com.foo.Bar", "com.foo.Baz"),
                    name -> MapDemo.logger(name).isLoggable(Level.INFO));
                    
    static boolean isInfoLoggable(String name) {
        return INFO_LOGGABLE.contains(name);
    }
    
    private static final String NAME = "com.foo.Bar";
    
    public void servlet() {
        if (INFO_LOGGABLE.contains(NAME)) {
            MapDemo.LOGGERS.get(NAME).log(Level.INFO, "This is fast...");
        }
    }
}
```
This last example also demonstrates how lazy constructs can be composed into more high-level, high-performance
concepts that can leverage constant folding and other JVM optimizations transitively. 

It is worth mentioning, the lazy collections all promises the provided function used to lazily compute
elements or values are invoked at-most-once even though used from several threads. This is an additional promise
compared to scalar `Lazy` values.

### Memoized functions

So far, we have talked about the fundamental features of Lazy Values & Collections as securely
wrapped `@Stable` value holders. However, as briefly shown above, it has become apparent, lazy primitives are amenable
to composition with other constructs in order to create more high-level and powerful features.

[Memoized functions](https://en.wikipedia.org/wiki/Memoization) are functions where the output for a particular 
input value is computed only once and are remembered such that remembered outputs can can be reused for subsequent
calls with recurring input values. Here is how we could make sure `Logger.getLogger("com.foo.Bar")`
in one of the first examples above is invoked at most once (provided it executes successfully)
in a multi-threaded environment:

```
class Memoized {

    // 1. Declare a memoized (cached) function backed by a lazily computed map
    private static final Function<String, Logger> LOGGERS =
            Map.ofLazy(Set.of("com.foo.Bar", "com.foo.Baz"), Logger::getLogger)::get;

    static Logger logger(String name) {
        // 2. Access the memoized value with as-declared-final performance
        //    (evaluation made before the first access)
        return LOGGERS.apply(name);
    }
}
```

In the example above, for each key, the function is invoked at most once per
loading of the containing class `MapDemo` (`MapDemo`, in turn, can be loaded at
most once into any given `ClassLoader`) as it is backed by a `Map` with lazily
computed values which upholds the invoke-at-most-once-per-key invariant.

It should be noted that the enumerated collection of keys given at creation time
constitutes the only valid inputs for the memoized function.

Similarly to how a `Funcion` can be memoized using a backing lazily computed map, the same pattern can be used
for an `IntFunction` that will record its cached value in a backing _lazy list_:

```
// 1. Declare a memoized (cached) IntFunction backed by a lazily computed list
static final IntFunction<String> ERROR_PAGES = 
        List.ofLazy(MAX_ERROR_CODE, ListDemo::readFromFile)::get;
        
...

// 2. Access the memoized value with as-declared-final performance
//    (evaluation made before the first access)
String errorPage = ERROR_PAGES.apply(2);
       
// <!DOCTYPE html>
// <html lang="en">
//   <head><meta charset="utf-8"></head>
//   <body>Payment was denied: Insufficient funds.</body>
// </html>
```

The same pattern can be used for creating a memoized `Predicate` (backed by a lazily computed `Set`).  The solution
for this is left for the reader as an exercise. 

As `Lazy::computeIfUnbound` can invoke a provided supplier several times if invoked from several threads, there is a
convenience method `Lazy::asSupplier` that provides an out-of-the-box memoized supplier that upholds the invoke-at-most
property also in multi-threaded environments.

## Alternatives

There are other classes in the JDK that support lazy computation including `Map`, `AtomicReference`, `ClassValue`,
and `ThreadLocal` which are similar in the sense that they support arbitrary mutation and thus, prevent the JVM
from reasoning about constantness and do not allow shifting computation _before_ being used.

So, alternatives would be to keep using explicit double-checked locking, maps, holder classes, Atomic classes,
and third-party frameworks.  Another alternative would be to add language support for immutable value holders.

## Risks and assumptions

Creating an API to provide thread-safe computed constant fields with an on-par performance with holder
classes efficiently is a non-trivial task. It is, however, assumed that the current JIT implementations
will likely suffice to reach the goals of this JEP.

## Dependencies

The work described here will likely enable subsequent work to provide pre-evaluated computed
constant fields at compile, condensation, and/or runtime.
