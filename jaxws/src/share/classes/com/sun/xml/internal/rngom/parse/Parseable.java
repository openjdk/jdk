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
package com.sun.xml.internal.rngom.parse;

import com.sun.xml.internal.rngom.ast.builder.*;
import com.sun.xml.internal.rngom.ast.om.*;

/**
 * An input that can be turned into a RELAX NG pattern.
 *
 * <p>
 * This is either a RELAX NG schema in the XML format, or a RELAX NG
 * schema in the compact syntax.
 */
public interface Parseable {
    /**
     * Parses this {@link Parseable} object into a RELAX NG pattern.
     *
     * @param sb
     *      The builder of the schema object model. This object
     *      dictates how the actual pattern is constructed.
     *
     * @return
     *      a parsed object. Always returns a non-null valid object.
     */
    <P extends ParsedPattern> P parse(SchemaBuilder<?,P,?,?,?,?> sb) throws BuildException, IllegalSchemaException;

    /**
     * Called from {@link Include} in response to
     * {@link Include#endInclude(Parseable, String, String, Location, Annotations)}
     * to parse the included grammar.
     *
     * @param g
     *      receives the events from the included grammar.
     */
    <P extends ParsedPattern> P parseInclude(String uri, SchemaBuilder<?,P,?,?,?,?> f, IncludedGrammar<P,?,?,?,?> g, String inheritedNs)
        throws BuildException, IllegalSchemaException;

    /**
     * Called from {@link SchemaBuilder} in response to
     * {@link SchemaBuilder#makeExternalRef(Parseable, String, String, Scope, Location, Annotations)}
     * to parse the referenced grammar.
     *
     * @param f
     *      receives the events from the referenced grammar.
     */
    <P extends ParsedPattern> P parseExternal(String uri, SchemaBuilder<?,P,?,?,?,?> f, Scope s, String inheritedNs)
        throws BuildException, IllegalSchemaException;
}
