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

import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JType;
import com.sun.tools.internal.txw2.model.Attribute;
import com.sun.tools.internal.txw2.model.Data;
import com.sun.tools.internal.txw2.model.Element;
import com.sun.tools.internal.txw2.model.Empty;
import com.sun.tools.internal.txw2.model.Leaf;
import com.sun.tools.internal.txw2.model.List;
import com.sun.tools.internal.txw2.model.Value;
import org.kohsuke.rngom.ast.builder.BuildException;
import org.kohsuke.rngom.ast.builder.DataPatternBuilder;
import org.kohsuke.rngom.ast.builder.ElementAnnotationBuilder;
import org.kohsuke.rngom.ast.builder.Grammar;
import org.kohsuke.rngom.ast.builder.NameClassBuilder;
import org.kohsuke.rngom.ast.builder.SchemaBuilder;
import org.kohsuke.rngom.ast.builder.Scope;
import org.kohsuke.rngom.ast.om.ParsedElementAnnotation;
import org.kohsuke.rngom.ast.util.LocatorImpl;
import org.kohsuke.rngom.nc.NameClass;
import org.kohsuke.rngom.nc.NameClassBuilderImpl;
import org.kohsuke.rngom.parse.Context;
import org.kohsuke.rngom.parse.IllegalSchemaException;
import org.kohsuke.rngom.parse.Parseable;

import javax.xml.namespace.QName;

/**
 * Builds a model from a RELAX NG grammar.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SchemaBuilderImpl implements SchemaBuilder<NameClass,Leaf,ParsedElementAnnotation,LocatorImpl,AnnotationsImpl,CommentListImpl> {
    private final NameClassBuilderImpl ncb = new NameClassBuilderImpl();
    private final JClass string;
    private final DatatypeFactory dtf;

    public SchemaBuilderImpl(JCodeModel codeModel) {
        string = codeModel.ref(String.class);
        dtf = new DatatypeFactory(codeModel);
    }


    public Leaf expandPattern(Leaf leaf) throws BuildException {
        return leaf;
    }



    public NameClassBuilder getNameClassBuilder() throws BuildException {
        return ncb;
    }

    private Leaf merge(java.util.List<Leaf> leaves) {
        for( int i=1; i<leaves.size(); i++ )
            leaves.get(0).merge(leaves.get(i));
        return leaves.get(0);
    }

    public Leaf makeChoice(java.util.List<Leaf> leaves, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        return merge(leaves);
    }

    public Leaf makeInterleave(java.util.List<Leaf> leaves, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        return merge(leaves);
    }

    public Leaf makeGroup(java.util.List<Leaf> leaves, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        return merge(leaves);
    }

    public Leaf makeOneOrMore(Leaf leaf, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        return leaf;
    }

    public Leaf makeZeroOrMore(Leaf leaf, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        return leaf.merge(new Empty(locator));
    }

    public Leaf makeOptional(Leaf leaf, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        return leaf.merge(new Empty(locator));
    }

    public Leaf makeList(Leaf leaf, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        return new List(locator,leaf);
    }

    public Leaf makeMixed(Leaf leaf, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        return leaf.merge(new Data(locator,string));
    }

    public Leaf makeEmpty(LocatorImpl locator, AnnotationsImpl annotations) {
        return new Empty(locator);
    }

    public Leaf makeNotAllowed(LocatorImpl locator, AnnotationsImpl annotations) {
        // technically this is incorrect, but we won't be
        // able to handle <notAllowed/> correctly anyway.
        return new Empty(locator);
    }

    public Leaf makeText(LocatorImpl locator, AnnotationsImpl annotations) {
        return new Data(locator,string);
    }

    public Leaf makeAttribute(NameClass nameClass, Leaf leaf, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        Leaf r = null;
        for( QName n : nameClass.listNames() ) {
            Leaf l = new Attribute(locator,n,leaf);
            if(r!=null)     r = r.merge(l);
            else            r = l;
        }
        if(r==null)     return new Empty(locator);
        return r;
    }

    public Leaf makeElement(NameClass nameClass, Leaf leaf, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        Leaf r = null;
        for( QName n : nameClass.listNames() ) {
            Leaf l = new Element(locator,n,leaf);
            if(r!=null)     r = r.merge(l);
            else            r = l;
        }
        if(r==null)     return new Empty(locator);
        return r;
    }

    public DataPatternBuilder makeDataPatternBuilder(String datatypeLibrary, String type, LocatorImpl locator) throws BuildException {
        return new DataPatternBuilderImpl(getType(datatypeLibrary, type));
    }

    private JType getType(String datatypeLibrary, String type) {
        JType t = dtf.getType(datatypeLibrary,type);
        if(t==null) t = string;
        return t;
    }

    public Leaf makeValue(String datatypeLibrary, String type, String value, Context c, String ns, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException {
        return new Value(locator,getType(datatypeLibrary, type),value);
    }

    public Grammar<Leaf,ParsedElementAnnotation,LocatorImpl,AnnotationsImpl,CommentListImpl> makeGrammar(Scope<Leaf,ParsedElementAnnotation,LocatorImpl,AnnotationsImpl,CommentListImpl> scope) {
        return new GrammarImpl(scope);
    }

    public Leaf annotate(Leaf leaf, AnnotationsImpl annotations) throws BuildException {
        return leaf;
    }

    public Leaf annotateAfter(Leaf leaf, ParsedElementAnnotation parsedElementAnnotation) throws BuildException {
        return leaf;
    }

    public Leaf makeErrorPattern() {
        return new Empty(null);
    }

    public boolean usesComments() {
        return false;
    }

    public Leaf makeExternalRef(Parseable current, String uri, String ns, Scope<Leaf,ParsedElementAnnotation,LocatorImpl,AnnotationsImpl,CommentListImpl> scope, LocatorImpl locator, AnnotationsImpl annotations) throws BuildException, IllegalSchemaException {
        // I'm not too sure if this is correct
        return current.parseExternal(uri, this, scope, ns );
    }

    public LocatorImpl makeLocation(String systemId, int lineNumber, int columnNumber) {
        return new LocatorImpl(systemId,lineNumber,columnNumber);
    }

    public AnnotationsImpl makeAnnotations(CommentListImpl commentList, Context context) {
        return new AnnotationsImpl();
    }

    public ElementAnnotationBuilder<Leaf, ParsedElementAnnotation, LocatorImpl, AnnotationsImpl, CommentListImpl> makeElementAnnotationBuilder(String ns, String localName, String prefix, LocatorImpl locator, CommentListImpl commentList, Context context) {
        return new ElementAnnotationBuilderImpl();
    }

    public CommentListImpl makeCommentList() {
        return null;
    }

    public Leaf commentAfter(Leaf leaf, CommentListImpl commentList) throws BuildException {
        return leaf;
    }
}
