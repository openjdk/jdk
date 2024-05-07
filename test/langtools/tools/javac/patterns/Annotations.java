/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8256266 8281238
 * @summary Verify annotations work correctly on binding variables
 * @library /tools/javac/lib
 * @enablePreview
 * @modules java.compiler
 *          jdk.compiler
 *          java.base/jdk.internal.classfile.impl
 * @build JavacTestingAbstractProcessor
 * @compile Annotations.java
 * @compile -J--enable-preview -processor Annotations -proc:only Annotations.java
 * @run main Annotations
 */

import com.sun.source.tree.InstanceOfTree;
import com.sun.source.util.TreePath;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.io.InputStream;

public class Annotations extends JavacTestingAbstractProcessor {
    public static void main(String... args) throws Exception {
        new Annotations().run();
    }

    void run() throws Exception {
        InputStream annotationsClass =
                Annotations.class.getResourceAsStream("Annotations.class");
        assert annotationsClass != null;
        ClassModel cf = ClassFile.of().parse(annotationsClass.readAllBytes());
        for (MethodModel m : cf.methods()) {
            if (m.methodName().equalsString("test")) {
                CodeAttribute codeAttr = m.findAttribute(Attributes.CODE).orElseThrow();
                RuntimeInvisibleTypeAnnotationsAttribute annotations = codeAttr.findAttribute(Attributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS).orElseThrow();
                String expected = "LAnnotations$DTA; pos: [LOCAL_VARIABLE, {start_pc=31, end_pc=38, index=1}], " +
                                  "LAnnotations$TA; pos: [LOCAL_VARIABLE, {start_pc=50, end_pc=57, index=1}], ";
                StringBuilder actual = new StringBuilder();
                for (TypeAnnotation ta: annotations.annotations()) {
                    TypeAnnotation.LocalVarTargetInfo info = ((TypeAnnotation.LocalVarTarget) ta.targetInfo()).table().getFirst();
                    actual.append(ta.className().stringValue() + " pos: [" + ta.targetInfo().targetType());
                    actual.append(", {start_pc=" + codeAttr.labelToBci(info.startLabel()) + ", end_pc=" + codeAttr.labelToBci(info.endLabel()));
                    actual.append(", index=" + info.index()+ "}], ");
                }
                if (!expected.contentEquals(actual)) {
                    throw new AssertionError("Unexpected type annotations: " +
                                              actual);
                }
            }
        }
    }

    private static void test(Object o) {
        if (o instanceof @DA String da) {
            System.err.println(da);
        }
        if (o instanceof @DTA String dta) {
            System.err.println(dta);
        }
        if (o instanceof @TA String ta) {
            System.err.println(ta);
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Trees trees = Trees.instance(processingEnv);

        for (Element root : roundEnv.getRootElements()) {
            TreePath tp = trees.getPath(root);
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitInstanceOf(InstanceOfTree node, Void p) {
                    BindingPatternTree bpt = (BindingPatternTree) node.getPattern();
                    VariableTree var = bpt.getVariable();
                    Element varEl = trees.getElement(new TreePath(getCurrentPath(), var));
                    String expectedDeclAnnos;
                    String expectedType;
                    switch (var.getName().toString()) {
                        case "da" -> {
                            expectedDeclAnnos = "@Annotations.DA";
                            expectedType = "java.lang.String";
                        }
                        case "dta" -> {
                            expectedDeclAnnos = "@Annotations.DTA";
                            expectedType = "java.lang.@Annotations.DTA String";
                        }
                        case "ta" -> {
                            expectedDeclAnnos = "";
                            expectedType = "java.lang.@Annotations.TA String";
                        }
                        default -> {
                            throw new AssertionError("Unexpected variable: " + var);
                        }
                    }
                    String declAnnos = varEl.getAnnotationMirrors().toString();
                    if (!expectedDeclAnnos.equals(declAnnos)) {
                        throw new AssertionError("Unexpected modifiers: " + declAnnos +
                                                  " for: " + var.getName());
                    }
                    TypeMirror varType = varEl.asType();
                    String type = varType.toString();
                    if (!expectedType.equals(type)) {
                        throw new AssertionError("Unexpected type: " + type +
                                                  " for: " + var.getName() + " expected " + expectedType);
                    }
                    return super.visitInstanceOf(node, p);
                }
            }.scan(tp.getCompilationUnit(), null);
        }
        return false;
    }

    @Target(ElementType.LOCAL_VARIABLE)
    @interface DA {}
    @Target({ElementType.TYPE_USE, ElementType.LOCAL_VARIABLE})
    @interface DTA {}
    @Target(ElementType.TYPE_USE)
    @interface TA {}
}

