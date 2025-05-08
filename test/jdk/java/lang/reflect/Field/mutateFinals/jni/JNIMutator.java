/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

/**
 * Launched by JNIMutatorTest to test a JNI attached thread attempting to mutate a final field.
 */

public class JNIMutator {
    private static final CountDownLatch finished = new CountDownLatch(1);
    private static volatile Object obj;
    private static volatile Throwable exc;

    // public class, public final field
    public static class C1 {
        public final int value;
        C1(int value) {
            this.value = value;
        }
    }

    // public class, non-public final field
    public static class C2 {
        final int value;
        public C2(int value) {
            this.value = value;
        }
    }

    // non-public class, public final field
    static class C3 {
        public final int value;
        C3(int value) {
            this.value = value;
        }
    }

    /**
     * Usage: java JNIMutate <classname> <true|false>
     */
    public static void main(String[] args) throws Exception {
        String cn = args[0];
        boolean expectIAE = Boolean.parseBoolean(args[1]);

        Class<?> clazz = Class.forName(args[0]);
        obj = clazz.getDeclaredConstructor(int.class).newInstance(100);

        // start native thread
        startThread();

        // wait for native thread to finish
        finished.await();

        if (expectIAE) {
            if (exc == null) {
                // IAE expected
                throw new RuntimeException("IllegalAccessException not thrown");
            } else if (!(exc instanceof IllegalAccessException)) {
                // unexpected exception
                throw new RuntimeException(exc);
            }
        } else if (exc != null) {
            // no exception expected
            throw new RuntimeException(exc);
        }
    }

    /**
     * Invoked by JNI attached thread to get object.
     */
    static Object getObject() {
        return obj;
    }

    /**
     * Invoked by JNI attached thread to get Field object with accessible enabled.
     */
    static Field getField() throws NoSuchFieldException {
        Field f = obj.getClass().getDeclaredField("value");
        f.setAccessible(true);
        return f;
    }

    /**
     * Invoked by JNI attached thread when finished.
     */
    static void finish(Throwable ex) {
        exc = ex;
        finished.countDown();
    }

    private static native void startThread();

    static {
        System.loadLibrary("JNIMutator");
    }
}
