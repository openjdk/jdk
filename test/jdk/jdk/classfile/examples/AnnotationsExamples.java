/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import helpers.CorpusTestHelper;
import jdk.classfile.Annotation;
import jdk.classfile.Attributes;
import jdk.classfile.ClassBuilder;
import jdk.classfile.ClassElement;
import jdk.classfile.ClassModel;
import jdk.classfile.ClassTransform;
import jdk.classfile.Classfile;
import jdk.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import jdk.classfile.constantpool.ConstantPoolBuilder;
import jdk.classfile.components.ClassPrinter;
import org.testng.Assert;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

public class AnnotationsExamples extends CorpusTestHelper {

    @Factory(dataProvider = "corpus")
    public AnnotationsExamples(Path path) throws IOException {
        super(path);
    }

    /** Add a single annotation to a class using a builder convenience */
    @Test(enabled = true)
    public void addAnno() {
        // @@@ Not correct
        ClassModel m = Classfile.parse(bytes);
        List<Annotation> annos = List.of(Annotation.of(ClassDesc.of("java.lang.FunctionalInterface")));
        var newBytes = m.transform(ClassTransform.endHandler(cb -> cb.with(RuntimeVisibleAnnotationsAttribute.of(annos))));
        ClassModel res = Classfile.parse(newBytes);
    }

