/*
 * @test /nodynamiccopyright/
 *
 * @compile/ref=empty.out -XDrawDiagnostics JBangException1.java
 * @compile/ref=empty.out -XDrawDiagnostics -Xlint:dangling-doc-comments JBangException1.java
 *
 * @compile/ref=empty.out -XDrawDiagnostics JBangException2.java
 * @compile/ref=JBangException2.enabled.out -XDrawDiagnostics -Xlint:dangling-doc-comments JBangException2.java
 */

// The classes being tested reside in files separate from this one because
// the classes need to provide the initial dangling comment, which would
// otherwise interfere with the JTReg test comment. For similar reasons,
// the files with test classes do __NOT__ have a copyright header.
