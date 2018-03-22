/*
 * @test /nodynamiccopyright/
 * @bug 8198552
 * @summary Check that -Xdoclint: option can be specified multiple times
 * @compile/fail/ref=MultipleDocLintOptionsTest.out -Xdoclint:html -Xdoclint:syntax -XDrawDiagnostics MultipleDocLintOptionsTest.java
 */

/** <html> */
public class MultipleDocLintOptionsTest {
    /** @return */
    int emptyReturn() { return -1; }
}
