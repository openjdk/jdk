/*
 * @test /nodynamiccopyright/
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-syntax EmptyReturnTest.java
 * @run main DocLintTester -Xmsgs:syntax -ref EmptyReturnTest.out EmptyReturnTest.java
 */

/** . */
public class EmptyReturnTest {
    /** @return */
    int emptyReturn() { }
}
