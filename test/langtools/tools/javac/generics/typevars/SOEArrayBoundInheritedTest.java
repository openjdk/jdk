/*
 * @test /nodynamiccopyright/
 * @bug 8389044
 * @summary javac crashes with StackOverflowError erasing a self-referential
 *          array-typed type-variable bound reached through inheritance
 * @compile/fail/ref=SOEArrayBoundInheritedTest.out -XDrawDiagnostics SOEArrayBoundInheritedTest.java
 */

class SOEArrayBoundInheritedTest {
    void m() {
        System.out.println(new A<Integer[]>().f());
    }
}

interface I<T> { T f(); }

class B<U extends U[]> implements I<U> {
    public U f() { return null; }
}

class A<V> extends B<V> {}
