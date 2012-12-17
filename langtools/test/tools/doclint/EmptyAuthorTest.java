/*
 * @test /nodynamiccopyright/
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-syntax EmptyAuthorTest.java
 * @run main DocLintTester -Xmsgs:syntax -ref EmptyAuthorTest.out EmptyAuthorTest.java
 */

/** @author */
public class EmptyAuthorTest {
}
