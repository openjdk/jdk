/*
 * @test /nodynamiccopyright/
 * @bug 8294020
 * @summary improve error position for records without header
 * @compile/fail/ref=RecordDeclarationSyntaxTest.out -XDrawDiagnostics RecordDeclarationSyntaxTest.java
 */

class RecordDeclarationSyntaxTest {
    record R {} // no header
}
