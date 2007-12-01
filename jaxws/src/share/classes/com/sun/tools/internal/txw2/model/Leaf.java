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
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.tools.internal.txw2.model.prop.Prop;
import com.sun.tools.internal.txw2.model.prop.ValueProp;
import com.sun.xml.internal.txw2.annotation.XmlValue;
import org.kohsuke.rngom.ast.om.ParsedPattern;
import org.xml.sax.Locator;

import java.util.Iterator;
import java.util.Set;

/**
 * {@link Leaf}s form a set (by a cyclic doubly-linked list.)
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Leaf implements ParsedPattern {
    private Leaf next;
    private Leaf prev;

    /**
     * Source location where this leaf was defined.
     */
    public Locator location;

    protected Leaf(Locator location) {
        this.location = location;
        prev = next = this;
    }

    public final Leaf getNext() {
        assert next!=null;
        assert next.prev == this;
        return next;
    }

    public final Leaf getPrev() {
        assert prev!=null;
        assert prev.next == this;
        return prev;
    }

    /**
     * Combines two sets into one set.
     *
     * @return this
     */
    public final Leaf merge(Leaf that) {
        Leaf n1 = this.next;
        Leaf n2 = that.next;

        that.next = n1;
        that.next.prev = that;
        this.next = n2;
        this.next.prev = this;

        return this;
    }

    /**
     * Returns the collection of all the siblings
     * (including itself)
     */
    public final Iterable<Leaf> siblings() {
        return new Iterable<Leaf>() {
            public Iterator<Leaf> iterator() {
                return new CycleIterator(Leaf.this);
            }
        };
    }

    /**
     * Populate the body of the writer class.
     *
     * @param props
     *      captures the generatesd {@link Prop}s to
     */
    abstract void generate(JDefinedClass clazz, NodeSet nset, Set<Prop> props);




    /**
     * Creates a prop of the data value method.
     */
    protected final void createDataMethod(JDefinedClass clazz, JType valueType, NodeSet nset, Set<Prop> props) {
        if(!props.add(new ValueProp(valueType)))
            return;

        JMethod m = clazz.method(JMod.PUBLIC,
            nset.opts.chainMethod? (JType)clazz : nset.codeModel.VOID,
            "_text");
        m.annotate(XmlValue.class);
        m.param(valueType,"value");
    }
}
