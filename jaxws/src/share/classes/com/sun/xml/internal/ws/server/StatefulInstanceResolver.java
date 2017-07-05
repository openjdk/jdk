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

package com.sun.xml.internal.ws.server;

import com.sun.istack.internal.FragmentContentHandler;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferSource;
import com.sun.xml.internal.stream.buffer.stax.StreamWriterBufferCreator;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.server.InstanceResolver;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.api.server.WSWebServiceContext;
import com.sun.xml.internal.ws.developer.EPRRecipe;
import com.sun.xml.internal.ws.developer.StatefulWebServiceManager;
import com.sun.xml.internal.ws.resources.ServerMessages;
import com.sun.xml.internal.ws.spi.ProviderImpl;
import com.sun.xml.internal.ws.util.xml.ContentHandlerToXMLStreamWriter;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link InstanceResolver} that looks at JAX-WS cookie header to
 * determine the instance to which a message will be routed.
 *
 * <p>
 * See {@link StatefulWebServiceManager} for more about user-level semantics.
 *
 * @author Kohsuke Kawaguchi
 */
public final class StatefulInstanceResolver<T> extends AbstractMultiInstanceResolver<T> implements StatefulWebServiceManager<T> {
    /**
     * This instance is used for serving messages that have no cookie
     * or cookie value that the server doesn't recognize.
     */
    private volatile @Nullable T fallback;

    /**
     * Maintains the stateful service instance and its time-out timer.
     */
    private final class Instance {
        final @NotNull T instance;
        TimerTask task;

        public Instance(T instance) {
            this.instance = instance;
        }

        /**
         * Resets the timer.
         */
        public synchronized void restartTimer() {
            cancel();
            if(timeoutMilliseconds==0)  return; // no timer

            task = new TimerTask() {
                public void run() {
                    try {
                        Callback<T> cb = timeoutCallback;
                        if(cb!=null) {
                            cb.onTimeout(instance,StatefulInstanceResolver.this);
                            return;
                        }
                        // default operation is to unexport it.
                        unexport(instance);
                    } catch (Throwable e) {
                        // don't let an error in the code kill the timer thread
                        logger.log(Level.SEVERE, "time out handler failed", e);
                    }
                }
            };
            timer.schedule(task,timeoutMilliseconds);
        }

        /**
         * Cancels the timer.
         */
        public synchronized void cancel() {
            if(task!=null)
                task.cancel();
            task = null;
        }
    }

    /**
     * Maps object ID to instances.
     */
    private final Map<String,Instance> instances = Collections.synchronizedMap(new HashMap<String,Instance>());
    /**
     * Reverse look up for {@link #instances}.
     */
    private final Map<T,String> reverseInstances = Collections.synchronizedMap(new HashMap<T,String>());

    // time out control. 0=disabled
    private volatile long timeoutMilliseconds = 0;
    private volatile Callback<T> timeoutCallback;

    public StatefulInstanceResolver(Class<T> clazz) {
        super(clazz);
    }

    @Override
    public @NotNull T resolve(Packet request) {
        HeaderList headers = request.getMessage().getHeaders();
        Header header = headers.get(COOKIE_TAG, true);
        String id=null;
        if(header!=null) {
            // find the instance
            id = header.getStringContent();
            Instance o = instances.get(id);
            if(o!=null) {
                o.restartTimer();
                return o.instance;
            }

            // huh? what is this ID?
            logger.log(Level.INFO,"Request had an unrecognized object ID "+id);
        }

        // need to fallback
        T fallback = this.fallback;
        if(fallback!=null)
            return fallback;

        if(id==null)
            throw new WebServiceException(ServerMessages.STATEFUL_COOKIE_HEADER_REQUIRED(COOKIE_TAG));
        else
            throw new WebServiceException(ServerMessages.STATEFUL_COOKIE_HEADER_INCORRECT(COOKIE_TAG,id));
    }

