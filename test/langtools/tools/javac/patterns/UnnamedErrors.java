/**
 * @test /nodynamiccopyright/
 * @bug 8304246
 * @summary Compiler Implementation for Unnamed patterns and variables
 * @enablePreview
 * @compile/fail/ref=UnnamedErrors.out -XDrawDiagnostics -XDshould-stop.at=FLOW UnnamedErrors.java
 */
public class UnnamedErrors {
    private int _; // error
    private int _, x;  // error
    private int x, _, y, _, z, _;  // error
    private int _ = 0, _ = 1; // error
    private int a = 0, _ = 1; // error

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

    void dominance_error(Object o) {
        switch (o) {
            case Number _ ->
                    System.out.println("A Number");
            case Integer _, String _ ->             // Error - dominated case pattern: `Integer _`
                    System.out.println("An Integer or a String");
            default ->
                    System.out.println("rest");
        }
    }

    void mixed_named_unnamed_error(Object o) {
        switch (o) {
            case Integer i, String _ ->
                    System.out.println("named/unnamed");
            default ->
                    System.out.println("rest");
        }

        switch (o) {
            case Integer _, String s ->
                    System.out.println("unnamed/named");
            default ->
                    System.out.println("rest");
        }

        switch (o) {
            case PairIS(_, _), String s ->
                    System.out.println("unnamed patterns/named");
            default ->
                    System.out.println("rest");
        }
    }

    private void test1() {
        try (Lock _ = null) {
        } catch (_) { }
    }

    int guard_errors(Object o, int x1, int x2) {
        return switch (o) {
            case Integer _ when x1 == 2, String _ when x2 == 1 -> 1;
            default -> 2;
        };
    }

    class Lock implements AutoCloseable {
        @Override
        public void close() {}
    }
    record PairIS(int i, String s) {}
}
