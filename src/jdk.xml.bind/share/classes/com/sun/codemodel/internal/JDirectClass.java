/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;

/**
 * A special {@link JClass} that represents an unknown class (except its name.)
 *
 * @author Kohsuke Kawaguchi
 * @see JCodeModel#directClass(String)
 */
final class JDirectClass extends JClass {

    private final String fullName;

    public JDirectClass(JCodeModel _owner,String fullName) {
        super(_owner);
        this.fullName = fullName;
    }

    public String name() {
        int i = fullName.lastIndexOf('.');
        if(i>=0)    return fullName.substring(i+1);
        return fullName;
    }

    public String fullName() {
        return fullName;
    }

    public JPackage _package() {
        int i = fullName.lastIndexOf('.');
        if(i>=0)    return owner()._package(fullName.substring(0,i));
        else        return owner().rootPackage();
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

    protected JClass substituteParams(JTypeVar[] variables, List<JClass> bindings) {
        return this;
    }
}
