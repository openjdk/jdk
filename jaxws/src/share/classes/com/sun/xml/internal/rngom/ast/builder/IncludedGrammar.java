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
package com.sun.xml.internal.rngom.ast.builder;

import com.sun.xml.internal.rngom.ast.om.Location;
import com.sun.xml.internal.rngom.ast.om.ParsedPattern;
import com.sun.xml.internal.rngom.ast.om.ParsedElementAnnotation;
import com.sun.xml.internal.rngom.parse.Parseable;

/**
 * {@link Scope} for &lt;grammar> element of the &lt;include>d grammar.
 * <p>
 * This object builds &lt;define>s in the included grammar that
 * override the definitions in the original grammar.
 */
public interface IncludedGrammar<
    P extends ParsedPattern,
    E extends ParsedElementAnnotation,
    L extends Location,
    A extends Annotations<E,L,CL>,
    CL extends CommentList<L>> extends GrammarSection<P,E,L,A,CL>, Scope<P,E,L,A,CL> {

    /**
     *
     * @return
     *      technically, an included gramamr does not produce a pattern,
     *      but this allows {@link Parseable#parseInclude(String, SchemaBuilder, IncludedGrammar, String)}
     *      to return the result from {@link IncludedGrammar} nicely.
     *
     *      <p>
     *      The value returned from this method will be returned from the abovementioned method.
     */
    P endIncludedGrammar(L loc, A anno) throws BuildException;
}
