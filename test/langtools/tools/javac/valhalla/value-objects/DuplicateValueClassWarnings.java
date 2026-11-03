/*
 * @test /nodynamiccopyright/
 * @bug 8386995
 * @summary Value class preview feature warning should be emitted only once
 * @enablePreview
 * @compile/ref=DuplicateValueClassWarnings.out -Xlint:preview -XDrawDiagnostics ${test.file}
 */

public value class DuplicateValueClassWarnings {
}
