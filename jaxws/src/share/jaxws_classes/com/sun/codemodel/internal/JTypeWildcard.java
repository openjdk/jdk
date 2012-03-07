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
import java.util.List;

/**
 * Represents a wildcard type like "? extends Foo".
 *
 * <p>
 * Instances of this class can be obtained from {@link JClass#wildcard()}
 *
 * TODO: extend this to cover "? super Integer".
 *
 * <p>
 * Our modeling of types are starting to look really ugly.
 * ideally it should have been done somewhat like APT,
 * but it's too late now.
 *
 * @author Kohsuke Kawaguchi
 */
final class JTypeWildcard extends JClass {

    private final JClass bound;

    JTypeWildcard(JClass bound) {
        super(bound.owner());
        this.bound = bound;
    }

    public String name() {
        return "? extends "+bound.name();
    }

    public String fullName() {
        return "? extends "+bound.fullName();
    }

    public JPackage _package() {
        return null;
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

    protected JClass substituteParams(JTypeVar[] variables, List<JClass> bindings) {
        JClass nb = bound.substituteParams(variables,bindings);
        if(nb==bound)
            return this;
        else
            return new JTypeWildcard(nb);
    }

    public void generate(JFormatter f) {
        if(bound._extends()==null)
            f.p("?");   // instead of "? extends Object"
        else
            f.p("? extends").g(bound);
    }
}
