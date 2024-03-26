/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing ClassFile annotations.
 * @run junit AnnotationTest
 */
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.*;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import org.junit.jupiter.api.Test;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import jdk.internal.classfile.impl.DirectClassBuilder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnnotationTest
 */
class AnnotationTest {
    enum E {C};

    private static Map<String, Object> constants
            = Map.ofEntries(
            new AbstractMap.SimpleImmutableEntry<>("i", 1),
            new AbstractMap.SimpleImmutableEntry<>("j", 1L),
            new AbstractMap.SimpleImmutableEntry<>("s", 1),
            new AbstractMap.SimpleImmutableEntry<>("b", 1),
            new AbstractMap.SimpleImmutableEntry<>("f", 1.0f),
            new AbstractMap.SimpleImmutableEntry<>("d", 1.0d),
            new AbstractMap.SimpleImmutableEntry<>("z", 1),
            new AbstractMap.SimpleImmutableEntry<>("c", (int) '1'),
            new AbstractMap.SimpleImmutableEntry<>("st", "1"),
            new AbstractMap.SimpleImmutableEntry<>("cl", ClassDesc.of("foo.Bar")),
            new AbstractMap.SimpleImmutableEntry<>("en", E.C),
            new AbstractMap.SimpleImmutableEntry<>("arr", new Object[] {1, "1", 1.0f})
    );

    private static final List<AnnotationElement> constantElements =
            constants.entrySet().stream()
                    .map(e -> AnnotationElement.of(e.getKey(), AnnotationValue.of(e.getValue())))
                    .toList();

    private static List<AnnotationElement> elements() {
        List<AnnotationElement> list = new ArrayList<>(constantElements);
        list.add(AnnotationElement.ofAnnotation("a", Annotation.of(ClassDesc.of("Bar"), constantElements)));
        return list;
    }

    private static boolean assertAnno(Annotation a, String annoClassDescriptor, boolean deep) {
        assertEquals(a.className().stringValue(), annoClassDescriptor);
        assertEquals(a.elements().size(), deep ? 13 : 12);
        Set<String> names = new HashSet<>();
        for (AnnotationElement evp : a.elements()) {
            names.add(evp.name().stringValue());
            switch (evp.name().stringValue()) {
                case "i", "j", "s", "b", "f", "d", "z", "c", "st":
                    assertTrue (evp.value() instanceof AnnotationValue.OfConstant c);
                    assertEquals(((AnnotationValue.OfConstant) evp.value()).constantValue(),
                                 constants.get(evp.name().stringValue()));
                    break;
                case "cl":
                    assertTrue (evp.value() instanceof AnnotationValue.OfClass c
                                && c.className().stringValue().equals("Lfoo/Bar;"));
                    break;
                case "en":
                    assertTrue (evp.value() instanceof AnnotationValue.OfEnum c
                                && c.className().stringValue().equals(E.class.descriptorString()) && c.constantName().stringValue().equals("C"));
                    break;
                case "a":
                    assertTrue (evp.value() instanceof AnnotationValue.OfAnnotation c
                                && assertAnno(c.annotation(), "LBar;", false));
                    break;
                case "arr":
                    assertTrue (evp.value() instanceof AnnotationValue.OfArray);
                    List<AnnotationValue> values = ((AnnotationValue.OfArray) evp.value()).values();
                    assertEquals(values.stream().map(v -> ((AnnotationValue.OfConstant) v).constant().constantValue()).collect(toSet()),
                                 Set.of(1, 1.0f, "1"));
                    break;
                default:
                    fail("Unexpected annotation element: " + evp.name().stringValue());

            }
        }
        assertEquals(names.size(), a.elements().size());
        return true;
    }

    private static RuntimeVisibleAnnotationsAttribute buildAnnotationsWithCPB(ConstantPoolBuilder constantPoolBuilder) {
        return RuntimeVisibleAnnotationsAttribute.of(Annotation.of(constantPoolBuilder.utf8Entry("LAnno;"), elements()));
    }

