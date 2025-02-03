/*
 * @test /nodynamiccopyright/
 * @bug 8026564 8043226 8334055
 * @summary The parts of a fully-qualified type can't be annotated.
 * @author Werner Dietl
 * @compile/fail/ref=CantAnnotatePackages.out -XDrawDiagnostics CantAnnotatePackages.java
 */

import java.lang.annotation.*;
import java.util.List;

class CantAnnotatePackages {
    // Before a package component:
    @TA java.lang.Object of1;

    List<@TA java.lang.Object> of2;
    java. @TA lang.Object of3;
    List<java. @TA lang.Object> of4;
}

@Target(ElementType.TYPE_USE)
@interface TA { }
