/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7192246
 * @summary  check that code attributed for default methods is correctly generated
 * @enablePreview
 * @modules java.base/jdk.internal.classfile.impl
 */

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.instruction.InvokeInstruction;
import java.io.*;

public class TestDefaultBody {

    interface TestInterface {
        int no_default(int i);
        default int yes_default(int i) { return impl(this, i); }
    }

    static int impl(TestInterface ti, int i) { return 0; }

    static final String TARGET_CLASS_NAME = "TestDefaultBody";
    static final String TARGET_NAME = "impl";
    static final String TARGET_TYPE = "(LTestDefaultBody$TestInterface;I)I";
    static final String SUBTEST_NAME = TestInterface.class.getName() + ".class";
    static final String TEST_METHOD_NAME = "yes_default";

    public static void main(String... args) throws Exception {
        new TestDefaultBody().run();
    }

    public void run() throws Exception {
        String workDir = System.getProperty("test.classes");
        File compiledTest = new File(workDir, SUBTEST_NAME);
        verifyDefaultBody(compiledTest);
    }

    void verifyDefaultBody(File f) {
        System.err.println("verify: " + f);
        try {
            ClassModel cf = ClassFile.of().parse(f.toPath());
            MethodModel testMethod = null;
            CodeAttribute codeAttr = null;
            for (MethodModel m : cf.methods()) {
                codeAttr = m.findAttribute(Attributes.CODE).orElse(null);
                String mname = m.methodName().stringValue();
                if (mname.equals(TEST_METHOD_NAME)) {
                    testMethod = m;
                    break;
                } else {
                    codeAttr = null;
                }
            }
            if (testMethod == null) {
                throw new Error("Test method not found");
            }
            if ((testMethod.flags().flagsMask() & ClassFile.ACC_ABSTRACT) != 0) {
                throw new Error("Test method is abstract");
            }
            if (codeAttr == null) {
                throw new Error("Code attribute in test method not found");
            }

            boolean found = false;
            for (CodeElement instr : codeAttr.elementList()) {
                if (instr instanceof InvokeInstruction ins && ins.opcode() == Opcode.INVOKESTATIC) {
                    found = true;
                    MemberRefEntry mref = ins.method();
                    String className = mref.owner().asInternalName();
                    String targetName = mref.name().stringValue();
                    String targetType = mref.type().stringValue();

                    if (!className.equals(TARGET_CLASS_NAME)) {
                        throw new Error("unexpected class in default method body " + className);
                    }
                    if (!targetName.equals(TARGET_NAME)) {
                        throw new Error("unexpected method name in default method body " + targetName);
                    }
                    if (!targetType.equals(TARGET_TYPE)) {
                        throw new Error("unexpected method type in default method body " + targetType);
                    }
                    break;
                }
            }

            if (!found) {
                throw new Error("no invokestatic found in default method body");
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error("error reading " + f +": " + e);
        }
    }
}
