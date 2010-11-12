/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.javac.code;

import javax.lang.model.element.ElementKind;

import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

/**
 * Contains operations specific to processing type annotations
 */
public class TypeAnnotations {
    private static final Context.Key<TypeAnnotations> key
        = new Context.Key<TypeAnnotations>();

    public static TypeAnnotations instance(Context context) {
        TypeAnnotations instance = context.get(key);
        if (instance == null)
            instance = new TypeAnnotations(context);
        return instance;
    }

    protected TypeAnnotations(Context context) {
        context.put(key, this);
    }

    public void taFillAndLift(JCClassDecl tree, boolean visitBodies) {
//308        new TypeAnnotationPositions().scan(tree);
//308        new TypeAnnotationLift().scan(tree);
    }

    private static class TypeAnnotationPositions extends TreeScanner {

        private ListBuffer<JCTree> frames = ListBuffer.lb();
        private void push(JCTree t) { frames = frames.prepend(t); }
        private JCTree pop() { return frames.next(); }
        private JCTree peek2() { return frames.toList().tail.head; }

        @Override
        public void scan(JCTree tree) {
            push(tree);
            super.scan(tree);
            pop();
        }

        private boolean inClass = false;

        @Override
        public void visitClassDef(JCClassDecl tree) {
           if (!inClass) {
               // Do not recurse into nested and inner classes since
               // TransTypes.visitClassDef makes an invocation for each class
               // separately.
               inClass = true;
               try {
                   super.visitClassDef(tree);
               } finally {
                   inClass = false;
               }
           }
        }

