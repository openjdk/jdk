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
 * @bug 8011181
 * @summary javac, empty UTF8 entry generated for inner class
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          jdk.compiler/com.sun.tools.javac.util
 */

import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sun.tools.javac.util.Assert;
import jdk.internal.classfile.*;
import jdk.internal.classfile.constantpool.*;

public class EmptyUTF8ForInnerClassNameTest {

    public static void main(String[] args) throws Exception {
        new EmptyUTF8ForInnerClassNameTest().run();
    }

    void run() throws Exception {
        checkClassFile(Paths.get(System.getProperty("test.classes"),
                this.getClass().getName() + "$1.class"));
        checkClassFile(Paths.get(System.getProperty("test.classes"),
                this.getClass().getName() + "$EnumPlusSwitch.class"));
    }

    void checkClassFile(final Path path) throws Exception {
        ClassModel classFile = Classfile.of().parse(
                new BufferedInputStream(Files.newInputStream(path)).readAllBytes());
        for (PoolEntry pe : classFile.constantPool()) {
            if (pe instanceof Utf8Entry utf8Info) {
                Assert.check(utf8Info.stringValue().length() > 0,
                        "UTF8 with length 0 found at class " + classFile.thisClass().name());
            }
        }
    }

    static class EnumPlusSwitch {

        public int m (Thread.State e) {
            switch (e) {
                case NEW:
                    return 0;
            }
            return -1;
        }
    }

}
