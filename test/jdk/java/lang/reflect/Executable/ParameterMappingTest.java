/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8180892 8284333 8292275
 * @enablePreview
 * @compile -parameters ParameterMappingTest.java
 * @run junit ParameterMappingTest
 * @summary Core reflection should handle parameters correctly with or without certain attributes
 */

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.classfile.components.ClassRemapper;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.classfile.ClassFile.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that parameter info (generics, parameter annotations, type annotations) are
 * applied correctly in the presence/absence of MethodParameters/Signature attributes.
 * <p>
 * Note that this test does not take care of local or anonymous classes in initializers
 * (yet). They have an outer instance parameter in non-static initializer statements
 * and no outer instance parameter in static initializer statements, but their
 * InnerClasses flags do not indicate such static information, nor do their
 * EnclosingMethod indicate which type of initializer they are in.
 */
public class ParameterMappingTest {
    /**
     * Loads Outer class that knows the patched inner classes, to prevent other
     * classloaders from loading the class file and fail on InnerClasses attribute
     * mismatch.
     */
    @BeforeAll
    public static void beforeTests() {
        load("Outer", false, false);
    }

    /**
     * Checks when both MethodParameters and Signature are present.
     * This is what javac generates after JDK-8292275.
     */
    @Test
    public void testFull() {
        checkFullParams("Outer$1Local", ACC_MANDATED, 0, 0, 0, ACC_SYNTHETIC);
        checkFullParams("Outer$Inner", ACC_MANDATED, 0, 0, 0);
        checkFullParams("Outer$1Local$LocalInner", ACC_MANDATED, 0, 0);
        checkFullParams("Outer$1Local$1Sub", ACC_MANDATED, 0, 0, 0, ACC_SYNTHETIC, ACC_SYNTHETIC);
        checkFullParams("MyEnum", ACC_SYNTHETIC, ACC_SYNTHETIC, 0, 0);
        checkFullParams("MyEnumNoGeneric", ACC_SYNTHETIC, ACC_SYNTHETIC, 0, 0);
    }

    /**
     * Checks when MethodParameters is present but Signature is absent.
     */
    @Test
    public void testNoSignature() {
        checkNoSignatureParams("Outer$1Local", ACC_MANDATED, 0, 0, 0, ACC_SYNTHETIC);
        checkNoSignatureParams("Outer$Inner", ACC_MANDATED, 0, 0, 0);
        checkNoSignatureParams("Outer$1Local$LocalInner", ACC_MANDATED, 0, 0);
        checkNoSignatureParams("Outer$1Local$1Sub", ACC_MANDATED, 0, 0, 0, ACC_SYNTHETIC, ACC_SYNTHETIC);
        checkNoSignatureParams("MyEnum", ACC_SYNTHETIC, ACC_SYNTHETIC, 0, 0);
        checkNoSignatureParams("MyEnumNoGeneric", ACC_SYNTHETIC, ACC_SYNTHETIC, 0, 0);
    }

    /**
     * Checks when MethodParameters is absent but Signature is present.
     * This is what javac generates by default before JDK-8292275.
     * Core reflection will fall back to the default heuristics, preserving the old behaviors.
     */
    @Test
    public void testNoParams() {
        // Generic (parameterization) fails for any generics for everything
        // (Executable::getAllGenericParameterTypes)
        // Parameter annotations succeeds for enum, inner classes, fails for anonymous/local classes
        // (Constructor::handleParameterNumberMismatch)
        // Parameter type annotations succeeds for inner classes, fails for anything else
        // (TypeAnnotationParser::buildAnnotatedTypesWithHeuristics)
        checkNoParams("Outer$1Local", 5, false, false, false); // local
        checkNoParams("Outer$Inner", 4, false, true, true); // inner
        checkNoParams("Outer$1Local$LocalInner", 3, false, true, true); // inner
        checkNoParams("Outer$1Local$1Sub", 6, false, false, false); // local
        checkNoParams("MyEnum", 4, false, true, false); // enum
        checkNoParams("MyEnumNoGeneric", 4, true, true, false); // no-generic, enum
    }

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static void assertHidden(Parameter param, int access) {
        assertEquals(0, param.getDeclaredAnnotations().length);
        assertEquals(0, param.getAnnotatedType().getDeclaredAnnotations().length);
        assertEquals(access, param.getModifiers() & (ACC_MANDATED | ACC_SYNTHETIC));
    }

    private static void assertVisible(Parameter param) {
        assertTrue(param.isNamePresent());
        assertEquals(param.getName(), param.getDeclaredAnnotation(ParamAnno.class).value());

        checkType(param.getAnnotatedType());
        assertEquals(0, param.getModifiers() & (ACC_MANDATED | ACC_SYNTHETIC));
    }

