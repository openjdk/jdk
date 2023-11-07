/*
 * @test  /nodynamiccopyright/
 * @bug 8314423
 * @summary Multiple patterns without unnamed variables
 * @compile/fail/ref=T8314423.out -XDrawDiagnostics --release 21 T8314423.java
 * @compile T8314423.java
 */

public class T8314423 {
    record R1() {}
    record R2() {}

    static void test(Object obj) {
        switch (obj) {
            case R1(), R2() -> System.out.println("R1 or R2");
            default -> System.out.println("other");
        }
    }

    public static void main(String[] args) {
        test(new R1());
    }
}
