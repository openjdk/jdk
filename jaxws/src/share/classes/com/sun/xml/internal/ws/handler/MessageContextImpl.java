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

package com.sun.xml.internal.ws.handler;

import com.sun.xml.internal.ws.api.message.Attachment;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.util.ReadOnlyPropertyException;

import javax.activation.DataHandler;
import javax.xml.ws.handler.MessageContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author WS Development Team
 */

class MessageContextImpl implements MessageContext {
    private Map<String,Object> fallbackMap = null;
    private Set<String> handlerScopeProps;
    Packet packet;


    void fallback() {
        if(fallbackMap == null) {
            fallbackMap = new HashMap<String,Object>();
            fallbackMap.putAll(packet.createMapView());
            fallbackMap.putAll(packet.invocationProperties);
        }
    }
    /** Creates a new instance of MessageContextImpl */
    public MessageContextImpl(Packet packet) {
        this.packet = packet;
        handlerScopeProps =  packet.getHandlerScopePropertyNames(false);
    }
    protected void updatePacket() {
        throw new UnsupportedOperationException("wrong call");
    }

    public void setScope(String name, Scope scope) {
        if(!containsKey(name))
            throw new IllegalArgumentException("Property " + name + " does not exist.");
        if(scope == Scope.APPLICATION) {
            handlerScopeProps.remove(name);
        } else {
            handlerScopeProps.add(name);

        }
    }

    public Scope getScope(String name) {
        if(!containsKey(name))
            throw new IllegalArgumentException("Property " + name + " does not exist.");
        if(handlerScopeProps.contains(name)) {
            return Scope.HANDLER;
        } else {
            return Scope.APPLICATION;
        }
    }

    public int size() {
        fallback();
        return fallbackMap.size();
    }

    public boolean isEmpty() {
        fallback();
        return fallbackMap.isEmpty();
    }

    public boolean containsKey(Object key) {
        if(fallbackMap == null) {
            if(packet.supports(key))
                return true;
            return packet.invocationProperties.containsKey(key);
        } else {
            fallback();
            return fallbackMap.containsKey(key);
        }
    }

    public boolean containsValue(Object value) {
        fallback();
        return fallbackMap.containsValue(value);
    }

    public Object put(String key, Object value) {
        if (fallbackMap == null) {
            if (packet.supports(key)) {
                return packet.put(key, value);     // strongly typed
            }
            if (!packet.invocationProperties.containsKey(key)) {
                //New property, default to Scope.HANDLER
                handlerScopeProps.add(key);
            }
            return packet.invocationProperties.put(key, value);

        } else {
            fallback();
            if (!fallbackMap.containsKey(key)) {
                //new property, default to Scope.HANDLER
                handlerScopeProps.add(key);
            }
            return fallbackMap.put(key, value);
        }
    }
    public Object get(Object key) {
        if(key == null)
            return null;
        Object value;
        if(fallbackMap == null) {
            if (packet.supports(key)) {
                value =  packet.get(key);    // strongly typed
            } else {
                value = packet.invocationProperties.get(key);
            }
        } else {
            fallback();
            value = fallbackMap.get(key);
        }
        //add the attachments from the Message to the corresponding attachment property
        if(key.equals(MessageContext.OUTBOUND_MESSAGE_ATTACHMENTS) ||
            key.equals(MessageContext.INBOUND_MESSAGE_ATTACHMENTS)){
            Map<String, DataHandler> atts = (Map<String, DataHandler>) value;
            if(atts == null)
                atts = new HashMap<String, DataHandler>();
            AttachmentSet attSet = packet.getMessage().getAttachments();
            for(Attachment att : attSet){
                atts.put(att.getContentId(), att.asDataHandler());
            }
            return atts;
        }
        return value;
    }

    public void putAll(Map<? extends String, ? extends Object> t) {
        fallback();
        for(String key: t.keySet()) {
            if(!fallbackMap.containsKey(key)) {
                //new property, default to Scope.HANDLER
                handlerScopeProps.add(key);
            }
        }
        fallbackMap.putAll(t);
    }

    public void clear() {
        fallback();
        fallbackMap.clear();
    }
    public Object remove(Object key){
        fallback();
        handlerScopeProps.remove(key);
        return fallbackMap.remove(key);
    }
    public Set<String> keySet() {
        fallback();
        return fallbackMap.keySet();
    }
    public Set<Map.Entry<String, Object>> entrySet(){
        fallback();
        return fallbackMap.entrySet();
    }
    public Collection<Object> values() {
        fallback();
        return fallbackMap.values();
    }


    /**
     * Fill a {@link Packet} with values of this {@link MessageContext}.
     */
    void fill(Packet packet) {
        if(fallbackMap != null) {
            for (Entry<String, Object> entry : fallbackMap.entrySet()) {
                String key = entry.getKey();
                if (packet.supports(key)) {
                    try {
                        packet.put(key, entry.getValue());
                    } catch (ReadOnlyPropertyException e) {
                        // Nothing to do
                    }
                } else {
                    packet.invocationProperties.put(key, entry.getValue());
                }
            }

            //Remove properties which are removed by user.
            packet.createMapView().keySet().retainAll(fallbackMap.keySet());
            packet.invocationProperties.keySet().retainAll(fallbackMap.keySet());
        }
    }

}
