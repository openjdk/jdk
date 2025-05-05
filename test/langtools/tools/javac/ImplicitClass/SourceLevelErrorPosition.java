/**
 * @test /nodynamiccopyright/
 * @bug 8310314 8344706
 * @summary Ensure proper error position for the "implicit classes not supported" error
 * @compile/fail/ref=SourceLevelErrorPosition.out --release 24 -XDrawDiagnostics SourceLevelErrorPosition.java
 */
class Nested {}
void main() {
    System.err.println("");
}
void test() {
    System.err.println("");
}
