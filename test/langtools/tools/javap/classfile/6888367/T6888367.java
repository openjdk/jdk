/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.constant.*;
import java.nio.file.Paths;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.*;

/*
 * @test
 * @bug 6888367
 * @summary classfile library parses signature attributes incorrectly
 * @enablePreview
 * @modules java.base/jdk.internal.classfile.impl
 */

/*
 * This test is a pretty detailed test both of javac signature generation and classfile
 * signature parsing.  The first part of the test tests all the examples given in the
 * second part of the test. Each example comes with one or two annotations, @Desc, @Sig,
 * for the descriptor and signature of the annotated declaration.  Annotations are
 * provided whenever the annotated item is expected to have a corresponding value.
 * Each annotation has two argument values.  The first arg is the expected value of the
 * descriptor/signature as found in the class file.  This value is mostly for documentation
 * purposes in reading the test.  The second value is the rendering of the descriptor or
 * signature using a custom Type visitor that explicitly includes an indication of the
 * Type classes being used to represent the  descriptor/signature.  Thus we test
 * that the descriptor/signature is being parsed into the expected type tree structure.
 */
public class T6888367 {

    public static void main(String... args) throws Exception {
        new T6888367().run();
    }

    public void run() throws Exception {
        ClassModel cm = getClassFile("Test");

        testFields(cm);
        testMethods(cm);
        testInnerClasses(cm); // recursive

        if (errors > 0)
            throw new Exception(errors + " errors found");
    }

    void testFields(ClassModel cm) throws Exception {
        String cn = cm.thisClass().name().stringValue();
        for (FieldModel fm: cm.fields()) {
            test("field " + cn + "." + fm.fieldName(), fm.fieldTypeSymbol(), fm);
        }
    }

    void testMethods(ClassModel cm) throws Exception {
        String cn = cm.thisClass().name().stringValue();
        for (MethodModel mm: cm.methods()) {
            test("method " + cn + "." + mm.methodName(), mm.methodTypeSymbol(), mm);
        }
    }

    void testInnerClasses(ClassModel cm) throws Exception {
        InnerClassesAttribute ic =
                cm.findAttribute(Attributes.INNER_CLASSES).orElse(null);
        assert ic != null;
        for (InnerClassInfo info: ic.classes()) {
            ClassEntry outerClass = info.outerClass().orElse(null);
            if (outerClass == null || !outerClass.name().equalsString(cm.getClass().getName())) {
                continue;
            }
            String innerClassName = info.innerClass().asInternalName();
            ClassModel icm = getClassFile(innerClassName);
            test("class " + innerClassName, null, icm);
            testInnerClasses(icm);
        }
    }

    void test(String name, ConstantDesc desc, AttributedElement m) {
        AnnotValues d = getDescValue(m);
        AnnotValues s = getSigValue(m);
        if (d == null && s == null) // not a test field or method if no @Desc or @Sig given
            return;

        System.err.println(name);
        SignatureAttribute sa = m.findAttribute(Attributes.SIGNATURE).orElse(null);
        if (sa != null)
            System.err.println("     signature: " + sa.signature());

        switch (desc) {
            case ClassDesc cDesc -> {
                System.err.println("    descriptor: " + cDesc.descriptorString());
                checkEqual(d.raw, cDesc.descriptorString());
                Signature dt = Signature.of(cDesc);
                checkEqual(d.type, tp.print(dt));
                if (s != null || sa != null) {
                    if (s != null && sa != null) {
                        checkEqual(s.raw, sa.signature().stringValue());
                        Signature st = Signature.parseFrom(sa.signature().stringValue());
                        checkEqual(s.type, tp.print(st));
                    } else if (s != null)
                        error("@Sig annotation found but not Signature attribute");
                    else
                        error("Signature attribute found but no @Sig annotation");
                }
            }
            case MethodTypeDesc mDesc -> {
                System.err.println("    descriptor: " + mDesc.descriptorString());
                checkEqual(d.raw, mDesc.descriptorString());
                MethodSignature mdt = MethodSignature.of(mDesc);
                checkEqual(d.type, tp.print(mdt));
                if (s != null || sa != null) {
                    if (s != null && sa != null) {
                        checkEqual(s.raw, sa.signature().stringValue());
                        MethodSignature mst = MethodSignature.parseFrom(sa.signature().stringValue());
                        checkEqual(s.type, tp.print(mst));
                    } else if (s != null)
                        error("@Sig annotation found but not Signature attribute");
                    else
                        error("Signature attribute found but no @Sig annotation");
                }
            }
            default -> throw new AssertionError();
        }
        System.err.println();
    }


