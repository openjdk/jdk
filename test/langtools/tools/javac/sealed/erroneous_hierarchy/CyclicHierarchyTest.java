/*
 * @test /nodynamiccopyright/
 * @bug 8319987
 * @summary compilation of sealed classes leads to infinite recursion
 * @compile/fail/ref=CyclicHierarchyTest.out -XDrawDiagnostics CyclicHierarchyTest.java
 */

class CyclicHierarchyTest {
    sealed interface Action permits Add {}
    sealed interface MathOp permits Add {}
    sealed static class Add implements MathOp permits Add {}
}
