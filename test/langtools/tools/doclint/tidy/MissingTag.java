/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @library ..
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -ref MissingTag.out MissingTag.java
 */

// tidy: Warning: missing <.*>
// tidy: Warning: missing </.*> before </.*>

/**
 * </p>
 * <h1> <b> text </h1>
 */
public class MissingTag { }
