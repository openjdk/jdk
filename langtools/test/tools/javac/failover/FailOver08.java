/*
 * @test /nodynamiccopyright/
 * @bug 6970584
 * @summary Flow.java should be more error-friendly
 * @author mcimadamore
 *
 * @compile/fail/ref=FailOver08.out -XDrawDiagnostics -XDshouldStopPolicy=FLOW -XDdev FailOver08.java
 */

class Test extends Test {
    Integer x = 1;
    { while (x) {}; }
}