    /**
     * Find classes with annotations of a certain type
     */
    @Test(enabled = true)
    public void findAnnotation() {
        ClassModel m = Classfile.parse(bytes);

        if (m.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).isPresent()) {
            RuntimeVisibleAnnotationsAttribute a = m.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).get();
            for (Annotation ann : a.annotations()) {
                if (ann.className().stringValue().equals("Ljava/lang/FunctionalInterface;"))
                    System.out.println(m.thisClass().asInternalName());
            }
        }
    }

    /**
     * Find classes with a specific annotation and create a new byte[] with that annotation swapped for @Deprecated.
     */
    @Test(enabled = true)
    public void swapAnnotation() {
        ClassModel m = Classfile.parse(bytes);
        ClassModel m2 = m;

        if (m.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).isPresent()) {
            RuntimeVisibleAnnotationsAttribute a = m.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).get();
            for (Annotation ann : a.annotations()) {
                if (ann.className().stringValue().equals("Ljava/lang/annotation/Documented;")) {
                    m2 = Classfile.parse(m.transform(SWAP_ANNO_TRANSFORM));
                }
            }
        }

        if (m2.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).isPresent()) {
            RuntimeVisibleAnnotationsAttribute a = m2.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).get();
            for (Annotation ann : a.annotations()) {
                if (ann.className().stringValue().equals("Ljava/lang/annotation/Documented;"))
                    throw new RuntimeException();
            }
        }
    }

    //where
    private static final ClassTransform SWAP_ANNO_TRANSFORM = (cb, ce) -> {
        switch (ce) {
            case RuntimeVisibleAnnotationsAttribute attr -> {
                List<Annotation> old = attr.annotations();
                List<Annotation> newAnnos = new ArrayList<>(old.size());
                for (Annotation ann : old) {
                    if (ann.className().stringValue().equals("Ljava/lang/annotation/Documented;")) {
                        newAnnos.add(Annotation.of(ClassDesc.of("java.lang.Deprecated"), List.of()));
                    }
                    else
                        newAnnos.add(ann);
                }
                cb.with(RuntimeVisibleAnnotationsAttribute.of(newAnnos));
            }
            default -> cb.with(ce);
        }
    };

    /**
     * Find classes with a specific annotation and create a new byte[] with the same content except also adding a new annotation
     */
    @Test(enabled = true)
    public void addAnnotation() {
        ClassModel m = Classfile.parse(bytes);
        ClassModel m2 = m;

        if (m.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).isPresent()) {
            RuntimeVisibleAnnotationsAttribute a = m.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).get();
            for (Annotation ann : a.annotations()) {
                if (ann.className().stringValue().equals("Ljava/lang/FunctionalInterface;")) {
                    m2 = Classfile.parse(m.transform((cb, ce) -> {
                        if (ce instanceof RuntimeVisibleAnnotationsAttribute ra) {
                            var oldAnnos = ra.annotations();
                            List<Annotation> newAnnos = new ArrayList<>(oldAnnos.size() + 1);
                            for (Annotation aa :oldAnnos)
                                newAnnos.add(aa);
                            ConstantPoolBuilder cpb = cb.constantPool();
                            newAnnos.add(Annotation.of(ClassDesc.of("java.lang.Deprecated"), List.of()));
                            cb.with(RuntimeVisibleAnnotationsAttribute.of(newAnnos));
                        } else {
                            cb.with(ce);
                        }
                    }));
                }
            }
        }

        int size = m2.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).orElseThrow().annotations().size();
        if (size !=2) {
            StringBuilder sb = new StringBuilder();
            ClassPrinter.toJson(m2, ClassPrinter.Verbosity.TRACE_ALL, sb::append);
            System.err.println(sb.toString());
        }
    }

    @Test(enabled = true)
    public void viaEndHandlerClassBuilderEdition() {
        ClassModel m = Classfile.parse(bytes);
        byte[] transformed = m.transform(ClassTransform.ofStateful(() -> new ClassTransform() {
            boolean found = false;

            @Override
            public void accept(ClassBuilder cb, ClassElement ce) {
                switch (ce) {
                    case RuntimeVisibleAnnotationsAttribute rvaa -> {
                        found = true;
                        List<Annotation> newAnnotations = new ArrayList<>(rvaa.annotations().size() + 1);
                        newAnnotations.addAll(rvaa.annotations());
                        newAnnotations.add(Annotation.of(ClassDesc.of("Foo")));
                        cb.with(RuntimeVisibleAnnotationsAttribute.of(newAnnotations));
                    }
                    default -> cb.with(ce);
                }
            }

            @Override
            public void atEnd(ClassBuilder builder) {
                if (!found) {
                    builder.with(RuntimeVisibleAnnotationsAttribute.of(List.of(Annotation.of(ClassDesc.of("Foo")))));
                }
            }
        }));

        ClassModel n = Classfile.parse(transformed);

        // This doesn't work
            // Assert.assertTrue(m.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).isPresent(), "All classes should have annotations by now");

        // But this does
        Assert.assertTrue(n.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).isPresent(), "All classes should have annotations by now");
    }

    @Test(enabled = true)
    public void viaEndHandlerClassTransformEdition() {
        ClassModel m = Classfile.parse(bytes);

        byte[] transformed = m.transform(ClassTransform.ofStateful(() -> new ClassTransform() {
            boolean found = false;

            @Override
            public void accept(ClassBuilder cb, ClassElement ce) {
                if (ce instanceof RuntimeVisibleAnnotationsAttribute rvaa) {
                    found = true;
                    List<Annotation> newAnnotations = new ArrayList<>(rvaa.annotations().size() + 1);
                    newAnnotations.addAll(rvaa.annotations());
                    newAnnotations.add(Annotation.of(ClassDesc.of("Foo")));

                    cb.with(RuntimeVisibleAnnotationsAttribute.of(newAnnotations));
                }
                else
                    cb.with(ce);
            }

            @Override
            public void atEnd(ClassBuilder builder) {
                if (!found) {
                    builder.with(RuntimeVisibleAnnotationsAttribute.of(List.of(Annotation.of(ClassDesc.of("Foo")))));
                }
            }
        }));

        ClassModel n = Classfile.parse(transformed);

        // This doesn't work
        //Assert.assertTrue(m.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).isPresent(), "All classes should have annotations by now");

        // But this does
        Assert.assertTrue(n.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).isPresent(), "All classes should have annotations by now");
    }
}