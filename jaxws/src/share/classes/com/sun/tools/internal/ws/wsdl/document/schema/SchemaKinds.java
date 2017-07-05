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

package com.sun.tools.internal.ws.wsdl.document.schema;

import com.sun.tools.internal.ws.wsdl.framework.Kind;

/**
 *
 * @author WS Development Team
 */
public class SchemaKinds {
    public static final Kind XSD_ATTRIBUTE = new Kind("xsd:attribute");
    public static final Kind XSD_ATTRIBUTE_GROUP =
        new Kind("xsd:attributeGroup");
    public static final Kind XSD_CONSTRAINT = new Kind("xsd:constraint");
    public static final Kind XSD_ELEMENT = new Kind("xsd:element");
    public static final Kind XSD_GROUP = new Kind("xsd:group");
    public static final Kind XSD_IDENTITY_CONSTRAINT =
        new Kind("xsd:identityConstraint");
    public static final Kind XSD_NOTATION = new Kind("xsd:notation");
    public static final Kind XSD_TYPE = new Kind("xsd:type");

    private SchemaKinds() {
    }
}
