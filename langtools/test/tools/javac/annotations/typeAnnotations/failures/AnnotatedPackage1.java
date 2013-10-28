/*
 * @test /nodynamiccopyright/
 * @bug 8006775
 * @summary Package declarations cannot use annotations.
 * @author Werner Dietl
 * @ignore
 * @compile/fail/ref=AnnotatedPackage1.out -XDrawDiagnostics AnnotatedPackage1.java
 */

package name.@A p1.p2;

import java.lang.annotation.*;

class AnnotatedPackage1 { }

@Target(ElementType.TYPE_USE)
@interface A { }
