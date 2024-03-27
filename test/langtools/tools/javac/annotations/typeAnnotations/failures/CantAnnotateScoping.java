/*
 * @test /nodynamiccopyright/
 * @bug 8006733 8006775 8043226
 * @summary Ensure behavior for nested types is correct.
 * @author Werner Dietl
 * @compile/fail/ref=CantAnnotateScoping.out -XDrawDiagnostics CantAnnotateScoping.java
 */

import java.util.List;
import java.util.ArrayList;

import java.lang.annotation.*;

@Target({ElementType.TYPE_USE})
@interface TA {}
@Target({ElementType.TYPE_USE})
@interface TA2 {}

@Target({ElementType.FIELD})
@interface DA {}
@Target({ElementType.FIELD})
@interface DA2 {}

@Target({ElementType.TYPE_USE, ElementType.FIELD})
@interface DTA {}
@Target({ElementType.TYPE_USE, ElementType.FIELD})
@interface DTA2 {}

class Test {
    static class Outer {
        static class SInner {}
    }

    // Legal
    List<Outer. @TA SInner> li;

    // Illegal: inadmissible location for type-use annotations: @TA
    @TA Outer.SInner osi;
    // Illegal: inadmissible location for type-use annotations: @TA
    List<@TA Outer.SInner> aloi;
    // Illegal
    // 1: inadmissible location for type-use annotations: @TA,@TA2
    // 2: annotation @DA not applicable in this type context
    Object o1 = new @TA @DA @TA2 Outer.SInner();
    // Illegal
    // 1: inadmissible location for type-use annotations: @TA
    // 2: annotation @DA not applicable in this type context
    Object o = new ArrayList<@TA @DA Outer.SInner>();

    // Illegal: inadmissible location for type-use annotations: @TA
    @TA java.lang.Object f1;

    // Legal: @DA is only a declaration annotation
    @DA java.lang.Object f2;

    // Legal: @DTA is both a type-use and declaration annotation
    @DTA java.lang.Object f3;

    // Illegal: inadmissible location for type-use annotations: @TA,@TA2
    @DTA @DA @TA @DA2 @TA2 java.lang.Object f4;

    // Illegal: annotation @DA not applicable in this type context
    java. @DA lang.Object f5;

    // Illegal: two messages:
    // 1: package java.XXX does not exist
    // 2: annotation @DA not applicable in this type context
    java. @DA XXX.Object f6;

    // Illegal: inadmissible location for type-use annotations: @TA
    java. @TA lang.Object f7;
}
