/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import tests.JImageGenerator;

import java.io.BufferedOutputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.lang.classfile.ClassFile.ACC_MANDATED;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_void;

/*
 * @test
 * @summary Make sure that ~20000 packages in a uber jar can be linked using jlink. Now that
 *          pagination is in place, the limitation is on the constant pool size, not number
 *          of packages.
 * @bug 8321413
 * @library ../lib /test/lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.*
 * @run main/othervm -Xlog:init=debug -XX:+UnlockDiagnosticVMOptions -XX:+BytecodeVerificationLocal JLink20000Packages
 */
public class JLink20000Packages {
    private static final ClassDesc CD_System = ClassDesc.of("java.lang.System");
    private static final ClassDesc CD_PrintStream = ClassDesc.of("java.io.PrintStream");
    private static final MethodTypeDesc MTD_void_String = MethodTypeDesc.of(CD_void, CD_String);

    public static void main(String[] args) throws Exception {
        String moduleName = "bug8321413x";
        Path src = Paths.get(moduleName);
        Files.createDirectories(src);
        Path jarPath = src.resolve(moduleName +".jar");
        Path imageDir = src.resolve("out-jlink");

        // Generate module with 20000 classes in unique packages
        try (JarOutputStream out = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jarPath)))) {
            Set<String> packageNames = new HashSet<>();
            for (int i = 0; i < 20_000; i++) {
                String packageName = "p" + i;
                packageNames.add(packageName);

                // Generate a class file for this package
                String className = "C" + i;
                byte[] classData = ClassFile.of().build(ClassDesc.of(packageName, className), cb -> {});
                out.putNextEntry(new JarEntry(packageName + "/" + className +".class"));
                out.write(classData);
            }

            // Write the main class
            out.putNextEntry(new JarEntry("testpackage/JLink20000PackagesTest.class"));
            out.write(generateMainClass());
            packageNames.add("testpackage");

            // Write the module descriptor
            byte[] moduleInfo = ClassFile.of().buildModule(ModuleAttribute.of(
                    ModuleDesc.of(moduleName), mab -> {
                        mab.requires(ModuleDesc.of("java.base"), ACC_MANDATED, null);
                        packageNames.forEach(pkgName -> mab.exports(PackageDesc.of(pkgName), 0));
                    }));
            out.putNextEntry(new JarEntry("module-info.class"));
            out.write(moduleInfo);
        }

        JImageGenerator.getJLinkTask()
                .output(imageDir)
                .addJars(jarPath)
                .addMods(moduleName)
                .call()
                .assertSuccess();

        Path binDir = imageDir.resolve("bin").toAbsolutePath();
        Path bin = binDir.resolve("java");

        ProcessBuilder processBuilder = new ProcessBuilder(bin.toString(),
                "-XX:+UnlockDiagnosticVMOptions",
                // Option is useful to verify build image
                "-XX:+BytecodeVerificationLocal",
                "-m", moduleName + "/testpackage.JLink20000PackagesTest");
        processBuilder.inheritIO();
        processBuilder.directory(binDir.toFile());
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0)
             throw new AssertionError("JLink20000PackagesTest failed to launch");
    }

    /**
     * Generate test class with main() does
     * System.out.println("JLink20000PackagesTest started.");
     */
    private static byte[] generateMainClass() {
        return ClassFile.of().build(ClassDesc.of("testpackage", "JLink20000PackagesTest"),
                cb -> {
                    cb.withMethod("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()),
                            ACC_PUBLIC | ACC_STATIC, mb -> {
                                mb.withCode(cob -> cob.getstatic(CD_System, "out", CD_PrintStream)
                                        .ldc("JLink20000PackagesTest started.")
                                        .invokevirtual(CD_PrintStream, "println", MTD_void_String)
                                        .return_()
                                );
                            });
                });
    }
}
