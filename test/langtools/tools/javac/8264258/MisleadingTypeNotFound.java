/*
 * @test /nodynamiccopyright/
 * @bug 8264258
 * @summary unknown.Class.default gives misleading compilation error
 * @compile/fail/ref=MisleadingTypeNotFound.out -Xlint:all -Werror -XDrawDiagnostics -XDdev MisleadingTypeNotFound.java
 */
package knownpkg;

public class MisleadingTypeNotFound {

    void classNotFound() {
        // Not found, but in an existing package
        Class<?> c1 = knownpkg.NotFound.class;

        // Not found, but in a (system) package which exists and is in scope
        Class<?> c2 = java.lang.NotFound.class;

        // Not found, on a genuinely unknown package
        Class<?> c3 = unknownpkg.NotFound.class;

        // Not found, but in the 'java' package which is in scope as per JLS 6.3 and observable as per JLS 7.4.3
        Class<?> c4 = java.NotFound.class;
    }
}
