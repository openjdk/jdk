/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary check for missing annotation value
 * @author Mahmood Ali
 * @compile/fail/ref=MissingAnnotationValue.out -XDrawDiagnostics -source 1.7 MissingAnnotationValue.java
 */
class MissingAnnotationValue<K extends @A Object> {
}

@interface A { int field(); }
