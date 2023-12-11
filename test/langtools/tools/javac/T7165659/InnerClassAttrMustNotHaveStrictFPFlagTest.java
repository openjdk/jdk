/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7165659
 * @summary javac incorrectly sets strictfp access flag on inner-classes
 * @enablePreview
 * @modules jdk.compiler/com.sun.tools.javac.util
 */

import java.io.File;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import com.sun.tools.javac.util.Assert;

public class InnerClassAttrMustNotHaveStrictFPFlagTest {

    public static void main(String[] args) throws Exception {
        new InnerClassAttrMustNotHaveStrictFPFlagTest().run();
    }

    private void run() throws Exception {
        File classPath = new File(System.getProperty("test.classes"), getClass().getSimpleName() + ".class");
        analyzeClassFile(classPath);
    }

    void analyzeClassFile(File path) throws Exception {
        ClassModel classFile = ClassFile.of().parse(path.toPath());
        InnerClassesAttribute innerClasses = classFile.findAttribute(Attributes.INNER_CLASSES).orElse(null);
        assert innerClasses != null;
        for (InnerClassInfo classInfo : innerClasses.classes()) {
            Assert.check(classInfo.flagsMask() != ClassFile.ACC_STRICT,
                    "Inner classes attribute must not have the ACC_STRICT flag set");
        }
    }

    strictfp void m() {
        new Runnable() {
            @Override
            public void run() {}
        };
    }

    static strictfp class Strict extends InnerClassAttrMustNotHaveStrictFPFlagTest {}

}
