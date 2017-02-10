/*
 * @test
 * @bug 8173609
 * @summary printing of modules
 * @compile/ref=module-info.out -Xprint p/P.java module-info.java
 */

/**
 * Printing of modules
 */
@Deprecated
module printing {
    requires static transitive java.base;
    exports p to m.m1, m.m2;
    opens p to m.m1, m.m2;
    uses p.P;
    provides p.P with p.P.P1, p.P.P2;
}
