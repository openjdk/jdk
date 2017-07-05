/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.rngom.digested;

import com.sun.xml.internal.rngom.ast.builder.Annotations;
import com.sun.xml.internal.rngom.ast.builder.BuildException;
import com.sun.xml.internal.rngom.ast.builder.CommentList;
import com.sun.xml.internal.rngom.ast.builder.Div;
import com.sun.xml.internal.rngom.ast.builder.Grammar;
import com.sun.xml.internal.rngom.ast.builder.Include;
import com.sun.xml.internal.rngom.ast.builder.Scope;
import com.sun.xml.internal.rngom.ast.om.Location;
import com.sun.xml.internal.rngom.ast.om.ParsedElementAnnotation;
import com.sun.xml.internal.rngom.ast.om.ParsedPattern;
import com.sun.xml.internal.rngom.ast.util.LocatorImpl;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
class GrammarBuilderImpl implements Grammar, Div {

    protected final DGrammarPattern grammar;

    protected final Scope parent;

    protected final DSchemaBuilderImpl sb;

    /**
     * Additional top-level element annotations.
     * Can be null.
     */
    private List<Element> additionalElementAnnotations;

    public GrammarBuilderImpl(DGrammarPattern p, Scope parent, DSchemaBuilderImpl sb) {
        this.grammar = p;
        this.parent = parent;
        this.sb = sb;
    }

    public ParsedPattern endGrammar(Location loc, Annotations anno) throws BuildException {
        if(anno!=null)
            grammar.annotation = ((Annotation)anno).getResult();
        if(additionalElementAnnotations!=null) {
            if(grammar.annotation==null)
                grammar.annotation = new DAnnotation();
            grammar.annotation.contents.addAll(additionalElementAnnotations);
        }
        return grammar;
    }

    public void endDiv(Location loc, Annotations anno) throws BuildException {
    }

    public void define(String name, Combine combine, ParsedPattern pattern, Location loc, Annotations anno) throws BuildException {
        if(name==START)
            grammar.start = (DPattern)pattern;
        else {
            // TODO: handle combine
            DDefine d = grammar.getOrAdd(name);
            d.setPattern( (DPattern) pattern );
            if(anno!=null)
                d.annotation = ((Annotation)anno).getResult();
        }
    }

    public void topLevelAnnotation(ParsedElementAnnotation ea) throws BuildException {
        if(additionalElementAnnotations==null)
            additionalElementAnnotations = new ArrayList<Element>();
        additionalElementAnnotations.add(((ElementWrapper)ea).element);
    }

    public void topLevelComment(CommentList comments) throws BuildException {
    }

    public Div makeDiv() {
        return this;
    }

    public Include makeInclude() {
        return new IncludeImpl(grammar,parent,sb);
    }

    public ParsedPattern makeParentRef(String name, Location loc, Annotations anno) throws BuildException {
        return parent.makeRef(name,loc,anno);
    }

    public ParsedPattern makeRef(String name, Location loc, Annotations anno) throws BuildException {
        return DSchemaBuilderImpl.wrap( new DRefPattern(grammar.getOrAdd(name)), (LocatorImpl)loc, (Annotation)anno );
    }
}
