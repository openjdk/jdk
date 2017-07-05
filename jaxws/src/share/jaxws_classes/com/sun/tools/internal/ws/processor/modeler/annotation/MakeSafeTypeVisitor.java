/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.processor.modeler.annotation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;
import java.util.Collection;
import java.util.Map;

/**
 *
 * @author dkohlert
 */
public class MakeSafeTypeVisitor extends SimpleTypeVisitor6<TypeMirror, Types> {

    TypeElement collectionType;
    TypeElement mapType;

    /**
     * Creates a new instance of MakeSafeTypeVisitor
     */
    public MakeSafeTypeVisitor(ProcessingEnvironment processingEnvironment) {
        collectionType = processingEnvironment.getElementUtils().getTypeElement(Collection.class.getName());
        mapType = processingEnvironment.getElementUtils().getTypeElement(Map.class.getName());
    }

    @Override
    public TypeMirror visitDeclared(DeclaredType t, Types types) {
        if (TypeModeler.isSubElement((TypeElement) t.asElement(), collectionType)
                || TypeModeler.isSubElement((TypeElement) t.asElement(), mapType)) {
            Collection<? extends TypeMirror> args = t.getTypeArguments();
            TypeMirror[] safeArgs = new TypeMirror[args.size()];
            int i = 0;
            for (TypeMirror arg : args) {
                safeArgs[i++] = visit(arg, types);
            }
            return types.getDeclaredType((TypeElement) t.asElement(), safeArgs);
        }
        return types.erasure(t);
    }

    @Override
    public TypeMirror visitNoType(NoType type, Types types) {
        return type;
    }
    @Override
    protected TypeMirror defaultAction(TypeMirror e, Types types) {
        return types.erasure(e);
    }
}
