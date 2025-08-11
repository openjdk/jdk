/*
 * /nodynamiccopyright/
 */

import java.lang.invoke.VarHandle;

class PolymorphicMethodTest<V> {
    VarHandle vh;
    V method(Object obj) {
        return (V)vh.getAndSet(this, obj);
    }
}
