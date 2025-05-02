/**
 * @test /nodynamiccopyright/
 * @bug 8344706
 * @compile/fail/ref=ImplicitClassRecovery.out -XDrawDiagnostics ImplicitClassRecovery.java
 */
public void main() {
    //the following is intentionally missing a semicolon:
    System.err.println("Hello!")
}
