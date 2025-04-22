/*
 * @test /nodynamiccopyright/
 * @bug 8354556
 * @summary Expand value-based class warnings to java.lang.ref API
 * @compile/fail/ref=RequiresIdentityTest.out --patch-module java.base=${test.src} -Werror -XDrawDiagnostics -Xlint:identity RequiresIdentityHelper.java RequiresIdentityTest.java
 */

package java.lang;

public class RequiresIdentityTest extends RequiresIdentityHelper {
    class Box<T> {}

    RequiresIdentityHelper<Integer> field;                      // should warn
    RequiresIdentityHelper<Integer>[] field2;                   // should warn
    Box<? extends RequiresIdentityHelper<Integer>> field3;      // should warn
    Box<? super RequiresIdentityHelper<Integer>> field4;        // should warn

    public RequiresIdentityTest() {}
    public RequiresIdentityTest(Integer i) {
        super(i); // should warn
    }

    void test(RequiresIdentity2<Object> ri, Integer i) {
        RequiresIdentityHelper<Integer> localVar;     // should warn
        RequiresIdentityHelper<Integer>[] localVar2;  // should warn
        // there should be warnings for the invocations below
        ri.foo(i);
        ri.bar(i, i); // two for this one
        ri.gg(i);
    }
}
