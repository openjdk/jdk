/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @comment Bug ID pending
 * @bug 8284333
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 * @compile -parameters ParameterMappingTest.java
 * @run junit ParameterMappingTest
 * @summary Core reflection should handle parameters correctly
 */

import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.ClassTransform;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.MethodTransform;
import jdk.internal.classfile.attribute.MethodParametersAttribute;
import jdk.internal.classfile.attribute.SignatureAttribute;
import jdk.internal.classfile.components.ClassRemapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Parameter;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

import static jdk.internal.classfile.Classfile.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParameterMappingTest {
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

    @Test
    public void testFull() {
        checkFullParams("Outer$1Local", ACC_MANDATED, 0, 0, 0, ACC_SYNTHETIC);
        checkFullParams("Outer$Inner", ACC_MANDATED, 0, 0, 0);
        checkFullParams("Outer$1Local$LocalInner", ACC_MANDATED, 0, 0);
        checkFullParams("Outer$1Local$1Sub", ACC_MANDATED, 0, 0, 0, ACC_SYNTHETIC, ACC_SYNTHETIC);
        checkFullParams("MyEnum", ACC_SYNTHETIC, ACC_SYNTHETIC, 0, 0);
        checkFullParams("MyEnumNoGeneric", ACC_SYNTHETIC, ACC_SYNTHETIC, 0, 0);
    }

    //@Test // TODO: Classfile API BufferedFieldBuilder.Model broke, needs fixing
    public void testNoSignature() {
        checkNoSignatureParams("Outer$1Local", ACC_MANDATED, 0, 0, 0, ACC_SYNTHETIC);
        checkNoSignatureParams("Outer$Inner", ACC_MANDATED, 0, 0, 0);
        checkNoSignatureParams("Outer$1Local$LocalInner", ACC_MANDATED, 0, 0);
        checkNoSignatureParams("Outer$1Local$1Sub", ACC_MANDATED, 0, 0, 0, ACC_SYNTHETIC, ACC_SYNTHETIC);
        checkNoSignatureParams("MyEnum", ACC_SYNTHETIC, ACC_SYNTHETIC, 0, 0);
        checkNoSignatureParams("MyEnumNoGeneric", ACC_SYNTHETIC, ACC_SYNTHETIC, 0, 0);
    }

    private static Class<?> load(String name, boolean dropParams, boolean dropSigs) {
        ClassModel cm;
        try (var in = ParameterMappingTest.class.getResourceAsStream("/" + name + ".class")) {
            Objects.requireNonNull(in);
            cm = Classfile.parse(in.readAllBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        ClassTransform transform = ClassRemapper.of(Map.of(cm.thisClass().asSymbol(), ClassDesc.of(name
                + (dropParams ? "$DropParams" : "") + (dropSigs ? "$DropSigs" : ""))));
        if (dropParams) transform = transform.andThen(ClassTransform.transformingMethods(MethodTransform.dropping(me
                -> me instanceof MethodParametersAttribute)));
        if (dropSigs) transform = transform.andThen(ClassTransform.transformingMethods(MethodTransform.dropping(me
                -> me instanceof SignatureAttribute)));

        try {
            return LOOKUP.defineClass(cm.transform(transform));
        } catch (IllegalAccessException iae) {
            throw new RuntimeException(iae);
        }
    }
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
