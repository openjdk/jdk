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
import com.sun.xml.internal.rngom.ast.om.Location;
import com.sun.xml.internal.rngom.ast.om.ParsedElementAnnotation;

/**
 *
 * @author
 *      Kohsuke Kawaguchi (kk@kohsuke.org)
 */
class AnnotationsHost extends Base implements Annotations {
    final Annotations lhs;
    final Annotations rhs;

    AnnotationsHost( Annotations lhs, Annotations rhs ) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public void addAttribute(String ns, String localName, String prefix,
        String value, Location _loc) throws BuildException {
        LocationHost loc = cast(_loc);
        lhs.addAttribute(ns, localName, prefix, value, loc.lhs);
        rhs.addAttribute(ns, localName, prefix, value, loc.rhs);
    }

    public void addComment(CommentList _comments) throws BuildException {
        CommentListHost comments = (CommentListHost) _comments;
        lhs.addComment(comments==null?null:comments.lhs);
        rhs.addComment(comments==null?null:comments.rhs);
    }

    public void addElement(ParsedElementAnnotation _ea) throws BuildException {
        ParsedElementAnnotationHost ea = (ParsedElementAnnotationHost) _ea;
        lhs.addElement(ea.lhs);
        rhs.addElement(ea.rhs);
    }

    public void addLeadingComment(CommentList _comments) throws BuildException {
        CommentListHost comments = (CommentListHost) _comments;
        lhs.addLeadingComment(comments.lhs);
        rhs.addLeadingComment(comments.rhs);
    }
}
