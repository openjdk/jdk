/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.constant.ConstantDesc;
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
import java.util.stream.Stream;

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

    // name -> (value, poolValue)
    private static final Map<String, Map.Entry<Object, ConstantDesc>> constants
            = Map.ofEntries(
            Map.entry("i", Map.entry(1, 1)),
            Map.entry("j", Map.entry(1L, 1L)),
            Map.entry("s", Map.entry((short) 1, 1)),
            Map.entry("b", Map.entry((byte) 1, 1)),
            Map.entry("f", Map.entry(1.0f, 1.0f)),
            Map.entry("d", Map.entry(1.0d, 1.0d)),
            Map.entry("z", Map.entry(true, 1)),
            Map.entry("c", Map.entry('1', (int) '1')),
            Map.entry("st", Map.entry("1", "1"))
    );

    private static final List<AnnotationElement> constantElements = Stream.concat(
            constants.entrySet().stream()
                    .map(e -> Map.entry(e.getKey(), e.getValue().getKey())),
            Stream.of(
                    Map.entry("cl", ClassDesc.of("foo.Bar")),
                    Map.entry("en", E.C),
                    Map.entry("arr", new Object[] {1, "1", 1.0f})
            ))
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
                    if (!(evp.value() instanceof AnnotationValue.OfConstant c))
                        return fail();
                    assertEquals(c.resolvedValue(),
                                 constants.get(evp.name().stringValue()).getKey());
                    assertEquals(c.constant().constantValue(),
                                 constants.get(evp.name().stringValue()).getValue());
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
                    if (!(evp.value() instanceof AnnotationValue.OfArray arr))
                        return fail();
                    List<AnnotationValue> values = arr.values();
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

    @Test
    void testEquality() {
        assertEquals(Annotation.of(CD_Object), Annotation.of(ClassDesc.of("java.lang.Object")));
        assertNotEquals(Annotation.of(CD_Object), Annotation.of(CD_String));
        assertEquals(Annotation.of(CD_Object, AnnotationElement.of("fly", AnnotationValue.ofInt(5))),
                Annotation.of(CD_Object, AnnotationElement.ofInt("fly", 5)));
        assertEquals(AnnotationElement.ofFloat("one", 1.2F),
                AnnotationElement.ofFloat("one", 1.2F));
        assertEquals(AnnotationElement.ofFloat("one", 1.2F),
                AnnotationElement.of("one", AnnotationValue.ofFloat(1.2F)));
        assertNotEquals(AnnotationElement.ofFloat("one", 1.2F),
                AnnotationElement.ofFloat("two", 1.2F));
        assertNotEquals(AnnotationElement.ofFloat("one", 1.2F),
                AnnotationElement.ofFloat("one", 2.1F));
        assertNotEquals(AnnotationElement.ofFloat("one", 1.2F),
                AnnotationElement.ofDouble("one", 1.2F));
        assertEquals(AnnotationValue.ofInt(23), AnnotationValue.ofInt(23));
        assertNotEquals(AnnotationValue.ofInt(23), AnnotationValue.ofInt(42));
        assertNotEquals(AnnotationValue.ofInt(23), AnnotationValue.ofLong(23));
        assertEquals(AnnotationValue.ofAnnotation(Annotation.of(CD_Object)),
                AnnotationValue.ofAnnotation(Annotation.of(Object.class.describeConstable().orElseThrow())));
    }
}