    @Test
    void testAnnos() {
        var cc = ClassFile.of();
        byte[] bytes = cc.build(ClassDesc.of("Foo"), cb -> {
            ((DirectClassBuilder) cb).writeAttribute(buildAnnotationsWithCPB(cb.constantPool()));
            cb.withMethod("foo", MethodTypeDesc.of(CD_void), 0, mb -> mb.with(buildAnnotationsWithCPB(mb.constantPool())));
            cb.withField("foo", CD_int, fb -> fb.with(buildAnnotationsWithCPB(fb.constantPool())));
        });
        ClassModel cm = cc.parse(bytes);
        List<ClassElement> ces = cm.elementList();
        List<Annotation> annos = ces.stream()
                .filter(ce -> ce instanceof RuntimeVisibleAnnotationsAttribute)
                .map(ce -> (RuntimeVisibleAnnotationsAttribute) ce)
                .flatMap(a -> a.annotations().stream())
                .collect(toList());
        List<Annotation> fannos = ces.stream()
                                     .filter(ce -> ce instanceof FieldModel)
                                     .map(ce -> (FieldModel) ce)
                                     .flatMap(ce -> ce.elementList().stream())
                                     .filter(ce -> ce instanceof RuntimeVisibleAnnotationsAttribute)
                                     .map(ce -> (RuntimeVisibleAnnotationsAttribute) ce)
                                     .flatMap(am -> am.annotations().stream())
                                     .collect(toList());
        List<Annotation> mannos = ces.stream()
                                     .filter(ce -> ce instanceof MethodModel)
                                     .map(ce -> (MethodModel) ce)
                                     .flatMap(ce -> ce.elementList().stream())
                                     .filter(ce -> ce instanceof RuntimeVisibleAnnotationsAttribute)
                                     .map(ce -> (RuntimeVisibleAnnotationsAttribute) ce)
                                     .flatMap(am -> am.annotations().stream())
                                     .collect(toList());
        assertEquals(annos.size(), 1);
        assertEquals(mannos.size(), 1);
        assertEquals(fannos.size(), 1);
        assertAnno(annos.get(0), "LAnno;", true);
        assertAnno(mannos.get(0), "LAnno;", true);
        assertAnno(fannos.get(0), "LAnno;", true);
    }

    // annotation default on methods

    private static RuntimeVisibleAnnotationsAttribute buildAnnotations() {
        return RuntimeVisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.of("Anno"),
                                                                   elements()));
    }

    @Test
    void testAnnosNoCPB() {
        var cc = ClassFile.of();
        byte[] bytes = cc.build(ClassDesc.of("Foo"), cb -> {
            ((DirectClassBuilder) cb).writeAttribute(buildAnnotations());
            cb.withMethod("foo", MethodTypeDesc.of(CD_void), 0, mb -> mb.with(buildAnnotations()));
            cb.withField("foo", CD_int, fb -> fb.with(buildAnnotations()));
        });
        ClassModel cm = cc.parse(bytes);
        List<ClassElement> ces = cm.elementList();
        List<Annotation> annos = ces.stream()
                .filter(ce -> ce instanceof RuntimeVisibleAnnotationsAttribute)
                .map(ce -> (RuntimeVisibleAnnotationsAttribute) ce)
                .flatMap(a -> a.annotations().stream())
                .toList();
        List<Annotation> fannos = ces.stream()
                .filter(ce -> ce instanceof FieldModel)
                .map(ce -> (FieldModel) ce)
                .flatMap(ce -> ce.elementList().stream())
                .filter(ce -> ce instanceof RuntimeVisibleAnnotationsAttribute)
                .map(ce -> (RuntimeVisibleAnnotationsAttribute) ce)
                .flatMap(am -> am.annotations().stream())
                .toList();
        List<Annotation> mannos = ces.stream()
                .filter(ce -> ce instanceof MethodModel)
                .map(ce -> (MethodModel) ce)
                .flatMap(ce -> ce.elementList().stream())
                .filter(ce -> ce instanceof RuntimeVisibleAnnotationsAttribute)
                .map(ce -> (RuntimeVisibleAnnotationsAttribute) ce)
                .flatMap(am -> am.annotations().stream())
                .toList();
        assertEquals(annos.size(), 1);
        assertEquals(mannos.size(), 1);
        assertEquals(fannos.size(), 1);
        assertAnno(annos.get(0), "LAnno;", true);
        assertAnno(mannos.get(0), "LAnno;", true);
        assertAnno(fannos.get(0), "LAnno;", true);
    }
}
