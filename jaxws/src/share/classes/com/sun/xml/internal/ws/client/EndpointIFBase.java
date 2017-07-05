/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.ws.client;

import com.sun.xml.internal.ws.pept.Delegate;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.spi.runtime.ClientTransportFactory;
import com.sun.xml.internal.ws.transport.http.client.HttpClientTransportFactory;

import javax.xml.ws.Binding;
import javax.xml.ws.BindingProvider;

import java.util.Map;

/**
 * @author WS Development Team
 */
public class EndpointIFBase implements com.sun.xml.internal.ws.pept.presentation.Stub,
    com.sun.xml.internal.ws.spi.runtime.StubBase, BindingProvider, InternalBindingProvider {

    protected Map<String, Object> _requestContext;
    protected Map<String, Object> _responseContext;

    protected String _bindingId = null;
    protected Delegate _delegate = null;
    protected BindingImpl binding;

    private ClientTransportFactory _transportFactory;

    void setResponseContext(ResponseContext context) {
        _responseContext = context;
    }

    public void _setDelegate(Delegate delegate) {
        _delegate = delegate;
    }

    public Delegate _getDelegate() {
        return _delegate;
    }

    public ClientTransportFactory _getTransportFactory() {
        _transportFactory =
            (com.sun.xml.internal.ws.spi.runtime.ClientTransportFactory)getRequestContext().get(BindingProviderProperties.CLIENT_TRANSPORT_FACTORY);

        if (_transportFactory == null) {
            _transportFactory = new HttpClientTransportFactory();
        }
        return _transportFactory;
    }

    public void _setTransportFactory(ClientTransportFactory f) {
        getRequestContext().put(BindingProviderProperties.CLIENT_TRANSPORT_FACTORY, f);
        _transportFactory = f;
    }

    //toDo: have to update generator on PeptStub to getContext
    public void updateResponseContext(MessageInfo messageInfo) {
        ResponseContext responseContext = (ResponseContext)
            messageInfo.getMetaData(BindingProviderProperties.JAXWS_RESPONSE_CONTEXT_PROPERTY);
        if (responseContext != null) { // null in async case
            setResponseContext(responseContext);
        }
    }

    /**
     * Get the JAXWSContext that is used in processing request messages.
     * <p/>
     * Modifications to the request context do not affect asynchronous
     * operations that have already been started.
     *
     * @return The JAXWSContext that is used in processing request messages.
     */
    public Map<String, Object> getRequestContext() {
        if (_requestContext == null)
            _requestContext = new RequestContext(this);

        return _requestContext;
    }

    /**
     * Get the JAXWSContext that resulted from processing a response message.
     * <p/>
     * The returned context is for the most recently completed synchronous
     * operation. Subsequent synchronous operation invocations overwrite the
     * response context. Asynchronous operations return their response context
     * via the Response interface.
     *
     * @return The JAXWSContext that is used in processing request messages.
     */
    public Map<String, Object> getResponseContext() {
        if (_responseContext == null)
            _responseContext = new ResponseContext(this);
        return _responseContext;
    }

    public Binding getBinding() {
        return binding;
    }

    public void _setBinding(BindingImpl binding) {
        this.binding = binding;
    }

    /**
     * returns binding id from BindingImpl
     *
     * @return the String representing the BindingID
     */
    public String _getBindingId() {
        return _bindingId;
    }

}
