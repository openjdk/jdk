/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8276036 8277213 8277441
 * @summary test for the value of full_count in the message of insufficient codecache
 * @requires vm.compMode != "Xint"
 * @library /test/lib
 */
public class CodeCacheFullCountTest {
    public static void main(String args[]) throws Throwable {
        if (args.length == 1) {
            wasteCodeCache();
        } else {
            runTest();
        }
    }

    public static void wasteCodeCache()  throws Exception {
        URL url = CodeCacheFullCountTest.class.getProtectionDomain().getCodeSource().getLocation();

        for (int i = 0; i < 500; i++) {
            ClassLoader cl = new MyClassLoader(url);
            refClass(cl.loadClass("SomeClass"));
        }
    }

    public static void runTest() throws Throwable {
        ProcessBuilder pb = ProcessTools.createTestJvm(
          "-XX:ReservedCodeCacheSize=2496k", "-XX:-UseCodeCacheFlushing", "-XX:-MethodFlushing", "CodeCacheFullCountTest", "WasteCodeCache");
        OutputAnalyzer oa = ProcessTools.executeProcess(pb);
        // Ignore adapter creation failures
        if (oa.getExitValue() != 0 && !oa.getStderr().contains("Out of space in CodeCache for adapters")) {
            oa.reportDiagnosticSummary();
            throw new RuntimeException("VM finished with exit code " + oa.getExitValue());
        }
        String stdout = oa.getStdout();

        Pattern pattern = Pattern.compile("full_count=(\\d)");
        Matcher stdoutMatcher = pattern.matcher(stdout);
        if (stdoutMatcher.find()) {
            int fullCount = Integer.parseInt(stdoutMatcher.group(1));
            if (fullCount == 0) {
                throw new RuntimeException("the value of full_count is wrong.");
            }
        } else {
            throw new RuntimeException("codecache shortage did not occur.");
        }
    }

    private static void refClass(Class clazz) throws Exception {
        Field name = clazz.getDeclaredField("NAME");
        name.setAccessible(true);
        name.get(null);
    }

    private static class MyClassLoader extends URLClassLoader {
        public MyClassLoader(URL url) {
            super(new URL[]{url}, null);
        }
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            try {
                return super.loadClass(name, resolve);
            } catch (ClassNotFoundException e) {
                return Class.forName(name, resolve, CodeCacheFullCountTest.class.getClassLoader());
            }
        }
    }
}

abstract class Foo {
    public abstract int foo();
}

class Foo1 extends Foo {
    private int a;
    public int foo() { return a; }
}

class Foo2 extends Foo {
    private int a;
    public int foo() { return a; }
}

class Foo3 extends Foo {
    private int a;
    public int foo() { return a; }
}

class Foo4 extends Foo {
    private int a;
    public int foo() { return a; }
}

class SomeClass {
    static final String NAME = "name";

    static {
        int res =0;
        Foo[] foos = new Foo[] { new Foo1(), new Foo2(), new Foo3(), new Foo4() };
        for (int i = 0; i < 100000; i++) {
            res = foos[i % foos.length].foo();
        }
    }
}
