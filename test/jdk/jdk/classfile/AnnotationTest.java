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
 * @bug 8335927
 * @summary Testing ClassFile annotations.
 * @run junit AnnotationTest
 */
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
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
            Map.entry("i", 1),
            Map.entry("j", 1L),
            Map.entry("s", (short) 1),
            Map.entry("b", (byte) 1),
            Map.entry("f", 1.0f),
            Map.entry("d", 1.0d),
            Map.entry("z", Boolean.TRUE),
            Map.entry("c", '1'),
            Map.entry("st", "1"),
            Map.entry("cl", ClassDesc.of("foo.Bar")),
            Map.entry("en", E.C),
            Map.entry("arr", new Object[] {1, "1", 1.0f})
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
                case "i":
                    assertTrue(evp.value() instanceof AnnotationValue.OfInteger i && i.intValue() == 1);
                    break;
                case "j":
                    assertTrue(evp.value() instanceof AnnotationValue.OfLong j && j.longValue() == 1L);
                    break;
                case "s":
                    assertTrue(evp.value() instanceof AnnotationValue.OfShort s && s.shortValue() == (short) 1);
                    break;
                case "b":
                    assertTrue(evp.value() instanceof AnnotationValue.OfByte b && b.byteValue() == (byte) 1);
                    break;
                case "f":
                    assertTrue(evp.value() instanceof AnnotationValue.OfFloat f && f.floatValue() == 1.0f);
                    break;
                case "d":
                    assertTrue(evp.value() instanceof AnnotationValue.OfDouble d && d.doubleValue() == 1.0d);
                    break;
                case "z":
                    assertTrue(evp.value() instanceof AnnotationValue.OfBoolean z && z.booleanValue());
                    break;
                case "c":
                    assertTrue(evp.value() instanceof AnnotationValue.OfCharacter c && c.charValue() == '1');
                    break;
                case "st":
                    assertTrue(evp.value() instanceof AnnotationValue.OfString st && st.stringValue().equals("1"));
                    break;
                case "cl":
                    assertTrue(evp.value() instanceof AnnotationValue.OfClass c
                                && c.className().stringValue().equals("Lfoo/Bar;"));
                    break;
                case "en":
                    assertTrue(evp.value() instanceof AnnotationValue.OfEnum c
                                && c.className().stringValue().equals(E.class.descriptorString()) && c.constantName().stringValue().equals("C"));
                    break;
                case "a":
                    assertTrue(evp.value() instanceof AnnotationValue.OfAnnotation c
                                && assertAnno(c.annotation(), "LBar;", false));
                    break;
                case "arr":
                    assertTrue(evp.value() instanceof AnnotationValue.OfArray);
                    List<AnnotationValue> values = ((AnnotationValue.OfArray) evp.value()).values();
                    assertEquals(values.stream().map(v -> switch (v) {
                        case AnnotationValue.OfInteger i -> i.intValue();
                        case AnnotationValue.OfFloat f -> f.floatValue();
                        case AnnotationValue.OfString s -> s.stringValue();
                        default -> fail("Unexpected value " + v);
                    }).collect(toSet()), Set.of(1, 1.0f, "1"));
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
