/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.internal.ws.processor.modeler.annotation;

import com.sun.istack.internal.tools.APTTypeVisitor;
import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.type.*;
import com.sun.mirror.util.Types;

import java.util.Collection;

/**
 *
 * @author dkohlert
 */
public class MakeSafeTypeVisitor extends APTTypeVisitor<TypeMirror, Types> implements WebServiceConstants {
    TypeDeclaration collectionDecl;
    TypeDeclaration mapDecl;

    /**
     * Creates a new instance of MakeSafeTypeVisitor
     */
    public MakeSafeTypeVisitor(AnnotationProcessorEnvironment apEnv) {
        collectionDecl = apEnv.getTypeDeclaration(COLLECTION_CLASSNAME);
        mapDecl = apEnv.getTypeDeclaration(MAP_CLASSNAME);
    }

    protected TypeMirror onArrayType(ArrayType type, Types apTypes) {
        return apTypes.getErasure(type);
    }

    protected TypeMirror onPrimitiveType(PrimitiveType type, Types apTypes) {
        return apTypes.getErasure(type);
    }

    protected TypeMirror onClassType(ClassType type, Types apTypes) {
        return processDeclaredType(type, apTypes);
    }

    protected TypeMirror onInterfaceType(InterfaceType type, Types apTypes) {
        return processDeclaredType(type, apTypes);
    }

    private TypeMirror processDeclaredType(DeclaredType type, Types apTypes) {
        if (TypeModeler.isSubtype(type.getDeclaration(), collectionDecl) ||
            TypeModeler.isSubtype(type.getDeclaration(), mapDecl)) {
            Collection<TypeMirror> args = type.getActualTypeArguments();
            TypeMirror[] safeArgs = new TypeMirror[args.size()];
            int i = 0;
            for (TypeMirror arg : args) {
                safeArgs[i++]= apply(arg, apTypes);
            }
            return apTypes.getDeclaredType(type.getDeclaration(), safeArgs);
        }
        return apTypes.getErasure(type);
    }

    protected TypeMirror onTypeVariable(TypeVariable type, Types apTypes) {
        return apTypes.getErasure(type);
    }

    protected TypeMirror onVoidType(VoidType type, Types apTypes) {
        return type;
    }

    protected TypeMirror onWildcard(WildcardType type, Types apTypes) {
        return apTypes.getErasure(type);
    }
}