    ClassModel getClassFile(String name) throws IOException, URISyntaxException {
        URL rsc = getClass().getResource(name + ".class");
        assert rsc != null;
        return ClassFile.of().parse(Paths.get(rsc.toURI()));
    }

    AnnotValues getDescValue(AttributedElement m) {
        return getAnnotValues(Desc.class.getName(), m);
    }

    AnnotValues getSigValue(AttributedElement m) {
        return getAnnotValues(Sig.class.getName(), m);
    }

    static class AnnotValues {
        AnnotValues(String raw, String type) {
            this.raw = raw;
            this.type = type;
        }
        final String raw;
        final String type;
    }

    AnnotValues getAnnotValues(String annotName, AttributedElement m) {
        RuntimeInvisibleAnnotationsAttribute annots = m.findAttribute(Attributes.RUNTIME_INVISIBLE_ANNOTATIONS).orElse(null);
        if (annots != null) {
            for (Annotation a: annots.annotations()) {
                if (a.classSymbol().descriptorString().equals("L" + annotName + ";")) {
                    String pv0 = ((AnnotationValue.OfString) a.elements().get(0).value()).stringValue();
                    String pv1 = ((AnnotationValue.OfString) a.elements().get(1).value()).stringValue();
                    return new AnnotValues(pv0, pv1);
                }
            }
        }
        return null;

    }

    void checkEqual(String expect, String found) {
        if (!(Objects.equals(expect, found))) {
            System.err.println("expected: " + expect);
            System.err.println("   found: " + found);
            error("unexpected values found");
        }
    }

    void error(String msg) {
        System.err.println("error: " + msg);
        errors++;
    }

    int errors;

    TypePrinter tp = new TypePrinter();

    class TypePrinter {
        <T> String print(T t) {
            switch (t) {
                case Signature.BaseTypeSig type -> {
                    return visitSimpleType(type);
                }
                case Signature.ArrayTypeSig type -> {
                    return visitArrayType(type);
                }
                case Signature.ClassTypeSig type -> {
                    return visitClassType(type);
                }
                case ClassSignature type -> {
                    return visitClassSigType(type);
                }
                case MethodSignature type -> {
                    return visitMethodType(type);
                }
                case Signature.TypeVarSig type -> {
                    return "S{" + type.identifier() + "}"; //Consider the TypeVarSig as Simple Type
                }
                default -> {
                    return null;
                }
            }
        }
        <T> String print(String pre, List<T> ts, String post) {
            if (ts == null)
                return null;
            StringBuilder sb = new StringBuilder();
            sb.append(pre);
            String sep = "";
            for (T t: ts) {
                sb.append(sep);
                switch (t) {
                    case Signature sig -> sb.append(print(sig));
                    case Signature.TypeParam pSig -> sb.append(visitTypeParamType(pSig));
                    case Signature.TypeArg aSig -> sb.append(visitWildcardType(aSig));
                    default -> throw new AssertionError();
                }
                sep = ",";
            }
            sb.append(post);
            return sb.toString();
        }

        public String visitSimpleType(Signature.BaseTypeSig type) {
            return "S{" + type.baseType() + "}";
        }

        public String visitArrayType(Signature.ArrayTypeSig type) {
            return "A{" + print(type.componentSignature()) + "}";
        }

