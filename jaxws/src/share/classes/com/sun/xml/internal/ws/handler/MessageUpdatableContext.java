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

import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.xml.ws.handler.MessageContext;

/**
 * The class represents a MessageContext(Properties) and also allows the Message to be modified.
 * This is extended by SOAPMessageContextImpl and LogicalMessageContextImpl.
 *
 * @author WS Development Team
 */
public abstract class MessageUpdatableContext implements MessageContext {
    final Packet packet;
    private MessageContextImpl ctxt;
    /** Creates a new instance of MessageUpdatableContext */
    public MessageUpdatableContext(Packet packet) {
        ctxt = new MessageContextImpl(packet);
        this.packet = packet;
    }

    /**
     * Fill a {@link Packet} with values of this {@link MessageContext}.
     */
    private void fill(Packet packet) {
        ctxt.fill(packet);
    }
    /**
     * Updates Message in the packet with user modifications
     */
    abstract void updateMessage();

    /**
     * Updates Message in the packet with user modifications
     * returns the new packet's message
     */
    Message getPacketMessage(){
        updateMessage();
        return packet.getMessage();
    }

    /**
     * Sets Message in the packet
     * Any user modifications done on previous Message are lost.
     */
    abstract void setPacketMessage(Message newMessage);

    /**
     * Updates the complete packet with user modfications to the message and
     * properties cahnges in MessageContext
     */
    final void updatePacket() {
        updateMessage();
        fill(packet);
    }

    MessageContextImpl getMessageContext() {
        return ctxt;
    }

    public void setScope(String name, Scope scope) {
        ctxt.setScope(name, scope);
    }

    public Scope getScope(String name) {
        return ctxt.getScope(name);
    }

    /* java.util.Map methods below here */

    public void clear() {
        ctxt.clear();
    }

    public boolean containsKey(Object obj) {
        return ctxt.containsKey(obj);
    }

    public boolean containsValue(Object obj) {
        return ctxt.containsValue(obj);
    }

    public Set<Entry<String, Object>> entrySet() {
        return ctxt.entrySet();
    }

    public Object get(Object obj) {
        return ctxt.get(obj);
    }

    public boolean isEmpty() {
        return ctxt.isEmpty();
    }

    public Set<String> keySet() {
        return ctxt.keySet();
    }

    public Object put(String str, Object obj) {
        return ctxt.put(str, obj);
    }

    public void putAll(Map<? extends String, ? extends Object> map) {
        ctxt.putAll(map);
    }

    public Object remove(Object obj) {
        return ctxt.remove(obj);
    }

    public int size() {
        return ctxt.size();
    }

    public Collection<Object> values() {
        return ctxt.values();
    }

}
