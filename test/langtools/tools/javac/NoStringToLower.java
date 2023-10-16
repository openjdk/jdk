/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8029800
 * @summary String.toLowerCase()/toUpperCase is generally dangerous, check it is not used in langtools
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 */

import java.io.*;
import java.util.*;
import javax.tools.*;
import jdk.internal.classfile.*;
import jdk.internal.classfile.constantpool.*;

public class NoStringToLower {
    public static void main(String... args) throws Exception {
        NoStringToLower c = new NoStringToLower();
        if (c.run(args))
            return;

        if (is_jtreg())
            throw new Exception(c.errors + " errors occurred");
        else
            System.exit(1);
    }

    static boolean is_jtreg() {
        return (System.getProperty("test.src") != null);
    }

    /**
     * Main entry point.
     */
    boolean run(String... args) throws Exception {
        JavaCompiler c = ToolProvider.getSystemJavaCompiler();
        try (JavaFileManager fm = c.getStandardFileManager(null, null, null)) {
            JavaFileManager.Location javacLoc = findJavacLocation(fm);
            String[] pkgs = {
                "javax.annotation.processing",
                "javax.lang.model",
                "javax.tools",
                "com.sun.source",
                "jdk.internal.classfile",
                "com.sun.tools.doclint",
                "com.sun.tools.javac",
                "com.sun.tools.javah",
                "com.sun.tools.javap",
                "com.sun.tools.jdeps",
                "jdk.javadoc"
            };
            for (String pkg: pkgs) {
                for (JavaFileObject fo: fm.list(javacLoc,
                        pkg, EnumSet.of(JavaFileObject.Kind.CLASS), true)) {
                    scan(fo);
                }
            }

            return (errors == 0);
        }
    }

    // depending on how the test is run, javac may be on bootclasspath or classpath
    JavaFileManager.Location findJavacLocation(JavaFileManager fm) {
        JavaFileManager.Location[] locns =
            { StandardLocation.PLATFORM_CLASS_PATH, StandardLocation.CLASS_PATH };
        try {
            for (JavaFileManager.Location l: locns) {
                JavaFileObject fo = fm.getJavaFileForInput(l,
                    "com.sun.tools.javac.Main", JavaFileObject.Kind.CLASS);
                if (fo != null)
                    return l;
            }
        } catch (IOException e) {
            throw new Error(e);
        }
        throw new IllegalStateException("Cannot find javac");
    }

    /**
     * Verify there are no references to String.toLowerCase() in a class file.
     */
    void scan(JavaFileObject fo) throws IOException {
        try (InputStream in = fo.openInputStream()) {
            ClassModel cf = Classfile.of().parse(in.readAllBytes());
            for (PoolEntry pe : cf.constantPool()) {
                if (pe instanceof MethodRefEntry ref) {
                    String methodDesc = ref.owner().name().stringValue() + "." + ref.name().stringValue() + ":" + ref.type().stringValue();

                    if ("java/lang/String.toLowerCase:()Ljava/lang/String;".equals(methodDesc)) {
                        error("found reference to String.toLowerCase() in: " + fo.getName());
                    }
                    if ("java/lang/String.toUpperCase:()Ljava/lang/String;".equals(methodDesc)) {
                        error("found reference to String.toLowerCase() in: " + fo.getName());
                    }
                }
            }
        } catch (ConstantPoolException ignore) {
        }
    }

    /**
     * Report an error.
     */
    void error(String msg) {
        System.err.println("Error: " + msg);
        errors++;
    }

    int errors;
}