    @Override
    public void start(WSWebServiceContext wsc, WSEndpoint endpoint) {
        super.start(wsc,endpoint);

        if(endpoint.getBinding().getAddressingVersion()==null)
            // addressing is not enabled.
            throw new WebServiceException(ServerMessages.STATEFUL_REQURES_ADDRESSING(clazz));

            // inject StatefulWebServiceManager.
        for(Field field: clazz.getDeclaredFields()) {
            if(field.getType()==StatefulWebServiceManager.class) {
                if(!Modifier.isStatic(field.getModifiers()))
                    throw new WebServiceException(ServerMessages.STATIC_RESOURCE_INJECTION_ONLY(StatefulWebServiceManager.class,field));
                new FieldInjectionPlan<T,StatefulWebServiceManager>(field).inject(null,this);
            }
        }

        for(Method method : clazz.getDeclaredMethods()) {
            Class[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != 1)
                continue;   // not what we are looking for

            if(paramTypes[0]==StatefulWebServiceManager.class) {
                if(!Modifier.isStatic(method.getModifiers()))
                    throw new WebServiceException(ServerMessages.STATIC_RESOURCE_INJECTION_ONLY(StatefulWebServiceManager.class,method));

                new MethodInjectionPlan<T,StatefulWebServiceManager>(method).inject(null,this);
            }
        }
    }

    @Override
    public void dispose() {
        reverseInstances.clear();
        synchronized(instances) {
            for (Instance t : instances.values()) {
                t.cancel();
                dispose(t.instance);
            }
            instances.clear();
        }
        if(fallback!=null)
            dispose(fallback);
        fallback = null;
    }

    @NotNull
    public W3CEndpointReference export(T o) {
        return export(W3CEndpointReference.class,o);
    }

    @NotNull
    public <EPR extends EndpointReference>EPR export(Class<EPR> epr, T o) {
        return export(epr,o,null);
    }

    public <EPR extends EndpointReference> EPR export(Class<EPR> epr, T o, EPRRecipe recipe) {
        return export(epr, InvokerTube.getCurrentPacket(), o, recipe);
    }

    @NotNull
    public <EPR extends EndpointReference>EPR export(Class<EPR> epr, WebServiceContext context, T o) {
        if (context instanceof WSWebServiceContext) {
            WSWebServiceContext wswsc = (WSWebServiceContext) context;
            return export(epr, wswsc.getRequestPacket(), o);
        }

        throw new WebServiceException(ServerMessages.STATEFUL_INVALID_WEBSERVICE_CONTEXT(context));
    }

    @NotNull
    public <EPR extends EndpointReference> EPR export(Class<EPR> adrsVer, @NotNull Packet currentRequest, T o) {
        return export(adrsVer,currentRequest,o,null);
    }

    public <EPR extends EndpointReference> EPR export(Class<EPR> adrsVer, @NotNull Packet currentRequest, T o, EPRRecipe recipe) {
        return export(adrsVer, currentRequest.webServiceContextDelegate.getEPRAddress(currentRequest,owner), o, recipe);
    }

    @NotNull
    public <EPR extends EndpointReference> EPR export(Class<EPR> adrsVer, String endpointAddress, T o) {
        return export(adrsVer,endpointAddress,o,null);
    }

    @NotNull
    public <EPR extends EndpointReference> EPR export(Class<EPR> adrsVer, String endpointAddress, T o, EPRRecipe recipe) {
        if(endpointAddress==null)
            throw new IllegalArgumentException("No address available");

        String key = reverseInstances.get(o);

        if(key!=null) return createEPR(key, adrsVer, endpointAddress, recipe);

        // not exported yet.
        synchronized(this) {
            // double check now in the synchronization block to
            // really make sure that we can export.
            key = reverseInstances.get(o);
            if(key!=null) return createEPR(key, adrsVer, endpointAddress, recipe);

            if(o!=null)
                prepare(o);
            key = UUID.randomUUID().toString();
            Instance instance = new Instance(o);
            instances.put(key, instance);
            reverseInstances.put(o,key);
            if(timeoutMilliseconds!=0)
                instance.restartTimer();
        }

        return createEPR(key, adrsVer, endpointAddress, recipe);
    }

