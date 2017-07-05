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

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import com.sun.tools.internal.txw2.model.NodeSet;
import com.sun.tools.internal.txw2.builder.xsd.XmlSchemaBuilder;
import com.sun.xml.xsom.parser.XSOMParser;

/**
 * @author Kohsuke Kawaguchi
 */
class XmlSchemaLoader implements SchemaBuilder {
    private final InputSource in;

    public XmlSchemaLoader(InputSource in) {
        this.in = in;
    }

    public NodeSet build(TxwOptions options) throws SAXException {
        XSOMParser xsom = new XSOMParser();
        xsom.parse(in);
        return XmlSchemaBuilder.build(xsom.getResult(),options);
    }
}
