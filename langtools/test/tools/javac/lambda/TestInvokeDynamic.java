/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7194586 8003280 8006694 8010404
 * @summary Add lambda tests
 *  Add back-end support for invokedynamic
 *  temporarily workaround combo tests are causing time out in several platforms
 * @library ../lib
 * @build JavacTestingAbstractThreadedTest
 * @run main/othervm TestInvokeDynamic
 */

// use /othervm to avoid jtreg timeout issues (CODETOOLS-7900047)
// see JDK-8006746

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreeScanner;

import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.BootstrapMethods_attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool.*;
import com.sun.tools.classfile.Instruction;
import com.sun.tools.classfile.LineNumberTable_attribute;
import com.sun.tools.classfile.Method;

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.Pool;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import static com.sun.tools.javac.jvm.ClassFile.*;

public class TestInvokeDynamic
    extends JavacTestingAbstractThreadedTest
    implements Runnable {

    enum StaticArgumentKind {
        STRING("Hello!", "String", "Ljava/lang/String;") {
            @Override
            boolean check(CPInfo cpInfo) throws Exception {
                return (cpInfo instanceof CONSTANT_String_info) &&
                        ((CONSTANT_String_info)cpInfo).getString()
                        .equals(value);
            }
        },
        CLASS(null, "Class<?>", "Ljava/lang/Class;") {
            @Override
            boolean check(CPInfo cpInfo) throws Exception {
                return (cpInfo instanceof CONSTANT_Class_info) &&
                        ((CONSTANT_Class_info)cpInfo).getName()
                        .equals("java/lang/String");
            }
        },
        INTEGER(1, "int", "I") {
            @Override
            boolean check(CPInfo cpInfo) throws Exception {
                return (cpInfo instanceof CONSTANT_Integer_info) &&
                        ((CONSTANT_Integer_info)cpInfo).value ==
                        ((Integer)value).intValue();
            }
        },
        LONG(1L, "long", "J") {
            @Override
            boolean check(CPInfo cpInfo) throws Exception {
                return (cpInfo instanceof CONSTANT_Long_info) &&
                        ((CONSTANT_Long_info)cpInfo).value ==
                        ((Long)value).longValue();
            }
        },
        FLOAT(1.0f, "float", "F") {
            @Override
            boolean check(CPInfo cpInfo) throws Exception {
                return (cpInfo instanceof CONSTANT_Float_info) &&
                        ((CONSTANT_Float_info)cpInfo).value ==
                        ((Float)value).floatValue();
            }
        },
        DOUBLE(1.0, "double","D") {
            @Override
            boolean check(CPInfo cpInfo) throws Exception {
                return (cpInfo instanceof CONSTANT_Double_info) &&
                        ((CONSTANT_Double_info)cpInfo).value ==
                        ((Double)value).doubleValue();
            }
        },
        METHOD_HANDLE(null, "MethodHandle", "Ljava/lang/invoke/MethodHandle;") {
            @Override
            boolean check(CPInfo cpInfo) throws Exception {
                if (!(cpInfo instanceof CONSTANT_MethodHandle_info))
                    return false;
                CONSTANT_MethodHandle_info handleInfo =
                        (CONSTANT_MethodHandle_info)cpInfo;
                return handleInfo.getCPRefInfo().getClassName().equals("Array") &&
                        handleInfo.reference_kind == RefKind.REF_invokeVirtual &&
                        handleInfo.getCPRefInfo()
                        .getNameAndTypeInfo().getName().equals("clone") &&
                        handleInfo.getCPRefInfo()
                        .getNameAndTypeInfo().getType().equals("()Ljava/lang/Object;");
            }
        },
        METHOD_TYPE(null, "MethodType", "Ljava/lang/invoke/MethodType;") {
            @Override
            boolean check(CPInfo cpInfo) throws Exception {
                return (cpInfo instanceof CONSTANT_MethodType_info) &&
                        ((CONSTANT_MethodType_info)cpInfo).getType()
                        .equals("()Ljava/lang/Object;");
            }
        };

        Object value;
        String sourceTypeStr;
        String bytecodeTypeStr;

        StaticArgumentKind(Object value, String sourceTypeStr,
                String bytecodeTypeStr) {
            this.value = value;
            this.sourceTypeStr = sourceTypeStr;
            this.bytecodeTypeStr = bytecodeTypeStr;
        }

        abstract boolean check(CPInfo cpInfo) throws Exception;

        Object getValue(Symtab syms, Names names, Types types) {
            switch (this) {
                case STRING:
                case INTEGER:
                case LONG:
                case FLOAT:
                case DOUBLE:
                    return value;
                case CLASS:
                    return syms.stringType.tsym;
                case METHOD_HANDLE:
                    return new Pool.MethodHandle(REF_invokeVirtual,
                            syms.arrayCloneMethod, types);
                case METHOD_TYPE:
                    return syms.arrayCloneMethod.type;
                default:
                    throw new AssertionError();
            }
        }
    }

    enum StaticArgumentsArity {
        ZERO(0),
        ONE(1),
        TWO(2),
        THREE(3);

        int arity;

        StaticArgumentsArity(int arity) {
            this.arity = arity;
        }
    }

    public static void main(String... args) throws Exception {
        for (StaticArgumentsArity arity : StaticArgumentsArity.values()) {
            if (arity.arity == 0) {
                pool.execute(new TestInvokeDynamic(arity));
            } else {
                for (StaticArgumentKind sak1 : StaticArgumentKind.values()) {
                    if (arity.arity == 1) {
                        pool.execute(new TestInvokeDynamic(arity, sak1));
                    } else {
                        for (StaticArgumentKind sak2 : StaticArgumentKind.values()) {
                            if (arity.arity == 2) {
                                pool.execute(new TestInvokeDynamic(arity, sak1, sak2));
                            } else {
                                for (StaticArgumentKind sak3 : StaticArgumentKind.values()) {
                                    pool.execute(
                                        new TestInvokeDynamic(arity, sak1, sak2, sak3));
                                }
                            }
                        }
                    }
                }
            }
        }

        checkAfterExec();
    }

    StaticArgumentsArity arity;
    StaticArgumentKind[] saks;
    DiagChecker dc;

    TestInvokeDynamic(StaticArgumentsArity arity, StaticArgumentKind... saks) {
        this.arity = arity;
        this.saks = saks;
        dc = new DiagChecker();
    }

    public void run() {
        int id = checkCount.incrementAndGet();
        JavaSource source = new JavaSource(id);
        JavacTaskImpl ct = (JavacTaskImpl)comp.getTask(null, fm.get(), dc,
                Arrays.asList("-g"), null, Arrays.asList(source));
        Context context = ct.getContext();
        Symtab syms = Symtab.instance(context);
        Names names = Names.instance(context);
        Types types = Types.instance(context);
        ct.addTaskListener(new Indifier(syms, names, types));
        try {
            ct.generate();
        } catch (Throwable t) {
            t.printStackTrace();
            throw new AssertionError(
                    String.format("Error thrown when compiling following code\n%s",
                    source.source));
        }
        if (dc.diagFound) {
            throw new AssertionError(
                    String.format("Diags found when compiling following code\n%s\n\n%s",
                    source.source, dc.printDiags()));
        }
        verifyBytecode(id);
    }

    void verifyBytecode(int id) {
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
                    if (!indyInfo.getNameAndTypeInfo().getType().equals("()V")) {
                        throw new
                            AssertionError("type mismatch for CONSTANT_InvokeDynamic_info");
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

            if (bsm_spec.bootstrap_arguments.length != arity.arity) {
                throw new Error("Bad number of static invokedynamic args " +
                        "in BootstrapMethod attribute");
            }

            int count = 0;
            for (StaticArgumentKind sak : saks) {
                if (!sak.check(cf.constant_pool
                        .get(bsm_spec.bootstrap_arguments[count]))) {
                    throw new Error("Bad static argument value " + sak);
                }
                count++;
            }

            CONSTANT_MethodHandle_info bsm_handle =
                    (CONSTANT_MethodHandle_info)cf.constant_pool
                    .get(bsm_spec.bootstrap_method_ref);

            if (bsm_handle.reference_kind != RefKind.REF_invokeStatic) {
                throw new Error("Bad kind on boostrap method handle");
            }

            CONSTANT_Methodref_info bsm_ref =
                    (CONSTANT_Methodref_info)cf.constant_pool
                    .get(bsm_handle.reference_index);

            if (!bsm_ref.getClassInfo().getName().equals("Bootstrap")) {
                throw new Error("Bad owner of boostrap method");
            }

            if (!bsm_ref.getNameAndTypeInfo().getName().equals("bsm")) {
                throw new Error("Bad boostrap method name");
            }

            if (!bsm_ref.getNameAndTypeInfo()
                    .getType().equals(asBSMSignatureString())) {
                throw new Error("Bad boostrap method type" +
                        bsm_ref.getNameAndTypeInfo().getType() + " " +
                        asBSMSignatureString());
            }

            LineNumberTable_attribute lnt =
                    (LineNumberTable_attribute)ea.attributes.get(Attribute.LineNumberTable);

            if (lnt == null) {
                throw new Error("No LineNumberTable attribute");
            }
            if (lnt.line_number_table_length != 2) {
                throw new Error("Wrong number of entries in LineNumberTable");
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error("error reading " + compiledTest +": " + e);
        }
    }

    String asBSMSignatureString() {
        StringBuilder buf = new StringBuilder();
        buf.append("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;");
        for (StaticArgumentKind sak : saks) {
            buf.append(sak.bytecodeTypeStr);
        }
        buf.append(")Ljava/lang/invoke/CallSite;");
        return buf.toString();
    }

    class JavaSource extends SimpleJavaFileObject {

        static final String source_template = "import java.lang.invoke.*;\n" +
                "class Bootstrap {\n" +
                "   public static CallSite bsm(MethodHandles.Lookup lookup, " +
                "String name, MethodType methodType #SARGS) {\n" +
                "       return null;\n" +
                "   }\n" +
                "}\n" +
                "class Test#ID {\n" +
                "   void m() { }\n" +
                "   void test() {\n" +
                "      Object o = this; // marker statement \n" +
                "      m();\n" +
                "   }\n" +
                "}";

        String source;

        JavaSource(int id) {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = source_template.replace("#SARGS", asSignatureString())
                    .replace("#ID", String.valueOf(id));
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }

        String asSignatureString() {
            int count = 0;
            StringBuilder buf = new StringBuilder();
            for (StaticArgumentKind sak : saks) {
                buf.append(",");
                buf.append(sak.sourceTypeStr);
                buf.append(' ');
                buf.append(String.format("x%d", count++));
            }
            return buf.toString();
        }
    }

    class Indifier extends TreeScanner<Void, Void> implements TaskListener {

        MethodSymbol bsm;
        Symtab syms;
        Names names;
        Types types;

        Indifier(Symtab syms, Names names, Types types) {
            this.syms = syms;
            this.names = names;
            this.types = types;
        }

        @Override
        public void started(TaskEvent e) {
            //do nothing
        }

        @Override
        public void finished(TaskEvent e) {
            if (e.getKind() == TaskEvent.Kind.ANALYZE) {
                scan(e.getCompilationUnit(), null);
            }
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
            super.visitMethodInvocation(node, p);
            JCMethodInvocation apply = (JCMethodInvocation)node;
            JCIdent ident = (JCIdent)apply.meth;
            Symbol oldSym = ident.sym;
            if (!oldSym.isConstructor()) {
                Object[] staticArgs = new Object[arity.arity];
                for (int i = 0; i < arity.arity ; i++) {
                    staticArgs[i] = saks[i].getValue(syms, names, types);
                }
                ident.sym = new Symbol.DynamicMethodSymbol(oldSym.name,
                        oldSym.owner, REF_invokeStatic, bsm, oldSym.type, staticArgs);
            }
            return null;
        }

        @Override
        public Void visitMethod(MethodTree node, Void p) {
            super.visitMethod(node, p);
            if (node.getName().toString().equals("bsm")) {
                bsm = ((JCMethodDecl)node).sym;
            }
            return null;
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
