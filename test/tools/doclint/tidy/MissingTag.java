/*
 * @test /nodynamiccopyright/
 * @library ..
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
