/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-syntax EmptyAuthorTest.java
 * @run main DocLintTester -Xmsgs:syntax -ref EmptyAuthorTest.out EmptyAuthorTest.java
 */

/** @author */
public class EmptyAuthorTest {
}
