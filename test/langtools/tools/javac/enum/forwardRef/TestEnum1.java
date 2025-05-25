/*
 * @test    /nodynamiccopyright/
 * @bug     6209839
 * @summary Illegal forward reference to enum constants allowed by javac
 * @author  Peter von der Ahé
 * @compile/fail/ref=TestEnum1.out -XDrawDiagnostics  TestEnum1.java
 */

enum TestEnum {
    BAR {
        private final TestEnum self = BAR;
        private final TestEnum other = QUX;
    },
    QUX
}
