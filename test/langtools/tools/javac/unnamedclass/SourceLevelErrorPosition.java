/**
 * @test /nodynamiccopyright/
 * @bug 8310314
 * @summary Ensure proper error position for the "unnamed classes not supported" error
 * @compile/fail/ref=SourceLevelErrorPosition.out -XDrawDiagnostics SourceLevelErrorPosition.java
 */
class Nested {}
void main() {
    System.err.println("");
}
void test() {
    System.err.println("");
}
