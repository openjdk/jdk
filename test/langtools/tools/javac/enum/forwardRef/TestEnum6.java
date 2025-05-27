/*
 * @test    /nodynamiccopyright/
 * @bug     6424491
 * @summary Cannot initialise nested enums
 * @author  Peter von der Ahé
 * @compile/fail/ref=TestEnum6.out -XDrawDiagnostics  TestEnum6.java
 */

public enum TestEnum6 {
    AAA(TestEnum6.AAA);
    TestEnum6(TestEnum6 e) {
    }
}
