/**
 * @test    /nodynamiccopyright/
 * @bug     7169362
 * @author  sogoel
 * @summary Default case not specified for other methods in container annotation
 * @compile/fail/ref=MissingDefaultCase1.out -XDrawDiagnostics MissingDefaultCase1.java
 */

import java.lang.annotation.ContainedBy;
import java.lang.annotation.ContainerFor;

@ContainedBy(FooContainer.class)
@interface Foo {}

@ContainerFor(Foo.class)
@interface FooContainer {
    Foo[] value();
    String other();  // missing default clause
}

@Foo @Foo
public class MissingDefaultCase1 {}

