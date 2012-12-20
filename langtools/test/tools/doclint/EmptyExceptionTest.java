/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-syntax EmptyExceptionTest.java
 * @run main DocLintTester -Xmsgs:syntax -ref EmptyExceptionTest.out EmptyExceptionTest.java
 */

/** . */
public class EmptyExceptionTest {
    /** @exception NullPointerException */
    int emptyException() throws NullPointerException { }
}
