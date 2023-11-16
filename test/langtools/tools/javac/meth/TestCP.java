/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6991980
 * @summary  polymorphic signature calls don't share the same CP entries
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 * @run main TestCP
 */

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.CodeAttribute;
import jdk.internal.classfile.constantpool.MemberRefEntry;
import jdk.internal.classfile.instruction.InvokeInstruction;

import java.lang.invoke.*;
import java.io.*;

public class TestCP {

    static class TestMethodHandleInvokeExact {
        static final String PS_TYPE = "(Ljava/lang/String;IC)Ljava/lang/Number;";

        void test(MethodHandle mh) throws Throwable {
            Number n = (Number)mh.invokeExact("daddy",1,'n');
            n = (Number)mh.invokeExact("bunny",1,'d');
            n = (Number)(mh.invokeExact("foo",1,'d'));
            n = (Number)((mh.invokeExact("bar",1,'d')));
        }
    }

    static class TestVarHandleGet {
        static final String PS_TYPE = "(Ljava/lang/String;IC)Ljava/lang/Number;";

        // Test with sig-poly return type
        void test(VarHandle vh) throws Throwable {
            Number n = (Number)vh.get("daddy",1,'n');
            n = (Number)vh.get("bunny",1,'d');
            n = (Number)(vh.get("foo",1,'d'));
            n = (Number)((vh.get("bar",1,'d')));
        }
    }

    static class TestVarHandleSet {
        static final String PS_TYPE = "(Ljava/lang/String;IC)V";

        // Test with non-sig-poly void return type
        void test(VarHandle vh) throws Throwable {
            vh.set("daddy",1,'n');
            vh.set("bunny",1,'d');
            vh.set("foo",1,'d');
            vh.set("bar",1,'d');
        }
    }

    static class TestVarHandleCompareAndSet {
        static final String PS_TYPE = "(Ljava/lang/String;IC)Z";

        // Test with non-sig-poly boolean return type
        void test(VarHandle vh) throws Throwable {
            boolean r = vh.compareAndSet("daddy",1,'n');
            r = vh.compareAndSet("bunny",1,'d');
            r = (vh.compareAndSet("foo",1,'d'));
            r = ((vh.compareAndSet("bar",1,'d')));
        }
    }

    static final int PS_CALLS_COUNT = 4;
    static final String TEST_METHOD_NAME = "test";

    public static void main(String... args) throws Exception {
        new TestCP().run();
    }

    public void run() throws Exception {
        verifySigPolyInvokeVirtual(
                getTestFile(TestMethodHandleInvokeExact.class),
                TestMethodHandleInvokeExact.PS_TYPE);

        verifySigPolyInvokeVirtual(
                getTestFile(TestVarHandleGet.class),
                TestVarHandleGet.PS_TYPE);

        verifySigPolyInvokeVirtual(
                getTestFile(TestVarHandleSet.class),
                TestVarHandleSet.PS_TYPE);

        verifySigPolyInvokeVirtual(
                getTestFile(TestVarHandleCompareAndSet.class),
                TestVarHandleCompareAndSet.PS_TYPE);
    }

    static File getTestFile(Class<?> c) {
        String workDir = System.getProperty("test.classes");
        return new File(workDir, getTestName(c));
    }
    static String getTestName(Class<?> c) {
        return c.getName() + ".class";
    }

    void verifySigPolyInvokeVirtual(File f, String psType) {
        System.err.println("verify: " + f);
        try {
            int count = 0;
            ClassModel cf = Classfile.of().parse(f.toPath());
            MethodModel testMethod = null;
            for (MethodModel m : cf.methods()) {
                if (m.methodName().equalsString(TEST_METHOD_NAME)) {
                    testMethod = m;
                    break;
                }
            }
            if (testMethod == null) {
                throw new Error("Test method not found");
            }
            CodeAttribute ea = testMethod.findAttribute(Attributes.CODE).orElse(null);
            if (ea == null) {
                throw new Error("Code attribute for test() method not found");
            }
            int instr_count = 0;
            MemberRefEntry methRef = null;

            for (CodeElement ce : ea.elementList()) {
                if (ce instanceof InvokeInstruction ins && ins.opcode() == Opcode.INVOKEVIRTUAL) {
                    instr_count++;
                    if (methRef == null) {
                        methRef = ins.method();
                    } else if (methRef != ins.method()) {
                        throw new Error("Unexpected CP entry in polymorphic signature call");
                    }
                    String type = methRef.type().stringValue();
                    if (!type.equals(psType)) {
                        throw new Error("Unexpected type in polymorphic signature call: " + type);
                    }
                }
            }
            if (instr_count != PS_CALLS_COUNT) {
                throw new Error("Wrong number of polymorphic signature call found: " + instr_count);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error("error reading " + f +": " + e);
        }
    }
}
