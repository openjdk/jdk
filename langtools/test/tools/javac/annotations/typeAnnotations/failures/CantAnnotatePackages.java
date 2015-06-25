/*
 * @test /nodynamiccopyright/
 * @bug 8026564 8074346
 * @summary The parts of a fully-qualified type can't be annotated.
 * @author Werner Dietl
 * @compile/fail/ref=CantAnnotatePackages.out -XDrawDiagnostics CantAnnotatePackages.java
 */


import java.lang.annotation.*;
import java.util.List;

class CantAnnotatePackages {
    // Before a package component:
    @TA java.lang.Object of1;

    // These result in a different error.
    // TODO: should this be unified?

    List<@TA java.lang.Object> of2;
    java. @TA lang.Object of3;
    List<java. @TA lang.Object> of4;

    List<@CantAnnotatePackages_TB java.lang.Object> of5; // test that we do reasonable things for missing types.

    // TODO: also note the order of error messages.
}

@Target(ElementType.TYPE_USE)
@interface TA { }
