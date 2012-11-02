/**
 * @test    /nodynamiccopyright/
 * @bug     7169362
 * @author  sogoel
 * @summary ContainerAnnotation does not have FooContainer.class specified
 * @compile/fail/ref=MissingContainer.out -XDrawDiagnostics MissingContainer.java
 */

import java.lang.annotation.ContainedBy;
import java.lang.annotation.ContainerFor;

@ContainedBy()
@interface Foo {}

@ContainerFor(Foo.class)
@interface FooContainer {
    Foo[] value();
}

@Foo @Foo
public class MissingContainer {}
