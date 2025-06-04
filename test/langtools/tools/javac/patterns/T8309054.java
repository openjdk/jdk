/**
 * @test /nodynamiccopyright/
 * @bug 8309054
 * @summary Parsing of erroneous patterns succeeds
 * @enablePreview
 * @compile/fail/ref=T8309054.out -XDrawDiagnostics --should-stop=at=FLOW T8309054.java
 */

public class T8309054  {
    public void test(Object obj) {
        boolean t1 = switch (obj) {
            case Long a[] -> true;
            default -> false;
        };
        boolean t2 = switch (obj) {
            case Double a[][][][] -> true;
            default -> false;
        };
        if (obj instanceof Float a[][]) {
        }
        if (obj instanceof Integer a = Integer.valueOf(0)) {
        }
    }
}