/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;

import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.InjectedNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.StructuralInput.MarkerType;

public final class NodeIntrinsicVerifier extends AbstractVerifier {

    private static final String NODE_CLASS_NAME = "value";

    private TypeMirror nodeType() {
        return env.getElementUtils().getTypeElement("org.graalvm.compiler.graph.Node").asType();
    }

    private TypeMirror stampType() {
        return env.getElementUtils().getTypeElement("org.graalvm.compiler.core.common.type.Stamp").asType();
    }

    private TypeMirror valueNodeType() {
        return env.getElementUtils().getTypeElement("org.graalvm.compiler.nodes.ValueNode").asType();
    }

    private TypeMirror classType() {
        return env.getElementUtils().getTypeElement("java.lang.Class").asType();
    }

    private TypeMirror resolvedJavaTypeType() {
        return env.getElementUtils().getTypeElement("jdk.vm.ci.meta.ResolvedJavaType").asType();
    }

    private TypeMirror resolvedJavaMethodType() {
        return env.getElementUtils().getTypeElement("jdk.vm.ci.meta.ResolvedJavaMethod").asType();
    }

    private TypeMirror structuralInputType() {
        return env.getElementUtils().getTypeElement("org.graalvm.compiler.nodeinfo.StructuralInput").asType();
    }

    private TypeMirror graphBuilderContextType() {
        return env.getElementUtils().getTypeElement("org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext").asType();
    }

    public NodeIntrinsicVerifier(ProcessingEnvironment env) {
        super(env);
    }

    @Override
    public Class<? extends Annotation> getAnnotationClass() {
        return NodeIntrinsic.class;
    }

    @Override
    public void verify(Element element, AnnotationMirror annotation, PluginGenerator generator) {
        if (element.getKind() != ElementKind.METHOD) {
            assert false : "Element is guaranteed to be a method.";
            return;
        }

        ExecutableElement intrinsicMethod = (ExecutableElement) element;
        if (!intrinsicMethod.getModifiers().contains(Modifier.STATIC)) {
            env.getMessager().printMessage(Kind.ERROR, String.format("A @%s method must be static.", NodeIntrinsic.class.getSimpleName()), element, annotation);
        }
        if (!intrinsicMethod.getModifiers().contains(Modifier.NATIVE)) {
            env.getMessager().printMessage(Kind.ERROR, String.format("A @%s method must be native.", NodeIntrinsic.class.getSimpleName()), element, annotation);
        }

        TypeMirror nodeClassMirror = resolveAnnotationValue(TypeMirror.class, findAnnotationValue(annotation, NODE_CLASS_NAME));
        TypeElement nodeClass = (TypeElement) env.getTypeUtils().asElement(nodeClassMirror);
        if (nodeClass.getSimpleName().contentEquals(NodeIntrinsic.class.getSimpleName())) {
            // default value
            Element enclosingElement = intrinsicMethod.getEnclosingElement();
            while (enclosingElement != null && enclosingElement.getKind() != ElementKind.CLASS) {
                enclosingElement = enclosingElement.getEnclosingElement();
            }
            if (enclosingElement != null) {
                nodeClass = (TypeElement) enclosingElement;
            }
        }

        TypeMirror returnType = intrinsicMethod.getReturnType();
        if (returnType instanceof TypeVariable) {
            env.getMessager().printMessage(Kind.ERROR, "@NodeIntrinsic cannot have a generic return type.", element, annotation);
        }

        boolean injectedStampIsNonNull = intrinsicMethod.getAnnotation(NodeIntrinsic.class).injectedStampIsNonNull();

        if (returnType.getKind() == TypeKind.VOID) {
            for (VariableElement parameter : intrinsicMethod.getParameters()) {
                if (parameter.getAnnotation(InjectedNodeParameter.class) != null) {
                    env.getMessager().printMessage(Kind.ERROR, "@NodeIntrinsic with an injected Stamp parameter cannot have a void return type.", element, annotation);
                    break;
                }
            }
        }

        TypeMirror[] constructorSignature = constructorSignature(intrinsicMethod);
        Map<ExecutableElement, String> nonMatches = new HashMap<>();
        List<ExecutableElement> factories = findIntrinsifyFactoryMethod(nodeClass, constructorSignature, nonMatches, injectedStampIsNonNull);
        List<ExecutableElement> constructors = Collections.emptyList();
        if (nodeClass.getModifiers().contains(Modifier.ABSTRACT)) {
            if (factories.isEmpty()) {
                env.getMessager().printMessage(Kind.ERROR, String.format("Cannot make a node intrinsic for an abstract class %s.", nodeClass.getSimpleName()), element, annotation);
            }
        } else if (!isNodeType(nodeClass)) {
            if (factories.isEmpty()) {
                env.getMessager().printMessage(Kind.ERROR, String.format("%s is not a subclass of %s.", nodeClass.getSimpleName(), nodeType()), element, annotation);
            }
        } else {
            TypeMirror ret = returnType;
            if (env.getTypeUtils().isAssignable(ret, structuralInputType())) {
                checkInputType(nodeClass, ret, element, annotation);
            }

            constructors = findConstructors(nodeClass, constructorSignature, nonMatches, injectedStampIsNonNull);
        }
        Formatter msg = new Formatter();
        if (factories.size() > 1) {
            msg.format("Found more than one factory in %s matching node intrinsic:", nodeClass);
            for (ExecutableElement candidate : factories) {
                msg.format("%n  %s", candidate);
            }
            env.getMessager().printMessage(Kind.ERROR, msg.toString(), intrinsicMethod, annotation);
        } else if (constructors.size() > 1) {
            msg.format("Found more than one constructor in %s matching node intrinsic:", nodeClass);
            for (ExecutableElement candidate : constructors) {
                msg.format("%n  %s", candidate);
            }
            env.getMessager().printMessage(Kind.ERROR, msg.toString(), intrinsicMethod, annotation);
        } else if (factories.size() == 1) {
            generator.addPlugin(new GeneratedNodeIntrinsicPlugin.CustomFactoryPlugin(intrinsicMethod, factories.get(0), constructorSignature));
        } else if (constructors.size() == 1) {
            generator.addPlugin(new GeneratedNodeIntrinsicPlugin.ConstructorPlugin(intrinsicMethod, constructors.get(0), constructorSignature));
        } else {
            msg.format("Could not find any factories or constructors in %s matching node intrinsic", nodeClass);
            if (!nonMatches.isEmpty()) {
                msg.format("%nFactories and constructors that failed to match:");
                for (Map.Entry<ExecutableElement, String> e : nonMatches.entrySet()) {
                    msg.format("%n  %s: %s", e.getKey(), e.getValue());
                }
            }
            env.getMessager().printMessage(Kind.ERROR, msg.toString(), intrinsicMethod, annotation);
        }
    }

