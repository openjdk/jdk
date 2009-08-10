/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.apt.mirror.declaration;


import java.util.LinkedHashMap;
import java.util.Map;

import com.sun.mirror.declaration.*;
import com.sun.mirror.type.AnnotationType;
import com.sun.mirror.util.SourcePosition;
import com.sun.tools.apt.mirror.AptEnv;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;


/**
 * Implementation of AnnotationMirror
 */
@SuppressWarnings("deprecation")
public class AnnotationMirrorImpl implements AnnotationMirror {

    protected final AptEnv env;
    protected final Attribute.Compound anno;
    protected final Declaration decl;


    AnnotationMirrorImpl(AptEnv env, Attribute.Compound anno, Declaration decl) {
        this.env = env;
        this.anno = anno;
        this.decl = decl;
    }


    /**
     * Returns a string representation of this annotation.
     * String is of one of the forms:
     *     @com.example.foo(name1=val1, name2=val2)
     *     @com.example.foo(val)
     *     @com.example.foo
     * Omit parens for marker annotations, and omit "value=" when allowed.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder("@");
        Constants.Formatter fmtr = Constants.getFormatter(sb);

        fmtr.append(anno.type.tsym);

        int len = anno.values.length();
        if (len > 0) {          // omit parens for marker annotations
            sb.append('(');
            boolean first = true;
            for (Pair<MethodSymbol, Attribute> val : anno.values) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;

                Name name = val.fst.name;
                if (len > 1 || name != env.names.value) {
                    fmtr.append(name);
                    sb.append('=');
                }
                sb.append(new AnnotationValueImpl(env, val.snd, this));
            }
            sb.append(')');
        }
        return fmtr.toString();
    }

    /**
     * {@inheritDoc}
     */
    public AnnotationType getAnnotationType() {
        return (AnnotationType) env.typeMaker.getType(anno.type);
    }

    /**
     * {@inheritDoc}
     */
    public Map<AnnotationTypeElementDeclaration, AnnotationValue>
                                                        getElementValues() {
        Map<AnnotationTypeElementDeclaration, AnnotationValue> res =
            new LinkedHashMap<AnnotationTypeElementDeclaration,
                                                   AnnotationValue>(); // whew!
        for (Pair<MethodSymbol, Attribute> val : anno.values) {
            res.put(getElement(val.fst),
                    new AnnotationValueImpl(env, val.snd, this));
        }
        return res;
    }

    public SourcePosition getPosition() {
        // Return position of the declaration on which this annotation
        // appears.
        return (decl == null) ? null : decl.getPosition();

    }

    public Declaration getDeclaration() {
        return this.decl;
    }

    /**
     * Returns the annotation type element for a symbol.
     */
    private AnnotationTypeElementDeclaration getElement(MethodSymbol m) {
        return (AnnotationTypeElementDeclaration)
                    env.declMaker.getExecutableDeclaration(m);
    }
}
