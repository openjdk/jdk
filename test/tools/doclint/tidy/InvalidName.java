/*
 * @test /nodynamiccopyright/
 * @bug 8004832
 * @summary Add new doclint package
 * @library ..
 * @build DocLintTester
 * @run main DocLintTester -ref InvalidName.out InvalidName.java
 */

// tidy: Warning: <a> cannot copy name attribute to id

/**
 * <a name="abc">valid</a>
 * <a name="abc123">valid</a>
 * <a name="a.1:2-3_4">valid</a>
 * <a name="foo()">invalid</a>
 */
public class InvalidName { }
