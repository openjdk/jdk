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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Special class object that represents the type of "null".
 *
 * <p>
 * Use this class with care.
 *
 * @author
 *      Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class JNullType extends JClass {

    JNullType(JCodeModel _owner) {
        super(_owner);
    }

    public String name() { return "null"; }
    public String fullName() { return "null"; }

    public JPackage _package() { return owner()._package(""); }

    public JClass _extends() { return null; }

    public Iterator<JClass> _implements() {
        return Collections.<JClass>emptyList().iterator();
    }

    public boolean isInterface() { return false; }
    public boolean isAbstract() { return false; }

    protected JClass substituteParams(JTypeVar[] variables, List<JClass> bindings) {
        return this;
    }
}
