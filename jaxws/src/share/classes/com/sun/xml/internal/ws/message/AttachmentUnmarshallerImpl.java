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

package com.sun.xml.internal.ws.message;

import com.sun.xml.internal.ws.api.message.Attachment;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.encoding.MimeMultipartParser;
import com.sun.xml.internal.ws.resources.EncodingMessages;

import javax.activation.DataHandler;
import javax.xml.bind.attachment.AttachmentUnmarshaller;
import javax.xml.ws.WebServiceException;

/**
 * Implementation of {@link AttachmentUnmarshaller} that uses
 * loads attachments from {@link AttachmentSet} directly.
 *
 * @author Vivek Pandey
 * @see MimeMultipartParser
 */
public final class AttachmentUnmarshallerImpl extends AttachmentUnmarshaller {

    private final AttachmentSet attachments;

    public AttachmentUnmarshallerImpl(AttachmentSet attachments) {
        this.attachments = attachments;
    }

    @Override
    public DataHandler getAttachmentAsDataHandler(String cid) {
        Attachment a = attachments.get(stripScheme(cid));
        if(a==null)
            throw new WebServiceException(EncodingMessages.NO_SUCH_CONTENT_ID(cid));
        return a.asDataHandler();
    }

    @Override
    public byte[] getAttachmentAsByteArray(String cid) {
        Attachment a = attachments.get(stripScheme(cid));
        if(a==null)
            throw new WebServiceException(EncodingMessages.NO_SUCH_CONTENT_ID(cid));
        return a.asByteArray();
    }

    /**
     * The CID reference has 'cid:' prefix, so get rid of it.
     */
    private String stripScheme(String cid) {
        if(cid.startsWith("cid:")) // work defensively, in case the input is wrong
            cid = cid.substring(4);
        return cid;
    }

}
