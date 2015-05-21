/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-accessibility AccessibilityTest.java
 * @run main DocLintTester -ref AccessibilityTest.out AccessibilityTest.java
 */

/** */
public class AccessibilityTest {

    /**
     * <h2> ... </h2>
     */
    public void missing_h1() { }

    /**
     * <h1> ... </h1>
     * <h3> ... </h3>
     */
    public void missing_h2() { }

    /**
     * <img src="x.jpg">
     */
    public void missing_alt() { }

    /**
     * <table summary="ok"><tr><th>head<tr><td>data</table>
     */
    public void table_with_summary() { }

    /**
     * <table><caption>ok</caption><tr><th>head<tr><td>data</table>
     */
    public void table_with_caption() { }

    /**
     * <table><tr><th>head<tr><td>data</table>
     */
    public void table_without_summary_and_caption() { }
}

