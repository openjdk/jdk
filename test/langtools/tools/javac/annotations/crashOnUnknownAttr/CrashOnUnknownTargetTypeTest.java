/*
 * @test /nodynamiccopyright/
 * @summary compiler is crashing with AssertionError for annotations with unknown target type
 * @bug 8296010
 * @build A
 * @compile/fail/ref=CrashOnUnknownTargetTypeTest.out -XDrawDiagnostics CrashOnUnknownTargetTypeTest.java
 */

public class CrashOnUnknownTargetTypeTest {
    @A Object o;
}
