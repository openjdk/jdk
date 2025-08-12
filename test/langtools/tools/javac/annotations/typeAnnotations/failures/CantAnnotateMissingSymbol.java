/*
 * @test /nodynamiccopyright/
 * @bug 8026564 8043226
 * @summary 8334055
 * @compile/fail/ref=CantAnnotateMissingSymbol.out -XDrawDiagnostics CantAnnotateMissingSymbol.java
 */

import java.lang.annotation.*;
import java.util.List;

class CantAnnotateMissingSymbol {
    List<@TA NoSuch> x;
}

@Target(ElementType.TYPE_USE)
@interface TA { }
