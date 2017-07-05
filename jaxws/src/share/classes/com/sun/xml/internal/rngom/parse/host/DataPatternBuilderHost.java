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
import com.sun.xml.internal.rngom.ast.builder.DataPatternBuilder;
import com.sun.xml.internal.rngom.ast.om.Location;
import com.sun.xml.internal.rngom.ast.om.ParsedElementAnnotation;
import com.sun.xml.internal.rngom.ast.om.ParsedPattern;
import com.sun.xml.internal.rngom.parse.Context;

/**
 *
 * @author
 *      Kohsuke Kawaguchi (kk@kohsuke.org)
 */
final class DataPatternBuilderHost extends Base implements DataPatternBuilder {
    final DataPatternBuilder lhs;
    final DataPatternBuilder rhs;

    DataPatternBuilderHost( DataPatternBuilder lhs, DataPatternBuilder rhs ) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public void addParam(String name, String value, Context context, String ns, Location _loc, Annotations _anno) throws BuildException {
        LocationHost loc = cast(_loc);
        AnnotationsHost anno = cast(_anno);

        lhs.addParam( name, value, context, ns, loc.lhs, anno.lhs );
        rhs.addParam( name, value, context, ns, loc.rhs, anno.rhs );
    }

    public void annotation(ParsedElementAnnotation _ea) {
        ParsedElementAnnotationHost ea = (ParsedElementAnnotationHost) _ea;

        lhs.annotation(ea.lhs);
        rhs.annotation(ea.rhs);
    }

    public ParsedPattern makePattern(Location _loc, Annotations _anno) throws BuildException {
        LocationHost loc = cast(_loc);
        AnnotationsHost anno = cast(_anno);

        return new ParsedPatternHost(
            lhs.makePattern( loc.lhs, anno.lhs ),
            rhs.makePattern( loc.rhs, anno.rhs ));
    }

    public ParsedPattern makePattern(ParsedPattern _except, Location _loc, Annotations _anno) throws BuildException {
        ParsedPatternHost except = (ParsedPatternHost) _except;
        LocationHost loc = cast(_loc);
        AnnotationsHost anno = cast(_anno);

        return new ParsedPatternHost(
            lhs.makePattern(except.lhs, loc.lhs, anno.lhs),
            rhs.makePattern(except.rhs, loc.rhs, anno.rhs));
    }
}
