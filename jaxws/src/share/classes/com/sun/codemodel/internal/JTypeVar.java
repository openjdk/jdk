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

import java.util.Iterator;
import java.util.List;

/**
 * Type variable used to declare generics.
 *
 * @see JGenerifiable
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class JTypeVar extends JClass implements JDeclaration {

    private final String name;

    private JClass bound;

    JTypeVar(JCodeModel owner, String _name) {
        super(owner);
        this.name = _name;
    }

    public String name() {
        return name;
    }

    public String fullName() {
        return name;
    }

    public JPackage _package() {
        return null;
    }

    /**
     * Adds a bound to this variable.
     *
     * @return  this
     */
    public JTypeVar bound( JClass c ) {
        if(bound!=null)
            throw new IllegalArgumentException("type variable has an existing class bound "+bound);
        bound = c;
        return this;
    }

    /**
     * Returns the class bound of this variable.
     *
     * <p>
     * If no bound is given, this method returns {@link Object}.
     */
    public JClass _extends() {
        if(bound!=null)
            return bound;
        else
            return owner().ref(Object.class);
    }

    /**
     * Returns the interface bounds of this variable.
     */
    public Iterator<JClass> _implements() {
        return bound._implements();
    }

    public boolean isInterface() {
        return false;
    }

    public boolean isAbstract() {
        return false;
    }

    /**
     * Prints out the declaration of the variable.
     */
    public void declare(JFormatter f) {
        f.id(name);
        if(bound!=null)
            f.p("extends").g(bound);
    }


    protected JClass substituteParams(JTypeVar[] variables, List<JClass> bindings) {
        for(int i=0;i<variables.length;i++)
            if(variables[i]==this)
                return bindings.get(i);
        return this;
    }

    public void generate(JFormatter f) {
        f.id(name);
    }
}
