/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8047769
 * @modules java.base/sun.security.provider
 * @summary SecureRandom should be more frugal with file descriptors
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;

public class FileInputStreamPoolTest {

    static final byte[] bytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

    static void testCaching(File file) throws IOException {
        InputStream in1 = TestProxy.FileInputStreamPool_getInputStream(file);
        InputStream in2 = TestProxy.FileInputStreamPool_getInputStream(file);
        assertTrue(in1 == in2,
            "1st InputStream: " + in1 +
                " is not same as 2nd: " + in2);

        byte[] readBytes = new byte[bytes.length];
        int nread = in1.read(readBytes);
        assertTrue(bytes.length == nread,
            "short read: " + nread +
                " bytes of expected: " + bytes.length);
        assertTrue(Arrays.equals(readBytes, bytes),
            "readBytes: " + Arrays.toString(readBytes) +
                " not equal to expected: " + Arrays.toString(bytes));
    }

    static void assertTrue(boolean test, String message) {
        if (!test) {
            throw new AssertionError(message);
        }
    }

    static void processReferences() {
        // make JVM process References
        System.gc();
        // help ReferenceHandler thread enqueue References
        while (TestProxy.Reference_tryHandlePending(false)) {}
        // help run Finalizers
        System.runFinalization();
    }

    public static void main(String[] args) throws Exception {
        // 1st create temporary file
        File file = File.createTempFile("test", ".dat");
        try (AutoCloseable acf = () -> {
            // On Windows, failure to delete file is probably a consequence
            // of the file still being opened - so the test should fail.
            assertTrue(file.delete(),
                "Can't delete: " + file + " (is it still open?)");
        }) {
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }

            // test caching 1t time
            testCaching(file);

            processReferences();

            // test caching 2nd time - this should only succeed if the stream
            // is re-opened as a consequence of cleared WeakReference
            testCaching(file);

            processReferences();
        }
    }

    /**
     * A proxy for (package)private static methods:
     *   sun.security.provider.FileInputStreamPool.getInputStream
     *   java.lang.ref.Reference.tryHandlePending
     */
    static class TestProxy {
        private static final Method getInputStreamMethod;
        private static final Method tryHandlePendingMethod;

        static {
            try {
                Class<?> fileInputStreamPoolClass =
                    Class.forName("sun.security.provider.FileInputStreamPool");
                getInputStreamMethod =
                    fileInputStreamPoolClass.getDeclaredMethod(
                        "getInputStream", File.class);
                getInputStreamMethod.setAccessible(true);

                tryHandlePendingMethod = Reference.class.getDeclaredMethod(
                    "tryHandlePending", boolean.class);
                tryHandlePendingMethod.setAccessible(true);
            } catch (Exception e) {
                throw new Error(e);
            }
        }

        static InputStream FileInputStreamPool_getInputStream(File file)
            throws IOException {
            try {
                return (InputStream) getInputStreamMethod.invoke(null, file);
            } catch (InvocationTargetException e) {
                Throwable te = e.getTargetException();
                if (te instanceof IOException) {
                    throw (IOException) te;
                } else if (te instanceof RuntimeException) {
                    throw (RuntimeException) te;
                } else if (te instanceof Error) {
                    throw (Error) te;
                } else {
                    throw new UndeclaredThrowableException(te);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        static boolean Reference_tryHandlePending(boolean waitForNotify) {
            try {
                return (boolean) tryHandlePendingMethod
                    .invoke(null, waitForNotify);
            } catch (InvocationTargetException e) {
                Throwable te = e.getTargetException();
                if (te instanceof RuntimeException) {
                    throw (RuntimeException) te;
                } else if (te instanceof Error) {
                    throw (Error) te;
                } else {
                    throw new UndeclaredThrowableException(te);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
