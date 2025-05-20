/*
 * @test    /nodynamiccopyright/
 * @bug     6209839
 * @summary Illegal forward reference to enum constants allowed by javac
 * @author  Peter von der Ahé
 * @compile/fail/ref=TestEnum3.out -XDrawDiagnostics  TestEnum3.java
 */

enum TestEnum {
    BAR,
    QUX,
    BAZ {
        private final String x = X;
    };
    static String X = "X";
}
