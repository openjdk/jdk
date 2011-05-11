/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7042566
 * @summary Unambiguous varargs method calls flagged as ambiguous
 */

import com.sun.source.util.JavacTask;
import com.sun.tools.classfile.Instruction;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool.*;
import com.sun.tools.classfile.Method;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.util.List;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class T7042566 {

    VarargsMethod m1;
    VarargsMethod m2;
    TypeConfiguration actuals;

    T7042566(TypeConfiguration m1_conf, TypeConfiguration m2_conf, TypeConfiguration actuals) {
        this.m1 = new VarargsMethod(m1_conf);
        this.m2 = new VarargsMethod(m2_conf);
        this.actuals = actuals;
    }

    void compileAndCheck() throws Exception {
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        JavaSource source = new JavaSource();
        ErrorChecker ec = new ErrorChecker();
        JavacTask ct = (JavacTask)tool.getTask(null, fm, ec,
                null, null, Arrays.asList(source));
        ct.call();
        check(source, ec);
    }

    void check(JavaSource source, ErrorChecker ec) {
        checkCount++;
        boolean resolutionError = false;
        VarargsMethod selectedMethod = null;

        boolean m1_applicable = m1.isApplicable(actuals);
        boolean m2_applicable = m2.isApplicable(actuals);

        if (!m1_applicable && !m2_applicable) {
            resolutionError = true;
        } else if (m1_applicable && m2_applicable) {
            //most specific
            boolean m1_moreSpecific = m1.isMoreSpecificThan(m2);
            boolean m2_moreSpecific = m2.isMoreSpecificThan(m1);

            resolutionError = m1_moreSpecific == m2_moreSpecific;
            selectedMethod = m1_moreSpecific ? m1 : m2;
        } else {
            selectedMethod = m1_applicable ?
                m1 : m2;
        }

        if (ec.errorFound != resolutionError) {
            throw new Error("invalid diagnostics for source:\n" +
                    source.getCharContent(true) +
                    "\nExpected resolution error: " + resolutionError +
                    "\nFound error: " + ec.errorFound +
                    "\nCompiler diagnostics:\n" + ec.printDiags());
        } else if (!resolutionError) {
            verifyBytecode(selectedMethod, source);
        }
    }

    void verifyBytecode(VarargsMethod selected, JavaSource source) {
        bytecodeCheckCount++;
        File compiledTest = new File("Test.class");
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
            Code_attribute ea = (Code_attribute)testMethod.attributes.get(Attribute.Code);
            if (testMethod == null) {
                throw new Error("Code attribute for test() method not found");
            }

            for (Instruction i : ea.getInstructions()) {
                if (i.getMnemonic().equals("invokevirtual")) {
                    int cp_entry = i.getUnsignedShort(1);
                    CONSTANT_Methodref_info methRef =
                            (CONSTANT_Methodref_info)cf.constant_pool.get(cp_entry);
                    String type = methRef.getNameAndTypeInfo().getType();
                    String sig = selected.parameterTypes.bytecodeSigStr;
                    if (!type.contains(sig)) {
                        throw new Error("Unexpected type method call: " + type + "" +
                                        "\nfound: " + sig +
                                        "\n" + source.getCharContent(true));
                    }
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error("error reading " + compiledTest +": " + e);
        }
    }

    class JavaSource extends SimpleJavaFileObject {

        static final String source_template = "class Test {\n" +
                "   #V1\n" +
                "   #V2\n" +
                "   void test() { m(#E); }\n" +
                "}";

        String source;

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = source_template.replaceAll("#V1", m1.toString()).
                    replaceAll("#V2", m2.toString()).
                    replaceAll("#E", actuals.expressionListStr);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    /** global decls ***/

    // Create a single file manager and reuse it for each compile to save time.
    static StandardJavaFileManager fm = JavacTool.create().getStandardFileManager(null, null, null);

    //statistics
    static int checkCount = 0;
    static int bytecodeCheckCount = 0;

    public static void main(String... args) throws Exception {
        for (TypeConfiguration tconf1 : TypeConfiguration.values()) {
            for (TypeConfiguration tconf2 : TypeConfiguration.values()) {
                for (TypeConfiguration tconf3 : TypeConfiguration.values()) {
                    new T7042566(tconf1, tconf2, tconf3).compileAndCheck();
                }
            }
        }

        System.out.println("Total checks made: " + checkCount);
        System.out.println("Bytecode checks made: " + bytecodeCheckCount);
    }

    enum TypeKind {
        OBJECT("Object", "(Object)null", "Ljava/lang/Object;"),
        STRING("String", "(String)null", "Ljava/lang/String;");

        String typeString;
        String valueString;
        String bytecodeString;

        TypeKind(String typeString, String valueString, String bytecodeString) {
            this.typeString = typeString;
            this.valueString = valueString;
            this.bytecodeString = bytecodeString;
        }

        boolean isSubtypeOf(TypeKind that) {
            return that == OBJECT ||
                    (that == STRING && this == STRING);
        }
    }

    enum TypeConfiguration {
        A(TypeKind.OBJECT),
        B(TypeKind.STRING),
        AA(TypeKind.OBJECT, TypeKind.OBJECT),
        AB(TypeKind.OBJECT, TypeKind.STRING),
        BA(TypeKind.STRING, TypeKind.OBJECT),
        BB(TypeKind.STRING, TypeKind.STRING),
        AAA(TypeKind.OBJECT, TypeKind.OBJECT, TypeKind.OBJECT),
        AAB(TypeKind.OBJECT, TypeKind.OBJECT, TypeKind.STRING),
        ABA(TypeKind.OBJECT, TypeKind.STRING, TypeKind.OBJECT),
        ABB(TypeKind.OBJECT, TypeKind.STRING, TypeKind.STRING),
        BAA(TypeKind.STRING, TypeKind.OBJECT, TypeKind.OBJECT),
        BAB(TypeKind.STRING, TypeKind.OBJECT, TypeKind.STRING),
        BBA(TypeKind.STRING, TypeKind.STRING, TypeKind.OBJECT),
        BBB(TypeKind.STRING, TypeKind.STRING, TypeKind.STRING);

        List<TypeKind> typeKindList;
        String expressionListStr;
        String parameterListStr;
        String bytecodeSigStr;

        private TypeConfiguration(TypeKind... typeKindList) {
            this.typeKindList = List.from(typeKindList);
            expressionListStr = asExpressionList();
            parameterListStr = asParameterList();
            bytecodeSigStr = asBytecodeString();
        }

        private String asExpressionList() {
            StringBuilder buf = new StringBuilder();
            String sep = "";
            for (TypeKind tk : typeKindList) {
                buf.append(sep);
                buf.append(tk.valueString);
                sep = ",";
            }
            return buf.toString();
        }

        private String asParameterList() {
            StringBuilder buf = new StringBuilder();
            String sep = "";
            int count = 0;
            for (TypeKind arg : typeKindList) {
                buf.append(sep);
                buf.append(arg.typeString);
                if (count == (typeKindList.size() - 1)) {
                    buf.append("...");
                }
                buf.append(" ");
                buf.append("arg" + count++);
                sep = ",";
            }
            return buf.toString();
        }

        private String asBytecodeString() {
            StringBuilder buf = new StringBuilder();
            int count = 0;
            for (TypeKind arg : typeKindList) {
                if (count == (typeKindList.size() - 1)) {
                    buf.append("[");
                }
                buf.append(arg.bytecodeString);
                count++;
            }
            return buf.toString();
        }
    }

    static class VarargsMethod {
        TypeConfiguration parameterTypes;

        public VarargsMethod(TypeConfiguration parameterTypes) {
            this.parameterTypes = parameterTypes;
        }

        @Override
        public String toString() {
            return "void m( " + parameterTypes.parameterListStr + ") {}";
        }

        boolean isApplicable(TypeConfiguration that) {
            List<TypeKind> actuals = that.typeKindList;
            List<TypeKind> formals = parameterTypes.typeKindList;
            if ((actuals.size() - formals.size()) < -1)
                return false; //not enough args
            for (TypeKind actual : actuals) {
                if (!actual.isSubtypeOf(formals.head))
                    return false; //type mismatch
                formals = formals.tail.isEmpty() ?
                    formals :
                    formals.tail;
            }
            return true;
        }

        boolean isMoreSpecificThan(VarargsMethod that) {
            List<TypeKind> actuals = parameterTypes.typeKindList;
            List<TypeKind> formals = that.parameterTypes.typeKindList;
            int checks = 0;
            int expectedCheck = Math.max(actuals.size(), formals.size());
            while (checks < expectedCheck) {
                if (!actuals.head.isSubtypeOf(formals.head))
                    return false; //type mismatch
                formals = formals.tail.isEmpty() ?
                    formals :
                    formals.tail;
                actuals = actuals.tail.isEmpty() ?
                    actuals :
                    actuals.tail;
                checks++;
            }
            return true;
        }
    }

    static class ErrorChecker implements javax.tools.DiagnosticListener<JavaFileObject> {

        boolean errorFound;
        List<String> errDiags = List.nil();

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errDiags = errDiags.append(diagnostic.getMessage(Locale.getDefault()));
                errorFound = true;
            }
        }

        String printDiags() {
            StringBuilder buf = new StringBuilder();
            for (String s : errDiags) {
                buf.append(s);
                buf.append("\n");
            }
            return buf.toString();
        }
    }
}
