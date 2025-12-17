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

/*
 * @test
 * @bug 8368500
 * @run junit/othervm ContextClassLoaderTest
 * @summary Check the context classloader is reset
 */
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.Future;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContextClassLoaderTest {

    @Test
    void testContextClassLoaderIsSetAndRestored() throws Exception {
        Future<?> future = ForkJoinPool.commonPool().submit(() -> {
            Thread thread = Thread.currentThread();
            ClassLoader originalCCL = thread.getContextClassLoader();
            ClassLoader customCCL = new URLClassLoader(new URL[0], originalCCL);
            // Set custom context classloader and verify it
            thread.setContextClassLoader(customCCL);
            assertSame(customCCL, thread.getContextClassLoader(), "Custom context class loader not set");

            // Reset to original and verify restoration
            thread.setContextClassLoader(originalCCL);
            assertSame(originalCCL, thread.getContextClassLoader(), "Original context class loader not restored");
        });
        future.get();
    }
}

