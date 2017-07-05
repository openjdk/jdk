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

/**
 * The {@link Node} that maps to the program element.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class WriterNode extends Node {
    /**
     * If this node is the sole child of a pattern block,
     * this field points to its name.
     *
     * <p>
     * When the element names are in conflict, this can be used.
     */
    protected String alternativeName;

    public WriterNode(Locator location, Leaf leaf) {
        super(location, leaf);
    }

    /**
     * Declares the class without its contents.
     *
     * The first step of the code generation.
     */
    abstract void declare(NodeSet nset);

    /**
     * Generates the contents.
     */
    abstract void generate(NodeSet nset);

    /**
     * Prepares for the code generation.
     */
    void prepare(NodeSet nset) {}
}
