/**
 * @test /nodynamiccopyright/
 * @bug 8250768
 * @summary Verify javac correctly reports errors for uses of classes declared using preview features.
 * @compile/ref=DeclaredUsingPreview-source.out -XDrawDiagnostics --enable-preview -source ${jdk.version} -Xlint:preview DeclaredUsingPreview.java DeclaredUsingPreviewDeclarations.java
 * @compile/ref=DeclaredUsingPreview-class.out -XDrawDiagnostics --enable-preview -source ${jdk.version} -Xlint:preview DeclaredUsingPreview.java
 */
public class DeclaredUsingPreview {
    DeclaredUsingPreviewDeclarations.C c;
    DeclaredUsingPreviewDeclarations.C2 c2; //TODO: should cause warning?
}
