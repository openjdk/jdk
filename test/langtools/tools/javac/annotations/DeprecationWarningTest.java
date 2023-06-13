/*
 * @test /nodynamiccopyright/
 * @bug 8293519
 * @summary deprecation warnings should be emitted for uses of annotation methods inside other annotations
 * @compile/fail/ref=DeprecationWarningTest.out -deprecation -Werror -XDrawDiagnostics DeprecationWarningTest.java
 */

// both classes can't be inside the same outermost class or the compiler wont emit the warning as `9.6.4.6 @Deprecated` mandates
@interface Anno {
    @Deprecated
    boolean b() default false;
}

@Anno(b = true)
class Foo {}