        public String visitMethodType(MethodSignature type) {
            StringBuilder sb = new StringBuilder();
            sb.append("M{");
            if (!type.typeParameters().isEmpty())
                sb.append(print("<", type.typeParameters(), ">"));
            sb.append(print(type.result()));
            sb.append(print("(", type.arguments(), ")"));
            if (!type.throwableSignatures().isEmpty())
                sb.append(print("", type.throwableSignatures(), ""));
            sb.append("}");
            return sb.toString();
        }

        public String visitClassSigType(ClassSignature type) {
            StringBuilder sb = new StringBuilder();
            sb.append("CS{");
            if (!type.typeParameters().isEmpty())
                sb.append(print("<", type.typeParameters(), ">"));
            sb.append(print(type.superclassSignature()));
            if (!type.superinterfaceSignatures().isEmpty())
                sb.append(print("i(", type.superinterfaceSignatures(), ")"));
            sb.append("}");
            return sb.toString();
        }

        public String visitClassType(Signature.ClassTypeSig type) {
            StringBuilder sb = new StringBuilder();
            sb.append("C{");
            if (type.outerType().isPresent()) {
                sb.append(print(type.outerType().get()));
                sb.append(".");
            }
            sb.append(type.className());
            if (!type.typeArgs().isEmpty())
                sb.append(print("<", type.typeArgs(), ">"));
            sb.append("}");
            return sb.toString();
        }

        public String visitTypeParamType(Signature.TypeParam type) {
            StringBuilder sb = new StringBuilder();
            sb.append("TA{");
            sb.append(type.identifier());
            if (type.classBound().isPresent()) {
                sb.append(":c");
                sb.append(print(type.classBound().get()));
            }
            if (!type.interfaceBounds().isEmpty())
                sb.append(print(":i", type.interfaceBounds(), ""));
            sb.append("}");
            return sb.toString();
        }

        public String visitWildcardType(Signature.TypeArg type) {
            switch (type.wildcardIndicator()) {
                case UNBOUNDED -> {
                    return "W{?}";
                }
                case EXTENDS -> {
                    return "W{e," + print(type.boundType().get()) + "}";
                }
                case SUPER -> {
                    return "W{s," + print(type.boundType().get()) + "}";
                }
                default -> {
                    if (type.boundType().isPresent()) return print(type.boundType().get());
                    else throw new AssertionError();
                }
            }
        }

    };
}


@interface Desc {
    String d();
    String t();
}

@interface Sig {
    String s();
    String t();
}

class Clss { }
interface Intf { }
class GenClss<T> { }

class Test {
    // fields

    @Desc(d="Z", t="S{Z}")
    boolean z;

    @Desc(d="B", t="S{B}")
    byte b;

    @Desc(d="C", t="S{C}")
    char c;

    @Desc(d="D", t="S{D}")
    double d;

    @Desc(d="F", t="S{F}")
    float f;

    @Desc(d="I", t="S{I}")
    int i;

    @Desc(d="J", t="S{J}")
    long l;

    @Desc(d="S", t="S{S}")
    short s;

    @Desc(d="LClss;", t="C{Clss}")
    Clss clss;

    @Desc(d="LIntf;", t="C{Intf}")
    Intf intf;

    @Desc(d="[I", t="A{S{I}}")
    int[] ai;

    @Desc(d="[LClss;", t="A{C{Clss}}")
    Clss[] aClss;

    @Desc(d="LGenClss;", t="C{GenClss}")
    @Sig(s="LGenClss<LClss;>;", t="C{GenClss<C{Clss}>}")
    GenClss<Clss> genClass;

    // methods, return types

    @Desc(d="()V", t="M{S{V}()}")
    void mv0() { }

    @Desc(d="()I", t="M{S{I}()}")
    int mi0() { return 0; }

    @Desc(d="()LClss;", t="M{C{Clss}()}")
    Clss mclss0() { return null; }

    @Desc(d="()[I", t="M{A{S{I}}()}")
    int[] mai0() { return null; }

