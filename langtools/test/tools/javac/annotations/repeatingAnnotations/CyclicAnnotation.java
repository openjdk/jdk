/**
 * @test    /nodynamiccopyright/
 * @bug     7169362
 * @author  sogoel
 * @summary Cyclic annotation not allowed
 * @compile/fail/ref=CyclicAnnotation.out -XDrawDiagnostics CyclicAnnotation.java
 */

import java.lang.annotation.ContainedBy;
import java.lang.annotation.ContainerFor;

@ContainedBy(Foo.class)
@ContainerFor(Baz.class)
@interface Baz {
    Foo[] value() default {};
}

@ContainedBy(Baz.class)
@ContainerFor(Foo.class)
@interface Foo{
    Baz[] value() default {};
}

@Foo(value = {@Baz,@Baz})
@Baz(value = {@Foo,@Foo})
public class CyclicAnnotation {}
