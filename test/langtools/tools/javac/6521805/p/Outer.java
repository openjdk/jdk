/* /nodynamiccopyright/ */

package p;

import java.util.Objects;

class Outer {
    class Super {
        {
            // access enclosing instance so this$0 field is generated
            Objects.requireNonNull(Outer.this);
        }
    }
}
