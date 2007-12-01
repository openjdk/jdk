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

import com.sun.tools.internal.txw2.model.Leaf;
import com.sun.tools.internal.txw2.model.Ref;
import org.kohsuke.rngom.ast.builder.BuildException;
import org.kohsuke.rngom.ast.builder.Grammar;
import org.kohsuke.rngom.ast.builder.Scope;
import org.kohsuke.rngom.ast.om.ParsedElementAnnotation;
import org.kohsuke.rngom.ast.util.LocatorImpl;

/**
 * @author Kohsuke Kawaguchi
 */
class GrammarImpl extends GrammarSectionImpl
    implements Grammar<Leaf,ParsedElementAnnotation,LocatorImpl,AnnotationsImpl,CommentListImpl> {

    GrammarImpl(Scope<Leaf,ParsedElementAnnotation,LocatorImpl,AnnotationsImpl,CommentListImpl> scope) {
        super(scope,new com.sun.tools.internal.txw2.model.Grammar());
    }

    public Leaf endGrammar(LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        return new Ref(locator,grammar,com.sun.tools.internal.txw2.model.Grammar.START);
    }

    public Leaf makeParentRef(String name, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        return parent.makeRef(name,locator,annotations);
    }

    public Leaf makeRef(String name, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        return new Ref(locator,grammar,name);
    }
}
