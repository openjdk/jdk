/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
 * @test
 * @bug 6982312
 * @summary tests various classfile format and attribute handling by pack200
 * @compile -XDignore.symbol.file Utils.java AttributeTests.java
 * @run main AttributeTests
 * @author ksrini
 */


public class AttributeTests {

    public static void main(String... args) throws Exception {
        test6982312();
    }
    /*
     * This is an interim test, which ensures pack200 handles JSR-292 related
     * classfile changes seamlessly, until all the classfile changes in jdk7
     * and jdk8 are fully supported. At that time this test should be jettisoned,
     * along with the associated jar file.
     *
     * The jar file  contains sources and classes noting the classes were
     * derived by using the javac from the lambda project,
     * see http://openjdk.java.net/projects/lambda/.
     * Therefore the classes contained in the jar cannot be compiled, using
     * the standard jdk7's javac compiler.
     */
    static void test6982312() throws IOException {
        String pack200Cmd = Utils.getPack200Cmd();
        File dynJar = new File(".", "dyn.jar");
        Utils.copyFile(new File(Utils.TEST_SRC_DIR, "dyn.jar"), dynJar);
        File testJar = new File(".", "test.jar");
        List<String> cmds = new ArrayList<String>();
        cmds.add(pack200Cmd);
        cmds.add("--repack");
        cmds.add(testJar.getAbsolutePath());
        cmds.add(dynJar.getAbsolutePath());
        Utils.runExec(cmds);
        /*
         * compare the repacked jar bit-wise, as all the files
         * should be transmitted "as-is".
         */
        Utils.doCompareBitWise(dynJar.getAbsoluteFile(), testJar.getAbsoluteFile());
        testJar.delete();
        dynJar.delete();
    }
}
