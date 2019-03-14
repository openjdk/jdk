/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @library ..
 * @modules jdk.compiler/com.sun.tools.doclint
 * @build DocLintTester
 * @run main DocLintTester -ref AnchorAlreadyDefined.out AnchorAlreadyDefined.java
 */

// tidy: Warning: <.*> anchor ".*" already defined

/**
 * <a name="here">valid</a>
 * <a name="here">duplicate</a>
 * <h2 id="here">duplicate</h2>
 */
public class AnchorAlreadyDefined { }