        private TypeAnnotationPosition resolveFrame(JCTree tree, JCTree frame,
                List<JCTree> path, TypeAnnotationPosition p) {
            switch (frame.getKind()) {
                case TYPE_CAST:
                    p.type = TargetType.TYPECAST;
                    p.pos = frame.pos;
                    return p;

                case INSTANCE_OF:
                    p.type = TargetType.INSTANCEOF;
                    p.pos = frame.pos;
                    return p;

                case NEW_CLASS:
                    p.type = TargetType.NEW;
                    p.pos = frame.pos;
                    return p;

                case NEW_ARRAY:
                    p.type = TargetType.NEW;
                    p.pos = frame.pos;
                    return p;

                case ANNOTATION_TYPE:
                case CLASS:
                case ENUM:
                case INTERFACE:
                    p.pos = frame.pos;
                    if (((JCClassDecl)frame).extending == tree) {
                        p.type = TargetType.CLASS_EXTENDS;
                        p.type_index = -1;
                    } else if (((JCClassDecl)frame).implementing.contains(tree)) {
                        p.type = TargetType.CLASS_EXTENDS;
                        p.type_index = ((JCClassDecl)frame).implementing.indexOf(tree);
                    } else if (((JCClassDecl)frame).typarams.contains(tree)) {
                        p.type = TargetType.CLASS_TYPE_PARAMETER;
                        p.parameter_index = ((JCClassDecl)frame).typarams.indexOf(tree);
                    } else
                        throw new AssertionError();
                    return p;

                case METHOD: {
                    JCMethodDecl frameMethod = (JCMethodDecl)frame;
                    p.pos = frame.pos;
                    if (frameMethod.receiverAnnotations.contains(tree))
                        p.type = TargetType.METHOD_RECEIVER;
                    else if (frameMethod.thrown.contains(tree)) {
                        p.type = TargetType.THROWS;
                        p.type_index = frameMethod.thrown.indexOf(tree);
                    } else if (((JCMethodDecl)frame).restype == tree) {
                        p.type = TargetType.METHOD_RETURN_GENERIC_OR_ARRAY;
                    } else if (frameMethod.typarams.contains(tree)) {
                        p.type = TargetType.METHOD_TYPE_PARAMETER;
                        p.parameter_index = frameMethod.typarams.indexOf(tree);
                    } else
                        throw new AssertionError();
                    return p;
                }
                case MEMBER_SELECT: {
                    JCFieldAccess fieldFrame = (JCFieldAccess)frame;
                    if ("class".contentEquals(fieldFrame.name)) {
                        p.type = TargetType.CLASS_LITERAL;
                        p.pos = TreeInfo.innermostType(fieldFrame.selected).pos;
                    } else
                        throw new AssertionError();
                    return p;
                }
                case PARAMETERIZED_TYPE: {
                    TypeAnnotationPosition nextP;
                    if (((JCTypeApply)frame).clazz == tree)
                        nextP = p; // generic: RAW; noop
                    else if (((JCTypeApply)frame).arguments.contains(tree))
                        p.location = p.location.prepend(
                                ((JCTypeApply)frame).arguments.indexOf(tree));
                    else
                        throw new AssertionError();

                    List<JCTree> newPath = path.tail;
                    return resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                }

                case ARRAY_TYPE: {
                    p.location = p.location.prepend(0);
                    List<JCTree> newPath = path.tail;
                    return resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                }

                case TYPE_PARAMETER:
                    if (path.tail.tail.head.getTag() == JCTree.CLASSDEF) {
                        JCClassDecl clazz = (JCClassDecl)path.tail.tail.head;
                        p.type = TargetType.CLASS_TYPE_PARAMETER_BOUND;
                        p.parameter_index = clazz.typarams.indexOf(path.tail.head);
                        p.bound_index = ((JCTypeParameter)frame).bounds.indexOf(tree);
                    } else if (path.tail.tail.head.getTag() == JCTree.METHODDEF) {
                        JCMethodDecl method = (JCMethodDecl)path.tail.tail.head;
                        p.type = TargetType.METHOD_TYPE_PARAMETER_BOUND;
                        p.parameter_index = method.typarams.indexOf(path.tail.head);
                        p.bound_index = ((JCTypeParameter)frame).bounds.indexOf(tree);
                    } else
                        throw new AssertionError();
                    p.pos = frame.pos;
                    return p;

                case VARIABLE:
                    VarSymbol v = ((JCVariableDecl)frame).sym;
                    p.pos = frame.pos;
                    switch (v.getKind()) {
                        case LOCAL_VARIABLE:
                            p.type = TargetType.LOCAL_VARIABLE; break;
                        case FIELD:
                            p.type = TargetType.FIELD_GENERIC_OR_ARRAY; break;
                        case PARAMETER:
                            p.type = TargetType.METHOD_PARAMETER_GENERIC_OR_ARRAY;
                            p.parameter_index = methodParamIndex(path, frame);
                            break;
                        default: throw new AssertionError();
                    }
                    return p;

//308                case ANNOTATED_TYPE: {
//308                    List<JCTree> newPath = path.tail;
//308                    return resolveFrame(newPath.head, newPath.tail.head,
//308                            newPath, p);
//308                }

                case METHOD_INVOCATION: {
                    JCMethodInvocation invocation = (JCMethodInvocation)frame;
                    if (!invocation.typeargs.contains(tree))
                        throw new AssertionError("{" + tree + "} is not an argument in the invocation: " + invocation);
                    p.type = TargetType.METHOD_TYPE_ARGUMENT;
                    p.pos = invocation.pos;
                    p.type_index = invocation.typeargs.indexOf(tree);
                    return p;
                }

                case EXTENDS_WILDCARD:
                case SUPER_WILDCARD: {
                    p.type = TargetType.WILDCARD_BOUND;
                    List<JCTree> newPath = path.tail;

                    TypeAnnotationPosition wildcard =
                        resolveFrame(newPath.head, newPath.tail.head, newPath,
                                new TypeAnnotationPosition());
                    if (!wildcard.location.isEmpty())
                        wildcard.type = wildcard.type.getGenericComplement();
                    p.wildcard_position = wildcard;
                    p.pos = frame.pos;
                    return p;
                }
            }
            return p;
        }

        private void setTypeAnnotationPos(List<JCTypeAnnotation> annotations, TypeAnnotationPosition position) {
            for (JCTypeAnnotation anno : annotations) {
                anno.annotation_position = position;
                anno.attribute_field.position = position;
            }
        }

        @Override
        public void visitNewArray(JCNewArray tree) {
            findPosition(tree, tree, tree.annotations);
            int dimAnnosCount = tree.dimAnnotations.size();

            // handle annotations associated with dimentions
            for (int i = 0; i < dimAnnosCount; ++i) {
                TypeAnnotationPosition p = new TypeAnnotationPosition();
                p.type = TargetType.NEW_GENERIC_OR_ARRAY;
                p.pos = tree.pos;
                p.location = p.location.append(i);
                setTypeAnnotationPos(tree.dimAnnotations.get(i), p);
            }

            // handle "free" annotations
            int i = dimAnnosCount == 0 ? 0 : dimAnnosCount - 1;
            JCExpression elemType = tree.elemtype;
            while (elemType != null) {
                if (elemType.getTag() == JCTree.ANNOTATED_TYPE) {
                    JCAnnotatedType at = (JCAnnotatedType)elemType;
                    TypeAnnotationPosition p = new TypeAnnotationPosition();
                    p.type = TargetType.NEW_GENERIC_OR_ARRAY;
                    p.pos = tree.pos;
                    p.location = p.location.append(i);
                    setTypeAnnotationPos(at.annotations, p);
                    elemType = at.underlyingType;
                } else if (elemType.getTag() == JCTree.TYPEARRAY) {
                    ++i;
                    elemType = ((JCArrayTypeTree)elemType).elemtype;
                } else
                    break;
            }

            // find annotations locations of initializer elements
            scan(tree.elems);
        }

