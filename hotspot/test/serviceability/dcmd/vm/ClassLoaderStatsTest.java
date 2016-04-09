/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test of diagnostic command VM.classloader_stats
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.jvmstat/sun.jvmstat.monitor
 * @build jdk.test.lib.*
 * @build jdk.test.lib.dcmd.*
 * @run testng ClassLoaderStatsTest
 */

import org.testng.annotations.Test;
import org.testng.Assert;

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassLoaderStatsTest {

    // ClassLoader         Parent              CLD*               Classes   ChunkSz   BlockSz  Type
    // 0x00000007c0215928  0x0000000000000000  0x0000000000000000       0         0         0  org.eclipse.osgi.baseadaptor.BaseAdaptor$1
    // 0x00000007c0009868  0x0000000000000000  0x00007fc52aebcc80       1      6144      3768  sun.reflect.DelegatingClassLoader
    // 0x00000007c0009868  0x0000000000000000  0x00007fc52b8916d0       1      6144      3688  sun.reflect.DelegatingClassLoader
    // 0x00000007c0009868  0x00000007c0038ba8  0x00007fc52afb8760       1      6144      3688  sun.reflect.DelegatingClassLoader
    // 0x00000007c0009868  0x0000000000000000  0x00007fc52afbb1a0       1      6144      3688  sun.reflect.DelegatingClassLoader
    // 0x0000000000000000  0x0000000000000000  0x00007fc523416070    5019  30060544  29956216  <boot classloader>
    //                                                                455   1210368    672848   + unsafe anonymous classes
    // 0x00000007c016b5c8  0x00000007c0038ba8  0x00007fc52a995000       5      8192      5864  org.netbeans.StandardModule$OneModuleClassLoader
    // 0x00000007c0009868  0x00000007c016b5c8  0x00007fc52ac13640       1      6144      3896  sun.reflect.DelegatingClassLoader
    // ...

    static Pattern clLine = Pattern.compile("0x\\p{XDigit}*\\s*0x\\p{XDigit}*\\s*0x\\p{XDigit}*\\s*(\\d*)\\s*(\\d*)\\s*(\\d*)\\s*(.*)");
    static Pattern anonLine = Pattern.compile("\\s*(\\d*)\\s*(\\d*)\\s*(\\d*)\\s*.*");

    public static DummyClassLoader dummyloader;

    public void run(CommandExecutor executor) throws ClassNotFoundException {

        // create a classloader and load our special class
        dummyloader = new DummyClassLoader();
        Class<?> c = Class.forName("TestClass", true, dummyloader);
        if (c.getClassLoader() != dummyloader) {
            Assert.fail("TestClass defined by wrong classloader: " + c.getClassLoader());
        }

        OutputAnalyzer output = executor.execute("VM.classloader_stats");
        Iterator<String> lines = output.asLines().iterator();
        while (lines.hasNext()) {
            String line = lines.next();
            Matcher m = clLine.matcher(line);
            if (m.matches()) {
                // verify that DummyClassLoader has loaded 1 class and 1 anonymous class
                if (m.group(4).equals("ClassLoaderStatsTest$DummyClassLoader")) {
                    System.out.println("line: " + line);
                    if (!m.group(1).equals("1")) {
                        Assert.fail("Should have loaded 1 class: " + line);
                    }
                    checkPositiveInt(m.group(2));
                    checkPositiveInt(m.group(3));

                    String next = lines.next();
                    System.out.println("next: " + next);
                    Matcher m1 = anonLine.matcher(next);
                    m1.matches();
                    if (!m1.group(1).equals("1")) {
                        Assert.fail("Should have loaded 1 anonymous class, but found : " + m1.group(1));
                    }
                    checkPositiveInt(m1.group(2));
                    checkPositiveInt(m1.group(3));
                }
            }
        }
    }

    private static void checkPositiveInt(String s) {
        if (Integer.parseInt(s) <= 0) {
            Assert.fail("Value should have been > 0: " + s);
        }
    }

    public static class DummyClassLoader extends ClassLoader {

        public static final String CLASS_NAME = "TestClass";

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
            if (!"TestClass".equals(name)) {
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
            if (!"TestClass".equals(name)) {
                throw new ClassNotFoundException("Unexpected class: " + name);
            }
            return defineClass(name, readClassFile(name + ".class"), null);
        }
    } /* DummyClassLoader */

    @Test
    public void jmx() throws ClassNotFoundException {
        run(new JMXExecutor());
    }
}

class TestClass {
    static {
        // force creation of anonymous class (for the lambdaform)
        Runnable r = () -> System.out.println("Hello");
        r.run();
    }
}
