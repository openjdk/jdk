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

package com.sun.xml.internal.ws.client.dispatch.impl;

import java.util.ArrayList;

import javax.xml.ws.http.HTTPBinding;
import javax.xml.ws.soap.SOAPBinding;

import com.sun.xml.internal.ws.pept.ept.ContactInfoList;
import com.sun.xml.internal.ws.pept.ept.ContactInfoListIterator;
import com.sun.xml.internal.ws.client.ContactInfoBase;
import com.sun.xml.internal.ws.client.ContactInfoListIteratorBase;
import com.sun.xml.internal.ws.client.dispatch.impl.protocol.MessageDispatcherHelper;
import com.sun.xml.internal.ws.encoding.soap.client.SOAP12XMLDecoder;
import com.sun.xml.internal.ws.encoding.soap.client.SOAP12XMLEncoder;
import com.sun.xml.internal.ws.encoding.soap.client.SOAPXMLDecoder;
import com.sun.xml.internal.ws.encoding.soap.client.SOAPXMLEncoder;
import com.sun.xml.internal.ws.encoding.xml.XMLDecoder;
import com.sun.xml.internal.ws.encoding.xml.XMLEncoder;
import com.sun.xml.internal.ws.protocol.xml.client.XMLMessageDispatcher;

/**
 * @author: WS Development Team
 */
public class DispatchContactInfoList implements ContactInfoList {

    public ContactInfoListIterator iterator() {
        ArrayList<Object> arrayList = new ArrayList<Object>();

        arrayList.add(new ContactInfoBase(null,
            new MessageDispatcherHelper(),
            new SOAPXMLEncoder(),
            new SOAPXMLDecoder(),SOAPBinding.SOAP11HTTP_BINDING));
        arrayList.add(new ContactInfoBase(null,
            new MessageDispatcherHelper(),
            new SOAP12XMLEncoder(),
            new SOAP12XMLDecoder(), SOAPBinding.SOAP12HTTP_BINDING));
        arrayList.add(new ContactInfoBase(null,
                new XMLMessageDispatcher(),
                new XMLEncoder(),
                new XMLDecoder(), HTTPBinding.HTTP_BINDING));
        /*arrayList.add(new DispatchContactInfo(null,
                new MessageDispatcherHelper(new DispatchEncoderDecoderUtil()),
                new SOAPFastEncoder(new DispatchEncoderDecoderUtil()),
                new SOAPFastDecoder(new DispatchEncoderDecoderUtil())));
          */
        return new ContactInfoListIteratorBase(arrayList);
    }
}
