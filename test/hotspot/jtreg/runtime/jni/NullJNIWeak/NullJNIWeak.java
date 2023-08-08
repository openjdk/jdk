/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 */

import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8313874
 * @summary Test calling JNI NewWeakGlobalRef with null value.
 *          Should not throw error/exception. Should return null.
 * @library /test/lib
 * @run main/othervm/native NullJNIWeak
 */
public class NullJNIWeak {
    static {
        System.loadLibrary("NullJNIWeak");
    }

    private static native Object newWeakGlobalRef(Object o);

    public static void main(String[] args) {
        Object result = newWeakGlobalRef(null);
        Asserts.assertEquals(result, null, "NewWeakGlobalRef(null) should return null");
    }
}
