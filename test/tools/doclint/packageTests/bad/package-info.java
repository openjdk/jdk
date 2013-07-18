/*
 * @test /nodynamiccopyright/
 * @bug 8020664
 * @summary doclint gives incorrect warnings on normal package statements
 * @library ../..
 * @build DocLintTester
 * @run main DocLintTester -ref package-info.out package-info.java
 */

// missing comment
package bad;
