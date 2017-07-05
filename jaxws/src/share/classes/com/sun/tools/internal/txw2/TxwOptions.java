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

import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import com.sun.xml.internal.txw2.annotation.XmlNamespace;

/**
 * Controls the various aspects of the TXW generation.
 *
 * But this doesn't contain options for the command-line interface
 * nor any of the driver-level configuration (such as where to place
 * the generated source code, etc.)
 *
 * @author Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class TxwOptions {
    public final JCodeModel codeModel = new JCodeModel();

    /**
     * The package to put the generated code into.
     */
    public JPackage _package;

    /**
     * Always non-null.
     */
    public ErrorListener errorListener;

    /**
     * The generated code will be sent to this.
     */
    CodeWriter codeWriter;

    /**
     * Schema file.
     */
    SchemaBuilder source;

    /**
     * If true, generate attribute/value methods that
     * returns the <tt>this</tt> object for chaining.
     */
    public boolean chainMethod;

    /**
     * If true, the generated code will not use the package-level
     * {@link XmlNamespace} annotation.
     */
    public boolean noPackageNamespace;
}
