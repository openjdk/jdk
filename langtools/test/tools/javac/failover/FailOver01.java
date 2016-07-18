/*
  * @test /nodynamiccopyright/
 * @bug 6970584
 * @summary Flow.java should be more error-friendly
 * @author mcimadamore
 *
 * @compile/fail/ref=FailOver01.out -XDrawDiagnostics -Xshouldstop:at=FLOW -XDdev FailOver01.java
 */

class Test { { x = "" } }
