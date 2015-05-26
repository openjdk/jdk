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

/**
 * @test
 * @bug 8067445
 * @summary Verify that file.Locations analyze sun.boot.class.path for BCP prepends/appends
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.main
 */

import java.io.IOException;
import java.util.EnumSet;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

public class BootClassPathPrepend {
    public static void main(String... args) throws IOException {
        if (args.length == 0) {
            new BootClassPathPrepend().reRun();
        } else {
            new BootClassPathPrepend().run();
        }
    }

    void reRun() {
        String testClasses = System.getProperty("test.classes");
        ToolBox tb = new ToolBox();
        tb.new JavaTask().vmOptions("-Xbootclasspath/p:" + testClasses)
                         .classArgs("real-run")
                         .className("BootClassPathPrepend")
                         .run()
                         .writeAll();
    }

    EnumSet<Kind> classKind = EnumSet.of(JavaFileObject.Kind.CLASS);

    void run() throws IOException {
        JavaCompiler toolProvider = ToolProvider.getSystemJavaCompiler();
        try (JavaFileManager fm = toolProvider.getStandardFileManager(null, null, null)) {
            Iterable<JavaFileObject> files =
                    fm.list(StandardLocation.PLATFORM_CLASS_PATH, "", classKind, false);
            for (JavaFileObject fo : files) {
                if (fo.isNameCompatible("BootClassPathPrepend", JavaFileObject.Kind.CLASS)) {
                    System.err.println("Found BootClassPathPrepend on bootclasspath");
                    return ;//found
                }
            }

            throw new AssertionError("Cannot find class that was prepended on BCP");
        }
    }
}
