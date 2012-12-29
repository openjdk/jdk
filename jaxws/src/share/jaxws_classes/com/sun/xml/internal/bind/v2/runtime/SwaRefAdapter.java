/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime;


import javax.activation.DataHandler;
import javax.xml.bind.annotation.XmlAttachmentRef;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.bind.attachment.AttachmentUnmarshaller;

import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallingContext;


/**
 * {@link XmlAdapter} that binds the value as a SOAP attachment.
 *
 * <p>
 * On the user classes the SwA handling is done by using the {@link XmlAttachmentRef}
 * annotation, but internally we treat it as a {@link XmlJavaTypeAdapter} with this
 * adapter class. This is true with both XJC and the runtime.
 *
 * <p>
 * the model builder code and the code generator does the conversion and
 * shield the rest of the RI from this mess.
 * Also see @see <a href="http://webservices.xml.com/pub/a/ws/2003/09/16/wsbp.html?page=2">http://webservices.xml.com/pub/a/ws/2003/09/16/wsbp.html?page=2</a>.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SwaRefAdapter extends XmlAdapter<String,DataHandler> {

    public SwaRefAdapter() {
    }

    public DataHandler unmarshal(String cid) {
        AttachmentUnmarshaller au = UnmarshallingContext.getInstance().parent.getAttachmentUnmarshaller();
        // TODO: error check
        return au.getAttachmentAsDataHandler(cid);
    }

    public String marshal(DataHandler data) {
        if(data==null)      return null;
        AttachmentMarshaller am = XMLSerializer.getInstance().attachmentMarshaller;
        // TODO: error check
        return am.addSwaRefAttachment(data);
    }
}
