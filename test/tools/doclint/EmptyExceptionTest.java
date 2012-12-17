/*
 * @test /nodynamiccopyright/
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-syntax EmptyExceptionTest.java
 * @run main DocLintTester -Xmsgs:syntax -ref EmptyExceptionTest.out EmptyExceptionTest.java
 */

/** . */
public class EmptyExceptionTest {
    /** @exception NullPointerException */
    int emptyException() throws NullPointerException { }
}