        @Override
        public void visitAnnotatedType(JCAnnotatedType tree) {
            findPosition(tree, peek2(), tree.annotations);
            super.visitAnnotatedType(tree);
        }

        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            TypeAnnotationPosition p = new TypeAnnotationPosition();
            p.type = TargetType.METHOD_RECEIVER;
            setTypeAnnotationPos(tree.receiverAnnotations, p);
            super.visitMethodDef(tree);
        }
        @Override
        public void visitTypeParameter(JCTypeParameter tree) {
            findPosition(tree, peek2(), tree.annotations);
            super.visitTypeParameter(tree);
        }

        void findPosition(JCTree tree, JCTree frame, List<JCTypeAnnotation> annotations) {
            if (!annotations.isEmpty()) {
                TypeAnnotationPosition p =
                        resolveFrame(tree, frame, frames.toList(),
                                new TypeAnnotationPosition());
                if (!p.location.isEmpty())
                    p.type = p.type.getGenericComplement();
                setTypeAnnotationPos(annotations, p);
            }
        }

        private int methodParamIndex(List<JCTree> path, JCTree param) {
            List<JCTree> curr = path;
            if (curr.head != param)
                curr = path.tail;
            JCMethodDecl method = (JCMethodDecl)curr.tail.head;
            return method.params.indexOf(param);
        }
    }

    private static class TypeAnnotationLift extends TreeScanner {
        List<Attribute.TypeCompound> recordedTypeAnnotations = List.nil();

        boolean isInner = false;
        @Override
        public void visitClassDef(JCClassDecl tree) {
            if (isInner) {
                // tree is an inner class tree.  stop now.
                // TransTypes.visitClassDef makes an invocation for each class
                // separately.
                return;
            }
            isInner = true;
            List<Attribute.TypeCompound> prevTAs = recordedTypeAnnotations;
            recordedTypeAnnotations = List.nil();
            try {
                super.visitClassDef(tree);
            } finally {
                tree.sym.typeAnnotations = tree.sym.typeAnnotations.appendList(recordedTypeAnnotations);
                recordedTypeAnnotations = prevTAs;
            }
        }

        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            List<Attribute.TypeCompound> prevTAs = recordedTypeAnnotations;
            recordedTypeAnnotations = List.nil();
            try {
                super.visitMethodDef(tree);
            } finally {
                tree.sym.typeAnnotations = tree.sym.typeAnnotations.appendList(recordedTypeAnnotations);
                recordedTypeAnnotations = prevTAs;
            }
        }

        @Override
        public void visitVarDef(JCVariableDecl tree) {
            List<Attribute.TypeCompound> prevTAs = recordedTypeAnnotations;
            recordedTypeAnnotations = List.nil();
            ElementKind kind = tree.sym.getKind();
            if (kind == ElementKind.LOCAL_VARIABLE && tree.mods.annotations.nonEmpty()) {
                // need to lift the annotations
                TypeAnnotationPosition position = new TypeAnnotationPosition();
                position.pos = tree.pos;
                position.type = TargetType.LOCAL_VARIABLE;
                for (Attribute.Compound attribute : tree.sym.attributes_field) {
                    Attribute.TypeCompound tc =
                        new Attribute.TypeCompound(attribute.type, attribute.values, position);
                    recordedTypeAnnotations = recordedTypeAnnotations.append(tc);
                }
            }
            try {
                super.visitVarDef(tree);
            } finally {
                if (kind.isField() || kind == ElementKind.LOCAL_VARIABLE)
                    tree.sym.typeAnnotations = tree.sym.typeAnnotations.appendList(recordedTypeAnnotations);
                recordedTypeAnnotations = kind.isField() ? prevTAs : prevTAs.appendList(recordedTypeAnnotations);
            }
        }

        @Override
        public void visitApply(JCMethodInvocation tree) {
            scan(tree.meth);
            scan(tree.typeargs);
            scan(tree.args);
        }

        public void visitAnnotation(JCAnnotation tree) {
            if (tree instanceof JCTypeAnnotation)
                recordedTypeAnnotations = recordedTypeAnnotations.append(((JCTypeAnnotation)tree).attribute_field);
            super.visitAnnotation(tree);
        }
    }

}
