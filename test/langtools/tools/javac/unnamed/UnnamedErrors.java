/**
 * @test
 * @compile/fail/ref=UnnamedErrors.out -XDrawDiagnostics UnnamedErrors.java
 */
public class UnnamedErrors {
    private int _; //no fields
    record R(int _) {} //no record components
    UnnamedErrors(int _) {} //no constructor parameters
    void test(int _) {} //no method parameters
}