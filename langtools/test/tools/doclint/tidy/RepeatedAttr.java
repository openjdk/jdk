/*
 * @test /nodynamiccopyright/
 * @library ..
 * @build DocLintTester
 * @run main DocLintTester -ref RepeatedAttr.out RepeatedAttr.java
 */

// tidy: Warning: <.*> dropping value ".*" for repeated attribute ".*"

/**
 * <img src="image.gif" alt alt="summary">
 */
public class RepeatedAttr { }
