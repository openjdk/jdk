/**
 * @test
 * @compile/fail/ref=UnnamedErrors.out -XDrawDiagnostics UnnamedErrors.java
 */
public class UnnamedErrors {
    private int _; //no fields
    record R(int _) {} //no record components
    UnnamedErrors(int _) {} //no constructor parameters
    void test(int _) {} //no method parameters

    record RR(int x) {}
    void test2() {
        Object o = Integer.valueOf(42);
        if (o instanceof _) {} //no top level

        if (o instanceof _(int x)) {} //no record pattern head

        switch (o) {
            case _:
                System.out.println("no underscore top level");
            default:
                System.out.println("");
        }

        switch (o) {
            case var _:
                System.out.println("no var _ top level");
            default:
                System.out.println("");
        }
    }
}