/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.model.nav;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import com.sun.codemodel.internal.JClass;
import com.sun.tools.internal.xjc.outline.Aspect;
import com.sun.tools.internal.xjc.outline.Outline;

/**
 * @author Kohsuke Kawaguchi
 */
public class EagerNClass extends EagerNType implements NClass {
    /*package*/ final Class c;

    public EagerNClass(Class type) {
        super(type);
        this.c = type;
    }

    @Override
    public boolean isBoxedType() {
        return boxedTypes.contains(c);
    }

    @Override
    public JClass toType(Outline o, Aspect aspect) {
        return o.getCodeModel().ref(c);
    }

    public boolean isAbstract() {
        return Modifier.isAbstract(c.getModifiers());
    }

    private static final Set<Class> boxedTypes = new HashSet<Class>();

    static {
        boxedTypes.add(Boolean.class);
        boxedTypes.add(Character.class);
        boxedTypes.add(Byte.class);
        boxedTypes.add(Short.class);
        boxedTypes.add(Integer.class);
        boxedTypes.add(Long.class);
        boxedTypes.add(Float.class);
        boxedTypes.add(Double.class);
    }
}
