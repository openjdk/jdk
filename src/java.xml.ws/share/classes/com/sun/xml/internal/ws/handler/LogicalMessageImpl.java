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

package com.sun.xml.internal.ws.handler;

import com.sun.xml.internal.ws.api.message.MessageHeaders;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.spi.db.BindingContext;
import com.sun.xml.internal.ws.spi.db.BindingContextFactory;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import com.sun.xml.internal.ws.message.EmptyMessageImpl;
import com.sun.xml.internal.ws.message.DOMMessage;
import com.sun.xml.internal.ws.message.jaxb.JAXBMessage;
import com.sun.xml.internal.ws.message.source.PayloadSourceMessage;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.util.JAXBSource;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.ws.LogicalMessage;
import javax.xml.ws.WebServiceException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Document;

/**
 * Implementation of {@link LogicalMessage}. This class implements the methods
 * used by LogicalHandlers to get/set the request or response either
 * as a JAXB object or as javax.xml.transform.Source.
 * <p/>
 * <p>The {@link Message} that is passed into the constructor
 * is used to retrieve the payload of the request or response.
 *
 * @author WS Development Team
 * @see Message
 * @see LogicalMessageContextImpl
 */
class LogicalMessageImpl implements LogicalMessage {
    private Packet packet;
//    protected JAXBContext defaultJaxbContext;
    protected BindingContext defaultJaxbContext;
    private ImmutableLM lm = null;


    public LogicalMessageImpl(BindingContext defaultJaxbContext, Packet
            packet) {
        // don't create extract payload until Users wants it.
        this.packet = packet;
        this.defaultJaxbContext = defaultJaxbContext;
    }

    public Source getPayload() {
        if (lm == null) {
            Source payload = packet.getMessage().copy().readPayloadAsSource();
            if (payload instanceof DOMSource) {
                lm = createLogicalMessageImpl(payload);
            }
            return payload;
        } else {
            return lm.getPayload();
        }
    }

    public void setPayload(Source payload) {
        lm = createLogicalMessageImpl(payload);
    }

    private ImmutableLM createLogicalMessageImpl(Source payload) {
        if (payload == null) {
            lm = new EmptyLogicalMessageImpl();
        } else if (payload instanceof DOMSource) {
            lm = new DOMLogicalMessageImpl((DOMSource) payload);
        } else {
            lm = new SourceLogicalMessageImpl(payload);
        }
        return lm;
    }

    public Object getPayload(BindingContext context) {
        if (context == null) {
            context = defaultJaxbContext;
        }
        if (context == null)
            throw new WebServiceException("JAXBContext parameter cannot be null");

        Object o;
        if (lm == null) {
            try {
                o = packet.getMessage().copy().readPayloadAsJAXB(context.createUnmarshaller());
            } catch (JAXBException e) {
                throw new WebServiceException(e);
            }
        } else {
            o = lm.getPayload(context);
            lm = new JAXBLogicalMessageImpl(context.getJAXBContext(), o);
        }
        return o;
    }

    public Object getPayload(JAXBContext context) {
        if (context == null) {
            return getPayload(defaultJaxbContext);
        }
        if (context == null)
            throw new WebServiceException("JAXBContext parameter cannot be null");

        Object o;
        if (lm == null) {
            try {
                o = packet.getMessage().copy().readPayloadAsJAXB(context.createUnmarshaller());
            } catch (JAXBException e) {
                throw new WebServiceException(e);
            }
        } else {
            o = lm.getPayload(context);
            lm = new JAXBLogicalMessageImpl(context, o);
        }
        return o;
    }

    public void setPayload(Object payload, BindingContext context) {
        if (context == null) {
            context = defaultJaxbContext;
        }
        if (payload == null) {
            lm = new EmptyLogicalMessageImpl();
        } else {
            lm = new JAXBLogicalMessageImpl(context.getJAXBContext(), payload);
        }
    }

    public void setPayload(Object payload, JAXBContext context) {
        if (context == null) {
                setPayload(payload, defaultJaxbContext);
        }
        if (payload == null) {
            lm = new EmptyLogicalMessageImpl();
        } else {
            lm = new JAXBLogicalMessageImpl(context, payload);
        }
    }

    public boolean isPayloadModifed() {
        return (lm != null);
    }

    /**
     * This should be called by first checking if the payload is modified.
     *
     * @param headers
     * @param attachments
     * @param binding
     * @return
     */
    public Message getMessage(MessageHeaders headers, AttachmentSet attachments, WSBinding binding) {
        assert isPayloadModifed();
        if(isPayloadModifed()) {
            return lm.getMessage(headers,attachments,binding);
        } else {
            return packet.getMessage();
        }

    }


