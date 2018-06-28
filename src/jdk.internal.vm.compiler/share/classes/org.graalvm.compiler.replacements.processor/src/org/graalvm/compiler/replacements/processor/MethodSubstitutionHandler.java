/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.processor;

import static org.graalvm.compiler.processor.AbstractProcessor.getAnnotationValue;
import static org.graalvm.compiler.processor.AbstractProcessor.getSimpleName;
import static org.graalvm.compiler.replacements.processor.ClassSubstitutionHandler.CLASS_SUBSTITUTION_CLASS_NAME;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;

import org.graalvm.compiler.processor.AbstractProcessor;

/**
 * Handler for the {@value #METHOD_SUBSTITUTION_CLASS_NAME} annotation.
 */
public final class MethodSubstitutionHandler extends AnnotationHandler {

    private static final String METHOD_SUBSTITUTION_CLASS_NAME = "org.graalvm.compiler.api.replacements.MethodSubstitution";

    private static final boolean DEBUG = false;

    private static final String ORIGINAL_METHOD_NAME = "value";
    private static final String ORIGINAL_IS_STATIC = "isStatic";
    private static final String ORIGINAL_SIGNATURE = "signature";

    private static final String ORIGINAL_METHOD_NAME_DEFAULT = "";
    private static final String ORIGINAL_SIGNATURE_DEFAULT = "";

    public MethodSubstitutionHandler(AbstractProcessor processor) {
        super(processor, METHOD_SUBSTITUTION_CLASS_NAME);
    }

    @SuppressWarnings("unused")
    @Override
    public void process(Element element, AnnotationMirror annotation, PluginGenerator generator) {
        if (element.getKind() != ElementKind.METHOD) {
            assert false : "Element is guaranteed to be a method.";
            return;
        }
        ExecutableElement substitutionMethod = (ExecutableElement) element;
        TypeElement substitutionType = findEnclosingClass(substitutionMethod);
        assert substitutionType != null;

        Messager messager = processor.env().getMessager();
        AnnotationMirror substitutionClassAnnotation = processor.getAnnotation(substitutionType, processor.getType(CLASS_SUBSTITUTION_CLASS_NAME));
        if (substitutionClassAnnotation == null) {
            messager.printMessage(Kind.ERROR, String.format("A @%s annotation is required on the enclosing class.", getSimpleName(CLASS_SUBSTITUTION_CLASS_NAME)), element, annotation);
            return;
        }
        boolean optional = getAnnotationValue(substitutionClassAnnotation, "optional", Boolean.class);
        if (optional) {
            return;
        }

        TypeElement originalType = ClassSubstitutionHandler.resolveOriginalType(processor, substitutionType, substitutionClassAnnotation);
        if (originalType == null) {
            messager.printMessage(Kind.ERROR, String.format("The @%s annotation is invalid on the enclosing class.", getSimpleName(CLASS_SUBSTITUTION_CLASS_NAME)), element, annotation);
            return;
        }

        if (!substitutionMethod.getModifiers().contains(Modifier.STATIC)) {
            messager.printMessage(Kind.ERROR, String.format("A @%s method must be static.", getSimpleName(METHOD_SUBSTITUTION_CLASS_NAME)), element, annotation);
        }

        if (substitutionMethod.getModifiers().contains(Modifier.ABSTRACT) || substitutionMethod.getModifiers().contains(Modifier.NATIVE)) {
            messager.printMessage(Kind.ERROR, String.format("A @%s method must not be native or abstract.", getSimpleName(METHOD_SUBSTITUTION_CLASS_NAME)), element, annotation);
        }

        String originalName = originalName(substitutionMethod, annotation);
        boolean isStatic = getAnnotationValue(annotation, ORIGINAL_IS_STATIC, Boolean.class);
        TypeMirror[] originalSignature = originalSignature(originalType, substitutionMethod, annotation, isStatic);
        if (originalSignature == null) {
            return;
        }
        ExecutableElement originalMethod = originalMethod(substitutionMethod, annotation, originalType, originalName, originalSignature, isStatic);
        if (DEBUG && originalMethod != null) {
            messager.printMessage(Kind.NOTE, String.format("Found original method %s in type %s.", originalMethod, findEnclosingClass(originalMethod)));
        }
    }

    private TypeMirror[] originalSignature(TypeElement originalType, ExecutableElement method, AnnotationMirror annotation, boolean isStatic) {
        String signatureString = getAnnotationValue(annotation, ORIGINAL_SIGNATURE, String.class);
        List<TypeMirror> parameters = new ArrayList<>();
        Messager messager = processor.env().getMessager();
        if (signatureString.equals(ORIGINAL_SIGNATURE_DEFAULT)) {
            for (int i = 0; i < method.getParameters().size(); i++) {
                parameters.add(method.getParameters().get(i).asType());
            }
            if (!isStatic) {
                if (parameters.isEmpty()) {
                    messager.printMessage(Kind.ERROR, "Method signature must be a static method with the 'this' object as its first parameter", method, annotation);
                    return null;
                } else {
                    TypeMirror thisParam = parameters.remove(0);
                    if (!isSubtype(originalType.asType(), thisParam)) {
                        Name thisName = method.getParameters().get(0).getSimpleName();
                        messager.printMessage(Kind.ERROR, String.format("The type of %s must assignable from %s", thisName, originalType), method, annotation);
                    }
                }
            }
            parameters.add(0, method.getReturnType());
        } else {
            try {
                APHotSpotSignature signature = new APHotSpotSignature(signatureString);
                parameters.add(signature.getReturnType(processor.env()));
                for (int i = 0; i < signature.getParameterCount(false); i++) {
                    parameters.add(signature.getParameterType(processor.env(), i));
                }
            } catch (Exception e) {
                /*
                 * That's not good practice and should be changed after APHotSpotSignature has
                 * received a cleanup.
                 */
                messager.printMessage(Kind.ERROR, String.format("Parsing the signature failed: %s", e.getMessage() != null ? e.getMessage() : e.toString()), method, annotation);
                return null;
            }
        }
        return parameters.toArray(new TypeMirror[parameters.size()]);
    }

