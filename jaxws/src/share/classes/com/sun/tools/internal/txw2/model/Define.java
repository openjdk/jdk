/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.txw2.model;

import com.sun.codemodel.JDefinedClass;
import com.sun.tools.internal.txw2.model.prop.Prop;
import com.sun.xml.internal.txw2.TypedXmlWriter;

import java.util.HashSet;
import java.util.Set;


/**
 * A named pattern.
 *
 * @author Kohsuke Kawaguchi
 */
public class Define extends WriterNode {
    public final Grammar scope;
    public final String name;

    JDefinedClass clazz;

    public Define(Grammar scope, String name) {
        super(null,null);
        if(scope==null)     scope = (Grammar)this;  // hack for start pattern
        this.scope = scope;
        this.name = name;
        assert name!=null;
    }

    /**
     * Returns true if this define only contains
     * one child (and thus considered inlinable.)
     *
     * A pattern definition is also inlineable if
     * it's the start of the grammar (because "start" isn't a meaningful name)
     */
    public boolean isInline() {
        return hasOneChild() || name==Grammar.START;
    }

    void declare(NodeSet nset) {
        if(isInline())  return;

        clazz = nset.createClass(name);
        clazz._implements(TypedXmlWriter.class);
    }

    void generate(NodeSet nset) {
        if(clazz==null)     return;

        HashSet<Prop> props = new HashSet<Prop>();
        for( Leaf l : this )
            l.generate(clazz,nset,props);
    }

    void generate(JDefinedClass clazz, NodeSet nset, Set<Prop> props) {
        if(isInline()) {
            for( Leaf l : this )
                l.generate(clazz,nset, props);
        } else {
            assert this.clazz!=null;
            clazz._implements(this.clazz);
        }
    }

    void prepare(NodeSet nset) {
        if(isInline() && leaf instanceof WriterNode && !name.equals(Grammar.START))
            ((WriterNode)leaf).alternativeName = name;
    }

    public String toString() {
        return "Define "+name;
    }
}
