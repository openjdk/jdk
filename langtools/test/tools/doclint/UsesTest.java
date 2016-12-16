/*
 * @test /nodynamiccopyright/
 * @bug 8160196
 * @summary Module summary page should display information based on "api" or "detail" mode.
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -ref UsesTest.out UsesTest.java
 */

/**
 * Invalid use of uses in class documentation.
 *
 * @uses ProvidesTest
 */
public class UsesTest {
    /**
     * Invalid use of uses in field documentation
     *
     * @uses ProvidesTest Test description.
     */
    public int invalid_param;

    /**
     * Invalid use of uses in method documentation
     *
     * @uses ProvidesTest Test description.
     */
    public class InvalidParam { }
}
