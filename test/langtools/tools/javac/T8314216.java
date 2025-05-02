/*
 * @test  /nodynamiccopyright/
 * @bug 8314216
 * @summary Multiple patterns without unnamed variables
 * @compile/fail/ref=T8314216.out -XDrawDiagnostics T8314216.java
 */

public class T8314216 {
    enum X {A, B}

    void test(Object obj) {
        switch (obj) {
            case X.A, Integer _ -> System.out.println("A or Integer");
            case String _, X.B -> System.out.println("B or String");
            default -> System.out.println("other");
        }
    }
}
