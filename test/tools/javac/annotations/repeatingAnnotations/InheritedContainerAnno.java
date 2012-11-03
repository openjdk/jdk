/**
 * @test    /nodynamiccopyright/
 * @bug     7169362
 * @author   sogoel
 * @summary Base anno is Inherited but Container anno is not
 * @compile/fail/ref=InheritedContainerAnno.out -XDrawDiagnostics InheritedContainerAnno.java
 */

import java.lang.annotation.ContainedBy;
import java.lang.annotation.ContainerFor;
import java.lang.annotation.Inherited;

@Inherited
@ContainedBy(FooContainer.class)
@interface Foo {}

@ContainerFor(Foo.class)
@interface FooContainer{
    Foo[] value();
}

@Foo @Foo
public class InheritedContainerAnno {}

