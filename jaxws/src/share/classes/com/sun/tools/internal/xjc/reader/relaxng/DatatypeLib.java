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

package com.sun.tools.internal.xjc.reader.relaxng;

import java.util.HashMap;
import java.util.Map;

import com.sun.tools.internal.xjc.model.CBuiltinLeafInfo;
import com.sun.tools.internal.xjc.model.TypeUse;
import com.sun.tools.internal.xjc.reader.xmlschema.SimpleTypeBuilder;

import com.sun.xml.internal.rngom.xml.util.WellKnownNamespaces;

/**
 * Data-bindable datatype library.
 *
 * @author Kohsuke Kawaguchi
 */
final class DatatypeLib {
    /**
     * Datatype library's namespace URI.
     */
    public final String nsUri;

    private final Map<String,TypeUse> types = new HashMap<String,TypeUse>();

    public DatatypeLib(String nsUri) {
        this.nsUri = nsUri;
    }

    /**
     * Maps the type name to the information.
     */
    TypeUse get(String name) {
        return types.get(name);
    }

    /**
     * Datatype library for the built-in type.
     */
    public static final DatatypeLib BUILTIN = new DatatypeLib("");

    /**
     * Datatype library for XML Schema datatypes.
     */
    public static final DatatypeLib XMLSCHEMA = new DatatypeLib(WellKnownNamespaces.XML_SCHEMA_DATATYPES);

    static {
        BUILTIN.types.put("token",CBuiltinLeafInfo.TOKEN);
        BUILTIN.types.put("string",CBuiltinLeafInfo.STRING);
        XMLSCHEMA.types.putAll(SimpleTypeBuilder.builtinConversions);
    }
}
