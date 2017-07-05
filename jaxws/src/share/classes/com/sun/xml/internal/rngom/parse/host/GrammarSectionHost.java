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
package com.sun.xml.internal.rngom.parse.host;

import com.sun.xml.internal.rngom.ast.builder.Annotations;
import com.sun.xml.internal.rngom.ast.builder.BuildException;
import com.sun.xml.internal.rngom.ast.builder.CommentList;
import com.sun.xml.internal.rngom.ast.builder.Div;
import com.sun.xml.internal.rngom.ast.builder.GrammarSection;
import com.sun.xml.internal.rngom.ast.builder.Include;
import com.sun.xml.internal.rngom.ast.om.Location;
import com.sun.xml.internal.rngom.ast.om.ParsedElementAnnotation;
import com.sun.xml.internal.rngom.ast.om.ParsedPattern;

/**
 *
 * @author
 *      Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class GrammarSectionHost extends Base implements GrammarSection {
    private final GrammarSection lhs;
    private final GrammarSection rhs;

    GrammarSectionHost( GrammarSection lhs, GrammarSection rhs ) {
        this.lhs = lhs;
        this.rhs = rhs;
        if(lhs==null || rhs==null)
            throw new IllegalArgumentException();
    }

    public void define(String name, Combine combine, ParsedPattern _pattern,
        Location _loc, Annotations _anno) throws BuildException {
        ParsedPatternHost pattern = (ParsedPatternHost) _pattern;
        LocationHost loc = cast(_loc);
        AnnotationsHost anno = cast(_anno);

        lhs.define(name, combine, pattern.lhs, loc.lhs, anno.lhs);
        rhs.define(name, combine, pattern.rhs, loc.rhs, anno.rhs);
    }

    public Div makeDiv() {
        return new DivHost( lhs.makeDiv(), rhs.makeDiv() );
    }

    public Include makeInclude() {
        Include l = lhs.makeInclude();
        if(l==null) return null;
        return new IncludeHost( l, rhs.makeInclude() );
    }

    public void topLevelAnnotation(ParsedElementAnnotation _ea) throws BuildException {
        ParsedElementAnnotationHost ea = (ParsedElementAnnotationHost) _ea;
        lhs.topLevelAnnotation(ea==null?null:ea.lhs);
        rhs.topLevelAnnotation(ea==null?null:ea.rhs);
    }

    public void topLevelComment(CommentList _comments) throws BuildException {
        CommentListHost comments = (CommentListHost) _comments;

        lhs.topLevelComment(comments==null?null:comments.lhs);
        rhs.topLevelComment(comments==null?null:comments.rhs);
    }
}
