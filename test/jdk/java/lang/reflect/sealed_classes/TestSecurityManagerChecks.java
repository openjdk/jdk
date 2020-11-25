/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8246778
 * @summary Test that security checks occur for getPermittedSubclasses
 * @library /test/lib
 * @modules java.compiler
 * @build jdk.test.lib.compiler.CompilerUtils jdk.test.lib.compiler.ModuleInfoMaker TestSecurityManagerChecks
 * @run main/othervm --enable-preview TestSecurityManagerChecks
 */

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import jdk.test.lib.compiler.*;

public class TestSecurityManagerChecks {

    private static final ClassLoader OBJECT_CL = Object.class.getClassLoader();

    public static void main(String[] args) throws Throwable {
        Path classes = compile();
        URL testLocation = TestSecurityManagerChecks.class
                                                    .getProtectionDomain()
                                                    .getCodeSource()
                                                    .getLocation();

        //need to use a different ClassLoader to run the test, so that the checks are performed:
        ClassLoader testCL = new URLClassLoader(new URL[] {testLocation}, OBJECT_CL);
        testCL.loadClass("TestSecurityManagerChecks")
              .getDeclaredMethod("run", Path.class)
               .invoke(null, classes);
    }

    public static void run(Path classes) throws Throwable {
        Configuration testConfig = ModuleLayer.boot()
                                              .configuration()
                                              .resolve(ModuleFinder.of(),
                                                       ModuleFinder.of(classes),
                                                       List.of("test"));
        ModuleLayer testLayer = ModuleLayer.boot()
                                           .defineModulesWithOneLoader(testConfig,
                                                                         OBJECT_CL);

        // First get hold of the target classes before we enable security
        Class<?> sealed = testLayer.findLoader("test").loadClass("test.Base");

        //try without a SecurityManager:
        Class<?>[] subclasses = sealed.getPermittedSubclasses();

        if (subclasses.length != 2) {
            throw new AssertionError("Incorrect permitted subclasses: " +
                                       Arrays.asList(subclasses));
        }

        System.out.println("OK - getPermittedSubclasses for " + sealed.getName() +
                           " got result: " + Arrays.asList(subclasses));

        String[] denyPackageAccess = new String[1];

        //try with a SecurityManager:
        SecurityManager sm = new SecurityManager() {
            @Override
            public void checkPackageAccess(String pkg) {
                if (Objects.equals(denyPackageAccess[0], pkg)) {
                    throw new SecurityException();
                }
            }
        };

        System.setSecurityManager(sm);

        denyPackageAccess[0] = "test";

        //should pass - does not return a class from package "test":
        sealed.getPermittedSubclasses();

        denyPackageAccess[0] = "test.a";

        try {
            sealed.getPermittedSubclasses();
            throw new Error("getPermittedSubclasses incorrectly succeeded for " +
                             sealed.getName());
        } catch (SecurityException e) {
            System.out.println("OK - getPermittedSubclasses for " + sealed.getName() +
                               " got expected exception: " + e);
        }
    }

    private static Path compile() throws IOException {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        ModuleInfoMaker maker = new ModuleInfoMaker(src);
        maker.writeJavaFiles("test",
                              "module test {}",
                              "package test; public sealed interface Base permits test.a.ImplA, test.b.ImplB, test.c.ImplC {}",
                              "package test.a; public final class ImplA implements test.Base {}",
                              "package test.b; public final class ImplB implements test.Base {}",
                              "package test.c; public final class ImplC implements test.Base {}"
                              );

        if (!CompilerUtils.compile(src, classes.resolve("test"), "--enable-preview", "-source", System.getProperty("java.specification.version"))) {
            throw new AssertionError("Compilation didn't succeed!");
        }

        Files.delete(classes.resolve("test").resolve("test").resolve("c").resolve("ImplC.class"));

        return classes;
    }
}
