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

package com.sun.tools.internal.txw2.builder.relaxng;

import com.sun.tools.internal.txw2.model.Define;
import com.sun.tools.internal.txw2.model.Grammar;
import com.sun.tools.internal.txw2.model.Leaf;
import org.kohsuke.rngom.ast.builder.BuildException;
import org.kohsuke.rngom.ast.builder.Div;
import org.kohsuke.rngom.ast.builder.GrammarSection;
import org.kohsuke.rngom.ast.builder.Include;
import org.kohsuke.rngom.ast.builder.Scope;
import org.kohsuke.rngom.ast.om.ParsedElementAnnotation;
import org.kohsuke.rngom.ast.util.LocatorImpl;

/**
 * @author Kohsuke Kawaguchi
 */
abstract class GrammarSectionImpl implements GrammarSection<Leaf,ParsedElementAnnotation,LocatorImpl,AnnotationsImpl,CommentListImpl> {

    protected final Scope<Leaf,ParsedElementAnnotation,LocatorImpl,AnnotationsImpl,CommentListImpl> parent;

    protected final Grammar grammar;

    GrammarSectionImpl(
        Scope<Leaf,ParsedElementAnnotation,LocatorImpl,AnnotationsImpl,CommentListImpl> scope,
        Grammar grammar ) {
        this.parent = scope;
        this.grammar = grammar;
    }

    public void topLevelAnnotation(ParsedElementAnnotation parsedElementAnnotation) throws BuildException {
    }

    public void topLevelComment(CommentListImpl commentList) throws BuildException {
    }

    public Div<Leaf, ParsedElementAnnotation, LocatorImpl, AnnotationsImpl, CommentListImpl> makeDiv() {
        return new DivImpl(parent,grammar);
    }

    public Include<Leaf, ParsedElementAnnotation, LocatorImpl, AnnotationsImpl, CommentListImpl> makeInclude() {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void define(String name, Combine combine, Leaf leaf, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        Define def = grammar.get(name);
        def.location = locator;

        if(combine==null || def.leaf==null) {
            def.leaf = leaf;
        } else {
            def.leaf.merge(leaf);
        }
    }
}
