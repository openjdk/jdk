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

package com.sun.xml.internal.ws.encoding;

import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSFeatureList;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.message.Attachment;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.message.MimeAttachmentSet;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.Map;

import javax.xml.ws.WebServiceFeature;

/**
 * {@link Codec} that uses MIME/multipart as the base format.
 *
 * @author Jitendra Kotamraju
 */
public final class SwACodec extends MimeCodec {

    public SwACodec(SOAPVersion version, WSFeatureList f, Codec rootCodec) {
        super(version, f);
        this.mimeRootCodec = rootCodec;
    }

    private SwACodec(SwACodec that) {
        super(that);
        this.mimeRootCodec = that.mimeRootCodec.copy();
    }

    @Override
    protected void decode(MimeMultipartParser mpp, Packet packet) throws IOException {
        // TODO: handle attachments correctly
        Attachment root = mpp.getRootPart();
        Codec rootCodec = getMimeRootCodec(packet);
        if (rootCodec instanceof RootOnlyCodec) {
            ((RootOnlyCodec)rootCodec).decode(root.asInputStream(),root.getContentType(),packet, new MimeAttachmentSet(mpp));
        } else {
            rootCodec.decode(root.asInputStream(),root.getContentType(),packet);
            Map<String, Attachment> atts = mpp.getAttachmentParts();
            for(Map.Entry<String, Attachment> att : atts.entrySet()) {
                packet.getMessage().getAttachments().add(att.getValue());
            }
        }
    }

    public ContentType encode(Packet packet, WritableByteChannel buffer) {
        //TODO: not yet implemented
        throw new UnsupportedOperationException();
    }

    public SwACodec copy() {
        return new SwACodec(this);
    }
}
