/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.processor.AbstractProcessor.getAnnotationValueList;
import static org.graalvm.compiler.processor.AbstractProcessor.getSimpleName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.Messager;
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

import org.graalvm.compiler.processor.AbstractProcessor;

/**
 * Handler for the {@value #NODE_INFO_CLASS_NAME} annotation.
 */
public final class NodeIntrinsicHandler extends AnnotationHandler {

    static final String CONSTANT_NODE_PARAMETER_CLASS_NAME = "org.graalvm.compiler.graph.Node.ConstantNodeParameter";
    static final String MARKER_TYPE_CLASS_NAME = "org.graalvm.compiler.nodeinfo.StructuralInput.MarkerType";
    static final String GRAPH_BUILDER_CONTEXT_CLASS_NAME = "org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext";
    static final String STRUCTURAL_INPUT_CLASS_NAME = "org.graalvm.compiler.nodeinfo.StructuralInput";
    static final String RESOLVED_JAVA_METHOD_CLASS_NAME = "jdk.vm.ci.meta.ResolvedJavaMethod";
    static final String RESOLVED_JAVA_TYPE_CLASS_NAME = "jdk.vm.ci.meta.ResolvedJavaType";
    static final String VALUE_NODE_CLASS_NAME = "org.graalvm.compiler.nodes.ValueNode";
    static final String STAMP_CLASS_NAME = "org.graalvm.compiler.core.common.type.Stamp";
    static final String NODE_CLASS_NAME = "org.graalvm.compiler.graph.Node";
    static final String NODE_INFO_CLASS_NAME = "org.graalvm.compiler.nodeinfo.NodeInfo";
    static final String NODE_INTRINSIC_CLASS_NAME = "org.graalvm.compiler.graph.Node.NodeIntrinsic";
    static final String INJECTED_NODE_PARAMETER_CLASS_NAME = "org.graalvm.compiler.graph.Node.InjectedNodeParameter";

    public NodeIntrinsicHandler(AbstractProcessor processor) {
        super(processor, NODE_INTRINSIC_CLASS_NAME);
    }

