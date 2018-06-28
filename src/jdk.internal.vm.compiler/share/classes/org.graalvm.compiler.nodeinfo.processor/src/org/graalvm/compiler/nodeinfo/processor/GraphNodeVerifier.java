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


package org.graalvm.compiler.nodeinfo.processor;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.TRANSIENT;

import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

import org.graalvm.compiler.processor.AbstractProcessor;

/**
 * Verifies static constraints on nodes.
 */
public class GraphNodeVerifier {

    private final AbstractProcessor processor;

    // Checkstyle: stop
    private final TypeElement Input;
    private final TypeElement OptionalInput;
    private final TypeElement Successor;

    final TypeElement Node;
    private final TypeElement NodeInputList;
    private final TypeElement NodeSuccessorList;

    private final TypeElement object;

    // Checkstyle: resume

    public GraphNodeVerifier(AbstractProcessor processor) {
        this.processor = processor;
        this.Input = getTypeElement("org.graalvm.compiler.graph.Node.Input");
        this.OptionalInput = getTypeElement("org.graalvm.compiler.graph.Node.OptionalInput");
        this.Successor = getTypeElement("org.graalvm.compiler.graph.Node.Successor");
        this.Node = getTypeElement("org.graalvm.compiler.graph.Node");
        this.NodeInputList = getTypeElement("org.graalvm.compiler.graph.NodeInputList");
        this.NodeSuccessorList = getTypeElement("org.graalvm.compiler.graph.NodeSuccessorList");
        this.object = getTypeElement("java.lang.Object");
    }

    /**
     * Returns a type element given a canonical name.
     *
     * @throw {@link NoClassDefFoundError} if a type element does not exist for {@code name}
     */
    public TypeElement getTypeElement(String name) {
        return processor.getTypeElement(name);
    }

    public TypeElement getTypeElement(Class<?> cls) {
        return getTypeElement(cls.getName());
    }

    public TypeMirror getType(String name) {
        return getTypeElement(name).asType();
    }

    public boolean isAssignableWithErasure(Element from, Element to) {
        Types types = processor.env().getTypeUtils();
        TypeMirror fromType = types.erasure(from.asType());
        TypeMirror toType = types.erasure(to.asType());
        return types.isAssignable(fromType, toType);
    }

    private void scanFields(TypeElement node) {
        TypeElement currentClazz = node;
        do {
            for (VariableElement field : ElementFilter.fieldsIn(currentClazz.getEnclosedElements())) {
                Set<Modifier> modifiers = field.getModifiers();
                if (modifiers.contains(STATIC) || modifiers.contains(TRANSIENT)) {
                    continue;
                }

                List<? extends AnnotationMirror> annotations = field.getAnnotationMirrors();

                boolean isNonOptionalInput = findAnnotationMirror(annotations, Input) != null;
                boolean isOptionalInput = findAnnotationMirror(annotations, OptionalInput) != null;
                boolean isSuccessor = findAnnotationMirror(annotations, Successor) != null;

                if (isNonOptionalInput || isOptionalInput) {
                    if (findAnnotationMirror(annotations, Successor) != null) {
                        throw new ElementException(field, "Field cannot be both input and successor");
                    } else if (isNonOptionalInput && isOptionalInput) {
                        throw new ElementException(field, "Inputs must be either optional or non-optional");
                    } else if (isAssignableWithErasure(field, NodeInputList)) {
                        if (modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Input list field must not be final");
                        }
                        if (modifiers.contains(PUBLIC)) {
                            throw new ElementException(field, "Input list field must not be public");
                        }
                    } else {
                        if (!isAssignableWithErasure(field, Node) && field.getKind() == ElementKind.INTERFACE) {
                            throw new ElementException(field, "Input field type must be an interface or assignable to Node");
                        }
                        if (modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Input field must not be final");
                        }
                        if (modifiers.contains(PUBLIC)) {
                            throw new ElementException(field, "Input field must not be public");
                        }
                    }
                } else if (isSuccessor) {
                    if (isAssignableWithErasure(field, NodeSuccessorList)) {
                        if (modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Successor list field must not be final");
                        }
                        if (modifiers.contains(PUBLIC)) {
                            throw new ElementException(field, "Successor list field must not be public");
                        }
                    } else {
                        if (!isAssignableWithErasure(field, Node)) {
                            throw new ElementException(field, "Successor field must be a Node type");
                        }
                        if (modifiers.contains(FINAL)) {
                            throw new ElementException(field, "Successor field must not be final");
                        }
                        if (modifiers.contains(PUBLIC)) {
                            throw new ElementException(field, "Successor field must not be public");
                        }
                    }

                } else {
                    if (isAssignableWithErasure(field, Node) && !field.getSimpleName().contentEquals("Null")) {
                        throw new ElementException(field, "Node field must be annotated with @" + Input.getSimpleName() + ", @" + OptionalInput.getSimpleName() + " or @" + Successor.getSimpleName());
                    }
                    if (isAssignableWithErasure(field, NodeInputList)) {
                        throw new ElementException(field, "NodeInputList field must be annotated with @" + Input.getSimpleName() + " or @" + OptionalInput.getSimpleName());
                    }
                    if (isAssignableWithErasure(field, NodeSuccessorList)) {
                        throw new ElementException(field, "NodeSuccessorList field must be annotated with @" + Successor.getSimpleName());
                    }
                    if (modifiers.contains(PUBLIC) && !modifiers.contains(FINAL)) {
                        throw new ElementException(field, "Data field must be final if public");
                    }
                }
            }
            currentClazz = getSuperType(currentClazz);
        } while (!isObject(getSuperType(currentClazz).asType()));
    }

    private AnnotationMirror findAnnotationMirror(List<? extends AnnotationMirror> mirrors, TypeElement expectedAnnotationType) {
        for (AnnotationMirror mirror : mirrors) {
            if (sameType(mirror.getAnnotationType(), expectedAnnotationType.asType())) {
                return mirror;
            }
        }
        return null;
    }

    private boolean isObject(TypeMirror type) {
        return sameType(object.asType(), type);
    }

    private boolean sameType(TypeMirror type1, TypeMirror type2) {
        return processor.env().getTypeUtils().isSameType(type1, type2);
    }

    private TypeElement getSuperType(TypeElement element) {
        if (element.getSuperclass() != null) {
            return processor.asTypeElement(element.getSuperclass());
        }
        return null;
    }

    void verify(TypeElement node) {
        scanFields(node);

        boolean foundValidConstructor = false;
        for (ExecutableElement constructor : ElementFilter.constructorsIn(node.getEnclosedElements())) {
            if (constructor.getModifiers().contains(PRIVATE)) {
                continue;
            } else if (!constructor.getModifiers().contains(PUBLIC) && !constructor.getModifiers().contains(PROTECTED)) {
                throw new ElementException(constructor, "Node class constructor must be public or protected");
            }

            foundValidConstructor = true;
        }

        if (!foundValidConstructor) {
            throw new ElementException(node, "Node class must have at least one protected constructor");
        }
    }
}
