/*
 * @test /nodynamiccopyright/
 * @bug 8004832 8284994
 * @summary Add new doclint package
 * @modules jdk.javadoc/jdk.javadoc.internal.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:-missing MissingRecordParamsTest.java
 * @run main DocLintTester -Xmsgs:missing -ref MissingRecordParamsTest.out MissingRecordParamsTest.java
 */

/** . */
public record MissingRecordParamsTest(int x) {  }
