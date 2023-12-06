/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8080878
 * @summary Checking ACC_MODULE flag is generated for module-info.
 * @library /tools/lib
 * @enablePreview
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.ToolBox
 * @run main ModuleFlagTest
 */

import java.lang.classfile.AccessFlags;
import java.lang.classfile.ClassFile;
import java.lang.reflect.AccessFlag;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.JavacTask;
import toolbox.ToolBox;

public class ModuleFlagTest {
    public static void main(String[] args) throws IOException {
        Path outdir = Paths.get(".");
        ToolBox tb = new ToolBox();
        final Path moduleInfo = Paths.get("module-info.java");
        tb.writeFile(moduleInfo, "module test_module{}");
        new JavacTask(tb)
                .outdir(outdir)
                .files(moduleInfo)
                .run();

        AccessFlags accessFlags = ClassFile.of().parse(outdir.resolve("module-info.class"))
                .flags();
        if (!accessFlags.has(AccessFlag.MODULE)) {
            throw new RuntimeException("ClassFile doesn't have module access flag");
        }
    }
}
