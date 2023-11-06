/**
 * @test /nodynamiccopyright/
 * @bug 8314578
 * @enablePreview
 * @summary Parsing of erroneous patterns succeeds
 * @compile/fail/ref=T8314578.out -XDrawDiagnostics T8314578.java
 */
public class T8314578 {
    record R1() {}
    record R2() {}

    static void test(Object o) {
        switch (o) {
            case R1() when o instanceof String s:
            case R2() when o instanceof Integer i:
                System.out.println("hello: " + i);
                break;
            default:
                break;
        }
    }

    static void test2(Object o) {
        switch (o) {
            case R1() when o instanceof String s:
                System.out.println("hello: " + s);
            case R2() when o instanceof Integer i:
                System.out.println("hello: " + i);
                break;
            default:
                break;
        }
    }

    static int unnamedInGuardsOK(String s) {
        return switch (s) {
            case String _ when s instanceof String _ ->  // should be OK
                    1;
            default ->
                    -1;
        };
    }
}