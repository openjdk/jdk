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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
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

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;

public final class MethodSubstitutionVerifier extends AbstractVerifier {

    private static final boolean DEBUG = false;

    private static final String ORIGINAL_METHOD_NAME = "value";
    private static final String ORIGINAL_IS_STATIC = "isStatic";
    private static final String ORIGINAL_SIGNATURE = "signature";

    private static final String ORIGINAL_METHOD_NAME_DEFAULT = "";
    private static final String ORIGINAL_SIGNATURE_DEFAULT = "";

    public MethodSubstitutionVerifier(ProcessingEnvironment env) {
        super(env);
    }

    @Override
    public Class<? extends Annotation> getAnnotationClass() {
        return MethodSubstitution.class;
    }

    @SuppressWarnings("unused")
    @Override
    public void verify(Element element, AnnotationMirror annotation, PluginGenerator generator) {
        if (element.getKind() != ElementKind.METHOD) {
            assert false : "Element is guaranteed to be a method.";
            return;
        }
        ExecutableElement substitutionMethod = (ExecutableElement) element;
        TypeElement substitutionType = findEnclosingClass(substitutionMethod);
        assert substitutionType != null;

        AnnotationMirror substitutionClassAnnotation = VerifierAnnotationProcessor.findAnnotationMirror(env, substitutionType.getAnnotationMirrors(), ClassSubstitution.class);
        if (substitutionClassAnnotation == null) {
            env.getMessager().printMessage(Kind.ERROR, String.format("A @%s annotation is required on the enclosing class.", ClassSubstitution.class.getSimpleName()), element, annotation);
            return;
        }
        boolean optional = resolveAnnotationValue(Boolean.class, findAnnotationValue(substitutionClassAnnotation, "optional"));
        if (optional) {
            return;
        }

        TypeElement originalType = ClassSubstitutionVerifier.resolveOriginalType(env, substitutionType, substitutionClassAnnotation);
        if (originalType == null) {
            env.getMessager().printMessage(Kind.ERROR, String.format("The @%s annotation is invalid on the enclosing class.", ClassSubstitution.class.getSimpleName()), element, annotation);
            return;
        }

        if (!substitutionMethod.getModifiers().contains(Modifier.STATIC)) {
            env.getMessager().printMessage(Kind.ERROR, String.format("A @%s method must be static.", MethodSubstitution.class.getSimpleName()), element, annotation);
        }

        if (substitutionMethod.getModifiers().contains(Modifier.ABSTRACT) || substitutionMethod.getModifiers().contains(Modifier.NATIVE)) {
            env.getMessager().printMessage(Kind.ERROR, String.format("A @%s method must not be native or abstract.", MethodSubstitution.class.getSimpleName()), element, annotation);
        }

        String originalName = originalName(substitutionMethod, annotation);
        boolean isStatic = resolveAnnotationValue(Boolean.class, findAnnotationValue(annotation, ORIGINAL_IS_STATIC));
        TypeMirror[] originalSignature = originalSignature(originalType, substitutionMethod, annotation, isStatic);
        if (originalSignature == null) {
            return;
        }
        ExecutableElement originalMethod = originalMethod(substitutionMethod, annotation, originalType, originalName, originalSignature, isStatic);
        if (DEBUG && originalMethod != null) {
            env.getMessager().printMessage(Kind.NOTE, String.format("Found original method %s in type %s.", originalMethod, findEnclosingClass(originalMethod)));
        }
    }

    private TypeMirror[] originalSignature(TypeElement originalType, ExecutableElement method, AnnotationMirror annotation, boolean isStatic) {
        AnnotationValue signatureValue = findAnnotationValue(annotation, ORIGINAL_SIGNATURE);
        String signatureString = resolveAnnotationValue(String.class, signatureValue);
        List<TypeMirror> parameters = new ArrayList<>();
        if (signatureString.equals(ORIGINAL_SIGNATURE_DEFAULT)) {
            for (int i = 0; i < method.getParameters().size(); i++) {
                parameters.add(method.getParameters().get(i).asType());
            }
            if (!isStatic) {
                if (parameters.isEmpty()) {
                    env.getMessager().printMessage(Kind.ERROR, "Method signature must be a static method with the 'this' object as its first parameter", method, annotation);
                    return null;
                } else {
                    TypeMirror thisParam = parameters.remove(0);
                    if (!isSubtype(originalType.asType(), thisParam)) {
                        Name thisName = method.getParameters().get(0).getSimpleName();
                        env.getMessager().printMessage(Kind.ERROR, String.format("The type of %s must assignable from %s", thisName, originalType), method, annotation);
                    }
                }
            }
            parameters.add(0, method.getReturnType());
        } else {
            try {
                APHotSpotSignature signature = new APHotSpotSignature(signatureString);
                parameters.add(signature.getReturnType(env));
                for (int i = 0; i < signature.getParameterCount(false); i++) {
                    parameters.add(signature.getParameterType(env, i));
                }
            } catch (Exception e) {
                /*
                 * That's not good practice and should be changed after APHotSpotSignature has
                 * received a cleanup.
                 */
                env.getMessager().printMessage(Kind.ERROR, String.format("Parsing the signature failed: %s", e.getMessage() != null ? e.getMessage() : e.toString()), method, annotation,
                                signatureValue);
                return null;
            }
        }
        return parameters.toArray(new TypeMirror[parameters.size()]);
    }

    private static String originalName(ExecutableElement substituteMethod, AnnotationMirror substitution) {
        String originalMethodName = resolveAnnotationValue(String.class, findAnnotationValue(substitution, ORIGINAL_METHOD_NAME));
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
            boolean optional = resolveAnnotationValue(Boolean.class, findAnnotationValue(substitutionAnnotation, "optional"));
            if (!optional) {
                env.getMessager().printMessage(Kind.ERROR, String.format("Could not find the original method with name '%s' and parameters '%s'.", originalName, Arrays.toString(signatureParameters)),
                                substitutionMethod, substitutionAnnotation);
            }
            return null;
        }

        if (originalMethod.getModifiers().contains(Modifier.STATIC) != isStatic) {
            boolean optional = resolveAnnotationValue(Boolean.class, findAnnotationValue(substitutionAnnotation, "optional"));
            if (!optional) {
                env.getMessager().printMessage(Kind.ERROR, String.format("The %s element must be set to %s.", ORIGINAL_IS_STATIC, !isStatic), substitutionMethod, substitutionAnnotation);
            }
            return null;
        }

        if (!isTypeCompatible(originalMethod.getReturnType(), signatureReturnType)) {
            env.getMessager().printMessage(
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
            original = env.getTypeUtils().erasure(original);
        }
        if (needsErasure(substitution)) {
            substitution = env.getTypeUtils().erasure(substitution);
        }
        return env.getTypeUtils().isSameType(original, substitution);
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
            t1Erased = env.getTypeUtils().erasure(t1Erased);
        }
        if (needsErasure(t2Erased)) {
            t2Erased = env.getTypeUtils().erasure(t2Erased);
        }
        return env.getTypeUtils().isSubtype(t1Erased, t2Erased);
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
