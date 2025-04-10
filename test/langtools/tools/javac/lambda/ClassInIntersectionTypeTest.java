/*
 * @test /nodynamiccopyright/
 * @bug 8322810
 * @summary Lambda expressions can implement classes
 * @compile/fail/ref=ClassInIntersectionTypeTest.out -XDrawDiagnostics ClassInIntersectionTypeTest.java
 */

import java.io.Serializable;

public class ClassInIntersectionTypeTest {
    // test 1
    void m() {
        var r = (ClassInIntersectionTypeTest & Runnable) () -> System.out.println("Hello, World!");
    }

    // test 2
    static void foo() {
        run(() -> System.out.println("Hello, World!"));
    }

    static <T extends ClassInIntersectionTypeTest & Runnable> void run(T t) {
        t.run();
    }

    @interface Anno {}
    Anno a = (Anno & Serializable) ()-> null; // OK

    Anno b = ()-> null; // OK

    static void bar() {
        annotationType(() -> null);
    }
    static <T extends Anno & Serializable> void annotationType(T t) {
        t.annotationType();  // OK
    }
}
