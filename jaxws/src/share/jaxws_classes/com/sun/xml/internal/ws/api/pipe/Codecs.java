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

package com.sun.xml.internal.ws.api.pipe;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.WSFeatureList;

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
     * This creates a full {@link Codec} for SOAP binding.
     *
     * @param feature the WebServiceFeature objects
     * @return non null codec to parse entire SOAP message(including MIME parts)
     */
    public static @NotNull SOAPBindingCodec createSOAPBindingCodec(WSFeatureList feature) {
        return new com.sun.xml.internal.ws.encoding.SOAPBindingCodec(feature);
    }

    /**
     * This creates a full {@link Codec} for XML binding.
     *
     * @param feature the WebServiceFeature objects
     * @return non null codec to parse entire XML message.
     */
    public static @NotNull Codec createXMLCodec(WSFeatureList feature) {
        return new com.sun.xml.internal.ws.encoding.XMLHTTPBindingCodec(feature);
    }


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
        return new com.sun.xml.internal.ws.encoding.SOAPBindingCodec(binding.getFeatures(), xmlEnvelopeCodec);
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

    /**
     * Creates a default {@link Codec} that can be used to used to
     * decode XML infoset in SOAP envelope(primary part in MIME message).
     * New codecs can be written using this codec as delegate. WSBinding
     * parameter is used to get SOAP version and features.
     *
     * @param binding SOAP version and features are used from this binding
     * @return non null default xml codec
     *
     * @deprecated use {@link #createSOAPEnvelopeXmlCodec(WSFeatureList)}
     */
    public static @NotNull StreamSOAPCodec createSOAPEnvelopeXmlCodec(@NotNull WSBinding binding) {
        return com.sun.xml.internal.ws.encoding.StreamSOAPCodec.create(binding);
    }

    /**
     * Creates a default {@link Codec} that can be used to used to
     * decode XML infoset in SOAP envelope(primary part in MIME message).
     * New codecs can be written using this codec as delegate. WSFeatureList
     * parameter is used to get SOAP version and features.
     *
     * @param features SOAP version and features are used from this WSFeatureList
     * @return non null default xml codec
     */
    public static @NotNull StreamSOAPCodec createSOAPEnvelopeXmlCodec(@NotNull WSFeatureList features) {
        return com.sun.xml.internal.ws.encoding.StreamSOAPCodec.create(features);
    }
}
