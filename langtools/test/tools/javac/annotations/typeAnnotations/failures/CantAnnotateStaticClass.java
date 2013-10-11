/*
 * @test /nodynamiccopyright/
 * @bug 8006733 8006775

 * @summary A static outer class cannot be annotated.
 * @author Werner Dietl
 * @compile/fail/ref=CantAnnotateStaticClass.out -XDrawDiagnostics CantAnnotateStaticClass.java
 */

import java.util.List;
import java.lang.annotation.*;

class CantAnnotateStaticClass {
    @Target(ElementType.TYPE_USE)
    @interface A {}

    static class Outer {
        class Inner {}
    }

    // 8 errors:
    @A Outer.Inner f1;
    @A Outer.Inner f1r() { return null; }
    void f1p(@A Outer.Inner p) { }
    void f1c(Object o) {
        Object l = (@A Outer.Inner) o;
    }

    List<@A Outer.Inner> f2;
    List<@A Outer.Inner> f2r() { return null; }
    void f2p(List<@A Outer.Inner> p) { }
    void f2c(Object o) {
        Object l = (List<@A Outer.Inner>) o;
    }

    // OK:
    @A Outer g1;
    List<@A Outer> g2;
    Outer. @A Inner g3;
    List<Outer. @A Inner> g4;
}
