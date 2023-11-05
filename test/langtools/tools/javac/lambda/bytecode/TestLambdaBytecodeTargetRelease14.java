/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8238358
 * @summary Lambda back-end should generate invokespecial for method handles referring to
 *          private instance methods when compiling with --release 14
 * @library /tools/javac/lib
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.util
 * @build combo.ComboTestHelper
 * @run main TestLambdaBytecodeTargetRelease14
 */

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.*;
import jdk.internal.classfile.constantpool.InvokeDynamicEntry;
import jdk.internal.classfile.constantpool.MethodHandleEntry;
import jdk.internal.classfile.instruction.InvokeDynamicInstruction;

import java.io.IOException;
import java.io.InputStream;

import combo.ComboInstance;
import combo.ComboParameter;
import combo.ComboTask.Result;
import combo.ComboTestHelper;

import java.lang.invoke.MethodHandleInfo;
import javax.tools.JavaFileObject;

public class TestLambdaBytecodeTargetRelease14 extends ComboInstance<TestLambdaBytecodeTargetRelease14> {

    static final int MF_ARITY = 3;
    static final String MH_SIG = "()V";

    enum ClassKind implements ComboParameter {
        CLASS("class"),
        INTERFACE("interface");

        String classStr;

        ClassKind(String classStr) {
            this.classStr = classStr;
        }

        @Override
        public String expand(String optParameter) {
            return classStr;
        }
    }

    enum AccessKind implements ComboParameter {
        PUBLIC("public"),
        PRIVATE("private");

        String accessStr;

        AccessKind(String accessStr) {
            this.accessStr = accessStr;
        }

        @Override
        public String expand(String optParameter) {
            return accessStr;
        }
    }

    enum StaticKind implements ComboParameter {
        STATIC("static"),
        INSTANCE("");

        String staticStr;

        StaticKind(String staticStr) {
            this.staticStr = staticStr;
        }

        @Override
        public String expand(String optParameter) {
            return staticStr;
        }
    }

    enum DefaultKind implements ComboParameter {
        DEFAULT("default"),
        NO_DEFAULT("");

        String defaultStr;

        DefaultKind(String defaultStr) {
            this.defaultStr = defaultStr;
        }

        @Override
        public String expand(String optParameter) {
            return defaultStr;
        }
    }

    static class MethodKind {
        ClassKind ck;
        AccessKind ak;
        StaticKind sk;
        DefaultKind dk;

        MethodKind(ClassKind ck, AccessKind ak, StaticKind sk, DefaultKind dk) {
            this.ck = ck;
            this.ak = ak;
            this.sk = sk;
            this.dk = dk;
        }

        boolean inInterface() {
            return ck == ClassKind.INTERFACE;
        }

        boolean isPrivate() {
            return ak == AccessKind.PRIVATE;
        }

        boolean isStatic() {
            return sk == StaticKind.STATIC;
        }

        boolean isDefault() {
            return dk == DefaultKind.DEFAULT;
        }

        boolean isOK() {
            if (isDefault() && (!inInterface() || isStatic())) {
                return false;
            } else if (inInterface() &&
                    ((!isStatic() && !isDefault()) || isPrivate())) {
                return false;
            } else {
                return true;
            }
        }
    }

    public static void main(String... args) throws Exception {
        new ComboTestHelper<TestLambdaBytecodeTargetRelease14>()
                .withDimension("CLASSKIND", (x, ck) -> x.ck = ck, ClassKind.values())
                .withArrayDimension("ACCESS", (x, acc, idx) -> x.accessKinds[idx] = acc, 2, AccessKind.values())
                .withArrayDimension("STATIC", (x, sk, idx) -> x.staticKinds[idx] = sk, 2, StaticKind.values())
                .withArrayDimension("DEFAULT", (x, dk, idx) -> x.defaultKinds[idx] = dk, 2, DefaultKind.values())
                .run(TestLambdaBytecodeTargetRelease14::new, TestLambdaBytecodeTargetRelease14::init);
    }

    ClassKind ck;
    AccessKind[] accessKinds = new AccessKind[2];
    StaticKind[] staticKinds = new StaticKind[2];
    DefaultKind[] defaultKinds = new DefaultKind[2];
    MethodKind mk1, mk2;

