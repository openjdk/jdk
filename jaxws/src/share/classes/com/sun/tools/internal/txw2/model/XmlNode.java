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

import org.xml.sax.Locator;

import javax.xml.namespace.QName;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

/**
 * Either an {@link Element} or {@link Attribute}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class XmlNode extends WriterNode {
    /**
     * Name of the attribute/element.
     *
     * In TXW, we ignore all infinite names.
     * (finite name class will be expanded to a list of {@link XmlNode}s.
     */
    public final QName name;

    protected XmlNode(Locator location, QName name, Leaf leaf) {
        super(location, leaf);
        this.name = name;
    }

    /**
     * Expand all refs and collect all children.
     */
    protected final Set<Leaf> collectChildren() {
        Set<Leaf> result = new HashSet<Leaf>();

        Stack<Node> work = new Stack<Node>();
        work.push(this);

        while(!work.isEmpty()) {
            for( Leaf l : work.pop() ) {
                if( l instanceof Ref ) {
                    work.push( ((Ref)l).def );
                } else {
                    result.add(l);
                }
            }
        }

        return result;
    }
}
