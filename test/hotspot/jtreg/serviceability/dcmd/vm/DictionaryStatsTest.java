/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test of diagnostic command VM.systemdictionary which prints dictionary stats
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          jdk.compiler
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run testng DictionaryStatsTest
 */

import org.testng.Assert;
import org.testng.annotations.Test;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.lang.ref.Reference;

public class DictionaryStatsTest {

    // Expecting some output like:

    // System Dictionary for 'TestClassLoader' @10ba88b9 class loader statistics:
    // Number of buckets       :       128 =      1024 bytes, each 8
    // Number of entries       :         6 =        96 bytes, each 16
    // Number of literals      :         6 =        96 bytes, avg  16.000
    // Total footprint         :           =      1216 bytes
    // Average bucket size     :     0.047
    // Variance of bucket size :     0.045
    // Std. dev. of bucket size:     0.211
    // Maximum bucket size     :         1
    // LoaderConstraintTable statistics:
    // Number of buckets       :       107 =       856 bytes, each 8
    // Number of entries       :        31 =      1736 bytes, each 56
    // Number of literals      :        31 =      1120 bytes, avg  36.000
    // Total footprint         :           =      3712 bytes
    // Average bucket size     :     0.290
    // Variance of bucket size :     0.281
    // Std. dev. of bucket size:     0.530
    // Maximum bucket size     :         2


    public void run(CommandExecutor executor) throws ClassNotFoundException {

        ClassLoader named_cl = new TestClassLoader("TestClassLoader", null);
        Class<?> c2 = Class.forName("TestClass2", true, named_cl);
        if (c2.getClassLoader() != named_cl) {
            Assert.fail("TestClass defined by wrong classloader: " + c2.getClassLoader());
        }

        // First test: simple output, no classes displayed
        OutputAnalyzer output = executor.execute("VM.systemdictionary");
        output.shouldContain("System Dictionary for 'bootstrap'");
        output.shouldMatch("System Dictionary for 'TestClassLoader'");
        output.shouldContain("class loader statistics:");
        output.shouldContain("Number of buckets");
        output.shouldContain("Number of entries");
        output.shouldContain("Number of literals");
        output.shouldContain("Total footprint");
        output.shouldContain("Average bucket size");
        output.shouldContain("Variance of bucket size");
        output.shouldContain("Std. dev. of bucket size");
        output.shouldContain("Maximum bucket size");
        output.shouldMatch("LoaderConstraintTable statistics:");

        // what is this?
        Reference.reachabilityFence(named_cl);
    }

    static class TestClassLoader extends ClassLoader {

        public TestClassLoader() {
            super();
        }

        public TestClassLoader(String name, ClassLoader parent) {
            super(name, parent);
        }

        public static final String CLASS_NAME = "TestClass2";

        static ByteBuffer readClassFile(String name)
        {
            File f = new File(System.getProperty("test.classes", "."),
                              name);
            try (FileInputStream fin = new FileInputStream(f);
                 FileChannel fc = fin.getChannel())
            {
                return fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            } catch (IOException e) {
                Assert.fail("Can't open file: " + name, e);
            }

            /* Will not reach here as Assert.fail() throws exception */
            return null;
        }

        protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException
        {
            Class<?> c;
            if (!CLASS_NAME.equals(name)) {
                c = super.loadClass(name, resolve);
            } else {
                // should not delegate to the system class loader
                c = findClass(name);
                if (resolve) {
                    resolveClass(c);
                }
            }
            return c;
        }

        protected Class<?> findClass(String name)
            throws ClassNotFoundException
        {
            if (!CLASS_NAME.equals(name)) {
                throw new ClassNotFoundException("Unexpected class: " + name);
            }
            return defineClass(name, readClassFile(name + ".class"), null);
        }

    }

    @Test
    public void jmx() throws ClassNotFoundException {
        run(new JMXExecutor());
    }

}

class TestClass2 {
    static {
        Runnable r = () -> System.out.println("Hello");
        r.run();
    }
}
