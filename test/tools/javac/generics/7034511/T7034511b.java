/*
 * @test /nodynamiccopyright/
 * @ignore 7041019 Bogus type-variable substitution with array types with dependencies on accessibility check
 * @bug     7034511 7040883
 * @summary Loophole in typesafety
 * @compile/fail/ref=T7034511b.out -XDrawDiagnostics T7034511b.java
 */

// backing out 7034511, see 7040883

class T7034511b {
    static class MyList<E> {
        E toArray(E[] e) { return null; }
    }

    void test(MyList<?> ml, Object o[]) {
        ml.toArray(o);
    }
}
