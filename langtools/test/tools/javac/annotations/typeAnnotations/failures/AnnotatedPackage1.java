/*
 * @test /nodynamiccopyright/
 * @bug 8006775
 * @summary Package declarations cannot use annotations.
 * @author Werner Dietl
 * @compile/fail/ref=AnnotatedPackage1.out -XDrawDiagnostics AnnotatedPackage1.java
 */

package name.@A p1.p2;

class AnnotatedPackage1 { }

@interface A { }
