/*
 * @test /nodynamiccopyright/
 * @library ..
 * @build DocLintTester
 * @run main DocLintTester -ref BadEnd.out BadEnd.java
 */

// tidy: Warning: <.*> is probably intended as </.*>

/**
 * <a name="here"> text <a>
 * <code> text <code>
 */
public class BadEnd { }
