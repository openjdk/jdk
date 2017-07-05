/*
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 * @test
 * @bug 6695810
 * @summary null oop passed to encode_heap_oop_not_null
 * @run main/othervm -Xbatch Test
 */

public class Test {
    Test _t;

    static void test(Test t1, Test t2) {
        if (t2 != null)
            t1._t = t2;

        if (t2 != null)
            t1._t = t2;
    }

    public static void main(String[] args) {
        Test t = new Test();
        for (int i = 0; i < 50; i++) {
            for (int j = 0; j < 100; j++) {
                test(t, t);
            }
            test(t, null);
        }
        for (int i = 0; i < 10000; i++) {
            test(t, t);
        }
        test(t, null);
    }
}
