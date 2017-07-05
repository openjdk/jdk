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
package com.sun.xml.internal.rngom.parse.xml;

import org.xml.sax.DTDHandler;
import org.xml.sax.SAXException;
import org.relaxng.datatype.ValidationContext;

import java.util.Hashtable;

public abstract class DtdContext implements DTDHandler, ValidationContext {
    private final Hashtable notationTable;
    private final Hashtable unparsedEntityTable;

    public DtdContext() {
        notationTable = new Hashtable();
        unparsedEntityTable = new Hashtable();
    }

    public DtdContext(DtdContext dc) {
        notationTable = dc.notationTable;
        unparsedEntityTable = dc.unparsedEntityTable;
    }

    public void notationDecl(String name, String publicId, String systemId)
        throws SAXException {
        notationTable.put(name, name);
    }

    public void unparsedEntityDecl(
        String name,
        String publicId,
        String systemId,
        String notationName)
        throws SAXException {
        unparsedEntityTable.put(name, name);
    }

    public boolean isNotation(String notationName) {
        return notationTable.get(notationName) != null;
    }

    public boolean isUnparsedEntity(String entityName) {
        return unparsedEntityTable.get(entityName) != null;
    }

    public void clearDtdContext() {
        notationTable.clear();
        unparsedEntityTable.clear();
    }
}
