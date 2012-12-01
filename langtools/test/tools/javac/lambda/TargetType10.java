/*
 * @test /nodynamiccopyright/
 * @bug 8003280
 * @summary Add lambda tests
 *  check that wildcards in the target method of a lambda conversion is handled correctly
 * @author  Maurizio Cimadamore
 * @compile/fail/ref=TargetType10.out -XDrawDiagnostics TargetType10.java
 */

class TargetType10 {
    interface Function<A,R> {
        R apply(A a);
    }

    static class Test {
        <A,B,C> Function<A,C> compose(Function<B,C> g, Function<A,? extends B> f) { return null; }
        { compose(x ->  "a" + x, x -> x + "b"); }
    }
}
