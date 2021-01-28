/*
 * @test /nodynamiccopyright/
 * @bug 8013852
 * @ignore 8261197
 * @summary ensure that declaration annotations are not allowed on
 *   method receiver types
 * @author Werner Dietl
 * @compile/fail/ref=DeclarationAnnotation.out -XDrawDiagnostics DeclarationAnnotation.java
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class DeclarationAnnotation {
    void bad(@DA DeclarationAnnotation this) {}
    void good(@TA DeclarationAnnotation this) {}
}

@Target(ElementType.PARAMETER)
@interface DA { }

@Target(ElementType.TYPE_USE)
@interface TA { }
