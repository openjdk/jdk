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
package com.sun.xml.internal.ws.server.sei;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.api.CompositeStructure;
import com.sun.xml.internal.bind.api.RawAccessor;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.message.jaxb.JAXBMessage;
import com.sun.xml.internal.ws.model.ParameterImpl;
import com.sun.xml.internal.ws.model.WrapperParameter;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;
import java.util.List;

/**
 * Builds a JAXB object that represents the payload.
 *
 * @see MessageFiller
 * @author Jitendra Kotamraju
 */
abstract class EndpointResponseMessageBuilder {
    abstract Message createMessage(Object[] methodArgs, Object returnValue);

    static final EndpointResponseMessageBuilder EMPTY_SOAP11 = new Empty(SOAPVersion.SOAP_11);
    static final EndpointResponseMessageBuilder EMPTY_SOAP12 = new Empty(SOAPVersion.SOAP_12);

    private static final class Empty extends EndpointResponseMessageBuilder {
        private final SOAPVersion soapVersion;

        public Empty(SOAPVersion soapVersion) {
            this.soapVersion = soapVersion;
        }

        Message createMessage(Object[] methodArgs, Object returnValue) {
            return Messages.createEmpty(soapVersion);
        }
    }

    /**
     * Base class for those {@link EndpointResponseMessageBuilder}s that build a {@link Message}
     * from JAXB objects.
     */
    private static abstract class JAXB extends EndpointResponseMessageBuilder {
        /**
         * This object determines the binding of the object returned
         * from {@link #createMessage(Object[], Object)}
         */
        private final Bridge bridge;
        private final SOAPVersion soapVersion;

        protected JAXB(Bridge bridge, SOAPVersion soapVersion) {
            assert bridge!=null;
            this.bridge = bridge;
            this.soapVersion = soapVersion;
        }

        final Message createMessage(Object[] methodArgs, Object returnValue) {
            return JAXBMessage.create( bridge, build(methodArgs, returnValue), soapVersion );
        }

        /**
         * Builds a JAXB object that becomes the payload.
         */
        abstract Object build(Object[] methodArgs, Object returnValue);
    }

    /**
     * Used to create a payload JAXB object just by taking
     * one of the parameters.
     */
    final static class Bare extends JAXB {
        /**
         * The index of the method invocation parameters that goes into the payload.
         */
        private final int methodPos;

        private final ValueGetter getter;

        /**
         * Creates a {@link EndpointResponseMessageBuilder} from a bare parameter.
         */
        Bare(ParameterImpl p, SOAPVersion soapVersion) {
            super(p.getBridge(), soapVersion);
            this.methodPos = p.getIndex();
            this.getter = ValueGetter.get(p);
        }

        /**
         * Picks up an object from the method arguments and uses it.
         */
        Object build(Object[] methodArgs, Object returnValue) {
            if (methodPos == -1) {
                return returnValue;
            }
            return getter.get(methodArgs[methodPos]);
        }
    }


    /**
     * Used to handle a 'wrapper' style request.
     * Common part of rpc/lit and doc/lit.
     */
    abstract static class Wrapped extends JAXB {

        /**
         * Where in the method argument list do they come from?
         */
        protected final int[] indices;

        /**
         * Abstracts away the {@link Holder} handling when touching method arguments.
         */
        protected final ValueGetter[] getters;

        protected Wrapped(WrapperParameter wp, SOAPVersion soapVersion) {
            super(wp.getBridge(), soapVersion);

            List<ParameterImpl> children = wp.getWrapperChildren();

            indices = new int[children.size()];
            getters = new ValueGetter[children.size()];
            for( int i=0; i<indices.length; i++ ) {
                ParameterImpl p = children.get(i);
                indices[i] = p.getIndex();
                getters[i] = ValueGetter.get(p);
            }
        }
    }

    /**
     * Used to create a payload JAXB object by wrapping
     * multiple parameters into one "wrapper bean".
     */
    final static class DocLit extends Wrapped {
        /**
         * How does each wrapped parameter binds to XML?
         */
        private final RawAccessor[] accessors;

