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
package com.sun.xml.internal.ws.handler;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.ws.LogicalMessage;
import javax.xml.ws.handler.LogicalMessageContext;
import javax.xml.ws.handler.MessageContext.Scope;
import javax.xml.ws.handler.MessageContext;

/**
 * Implementation of LogicalMessageContext that is used in
 * the XML/HTTP binding. It is similar to LogicalMessageContextImpl
 * except that the message impl class it uses is a
 * {@link XMLLogicalMessageImpl} rather than a
 * {@link LogicalMessageImpl}.
 *
 * <p>Class has to defer information to HandlerContext so that properties
 * are shared between this and SOAPMessageContext.
 *
 * @see LogicalMessageImpl
 * @see XMLHandlerContext
 * @see XMLLogicalMessageImpl
 *
 * @author WS Development Team
 */
public class XMLLogicalMessageContextImpl implements LogicalMessageContext {

    private MessageContext ctxt;
    private XMLHandlerContext handlerCtxt;

    public XMLLogicalMessageContextImpl(XMLHandlerContext handlerCtxt) {
        this.handlerCtxt = handlerCtxt;
        ctxt = handlerCtxt.getMessageContext();
    }

    public XMLHandlerContext getHandlerContext() {
        return handlerCtxt;
    }

    public LogicalMessage getMessage() {
        return new XMLLogicalMessageImpl(handlerCtxt);
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
