/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.SourceFile_attribute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.JavaFileObject;

public class SourceFileTestBase extends TestBase {

    protected void test(Class<?> classToTest, String fileName) throws Exception {
        assertAttributePresent(ClassFile.read(getClassFile(classToTest)), fileName);
    }

    protected void test(String classToTest, String fileName) throws Exception {
        assertAttributePresent(ClassFile.read(getClassFile(classToTest + ".class")), fileName);
    }

    /**
     * Compile sourceCode and for all "classesToTest" checks SourceFile attribute.
     */
    protected void compileAndTest(String sourceCode, String... classesToTest) throws Exception {

        Map<String, ? extends JavaFileObject> classes = compile(sourceCode);
        String fileName = ToolBox.getJavaFileNameFromSource(sourceCode);
        for (String className : classesToTest) {
            assertAttributePresent(ClassFile.read(classes.get(className).openInputStream()), fileName);
        }
    }

    private void assertAttributePresent(ClassFile classFile, String fileName) throws Exception {

        //We need to count attributes with the same names because there is no appropriate API in the ClassFile.

        List<SourceFile_attribute> sourceFileAttributes = new ArrayList<>();
        for (Attribute a : classFile.attributes.attrs) {
            if (Attribute.SourceFile.equals(a.getName(classFile.constant_pool))) {
                sourceFileAttributes.add((SourceFile_attribute) a);
            }
        }

        assertEquals(sourceFileAttributes.size(), 1, "Should be the only SourceFile attribute");

        SourceFile_attribute attribute = sourceFileAttributes.get(0);

        assertEquals(classFile.constant_pool.getUTF8Info(attribute.attribute_name_index).value,
                Attribute.SourceFile, "Incorrect attribute name");
        assertEquals(classFile.constant_pool.getUTF8Info(attribute.sourcefile_index).value, fileName,
                "Incorrect source file name");
        assertEquals(attribute.attribute_length, 2, "Incorrect attribute length");
    }
}