    private void checkInputType(TypeElement nodeClass, TypeMirror returnType, Element element, AnnotationMirror annotation) {
        InputType inputType = getInputType(returnType, element, annotation);
        if (inputType != InputType.Value) {
            boolean allowed = false;
            InputType[] allowedTypes = nodeClass.getAnnotation(NodeInfo.class).allowedUsageTypes();
            for (InputType allowedType : allowedTypes) {
                if (inputType == allowedType) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                env.getMessager().printMessage(Kind.ERROR, String.format("@NodeIntrinsic returns input type %s, but only %s is allowed.", inputType, Arrays.toString(allowedTypes)), element,
                                annotation);
            }
        }
    }

    private InputType getInputType(TypeMirror type, Element element, AnnotationMirror annotation) {
        TypeElement current = (TypeElement) env.getTypeUtils().asElement(type);
        while (current != null) {
            MarkerType markerType = current.getAnnotation(MarkerType.class);
            if (markerType != null) {
                return markerType.value();
            }

            current = (TypeElement) env.getTypeUtils().asElement(current.getSuperclass());
        }

        env.getMessager().printMessage(Kind.ERROR, String.format("The class %s is a subclass of StructuralInput, but isn't annotated with @MarkerType.", type), element, annotation);
        return InputType.Value;
    }

    private boolean isNodeType(TypeElement nodeClass) {
        return env.getTypeUtils().isSubtype(nodeClass.asType(), nodeType());
    }

    private TypeMirror[] constructorSignature(ExecutableElement method) {
        TypeMirror[] parameters = new TypeMirror[method.getParameters().size()];
        for (int i = 0; i < method.getParameters().size(); i++) {
            VariableElement parameter = method.getParameters().get(i);
            if (parameter.getAnnotation(ConstantNodeParameter.class) == null) {
                parameters[i] = valueNodeType();
            } else {
                TypeMirror type = parameter.asType();
                if (isTypeCompatible(type, classType())) {
                    type = resolvedJavaTypeType();
                }
                parameters[i] = type;
            }
        }
        return parameters;
    }

