/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-syntax EmptyAuthorTest.java
 * @run main DocLintTester -Xmsgs:syntax -ref EmptyAuthorTest.out EmptyAuthorTest.java
 */

/** @author */
public class EmptyAuthorTest {
}