        //private final RawAccessor retAccessor;

        /**
         * Wrapper bean.
         */
        private final Class wrapper;

        /**
         * Creates a {@link EndpointResponseMessageBuilder} from a {@link WrapperParameter}.
         */
        DocLit(WrapperParameter wp, SOAPVersion soapVersion) {
            super(wp, soapVersion);

            wrapper = (Class)wp.getBridge().getTypeReference().type;

            List<ParameterImpl> children = wp.getWrapperChildren();

            accessors = new RawAccessor[children.size()];
            for( int i=0; i<accessors.length; i++ ) {
                ParameterImpl p = children.get(i);
                QName name = p.getName();
                try {
                    accessors[i] = p.getOwner().getJAXBContext().getElementPropertyAccessor(
                        wrapper, name.getNamespaceURI(), name.getLocalPart() );
                } catch (JAXBException e) {
                    throw new WebServiceException(  // TODO: i18n
                        wrapper+" do not have a property of the name "+name,e);
                }
            }

        }

        /**
         * Packs a bunch of arguments into a {@link CompositeStructure}.
         */
        Object build(Object[] methodArgs, Object returnValue) {
            try {
                Object bean = wrapper.newInstance();

                // fill in wrapped parameters from methodArgs
                for( int i=indices.length-1; i>=0; i-- ) {
                    if (indices[i] == -1) {
                        accessors[i].set(bean, returnValue);
                    } else {
                        accessors[i].set(bean,getters[i].get(methodArgs[indices[i]]));
                    }
                }

                return bean;
            } catch (InstantiationException e) {
                // this is irrecoverable
                Error x = new InstantiationError(e.getMessage());
                x.initCause(e);
                throw x;
            } catch (IllegalAccessException e) {
                // this is irrecoverable
                Error x = new IllegalAccessError(e.getMessage());
                x.initCause(e);
                throw x;
            } catch (AccessorException e) {
                // this can happen when the set method throw a checked exception or something like that
                throw new WebServiceException(e);    // TODO:i18n
            }
        }
    }


    /**
     * Used to create a payload JAXB object by wrapping
     * multiple parameters into a {@link CompositeStructure}.
     *
     * <p>
     * This is used for rpc/lit, as we don't have a wrapper bean for it.
     * (TODO: Why don't we have a wrapper bean for this, when doc/lit does!?)
     */
    final static class RpcLit extends Wrapped {
        /**
         * How does each wrapped parameter binds to XML?
         */
        private final Bridge[] parameterBridges;

        /**
         * Used for error diagnostics.
         */
        private final List<ParameterImpl> children;

        /**
         * Creates a {@link EndpointResponseMessageBuilder} from a {@link WrapperParameter}.
         */
        RpcLit(WrapperParameter wp, SOAPVersion soapVersion) {
            super(wp, soapVersion);
            // we'll use CompositeStructure to pack requests
            assert wp.getTypeReference().type==CompositeStructure.class;

            this.children = wp.getWrapperChildren();

            parameterBridges = new Bridge[children.size()];
            for( int i=0; i<parameterBridges.length; i++ )
                parameterBridges[i] = children.get(i).getBridge();
        }

        /**
         * Packs a bunch of arguments intoa {@link CompositeStructure}.
         */
        CompositeStructure build(Object[] methodArgs, Object returnValue) {
            CompositeStructure cs = new CompositeStructure();
            cs.bridges = parameterBridges;
            cs.values = new Object[parameterBridges.length];

            // fill in wrapped parameters from methodArgs
            for( int i=indices.length-1; i>=0; i-- ) {
                Object v;
                if (indices[i] == -1) {
                    v = getters[i].get(returnValue);
                } else {
                    v = getters[i].get(methodArgs[indices[i]]);
                }
                if(v==null) {
                    throw new WebServiceException("Method Parameter: "+
                        children.get(i).getName() +" cannot be null. This is BP 1.1 R2211 violation.");
                }
                cs.values[i] = v;
            }

            return cs;
        }
    }
}
