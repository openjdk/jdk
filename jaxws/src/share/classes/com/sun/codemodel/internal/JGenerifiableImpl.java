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
package com.sun.codemodel.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link JGenerifiable}.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
abstract class JGenerifiableImpl implements JGenerifiable, JDeclaration {

    /** Lazily created list of {@link JTypeVar}s. */
    private List<JTypeVar> typeVariables = null;

    protected abstract JCodeModel owner();

    public void declare( JFormatter f ) {
        if(typeVariables!=null) {
            f.p('<');
            for (int i = 0; i < typeVariables.size(); i++) {
                if(i!=0)    f.p(',');
                f.d(typeVariables.get(i));
            }
            f.p('>');
        }
    }


    public JTypeVar generify(String name) {
        JTypeVar v = new JTypeVar(owner(),name);
        if(typeVariables==null)
            typeVariables = new ArrayList<JTypeVar>(3);
        typeVariables.add(v);
        return v;
    }

    public JTypeVar generify(String name, Class bound) {
        return generify(name,owner().ref(bound));
    }

    public JTypeVar generify(String name, JClass bound) {
        return generify(name).bound(bound);
    }

    public JTypeVar[] typeParams() {
        if(typeVariables==null)
            return JTypeVar.EMPTY_ARRAY;
        else
            return typeVariables.toArray(new JTypeVar[typeVariables.size()]);
    }

}
