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
package com.sun.xml.internal.org.jvnet.mimepull;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Represents an attachment part in a MIME message. MIME message parsing is done
 * lazily using a pull parser, so the part may not have all the data. {@link #read}
 * and {@link #readOnce} may trigger the actual parsing the message. In fact,
 * parsing of an attachment part may be triggered by calling {@link #read} methods
 * on some other attachemnt parts. All this happens behind the scenes so the
 * application developer need not worry about these details.
 *
 * @author Jitendra Kotamraju
 */
public class MIMEPart {

    private volatile InternetHeaders headers;
    private volatile String contentId;
    private String contentType;
    volatile boolean parsed;    // part is parsed or not
    final MIMEMessage msg;
    private final DataHead dataHead;

    MIMEPart(MIMEMessage msg) {
        this.msg = msg;
        this.dataHead = new DataHead(this);
    }

    MIMEPart(MIMEMessage msg, String contentId) {
        this(msg);
        this.contentId = contentId;
    }

    /**
     * Can get the attachment part's content multiple times. That means
     * the full content needs to be there in memory or on the file system.
     * Calling this method would trigger parsing for the part's data. So
     * do not call this unless it is required(otherwise, just wrap MIMEPart
     * into a object that returns InputStream for e.g DataHandler)
     *
     * @return data for the part's content
     */
    public InputStream read() {
        return dataHead.read();
    }

    /**
     * Cleans up any resources that are held by this part (for e.g. deletes
     * the temp file that is used to serve this part's content). After
     * calling this, one shouldn't call {@link #read()} or {@link #readOnce()}
     */
    public void close() {
        dataHead.close();
    }


    /**
     * Can get the attachment part's content only once. The content
     * will be lost after the method. Content data is not be stored
     * on the file system or is not kept in the memory for the
     * following case:
     *   - Attachement parts contents are accessed sequentially
     *
     * In general, take advantage of this when the data is used only
     * once.
     *
     * @return data for the part's content
     */
    public InputStream readOnce() {
        return dataHead.readOnce();
    }

    public void moveTo(File f) {
        dataHead.moveTo(f);
    }

    /**
     * Returns Content-ID MIME header for this attachment part
     *
     * @return Content-ID of the part
     */
    public String getContentId() {
        if (contentId == null) {
            getHeaders();
        }
        return contentId;
    }

    /**
     * Returns Content-Type MIME header for this attachment part
     *
     * @return Content-Type of the part
     */
    public String getContentType() {
        if (contentType == null) {
            getHeaders();
        }
        return contentType;
    }

    private void getHeaders() {
        // Trigger parsing for the part headers
        while(headers == null) {
            if (!msg.makeProgress()) {
                if (headers == null) {
                    throw new IllegalStateException("Internal Error. Didn't get Headers even after complete parsing.");
                }
            }
        }
    }

    /**
     * Return all the values for the specified header.
     * Returns <code>null</code> if no headers with the
     * specified name exist.
     *
     * @param   name header name
     * @return  list of header values, or null if none
     */
    public List<String> getHeader(String name) {
        getHeaders();
        assert headers != null;
        return headers.getHeader(name);
    }

    /**
     * Return all the headers
     *
     * @return list of Header objects
     */
    public List<? extends Header> getAllHeaders() {
        getHeaders();
        assert headers != null;
        return headers.getAllHeaders();
    }

    /**
     * Callback to set headers
     *
     * @param headers MIME headers for the part
     */
    void setHeaders(InternetHeaders headers) {
        this.headers = headers;
        List<String> ct = getHeader("Content-Type");
        this.contentType = (ct == null) ? "application/octet-stream" : ct.get(0);
    }

    /**
     * Callback to notify that there is a partial content for the part
     *
     * @param buf content data for the part
     */
    void addBody(ByteBuffer buf) {
        dataHead.addBody(buf);
    }

    /**
     * Callback to indicate that parsing is done for this part
     * (no more update events for this part)
     */
    void doneParsing() {
        parsed = true;
        dataHead.doneParsing();
    }

    /**
     * Callback to set Content-ID for this part
     * @param cid Content-ID of the part
     */
    void setContentId(String cid) {
        this.contentId = cid;
    }

    @Override
    public String toString() {
        return "Part="+contentId;
    }

}
