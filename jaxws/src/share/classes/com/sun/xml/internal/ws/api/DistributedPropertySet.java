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

package com.sun.xml.internal.ws.api;

import com.sun.istack.internal.FinalArrayList;
import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.client.RequestContext;
import com.sun.xml.internal.ws.client.ResponseContext;

import javax.xml.ws.WebServiceContext;
import java.util.Map.Entry;
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
public abstract class DistributedPropertySet extends PropertySet {
    /**
     * All {@link PropertySet}s that are bundled into this {@link PropertySet}.
     */
    private final FinalArrayList<PropertySet> satellites = new FinalArrayList<PropertySet>();

    public void addSatellite(@NotNull PropertySet satellite) {
        satellites.add(satellite);
    }

    public void removeSatellite(@NotNull PropertySet satellite) {
        satellites.remove(satellite);
    }

    public void copySatelliteInto(@NotNull DistributedPropertySet r) {
        r.satellites.addAll(this.satellites);
    }

    @Override
    public Object get(Object key) {
        // check satellites
        for (PropertySet child : satellites) {
            if(child.supports(key))
                return child.get(key);
        }

        // otherwise it must be the master
        return super.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        // check satellites
        for (PropertySet child : satellites) {
            if(child.supports(key))
                return child.put(key,value);
        }

        // otherwise it must be the master
        return super.put(key,value);
    }

    @Override
    public boolean supports(Object key) {
        // check satellites
        for (PropertySet child : satellites) {
            if(child.supports(key))
                return true;
        }

        return super.supports(key);
    }

    @Override
    public Object remove(Object key) {
        // check satellites
        for (PropertySet child : satellites) {
            if(child.supports(key))
                return child.remove(key);
        }

        return super.remove(key);
    }

    @Override
    /*package*/ void createEntrySet(Set<Entry<String, Object>> core) {
        super.createEntrySet(core);
        for (PropertySet child : satellites) {
            child.createEntrySet(core);
        }
    }
}
