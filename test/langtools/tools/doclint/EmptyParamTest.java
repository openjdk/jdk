/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-syntax EmptyParamTest.java
 * @run main DocLintTester -Xmsgs:syntax -ref EmptyParamTest.out EmptyParamTest.java
 */

/** . */
public class EmptyParamTest {
    /** @param i */
    int emptyParam(int i) { }
}
