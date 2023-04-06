/**
 * @test
 * @enablePreview
 * @compile/fail/ref=UnnamedErrors.out -XDrawDiagnostics -XDshould-stop.at=FLOW UnnamedErrors.java
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

    void dominance1(Object o) {
        switch (o) {
            case Number _ ->
                    System.out.println("A Number");
            case Integer _, String _ ->             // Error - dominated case pattern: `Integer _`
                    System.out.println("An Integer or a String");
            default ->
                    System.out.println("rest");
        }
    }
}