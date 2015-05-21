/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-syntax EmptySinceTest.java
 * @run main DocLintTester -Xmsgs:syntax -ref EmptySinceTest.out EmptySinceTest.java
 */

/** . */
public class EmptySinceTest {
    /** @since */
    int emptySince() { }
}
