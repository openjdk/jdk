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

package com.sun.tools.internal.txw2;

import com.sun.codemodel.writer.FileCodeWriter;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.kohsuke.rngom.parse.compact.CompactParseable;
import org.kohsuke.rngom.parse.xml.SAXParseable;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

/**
 * Ant task interface for txw compiler.
 *
 * @author ryan_shoemaker@dev.java.net
 */
public class TxwTask extends org.apache.tools.ant.Task {

    // txw options - reuse command line options from the main driver
    private final TxwOptions options = new TxwOptions();

    // schema file
    private File schemaFile;

    // syntax style of RELAX NG source schema - "xml" or "compact"
    private static enum Style {
        COMPACT, XML, XMLSCHEMA, AUTO_DETECT
    }
    private Style style = Style.AUTO_DETECT;

    public TxwTask() {
        // default package
        options._package = options.codeModel.rootPackage();

        // default codewriter
        try {
            options.codeWriter = new FileCodeWriter(new File("."));
        } catch (IOException e) {
            throw new BuildException(e);
        }
    }

    /**
     * Parse @package
     *
     * @param pkg name of the package to generate the java classes into
     */
    public void setPackage( String pkg ) {
        options._package = options.codeModel._package( pkg );
    }

    /**
     * Parse @syntax
     *
     * @param style either "compact" for RELAX NG compact syntax or "XML"
     * for RELAX NG xml syntax
     */
    public void setSyntax( String style ) {
        this.style = Style.valueOf(style.toUpperCase());
    }

    /**
     * parse @schema
     *
     * @param schema the schema file to be processed by txw
     */
    public void setSchema( File schema ) {
        schemaFile = schema;
    }

    /**
     * parse @destdir
     *
     * @param dir the directory to produce generated source code in
     */
    public void setDestdir( File dir ) {
        try {
            options.codeWriter = new FileCodeWriter(dir);
        } catch (IOException e) {
            throw new BuildException(e);
        }
    }

    /**
     * parse @methodChaining
     *
     * @param flg true if the txw should generate api's that allow
     * method chaining (when possible, false otherwise
     */
    public void setMethodChaining( boolean flg ) {
        options.chainMethod = flg;
    }

    /**
     * launch txw
     */
    public void execute() throws BuildException {
        options.errorListener = new AntErrorListener(getProject());

        try {
            InputSource in = new InputSource(schemaFile.toURL().toExternalForm());

            String msg = "Compiling: " + in.getSystemId();
            log( msg, Project.MSG_INFO );

            if(style==Style.AUTO_DETECT) {
                String fileName = schemaFile.getPath().toLowerCase();
                if(fileName.endsWith("rnc"))
                    style = Style.COMPACT;
                else
                if(fileName.endsWith("xsd"))
                    style = Style.XMLSCHEMA;
                else
                    style = Style.XML;
            }

            switch(style) {
            case COMPACT:
                options.source = new RELAXNGLoader(new CompactParseable(in,options.errorListener));
                break;
            case XML:
                options.source = new RELAXNGLoader(new SAXParseable(in,options.errorListener));
                break;
            case XMLSCHEMA:
                options.source = new XmlSchemaLoader(in);
                break;
            }
        } catch (MalformedURLException e) {
            throw new BuildException(e);
        }

        // kick off the compiler
        Main.run(options);
        log( "Compilation complete.", Project.MSG_INFO );
    }
}
