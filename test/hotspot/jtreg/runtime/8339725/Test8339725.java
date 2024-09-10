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
 * @library /test/lib
 * @library /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm/native -agentlib:agent8339725 Test8339725
 */

import java.util.Base64;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import java.io.File;

public class Test8339725 {
    public static void main(String[] args) throws Exception {
        test("-XX:+UseG1GC");
        test("-XX:+UseZGC");
    }

    public static void test(String gcArg) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-agentpath:" + Utils.TEST_NATIVE_PATH + File.separator + System.mapLibraryName("agent8339725"), "-Xmx100m", gcArg, "Test");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        System.out.println(output.getOutput());
        output.shouldContain("OutOfMemoryError");
    }
}

class Test {
    public static void main(String[] args) throws Exception {
        long last = System.nanoTime();
        for (int i = 0;; i++) {
            CustomClassLoader loader = new CustomClassLoader();
            Class<?> k = loader.findClass("TemplateFFFFFFFF");
            Object o = k.getDeclaredConstructor().newInstance();

            // call gc every ~1 second.
            if ((System.nanoTime() - last) >= 1e9) {
                System.gc();
                last = System.nanoTime();
            }
        }
    }
}

class CustomClassLoader extends ClassLoader {
    @Override
    public Class findClass(String name) throws ClassNotFoundException {
        byte[] b = Base64.getDecoder()
                .decode("yv66vgAAADQADgoAAwALBwAMBwANAQAGPGluaXQ+AQADKClWAQAEQ29kZQEAD0xpbmVOdW1iZXJU" +
                        "YWJsZQEAEmRvVGVtcGxhdGVGRkZGRkZGRgEAClNvdXJjZUZpbGUBABVUZW1wbGF0ZUZGRkZGRkZG" +
                        "LmphdmEMAAQABQEAEFRlbXBsYXRlRkZGRkZGRkYBABBqYXZhL2xhbmcvT2JqZWN0ACEAAgADAAAA" +
                        "AAACAAEABAAFAAEABgAAAB0AAQABAAAABSq3AAGxAAAAAQAHAAAABgABAAAAAQABAAgABQABAAYA" +
                        "AAAZAAAAAQAAAAGxAAAAAQAHAAAABgABAAAAAwABAAkAAAACAAo=");
        return defineClass(name, b, 0, b.length);
    }
}
