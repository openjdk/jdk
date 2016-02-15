/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8147801
 * @summary java.nio.file.ClosedFileSystemException when using Javadoc API's in JDK9
 * @modules jdk.javadoc/com.sun.tools.javadoc
 * @library jarsrc
 * @build lib.* p.*
 * @run main T8147801
 */

import java.io.IOException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.RootDoc;

/*
 * This test verifies the use of the hidden fileManager.deferClose
 * option, to work around the limitation that javadoc objects
 * (like RootDoc and related types) should cannot normally be used
 * after javadoc exits, closing its file manager (if it opened it.)
 *
 * The test runs javadoc on a chain of classes, 1 in source form,
 * and 2 in a jar file. javadoc/javac will "complete" classes found
 * in source, but will eagerly "classes" in class form.
 * The chain is p/Test.java -> lib/Lib1.class -> lib/Lib2.class.
 * After javadoc exits, the classes are examined, to finally force
 * the classes to be completed, possibly causing javac to try and access
 * references into a .jar file system which has now been closed.
 *
 * The test runs two test cases -- one without the workaround option,
 * to test the validity of the test case, and one with the workaround
 * option, to test that it works as expected.
 */
public class T8147801 {
    public static void main(String... args) throws Exception {
        new T8147801().run();
    }

    void run() throws Exception {
        initJar();
        test(false);
        test(true);
        if (errors > 0) {
            throw new Exception(errors + " errors occurred");
        }
    }

    void test(boolean withOption) {
        System.err.println("Testing " + (withOption ? "with" : "without") + " option");
        try {
            RootDoc root = getRootDoc(withOption);
            for (ClassDoc cd: root.specifiedClasses()) {
                dump("", cd);
            }
            if (!withOption) {
                error("expected option did not occur");
            }
        } catch (ClosedFileSystemException e) {
            if (withOption) {
                error("Unexpected exception: " + e);
            } else {
                System.err.println("Exception received as expected: " + e);
            }
        }
        System.err.println();
    }

    RootDoc getRootDoc(boolean withOption) {
        List<String> opts = new ArrayList<>();
        if (withOption)
            opts.add("-XDfileManager.deferClose=10");
        opts.add("-doclet");
        opts.add(getClass().getName());
        opts.add("-classpath");
        opts.add(jarPath.toString());
        opts.add(Paths.get(System.getProperty("test.src"), "p", "Test.java").toString());
        System.err.println("javadoc opts: " + opts);
        int rc = com.sun.tools.javadoc.Main.execute(
                "javadoc",
                // by specifying our own class loader, we get the same Class instance as this
                getClass().getClassLoader(),
                opts.toArray(new String[opts.size()]));
        if (rc != 0) {
            error("unexpected exit from javadoc or doclet: " + rc);
        }
        return cachedRoot;
    }

    void dump(String prefix, ClassDoc cd) {
        System.err.println(prefix + "class: " + cd);
        for (FieldDoc fd: cd.fields()) {
            System.err.println(fd);
            if (fd.type().asClassDoc() != null) {
                dump(prefix + "  ", fd.type().asClassDoc());
            }
        }
    }

    void initJar() throws IOException {
        Path testClasses = Paths.get(System.getProperty("test.classes"));
        jarPath = Paths.get("lib.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarPath))) {
            String[] classNames = {"Lib1.class", "Lib2.class"};
            for (String cn : classNames) {
                out.putNextEntry(new JarEntry("lib/" + cn));
                Path libClass = testClasses.resolve("jarsrc").resolve("lib").resolve(cn);
                out.write(Files.readAllBytes(libClass));
            }
        }
    }

    void error(String msg) {
        System.err.println("Error: " + msg);
        errors++;
    }

    Path jarPath;
    int errors;

    // Bad doclet caches the RootDoc for later use

    static RootDoc cachedRoot;

    public static boolean start(RootDoc root) {
        cachedRoot = root;
        return true;
    }
}
