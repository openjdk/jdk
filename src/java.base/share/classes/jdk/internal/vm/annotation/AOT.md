AOTClassInitializer
------

Normally, static initializers, if not triggered by an AOT cache, are
executed in a demand-driven order specified by the JVMS and JLS.

The implementation of an AOT cache uses restricted execution of the
JVM during an "assembly phase", which executes certain class
initializers and bootstrap methods in a separate JVM, and then records
associated memory images of the results into the AOT cache.

When a production run uses that AOT cache, it starts up in a state
which looks exactly as if the execution of that assembly phase was the
earliest part of the production run.  Thus, the application observes
the effect of classes having been loaded, linked, and even
initialized, as if the JVM did that work at the exact moment
requested, though unaccountably fast.

This optimization only works if every operation in the assembly phase
is "pure" enough to execute in a separate JVM.  For example, such an
operation cannot be allowed to observe the process ID or a wall clock.
It must not write the file system, and may only read parts which (like
the classpath) we can prove will be the same for both JVM runs.  If a
special value like random seed is picked, it must be a value that will
not need to be picked again in the production run.

Controlling the "purity" of all of these operations mandates that we
place very tight restrictions on what classes may be initialized
during AOT assembly.

When a static initializer is executed during AOT assembly, the value
of each static field is recorded in the AOT cache.  When the JVM
starts its production run, using that same AOT cache, the JVM
immediately marks the class as initialized, and uses all static field
values recorded in the cache.

AOT-cached static field values can refer to objects in the Java heap.
Also, AOT-resolved constant pool entries (like lambda creation sites)
can refer to heap objects as well.  Such heap objects are captured in
the AOT cache, along with all supporting metadata that may be
necessary.

When an object exists in the AOT cache, it must be the case that the
class of each such cached heap object is also initialized.  This is
because the result of loading an AOT cache must look like a valid,
standard-conformant execution of the JVM.  But a heap object with an
uninitialized class is an impossibility within the JVMS.

> This is not true in the current implementation - a "special object
> graph" is tracked; currently we only ensure initialization is complete
> before objects are returned. This double-initialization is risky but
> apparently worked for simple cases before JEP 483. `heapShared.hpp`
> has more details about this graph.

For such reasons, one class often has an "initialization dependency"
on another class.  Some class A cannot complete its initialization
until some other class B has at least started its initialization.  For
example, initialization of `PrimitiveClassDescImpl` is triggered by
initialization of `ConstantDescs`.

This is sometimes called an "init-dependency of A on B".  Note that we
must assert that any initialization that is _started_ in the AOT cache
is also _finished_ before the AOT cache is fully generated.  This is
true because initializer threads cannot be stored in the AOT cache,
only the data images created by such threads.

Therefore, if a class A is marked `AOTClassInitializer`, and it has
an init-dependency on some class B, that class B must also be marked
as `AOTClassInitializer`, if B in fact has a `<clinit>` method.

Some dependencies are not explicitly expressed as field values, such
as `MemberName` references to methods in holder classes like
`DirectMethodHandle$Holder`, which requires the target class to be
AOT-initialized despite having no static methods.

A class which is so simple that it has no `<clinit>` does not need to
be marked by this annotation, as long as all its super-classes, and
initialized super-interfaces, are also equally simple.  The AOT
assembly phase will pre-initialize it if necessary.  Every class which
has complex initialization, and either has instances in the AOT heap,
or certain kinds of method handle references in the AOT heap, must be
marked this way.

During the AOT assembly phase, all of these dependencies are carefully
checked by a JVM class called `CDSHeapVerifier`.  See
`aotConstantPoolResolver.cpp` for more details.

Note that many classes do not need marking with this annotation simply
because they are not used at AOT time, or because they are used as
pure types, without the need for initialization.
