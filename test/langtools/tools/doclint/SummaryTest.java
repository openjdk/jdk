/*
 * @test /nodynamiccopyright/
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -ref SummaryTest.out SummaryTest.java
 */

/** No comment. */
public class SummaryTest {
    /**
     {@summary legal} **/
    public void m0() {}

    /** <p> {@summary illegal} **/
    public void m1() {}

    /** {@summary legal} text {@summary illegal} **/
    public void m2() {}
}
