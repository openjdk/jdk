/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8388319
 * @summary Unset field should be printed for uninitializedThis in stack only
 * @library /tools/lib /test/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.JavapTask
 * @run junit ${test.main.class}
 */

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;

import jdk.test.lib.helpers.ClassFileInstaller;
import org.junit.jupiter.api.Test;
import toolbox.JavapTask;
import toolbox.Task;
import toolbox.ToolBox;

import static java.lang.classfile.ClassFile.*;
import static java.lang.classfile.ClassFile.ACC_IDENTITY;
import static java.lang.classfile.ClassFile.ACC_STRICT_INIT;
import static java.lang.constant.ConstantDescs.*;
import static org.junit.jupiter.api.Assertions.fail;

public class UnsetFieldWithStackUninitTest {
    @Test
    void test() throws Exception {
        ClassDesc testDesc = ClassDesc.of("Test");
        var bytes = ClassFile.of().build(testDesc, clb -> clb
                .withVersion(latestMajorVersion(), PREVIEW_MINOR_VERSION)
                .withFlags(ACC_PUBLIC | ACC_IDENTITY)
                .withField("f", CD_int, ACC_STRICT_INIT)
                .withMethodBody(INIT_NAME, MTD_void, 0, cob -> {
                    cob.aload(0) // stack for invokespecial
                            .dup() // stack for putfield
                            .iconst_4()
                            .iconst_m1() // stack for astore
                            .istore(0) // nuke uninitializedThis from locals
                            .iconst_3(); // stack for branch
                    var elseLabel = cob.newLabel();
                    var endIfLabel = cob.newLabel();
                    cob.ifeq(elseLabel)
                            .putfield(testDesc, "f", CD_int)
                            .goto_(endIfLabel)
                            .labelBinding(elseLabel)
                            .putfield(testDesc, "f", CD_int)
                            .labelBinding(endIfLabel)
                            .invokespecial(CD_Object, INIT_NAME, MTD_void)
                            .return_();
                }));
        ClassFileInstaller.writeClassToDisk("Test", bytes);

        String golden = """
                public class Test {
                  int f;

                  Test();
                    Code:
                         0: aload_0
                         1: dup
                         2: iconst_4
                         3: iconst_m1
                         4: istore_0
                         5: iconst_3
                         6: ifeq          15
                         9: putfield      #8                  // Field f:I
                        12: goto          18
                      StackMap locals:  int
                      StackMap stack:  uninit_this uninit_this int
                      StackMap unset fields: f:I
                        15: putfield      #8                  // Field f:I
                      StackMap locals:  int
                      StackMap stack:  uninit_this
                      StackMap unset fields:
                        18: invokespecial #12                 // Method java/lang/Object."<init>":()V
                        21: return
                }
                """;

        String out = new JavapTask(new ToolBox())
                .options("-c", "-XDdetails:stackMaps")
                .classes("Test.class")
                .run()
                .getOutput(Task.OutputKind.DIRECT);
        if (!golden.equals(out)) {
            fail("Unexpected output:\n" + out);
        }
    }
}
