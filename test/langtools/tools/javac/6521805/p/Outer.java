/* /nodynamiccopyright/ */

package p;

class Outer {
    class Super {
        {
            // access enclosing instance so this$0 field is generated
            Outer.this.toString();
        }
    }
}
