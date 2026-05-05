/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6173575
 * @summary Unit tests for appendToBootstrapClassLoaderSearch and
 *   appendToSystemClassLoaderSearch methods.
 *
 * @library /test/lib
 * @build ClassUnloadTest
 * @run driver jdk.test.lib.util.JavaAgentBuilder ClassUnloadTest ClassUnloadTest.jar
 * @run main/othervm -Xlog:class+unload -javaagent:ClassUnloadTest.jar ClassUnloadTest
 */

import java.lang.instrument.Instrumentation;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.util.JarUtils;

public class ClassUnloadTest {

    static Instrumentation ins;

    public static void main(String args[]) throws Exception {
        // Setup: create Foo and Bar in a temp directory
        Path otherDir = Path.of("other");
        Files.createDirectories(otherDir);

        // Create Foo.java referencing Bar
        Files.writeString(otherDir.resolve("Foo.java"),
            "public class Foo {\n" +
            "    public static boolean doSomething() {\n" +
            "        try {\n" +
            "            Bar b = new Bar();\n" +
            "            return true;\n" +
            "        } catch (NoClassDefFoundError x) {\n" +
            "            return false;\n" +
            "        }\n" +
            "    }\n" +
            "}\n");

        Files.writeString(otherDir.resolve("Bar.java"),
            "public class Bar { }\n");

        // Compile both
        CompilerUtils.compile(otherDir, otherDir);

        // Create Bar.jar, then delete Bar.class so Foo can't find it
        JarUtils.createJarFile(otherDir.resolve("Bar.jar"), otherDir, Path.of("Bar.class"));
        Files.delete(otherDir.resolve("Bar.class"));

        String dir = otherDir.toString() + File.separator;
        String jar = dir + "Bar.jar";
        System.out.println(jar);

        URL u = otherDir.toUri().toURL();
        URL urls[] = { u };

        // This should fail as Bar is not available
        Invoker i1 = new Invoker(urls, "Foo", "doSomething");
        Boolean result = (Boolean)i1.invoke((Object)null);
        if (result.booleanValue()) {
            throw new RuntimeException("Test configuration error - doSomething should not succeed");
        }

        // put Bar on the system class path
        ins.appendToSystemClassLoaderSearch( new JarFile(jar) );

        // This should fail even though Bar is now available
        result = (Boolean)i1.invoke((Object)null);
        if (result.booleanValue()) {
            throw new RuntimeException("Test configuration error - doSomething should not succeed");
        }

        // This should succeed because this is a different Foo
        Invoker i2 = new Invoker(urls, "Foo", "doSomething");
        result = (Boolean)i2.invoke((Object)null);
        if (!result.booleanValue()) {
            throw new RuntimeException("Test configuration error - doSomething did not succeed");
        }

        // Exercise some class unloading
        i1 = i2 = null;
        System.gc();
    }

    static class Invoker {
        URLClassLoader cl;
        Method m;

        public Invoker(URL urls[], String cn, String mn, Class ... params)
            throws ClassNotFoundException, NoSuchMethodException
        {
            cl = new URLClassLoader(urls);
            Class c = Class.forName("Foo", true, cl);
            m = c.getDeclaredMethod(mn, params);
        }

        public Object invoke(Object ... args)
            throws IllegalAccessException, InvocationTargetException
        {
            return m.invoke(args);
        }
    }

    public static void premain(String args, Instrumentation i) {
        ins = i;
    }
}
