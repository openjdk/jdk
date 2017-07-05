/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.codemodel.internal;

import java.util.Iterator;
import java.util.Collections;
import java.util.List;

/**
 * Array class.
 *
 * @author
 *      Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
final class JArrayClass extends JClass {

    // array component type
    private final JType componentType;


    JArrayClass( JCodeModel owner, JType component ) {
        super(owner);
        this.componentType = component;
    }


    public String name() {
        return componentType.name()+"[]";
    }

    public String fullName() {
        return componentType.fullName()+"[]";
    }

    public String binaryName() {
        return componentType.binaryName()+"[]";
    }

    public void generate(JFormatter f) {
        f.g(componentType).p("[]");
    }

    public JPackage _package() {
        return owner().rootPackage();
    }

    public JClass _extends() {
        return owner().ref(Object.class);
    }

    public Iterator<JClass> _implements() {
        return Collections.<JClass>emptyList().iterator();
    }

    public boolean isInterface() {
        return false;
    }

    public boolean isAbstract() {
        return false;
    }

    public JType elementType() {
        return componentType;
    }

    public boolean isArray() {
        return true;
    }


    //
    // Equality is based on value
    //

    public boolean equals(Object obj) {
        if(!(obj instanceof JArrayClass))   return false;

        if( componentType.equals( ((JArrayClass)obj).componentType ) )
            return true;

        return false;
    }

    public int hashCode() {
        return componentType.hashCode();
    }

    protected JClass substituteParams(JTypeVar[] variables, List<JClass> bindings) {
        if( componentType.isPrimitive() )
            return this;

        JClass c = ((JClass)componentType).substituteParams(variables,bindings);
        if(c==componentType)
            return this;

        return new JArrayClass(owner(),c);
    }

}
