/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8009649
 * @summary Lambda back-end should generate invokespecial for method handles referring to private instance methods
 * @library ../../lib
 * @build JavacTestingAbstractThreadedTest
 * @run main/othervm TestLambdaBytecode
 */

// use /othervm to avoid jtreg timeout issues (CODETOOLS-7900047)
// see JDK-8006746

import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.BootstrapMethods_attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool.*;
import com.sun.tools.classfile.Instruction;
import com.sun.tools.classfile.Method;

import com.sun.tools.javac.api.JavacTaskImpl;


import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

import static com.sun.tools.javac.jvm.ClassFile.*;

public class TestLambdaBytecode
    extends JavacTestingAbstractThreadedTest
    implements Runnable {

    enum ClassKind {
        CLASS("class"),
        INTERFACE("interface");

        String classStr;

        ClassKind(String classStr) {
            this.classStr = classStr;
        }
    }

    enum AccessKind {
        PUBLIC("public"),
        PRIVATE("private");

        String accessStr;

        AccessKind(String accessStr) {
            this.accessStr = accessStr;
        }
    }

    enum StaticKind {
        STATIC("static"),
        INSTANCE("");

        String staticStr;

        StaticKind(String staticStr) {
            this.staticStr = staticStr;
        }
    }

    enum DefaultKind {
        DEFAULT("default"),
        NO_DEFAULT("");

        String defaultStr;

        DefaultKind(String defaultStr) {
            this.defaultStr = defaultStr;
        }
    }

    enum ExprKind {
        LAMBDA("Runnable r = ()->{ target(); };");

        String exprString;

        ExprKind(String exprString) {
            this.exprString = exprString;
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

        String mods() {
            StringBuilder buf = new StringBuilder();
            buf.append(ak.accessStr);
            buf.append(' ');
            buf.append(sk.staticStr);
            buf.append(' ');
            buf.append(dk.defaultStr);
            return buf.toString();
        }
    }

    public static void main(String... args) throws Exception {
        for (ClassKind ck : ClassKind.values()) {
            for (AccessKind ak1 : AccessKind.values()) {
                for (StaticKind sk1 : StaticKind.values()) {
                    for (DefaultKind dk1 : DefaultKind.values()) {
                        for (AccessKind ak2 : AccessKind.values()) {
                            for (StaticKind sk2 : StaticKind.values()) {
                                for (DefaultKind dk2 : DefaultKind.values()) {
                                    for (ExprKind ek : ExprKind.values()) {
                                        pool.execute(new TestLambdaBytecode(ck, ak1, ak2, sk1, sk2, dk1, dk2, ek));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        checkAfterExec();
    }

    MethodKind mk1, mk2;
    ExprKind ek;
    DiagChecker dc;

    TestLambdaBytecode(ClassKind ck, AccessKind ak1, AccessKind ak2, StaticKind sk1,
            StaticKind sk2, DefaultKind dk1, DefaultKind dk2, ExprKind ek) {
        mk1 = new MethodKind(ck, ak1, sk1, dk1);
        mk2 = new MethodKind(ck, ak2, sk2, dk2);
        this.ek = ek;
        dc = new DiagChecker();
    }

    public void run() {
        int id = checkCount.incrementAndGet();
        JavaSource source = new JavaSource(id);
        JavacTaskImpl ct = (JavacTaskImpl)comp.getTask(null, fm.get(), dc,
                null, null, Arrays.asList(source));
        try {
            ct.generate();
        } catch (Throwable t) {
            t.printStackTrace();
            throw new AssertionError(
                    String.format("Error thrown when compiling following code\n%s",
                    source.source));
        }
        if (dc.diagFound) {
            boolean errorExpected = !mk1.isOK() || !mk2.isOK();
            errorExpected |= mk1.isStatic() && !mk2.isStatic();

            if (!errorExpected) {
                throw new AssertionError(
                        String.format("Diags found when compiling following code\n%s\n\n%s",
                        source.source, dc.printDiags()));
            }
            return;
        }
        verifyBytecode(id, source);
    }

    void verifyBytecode(int id, JavaSource source) {
        File compiledTest = new File(String.format("Test%d.class", id));
        try {
            ClassFile cf = ClassFile.read(compiledTest);
            Method testMethod = null;
            for (Method m : cf.methods) {
                if (m.getName(cf.constant_pool).equals("test")) {
                    testMethod = m;
                    break;
                }
            }
            if (testMethod == null) {
                throw new Error("Test method not found");
            }
            Code_attribute ea =
                    (Code_attribute)testMethod.attributes.get(Attribute.Code);
            if (testMethod == null) {
                throw new Error("Code attribute for test() method not found");
            }

            int bsmIdx = -1;

            for (Instruction i : ea.getInstructions()) {
                if (i.getMnemonic().equals("invokedynamic")) {
                    CONSTANT_InvokeDynamic_info indyInfo =
                         (CONSTANT_InvokeDynamic_info)cf
                            .constant_pool.get(i.getShort(1));
                    bsmIdx = indyInfo.bootstrap_method_attr_index;
                    if (!indyInfo.getNameAndTypeInfo().getType().equals(makeIndyType(id))) {
                        throw new
                            AssertionError("type mismatch for CONSTANT_InvokeDynamic_info " + source.source + "\n" + indyInfo.getNameAndTypeInfo().getType() + "\n" + makeIndyType(id));
                    }
                }
            }
            if (bsmIdx == -1) {
                throw new Error("Missing invokedynamic in generated code");
            }

            BootstrapMethods_attribute bsm_attr =
                    (BootstrapMethods_attribute)cf
                    .getAttribute(Attribute.BootstrapMethods);
            if (bsm_attr.bootstrap_method_specifiers.length != 1) {
                throw new Error("Bad number of method specifiers " +
                        "in BootstrapMethods attribute");
            }
            BootstrapMethods_attribute.BootstrapMethodSpecifier bsm_spec =
                    bsm_attr.bootstrap_method_specifiers[0];

            if (bsm_spec.bootstrap_arguments.length != MF_ARITY) {
                throw new Error("Bad number of static invokedynamic args " +
                        "in BootstrapMethod attribute");
            }

            CONSTANT_MethodHandle_info mh =
                    (CONSTANT_MethodHandle_info)cf.constant_pool.get(bsm_spec.bootstrap_arguments[1]);

            boolean kindOK;
            switch (mh.reference_kind) {
                case REF_invokeStatic: kindOK = mk2.isStatic(); break;
                case REF_invokeSpecial: kindOK = !mk2.isStatic(); break;
                case REF_invokeInterface: kindOK = mk2.inInterface(); break;
                default:
                    kindOK = false;
            }

            if (!kindOK) {
                throw new Error("Bad invoke kind in implementation method handle");
            }

            if (!mh.getCPRefInfo().getNameAndTypeInfo().getType().toString().equals(MH_SIG)) {
                throw new Error("Type mismatch in implementation method handle");
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error("error reading " + compiledTest +": " + e);
        }
    }
    String makeIndyType(int id) {
        StringBuilder buf = new StringBuilder();
        buf.append("(");
        if (!mk2.isStatic()) {
            buf.append(String.format("LTest%d;", id));
        }
        buf.append(")Ljava/lang/Runnable;");
        return buf.toString();
    }

    static final int MF_ARITY = 3;
    static final String MH_SIG = "()V";

    class JavaSource extends SimpleJavaFileObject {

        static final String source_template =
                "#CK Test#ID {\n" +
                "   #MOD1 void test() { #EK }\n" +
                "   #MOD2 void target() { }\n" +
                "}\n";

        String source;

        JavaSource(int id) {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = source_template.replace("#CK", mk1.ck.classStr)
                    .replace("#MOD1", mk1.mods())
                    .replace("#MOD2", mk2.mods())
                    .replace("#EK", ek.exprString)
                    .replace("#ID", String.valueOf(id));
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    static class DiagChecker
        implements javax.tools.DiagnosticListener<JavaFileObject> {

        boolean diagFound;
        ArrayList<String> diags = new ArrayList<>();

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            diags.add(diagnostic.getMessage(Locale.getDefault()));
            diagFound = true;
        }

        String printDiags() {
            StringBuilder buf = new StringBuilder();
            for (String s : diags) {
                buf.append(s);
                buf.append("\n");
            }
            return buf.toString();
        }
    }

}
