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

package com.sun.xml.internal.ws.api.pipe;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;

/**
 * Factory methods for some of the {@link Codec} implementations.
 *
 * <p>
 * This class provides methods to create codecs for SOAP/HTTP binding.
 * It allows to replace default SOAP envelope(primary part in MIME message)
 * codec in the whole Codec.
 *
 * <p>
 * This is a part of the JAX-WS RI internal API so that
 * {@link Tube} and transport implementations can reuse the implementations
 * done inside the JAX-WS.
 *
 * @author Jitendra Kotamraju
 * @author Kohsuke Kawaguchi
 */
public abstract class Codecs {

    /**
     * This creates a full {@link Codec} for SOAP binding using the primary
     * XML codec argument. The codec argument is used to encode/decode SOAP envelopes
     * while the returned codec is responsible for encoding/decoding the whole
     * message.
     *
     * <p>
     * Creates codecs can be set during the {@link Tube}line assembly process.
     *
     * @see ServerTubeAssemblerContext#setCodec(Codec)
     * @see ClientTubeAssemblerContext#setCodec(Codec)
     *
     * @param binding binding of the webservice
     * @param xmlEnvelopeCodec SOAP envelope codec
     * @return non null codec to parse entire SOAP message(including MIME parts)
     */
    public static @NotNull SOAPBindingCodec createSOAPBindingCodec(WSBinding binding, StreamSOAPCodec xmlEnvelopeCodec) {
        return new com.sun.xml.internal.ws.encoding.SOAPBindingCodec(binding, xmlEnvelopeCodec);
    }

    /**
     * Creates a default {@link Codec} that can be used to used to
     * decode XML infoset in SOAP envelope(primary part in MIME message). New codecs
     * can be written using this codec as delegate.
     *
     * @param version SOAP version of the binding
     * @return non null default xml codec
     */
    public static @NotNull
    StreamSOAPCodec createSOAPEnvelopeXmlCodec(@NotNull SOAPVersion version) {
        return com.sun.xml.internal.ws.encoding.StreamSOAPCodec.create(version);
    }
}
