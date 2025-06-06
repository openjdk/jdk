/*
 * @test    /nodynamiccopyright/
 * @bug     6209839
 * @summary Illegal forward reference to enum constants allowed by javac
 * @author  Peter von der Ahé
 * @compile/fail/ref=TestEnum2.out -XDrawDiagnostics  TestEnum2.java
 */

enum TestEnum {
    BAR,
    QUX,
    BAZ {
        private final TestEnum a = BAR;
        private final TestEnum b = QUX;
    }
}
