/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @library ..
 * @build DocLintTester
 * @run main DocLintTester -ref AnchorAlreadyDefined.out AnchorAlreadyDefined.java
 */

// tidy: Warning: <.*> anchor ".*" already defined

/**
 * <a name="here">valid</a>
 * <a name="here">duplicate</a>
 * <h1 id="here">duplicate</h1>
 */
public class AnchorAlreadyDefined { }