    private static String originalName(ExecutableElement substituteMethod, AnnotationMirror substitution) {
        String originalMethodName = getAnnotationValue(substitution, ORIGINAL_METHOD_NAME, String.class);
        if (originalMethodName.equals(ORIGINAL_METHOD_NAME_DEFAULT)) {
            originalMethodName = substituteMethod.getSimpleName().toString();
        }
        return originalMethodName;
    }

    private ExecutableElement originalMethod(ExecutableElement substitutionMethod, AnnotationMirror substitutionAnnotation, TypeElement originalType, String originalName,
                    TypeMirror[] originalSignature, boolean isStatic) {
        TypeMirror signatureReturnType = originalSignature[0];
        TypeMirror[] signatureParameters = Arrays.copyOfRange(originalSignature, 1, originalSignature.length);
        List<ExecutableElement> searchElements;
        if (originalName.equals("<init>")) {
            searchElements = ElementFilter.constructorsIn(originalType.getEnclosedElements());
        } else {
            searchElements = ElementFilter.methodsIn(originalType.getEnclosedElements());
        }

        Messager messager = processor.env().getMessager();
        ExecutableElement originalMethod = null;
        outer: for (ExecutableElement searchElement : searchElements) {
            if (searchElement.getSimpleName().toString().equals(originalName) && searchElement.getParameters().size() == signatureParameters.length) {
                for (int i = 0; i < signatureParameters.length; i++) {
                    VariableElement parameter = searchElement.getParameters().get(i);
                    if (!isTypeCompatible(parameter.asType(), signatureParameters[i])) {
                        continue outer;
                    }
                }
                originalMethod = searchElement;
                break;
            }
        }
        if (originalMethod == null) {
            boolean optional = getAnnotationValue(substitutionAnnotation, "optional", Boolean.class);
            if (!optional) {
                messager.printMessage(Kind.ERROR,
                                String.format("Could not find the original method with name '%s' and parameters '%s'.", originalName, Arrays.toString(signatureParameters)),
                                substitutionMethod, substitutionAnnotation);
            }
            return null;
        }

        if (originalMethod.getModifiers().contains(Modifier.STATIC) != isStatic) {
            boolean optional = getAnnotationValue(substitutionAnnotation, "optional", Boolean.class);
            if (!optional) {
                messager.printMessage(Kind.ERROR, String.format("The %s element must be set to %s.", ORIGINAL_IS_STATIC, !isStatic), substitutionMethod, substitutionAnnotation);
            }
            return null;
        }

        if (!isTypeCompatible(originalMethod.getReturnType(), signatureReturnType)) {
            messager.printMessage(
                            Kind.ERROR,
                            String.format("The return type of the substitution method '%s' must match with the return type of the original method '%s'.", signatureReturnType,
                                            originalMethod.getReturnType()),
                            substitutionMethod, substitutionAnnotation);
            return null;
        }

        return originalMethod;
    }

    private boolean isTypeCompatible(TypeMirror originalType, TypeMirror substitutionType) {
        TypeMirror original = originalType;
        TypeMirror substitution = substitutionType;
        if (needsErasure(original)) {
            original = processor.env().getTypeUtils().erasure(original);
        }
        if (needsErasure(substitution)) {
            substitution = processor.env().getTypeUtils().erasure(substitution);
        }
        return processor.env().getTypeUtils().isSameType(original, substitution);
    }

    /**
     * Tests whether one type is a subtype of another. Any type is considered to be a subtype of
     * itself.
     *
     * @param t1 the first type
     * @param t2 the second type
     * @return {@code true} if and only if the first type is a subtype of the second
     */
    private boolean isSubtype(TypeMirror t1, TypeMirror t2) {
        TypeMirror t1Erased = t1;
        TypeMirror t2Erased = t2;
        if (needsErasure(t1Erased)) {
            t1Erased = processor.env().getTypeUtils().erasure(t1Erased);
        }
        if (needsErasure(t2Erased)) {
            t2Erased = processor.env().getTypeUtils().erasure(t2Erased);
        }
        return processor.env().getTypeUtils().isSubtype(t1Erased, t2Erased);
    }

    private static boolean needsErasure(TypeMirror typeMirror) {
        return typeMirror.getKind() != TypeKind.NONE && typeMirror.getKind() != TypeKind.VOID && !typeMirror.getKind().isPrimitive() && typeMirror.getKind() != TypeKind.OTHER &&
                        typeMirror.getKind() != TypeKind.NULL;
    }

    private static TypeElement findEnclosingClass(Element element) {
        if (element.getKind().isClass()) {
            return (TypeElement) element;
        }

        Element enclosing = element.getEnclosingElement();
        while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
            if (enclosing.getKind().isClass()) {
                return (TypeElement) enclosing;
            }
            enclosing = enclosing.getEnclosingElement();
        }
        return null;
    }

}
