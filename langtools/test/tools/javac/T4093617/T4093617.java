/*
 * @test  /nodynamiccopyright/
 * @bug     4093617
 * @summary Object has no superclass
 * @author  Peter von der Ah\u00e9
 * @compile/fail/ref=T4093617.out -XDstdout -XDdiags=%b:%l:%_%m T4093617.java
 */

package java.lang;

class Object {
    Object() { super(); }
}
