/*
 * @test
 * @bug 8168774
 * @summary Polymorhic signature method check crashes javac
 * @compile -Xmodule:java.base BadPolySig.java
 */

package java.lang.invoke;

class MethodHandle {
    native Object m();
}
