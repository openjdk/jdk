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

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.api.CompositeStructure;
import com.sun.xml.internal.bind.api.RawAccessor;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.model.ParameterImpl;
import com.sun.xml.internal.ws.model.WrapperParameter;
import com.sun.xml.internal.ws.message.jaxb.JAXBMessage;
import java.util.Collection;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;
import java.util.List;

/**
 * Builds Async bean
 *
 * @see MessageFiller
 * @author Jitendra Kotamraju
 */
abstract class AsyncBuilder {

    abstract Object fillAsyncBean(Object[] methodArgs, Object returnValue, Object bean);

    /**
     * Used to create a payload JAXB object just by taking
     * one of the parameters.
     */
    final static class Bare extends AsyncBuilder {
        /**
         * The index of the method invocation parameters that goes into the payload.
         */
        private final int methodPos;
        private final ValueGetter getter;
        private final RawAccessor accessor;

        /**
         * Creates a {@link BodyBuilder} from a bare parameter.
         */
        Bare(Class wrapper, ParameterImpl p) {
            this.methodPos = p.getIndex();
            this.getter = ValueGetter.get(p);
            QName name = p.getName();
            try {
                accessor = p.getOwner().getJAXBContext().getElementPropertyAccessor(
                        wrapper, name.getNamespaceURI(), name.getLocalPart() );
            } catch (JAXBException e) {
                throw new WebServiceException(  // TODO: i18n
                    wrapper+" do not have a property of the name "+name,e);
            }
        }

        /**
         * Picks up an object from the method arguments and uses it.
         */
        Object fillAsyncBean(Object[] methodArgs, Object returnValue, Object bean) {
            Object obj = (methodPos == -1) ? returnValue : getter.get(methodArgs[methodPos]);
            try {
                accessor.set(bean, obj);
            } catch (Exception e) {
                throw new WebServiceException(e);    // TODO:i18n
            }
            return bean;
        }
    }

    final static class Filler extends AsyncBuilder {
        /**
         * The index of the method invocation parameters that goes into the payload.
         */
        private final int methodPos;
        private final ValueGetter getter;

        /**
         * Creates a {@link BodyBuilder} from a bare parameter.
         */
        Filler(ParameterImpl p) {
            this.methodPos = p.getIndex();
            this.getter = ValueGetter.get(p);
        }

        /**
         * Picks up an object from the method arguments and uses it.
         */
        Object fillAsyncBean(Object[] methodArgs, Object returnValue, Object bean) {
            return (methodPos == -1) ? returnValue : getter.get(methodArgs[methodPos]);
        }
    }

    public static AsyncBuilder NONE = new None();

    static final class None extends AsyncBuilder {
        Object fillAsyncBean(Object[] methodArgs, Object returnValue, Object bean) {
            return bean;
        }
    }

    static final class Composite extends AsyncBuilder {
        private final AsyncBuilder[] builders;
        private final Class beanClass;

        public Composite(AsyncBuilder[] builders, Class beanClass) {
            this.builders = builders;
            this.beanClass = beanClass;
        }


        public Composite(Collection<? extends AsyncBuilder> builders, Class beanClass) {
            this(builders.toArray(new AsyncBuilder[builders.size()]), beanClass);
        }

        Object fillAsyncBean(Object[] methodArgs, Object returnValue, Object bean) {
            try {
                bean = beanClass.newInstance();
            } catch (Exception ex) {
                throw new WebServiceException(ex);
            }
            for (AsyncBuilder builder : builders) {
                builder.fillAsyncBean(methodArgs, returnValue, bean);
            }
            return bean;
        }
    }


    /**
     * Used to handle a 'wrapper' style request.
     * Common part of rpc/lit and doc/lit.
     */
    abstract static class Wrapped extends AsyncBuilder {

        /**
         * Where in the method argument list do they come from?
         */
        protected final int[] indices;

        /**
         * Abstracts away the {@link Holder} handling when touching method arguments.
         */
        protected final ValueGetter[] getters;

        protected Wrapped(WrapperParameter wp) {

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

        /**
         * Wrapper bean.
         */
        private final Class wrapper;

        /**
         * Creates a {@link BodyBuilder} from a {@link WrapperParameter}.
         */
        DocLit(Class wrapper, WrapperParameter wp) {
            super(wp);
            this.wrapper = wrapper;

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
        Object fillAsyncBean(Object[] methodArgs, Object returnValue, Object bean) {
            try {
                // fill in wrapped parameters from methodArgs
                for( int i=indices.length-1; i>=0; i-- ) {
                    Object obj = (indices[i] == -1) ? returnValue : methodArgs[indices[i]];
                    accessors[i].set(bean,getters[i].get(obj));
                }
            } catch (Exception e) {
                throw new WebServiceException(e);    // TODO:i18n
            }
            return bean;
        }
    }

}
