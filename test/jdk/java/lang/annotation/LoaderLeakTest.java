/*
 * Copyright (c) 2004, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5040740
 * @summary annotations cause memory leak
 * @library /test/lib
 * @build jdk.test.lib.*
 * @run junit LoaderLeakTest
 */

import java.lang.annotation.Retention;
import java.lang.ref.WeakReference;
import java.nio.file.*;
import java.util.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.ForceGC;
import org.junit.jupiter.api.Test;

public class LoaderLeakTest {

    @Test
    public void testWithoutReadingAnnotations() throws Throwable {
        runJavaProcessExpectSuccessExitCode("Main");
    }

    @Test
    public void testWithReadingAnnotations() throws Throwable {
        runJavaProcessExpectSuccessExitCode("Main",  "foo");
    }

    private void runJavaProcessExpectSuccessExitCode(String ... command) throws Throwable {
        var processBuilder = ProcessTools.createTestJavaProcessBuilder(command)
                                                      .directory(Paths.get(Utils.TEST_CLASSES).toFile());
        ProcessTools.executeCommand(processBuilder).shouldHaveExitValue(0);
    }

}

class Main {

    public static void main(String[] args) throws Exception {
        doTest(args.length != 0);
    }

    static void doTest(boolean readAnn) throws Exception {
        ClassLoader loader = new SimpleClassLoader();
        var loaderRef = new WeakReference<>(loader);
        var c = new WeakReference<Class<?>>(loader.loadClass("C"));
        // c.get() should never return null here since we hold a strong
        // reference to the class loader that loaded the class referred by c.
        if (c.get().getClassLoader() != loader)
            throw new AssertionError("wrong classloader");
        if (readAnn)
            System.out.println(c.get().getAnnotations()[0]);
        loader = null;

        if (!ForceGC.wait(() -> loaderRef.refersTo(null) && c.refersTo(null))) {
            Asserts.fail("loader and C not unloaded");
        }
    }
}

@Retention(RUNTIME)
@interface A {
    B b();
}

@interface B { }

@A(b=@B()) class C { }

class SimpleClassLoader extends ClassLoader {
    public SimpleClassLoader() { }

    private byte[] getClassImplFromDataBase(String className) {
        try {
            return Files.readAllBytes(Paths.get(className + ".class"));
        } catch (Exception e) {
            throw new Error("could not load class " + className, e);
        }
    }

    @Override
    public Class<?> loadClass(String className, boolean resolveIt)
            throws ClassNotFoundException {
        switch (className) {
            case "A", "B", "C" -> {
                var classData = getClassImplFromDataBase(className);
                return defineClass(className, classData, 0, classData.length);
            }
        }
        return super.loadClass(className, resolveIt);
    }

}
