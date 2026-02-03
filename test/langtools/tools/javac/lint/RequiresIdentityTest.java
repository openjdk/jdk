/*
 * @test /nodynamiccopyright/
 * @bug 8354556
 * @summary Expand value-based class warnings to java.lang.ref API
 * @compile --patch-module java.base=${test.src} RequiresIdentityHelper.java
 * @compile/fail/ref=RequiresIdentityTest.out --patch-module java.base=${test.src} -Werror -XDrawDiagnostics -Xlint:identity RequiresIdentityTest.java
 * @compile/fail/ref=RequiresIdentityTest.out --patch-module java.base=${test.src} -Werror -XDrawDiagnostics -Xlint:synchronization RequiresIdentityTest.java
 * @compile/ref=RequiresIdentityTest2.out --patch-module java.base=${test.src} -Werror -XDrawDiagnostics -Xlint:-identity RequiresIdentityTest.java
 * @compile/ref=RequiresIdentityTest2.out --patch-module java.base=${test.src} -Werror -XDrawDiagnostics -Xlint:-synchronization RequiresIdentityTest.java
 * @compile/fail/ref=RequiresIdentityTest.out --patch-module java.base=${test.src} -Werror -XDrawDiagnostics -Xlint:identity RequiresIdentityHelper.java RequiresIdentityTest.java
 * @compile/fail/ref=RequiresIdentityTest.out --patch-module java.base=${test.src} -Werror -XDrawDiagnostics -Xlint:synchronization RequiresIdentityHelper.java RequiresIdentityTest.java
 * @compile/ref=RequiresIdentityTest2.out --patch-module java.base=${test.src} -Werror -XDrawDiagnostics -Xlint:-identity RequiresIdentityHelper.java RequiresIdentityTest.java
 * @compile/ref=RequiresIdentityTest2.out --patch-module java.base=${test.src} -Werror -XDrawDiagnostics -Xlint:-synchronization RequiresIdentityHelper.java RequiresIdentityTest.java
 */

package java.lang;

@SuppressWarnings("deprecation")
public class RequiresIdentityTest extends RequiresIdentityHelper<Integer> // should warn
                                  implements RequiresIdentityHelper.RequiresIdentityInt<Integer> { // should warn
    class Box<T> {}

    RequiresIdentityHelper<Integer> field;                      // should warn
    RequiresIdentityHelper<Integer>[] field2;                   // should warn
    Box<? extends RequiresIdentityHelper<Integer>> field3;      // should warn
    Box<? super RequiresIdentityHelper<Integer>> field4;        // should warn
    RequiresIdentityHelper<Integer> field5 = new RequiresIdentityHelper<Integer>(); // two warnings here

    public RequiresIdentityTest() {}
    public RequiresIdentityTest(Integer i) {
        super(i); // should warn
    }

    void test(RequiresIdentity2<Object> ri, Integer i) { // warn on the first argument due to its enclosing type: RequiresIdentityHelper<Integer>
        RequiresIdentityHelper<Integer> localVar;     // should warn
        RequiresIdentityHelper<Integer>[] localVar2;  // should warn
        // there should be warnings for the invocations below
        ri.foo(i);
        ri.bar(i,  // warn here
               i); // and here too
        ri.gg(i);
    }

    interface I extends RequiresIdentityHelper.RequiresIdentityInt<Integer> {} // should warn

    void m(Object o) {
        RequiresIdentityHelper<?> ri = (RequiresIdentityHelper<Integer>) o; // should warn
    }

    RequiresIdentityHelper<Integer> test() { // warn
        return null;
    }

    // two warns here one for the type parameter and one for the result type
    <T extends RequiresIdentityHelper<Integer>> T test2() { return null; }

    class SomeClass<T extends RequiresIdentityHelper<Integer>> {} // warn

    record R(RequiresIdentityHelper<Integer> c) {} // warn
    record RR(R r) {}

    void m1(RequiresIdentityInt<Integer> ri) { // warn here
        if (ri instanceof RequiresIdentityInt<Integer> rii) {} // and here
    }

    void m2(RR rr) {
        if (rr instanceof RR(R(RequiresIdentityHelper<Integer> rii))) {}
    }

    <T> void m3() {}
    void m4() {
        this.<RequiresIdentityHelper<Integer>>m3();
    }

    MyIntFunction<Integer> field6 = Integer::new; // two warnings here

    class Run<T> {
        public <@jdk.internal.RequiresIdentity K> void run() {}
    }
    void m5(Runnable r) {}
    void m6() {
        m5(new Run<Object>()::<Integer>run);
    }

    void m7(Integer i, Object o) {
        RequiresIdentityHelper<Object> var1 = new <Object>RequiresIdentityHelper<Object>(i);
        RequiresIdentityHelper<Object> var2 = new <Integer>RequiresIdentityHelper<Object>(o);
        RequiresIdentityHelper<Integer> var3 = new <Object>RequiresIdentityHelper<Integer>(o);
    }
}
