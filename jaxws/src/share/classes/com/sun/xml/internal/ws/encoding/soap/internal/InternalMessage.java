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
package com.sun.xml.internal.ws.encoding.soap.internal;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Represents a SOAP message with headers, a body, and attachments.
 *
 * @author WS Development Team
 */
public class InternalMessage {
    private List<HeaderBlock> headers;
    private Set<QName> headerSet;
    private BodyBlock body;
    private final Map<String,AttachmentBlock> attachments = new HashMap<String, AttachmentBlock>();

    /**
     * @return the <code>BodyBlock</code> for this message
     */
    public BodyBlock getBody() {
        return body;
    }

    public void addHeader(HeaderBlock headerBlock) {
        if (headers == null) {
            headers = new ArrayList<HeaderBlock>();
            headerSet = new HashSet<QName>();
        }
        headers.add(headerBlock);
        headerSet.add(headerBlock.getName());
    }

    /*
     * Checks if a header is already present
     */
    public boolean isHeaderPresent(QName name) {
        if (headerSet == null) {
            return false;
        }
        return headerSet.contains(name);
    }

    /**
     * @return a <code>List</code> of <code>HeaderBlocks</code associated
     * with this message
     */
    public List<HeaderBlock> getHeaders() {
        return headers;
    }

    /**
     * @param body
     */
    public void setBody(BodyBlock body) {
        this.body = body;
    }

    public void addAttachment(AttachmentBlock attachment){
        attachments.put(attachment.getId(),attachment);
    }

    public AttachmentBlock getAttachment(String contentId){
        return attachments.get(contentId);
    }

    /**
     * @return a <code>Map</code> of contentIds to attachments
     */
    public Map<String, AttachmentBlock> getAttachments() {
        return attachments;
    }

}
