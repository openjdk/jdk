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

package com.sun.xml.internal.ws.encoding;

import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.WSFeatureList;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.message.stream.StreamHeader11;

import javax.xml.stream.XMLStreamReader;
import java.util.Collections;
import java.util.List;

/**
 * {@link StreamSOAPCodec} for SOAP 1.1.
 *
 * @author Paul.Sandoz@Sun.Com
 */
final class StreamSOAP11Codec extends StreamSOAPCodec {

    public static final String SOAP11_MIME_TYPE = "text/xml";
    public static final String DEFAULT_SOAP11_CONTENT_TYPE =
            SOAP11_MIME_TYPE+"; charset="+SOAPBindingCodec.DEFAULT_ENCODING;

    private static final List<String> EXPECTED_CONTENT_TYPES = Collections.singletonList(SOAP11_MIME_TYPE);

    /*package*/  StreamSOAP11Codec() {
        super(SOAPVersion.SOAP_11);
    }

    /*package*/  StreamSOAP11Codec(WSBinding binding) {
        super(binding);
    }

    /*package*/  StreamSOAP11Codec(WSFeatureList features) {
        super(features);
    }

    public String getMimeType() {
        return SOAP11_MIME_TYPE;
    }

    @Override
    protected ContentType getContentType(Packet packet) {
        ContentTypeImpl.Builder b = getContenTypeBuilder(packet);
        b.soapAction = packet.soapAction;
        return b.build();
    }

    @Override
    protected String getDefaultContentType() {
        return DEFAULT_SOAP11_CONTENT_TYPE;
    }

    protected List<String> getExpectedContentTypes() {
        return EXPECTED_CONTENT_TYPES;
    }
}
