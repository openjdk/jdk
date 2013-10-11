/* @test /nodynamiccopyright/
 * @bug 8025246
 * @summary doclint is showing error on anchor already defined when it's not
 * @library ../..
 * @build DocLintTester
 * @run main DocLintTester -ref package-info.out package-info.java
 * @compile/fail/ref=package-info.javac.out -XDrawDiagnostics -Werror -Xdoclint:all package-info.java
 */

/**
 * <a name=here>here</a>
 * <a name=here>here again</a>
 */
package p;

