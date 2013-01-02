/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-syntax EmptyReturnTest.java
 * @run main DocLintTester -Xmsgs:syntax -ref EmptyReturnTest.out EmptyReturnTest.java
 */

/** . */
public class EmptyReturnTest {
    /** @return */
    int emptyReturn() { }
}