    private static void assertVisibleNoSignature(Parameter param) {
        assertTrue(param.isNamePresent());
        assertEquals(param.getName(), param.getDeclaredAnnotation(ParamAnno.class).value());

        checkTypeNoSignature(param.getAnnotatedType());
        assertEquals(0, param.getModifiers() & (ACC_MANDATED | ACC_SYNTHETIC));
    }

    private static void checkType(AnnotatedType at) {
        assertEquals(at.getType().getTypeName(), at.getDeclaredAnnotation(TypeAnno.class).value());

        if (at instanceof AnnotatedParameterizedType apt) {
            for (var arg : apt.getAnnotatedActualTypeArguments()) {
                checkType(arg);
            }
        }
    }

    private static void checkTypeNoSignature(AnnotatedType at) {
        var typeanno = at.getDeclaredAnnotation(TypeAnno.class);
        var targetName = typeanno.erased().isEmpty() ? typeanno.value() : typeanno.erased();
        assertEquals(at.getType().getTypeName(), targetName);
        assertTrue(at instanceof AnnotatedArrayType || at.getType() instanceof Class<?>);
    }

    private static void checkFullParams(String name, int... flags) {
        var params = load(name, false, false).getDeclaredConstructors()[0].getParameters();
        assertEquals(flags.length, params.length);
        for (int i = 0; i < flags.length; i++) {
            if (flags[i] == 0) {
                assertVisible(params[i]);
            } else {
                assertHidden(params[i], flags[i]);
            }
        }
    }

    private static void checkNoSignatureParams(String name, int... flags) {
        var params = load(name, false, true).getDeclaredConstructors()[0].getParameters();
        assertEquals(flags.length, params.length);
        for (int i = 0; i < flags.length; i++) {
            if (flags[i] == 0) {
                assertVisibleNoSignature(params[i]);
            } else {
                assertHidden(params[i], flags[i]);
            }
        }
    }

    private static void checkNoParams(String name, int length, boolean genericSuccess, boolean paramAnnoSuccess, boolean paramTypeAnnoSuccess) {
        var modelParams = load(name, false, false).getDeclaredConstructors()[0].getParameters();
        var params = load(name, true, false).getDeclaredConstructors()[0].getParameters();
        assertEquals(length, params.length);
        for (int i = 0; i < length; i++) {
            var modelParam = modelParams[i];
            var param = params[i];
            assertFalse(param.isNamePresent());
            if (genericSuccess) {
                assertEquals(modelParam.getParameterizedType(), param.getParameterizedType());
            } else {
                // core reflection cannot apply generics when there's count mismatch
                // without MethodParameters attribute
                assertEquals(param.getType(), param.getParameterizedType());
            }

            if (paramAnnoSuccess) {
                assertArrayEquals(modelParam.getAnnotations(), param.getAnnotations());
            }

            if (paramTypeAnnoSuccess) {
                if (genericSuccess) {
                    assertEquals(modelParam.getAnnotatedType(), param.getAnnotatedType());
                } else {
                    // Since generics are erased, only top-level type annotations are guaranteed to be present
                    assertArrayEquals(modelParam.getAnnotatedType().getAnnotations(), param.getAnnotatedType().getAnnotations());
                }
            }
        }
    }

    private static final Map<String, Class<?>> defined = new ConcurrentHashMap<>(6 * 4);