    void init() {
        mk1 = new MethodKind(ck, accessKinds[0], staticKinds[0], defaultKinds[0]);
        mk2 = new MethodKind(ck, accessKinds[1], staticKinds[1], defaultKinds[1]);
    }

    String source_template =
                "#{CLASSKIND} Test {\n" +
                "   #{ACCESS[0]} #{STATIC[0]} #{DEFAULT[0]} void test() { Runnable r = ()->{ target(); }; }\n" +
                "   #{ACCESS[1]} #{STATIC[1]} #{DEFAULT[1]} void target() { }\n" +
                "}\n";

    @Override
    public void doWork() throws IOException {
        newCompilationTask()
                .withSourceFromTemplate(source_template)
                .withOption("--release").withOption("14")
                .generate(this::verifyBytecode);
    }

    void verifyBytecode(Result<Iterable<? extends JavaFileObject>> res) {
        if (res.hasErrors()) {
            boolean errorExpected = !mk1.isOK() || !mk2.isOK();
            errorExpected |= mk1.isStatic() && !mk2.isStatic();

            if (!errorExpected) {
                fail("Diags found when compiling instance; " + res.compilationInfo());
            }
            return;
        }
        try (InputStream is = res.get().iterator().next().openInputStream()) {
            ClassModel cm = Classfile.of().parse(is.readAllBytes());
            MethodModel testMethod = null;
            for (MethodModel m : cm.methods()) {
                if (m.methodName().equalsString("test")) {
                    testMethod = m;
                    break;
                }
            }
            if (testMethod == null) {
                fail("Test method not found");
                return;
            }
            CodeAttribute ea = testMethod.findAttribute(Attributes.CODE).orElse(null);
            if (ea == null) {
                fail("Code attribute for test() method not found");
                return;
            }

            int bsmIdx = -1;

            for (CodeElement ce : ea.elementList()) {
                if (ce instanceof InvokeDynamicInstruction indy) {
                    InvokeDynamicEntry indyInfo = indy.invokedynamic();
                    bsmIdx = indyInfo.bootstrap().bsmIndex();
                    if (!indyInfo.type().equalsString(makeIndyType())) {
                        fail("type mismatch for CONSTANT_InvokeDynamic_info " +
                                res.compilationInfo() + "\n" + indyInfo.type().stringValue() +
                                "\n" + makeIndyType());
                        return;
                    }
                }
            }
            if (bsmIdx == -1) {
                fail("Missing invokedynamic in generated code");
                return;
            }

            BootstrapMethodsAttribute bsm_attr = cm.findAttribute(Attributes.BOOTSTRAP_METHODS).orElseThrow();
            if (bsm_attr.bootstrapMethodsSize() != 1) {
                fail("Bad number of method specifiers " +
                        "in BootstrapMethods attribute");
                return;
            }
            BootstrapMethodEntry bsm_spec = bsm_attr.bootstrapMethods().get(0);

            if (bsm_spec.arguments().size() != MF_ARITY) {
                fail("Bad number of static invokedynamic args " +
                        "in BootstrapMethod attribute");
                return;
            }

            MethodHandleEntry mh = (MethodHandleEntry) bsm_spec.arguments().get(1);

            boolean kindOK = switch (mh.kind()) {
                case MethodHandleInfo.REF_invokeStatic -> mk2.isStatic();
                case MethodHandleInfo.REF_invokeSpecial -> !mk2.isStatic();
                case MethodHandleInfo.REF_invokeInterface -> mk2.inInterface();
                default -> false;
            };

            if (!kindOK) {
                fail("Bad invoke kind in implementation method handle");
                return;
            }

            if (!mh.reference().type().equalsString(MH_SIG)) {
                fail("Type mismatch in implementation method handle");
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail("error reading " + res.compilationInfo() + ": " + e);
        }
    }

    String makeIndyType() {
        StringBuilder buf = new StringBuilder();
        buf.append("(");
        if (!mk2.isStatic()) {
            buf.append("LTest;");
        }
        buf.append(")Ljava/lang/Runnable;");
        return buf.toString();
    }
}
