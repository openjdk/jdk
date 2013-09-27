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
 * @bug 8002099 8006694
 * @summary Add support for intersection types in cast expression
 *  temporarily workaround combo tests are causing time out in several platforms
 * @library ../../lib
 * @build JavacTestingAbstractThreadedTest
 * @run main/othervm/timeout=360 IntersectionTypeCastTest
 */

// use /othervm to avoid jtreg timeout issues (CODETOOLS-7900047)
// see JDK-8006746

import java.net.URI;
import java.util.Arrays;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

public class IntersectionTypeCastTest
    extends JavacTestingAbstractThreadedTest
    implements Runnable {

    interface Type {
        boolean subtypeOf(Type that);
        String asString();
        boolean isClass();
        boolean isInterface();
    }

    enum InterfaceKind implements Type {
        A("interface A { }\n", "A", null),
        B("interface B { }\n", "B", null),
        C("interface C extends A { }\n", "C", A);

        String declStr;
        String typeStr;
        InterfaceKind superInterface;

        InterfaceKind(String declStr, String typeStr,
                InterfaceKind superInterface) {
            this.declStr = declStr;
            this.typeStr = typeStr;
            this.superInterface = superInterface;
        }

        @Override
        public boolean subtypeOf(Type that) {
            return this == that || superInterface == that ||
                   that == ClassKind.OBJECT;
        }

        @Override
        public String asString() {
            return typeStr;
        }

        @Override
        public boolean isClass() {
            return false;
        }

        @Override
        public boolean isInterface() {
            return true;
        }
    }

    enum ClassKind implements Type {
        OBJECT(null, "Object"),
        CA("#M class CA implements A { }\n", "CA",
           InterfaceKind.A),
        CB("#M class CB implements B { }\n", "CB",
           InterfaceKind.B),
        CAB("#M class CAB implements A, B { }\n", "CAB",
            InterfaceKind.A, InterfaceKind.B),
        CC("#M class CC implements C { }\n", "CC",
           InterfaceKind.C, InterfaceKind.A),
        CCA("#M class CCA implements C, A { }\n", "CCA",
            InterfaceKind.C, InterfaceKind.A),
        CCB("#M class CCB implements C, B { }\n", "CCB",
            InterfaceKind.C, InterfaceKind.A, InterfaceKind.B),
        CCAB("#M class CCAB implements C, A, B { }\n", "CCAB",
             InterfaceKind.C, InterfaceKind.A, InterfaceKind.B);

        String declTemplate;
        String typeStr;
        List<InterfaceKind> superInterfaces;

        ClassKind(String declTemplate, String typeStr,
                InterfaceKind... superInterfaces) {
            this.declTemplate = declTemplate;
            this.typeStr = typeStr;
            this.superInterfaces = List.from(superInterfaces);
        }

        String getDecl(ModifierKind mod) {
            return declTemplate != null ?
                    declTemplate.replaceAll("#M", mod.modStr) :
                    "";
        }

        @Override
        public boolean subtypeOf(Type that) {
            return this == that || superInterfaces.contains(that) ||
                    that == OBJECT;
        }

        @Override
        public String asString() {
            return typeStr;
        }

        @Override
        public boolean isClass() {
            return true;
        }

        @Override
        public boolean isInterface() {
            return false;
        }
    }

    enum ModifierKind {
        NONE(""),
        FINAL("final");

        String modStr;

        ModifierKind(String modStr) {
            this.modStr = modStr;
        }
    }

    enum CastKind {
        CLASS("(#C)", 0),
        INTERFACE("(#I0)", 1),
        INTERSECTION2("(#C & #I0)", 1),
        INTERSECTION3("(#C & #I0 & #I1)", 2);
        //INTERSECTION4("(#C & #I0 & #I1 & #I2)", 3);

        String castTemplate;
        int interfaceBounds;

        CastKind(String castTemplate, int interfaceBounds) {
            this.castTemplate = castTemplate;
            this.interfaceBounds = interfaceBounds;
        }
    }

    static class CastInfo {
        CastKind kind;
        Type[] types;

        CastInfo(CastKind kind, Type... types) {
            this.kind = kind;
            this.types = types;
        }

        String getCast() {
            String temp = kind.castTemplate.replaceAll("#C",
                    types[0].asString());
            for (int i = 0; i < kind.interfaceBounds ; i++) {
                temp = temp.replace(String.format("#I%d", i),
                                    types[i + 1].asString());
            }
            return temp;
        }

        boolean hasDuplicateTypes() {
            for (int i = 0 ; i < types.length ; i++) {
                for (int j = 0 ; j < types.length ; j++) {
                    if (i != j && types[i] == types[j]) {
                        return true;
                    }
                }
            }
            return false;
        }

        boolean compatibleWith(ModifierKind mod, CastInfo that) {
            for (Type t1 : types) {
                for (Type t2 : that.types) {
                    boolean compat =
                            t1.subtypeOf(t2) ||
                            t2.subtypeOf(t1) ||
                            (t1.isInterface() && t2.isInterface()) || //side-cast (1)
                            (mod == ModifierKind.NONE &&
                            (t1.isInterface() != t2.isInterface())); //side-cast (2)
                    if (!compat) return false;
                }
            }
            return true;
        }
    }

    public static void main(String... args) throws Exception {
        for (ModifierKind mod : ModifierKind.values()) {
            for (CastInfo cast1 : allCastInfo()) {
                for (CastInfo cast2 : allCastInfo()) {
                    pool.execute(
                        new IntersectionTypeCastTest(mod, cast1, cast2));
                }
            }
        }
        checkAfterExec();
    }

    static List<CastInfo> allCastInfo() {
        ListBuffer<CastInfo> buf = new ListBuffer<>();
        for (CastKind kind : CastKind.values()) {
            for (ClassKind clazz : ClassKind.values()) {
                if (kind == CastKind.INTERFACE && clazz != ClassKind.OBJECT) {
                    continue;
                } else if (kind.interfaceBounds == 0) {
                    buf.append(new CastInfo(kind, clazz));
                    continue;
                } else {
                    for (InterfaceKind intf1 : InterfaceKind.values()) {
                        if (kind.interfaceBounds == 1) {
                            buf.append(new CastInfo(kind, clazz, intf1));
                            continue;
                        } else {
                            for (InterfaceKind intf2 : InterfaceKind.values()) {
                                if (kind.interfaceBounds == 2) {
                                    buf.append(
                                            new CastInfo(kind, clazz, intf1, intf2));
                                    continue;
                                } else {
                                    for (InterfaceKind intf3 : InterfaceKind.values()) {
                                        buf.append(
                                                new CastInfo(kind, clazz, intf1,
                                                             intf2, intf3));
                                        continue;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return buf.toList();
    }

    ModifierKind mod;
    CastInfo cast1, cast2;
    JavaSource source;
    DiagnosticChecker diagChecker;

    IntersectionTypeCastTest(ModifierKind mod, CastInfo cast1, CastInfo cast2) {
        this.mod = mod;
        this.cast1 = cast1;
        this.cast2 = cast2;
        this.source = new JavaSource();
        this.diagChecker = new DiagnosticChecker();
    }

    @Override
    public void run() {
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();

        JavacTask ct = (JavacTask)tool.getTask(null, fm.get(), diagChecker,
                null, null, Arrays.asList(source));
        try {
            ct.analyze();
        } catch (Throwable ex) {
            throw new AssertionError("Error thrown when compiling the following code:\n" +
                    source.getCharContent(true));
        }
        check();
    }

    class JavaSource extends SimpleJavaFileObject {

        String bodyTemplate = "class Test {\n" +
                              "   void test() {\n" +
                              "      Object o = #C1#C2null;\n" +
                              "   } }";

        String source = "";

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            for (ClassKind ck : ClassKind.values()) {
                source += ck.getDecl(mod);
            }
            for (InterfaceKind ik : InterfaceKind.values()) {
                source += ik.declStr;
            }
            source += bodyTemplate.replaceAll("#C1", cast1.getCast()).
                    replaceAll("#C2", cast2.getCast());
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    void check() {
        checkCount.incrementAndGet();

        boolean errorExpected = cast1.hasDuplicateTypes() ||
                cast2.hasDuplicateTypes();

        errorExpected |= !cast2.compatibleWith(mod, cast1);

        if (errorExpected != diagChecker.errorFound) {
            throw new Error("invalid diagnostics for source:\n" +
                source.getCharContent(true) +
                "\nFound error: " + diagChecker.errorFound +
                "\nExpected error: " + errorExpected);
        }
    }

    static class DiagnosticChecker
        implements javax.tools.DiagnosticListener<JavaFileObject> {

        boolean errorFound;

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errorFound = true;
            }
        }
    }

}
