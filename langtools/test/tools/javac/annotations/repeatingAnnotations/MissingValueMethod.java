/**
 * @test    /nodynamiccopyright/
 * @bug     7169362
 * @author   sogoel
 * @summary Missing value() method in ContainerAnnotation
 * @compile/fail/ref=MissingValueMethod.out -XDrawDiagnostics MissingValueMethod.java
 */

import java.lang.annotation.ContainedBy;
import java.lang.annotation.ContainerFor;

@ContainedBy(FooContainer.class)
@interface Foo {}

@ContainerFor(Foo.class)
@interface FooContainer{
    Foo[] values();  // wrong method name
}

@Foo @Foo
public class MissingValueMethod {}

