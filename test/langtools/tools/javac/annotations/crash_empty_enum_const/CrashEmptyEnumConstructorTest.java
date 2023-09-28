/*
 * @test /nodynamiccopyright/
 * @bug 8305971
 * @summary NPE in JavacProcessingEnvironment for missing enum constructor body
 * @compile SimpleProcessor.java
 * @compile/fail/ref=CrashEmptyEnumConstructorTest.out -processor SimpleProcessor -XDrawDiagnostics CrashEmptyEnumConstructorTest.java
 */

enum CrashEmptyEnumConstructorTest {
    ONE("");
    CrashEmptyEnumConstructorTest(String one);
    final String one;
}