    private List<ExecutableElement> findConstructors(TypeElement nodeClass, TypeMirror[] signature, Map<ExecutableElement, String> nonMatches, boolean requiresInjectedStamp) {
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(nodeClass.getEnclosedElements());
        List<ExecutableElement> found = new ArrayList<>(constructors.size());
        for (ExecutableElement constructor : constructors) {
            if (matchSignature(0, constructor, signature, nonMatches, requiresInjectedStamp)) {
                found.add(constructor);
            }
        }
        return found;
    }

    private List<ExecutableElement> findIntrinsifyFactoryMethod(TypeElement nodeClass, TypeMirror[] signature, Map<ExecutableElement, String> nonMatches, boolean requiresInjectedStamp) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(nodeClass.getEnclosedElements());
        List<ExecutableElement> found = new ArrayList<>(methods.size());
        for (ExecutableElement method : methods) {
            if (!method.getSimpleName().toString().equals("intrinsify")) {
                continue;
            }

            if (method.getParameters().size() < 2) {
                continue;
            }

            VariableElement firstArg = method.getParameters().get(0);
            if (!isTypeCompatible(firstArg.asType(), graphBuilderContextType())) {
                continue;
            }

            VariableElement secondArg = method.getParameters().get(1);
            if (!isTypeCompatible(secondArg.asType(), resolvedJavaMethodType())) {
                continue;
            }

            if (method.getReturnType().getKind() != TypeKind.BOOLEAN) {
                continue;
            }

            if (matchSignature(2, method, signature, nonMatches, requiresInjectedStamp)) {
                found.add(method);
            }
        }
        return found;
    }

    private boolean matchSignature(int numSkippedParameters, ExecutableElement method, TypeMirror[] signature, Map<ExecutableElement, String> nonMatches, boolean requiresInjectedStamp) {
        int sIdx = 0;
        int cIdx = numSkippedParameters;
        boolean missingStampArgument = requiresInjectedStamp;
        while (cIdx < method.getParameters().size()) {
            VariableElement parameter = method.getParameters().get(cIdx++);
            TypeMirror paramType = parameter.asType();
            if (parameter.getAnnotation(InjectedNodeParameter.class) != null) {
                if (missingStampArgument && env.getTypeUtils().isSameType(paramType, stampType())) {
                    missingStampArgument = false;
                }
                // skip injected parameters
                continue;
            }
            if (missingStampArgument) {
                nonMatches.put(method, String.format("missing injected %s argument", stampType()));
                return false;
            }

            if (cIdx == method.getParameters().size() && paramType.getKind() == TypeKind.ARRAY) {
                // last argument of constructor is varargs, match remaining intrinsic arguments
                TypeMirror varargsType = ((ArrayType) paramType).getComponentType();
                while (sIdx < signature.length) {
                    if (!isTypeCompatible(varargsType, signature[sIdx++])) {
                        nonMatches.put(method, String.format("the types of argument %d are incompatible: %s != %s", sIdx, varargsType, signature[sIdx - 1]));
                        return false;
                    }
                }
            } else if (sIdx >= signature.length) {
                // too many arguments in intrinsic method
                nonMatches.put(method, "too many arguments");
                return false;
            } else if (!isTypeCompatible(paramType, signature[sIdx++])) {
                nonMatches.put(method, String.format("the type of argument %d is incompatible: %s != %s", sIdx, paramType, signature[sIdx - 1]));
                return false;
            }
        }
        if (missingStampArgument) {
            nonMatches.put(method, String.format("missing injected %s argument", stampType()));
            return false;
        }

        if (sIdx != signature.length) {
            nonMatches.put(method, "not enough arguments");
            return false;
        }
        return true;
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

    private static boolean needsErasure(TypeMirror typeMirror) {
        return typeMirror.getKind() != TypeKind.NONE && typeMirror.getKind() != TypeKind.VOID && !typeMirror.getKind().isPrimitive() && typeMirror.getKind() != TypeKind.OTHER &&
                        typeMirror.getKind() != TypeKind.NULL;
    }
}
