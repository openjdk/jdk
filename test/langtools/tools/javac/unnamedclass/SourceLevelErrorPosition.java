/**
 * @test /nodynamiccopyright/
 * @compile/fail/ref=SourceLevelErrorPosition.out -XDrawDiagnostics SourceLevelErrorPosition.java
 */
class Nested {}
void main() {
    System.err.println("");
}
void test() {
    System.err.println("");
}
