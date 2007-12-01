/*
 * @test  /nodynamiccopyright/
 * @bug 6394563
 * @summary javac ignores -nowarn switch in 1.5.0_06 for deprecation warnings
 *
 * @compile/ref=T6394563.note.out  -XDstdout -XDrawDiagnostics -nowarn             T6394563.java
 * @compile/ref=T6394563.note.out  -XDstdout -XDrawDiagnostics -nowarn -source 1.5 T6394563.java
 * @compile/ref=T6394563.empty.out -XDstdout -XDrawDiagnostics -nowarn -source 1.4 T6394563.java
 *
 * @compile/ref=T6394563.warn.out  -XDstdout -XDrawDiagnostics -Xlint -nowarn             T6394563.java
 * @compile/ref=T6394563.warn.out  -XDstdout -XDrawDiagnostics -Xlint -nowarn -source 1.5 T6394563.java
 * @compile/ref=T6394563.empty.out -XDstdout -XDrawDiagnostics -Xlint -nowarn -source 1.4 T6394563.java
 */

class T6394563 {
    void useDeprecated() {
        deprecated.foo();
    }
}

class deprecated {
    /** @deprecated */ static void foo() { }
}
