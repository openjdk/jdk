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

package com.oracle.webservices.internal.api.message;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.client.RequestContext;
import com.sun.xml.internal.ws.client.ResponseContext;

import javax.xml.ws.WebServiceContext;

import java.util.AbstractMap;
import java.util.Map.Entry;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@link PropertySet} that combines properties exposed from multiple
 * {@link PropertySet}s into one.
 *
 * <p>
 * This implementation allows one {@link PropertySet} to assemble
 * all properties exposed from other "satellite" {@link PropertySet}s.
 * (A satellite may itself be a {@link DistributedPropertySet}, so
 * in general this can form a tree.)
 *
 * <p>
 * This is useful for JAX-WS because the properties we expose to the application
 * are contributed by different pieces, and therefore we'd like each of them
 * to have a separate {@link PropertySet} implementation that backs up
 * the properties. For example, this allows FastInfoset to expose its
 * set of properties to {@link RequestContext} by using a strongly-typed fields.
 *
 * <p>
 * This is also useful for a client-side transport to expose a bunch of properties
 * into {@link ResponseContext}. It simply needs to create a {@link PropertySet}
 * object with methods for each property it wants to expose, and then add that
 * {@link PropertySet} to {@link Packet}. This allows property values to be
 * lazily computed (when actually asked by users), thus improving the performance
 * of the typical case where property values are not asked.
 *
 * <p>
 * A similar benefit applies on the server-side, for a transport to expose
 * a bunch of properties to {@link WebServiceContext}.
 *
 * <p>
 * To achieve these benefits, access to {@link DistributedPropertySet} is slower
 * compared to {@link PropertySet} (such as get/set), while adding a satellite
 * object is relatively fast.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class BaseDistributedPropertySet extends BasePropertySet implements DistributedPropertySet {

    /**
     * All {@link PropertySet}s that are bundled into this {@link PropertySet}.
     */
    private final Map<Class<? extends com.oracle.webservices.internal.api.message.PropertySet>, PropertySet> satellites
        = new IdentityHashMap<Class<? extends com.oracle.webservices.internal.api.message.PropertySet>, PropertySet>();

    private final Map<String, Object> viewthis;

    public BaseDistributedPropertySet() {
        this.viewthis = super.createView();
    }

    @Override
    public void addSatellite(@NotNull PropertySet satellite) {
        addSatellite(satellite.getClass(), satellite);
    }

    @Override
    public void addSatellite(@NotNull Class<? extends com.oracle.webservices.internal.api.message.PropertySet> keyClass, @NotNull PropertySet satellite) {
        satellites.put(keyClass, satellite);
    }

    @Override
    public void removeSatellite(PropertySet satellite) {
        satellites.remove(satellite.getClass());
    }

    public void copySatelliteInto(@NotNull DistributedPropertySet r) {
        for (Map.Entry<Class<? extends com.oracle.webservices.internal.api.message.PropertySet>, PropertySet> entry : satellites.entrySet()) {
            r.addSatellite(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void copySatelliteInto(MessageContext r) {
        copySatelliteInto((DistributedPropertySet)r);
    }

    @Override
    public @Nullable <T extends com.oracle.webservices.internal.api.message.PropertySet> T getSatellite(Class<T> satelliteClass) {
        T satellite = (T) satellites.get(satelliteClass);
        if (satellite != null) {
            return satellite;
        }

        for (PropertySet child : satellites.values()) {
            if (satelliteClass.isInstance(child)) {
                return satelliteClass.cast(child);
            }

            if (DistributedPropertySet.class.isInstance(child)) {
                satellite = DistributedPropertySet.class.cast(child).getSatellite(satelliteClass);
                if (satellite != null) {
                    return satellite;
                }
            }
        }
        return null;
    }

    @Override
    public Map<Class<? extends com.oracle.webservices.internal.api.message.PropertySet>, com.oracle.webservices.internal.api.message.PropertySet> getSatellites() {
        return satellites;
    }

    @Override
    public Object get(Object key) {
        // check satellites
        for (PropertySet child : satellites.values()) {
            if (child.supports(key)) {
                return child.get(key);
            }
        }

        // otherwise it must be the master
        return super.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        // check satellites
        for (PropertySet child : satellites.values()) {
            if(child.supports(key)) {
                return child.put(key,value);
            }
        }

        // otherwise it must be the master
        return super.put(key,value);
    }

    @Override
    public boolean containsKey(Object key) {
        if (viewthis.containsKey(key))
            return true;
        for (PropertySet child : satellites.values()) {
            if (child.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean supports(Object key) {
        // check satellites
        for (PropertySet child : satellites.values()) {
            if (child.supports(key)) {
                return true;
            }
        }

        return super.supports(key);
    }

    @Override
    public Object remove(Object key) {
        // check satellites
        for (PropertySet child : satellites.values()) {
            if (child.supports(key)) {
                return child.remove(key);
            }
        }

        return super.remove(key);
    }

    @Override
    protected void createEntrySet(Set<Entry<String, Object>> core) {
        super.createEntrySet(core);
        for (PropertySet child : satellites.values()) {
            ((BasePropertySet) child).createEntrySet(core);
        }
    }

    protected Map<String, Object> asMapLocal() {
        return viewthis;
    }

    protected boolean supportsLocal(Object key) {
        return super.supports(key);
    }

    class DistributedMapView extends AbstractMap<String, Object> {
        @Override
        public Object get(Object key) {
            for (PropertySet child : satellites.values()) {
                if (child.supports(key)) {
                    return child.get(key);
                }
            }

            return viewthis.get(key);
        }

        @Override
        public int size() {
            int size = viewthis.size();
            for (PropertySet child : satellites.values()) {
                size += child.asMap().size();
            }
            return size;
        }

        @Override
        public boolean containsKey(Object key) {
            if (viewthis.containsKey(key))
                return true;
            for (PropertySet child : satellites.values()) {
                if (child.containsKey(key))
                    return true;
            }
            return false;
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            Set<Entry<String, Object>> entries = new HashSet<Entry<String, Object>>();
            for (PropertySet child : satellites.values()) {
                for (Entry<String,Object> entry : child.asMap().entrySet()) {
                    // the code below is here to avoid entries.addAll(child.asMap().entrySet()); which works differently on JDK6/7
                    // see DMI_ENTRY_SETS_MAY_REUSE_ENTRY_OBJECTS
                    entries.add(new SimpleImmutableEntry<String, Object>(entry.getKey(), entry.getValue()));
                }
            }
            for (Entry<String,Object> entry : viewthis.entrySet()) {
                // the code below is here to avoid entries.addAll(child.asMap().entrySet()); which works differently on JDK6/7
                // see DMI_ENTRY_SETS_MAY_REUSE_ENTRY_OBJECTS
                entries.add(new SimpleImmutableEntry<String, Object>(entry.getKey(), entry.getValue()));
            }

            return entries;
        }

        @Override
        public Object put(String key, Object value) {
            for (PropertySet child : satellites.values()) {
                if (child.supports(key)) {
                    return child.put(key, value);
                }
            }

            return viewthis.put(key, value);
        }

        @Override
        public void clear() {
            satellites.clear();
            viewthis.clear();
        }

        @Override
        public Object remove(Object key) {
            for (PropertySet child : satellites.values()) {
                if (child.supports(key)) {
                    return child.remove(key);
                }
            }

            return viewthis.remove(key);
        }
    }

    @Override
    protected Map<String, Object> createView() {
        return new DistributedMapView();
    }
}