    @Override
    public void process(Element element, AnnotationMirror annotation, PluginGenerator generator) {
        if (element.getKind() != ElementKind.METHOD) {
            assert false : "Element is guaranteed to be a method.";
            return;
        }

        ExecutableElement intrinsicMethod = (ExecutableElement) element;
        Messager messager = processor.env().getMessager();
        if (!intrinsicMethod.getModifiers().contains(Modifier.STATIC)) {
            messager.printMessage(Kind.ERROR, String.format("A @%s method must be static.", getSimpleName(NODE_INTRINSIC_CLASS_NAME)), element, annotation);
        }
        if (!intrinsicMethod.getModifiers().contains(Modifier.NATIVE)) {
            messager.printMessage(Kind.ERROR, String.format("A @%s method must be native.", getSimpleName(NODE_INTRINSIC_CLASS_NAME)), element, annotation);
        }

        TypeMirror nodeClassMirror = getAnnotationValue(annotation, "value", TypeMirror.class);
        TypeElement nodeClass = processor.asTypeElement(nodeClassMirror);
        if (processor.env().getTypeUtils().isSameType(nodeClassMirror, annotation.getAnnotationType())) {
            // default value
            Element enclosingElement = intrinsicMethod.getEnclosingElement();
            while (enclosingElement != null && enclosingElement.getKind() != ElementKind.CLASS) {
                enclosingElement = enclosingElement.getEnclosingElement();
            }
            if (enclosingElement != null) {
                nodeClass = (TypeElement) enclosingElement;
            } else {
                messager.printMessage(Kind.ERROR, String.format("Cannot find a class enclosing @%s method.", getSimpleName(NODE_INTRINSIC_CLASS_NAME)), element, annotation);
            }
        }

        TypeMirror returnType = intrinsicMethod.getReturnType();
        if (returnType instanceof TypeVariable) {
            messager.printMessage(Kind.ERROR, "@NodeIntrinsic cannot have a generic return type.", element, annotation);
        }

        boolean injectedStampIsNonNull = getAnnotationValue(annotation, "injectedStampIsNonNull", Boolean.class);

        if (returnType.getKind() == TypeKind.VOID) {
            for (VariableElement parameter : intrinsicMethod.getParameters()) {
                if (processor.getAnnotation(parameter, processor.getType(INJECTED_NODE_PARAMETER_CLASS_NAME)) != null) {
                    messager.printMessage(Kind.ERROR, "@NodeIntrinsic with an injected Stamp parameter cannot have a void return type.", element, annotation);
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
                messager.printMessage(Kind.ERROR, String.format("Cannot make a node intrinsic for abstract class %s.", nodeClass.getSimpleName()), element, annotation);
            }
        } else if (!isNodeType(nodeClass)) {
            if (factories.isEmpty()) {
                messager.printMessage(Kind.ERROR, String.format("%s is not a subclass of %s.", nodeClass.getSimpleName(), processor.getType(NODE_CLASS_NAME)), element, annotation);
            }
        } else {
            TypeMirror ret = returnType;
            if (processor.env().getTypeUtils().isAssignable(ret, processor.getType(STRUCTURAL_INPUT_CLASS_NAME))) {
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
            messager.printMessage(Kind.ERROR, msg.toString(), intrinsicMethod, annotation);
        } else if (constructors.size() > 1) {
            msg.format("Found more than one constructor in %s matching node intrinsic:", nodeClass);
            for (ExecutableElement candidate : constructors) {
                msg.format("%n  %s", candidate);
            }
            messager.printMessage(Kind.ERROR, msg.toString(), intrinsicMethod, annotation);
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
            messager.printMessage(Kind.ERROR, msg.toString(), intrinsicMethod, annotation);
        }
    }

    private void checkInputType(TypeElement nodeClass, TypeMirror returnType, Element element, AnnotationMirror annotation) {
        String inputType = getInputType(returnType, element, annotation);
        if (!inputType.equals("Value")) {
            boolean allowed = false;
            List<VariableElement> allowedTypes = getAnnotationValueList(processor.getAnnotation(nodeClass, processor.getType(NODE_INFO_CLASS_NAME)), "allowedUsageTypes", VariableElement.class);
            for (VariableElement allowedType : allowedTypes) {
                if (allowedType.getSimpleName().contentEquals(inputType)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                processor.env().getMessager().printMessage(Kind.ERROR, String.format("@NodeIntrinsic returns input type %s, but only %s is allowed.", inputType, allowedTypes), element, annotation);
            }
        }
    }

    private String getInputType(TypeMirror type, Element element, AnnotationMirror annotation) {
        TypeElement current = processor.asTypeElement(type);
        while (current != null) {
            AnnotationMirror markerType = processor.getAnnotation(current, processor.getType(MARKER_TYPE_CLASS_NAME));
            if (markerType != null) {
                return getAnnotationValue(markerType, "value", VariableElement.class).getSimpleName().toString();
            }

            current = processor.asTypeElement(current.getSuperclass());
        }

        processor.env().getMessager().printMessage(Kind.ERROR,
                        String.format("The class %s is a subclass of StructuralInput, but isn't annotated with @MarkerType. %s", type, element.getAnnotationMirrors()),
                        element, annotation);
        return "Value";
    }

    private boolean isNodeType(TypeElement nodeClass) {
        return processor.env().getTypeUtils().isSubtype(nodeClass.asType(), processor.getType(NODE_CLASS_NAME));
    }

    private TypeMirror[] constructorSignature(ExecutableElement method) {
        TypeMirror[] parameters = new TypeMirror[method.getParameters().size()];
        for (int i = 0; i < method.getParameters().size(); i++) {
            VariableElement parameter = method.getParameters().get(i);
            if (processor.getAnnotation(parameter, processor.getType(CONSTANT_NODE_PARAMETER_CLASS_NAME)) == null) {
                parameters[i] = processor.getType(VALUE_NODE_CLASS_NAME);
            } else {
                TypeMirror type = parameter.asType();
                if (isTypeCompatible(type, processor.getType("java.lang.Class"))) {
                    type = processor.getType(RESOLVED_JAVA_TYPE_CLASS_NAME);
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
                nonMatches.put(method, "Too few arguments");
                continue;
            }

            VariableElement firstArg = method.getParameters().get(0);
            if (!isTypeCompatible(firstArg.asType(), processor.getType(GRAPH_BUILDER_CONTEXT_CLASS_NAME))) {
                nonMatches.put(method, "First argument isn't of type GraphBuilderContext");
                continue;
            }

            VariableElement secondArg = method.getParameters().get(1);
            if (!isTypeCompatible(secondArg.asType(), processor.getType(RESOLVED_JAVA_METHOD_CLASS_NAME))) {
                nonMatches.put(method, "Second argument isn't of type ResolvedJavaMethod");
                continue;
            }

            if (method.getReturnType().getKind() != TypeKind.BOOLEAN) {
                nonMatches.put(method, "Doesn't return boolean");
                continue;
            }

            if (!method.getModifiers().contains(Modifier.STATIC)) {
                nonMatches.put(method, "Method is non-static");
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
            if (processor.getAnnotation(parameter, processor.getType(INJECTED_NODE_PARAMETER_CLASS_NAME)) != null) {
                if (missingStampArgument && processor.env().getTypeUtils().isSameType(paramType, processor.getType(STAMP_CLASS_NAME))) {
                    missingStampArgument = false;
                }
                // skip injected parameters
                continue;
            }
            if (missingStampArgument) {
                nonMatches.put(method, String.format("missing injected %s argument", processor.getType(STAMP_CLASS_NAME)));
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
            nonMatches.put(method, String.format("missing injected %s argument", processor.getType(STAMP_CLASS_NAME)));
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
            original = processor.env().getTypeUtils().erasure(original);
        }
        if (needsErasure(substitution)) {
            substitution = processor.env().getTypeUtils().erasure(substitution);
        }
        return processor.env().getTypeUtils().isSameType(original, substitution);
    }

    private static boolean needsErasure(TypeMirror typeMirror) {
        return typeMirror.getKind() != TypeKind.NONE && typeMirror.getKind() != TypeKind.VOID && !typeMirror.getKind().isPrimitive() && typeMirror.getKind() != TypeKind.OTHER &&
                        typeMirror.getKind() != TypeKind.NULL;
    }
}
