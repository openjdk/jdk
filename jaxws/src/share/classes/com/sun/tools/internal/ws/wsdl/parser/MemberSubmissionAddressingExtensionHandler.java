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
/*
 * $Id: MemberSubmissionAddressingExtensionHandler.java,v 1.1.2.6 2006/10/31 19:52:07 vivekp Exp $
 */

package com.sun.tools.internal.ws.wsdl.parser;

import com.sun.tools.internal.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.util.Map;

/**
 * @author Arun Gupta
 */
public class MemberSubmissionAddressingExtensionHandler extends W3CAddressingExtensionHandler {
    public MemberSubmissionAddressingExtensionHandler(Map<String, AbstractExtensionHandler> extensionHandlerMap) {
        super(extensionHandlerMap);
    }

    public MemberSubmissionAddressingExtensionHandler(Map<String, AbstractExtensionHandler> extensionHandlerMap, ErrorReceiver env) {
        super(extensionHandlerMap, env);
    }

    @Override
    public String getNamespaceURI() {
        return AddressingVersion.MEMBER.wsdlNsUri;
    }

    protected QName getActionQName() {
        return AddressingVersion.MEMBER.wsdlActionTag;
    }

    protected QName getWSDLExtensionQName() {
        return AddressingVersion.MEMBER.wsdlExtensionTag;
    }

    @Override
    public boolean handlePortExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        // ignore any extension elements
        return false;
    }

}
