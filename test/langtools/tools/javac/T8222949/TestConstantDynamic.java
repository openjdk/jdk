/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8222949
 * @summary add condy support to javac's pool API
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
 * @run main TestConstantDynamic
 */

import java.io.IOException;
import java.io.InputStream;

import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;

import com.sun.source.tree.*;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreeScanner;

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.*;
import jdk.internal.classfile.constantpool.*;
import jdk.internal.classfile.instruction.ConstantInstruction;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.PoolConstant.LoadableConstant;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.List;

import combo.ComboParameter;
import combo.ComboTestHelper;
import combo.ComboInstance;
import combo.ComboTask.Result;

import static java.lang.invoke.MethodHandleInfo.REF_invokeStatic;

public class TestConstantDynamic extends ComboInstance<TestConstantDynamic> {

    enum ConstantType implements ComboParameter {
        STRING("String", "Ljava/lang/String;", Opcode.LDC),
        CLASS("Class<?>", "Ljava/lang/Class;", Opcode.LDC),
        INTEGER("int", "I", Opcode.LDC),
        LONG("long", "J", Opcode.LDC2_W),
        FLOAT("float", "F", Opcode.LDC),
        DOUBLE("double", "D", Opcode.LDC2_W),
        METHOD_HANDLE("MethodHandle", "Ljava/lang/invoke/MethodHandle;", Opcode.LDC),
        METHOD_TYPE("MethodType", "Ljava/lang/invoke/MethodType;", Opcode.LDC);

        String sourceTypeStr;
        String bytecodeTypeStr;
        Opcode opcode;

        ConstantType(String sourceTypeStr, String bytecodeTypeStr, Opcode opcode) {
            this.sourceTypeStr = sourceTypeStr;
            this.bytecodeTypeStr = bytecodeTypeStr;
            this.opcode = opcode;
        }

        @Override
        public String expand(String optParameter) {
            return sourceTypeStr;
        }
    }

    enum Value implements ComboParameter {
        STRING("\"Hello!\""),
        CLASS("null"),
        INTEGER("1"),
        LONG("1L"),
        FLOAT("1.0f"),
        DOUBLE("1.0"),
        METHOD_HANDLE("null"),
        METHOD_TYPE("null");

        String value;

        Value(String value) {
            this.value = value;
        }

        @Override
        public String expand(String optParameter) {
            return value;
        }
    }

    public static void main(String... args) throws Exception {
        new ComboTestHelper<TestConstantDynamic>()
                .withFilter(TestConstantDynamic::redundantTestFilter)
                .withDimension("TYPE", (x, type) -> x.type = type, ConstantType.values())
                .withDimension("VALUE", (x, value) -> x.value = value, Value.values())
                .run(TestConstantDynamic::new);
    }

    ConstantType type;
    Value value;

    boolean redundantTestFilter() {
        return type.name().equals(value.name());
    }

    final String source_template =
                "import java.lang.invoke.*;\n" +
                "import java.lang.invoke.MethodHandles.*;\n" +
                "class Test {\n" +
                "    static final #{TYPE} f = #{VALUE};\n" +

                "    static #{TYPE} bsm(MethodHandles.Lookup lookup, String name, Class<?> type) {\n" +
                "        return f;\n" +
                "    }\n" +

                "    static void test() {\n" +
                "        #{TYPE} i = f;\n" +
                "    }\n" +
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
                        return new Condifier(syms, names, types);
                    })
                .generate(this::verifyBytecode);
    }

    void verifyBytecode(Result<Iterable<? extends JavaFileObject>> res) {
        if (res.hasErrors()) {
            fail("Diags found when compiling instance: " + res.compilationInfo());
            return;
        }
        try (InputStream is = res.get().iterator().next().openInputStream()){
            ClassModel cf = Classfile.of().parse(is.readAllBytes());
            MethodModel testMethod = null;
            for (MethodModel m : cf.methods()) {
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

            BootstrapMethodEntry bootstrapMethodEntry = null;

            for (CodeElement i : ea.elementList()) {
                if (i instanceof ConstantInstruction.LoadConstantInstruction lci) {
                    ConstantDynamicEntry condyInfo = (ConstantDynamicEntry)lci.constantEntry();
                    bootstrapMethodEntry = condyInfo.bootstrap();
                    System.out.println("condyInfo.getNameAndTypeInfo().getType() " + condyInfo.type().stringValue());
                    if (!condyInfo.type().equalsString(type.bytecodeTypeStr)) {
                        fail("type mismatch for ConstantDynamicEntry");
                        return;
                    }
                    if (lci.opcode() != type.opcode) {
                        fail("unexpected opcode for constant value: " + lci.opcode());
                        return;
                    }
                }
            }


            if (bootstrapMethodEntry == null) {
                fail("Missing constantdynamic in generated code");
                return;
            }

            BootstrapMethodsAttribute bsm_attr = cf.findAttribute(Attributes.BOOTSTRAP_METHODS).orElseThrow();
            if (bsm_attr.bootstrapMethods().size() != 1) {
                fail("Bad number of method specifiers " +
                        "in BootstrapMethods attribute");
                return;
            }
            BootstrapMethodEntry bsm_spec =
                    bsm_attr.bootstrapMethods().getFirst();

            MethodHandleEntry bsm_handle = bsm_spec.bootstrapMethod();

            if (bsm_handle.kind() != REF_invokeStatic) {
                fail("Bad kind on boostrap method handle");
                return;
            }

            MemberRefEntry bsm_ref = bsm_handle.reference();

            if (!bsm_ref.owner().name().equalsString("Test")) {
                fail("Bad owner of boostrap method");
                return;
            }

            if (!bsm_ref.name().equalsString("bsm")) {
                fail("Bad boostrap method name");
                return;
            }

            if (!bsm_ref.type().equalsString(asBSMSignatureString())) {
                fail("Bad boostrap method type" +
                        bsm_ref.type() + " " +
                        asBSMSignatureString());
                return;
            }

            LineNumberTableAttribute lnt = ea.findAttribute(Attributes.LINE_NUMBER_TABLE).orElse(null);

            if (lnt == null) {
                fail("No LineNumberTable attribute");
                return;
            }
            if (lnt.lineNumbers().size() != 2) {
                fail("Wrong number of entries in LineNumberTable");
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail("error reading classfile: " + res.compilationInfo());
        }
    }

    String asBSMSignatureString() {
        StringBuilder buf = new StringBuilder();
        buf.append("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;");
        buf.append(")" + type.bytecodeTypeStr);
        return buf.toString();
    }

    class Condifier extends TreeScanner<Void, Void> implements TaskListener {

        MethodHandleSymbol bsm;
        Symtab syms;
        Names names;
        Types types;

        Condifier(Symtab syms, Names names, Types types) {
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
        public Void visitVariable(VariableTree node, Void p) {
            super.visitVariable(node, p);
            JCVariableDecl tree = (JCVariableDecl)node;
            VarSymbol v = tree.sym;
            if (tree.init != null && v.name.toString().equals("i")) {
                List<Type> bsm_staticArgs = List.of(syms.methodHandleLookupType,
                        syms.stringType,
                        syms.classType);
                Name bsmName = names.fromString("bsm");
                Symbol.DynamicVarSymbol dynSym = new Symbol.DynamicVarSymbol(bsmName,
                        syms.noSymbol,
                        bsm,
                        v.type,
                        new LoadableConstant[0]);
                ((JCIdent)tree.init).sym = dynSym;
                ((JCIdent)tree.init).name = bsmName;
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
