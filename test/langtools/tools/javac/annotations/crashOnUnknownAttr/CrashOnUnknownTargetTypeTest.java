/*
 * @test /nodynamiccopyright/
 * @summary compiler is crashing with AssertionError for annotations with unknown target type
 * @bug 8296010
 * @build A
 * @compile/fail/ref=CrashOnUnknownTargetTypeTest.out -XDrawDiagnostics CrashOnUnknownTargetTypeTest.java
 */

public class CrashOnUnknownTargetTypeTest {
    @A Object o;
    @A void m() {}
    void foo(@A int a) {
        @A int i = 0;
    }
    @A CrashOnUnknownTargetTypeTest() {}
    @A @interface B {}
    class Inner<@A T> {}
    @A class Inner2 {}
    record R(@A int i) {}
}
