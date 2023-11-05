/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7194586 8003280 8006694 8010404 8129962
 * @summary Add lambda tests
 *  Add back-end support for invokedynamic
 *  temporarily workaround combo tests are causing time out in several platforms
 * @library /tools/javac/lib
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.jvm
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build combo.ComboTestHelper
 * @run main TestInvokeDynamic
 */

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandleInfo;

import javax.tools.JavaFileObject;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreeScanner;

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.*;
import jdk.internal.classfile.constantpool.*;
import jdk.internal.classfile.instruction.InvokeDynamicInstruction;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodHandleSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.util.Names;

import combo.ComboParameter;
import combo.ComboTestHelper;
import combo.ComboInstance;
import combo.ComboTask.Result;

public class TestInvokeDynamic extends ComboInstance<TestInvokeDynamic> {

    enum StaticArgumentKind implements ComboParameter {
        STRING("Hello!", "String", "Ljava/lang/String;") {
            @Override
            boolean check(PoolEntry poolEntry) throws Exception {
                return (poolEntry instanceof StringEntry) &&
                        ((StringEntry)poolEntry).stringValue()
                        .equals(value);
            }
        },
        CLASS(null, "Class<?>", "Ljava/lang/Class;") {
            @Override
            boolean check(PoolEntry poolEntry) throws Exception {
                return (poolEntry instanceof ClassEntry) &&
                        ((ClassEntry)poolEntry).name()
                        .equalsString("java/lang/String");
            }
        },
        INTEGER(1, "int", "I") {
            @Override
            boolean check( PoolEntry poolEntry) throws Exception {
                return (poolEntry instanceof IntegerEntry) &&
                        ((IntegerEntry)poolEntry).intValue() ==
                                (Integer) value;
            }
        },
        LONG(1L, "long", "J") {
            @Override
            boolean check( PoolEntry poolEntry) throws Exception {
                return (poolEntry instanceof LongEntry) &&
                        ((LongEntry)poolEntry).longValue() ==
                                (Long) value;
            }
        },
        FLOAT(1.0f, "float", "F") {
            @Override
            boolean check( PoolEntry poolEntry) throws Exception {
                return (poolEntry instanceof FloatEntry) &&
                        ((FloatEntry)poolEntry).floatValue() ==
                                (Float) value;
            }
        },
        DOUBLE(1.0, "double","D") {
            @Override
            boolean check( PoolEntry poolEntry) throws Exception {
                return (poolEntry instanceof DoubleEntry) &&
                        ((DoubleEntry)poolEntry).doubleValue() ==
                                (Double) value;
            }
        },
        METHOD_HANDLE(null, "MethodHandle", "Ljava/lang/invoke/MethodHandle;") {
            @Override
            boolean check( PoolEntry poolEntry) throws Exception {
                if (!(poolEntry instanceof MethodHandleEntry handleInfo))
                    return false;
                return handleInfo.reference().owner().name().equalsString("Array") &&
                        handleInfo.kind() == MethodHandleInfo.REF_invokeVirtual &&
                        handleInfo.reference().name().equalsString("clone") &&
                        handleInfo.reference().type().equalsString("()Ljava/lang/Object;");
            }
        },
        METHOD_TYPE(null, "MethodType", "Ljava/lang/invoke/MethodType;") {
            @Override
            boolean check( PoolEntry poolEntry) throws Exception {
                return (poolEntry instanceof MethodTypeEntry methodTypeEntry) &&
                        methodTypeEntry.asSymbol().descriptorString().equals("()Ljava/lang/Object;");
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

        abstract boolean check( PoolEntry poolEntry) throws Exception;

        LoadableConstant getValue(Symtab syms) {
            return switch (this) {
                case STRING -> LoadableConstant.String((String) value);
                case INTEGER -> LoadableConstant.Int((Integer) value);
                case LONG -> LoadableConstant.Long((Long) value);
                case FLOAT -> LoadableConstant.Float((Float) value);
                case DOUBLE -> LoadableConstant.Double((Double) value);
                case CLASS -> (ClassType) syms.stringType;
                case METHOD_HANDLE -> syms.arrayCloneMethod.asHandle();
                case METHOD_TYPE -> ((MethodType) syms.arrayCloneMethod.type);
                default -> throw new AssertionError();
            };
        }

        @Override
        public String expand(String optParameter) {
            return sourceTypeStr;
        }
    }

    enum StaticArgumentsArity implements ComboParameter {
        ZERO(0, ""),
        ONE(1, ",#{SARG[0]} s1"),
        TWO(2, ",#{SARG[0]} s1, #{SARG[1]} s2"),
        THREE(3, ",#{SARG[0]} s1, #{SARG[1]} s2, #{SARG[2]} s3");

        int arity;
        String argsTemplate;

        StaticArgumentsArity(int arity, String argsTemplate) {
            this.arity = arity;
            this.argsTemplate = argsTemplate;
        }

        @Override
        public String expand(String optParameter) {
            return argsTemplate;
        }
    }

    public static void main(String... args) throws Exception {
        new ComboTestHelper<TestInvokeDynamic>()
                .withFilter(TestInvokeDynamic::redundantTestFilter)
                .withDimension("SARGS", (x, arity) -> x.arity = arity, StaticArgumentsArity.values())
                .withArrayDimension("SARG", (x, arg, idx) -> x.saks[idx] = arg, 3, StaticArgumentKind.values())
                .run(TestInvokeDynamic::new);
    }

    StaticArgumentsArity arity;
    StaticArgumentKind[] saks = new StaticArgumentKind[3];

    boolean redundantTestFilter() {
        for (int i = arity.arity ; i < saks.length ; i++) {
            if (saks[i].ordinal() != 0) {
                return false;
            }
        }
        return true;
    }

    final String source_template =
                "import java.lang.invoke.*;\n" +
                "class Test {\n" +
                "   void m() { }\n" +
                "   void test() {\n" +
                "      Object o = this; // marker statement \n" +
                "      m();\n" +
                "   }\n" +
                "}\n" +
                "class Bootstrap {\n" +
                "   public static CallSite bsm(MethodHandles.Lookup lookup, " +
                "String name, MethodType methodType #{SARGS}) {\n" +
                "       return null;\n" +
                "   }\n" +
                "}";

    @Override
    public void doWork() throws IOException {
        newCompilationTask()
                .withOption("-g")
                .withSourceFromTemplate(source_template)
                .withListenerFactory(context -> {
                        Symtab syms = Symtab.instance(context);
                        Names names = Names.instance(context);
                        Types types = Types.instance(context);
                        return new Indifier(syms, names, types);
                    })
                .generate(this::verifyBytecode);
    }

    void verifyBytecode(Result<Iterable<? extends JavaFileObject>> res) {
        if (res.hasErrors()) {
            fail("Diags found when compiling instance: " + res.compilationInfo());
            return;
        }
        try (InputStream is = res.get().iterator().next().openInputStream()){
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
                    InvokeDynamicEntry indyEntry = indy.invokedynamic();
                    bsmIdx = indyEntry.bootstrap().bsmIndex();
                    if (!indyEntry.type().equalsString("()V")) {
                        fail("type mismatch for CONSTANT_InvokeDynamic_info");
                        return;
                    }
                }
            }
            if (bsmIdx == -1) {
                fail("Missing invokedynamic in generated code");
                return;
            }

            BootstrapMethodsAttribute bsm_attr = cm
                    .findAttribute(Attributes.BOOTSTRAP_METHODS).orElseThrow();
            if (bsm_attr.bootstrapMethodsSize() != 1) {
                fail("Bad number of method specifiers " +
                        "in BootstrapMethods attribute");
                return;
            }
            BootstrapMethodEntry bsm_spec =
                    bsm_attr.bootstrapMethods().getFirst();

            if (bsm_spec.arguments().size() != arity.arity) {
                fail("Bad number of static invokedynamic args " +
                        "in BootstrapMethod attribute");
                return;
            }

            for (int i = 0 ; i < arity.arity ; i++) {
                if (!saks[i].check(bsm_spec.arguments().get(i))) {
                    fail("Bad static argument value " + saks[i]);
                    return;
                }
            }

            MethodHandleEntry bsm_handle = bsm_spec.bootstrapMethod();

            if (bsm_handle.kind() != MethodHandleInfo.REF_invokeStatic) {
                fail("Bad kind on boostrap method handle");
                return;
            }

            MemberRefEntry bsm_ref =bsm_handle.reference();

            if (!bsm_ref.owner().name().equalsString("Bootstrap")) {
                fail("Bad owner of boostrap method");
                return;
            }

            if (!bsm_ref.name().equalsString("bsm")) {
                fail("Bad boostrap method name");
                return;
            }

            if (!bsm_ref.type().equalsString(asBSMSignatureString())) {
                fail("Bad boostrap method type" +
                        bsm_ref.type().stringValue() + " " +
                        asBSMSignatureString());
                return;
            }

            LineNumberTableAttribute lnt = ea.findAttribute(Attributes.LINE_NUMBER_TABLE).orElse(null);

            if (lnt == null) {
                fail("No LineNumberTable attribute");
                return;
            }
            if (lnt.lineNumbers().size() != 3) {
                fail("Wrong number of entries in LineNumberTable");
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail("error reading classfile: " + res.compilationInfo());
            return;
        }
    }

    String asBSMSignatureString() {
        StringBuilder buf = new StringBuilder();
        buf.append("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;");
        for (int i = 0 ; i < arity.arity ; i++) {
            buf.append(saks[i].bytecodeTypeStr);
        }
        buf.append(")Ljava/lang/invoke/CallSite;");
        return buf.toString();
    }

    class Indifier extends TreeScanner<Void, Void> implements TaskListener {

        MethodHandleSymbol bsm;
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
                LoadableConstant[] staticArgs = new LoadableConstant[arity.arity];
                for (int i = 0; i < arity.arity ; i++) {
                    staticArgs[i] = saks[i].getValue(syms);
                }
                ident.sym = new Symbol.DynamicMethodSymbol(oldSym.name,
                        oldSym.owner, bsm, oldSym.type, staticArgs);
            }
            return null;
        }

        @Override
        public Void visitMethod(MethodTree node, Void p) {
            super.visitMethod(node, p);
            if (node.getName().toString().equals("bsm")) {
                bsm = ((JCMethodDecl)node).sym.asHandle();
            }
            return null;
        }
    }
}
