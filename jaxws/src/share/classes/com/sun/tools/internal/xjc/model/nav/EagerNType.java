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

package com.sun.tools.internal.xjc.model.nav;

import java.lang.reflect.Type;

import com.sun.codemodel.internal.JType;
import com.sun.tools.internal.xjc.outline.Aspect;
import com.sun.tools.internal.xjc.outline.Outline;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;

/**
 * @author Kohsuke Kawaguchi
 */
class EagerNType implements NType {
    /*package*/ final Type t;

    public EagerNType(Type type) {
        this.t = type;
        assert t!=null;
    }

    public JType toType(Outline o, Aspect aspect) {
        try {
            return o.getCodeModel().parseType(t.toString());
        } catch (ClassNotFoundException e) {
            throw new NoClassDefFoundError(e.getMessage());
        }
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EagerNType)) return false;

        final EagerNType eagerNType = (EagerNType) o;

        return t.equals(eagerNType.t);
    }

    public boolean isBoxedType() {
        return false;
    }

    public int hashCode() {
        return t.hashCode();
    }

    public String fullName() {
        return Navigator.REFLECTION.getTypeName(t);
    }
}
