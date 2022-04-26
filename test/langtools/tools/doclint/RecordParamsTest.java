/*
 * @test /nodynamiccopyright/
 * @bug 8004832 8284994
 * @summary Add new doclint package
 * @modules jdk.javadoc/jdk.javadoc.internal.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:all -ref RecordParamsTest.out RecordParamsTest.java
 */

/**
 * Comment.
 * @param a aaa
 * @param a aaa
 * @param z zzz
 */
public record RecordParamsTest(int a, int b, int c) {  }
