/*
 * Copyright (c) 2018 Google LLC. All rights reserved.
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
 * @bug 8198945 8207018 8207017
 * @summary Invalid RuntimeVisibleTypeAnnotations for annotation on anonymous class type parameter
 * @library /tools/lib
 * @enablePreview
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          java.base/jdk.internal.classfile.impl
 *          jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JavapTask
 * @run compile -g AnonymousClassTest.java
 * @run main AnonymousClassTest
 */

import static java.util.stream.Collectors.toSet;

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import toolbox.ToolBox;

public class AnonymousClassTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface TA {
        int value() default 0;
    }

    private void f() {
        new @TA(0) Callable<@TA(1) Object>() {
            public Object call() {
                return null;
            }
        };
    }

    class Inner {
        private void g() {
            // The annotation is propagated from the top-level class Object to NEW expression for
            // the anonymous class' synthetic class declaration, which is an inner class of an inner
            // class.
            new @TA(2) Object() {};
        }
    }

    private void g() {
        new @TA(3) AnonymousClassTest.@TA(4) Inner() {};
    }

    // intance initializer
    {
        new @TA(5) Object() {};
    }

    // static initializer
    static {
        new @TA(6) Object() {};
    }

    public static void main(String args[]) throws Exception {
        testAnonymousClassDeclaration();
        testTopLevelMethod();
        testInnerClassMethod();
        testQualifiedSuperType();
        testInstanceAndClassInit();
    }

    static void testAnonymousClassDeclaration() throws Exception {
        ClassModel cm = ClassFile.of().parse(Paths.get(ToolBox.testClasses, "AnonymousClassTest$1.class"));
        RuntimeVisibleTypeAnnotationsAttribute rvta =
                cm.findAttribute(Attributes.runtimeVisibleTypeAnnotations()).orElse(null);
        assert rvta != null;
        assertEquals(
                Set.of(
                        "@LAnonymousClassTest$TA;(1) CLASS_EXTENDS, offset=-1, location=[TYPE_ARGUMENT(0)]",
                        "@LAnonymousClassTest$TA;(0) CLASS_EXTENDS, offset=-1, location=[]"),
                rvta.annotations().stream()
                        .map(a -> annotationDebugString(cm, null, a))
                        .collect(toSet()));
    }

    static void testTopLevelMethod() throws Exception {
        ClassModel cm = ClassFile.of().parse(Paths.get(ToolBox.testClasses, "AnonymousClassTest.class"));
        MethodModel method = findMethod(cm, "f");
        Set<TypeAnnotation> annotations = getRuntimeVisibleTypeAnnotations(method);
        CodeAttribute cAttr = method.findAttribute(Attributes.code()).orElse(null);
        assertEquals(
                Set.of("@LAnonymousClassTest$TA;(0) NEW, offset=0, location=[INNER_TYPE]"),
                annotations.stream().map(a -> annotationDebugString(cm, cAttr, a)).collect(toSet()));
    }

    static void testInnerClassMethod() throws Exception {
        ClassModel cm =
                ClassFile.of().parse(Paths.get(ToolBox.testClasses, "AnonymousClassTest$Inner.class"));
        MethodModel method = findMethod(cm, "g");
        Set<TypeAnnotation> annotations = getRuntimeVisibleTypeAnnotations(method);
        CodeAttribute cAttr = method.findAttribute(Attributes.code()).orElse(null);
        // The annotation needs two INNER_TYPE type path entries to apply to
        // AnonymousClassTest$Inner$1.
        assertEquals(
                Set.of(
                        "@LAnonymousClassTest$TA;(2) NEW, offset=0, location=[INNER_TYPE, INNER_TYPE]"),
                annotations.stream().map(a -> annotationDebugString(cm, cAttr, a)).collect(toSet()));
    }

    static void testQualifiedSuperType() throws Exception {
        {
            ClassModel cm =
                    ClassFile.of().parse(Paths.get(ToolBox.testClasses, "AnonymousClassTest.class"));
            MethodModel method = findMethod(cm, "g");
            Set<TypeAnnotation> annotations = getRuntimeVisibleTypeAnnotations(method);
            CodeAttribute cAttr = method.findAttribute(Attributes.code()).orElse(null);
            // Only @TA(4) is propagated to the anonymous class declaration.
            assertEquals(
                    Set.of("@LAnonymousClassTest$TA;(4) NEW, offset=0, location=[INNER_TYPE]"),
                    annotations.stream().map(a -> annotationDebugString(cm, cAttr, a)).collect(toSet()));
        }

        {
            ClassModel cm =
                    ClassFile.of().parse(Paths.get(ToolBox.testClasses, "AnonymousClassTest$2.class"));
            RuntimeVisibleTypeAnnotationsAttribute rvta =
                    cm.findAttribute(Attributes.runtimeVisibleTypeAnnotations()).orElse(null);
            assert rvta != null;
            assertEquals(
                    Set.of(
                            "@LAnonymousClassTest$TA;(3) CLASS_EXTENDS, offset=-1, location=[]",
                            "@LAnonymousClassTest$TA;(4) CLASS_EXTENDS, offset=-1, location=[INNER_TYPE]"),
                    rvta.annotations().stream()
                            .map(a -> annotationDebugString(cm, null, a))
                            .collect(toSet()));
        }
    }

    static void testInstanceAndClassInit() throws Exception {
        ClassModel cm = ClassFile.of().parse(Paths.get(ToolBox.testClasses, "AnonymousClassTest.class"));
        MethodModel method = findMethod(cm, "<init>");
        Set<TypeAnnotation> annotations = getRuntimeVisibleTypeAnnotations(method);
        CodeAttribute cAttr1 = method.findAttribute(Attributes.code()).orElse(null);
        assertEquals(
                Set.of("@LAnonymousClassTest$TA;(5) NEW, offset=4, location=[INNER_TYPE]"),
                annotations.stream().map(a -> annotationDebugString(cm, cAttr1, a)).collect(toSet()) );

        method = findMethod(cm, "<clinit>");
        annotations = getRuntimeVisibleTypeAnnotations(method);
        CodeAttribute cAttr2 = method.findAttribute(Attributes.code()).orElse(null);
        assertEquals(
                Set.of("@LAnonymousClassTest$TA;(6) NEW, offset=16, location=[INNER_TYPE]"),
                annotations.stream().map(a -> annotationDebugString(cm, cAttr2, a)).collect(toSet()) );
    }

    // Returns the Method's RuntimeVisibleTypeAnnotations, and asserts that there are no RVTIs
    // erroneously associated with the Method instead of its Code attribute.
    private static Set<TypeAnnotation> getRuntimeVisibleTypeAnnotations(MethodModel method) {
        if (method.findAttribute(Attributes.runtimeVisibleTypeAnnotations()).orElse(null) != null) {
            throw new AssertionError(
                    "expected no RuntimeVisibleTypeAnnotations attribute on enclosing method");
        }
        CodeAttribute code = method.findAttribute(Attributes.code()).orElse(null);
        assert code != null;
        RuntimeVisibleTypeAnnotationsAttribute rvta =
                code.findAttribute(Attributes.runtimeVisibleTypeAnnotations()).orElse(null);
        assert rvta != null;
        return new HashSet<>(rvta.annotations());
    }

    private static MethodModel findMethod(ClassModel cm, String name) {
        return cm.methods().stream()
                .filter(
                        m -> m.methodName().stringValue().contentEquals(name))
                .findFirst()
                .get();
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!actual.equals(expected)) {
            throw new AssertionError(String.format("expected: %s, saw: %s", expected, actual));
        }
    }

    private static String annotationDebugString(ClassModel cm, CodeAttribute cAttr, TypeAnnotation annotation) {
        TypeAnnotation.TargetInfo info = annotation.targetInfo();
        int offset = info instanceof TypeAnnotation.OffsetTarget offsetInfo? cAttr.labelToBci(offsetInfo.target()): -1;
        String name;
        try {
            name = annotation.classSymbol().descriptorString();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        List<String> location = new ArrayList<>();
        for (TypeAnnotation.TypePathComponent path: annotation.targetPath()) {
            if (path.typePathKind() == TypeAnnotation.TypePathComponent.Kind.INNER_TYPE)location.add(path.typePathKind().name());
            else location.add(path.typePathKind() + "(" + path.typeArgumentIndex() + ")");
        }
        return String.format(
                "@%s(%s) %s, offset=%d, location=%s",
                name,
                annotationValueDebugString(cm, annotation),
                info.targetType(),
                offset,
                location);
    }

    private static String annotationValueDebugString(ClassModel cm, Annotation annotation) {
        if (annotation.elements().size() != 1) {
            throw new UnsupportedOperationException();
        }
        try {
            return elementValueDebugString(annotation.elements().get(0).value());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String elementValueDebugString(AnnotationValue value) {
        if (value.tag() == 'I') {
            return Integer.toString(((AnnotationValue.OfInteger) value).intValue());
        } else {
            throw new UnsupportedOperationException(String.format("%c", value.tag()));
        }
    }
}
