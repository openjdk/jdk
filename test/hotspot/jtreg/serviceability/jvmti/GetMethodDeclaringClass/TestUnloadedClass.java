/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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
 * @bug 8339725
 * @summary Stress test GetMethodDeclaringClass
 * @requires vm.jvmti
 * @requires (os.family == "linux")
 * @library /test/lib
 * @run driver/timeout=300 TestUnloadedClass
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Platform;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;

public class TestUnloadedClass {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "-agentpath:" + Utils.TEST_NATIVE_PATH + File.separator + System.mapLibraryName("TestUnloadedClass"),
                "-Xmx50m",
                "Test");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        if (!Platform.isDebugBuild()) {
            output.shouldContain("OutOfMemoryError");
        }
    }
}

class Test {
    public static void main(String[] args) throws Exception {
        long last = System.nanoTime();
        for (int i = 0;;i++) {
            if (Platform.isDebugBuild() && i >= 1000) {
                // Debug build costs too much time to OOM so limit the loop iteration
                break;
            }
            CustomClassLoader loader = new CustomClassLoader();
            Class<?> k = loader.findClass("MyClass");
            Constructor<?> c = k.getDeclaredConstructor();
            c.setAccessible(true);
            c.newInstance();

            // call gc every ~1 second.
            if ((System.nanoTime() - last) >= 1e9) {
                System.gc();
                last = System.nanoTime();
            }
        }
    }
}

class CustomClassLoader extends ClassLoader {
    static byte[] BYTES;

    static {
        try (InputStream in = CustomClassLoader.class.getResourceAsStream("MyClass.class")) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
                BYTES = baos.toByteArray();
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public Class findClass(String name) throws ClassNotFoundException {
        return defineClass(name, BYTES, 0, BYTES.length);
    }
}

class MyClass {
}
