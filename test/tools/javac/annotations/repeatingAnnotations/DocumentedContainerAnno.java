/**
 * @test    /nodynamiccopyright/
 * @bug     7169362
 * @author  sogoel
 * @summary Base anno is Documented but Container anno is not
 * @compile/fail/ref=DocumentedContainerAnno.out -XDrawDiagnostics DocumentedContainerAnno.java
 */

import java.lang.annotation.ContainedBy;
import java.lang.annotation.ContainerFor;
import java.lang.annotation.Documented;

@Documented
@ContainedBy(FooContainer.class)
@interface Foo {}

@ContainerFor(Foo.class)
@interface FooContainer{
    Foo[] value();
}

@Foo @Foo
public class DocumentedContainerAnno {}
