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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

/**
 * Represents X&lt;Y>.
 *
 * TODO: consider separating the decl and the use.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
class JNarrowedClass extends JClass {
    /**
     * A generic class with type parameters.
     */
    final JClass basis;
    /**
     * Arguments to those parameters.
     */
    private final List<JClass> args;

    JNarrowedClass(JClass basis, JClass arg) {
        this(basis,Collections.singletonList(arg));
    }

    JNarrowedClass(JClass basis, List<JClass> args) {
        super(basis.owner());
        this.basis = basis;
        assert !(basis instanceof JNarrowedClass);
        this.args = args;
    }

    @Override
    public JClass narrow( JClass clazz ) {
        List<JClass> newArgs = new ArrayList<JClass>(args);
        newArgs.add(clazz);
        return new JNarrowedClass(basis,newArgs);
    }

    @Override
    public JClass narrow( JClass... clazz ) {
        List<JClass> newArgs = new ArrayList<JClass>(args);
        newArgs.addAll(Arrays.asList(clazz));
        return new JNarrowedClass(basis,newArgs);
    }

    public String name() {
        StringBuilder buf = new StringBuilder();
        buf.append(basis.name());
        buf.append('<');
        boolean first = true;
        for (JClass c : args) {
            if(first)
                first = false;
            else
                buf.append(',');
            buf.append(c.name());
        }
        buf.append('>');
        return buf.toString();
    }

    public String fullName() {
        StringBuilder buf = new StringBuilder();
        buf.append(basis.fullName());
        buf.append('<');
        boolean first = true;
        for (JClass c : args) {
            if(first)
                first = false;
            else
                buf.append(',');
            buf.append(c.fullName());
        }
        buf.append('>');
        return buf.toString();
    }

    @Override
    public String binaryName() {
        StringBuilder buf = new StringBuilder();
        buf.append(basis.binaryName());
        buf.append('<');
        boolean first = true;
        for (JClass c : args) {
            if(first)
                first = false;
            else
                buf.append(',');
            buf.append(c.binaryName());
        }
        buf.append('>');
        return buf.toString();
    }

    @Override
    public void generate(JFormatter f) {
        f.t(basis).p('<').g(args).p(JFormatter.CLOSE_TYPE_ARGS);
    }

    @Override
    void printLink(JFormatter f) {
        basis.printLink(f);
        f.p("{@code <}");
        boolean first = true;
        for( JClass c : args ) {
            if(first)
                first = false;
            else
                f.p(',');
            c.printLink(f);
        }
        f.p("{@code >}");
    }

    public JPackage _package() {
        return basis._package();
    }

    public JClass _extends() {
        JClass base = basis._extends();
        if(base==null)  return base;
        return base.substituteParams(basis.typeParams(),args);
    }

    public Iterator<JClass> _implements() {
        return new Iterator<JClass>() {
            private final Iterator<JClass> core = basis._implements();
            public void remove() {
                core.remove();
            }
            public JClass next() {
                return core.next().substituteParams(basis.typeParams(),args);
            }
            public boolean hasNext() {
                return core.hasNext();
            }
        };
    }

    @Override
    public JClass erasure() {
        return basis;
    }

    public boolean isInterface() {
        return basis.isInterface();
    }

    public boolean isAbstract() {
        return basis.isAbstract();
    }

    @Override
    public boolean isArray() {
        return false;
    }


    //
    // Equality is based on value
    //

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof JNarrowedClass))   return false;
        return fullName().equals(((JClass)obj).fullName());
    }

    @Override
    public int hashCode() {
        return fullName().hashCode();
    }

    protected JClass substituteParams(JTypeVar[] variables, List<JClass> bindings) {
        JClass b = basis.substituteParams(variables,bindings);
        boolean different = b!=basis;

        List<JClass> clazz = new ArrayList<JClass>(args.size());
        for( int i=0; i<clazz.size(); i++ ) {
            JClass c = args.get(i).substituteParams(variables,bindings);
            clazz.set(i,c);
            different |= c != args.get(i);
        }

        if(different)
            return new JNarrowedClass(b,clazz);
        else
            return this;
    }

    @Override
    public List<JClass> getTypeParameters() {
        return args;
    }
}
