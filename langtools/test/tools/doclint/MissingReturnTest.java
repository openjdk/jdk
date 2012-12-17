/*
 * @test /nodynamiccopyright/
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-missing MissingReturnTest.java
 * @run main DocLintTester -Xmsgs:missing -ref MissingReturnTest.out MissingReturnTest.java
 */

/** . */
public class MissingReturnTest {
    /** no return allowed */
    MissingReturnTest() { }

    /** no return allowed */
    void return_void() { }

    /** no return required */
    Void return_Void() { }

    /** */
    int missingReturn() { }
}
