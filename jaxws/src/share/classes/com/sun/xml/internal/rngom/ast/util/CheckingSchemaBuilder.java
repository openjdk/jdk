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
package com.sun.xml.internal.rngom.ast.util;

import com.sun.xml.internal.rngom.ast.builder.BuildException;
import com.sun.xml.internal.rngom.ast.builder.SchemaBuilder;
import com.sun.xml.internal.rngom.ast.om.ParsedPattern;
import com.sun.xml.internal.rngom.binary.SchemaBuilderImpl;
import com.sun.xml.internal.rngom.binary.SchemaPatternBuilder;
import com.sun.xml.internal.rngom.parse.IllegalSchemaException;
import com.sun.xml.internal.rngom.parse.host.ParsedPatternHost;
import com.sun.xml.internal.rngom.parse.host.SchemaBuilderHost;
import org.relaxng.datatype.DatatypeLibraryFactory;
import org.xml.sax.ErrorHandler;

/**
 * Wraps a {@link SchemaBuilder} and does all the semantic checks
 * required by the RELAX NG spec.
 *
 * <h2>Usage</h2>
 * <p>
 * Whereas you normally write it as follows:
 * <pre>
 * YourParsedPattern r = (YourParsedPattern)parseable.parse(sb);
 * </pre>
 * write this as follows:
 * <pre>
 * YourParsedPattern r = (YourParsedPattern)parseable.parse(new CheckingSchemaBuilder(sb,eh));
 * </pre>
 *
 * <p>
 * The checking is done by using the <tt>rngom.binary</tt> package, so if you are using
 * that package for parsing schemas, then there's no need to use this.
 *
 * @author
 *      Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class CheckingSchemaBuilder extends SchemaBuilderHost {
    /**
     *
     * @param sb
     *      Your {@link SchemaBuilder} that parses RELAX NG schemas.
     * @param eh
     *      All the errors found will be sent to this handler.
     */
    public CheckingSchemaBuilder( SchemaBuilder sb, ErrorHandler eh ) {
        super(new SchemaBuilderImpl(eh),sb);
    }
    public CheckingSchemaBuilder( SchemaBuilder sb, ErrorHandler eh, DatatypeLibraryFactory dlf ) {
        super(new SchemaBuilderImpl(eh,dlf,new SchemaPatternBuilder()),sb);
    }

    public ParsedPattern expandPattern(ParsedPattern p)
        throws BuildException, IllegalSchemaException {

        // just return the result from the user-given SchemaBuilder
        ParsedPatternHost r = (ParsedPatternHost)super.expandPattern(p);
        return r.rhs;
    }
}
