/**
 * @test /nodynamiccopyright/
 * @compile/fail/ref=ImplicitClassRecovery.out -XDrawDiagnostics --enable-preview --source ${jdk.version} ImplicitClassRecovery.java
 */
public void main() {
    //the following is intentionally missing a semicolon:
    System.err.println("Hello!")
}
