/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URL;
import java.util.List;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.util.ArrayList;

public class ClassfileTestHelper {
    int expected_tinvisibles = 0;
    int expected_tvisibles = 0;
    int expected_invisibles = 0;
    int expected_visibles = 0;
    List<String> extraOptions = List.of();

    //Makes debugging much easier. Set to 'false' for less output.
    public Boolean verbose = true;
    void println(String msg) { if (verbose) System.err.println(msg); }
    void print(String msg) { if (verbose) System.err.print(msg); }

    File writeTestFile(String fname, String source) throws IOException {
      File f = new File(fname);
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)));
        out.println(source);
        out.close();
        return f;
    }

    File compile(File f) {
        List<String> options = new ArrayList<>(List.of("-g", f.getPath()));
        options.addAll(extraOptions);
        int rc = com.sun.tools.javac.Main.compile(options.toArray(new String[0]));
        if (rc != 0)
            throw new Error("compilation failed. rc=" + rc);
        String path = f.getPath();
        return new File(path.substring(0, path.length() - 5) + ".class");
    }

    ClassModel getClassFile(String name) throws IOException {
        URL url = getClass().getResource(name);
        assert url != null;
        try (InputStream in = url.openStream()) {
            return ClassFile.of().parse(in.readAllBytes());
        }
    }

    ClassModel getClassFile(URL url) throws IOException {
        try (InputStream in = url.openStream()) {
            return ClassFile.of().parse(in.readAllBytes());
        }
    }

    /************ Helper annotations counting methods ******************/
    void test(ClassModel cm) {
        test(cm, false); //For ClassModel, not look for annotations in code attr
    }
    // default to not looking in code attribute
    void test(FieldModel fm) {
        test(fm, false);
    }

    void test(MethodModel mm ) {
        test(mm, false);
    }

    // 'local' determines whether to look for annotations in code attribute or not.
    void test(AttributedElement m, Boolean local) {
        test(m, Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS, local);
        test(m, Attributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS, local);
        test(m, Attributes.RUNTIME_VISIBLE_ANNOTATIONS, local);
        test(m, Attributes.RUNTIME_INVISIBLE_ANNOTATIONS, local);
    }

    // Test the result of MethodModel.findAttribute according to expectations
    // encoded in the class/field/method name; increment annotations counts.
    <T extends Attribute<T>>void test(AttributedElement m, AttributeMapper<T> annName, Boolean local) {
        String name;
        Attribute<T> attr;
        boolean isTAattr = annName.name().contains("Type");
        switch(m) {
            case FieldModel fm -> {
                name = fm.fieldName().stringValue();
                attr = extractAnnotation(m, annName, local);
            }
            case MethodModel mm -> {
                name = mm.methodName().stringValue();
                attr = extractAnnotation(m, annName, local);
            }
            default -> {
                ClassModel cm = (ClassModel) m;
                name = cm.thisClass().asInternalName();
                attr = extractAnnotation(cm, annName, local);
            }
        }

        if (attr != null) {
            if(isTAattr) { //count RuntimeTypeAnnotations
//                List <TypeAnnotation> tAnnots = new ArrayList<TypeAnnotation>();
                switch (attr) {
                    case RuntimeVisibleTypeAnnotationsAttribute vtAttr -> {
                        List <TypeAnnotation> tAnnots = vtAttr.annotations();
                        tvisibles += tAnnots.size();
                        allt += tAnnots.size();
                    }
                    case RuntimeInvisibleTypeAnnotationsAttribute invtAttr -> {
                        System.err.println(invtAttr.annotations());
                        List <TypeAnnotation> tAnnots = invtAttr.annotations();
                        tinvisibles += tAnnots.size();
                        allt += tAnnots.size();
                    }
                    default -> throw new AssertionError();
                }
                // This snippet is simply for printlin. which are duplicated in two cases. Therefore, I want to drop it.
//                if (!tAnnots.isEmpty()) {
////                    for (TypeAnnotation tAnnot : tAnnots)
////                        println("  types:" + tAnnot.targetInfo().targetType());
////                    println("Local: " + local + ", " + name + ", " + annName + ": " + tAnnots.size());
//                    allt += tAnnots.size();
//                }
            } else {
                List <Annotation> annots;
                switch (attr) {
                    case RuntimeVisibleAnnotationsAttribute tAttr -> {
                        annots = tAttr.annotations();
                        visibles += annots.size();
                    }
                    case RuntimeInvisibleAnnotationsAttribute tAttr -> {
                        annots = tAttr.annotations();
                        invisibles += annots.size();
                    }
                    default -> throw new AssertionError();
                }
                if (!annots.isEmpty()) {
                    println("Local: " + local + ", " + name + ", " + annName + ": " + annots.size());
                    all += annots.size();
                }
            }
        }
    }
    <T extends Attribute<T>> Attribute<T> extractAnnotation(AttributedElement m, AttributeMapper<T> annName, Boolean local) {
        CodeAttribute cAttr;
        Attribute<T> attr = null;
        if (local) {
            cAttr = m.findAttribute(Attributes.CODE).orElse(null);
            if (cAttr != null) {
                attr = cAttr.findAttribute(annName).orElse(null);
            }
        } else {
            attr = m.findAttribute(annName).orElse(null);
        }
        return attr;
    }

    void countAnnotations() {
        errors=0;
        int expected_allt = expected_tvisibles + expected_tinvisibles;
        int expected_all = expected_visibles + expected_invisibles;

        if (expected_allt != allt) {
            errors++;
            System.err.println("Failure: expected " + expected_allt +
                    " type annotations but found " + allt);
        }
        if (expected_all != all) {
            errors++;
            System.err.println("Failure: expected " + expected_all +
                    " annotations but found " + all);
        }
        if (expected_tvisibles != tvisibles) {
            errors++;
            System.err.println("Failure: expected " + expected_tvisibles +
                    " typevisible annotations but found " + tvisibles);
        }

        if (expected_tinvisibles != tinvisibles) {
            errors++;
            System.err.println("Failure: expected " + expected_tinvisibles +
                    " typeinvisible annotations but found " + tinvisibles);
        }
        allt=0;
        tvisibles=0;
        tinvisibles=0;
        all=0;
        visibles=0;
        invisibles=0;
    }

    int errors;
    int allt;
    int tvisibles;
    int tinvisibles;
    int all;
    int visibles;
    int invisibles;
}
