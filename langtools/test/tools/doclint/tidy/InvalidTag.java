/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @library ..
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -ref InvalidTag.out InvalidTag.java
 */

// tidy: Error: <.*> is not recognized!

/**
 * List<String> list = new ArrayList<>();
 */
public class InvalidTag { }
