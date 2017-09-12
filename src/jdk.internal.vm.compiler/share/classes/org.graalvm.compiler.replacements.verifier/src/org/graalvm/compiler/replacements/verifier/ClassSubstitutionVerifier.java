/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.verifier;

import java.lang.annotation.Annotation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import org.graalvm.compiler.api.replacements.ClassSubstitution;

public final class ClassSubstitutionVerifier extends AbstractVerifier {

    private static final String TYPE_VALUE = "value";
    private static final String STRING_VALUE = "className";
    private static final String OPTIONAL = "optional";

    public ClassSubstitutionVerifier(ProcessingEnvironment env) {
        super(env);
    }

    @Override
    public Class<? extends Annotation> getAnnotationClass() {
        return ClassSubstitution.class;
    }

    @Override
    public void verify(Element element, AnnotationMirror classSubstitution, PluginGenerator generator) {
        if (!element.getKind().isClass()) {
            assert false : "Element is guaranteed to be a class.";
            return;
        }
        TypeElement type = (TypeElement) element;

        TypeElement substitutionType = resolveOriginalType(env, type, classSubstitution);
        if (substitutionType == null) {
            return;
        }
    }

    static TypeElement resolveOriginalType(ProcessingEnvironment env, Element sourceElement, AnnotationMirror classSubstition) {
        AnnotationValue typeValue = findAnnotationValue(classSubstition, TYPE_VALUE);
        AnnotationValue stringValue = findAnnotationValue(classSubstition, STRING_VALUE);
        AnnotationValue optionalValue = findAnnotationValue(classSubstition, OPTIONAL);

        assert typeValue != null && stringValue != null && optionalValue != null;

        TypeMirror type = resolveAnnotationValue(TypeMirror.class, typeValue);
        String[] classNames = resolveAnnotationValue(String[].class, stringValue);
        boolean optional = resolveAnnotationValue(Boolean.class, optionalValue);

        if (type.getKind() != TypeKind.DECLARED) {
            env.getMessager().printMessage(Kind.ERROR, "The provided class must be a declared type.", sourceElement, classSubstition, typeValue);
            return null;
        }

        if (!classSubstition.getAnnotationType().asElement().equals(((DeclaredType) type).asElement())) {
            if (classNames.length != 0) {
                String msg = "The usage of value and className is exclusive.";
                env.getMessager().printMessage(Kind.ERROR, msg, sourceElement, classSubstition, stringValue);
                env.getMessager().printMessage(Kind.ERROR, msg, sourceElement, classSubstition, typeValue);
            }

            return (TypeElement) ((DeclaredType) type).asElement();
        }

        if (classNames.length != 0) {
            TypeElement typeElement = null;
            for (String className : classNames) {
                typeElement = env.getElementUtils().getTypeElement(className);
                if (typeElement != null) {
                    break;
                }
            }
            if (typeElement == null && !optional) {
                env.getMessager().printMessage(Kind.ERROR, String.format("The class '%s' was not found on the classpath.", stringValue), sourceElement, classSubstition, stringValue);
            }

            return typeElement;
        }

        if (!optional) {
            env.getMessager().printMessage(Kind.ERROR, String.format("No value for '%s' or '%s' provided but required.", TYPE_VALUE, STRING_VALUE), sourceElement, classSubstition);
        }

        return null;
    }

}
