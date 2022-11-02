/*
 * @test /nodynamiccopyright/
 * @bug 8193904
 * @summary Definite assignment required whether "this.x" or "Foo.this.x"
 *
 * @compile/fail/ref=QualifiedThis.out -XDrawDiagnostics QualifiedThis.java
 */

class QualifiedThis {
    final int foo;
    QualifiedThis() {
        System.err.println(QualifiedThis.this.foo);
        this.foo = 42;
    }
}
