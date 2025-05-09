/*
 * @test /nodynamiccopyright/
 * @bug 8322810
 * @summary Lambda expressions can implement classes
 * @compile/fail/ref=ClassInIntersectionTypeTest.out -XDrawDiagnostics ClassInIntersectionTypeTest.java
 */

import java.io.Serializable;
import java.lang.annotation.Annotation;

public class ClassInIntersectionTypeTest {
    // test 1
    void m1() {
        ClassInIntersectionTypeTest r1 = (ClassInIntersectionTypeTest & Runnable) () -> System.out.println("Hello, World!");
        ClassInIntersectionTypeTest r2 = (ClassInIntersectionTypeTest & Runnable) ClassInIntersectionTypeTest::run1;
    }

    static void run1() {}

    // test 2
    static void foo() {
        run2(() -> System.out.println("Hello, World!"));
        run2(ClassInIntersectionTypeTest::run1);
    }

    static <T extends ClassInIntersectionTypeTest & Runnable> void run2(T t) {
        t.run();
    }

    static Class<? extends Annotation> myAnnoType() { return null; }
    @interface Anno {}
    @interface Anno2 {}

    Anno anno1 = (Anno & Serializable) ()-> null; // annotations not allowed
    Anno anno2 = (Serializable & Anno & Anno2) ()-> null; // annotations not allowed
    Anno anno3 = (Anno & Serializable) ClassInIntersectionTypeTest::myAnnoType; // annotations not allowed
    Anno anno4 = (Serializable & Anno2 & Anno) ClassInIntersectionTypeTest::myAnnoType; // annotations not allowed

    static void bar() {
        annotationType(() -> null);
        annotationType(ClassInIntersectionTypeTest::myAnnoType);
    }

    static <T extends Anno & Serializable> void annotationType(T t) {
        t.annotationType();
    }

    Anno anno5 = ()-> null; // annotations are not functional interfaces
}