    @Desc(d="()[LClss;", t="M{A{C{Clss}}()}")
    Clss[] maClss0() { return null; }

    @Desc(d="()LGenClss;", t="M{C{GenClss}()}")
    @Sig(s="()LGenClss<LClss;>;", t="M{C{GenClss<C{Clss}>}()}")
    GenClss<Clss> mgenClss0() { return null; }

    @Desc(d="()LGenClss;", t="M{C{GenClss}()}")
    @Sig(s="()LGenClss<*>;", t="M{C{GenClss<W{?}>}()}")
    GenClss<?> mgenClssW0() { return null; }

    @Desc(d="()LGenClss;", t="M{C{GenClss}()}")
    @Sig(s="()LGenClss<+LClss;>;", t="M{C{GenClss<W{e,C{Clss}}>}()}")
    GenClss<? extends Clss> mgenClssWExtClss0() { return null; }

    @Desc(d="()LGenClss;", t="M{C{GenClss}()}")
    @Sig(s="()LGenClss<-LClss;>;", t="M{C{GenClss<W{s,C{Clss}}>}()}")
    GenClss<? super Clss> mgenClssWSupClss0() { return null; }

    @Desc(d="()Ljava/lang/Object;", t="M{C{java/lang/Object}()}")
    @Sig(s="<T:Ljava/lang/Object;>()TT;", t="M{<TA{T:cC{java/lang/Object}}>S{T}()}")
    <T> T mt0() { return null; }

    @Desc(d="()LGenClss;", t="M{C{GenClss}()}")
    @Sig(s="<T:Ljava/lang/Object;>()LGenClss<+TT;>;",
        t="M{<TA{T:cC{java/lang/Object}}>C{GenClss<W{e,S{T}}>}()}")
    <T> GenClss<? extends T> mgenClssWExtT0() { return null; }

    @Desc(d="()LGenClss;", t="M{C{GenClss}()}")
    @Sig(s="<T:Ljava/lang/Object;>()LGenClss<-TT;>;", t="M{<TA{T:cC{java/lang/Object}}>C{GenClss<W{s,S{T}}>}()}")
    <T> GenClss<? super T> mgenClssWSupT0() { return null; }

    // methods, arg types

    @Desc(d="(I)V", t="M{S{V}(S{I})}")
    void mi1(int arg) { }

    @Desc(d="(LClss;)V", t="M{S{V}(C{Clss})}")
    void mclss1(Clss arg) { }

    @Desc(d="([I)V", t="M{S{V}(A{S{I}})}")
    void mai1(int[] arg) { }

    @Desc(d="([LClss;)V", t="M{S{V}(A{C{Clss}})}")
    void maClss1(Clss[] arg) { }

    @Desc(d="(LGenClss;)V", t="M{S{V}(C{GenClss})}")
    @Sig(s="(LGenClss<LClss;>;)V", t="M{S{V}(C{GenClss<C{Clss}>})}")
    void mgenClss1(GenClss<Clss> arg) { }

    @Desc(d="(LGenClss;)V", t="M{S{V}(C{GenClss})}")
    @Sig(s="(LGenClss<*>;)V", t="M{S{V}(C{GenClss<W{?}>})}")
    void mgenClssW1(GenClss<?> arg) { }

    @Desc(d="(LGenClss;)V", t="M{S{V}(C{GenClss})}")
    @Sig(s="(LGenClss<+LClss;>;)V", t="M{S{V}(C{GenClss<W{e,C{Clss}}>})}")
    void mgenClssWExtClss1(GenClss<? extends Clss> arg) { }

    @Desc(d="(LGenClss;)V", t="M{S{V}(C{GenClss})}")
    @Sig(s="(LGenClss<-LClss;>;)V", t="M{S{V}(C{GenClss<W{s,C{Clss}}>})}")
    void mgenClssWSupClss1(GenClss<? super Clss> arg) { }