    private static Class<?> load(String name, boolean dropParams, boolean dropSigs) {
        ClassFile cf = ClassFile.of();
        String cn = name + (dropParams ? "$DropParams" : "") + (dropSigs ? "$DropSigs" : "");
        Class<?> cr;
        if ((cr = defined.get(cn)) != null)
            return cr;

        ClassModel cm;
        try (var in = ParameterMappingTest.class.getResourceAsStream("/" + name + ".class")) {
            Objects.requireNonNull(in);
            cm = cf.parse(in.readAllBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        var pipedBytes = ClassRemapper.of(Map.of(cm.thisClass().asSymbol(), ClassDesc.of(cn))).remapClass(cf, cm);
        if (dropParams) pipedBytes = cf.transform(cf.parse(pipedBytes),
                ClassTransform.transformingMethods(MethodTransform.dropping(me
                -> me instanceof MethodParametersAttribute)));
        if (dropSigs) pipedBytes = cf.transform(cf.parse(pipedBytes),
                ClassTransform.transformingMethods(MethodTransform.dropping(me
                -> me instanceof SignatureAttribute)));
        if (!dropParams && !dropSigs) {
            // insert InnerClasses to prevent reflection glitches
            cm = cf.parse(pipedBytes);
            var cp = ConstantPoolBuilder.of(cm);
            var innerClassOpt = cm.findAttribute(Attributes.INNER_CLASSES);
            if (innerClassOpt.isPresent()) {
                var inners = innerClassOpt.get();
                var list = new ArrayList<>(inners.classes());
                for (var inner : inners.classes()) {
                    var innerName = inner.innerClass().asInternalName();
                    if (checks.contains(innerName)) {
                        dupInner(list, innerName + "$DropParams", cp, inner);
                        dupInner(list, innerName + "$DropSigs", cp, inner);
                        dupInner(list, innerName + "$DropParams$DropSigs", cp, inner);
                    }
                }
                final var currentModel = cm;
                pipedBytes = cf.build(cm.thisClass(), cp, cb -> {
                    for (var e : currentModel.elements()) {
                        if (!(e instanceof InnerClassesAttribute)) {
                            cb.with(e);
                        }
                    }
                    cb.with(InnerClassesAttribute.of(list));
                });
            }
        }

        try {
            cr = LOOKUP.defineClass(pipedBytes);
            defined.put(cn, cr);
            return cr;
        } catch (IllegalAccessException iae) {
            throw new RuntimeException(iae);
        }
    }

    private static void dupInner(List<InnerClassInfo> list, String name, ConstantPoolBuilder cp, InnerClassInfo original) {
        list.add(InnerClassInfo.of(cp.classEntry(cp.utf8Entry(name)), original.outerClass(), original.innerName(), original.flagsMask()));
    }

    static final Set<String> checks = Set.of("Outer$Inner", "Outer$1Local", "Outer$1Local$LocalInner", "Outer$1Local$1Sub");
}

class Outer {
    class Inner<T> {
        // Has a leading implicit Outer instance
        Inner(//@TypeAnno("receiver") Outer Outer.this,
              @ParamAnno("v0") @TypeAnno(value = "T", erased = "java.lang.Object") T v0,
              @ParamAnno("p0") @TypeAnno("int") int p0,
              @ParamAnno("p2") @TypeAnno(value = "java.util.Comparator<java.lang.Integer>", erased = "java.util.Comparator") Comparator<@TypeAnno("java.lang.Integer") Integer> p2) {}
    }

    void method(int p) {
        class Local<V> {
            // Has a leading implicit Outer instance
            // and a trailing synthetic int p
            Local(//@TypeAnno("receiver") Outer Outer.this,
                  @ParamAnno("value") @TypeAnno(value = "V", erased = "java.lang.Object") V value,
                  @ParamAnno("q") @TypeAnno("int") int q,
                  @ParamAnno("p2") @TypeAnno(value = "java.util.Comparator<java.lang.Integer>", erased = "java.util.Comparator") Comparator<@TypeAnno("java.lang.Integer") Integer> p2) {
                System.out.println(p + q);
            }

            class LocalInner {
                LocalInner(//@TypeAnno("receiver") Local<@TypeAnno("V") V> Local.this,
                           @ParamAnno("a") @TypeAnno("int") int a,
                           @ParamAnno("p2") @TypeAnno(value = "java.util.Comparator<java.lang.Integer>", erased = "java.util.Comparator") Comparator<@TypeAnno("java.lang.Integer") Integer> p2) {
                }
            }

            void method(long r) {
                class Sub<P> extends Local<P> {
                    Sub(//
                        @ParamAnno("value") @TypeAnno(value = "P", erased = "java.lang.Object") P value,
                        @ParamAnno("q") @TypeAnno("int") int q,
                        @ParamAnno("p2") @TypeAnno(value = "java.util.Comparator<java.lang.Integer>", erased = "java.util.Comparator") Comparator<@TypeAnno("java.lang.Integer") Integer> p2) {
                        super(value, q, p2);
                        System.out.println(p + q + r);
                    }
                }
            }
        }
    }
}

enum MyEnum {
    ;
    // 8284333: enum constructor and type annotation
    MyEnum(@ParamAnno("p0") @TypeAnno("int") int p0,
           @ParamAnno("p2") @TypeAnno(value = "java.util.Comparator<java.lang.Integer>", erased = "java.util.Comparator") Comparator<@TypeAnno("java.lang.Integer") Integer> p2) {}
}

enum MyEnumNoGeneric {
    ;
    // This constructor has a signature, make sure it works with only methodparameters (no signature)
    MyEnumNoGeneric(@ParamAnno("sq") @TypeAnno("int") int sq,
                    @ParamAnno("pr") @TypeAnno("java.lang.Object") Object pr) {}
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface TypeAnno {
    String value();

    String erased() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@interface ParamAnno {
    String value();
}
