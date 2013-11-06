/*
 * @test /nodynamiccopyright/
 * @bug 8006733 8006775
 * @summary Ensure behavior for nested types is correct.
 * @author Werner Dietl
 * @compile CantAnnotateStaticClass.java
 */

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.lang.annotation.*;

class Top {
    @Target(ElementType.TYPE_USE)
    @interface TA {}

    @Target(ElementType.TYPE_USE)
    @interface TB {}

    @Target(ElementType.TYPE_USE)
    @interface TC {}

    class Outer {
        class Inner {
            Object o1 = Top.this;
            Object o2 = Outer.this;
            Object o3 = this;
        }
        // Illegal
        // static class SInner {}
        // interface IInner {}
    }

    // All combinations are OK

    Top.@TB Outer f1;
    @TB Outer.Inner f1a;
    Outer. @TC Inner f1b;
    @TB Outer. @TC Inner f1c;

    @TA Top. @TB Outer f2;
    @TA Top. @TB Outer.Inner f2a;
    @TA Top. Outer. @TC Inner f2b;
    @TA Top. @TB Outer. @TC Inner f2c;

    @TB Outer f1r() { return null; }
    @TB Outer.Inner f1ra() { return null; }
    Outer. @TC Inner f1rb() { return null; }
    @TB Outer. @TC Inner f1rc() { return null; }

    void f1param(@TB Outer p,
            @TB Outer.Inner p1,
            Outer. @TC Inner p2,
            @TB Outer. @TC Inner p3) { }

    void f1cast(Object o) {
        Object l;
        l = (@TB Outer) o;
        l = (@TB Outer.Inner) o;
        l = (Outer. @TC Inner) o;
        l = (@TB Outer. @TC Inner) o;
    }

    List<@TB Outer> g1;
    List<@TB Outer.Inner> g1a;
    List<Outer. @TC Inner> g1b;
    List<@TB Outer. @TC Inner> g1c;

    List<@TA Top. @TB Outer> g2;
    List<@TA Top. @TB Outer.Inner> g2a;
    List<@TA Top. Outer. @TC Inner> g2b;
    List<@TA Top. @TB Outer. @TC Inner> g2c;

    List<@TB Outer> g1r() { return null; }
    List<@TB Outer.Inner> g1ra() { return null; }
    List<Outer. @TC Inner> g1rb() { return null; }
    List<@TB Outer. @TC Inner> g1rc() { return null; }

    void g1param(List<@TB Outer> p,
            List<@TB Outer.Inner> p1,
            List<Outer. @TC Inner> p2,
            List<@TB Outer. @TC Inner> p3) { }

    void g1new(Object o) {
        Object l;
        l = new @TB ArrayList<@TB Outer>();
        l = new @TB ArrayList<@TB Outer.Inner>();
        l = new @TB HashMap<String, Outer. @TC Inner>();
        l = new @TB HashMap<String, @TB Outer. Inner>();
        l = new @TB HashMap<String, @TB Outer. @TC Inner>();
        l = new @TB HashMap<String, @TA Top. Outer. @TC Inner>();
        l = new @TB HashMap<String, @TA Top. @TB Outer. Inner>();
        l = new @TB HashMap<String, @TA Top. @TB Outer. @TC Inner>();
    }
}
