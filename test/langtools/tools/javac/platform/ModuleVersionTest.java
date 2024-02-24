/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8318913
 * @summary Verify correct module versions are recorded when --release is used.
 * @library /tools/lib
 * @enablePreview
 * @modules
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.platform
 *          jdk.compiler/com.sun.tools.javac.util:+open
 * @run junit ModuleVersionTest
 */


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.ModuleRequireInfo;

import org.junit.Test;

import toolbox.JavacTask;
import toolbox.ToolBox;

import static org.junit.Assert.*;
public class ModuleVersionTest {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^([0-9]+)(.[0-9]+)*(-.*)?");

    @Test
    public void testVersionInDependency() throws Exception {
        doTestVersionInDependency("11", "11");

        String expectedVersion = System.getProperty("java.version");
        Matcher m = VERSION_PATTERN.matcher(expectedVersion);

        if (m.find()) {
            String preRelease = m.group(3);

            expectedVersion = m.group(1);

            if (preRelease != null) {
                expectedVersion += preRelease;
            }
        }

        doTestVersionInDependency(System.getProperty("java.specification.version"), expectedVersion);
    }

    private void doTestVersionInDependency(String specificationVersion,
                                           String expectedVersion) throws Exception {
        Path root = Paths.get(".");
        Path classes = root.resolve("classes");
        Files.createDirectories(classes);
        ToolBox tb = new ToolBox();

        new JavacTask(tb)
            .outdir(classes)
            .options("--release", specificationVersion)
            .sources("""
                     module test {}
                     """,
                     """
                     package test;
                     public class Test {
                     }
                     """)
            .run()
            .writeAll();

        Path moduleInfo = classes.resolve("module-info.class");
        ClassModel clazz = ClassFile.of().parse(moduleInfo);

        assertTrue(clazz.isModuleInfo());
        ModuleAttribute module = clazz.findAttribute(Attributes.MODULE).get();
        ModuleRequireInfo req = module.requires().get(0);
        assertEquals("java.base", req.requires().name().stringValue());
        assertEquals(expectedVersion, req.requiresVersion().get().stringValue());
    }

}
