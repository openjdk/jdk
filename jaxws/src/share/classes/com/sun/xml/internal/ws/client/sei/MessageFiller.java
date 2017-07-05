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

package com.sun.xml.internal.ws.client.sei;

import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.ws.api.message.Attachment;
import com.sun.xml.internal.ws.api.message.Headers;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.message.ByteArrayAttachment;
import com.sun.xml.internal.ws.message.DataHandlerAttachment;
import com.sun.xml.internal.ws.message.JAXBAttachment;
import com.sun.xml.internal.ws.model.ParameterImpl;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;
import javax.activation.DataHandler;
import javax.xml.transform.Source;
import javax.xml.ws.WebServiceException;

/**
 * Puts a non-payload message parameter to {@link Message}.
 *
 * <p>
 * Instance of this class is used to handle header parameters and attachment parameters.
 * They add things to {@link Message}.
 *
 * @see BodyBuilder
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
abstract class MessageFiller {

    /**
     * The index of the method invocation parameters that this object looks for.
     */
    protected final int methodPos;

    protected MessageFiller( int methodPos) {
        this.methodPos = methodPos;
    }

    /**
     * Moves an argument of a method invocation into a {@link Message}.
     */
    abstract void fillIn(Object[] methodArgs, Message msg);

    /**
     * Adds a parameter as an MIME attachment to {@link Message}.
     */
    static abstract class AttachmentFiller extends MessageFiller {
        protected final ParameterImpl param;
        protected final ValueGetter getter;
        protected final String mimeType;
        private final String contentIdPart;

        protected AttachmentFiller(ParameterImpl param, ValueGetter getter) {
            super(param.getIndex());
            this.param = param;
            this.getter = getter;
            mimeType = param.getBinding().getMimeType();
            try {
                contentIdPart = URLEncoder.encode(param.getPartName(), "UTF-8")+'=';
            } catch (UnsupportedEncodingException e) {
                throw new WebServiceException(e);
            }
        }

        /**
         * Creates an MessageFiller based on the parameter type
         *
         * @param param
         *      runtime Parameter that abstracts the annotated java parameter
         * @param getter
         *      Gets a value from an object that represents a parameter passed
         *      as a method argument.
         */
        public static MessageFiller createAttachmentFiller(ParameterImpl param, ValueGetter getter) {
            Class type = (Class)param.getTypeReference().type;
            if (DataHandler.class.isAssignableFrom(type) || Source.class.isAssignableFrom(type)) {
                return new DataHandlerFiller(param, getter);
            } else if (byte[].class==type) {
                return new ByteArrayFiller(param, getter);
            } else if(isXMLMimeType(param.getBinding().getMimeType())) {
                return new JAXBFiller(param, getter);
            } else {
                return new DataHandlerFiller(param, getter);
            }
        }

        String getContentId() {
            return contentIdPart+UUID.randomUUID()+"@jaxws.sun.com";
        }
    }

    private static class ByteArrayFiller extends AttachmentFiller {
        protected ByteArrayFiller(ParameterImpl param, ValueGetter getter) {
            super(param, getter);
        }
        void fillIn(Object[] methodArgs, Message msg) {
            String contentId = getContentId();
            Object obj = getter.get(methodArgs[methodPos]);
            Attachment att = new ByteArrayAttachment(contentId,(byte[])obj,mimeType);
            msg.getAttachments().add(att);
        }
    }

    private static class DataHandlerFiller extends AttachmentFiller {
        protected DataHandlerFiller(ParameterImpl param, ValueGetter getter) {
            super(param, getter);
        }
        void fillIn(Object[] methodArgs, Message msg) {
            String contentId = getContentId();
            Object obj = getter.get(methodArgs[methodPos]);
            DataHandler dh = (obj instanceof DataHandler) ? (DataHandler)obj : new DataHandler(obj,mimeType);
            Attachment att = new DataHandlerAttachment(contentId, dh);
            msg.getAttachments().add(att);
        }
    }

    private static class JAXBFiller extends AttachmentFiller {
        protected JAXBFiller(ParameterImpl param, ValueGetter getter) {
            super(param, getter);
        }
        void fillIn(Object[] methodArgs, Message msg) {
            String contentId = getContentId();
            Object obj = getter.get(methodArgs[methodPos]);
            Attachment att = new JAXBAttachment(contentId, obj, param.getBridge(), mimeType);
            msg.getAttachments().add(att);
        }
    }

    /**
     * Adds a parameter as an header.
     */
    static final class Header extends MessageFiller {
        private final Bridge bridge;
        private final ValueGetter getter;

        protected Header(int methodPos, Bridge bridge, ValueGetter getter) {
            super(methodPos);
            this.bridge = bridge;
            this.getter = getter;
        }

        void fillIn(Object[] methodArgs, Message msg) {
            Object value = getter.get(methodArgs[methodPos]);
            msg.getHeaders().add(Headers.create(bridge,value));
        }
    }

    private static boolean isXMLMimeType(String mimeType){
        return (mimeType.equals("text/xml") || mimeType.equals("application/xml")) ? true : false;
    }

}
