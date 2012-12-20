/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
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
