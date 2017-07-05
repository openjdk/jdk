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

package com.sun.xml.internal.ws.server;

import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.NextAction;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractTubeImpl;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.fault.SOAPFaultBuilder;
import com.sun.xml.internal.ws.util.pipe.AbstractSchemaValidationTube;
import org.xml.sax.SAXException;

import javax.xml.transform.Source;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import javax.xml.ws.WebServiceException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Tube} that does the schema validation on the server side.
 *
 * @author Jitendra Kotamraju
 */
public class ServerSchemaValidationTube extends AbstractSchemaValidationTube {

    private static final Logger LOGGER = Logger.getLogger(ServerSchemaValidationTube.class.getName());

    private final Schema schema;
    private final Validator validator;

    private final boolean noValidation;
    private final SEIModel seiModel;
    private final WSDLPort wsdlPort;

    public ServerSchemaValidationTube(WSEndpoint endpoint, WSBinding binding,
            SEIModel seiModel, WSDLPort wsdlPort, Tube next) {
        super(binding, next);
        this.seiModel = seiModel;
        this.wsdlPort = wsdlPort;

        if (endpoint.getServiceDefinition() != null) {
            MetadataResolverImpl mdresolver = new MetadataResolverImpl(endpoint.getServiceDefinition());
            Source[] sources = getSchemaSources(endpoint.getServiceDefinition(), mdresolver);
            for(Source source : sources) {
                LOGGER.fine("Constructing service validation schema from = "+source.getSystemId());
                //printDOM((DOMSource)source);
            }
            if (sources.length != 0) {
                noValidation = false;
                sf.setResourceResolver(mdresolver);
                try {
                    schema = sf.newSchema(sources);
                } catch(SAXException e) {
                    throw new WebServiceException(e);
                }
                validator = schema.newValidator();
                return;
            }
        }
        noValidation = true;
        schema = null;
        validator = null;
    }

    protected Validator getValidator() {
        return validator;
    }

    protected boolean isNoValidation() {
        return noValidation;
    }

    @Override
    public NextAction processRequest(Packet request) {
        if (isNoValidation() || !feature.isInbound() || !request.getMessage().hasPayload() || request.getMessage().isFault()) {
            return super.processRequest(request);
        }
        try {
            doProcess(request);
        } catch(SAXException se) {
            LOGGER.log(Level.WARNING, "Client Request doesn't pass Service's Schema Validation", se);
            // Client request is invalid. So sending specific fault code
            // Also converting this to fault message so that handlers may get
            // to see the message.
            SOAPVersion soapVersion = binding.getSOAPVersion();
            Message faultMsg = SOAPFaultBuilder.createSOAPFaultMessage(
                    soapVersion, null, se, soapVersion.faultCodeClient);
            return doReturnWith(request.createServerResponse(faultMsg,
                    wsdlPort, seiModel, binding));
        }
        return super.processRequest(request);
    }

    @Override
    public NextAction processResponse(Packet response) {
        if (isNoValidation() || !feature.isOutbound() || response.getMessage() == null || !response.getMessage().hasPayload() || response.getMessage().isFault()) {
            return super.processResponse(response);
        }
        try {
            doProcess(response);
        } catch(SAXException se) {
            // TODO: Should we convert this to fault Message ??
            throw new WebServiceException(se);
        }
        return super.processResponse(response);
    }

    protected ServerSchemaValidationTube(ServerSchemaValidationTube that, TubeCloner cloner) {
        super(that,cloner);
        //this.docs = that.docs;
        this.schema = that.schema;      // Schema is thread-safe
        this.validator = schema.newValidator();
        this.noValidation = that.noValidation;
        this.seiModel = that.seiModel;
        this.wsdlPort = that.wsdlPort;
    }

    public AbstractTubeImpl copy(TubeCloner cloner) {
        return new ServerSchemaValidationTube(this,cloner);
    }

}
