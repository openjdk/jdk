/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
/*
 * Copyright (C) 2004-2015
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sun.xml.internal.rngom.ast.builder;

import com.sun.xml.internal.rngom.ast.om.Location;
import com.sun.xml.internal.rngom.ast.om.ParsedElementAnnotation;
import com.sun.xml.internal.rngom.ast.om.ParsedNameClass;
import com.sun.xml.internal.rngom.ast.om.ParsedPattern;
import com.sun.xml.internal.rngom.parse.*;
import com.sun.xml.internal.rngom.parse.IllegalSchemaException;
import com.sun.xml.internal.rngom.parse.Parseable;

import java.util.List;

// TODO: define combine error check should be done by the parser.
public interface SchemaBuilder<
    N extends ParsedNameClass,
    P extends ParsedPattern,
    E extends ParsedElementAnnotation,
    L extends Location,
    A extends Annotations<E,L,CL>,
    CL extends CommentList<L>> {

    /**
     * Returns the {@link NameClassBuilder}, which is used to build name
     * classes for this {@link SchemaBuilder}. The
     * {@link com.sun.xml.internal.rngom.nc.NameClass}es that are built will then be
     * fed into this {@link SchemaBuilder}to further build RELAX NG patterns.
     *
     * @return always return a non-null valid object. This method can (and
     *         probably should) always return the same object.
     */
    NameClassBuilder<N,E,L,A,CL> getNameClassBuilder() throws BuildException;

    P makeChoice(List<P> patterns, L loc, A anno) throws BuildException;

    P makeInterleave(List<P> patterns, L loc, A anno) throws BuildException;

    P makeGroup(List<P> patterns, L loc, A anno) throws BuildException;

    P makeOneOrMore(P p, L loc, A anno) throws BuildException;

    P makeZeroOrMore(P p, L loc, A anno) throws BuildException;

    P makeOptional(P p, L loc, A anno) throws BuildException;

    P makeList(P p, L loc, A anno) throws BuildException;

    P makeMixed(P p, L loc, A anno) throws BuildException;

    P makeEmpty(L loc, A anno);

    P makeNotAllowed(L loc, A anno);

    P makeText(L loc, A anno);

    P makeAttribute(N nc, P p, L loc, A anno) throws BuildException;

    P makeElement(N nc, P p, L loc, A anno) throws BuildException;

    DataPatternBuilder makeDataPatternBuilder(String datatypeLibrary, String type, L loc) throws BuildException;

    P makeValue(String datatypeLibrary, String type, String value,
            Context c, String ns, L loc, A anno) throws BuildException;

    /**
     *
     * @param parent
     *      The parent scope. null if there's no parent scope.
     *      For example, if the complete document looks like the following:
     *      <pre>{@code
     *      <grammar>
     *        <start><element name="root"><empty/></element></start>
     *      </grammar>
     *      }</pre>
     *      Then when the outer-most {@link Grammar} is created, it will
     *      receive the {@code null} parent.
     */
    Grammar<P,E,L,A,CL> makeGrammar(Scope<P,E,L,A,CL> parent);

    /**
     * Called when annotation is found right inside a pattern
     *
     * such as,
     *
     * <pre>{@code
     * <element name="foo">     <!-- this becomes 'P' -->
     *   <foreign:annotation /> <!-- this becomes 'A' -->
     *   ...
     * </element>
     * }</pre>
     */
    P annotate(P p, A anno) throws BuildException;

    /**
     * Called when element annotation is found after a pattern.
     *
     * such as,
     *
     * <pre>{@code
     * <element name="foo">
     *   <empty />              <!-- this becomes 'P' -->
     *   <foreign:annotation /> <!-- this becomes 'E' -->
     * </element>
     * }</pre>
     */
    P annotateAfter(P p, E e) throws BuildException;

    P commentAfter(P p, CL comments) throws BuildException;

    /**
     *
     * @param current
     *      Current grammar that we are parsing. This is what contains
     *      externalRef.
     * @param scope
     *      The parent scope. null if there's no parent scope.
     *      See {@link #makeGrammar(Scope)} for more details about
     *      when this parameter can be null.
     */
    P makeExternalRef(Parseable current, String uri, String ns, Scope<P,E,L,A,CL> scope,
        L loc, A anno) throws BuildException, IllegalSchemaException;

    L makeLocation(String systemId, int lineNumber, int columnNumber);

    /**
     * Creates {@link Annotations} object to parse annotations on patterns.
     *
     * @return
     *      must be non-null.
     */
    A makeAnnotations(CL comments, Context context);

    ElementAnnotationBuilder<P,E,L,A,CL> makeElementAnnotationBuilder(String ns,
        String localName, String prefix, L loc, CL comments,
        Context context);

    CL makeCommentList();

    P makeErrorPattern();

    /**
     * If this {@link SchemaBuilder}is interested in actually parsing
     * comments, this method returns true.
     * <p>
     * Returning false allows the schema parser to speed up the processing by
     * skiping comment-related handlings.
     */
    boolean usesComments();

    /**
     * Called after all the parsing is done.
     *
     * <p>
     * This hook typically allows as {@link SchemaBuilder} to expand
     * notAllowed (if it's following the simplification as in the spec.)
     */
    P expandPattern( P p ) throws BuildException, IllegalSchemaException;
}
