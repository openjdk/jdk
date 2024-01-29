/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;

import java.io.IOException;
import java.lang.annotation.RetentionPolicy;
import java.util.*;
import java.util.function.Supplier;

import javax.tools.JavaFileObject;

public abstract class RuntimeAnnotationsTestBase extends AnnotationsTestBase {

    @Override
    public void test(TestCase testCase, Map<String, ? extends JavaFileObject> classes)
            throws IOException {
        for (Map.Entry<String, ? extends JavaFileObject> entry : classes.entrySet()) {
            String className = entry.getKey();
            TestCase.TestClassInfo clazz = testCase.getTestClassInfo(className);
            echo("Testing class : " + className);
            ClassModel classFile = readClassFile(entry.getValue());

            testAttributes(clazz, classFile, classFile);

            testMethods(clazz, classFile);

            testFields(clazz, classFile);
        }
    }

    private void testMethods(TestCase.TestClassInfo clazz, ClassModel classFile) {
        String className = clazz.getName();
        Set<String> foundMethods = new HashSet<>();
        for (MethodModel method : classFile.methods()) {
            String methodName = method.methodName().stringValue() +
                    method.methodTypeSymbol().displayDescriptor();
            methodName = methodName.substring(0, methodName.indexOf(")") + 1);
            if (methodName.startsWith("<init>")) {
                String constructorName = className.replaceAll(".*\\$", "");
                methodName = methodName.replace("<init>", constructorName);
            }
            echo("Testing method : " + methodName);

            TestCase.TestMethodInfo testMethod = clazz.getTestMethodInfo(methodName);
            foundMethods.add(methodName);
            if (testMethod == null) {
                continue;
            }
            testAttributes(testMethod, classFile, method);
        }
        checkContains(foundMethods, clazz.methods.keySet(), "Methods in class : " + className);
    }

    private void testFields(TestCase.TestClassInfo clazz, ClassModel classFile) {
        Set<String> foundFields = new HashSet<>();
        for (FieldModel field : classFile.fields()) {
            String fieldName = field.fieldName().stringValue();
            echo("Testing field : " + fieldName);

            TestCase.TestFieldInfo testField = clazz.getTestFieldInfo(fieldName);
            foundFields.add(fieldName);
            if (testField == null) {
                continue;
            }
            testAttributes(testField, classFile, field);
        }
        checkContains(foundFields, clazz.fields.keySet(), "Fields in class : " + clazz.getName());
    }

    private void testAttributes(
            TestCase.TestMemberInfo member,
            ClassModel classFile,
            AttributedElement attributedElement) {
        Map<String, Annotation> actualInvisible = collectAnnotations(
                member,
                attributedElement,
                Attributes.RUNTIME_INVISIBLE_ANNOTATIONS);
        Map<String, Annotation> actualVisible = collectAnnotations(
                member,
                attributedElement,
                Attributes.RUNTIME_VISIBLE_ANNOTATIONS);

        checkEquals(actualInvisible.keySet(),
                member.getRuntimeInvisibleAnnotations(), "RuntimeInvisibleAnnotations");
        checkEquals(actualVisible.keySet(),
                member.getRuntimeVisibleAnnotations(), "RuntimeVisibleAnnotations");

        for (TestAnnotationInfo expectedAnnotation : member.annotations.values()) {
            RetentionPolicy policy = getRetentionPolicy(expectedAnnotation.annotationName);
            if (policy == RetentionPolicy.SOURCE) {
                continue;
            }
            printf("Testing: isVisible: %s %s%n", policy.toString(), expectedAnnotation.annotationName);
            Annotation actualAnnotation =
                    (policy == RetentionPolicy.RUNTIME ? actualVisible : actualInvisible)
                            .get(expectedAnnotation.annotationName);
            if (checkNotNull(actualAnnotation, "Annotation is found : "
                    + expectedAnnotation.annotationName)) {
                expectedAnnotation.testAnnotation(this, classFile, actualAnnotation);
            }
        }
    }

    private <T extends Attribute<T>> Map<String, Annotation> collectAnnotations(
            TestCase.TestMemberInfo member,
            AttributedElement attributedElement,
            AttributeMapper<T> attribute) {

        Object attr = attributedElement.findAttribute(attribute).orElse(null);
        Map<String, Annotation> actualAnnotations = new HashMap<>();
        RetentionPolicy policy = getRetentionPolicy(attribute.name());
        if (member.isAnnotated(policy)) {
            if (!checkNotNull(attr, String.format("%s should be not null value", attribute.name()))) {
                // test case failed, stop checking
                return actualAnnotations;
            }
            List<Annotation> annotationList;
            switch (attr) {
                case RuntimeVisibleAnnotationsAttribute annots -> {
                    annotationList = annots.annotations();
                }
                case RuntimeInvisibleAnnotationsAttribute annots -> {
                    annotationList = annots.annotations();
                }
                default -> throw new AssertionError();
            }
            for (Annotation ann : annotationList) {
                String name = ann.classSymbol().displayName();
                actualAnnotations.put(name, ann);
            }
            checkEquals(countNumberOfAttributes(attributedElement.attributes(),
                    getRetentionPolicy(attribute.name()) == RetentionPolicy.RUNTIME
                            ? RuntimeVisibleAnnotationsAttribute.class
                            : RuntimeInvisibleAnnotationsAttribute.class),
                    1L,
                    String.format("Number of %s", attribute.name()));
        } else {
            checkNull(attr, String.format("%s should be null", attribute.name()));
        }
        return actualAnnotations;
    }
}
