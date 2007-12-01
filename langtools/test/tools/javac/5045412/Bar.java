/**
 * @test  /nodynamiccopyright/
 * @bug 5045412
 * @compile/fail/ref=out -XDstdout -XDrawDiagnostics -Xlint:serial -XDfailcomplete=java.io.Serializable Bar.java
 */

/**
 * @test  /nodynamiccopyright/
 * @bug 5045412
 * @compile/fail/ref=out -XDstdout -XDrawDiagnostics -Xlint:serial -XDfailcomplete=java.io.Serializable Bar.java Foo.java
 */

class Bar implements java.io.Serializable { }
