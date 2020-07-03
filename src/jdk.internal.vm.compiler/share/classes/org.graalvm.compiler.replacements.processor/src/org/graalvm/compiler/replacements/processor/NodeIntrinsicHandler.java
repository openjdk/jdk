/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
    static final String RESOLVED_JAVA_TYPE_CLASS_NAME = "jdk.vm.ci.meta.ResolvedJavaType";
    static final String VALUE_NODE_CLASS_NAME = "org.graalvm.compiler.nodes.ValueNode";
    static final String STAMP_CLASS_NAME = "org.graalvm.compiler.core.common.type.Stamp";
    static final String NODE_CLASS_NAME = "org.graalvm.compiler.graph.Node";
    static final String NODE_INFO_CLASS_NAME = "org.graalvm.compiler.nodeinfo.NodeInfo";
    static final String NODE_INTRINSIC_CLASS_NAME = "org.graalvm.compiler.graph.Node.NodeIntrinsic";
    static final String NODE_INTRINSIC_FACTORY_CLASS_NAME = "org.graalvm.compiler.graph.Node.NodeIntrinsicFactory";
    static final String INJECTED_NODE_PARAMETER_CLASS_NAME = "org.graalvm.compiler.graph.Node.InjectedNodeParameter";
    static final String FOREIGN_CALL_DESCRIPTOR_CLASS_NAME = "org.graalvm.compiler.core.common.spi.ForeignCallDescriptor";

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
        boolean isFactory = processor.getAnnotation(nodeClass, processor.getType(NODE_INTRINSIC_FACTORY_CLASS_NAME)) != null;

        if (returnType.getKind() == TypeKind.VOID) {
            for (VariableElement parameter : intrinsicMethod.getParameters()) {
                if (processor.getAnnotation(parameter, processor.getType(INJECTED_NODE_PARAMETER_CLASS_NAME)) != null) {
                    messager.printMessage(Kind.ERROR, "@NodeIntrinsic with an injected Stamp parameter cannot have a void return type.", element, annotation);
                    break;
                }
            }
        }
        Formatter msg = new Formatter();
        List<ExecutableElement> factories = findIntrinsifyFactoryMethods(nodeClass);
        if (factories.size() > 0) {
            boolean hadError = false;
            if (isFactory) {
                for (ExecutableElement candidate : factories) {
                    String error = checkIntrinsifyFactorySignature(candidate);
                    if (error != null) {
                        messager.printMessage(Kind.ERROR, msg.format("intrinsify method has invalid signature: %s%n%s", error, candidate).toString(), candidate);
                        hadError = true;
                    }
                }
            } else {
                for (ExecutableElement candidate : factories) {
                    messager.printMessage(Kind.ERROR, String.format("Found intrinsify methods in %s which is not a NodeIntrinsicFactory", nodeClass), candidate);
                    hadError = true;
                }
            }
            if (hadError) {
                return;
            }
        }

        TypeMirror[] constructorSignature = constructorSignature(intrinsicMethod);
        Map<ExecutableElement, String> nonMatches = new HashMap<>();
        if (isFactory) {
            List<ExecutableElement> candidates = findIntrinsifyFactoryMethods(factories, constructorSignature, nonMatches, injectedStampIsNonNull);
            if (checkTooManyElements(annotation, intrinsicMethod, messager, nodeClass, "factories", candidates, msg)) {
                return;
            }
            if (candidates.size() == 1) {
                generator.addPlugin(new GeneratedNodeIntrinsicPlugin.CustomFactoryPlugin(intrinsicMethod, candidates.get(0), constructorSignature));
                return;
            }
        } else {
            if (nodeClass.getModifiers().contains(Modifier.ABSTRACT)) {
                messager.printMessage(Kind.ERROR, String.format("Cannot make a node intrinsic for abstract class %s.", nodeClass.getSimpleName()), element, annotation);
                return;
            } else if (!isNodeType(nodeClass)) {
                messager.printMessage(Kind.ERROR, String.format("%s is not a subclass of %s.", nodeClass.getSimpleName(), processor.getType(NODE_CLASS_NAME)), element, annotation);
                return;
            }
            if (processor.env().getTypeUtils().isAssignable(returnType, processor.getType(STRUCTURAL_INPUT_CLASS_NAME))) {
                checkInputType(nodeClass, returnType, element, annotation);
            }

            List<ExecutableElement> constructors = findConstructors(nodeClass, constructorSignature, nonMatches, injectedStampIsNonNull);
            if (checkTooManyElements(annotation, intrinsicMethod, messager, nodeClass, "constructors", constructors, msg)) {
                return;
            }
            if (constructors.size() == 1) {
                generator.addPlugin(new GeneratedNodeIntrinsicPlugin.ConstructorPlugin(intrinsicMethod, constructors.get(0), constructorSignature));
                return;
            }
        }
        String label = isFactory ? "factories" : "constructors";
        msg.format("Could not find any %s in %s matching node intrinsic", label, nodeClass);
        if (!nonMatches.isEmpty()) {
            msg.format("%nThese %s failed to match:", label);
            for (Map.Entry<ExecutableElement, String> e : nonMatches.entrySet()) {
                msg.format("%n  %s: %s", e.getKey(), e.getValue());
            }
        }
        messager.printMessage(Kind.ERROR, msg.toString(), intrinsicMethod, annotation);
    }

    private static boolean checkTooManyElements(AnnotationMirror annotation, ExecutableElement intrinsicMethod, Messager messager, TypeElement nodeClass, String kind, List<ExecutableElement> elements,
                    Formatter msg) {
        if (elements.size() > 1) {
            msg.format("Found more than one %s in %s matching node intrinsic:", kind, nodeClass);
            for (ExecutableElement candidate : elements) {
                msg.format("%n  %s", candidate);
            }
            messager.printMessage(Kind.ERROR, msg.toString(), intrinsicMethod, annotation);
            return true;
        }
        return false;
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

    private String checkIntrinsifyFactorySignature(ExecutableElement method) {
        if (method.getParameters().size() < 1) {
            return "Too few arguments";
        }

        VariableElement firstArg = method.getParameters().get(0);
        if (!isTypeCompatible(firstArg.asType(), processor.getType(GRAPH_BUILDER_CONTEXT_CLASS_NAME))) {
            return "First argument isn't of type GraphBuilderContext";
        }

        if (method.getReturnType().getKind() != TypeKind.BOOLEAN) {
            return "Doesn't return boolean";
        }

        if (!method.getModifiers().contains(Modifier.STATIC)) {
            return "Method is non-static";
        }

        if (!method.getModifiers().contains(Modifier.PUBLIC)) {
            return "Method is non-public";
        }
        return null;
    }

    private static List<ExecutableElement> findIntrinsifyFactoryMethods(TypeElement nodeClass) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(nodeClass.getEnclosedElements());
        List<ExecutableElement> found = new ArrayList<>(1);
        for (ExecutableElement method : methods) {
            if (method.getSimpleName().toString().equals("intrinsify")) {
                found.add(method);
            }
        }
        return found;
    }

    private List<ExecutableElement> findIntrinsifyFactoryMethods(List<ExecutableElement> intrinsifyFactoryMethods, TypeMirror[] signature, Map<ExecutableElement, String> nonMatches,
                    boolean requiresInjectedStamp) {
        List<ExecutableElement> found = new ArrayList<>(1);
        for (ExecutableElement method : intrinsifyFactoryMethods) {
            if (matchSignature(1, method, signature, nonMatches, requiresInjectedStamp)) {
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