    /**
     * Creates an EPR that has the right key.
     */
    private <EPR extends EndpointReference> EPR createEPR(String key, Class<EPR> eprClass, String address, EPRRecipe recipe) {
        AddressingVersion adrsVer = AddressingVersion.fromSpecClass(eprClass);

        try {
            StreamWriterBufferCreator w = new StreamWriterBufferCreator();

            w.writeStartDocument();
            w.writeStartElement("wsa","EndpointReference", adrsVer.nsUri);
            w.writeNamespace("wsa",adrsVer.nsUri);

            w.writeStartElement("wsa","Address",adrsVer.nsUri);
            w.writeCharacters(address);
            w.writeEndElement();

            w.writeStartElement("wsa","ReferenceParameters",adrsVer.nsUri);
            w.writeStartElement(COOKIE_TAG.getPrefix(), COOKIE_TAG.getLocalPart(), COOKIE_TAG.getNamespaceURI());
            w.writeCharacters(key);
            w.writeEndElement();
            if(recipe!=null) {
                for (Header h : recipe.getReferenceParameters())
                    h.writeTo(w);
            }
            w.writeEndElement();

            if(recipe!=null) {
                List<Source> metadata = recipe.getMetadata();
                if(!metadata.isEmpty()) {
                    w.writeStartElement("wsa","Metadata",adrsVer.nsUri);
                    Transformer t = XmlUtil.newTransformer();
                    for (Source s : metadata)
                        try {
                            t.transform(s,
                                new SAXResult(new FragmentContentHandler(new ContentHandlerToXMLStreamWriter(w))));
                        } catch (TransformerException e) {
                            throw new IllegalArgumentException("Unable to write EPR metadata "+s,e);
                        }
                    w.writeEndElement();
                }
            }

            w.writeEndElement();
            w.writeEndDocument();

            // TODO: this can be done better by writing SAX code that produces infoset
            // and setting that as Source.
            return eprClass.cast(ProviderImpl.INSTANCE.readEndpointReference(
                new XMLStreamBufferSource(w.getXMLStreamBuffer())));
        } catch (XMLStreamException e) {
            throw new Error(e); // this must be a bug in our code
        }
    }

    public void unexport(@Nullable T o) {
        if(o==null)     return;
        String key = reverseInstances.get(o);
        if(key==null)   return; // already unexported
        instances.remove(key);
        reverseInstances.remove(o);
    }

    public T resolve(EndpointReference epr) {
        class CookieSniffer extends DefaultHandler {
            StringBuilder buf = new StringBuilder();
            boolean inCookie = false;
            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                if(localName.equals(COOKIE_TAG.getLocalPart()) && uri.equals(COOKIE_TAG.getNamespaceURI()))
                    inCookie = true;
            }
            public void characters(char ch[], int start, int length) throws SAXException {
                if(inCookie)
                    buf.append(ch,start,length);
            }
            public void endElement(String uri, String localName, String qName) throws SAXException {
                inCookie = false;
            }
        }
        CookieSniffer sniffer = new CookieSniffer();
        epr.writeTo(new SAXResult(sniffer));

        Instance o = instances.get(sniffer.buf.toString());
        if(o!=null)
            return o.instance;
        return null;
    }

    public void setFallbackInstance(T o) {
        if(o!=null)
            prepare(o);
        this.fallback = o;
    }

    public void setTimeout(long milliseconds, Callback<T> callback) {
        if(milliseconds<0)
            throw new IllegalArgumentException();
        this.timeoutMilliseconds = milliseconds;
        this.timeoutCallback = callback;
        if(timeoutMilliseconds>0)
            startTimer();
    }

    public void touch(T o) {
        String key = reverseInstances.get(o);
        if(key==null)   return; // already unexported.
        Instance inst = instances.get(key);
        if(inst==null)  return;
        inst.restartTimer();
    }

    /**
     * Timer that controls the instance time out. Lazily created.
     */
    private volatile Timer timer;

    private synchronized void startTimer() {
        if(timer==null)
            timer = new Timer("JAX-WS stateful web service timeout timer");
    }


    private static final QName COOKIE_TAG = new QName("http://jax-ws.dev.java.net/xml/ns/","objectId","jaxws");

    private static final Logger logger =
        Logger.getLogger(com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server");
}