    @Desc(d="(Ljava/lang/Object;)V", t="M{S{V}(C{java/lang/Object})}")
    @Sig(s="<T:Ljava/lang/Object;>(TT;)V",
        t="M{<TA{T:cC{java/lang/Object}}>S{V}(S{T})}")
    <T> void mt1(T arg) { }

    @Desc(d="(LGenClss;)V", t="M{S{V}(C{GenClss})}")
    @Sig(s="<T:Ljava/lang/Object;>(LGenClss<+TT;>;)V",
        t="M{<TA{T:cC{java/lang/Object}}>S{V}(C{GenClss<W{e,S{T}}>})}")
    <T> void mgenClssWExtT1(GenClss<? extends T> arg) { }

    @Desc(d="(LGenClss;)V", t="M{S{V}(C{GenClss})}")
    @Sig(s="<T:Ljava/lang/Object;>(LGenClss<-TT;>;)V",
        t="M{<TA{T:cC{java/lang/Object}}>S{V}(C{GenClss<W{s,S{T}}>})}")
    <T> void mgenClssWSupT1(GenClss<? super T> arg) { }

    // methods, throws

    @Desc(d="()V", t="M{S{V}()}")
    void m_E() throws Exception { }

    @Desc(d="()V", t="M{S{V}()}")
    @Sig(s="<T:Ljava/lang/Throwable;>()V^TT;",
        t="M{<TA{T:cC{java/lang/Throwable}}>S{V}()S{T}}")
    <T extends Throwable> void m_T() throws T { }

    // inner classes

    static class X {
        // no sig
        class P { }

        @Sig(s="<TQ:Ljava/lang/Object;>LTest$X$P;",
            t="CS{<TA{TQ:cC{java/lang/Object}}>C{Test$X$P}}")
        class Q<TQ> extends P { }

        @Sig(s="<TR:Ljava/lang/Object;>LTest$X$Q<TTR;>;",
            t="CS{<TA{TR:cC{java/lang/Object}}>C{Test$X$Q<S{TR}>}}")
        class R<TR> extends Q<TR> { }
    }

    @Sig(s="<TY:Ljava/lang/Object;>Ljava/lang/Object;",
        t="CS{<TA{TY:cC{java/lang/Object}}>C{java/lang/Object}}")
    static class Y<TY> {
        // no sig
        class P { }

        @Sig(s="<TQ:Ljava/lang/Object;>LTest$Y<TTY;>.P;",
            t="CS{<TA{TQ:cC{java/lang/Object}}>C{C{Test$Y<S{TY}>}.P}}")
        class Q<TQ> extends P { }

        @Sig(s="<TR:Ljava/lang/Object;>LTest$Y<TTY;>.Q<TTR;>;",
            t="CS{<TA{TR:cC{java/lang/Object}}>C{C{Test$Y<S{TY}>}.Q<S{TR}>}}")
        class R<TR> extends Q<TR> {
            // no sig
            class R1 { }

            @Sig(s="<TR2:Ljava/lang/Object;>LTest$Y<TTY;>.R<TTR;>.R1;",
                t="CS{<TA{TR2:cC{java/lang/Object}}>C{C{C{Test$Y<S{TY}>}.R<S{TR}>}.R1}}")
            class R2<TR2> extends R1 { }
        }

        @Sig(s="LTest$Y<TTY;>.Q<TTY;>;", t="C{C{Test$Y<S{TY}>}.Q<S{TY}>}")
        class S extends Q<TY> {
            // no sig
            class S1 { }

            @Sig(s="<TS2:Ljava/lang/Object;>LTest$Y<TTY;>.S.S1;",
                t="CS{<TA{TS2:cC{java/lang/Object}}>C{C{C{Test$Y<S{TY}>}.S}.S1}}")
            class S2<TS2> extends S1 { }

            @Sig(s="LTest$Y<TTY;>.S.S2<TTY;>;",
                t="C{C{C{Test$Y<S{TY}>}.S}.S2<S{TY}>}")
            class S3 extends S2<TY> { }
        }
    }
}