    private abstract class ImmutableLM {
        public abstract Source getPayload();
        public abstract Object getPayload(BindingContext context);
        public abstract Object getPayload(JAXBContext context);
        public abstract Message getMessage(MessageHeaders headers, AttachmentSet attachments, WSBinding binding);

    }

    private class DOMLogicalMessageImpl extends SourceLogicalMessageImpl {
        private DOMSource dom;

        public DOMLogicalMessageImpl(DOMSource dom) {
            super(dom);
            this.dom = dom;
        }

        @Override
        public Source getPayload() {
            return dom;
        }

        public Message getMessage(MessageHeaders headers, AttachmentSet attachments, WSBinding binding) {
            Node n = dom.getNode();
            if(n.getNodeType()== Node.DOCUMENT_NODE) {
                n = ((Document)n).getDocumentElement();
            }
            return new DOMMessage(binding.getSOAPVersion(), headers, (Element)n, attachments);
        }
    }

    private class EmptyLogicalMessageImpl extends ImmutableLM {
        public EmptyLogicalMessageImpl() {

        }

        @Override
        public Source getPayload() {
            return null;
        }

        @Override
        public Object getPayload(JAXBContext context) {
            return null;
        }

        @Override
        public Object getPayload(BindingContext context) {
            return null;
        }

        public Message getMessage(MessageHeaders headers, AttachmentSet attachments, WSBinding binding) {
            return new EmptyMessageImpl(headers,attachments,binding.getSOAPVersion());
        }
    }

    private class JAXBLogicalMessageImpl extends ImmutableLM {
        private JAXBContext ctxt;
        private Object o;

        public JAXBLogicalMessageImpl(JAXBContext ctxt, Object o) {
            this.ctxt = ctxt;
            this.o = o;

        }

        @Override
        public Source getPayload() {
            JAXBContext context = ctxt;
            if (context == null) {
                context = defaultJaxbContext.getJAXBContext();
            }
            try {
                return new JAXBSource(context, o);
            } catch (JAXBException e) {
                throw new WebServiceException(e);
            }
        }

        @Override
        public Object getPayload(JAXBContext context) {
//            if(context == ctxt) {
//                return o;
//            }
            try {
                Source payloadSrc = getPayload();
                if (payloadSrc == null)
                    return null;
                Unmarshaller unmarshaller = context.createUnmarshaller();
                return unmarshaller.unmarshal(payloadSrc);
            } catch (JAXBException e) {
                throw new WebServiceException(e);
            }
        }
        public Object getPayload(BindingContext context) {
//          if(context == ctxt) {
//              return o;
//          }
          try {
              Source payloadSrc = getPayload();
              if (payloadSrc == null)
                  return null;
              Unmarshaller unmarshaller = context.createUnmarshaller();
              return unmarshaller.unmarshal(payloadSrc);
          } catch (JAXBException e) {
              throw new WebServiceException(e);
          }
      }

        public Message getMessage(MessageHeaders headers, AttachmentSet attachments, WSBinding binding) {
            return JAXBMessage.create(BindingContextFactory.create(ctxt), o,binding.getSOAPVersion(), headers,attachments);
        }
    }

    private class SourceLogicalMessageImpl extends ImmutableLM {
        private Source payloadSrc;

        public SourceLogicalMessageImpl(Source source) {
            this.payloadSrc = source;
        }

        public Source getPayload() {
            assert (!(payloadSrc instanceof DOMSource));
            try {
                Transformer transformer = XmlUtil.newTransformer();
                DOMResult domResult = new DOMResult();
                transformer.transform(payloadSrc, domResult);
                DOMSource dom = new DOMSource(domResult.getNode());
                lm = new DOMLogicalMessageImpl((DOMSource) dom);
                payloadSrc = null;
                return dom;
            } catch (TransformerException te) {
                throw new WebServiceException(te);
            }
        }

        public Object getPayload(JAXBContext context) {
            try {
                Source payloadSrc = getPayload();
                if (payloadSrc == null)
                    return null;
                Unmarshaller unmarshaller = context.createUnmarshaller();
                return unmarshaller.unmarshal(payloadSrc);
            } catch (JAXBException e) {
                throw new WebServiceException(e);
            }

        }

        public Object getPayload(BindingContext context) {
            try {
                Source payloadSrc = getPayload();
                if (payloadSrc == null)
                    return null;
                Unmarshaller unmarshaller = context.createUnmarshaller();
                return unmarshaller.unmarshal(payloadSrc);
            } catch (JAXBException e) {
                throw new WebServiceException(e);
            }

        }

        public Message getMessage(MessageHeaders headers, AttachmentSet attachments, WSBinding binding) {
            assert (payloadSrc!=null);
            return new PayloadSourceMessage(headers, payloadSrc, attachments,binding.getSOAPVersion());
        }
    }
}
