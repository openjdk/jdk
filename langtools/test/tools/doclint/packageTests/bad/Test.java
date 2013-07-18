/*
 * @test /nodynamiccopyright/
 * @bug 8020664
 * @summary doclint gives incorrect warnings on normal package statements
 * @library ../..
 * @build DocLintTester
 * @run main DocLintTester -ref Test.out Test.java
 */

/** Unexpected comment */
package bad;

class Test { }

