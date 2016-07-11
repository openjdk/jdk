/*
 * @test /nodynamiccopyright/
 * @bug 6970584
 * @summary Flow.java should be more error-friendly
 * @author mcimadamore
 *
 * @compile/fail/ref=FailOver06.out -XDrawDiagnostics -Xshouldstop:at=FLOW -XDdev FailOver06.java
 */

class Test extends Test {
    Inference x = 1;
    { if (x == 1) { } else { } }
}
