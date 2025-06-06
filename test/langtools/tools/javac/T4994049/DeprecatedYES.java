/*
 * @test    /nodynamiccopyright/
 * @bug     6229758
 * @summary deprecatedNOT! is
 * @author  Peter von der Ahé
 * @compile -Xlint:deprecation DeprecatedYES.java
 * @compile/fail/ref=DeprecatedYES.out -XDrawDiagnostics -Werror -Xlint:deprecation DeprecatedYES.java
 */

class A {
    /**@deprecated*/
    void foo() {
    }
}

class B {
    void bar(A a) {
        a.foo();
    }
}
