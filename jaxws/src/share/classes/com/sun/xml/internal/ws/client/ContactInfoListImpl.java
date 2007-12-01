/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.ws.client;

import com.sun.xml.internal.ws.pept.ept.ContactInfoList;
import com.sun.xml.internal.ws.pept.ept.ContactInfoListIterator;
import com.sun.xml.internal.ws.encoding.soap.client.SOAP12XMLDecoder;
import com.sun.xml.internal.ws.encoding.soap.client.SOAP12XMLEncoder;
import com.sun.xml.internal.ws.encoding.soap.client.SOAPXMLDecoder;
import com.sun.xml.internal.ws.encoding.soap.client.SOAPXMLEncoder;
import com.sun.xml.internal.ws.protocol.soap.client.SOAPMessageDispatcher;
import com.sun.xml.internal.ws.protocol.xml.client.XMLMessageDispatcher;

import javax.xml.ws.soap.SOAPBinding;
import java.util.ArrayList;

/**
 * @author WS Development Team
 * List of {@link com.sun.pept.ept.ContactInfo}s
 */
public class ContactInfoListImpl implements ContactInfoList {
    private static final ArrayList arrayList = new ArrayList();
    static {
        arrayList.add(new ContactInfoBase(null,
            new SOAPMessageDispatcher(),
            new SOAPXMLEncoder(),
            new SOAPXMLDecoder(), SOAPBinding.SOAP11HTTP_BINDING));
        arrayList.add(new ContactInfoBase(null,
            new SOAPMessageDispatcher(),
            new SOAP12XMLEncoder(),
            new SOAP12XMLDecoder(), SOAPBinding.SOAP12HTTP_BINDING));
    }

    /**
     * Iterator over the list of {@link com.sun.pept.ept.ContactInfo}s
     * @see com.sun.pept.ept.ContactInfoList#iterator()
     */
    public ContactInfoListIterator iterator() {
        return new ContactInfoListIteratorBase(arrayList);
    }

}
