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
package com.sun.xml.internal.ws.server;
import javax.xml.ws.handler.MessageContext;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.xml.ws.handler.MessageContext.Scope;

public class AppMsgContextImpl implements MessageContext {

    private MessageContext ctxt;
    private Map<String, Object> appContext; // properties in APPLICATION scope

    private void init() {
        if (appContext == null) {
            appContext = new HashMap<String, Object>();
            Iterator<Entry<String, Object>> i = ctxt.entrySet().iterator();
            while(i.hasNext()) {
                Entry<String, Object> entry = i.next();
                if (ctxt.getScope(entry.getKey()) == Scope.APPLICATION) {
                    appContext.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    public AppMsgContextImpl(MessageContext ctxt) {
        this.ctxt = ctxt;
    }

    /* java.util.Map methods below here */

    public void clear() {
        init();
        Set<Entry<String, Object>> props = appContext.entrySet();
        for (Entry<String, Object> prop : props) {
            ctxt.remove(prop.getKey());
        }
        appContext.clear();
    }

    public boolean containsKey(Object obj) {
        init();
        return appContext.containsKey(obj);
    }

    public boolean containsValue(Object obj) {
        init();
        return appContext.containsValue(obj);
    }

    public Set<Entry<String, Object>> entrySet() {
        init();
        return appContext.entrySet();
    }

    public Object get(Object obj) {
        init();
        return appContext.get(obj);
    }

    public boolean isEmpty() {
        init();
        return appContext.isEmpty();
    }

    public Set<String> keySet() {
        init();
        return appContext.keySet();
    }

    public Object put(String str, Object obj) {
        init();
        Scope scope = null;
        try {
            scope = ctxt.getScope(str);
        } catch(IllegalArgumentException ie) {
            // It's okay, MessageContext didn't have this property
        }
        if (scope != null && scope == Scope.HANDLER) {
            throw new IllegalArgumentException(
                    "Cannot overwrite property in HANDLER scope");
        }
        ctxt.put(str, obj);
        ctxt.setScope(str, Scope.APPLICATION);
        return appContext.put(str, obj);
    }

    public void putAll(Map<? extends String, ? extends Object> map) {
        init();
        Set<? extends Entry<? extends String, ? extends Object>> props = map.entrySet();
        for(Entry<? extends String, ? extends Object> prop : props) {
            put(prop.getKey(), prop.getValue());
        }
    }

    public Object remove(Object key) {
        init();
        Scope scope = null;
        try {
            scope = ctxt.getScope((String)key);
        } catch(IllegalArgumentException ie) {
            // It's okay, MessageContext didn't have this property
        }
        if (scope != null && scope == Scope.HANDLER) {
            throw new IllegalArgumentException(
                    "Cannot remove property in HANDLER scope");
        }
        ctxt.remove(key);
        return appContext.remove(key);
    }

    public int size() {
        init();
        return appContext.size();
    }

    public Collection<Object> values() {
        init();
        return appContext.values();
    }

    public void setScope(String name, Scope scope) {

    }

    public Scope getScope(String name) {
        return null;
    }

}
