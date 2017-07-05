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

import com.sun.xml.internal.rngom.ast.builder.BuildException;
import com.sun.xml.internal.rngom.ast.builder.SchemaBuilder;
import com.sun.xml.internal.rngom.ast.util.CheckingSchemaBuilder;
import com.sun.xml.internal.rngom.parse.Parseable;
import com.sun.xml.internal.rngom.parse.compact.CompactParseable;
import com.sun.xml.internal.rngom.parse.xml.SAXParseable;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class Main {
    public static void main(String[] args) throws Exception {
        Parseable p;

        ErrorHandler eh = new DefaultHandler() {
            public void error(SAXParseException e) throws SAXException {
                throw e;
            }
        };

        // the error handler passed to Parseable will receive parsing errors.
        if(args[0].endsWith(".rng"))
            p = new SAXParseable(new InputSource(args[0]),eh);
        else
            p = new CompactParseable(new InputSource(args[0]),eh);

        // the error handler passed to CheckingSchemaBuilder will receive additional
        // errors found during the RELAX NG restrictions check.
        // typically you'd want to pass in the same error handler,
        // as there's really no distinction between those two kinds of errors.
        SchemaBuilder sb = new CheckingSchemaBuilder(new DSchemaBuilderImpl(),eh);
        try {
            // run the parser
            p.parse(sb);
        } catch( BuildException e ) {
            // I found that Crimson doesn't show the proper stack trace
            // when a RuntimeException happens inside a SchemaBuilder.
            // the following code shows the actual exception that happened.
            if( e.getCause() instanceof SAXException ) {
                SAXException se = (SAXException) e.getCause();
                if(se.getException()!=null)
                    se.getException().printStackTrace();
            }
            throw e;
        }
    }
}
