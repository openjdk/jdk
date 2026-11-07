/*
 * @test
 * @summary Basic test for singleobject classes
 * @compile SingleObjectTest.java
 * @run main SingleObjectTest
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

public class SingleObjectTest {

    static class Base {
        int baseVal() {
            return 10;
        }
    }

    public singleobject class S extends Base implements Runnable {
        int count;

        @Override
        public void run() {
            count++;
        }
    }

    public static void main(String[] args) {
        int singleobject = 1;
        if (singleobject != 1) {
            throw new AssertionError();
        }

        S s1 = S.getInstance();
        S s2 = S.getInstance();
        if (s1 == null || s1 != s2) {
            throw new AssertionError();
        }

        s1.run();
        if (s2.count != 1 || s1.baseVal() != 10) {
            throw new AssertionError();
        }

        if (!Modifier.isFinal(S.class.getModifiers())) {
            throw new AssertionError();
        }

        Constructor<?>[] cs = S.class.getDeclaredConstructors();
        if (cs.length != 1 || !Modifier.isPrivate(cs[0].getModifiers())) {
            throw new AssertionError();
        }
    }
}
