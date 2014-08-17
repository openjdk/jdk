/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.wsdl.document.mime;

import com.sun.tools.internal.ws.wsdl.framework.Entity;
import com.sun.tools.internal.ws.wsdl.framework.EntityAction;
import com.sun.tools.internal.ws.wsdl.framework.ExtensionImpl;
import com.sun.tools.internal.ws.wsdl.framework.ExtensionVisitor;
import org.xml.sax.Locator;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A MIME multipartRelated extension.
 *
 * @author WS Development Team
 */
public class MIMEMultipartRelated extends ExtensionImpl {

    public MIMEMultipartRelated(Locator locator) {
        super(locator);
        _parts = new ArrayList<MIMEPart>();
    }

    public QName getElementName() {
        return MIMEConstants.QNAME_MULTIPART_RELATED;
    }

    public void add(MIMEPart part) {
        _parts.add(part);
    }

    public Iterable<MIMEPart> getParts() {
        return _parts;
    }

    public void withAllSubEntitiesDo(EntityAction action) {
        super.withAllSubEntitiesDo(action);

        for (Iterator iter = _parts.iterator(); iter.hasNext();) {
            action.perform((Entity) iter.next());
        }
    }

    public void accept(ExtensionVisitor visitor) throws Exception {
        visitor.preVisit(this);
        visitor.postVisit(this);
    }

    public void validateThis() {
    }

    private List<MIMEPart> _parts;
}
