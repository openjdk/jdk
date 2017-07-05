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

package com.sun.xml.internal.ws.api;

import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

/**
 * Placeholder for backwards compatibility.
 *
 * @deprecated Use com.oracle.webservices.internal.api.message.PropertySet instead.
 * @author snajper
 */
public abstract class PropertySet extends com.oracle.webservices.internal.api.message.BasePropertySet {
    /**
     * Represents the list of strongly-typed known properties
     * (keyed by property names.)
     *
     * <p>
     * Just giving it an alias to make the use of this class more fool-proof.
     * @deprecated
     */
    protected static class PropertyMap extends com.oracle.webservices.internal.api.message.BasePropertySet.PropertyMap {}

    /**
     * @deprecated
     */
    protected static PropertyMap parse(final Class clazz) {
        com.oracle.webservices.internal.api.message.BasePropertySet.PropertyMap pm = com.oracle.webservices.internal.api.message.BasePropertySet.parse(clazz);
        PropertyMap map = new PropertyMap();
        map.putAll(pm);
        return map;
    }

    /**
     * Gets the name of the property.
     *
     * @param key
     *      This field is typed as {@link Object} to follow the {@link Map#get(Object)}
     *      convention, but if anything but {@link String} is passed, this method
     *      just returns null.
     */
    public Object get(Object key) {
        Accessor sp = getPropertyMap().get(key);
        if(sp!=null)
            return sp.get(this);
        throw new IllegalArgumentException("Undefined property "+key);
    }

    /**
     * Sets a property.
     *
     * <h3>Implementation Note</h3>
     * This method is slow. Code inside JAX-WS should define strongly-typed
     * fields in this class and access them directly, instead of using this.
     *
     * @throws ReadOnlyPropertyException
     *      if the given key is an alias of a strongly-typed field,
     *      and if the name object given is not assignable to the field.
     *
     * @see Property
     */
    public Object put(String key, Object value) {
        Accessor sp = getPropertyMap().get(key);
        if(sp!=null) {
            Object old = sp.get(this);
            sp.set(this,value);
            return old;
        } else {
            throw new IllegalArgumentException("Undefined property "+key);
        }
    }

    public boolean supports(Object key) {
        return getPropertyMap().containsKey(key);
    }

    public Object remove(Object key) {
        Accessor sp = getPropertyMap().get(key);
        if(sp!=null) {
            Object old = sp.get(this);
            sp.set(this,null);
            return old;
        } else {
            throw new IllegalArgumentException("Undefined property "+key);
        }
    }

    protected void createEntrySet(Set<Entry<String,Object>> core) {
        for (final Entry<String, Accessor> e : getPropertyMap().entrySet()) {
            core.add(new Entry<String, Object>() {
                public String getKey() {
                    return e.getKey();
                }

                public Object getValue() {
                    return e.getValue().get(PropertySet.this);
                }

                public Object setValue(Object value) {
                    Accessor acc = e.getValue();
                    Object old = acc.get(PropertySet.this);
                    acc.set(PropertySet.this,value);
                    return old;
                }
            });
        }
    }

    protected abstract PropertyMap getPropertyMap();
}
