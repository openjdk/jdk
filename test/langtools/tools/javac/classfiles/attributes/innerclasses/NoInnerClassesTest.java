/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8042251
 * @summary Test that there are no inner classes attributes in case of there are no inner classes.
 * @library /tools/lib /tools/javac/lib ../lib
 * @enablePreview
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox InMemoryFileManager TestBase
 * @run main NoInnerClassesTest
 */

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;

import java.io.IOException;

public class NoInnerClassesTest extends TestBase {

    public static void main(String[] args) throws IOException {
        new NoInnerClassesTest().test();
    }

    public void test() throws IOException {
        ClassModel classModel = readClassFile("NoInnerClassesTest");
        assertNull(classModel.findAttribute(Attributes.innerClasses()).orElse(null), "Found inner class attribute");
    }
}
