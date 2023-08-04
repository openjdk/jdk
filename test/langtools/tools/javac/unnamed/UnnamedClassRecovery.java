/**
 * @test /nodynamiccopyright/
 * @compile/fail/ref=UnnamedClassRecovery.out -XDrawDiagnostics --enable-preview --source ${jdk.version} UnnamedClassRecovery.java
 */
public void main() {
    //the following is intentionally missing a semicolon:
    System.err.println("Hello!")
}
