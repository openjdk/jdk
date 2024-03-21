## Summary

Introduce _Monotonic Values_, which are immutable value holders that are initialized at most once.
Monotonic values offer the performance and safety benefits of final fields, while offering greater
flexibility as to the timing of initialization. This is a [preview API](https://openjdk.org/jeps/12).

## Goals

- Decouple the initialization of monotonic values from the initialization of their containing class or object.
- Provide an easy and intuitive API for monotonic values and collections thereof.
- Enable [constant folding](https://en.wikipedia.org/wiki/Constant_folding) optimizations for monotonic values.
- Support dependencies between monotonic values.
- Reduce the amount of static initializer code and/or field initialization to be executed.
- Allow the initialization of values to be decoupled from one another (disentanglement of the soup of `<clinit>` dependencies).
- Uphold integrity and consistency, even in a multi-threaded environment.

## Non-goals

- It is not a goal to provide additional language support for expressing monotonic computation. 
  This might be the subject of a future JEP.
- It is not a goal to prevent or deprecate existing idioms for expressing lazy initialization.

## Motivation

Most Java developers have heard the advice "prefer immutability" (Effective
Java, Item 17). Immutability confers many advantages including:

* an immutable object can only be in one state
* the invariants of an immutable object can be enforced by its constructor
* immutable objects can be freely shared across threads
* immutability enables all manner of runtime optimizations.

Java's main tool for managing immutability  is `final` fields (and more recently, `record` classes).
Unfortunately, `final` fields come with restrictions. Final instance fields must be set by the end of
the constructor, and `static final` fields during class initialization. Moreover, the order in which `final`
field initializers are executed is determined by the [textual order](https://docs.oracle.com/javase/specs/jls/se7/html/jls-13.html#jls-12.4.1) 
and is then made explicit in the resulting class file. As such, the initialization of a `final`
field is fixed in time; it cannot be arbitrarily moved forward or backward. In other words, developers
cannot cause specific constants to be initialized before the class/object is initialized and cannot cause
constants to be initialized after the class or object is initialized.
This means that developers are forced to choose between finality and all its
benefits, and flexibility over the timing of initialization.  Developers have
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
whose computation needs to be shifted in time generally requires its own holder
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
A classic example of this is the [Fibonacci sequence](https://en.wikipedia.org/wiki/Fibonacci_sequence), where each element in the sequence 
is the sum of the two preceding elements. More formally:

```
fib(n) = fib(n-1) + fib(n-2) when n >= 2
fib(n) = n when 0 <= n < 2
```

When a computation depends on more sub-computations, it induces a *dependency graph*, where 
each computation is a node in the graph, and has zero or more edges to each of the 
sub-computation nodes it depends on. For instance, the dependency graph associated with
`fib(5)` is given below:

```
 
               ___________fib(5)___________
              /                            \
        ____fib(4)____                ____fib(3)____
       /              \              /              \
     fib(3)         fib(2)         fib(2)          fib(1)
    /      \       /      \       /      \
  fib(2)  fib(1) fib(1)  fib(0) fib(1)  fib(0)

```

This dependency graph has a number of interesting properties. Firstly, as `n` grows, it becomes
increasingly demanding to compute `fib(n)` on the fly - that is, the number of nodes in the graph
grows *exponentially* with `n`. Secondly, the dependency graph contains many repeated nodes. 
For instance, `fib(1)` occurs 4 times; `fib(2)` occurs 3 times, etc. Any repeated node in the 
dependency graph leads to some waste, as we are spending valuable CPU resources to compute a value
that has already been computed before.

Here is an example of how to implement a class holding values in the Fibonacci sequence using a `Map`:

```
static class Fibonacci {

    private final Map<Integer, Integer> map;

    public Fibonacci(int upperBound) {
        map = new HashMap<>(upperBound);
    }

    public int number(int n) {
        if (n < 2) {
            return n;
        }
        Integer v = map.get(n);
        if (v != null) {
            return v;
        }
        int n1 = number(n - 1);
        int n2 = number(n - 2);
        int sum = n1 + n2;
        map.put(n, sum);
        return sum;
    }

}
```

Clients can then use the `Fibonacci` class to obtain the value for a given `n < upperBound`:

```
Fibonacci fibonacci = new Fibonacci(20);

int[] fibs = IntStream.range(0, 10)
        .map(fibonacci::number)
        .toArray(); // { 0, 1, 1, 2, 3, 5, 8, 13, 21, 34 }
```

Unfortunately, this approach provides a number of challenges. First, retrieving the values
from a `Map` is slow, as said values cannot  be constant-folded. Even worse, using `Map` can
be error-prone, as dependencies between values in the Fibonacci sequence have to be managed manually.
For instance, when calling `fib(n)`, entries for all `fib(x)` (where x < n) must also be added to the map.
Furthermore, the class holder idiom (see above) is clearly insufficient in this case, as 
the number of required holder classes is *statically unbounded* - it depends on the value of 
the construction parameter `upperBound`.

What we are missing -- in all cases -- is a way to *promise* that a constant
will be initialized by the time it is used, with a value that is computed at
most once. Such a mechanism would give the Java runtime maximum opportunity to
stage and optimize its computation, thus avoiding the penalties (static
footprint, loss of runtime optimizations) that plague the workarounds shown
above. Moreover, such a mechanism should gracefully scale to handle collections of
constant values, while retaining efficient computer resource management



## Description

### Preview Feature

Monotonic Values is a [preview API](https://openjdk.org/jeps/12), disabled by default.
To use the Monotonic Value API, the JVM flag `--enable-preview` must be passed in, as follows:

- Compile the program with `javac --release 23 --enable-preview Main.java` and run it with `java --enable-preview Main`; or,

- When using the source code launcher, run the program with `java --source 23 --enable-preview Main.java`; or,

- When using `jshell`, start it with `jshell --enable-preview`.

### Outline

The [Monotonic Value API](https://cr.openjdk.org/~pminborg/computed-constant/api/java.base/java/lang/ComputedConstant.html) defines classes and an interface so that client code in libraries and applications can

- Define and use monotonic objects: `Monotonic`

- Define and use monotonic collections: `List<Monotonic<V>>` and `Map<K, Monotonic<V>>` 

The [Monotonic Value API](https://cr.openjdk.org/~pminborg/computed-constant/api/java.base/java/lang/ComputedConstant.html) resides in the [`java.lang`] package of the [`java.base`] module.

### Monotonic Value

A _monotonic value_ is a holder object that is bound at most once whereby it
goes from "absent" to "present". It is expressed as an object of type `Monotonic`,
which, like `Future`, is  a holder for some computation that may or may not have occurred yet.
Fresh (absent) `Monotonic` instances are created via the factory method `Monotonic::of`:

```
class Bar {
    // 1. Declare a monotonic value
    private static final Monotonic<Logger> LOGGER = Monotonic.of();
    
    static void init() {
        // 2. Bind the monotonic value _after_ being declared
        LOGGER.bind(Logger.getLogger("com.foo.Bar"));
    }
    
    static Logger logger() {
        // 3. Access the monotonic value with as-declared-final performance
        return LOGGER.get();
    }
}
```

This is similar in spirit to the holder-class idiom, and offers the same
performance, constant-folding, and thread-safety characteristics, but is simpler
and incurs a lower static footprint since no additional class is required.

In case a monotonic value cannot be pre-bound as in the example above, it is possible
to compute and bind an absent value on-demand as shown in this example:

```
class Bar {
    // 1. Declare a monotonic value
    private static final Monotonic<Logger> LOGGER = Monotonic.of();
    
    static Logger logger() {
        // 2. Access the monotonic value with as-declared-final performance
        //    (evaluation made before the first access)
        return LOGGER.computeIfAbsent( () -> Logger.getLogger("com.foo.Bar") );
    }
}
```
Calling `logger()` multiple times yields the same value from each invocation.
`Monotonic::computeIfAbsent` can invoke the value supplier several times if called
from a plurality  of threads but only one witness value is ever exposed to the 
outside world. 

To also guarantee the value supplier is invoked *at most once*,
even though invoked by several threads, there is a convenience method, located 
in the utility class `Monotonics`, providing precisely that:

```
class Bar {
    // 1. Declare a memoized (cached) Supplier (backed by an 
    //    internal monotonic value) that is invoked at most once
    private static final Supplier<Logger> LOGGER = Monotonics.asMemoized(
                    () -> Logger.getLogger("com.foo.Bar"));

    static Logger logger() {
        // 2. Access the memoized value with as-declared-final performance
        //    (evaluation made before the first access)
        return LOGGER.get();
    }
}
```

In the example above, the supplier is invoked at most once per
loading of the containing class `Bar` (`Bar`, in turn, can be loaded at
most once into any given `ClassLoader`). 

A value supplier may return `null` which will be considered the bound value.
Null-averse applications can also use `Monotonic<Optional<V>>`.

### Monotonic Collections

While initializing a single field of type `Monotonic` is cheap (remember, creating a new `Monotonic` 
object only creates the *holder* for the value), this (small) initialization cost has 
to be paid for each field of type `Monotonic` declared by the class. As a result, the class static 
and/or instance initializer will keep growing with the number of `Monotonic` fields, thus degrading performance.

To handle these cases, the Monotonic Value API provides constructs that allow the creation and handling of a
*`List` of `Monotonic` elements*. Such a `List` is a list whose elements are created lazily on demand
before a particular element is first accessed. Lists of monotonic values are objects of type `List<Monotonic>`.
Consequently, each element in the list enjoys the same properties as a `Monotonic` but may require less resources.

Like a `Monotonic` object, a `List<Monotonic>` object is created via a factory method by providing a size
of the desired List:

```
static <V> List<Monotonic<V>> ofList(int size) { ... }
```

This allows for improving the handling of lists with monotonic values and enables a much better
implementation of the `Fibonacci` class mentioned earlier:

```
class Fibonacci {

    private final List<Monotonic<Integer>> numberCache;

    public Fibonacci(int upperBound) {
        numberCache = Monotonic.ofList(upperBound);
    }

    public int number(int n) {
        return (n < 2)
                ? n
                : Monotonics.computeIfAbsent(numberCache, n -1 , this::number)
                + Monotonics.computeIfAbsent(numberCache, n - 2, this::number);
    }

}
```

Just like before, we can perform retrieval of values like this:

```
Fibonacci fibonacci = new Fibonacci(20);

int[] fibs = IntStream.range(0, 10)
        .map(fibonacci::number)
        .toArray(); // { 0, 1, 1, 2, 3, 5, 8, 13, 21, 34 }
```

Note how there's only field of type `List<Monotonic<Integer>>` to initialize - every other computation is
performed before the corresponding element of the list is accessed. Note also how the value of an element in the
list, stored in an instance field, depends on the value of other (lower-index) element values. The Monotonic Value API
allows modeling this cleanly, while still preserving good constant-folding guarantees and integrity of updates in
the case of multi-threaded access.

Similarly to how a `Supplier` can be memoized using a backing `Monotonic`, the same pattern can be used 
for an `IntFunction` that will record its cached value in a backing _list of `Monotonic` elements_:

```
class Fibonacci {

    private final IntFunction<Integer> numCache;

    public Fibonacci3(int upperBound) {
        numCache = Monotonics.asMemoized(upperBound, this::number);
    }

    public int number(int n) {
        return (n < 2)
                ? n
                : numCache.apply(n - 1) + numCache.apply(n - 2);
    }

}
```

Finally, a `Map` of `Monotonic` values can also be defined and used similar to how a 
`List` of `Monotonic` elements are handled. In the example below, we cache values for 
an enumerated collection of keys:

```
class MapDemo {

    private static final Map<String, Monotonic<Logger>> LOGGERS =
            Monotonic.ofMap(Set.of("com.foo.Bar", "com.foo.Baz"));

    static Logger logger(String name) {
        return Monotonics.computeIfAbsent(LOGGERS, name, Logger::getLogger);
    }
}
```

Correspondingly to memoized suppliers and int-functions, a general `Function` can also be memoized
via a backing `Map` of `Monotonic` values, thereby ensuring the resulting value for each input 
parameter is computed as most once even in a multi-threaded environment.
Here is an example of how such a memoized function can be used:

```
class MapDemo2 {

    // 1. Declare a memoized (cached) function backed by a monotonic map
    private static final Function<String, Logger> LOGGERS =
            Monotonics.asMemoized(
                    Set.of("com.foo.Bar", "com.foo.Baz"),
                    Logger::getLogger);

    static Logger logger(String name) {
        // 2. Access the memoized value with as-declared-final performance
        //    (evaluation made before the first access)
        return LOGGERS.apply(name);
    }
}
```
It should be noted that only the enumerated collection of keys given at creation time
constitutes valid inputs to the memoized function.

### Safety

Binding a monotonic value is an atomic, non-blocking operation, e.g. calling `Monotonic::computeIfAbsent`
or `Monotonic::bind`, either results in successfully initializing the monotonic to a value, or fails
with an exception. This is true regardless of whether the monotonic value is accessed by a single
thread, or concurrently, by multiple threads. 

The attentive reader might have noticed the similarities between `Monotonic` and the `@Stable` annotation.
This annotation is used in JDK internal code to mark scalar and array variables whose values or elements will
change *at most once*. This annotation is powerful and often crucial to achieving optimal internal performance,
but it is also easy to misuse: further updating a `@Stable` field after its initial update will result in
undefined behavior, as the JIT compiler might have *already* constant-folded the now overwritten
field value. `Monotonic` solves this issue by effectively providing a *safe* and *efficient* wrapper
around the `@Stable` annotation.

### Performance

Constant folded `Monotonic` values have the same retrieval performance as values managed via the class-holder-idiom.
Monotonic reference values are faster to obtain than reference values managed via double-checked-idiom constructs
as monotonic values relies on explicit memory barriers rather than performing volatile access on each get operation.

## Alternatives

There are other classes in the JDK that support lazy computation including `Map`, `AtomicReference`, `ClassValue`,
and `ThreadLocal` which are similar in the sense that they support arbitrary mutation and thus, prevent the JVM
from reasoning about constantness and do not allow shifting computation _before_ being used.

So, alternatives would be to keep using explicit double-checked locking, maps, holder classes, Atomic classes, 
and third-party frameworks.  Another alternative would be to add language support for immutable value holders.

## Risks and Assumptions

Creating an API to provide thread-safe computed constant fields with an on-par performance with holder
classes efficiently is a non-trivial task. It is, however, assumed that the current JIT implementations
will likely suffice to reach the goals of this JEP.

## Dependencies

The work described here will likely enable subsequent work to provide pre-evaluated computed
constant fields at compile, condensation, and/or runtime.

[`java.lang`]: https://cr.openjdk.org/~pminborg/computed-constant/api/java.base/java/lang/package-summary.html
[`java.base`]: https://cr.openjdk.org/~pminborg/computed-constant/api/java.base/module-summary.html