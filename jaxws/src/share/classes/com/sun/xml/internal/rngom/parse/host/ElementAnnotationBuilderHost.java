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

import com.sun.xml.internal.rngom.ast.builder.BuildException;
import com.sun.xml.internal.rngom.ast.builder.CommentList;
import com.sun.xml.internal.rngom.ast.builder.ElementAnnotationBuilder;
import com.sun.xml.internal.rngom.ast.om.Location;
import com.sun.xml.internal.rngom.ast.om.ParsedElementAnnotation;

/**
 *
 * @author
 *      Kohsuke Kawaguchi (kk@kohsuke.org)
 */
final class ElementAnnotationBuilderHost extends AnnotationsHost implements ElementAnnotationBuilder {
    final ElementAnnotationBuilder lhs;
    final ElementAnnotationBuilder rhs;

    ElementAnnotationBuilderHost( ElementAnnotationBuilder lhs, ElementAnnotationBuilder rhs ) {
        super(lhs,rhs);
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public void addText(String value, Location _loc, CommentList _comments) throws BuildException {
        LocationHost loc = cast(_loc);
        CommentListHost comments = (CommentListHost) _comments;

        lhs.addText( value, loc.lhs, comments==null?null:comments.lhs );
        rhs.addText( value, loc.rhs, comments==null?null:comments.rhs );
    }

    public ParsedElementAnnotation makeElementAnnotation() throws BuildException {
        return new ParsedElementAnnotationHost(
            lhs.makeElementAnnotation(),
            rhs.makeElementAnnotation() );
    }
}
