/*
 * @test /nodynamiccopyright/
 * @library ..
 * @build DocLintTester
 * @run main DocLintTester -ref InvalidTag.out InvalidTag.java
 */

// tidy: Error: <.*> is not recognized!

/**
 * List<String> list = new ArrayList<>();
 */
public class InvalidTag { }
