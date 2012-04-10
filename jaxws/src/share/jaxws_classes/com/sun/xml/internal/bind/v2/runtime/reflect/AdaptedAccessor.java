/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime.reflect;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.ClassFactory;
import com.sun.xml.internal.bind.v2.model.core.Adapter;
import com.sun.xml.internal.bind.v2.runtime.Coordinator;

/**
 * {@link Accessor} that adapts the value by using {@link Adapter}.
 *
 * @see Accessor#adapt
 * @author Kohsuke Kawaguchi
 */
final class AdaptedAccessor<BeanT,InMemValueT,OnWireValueT> extends Accessor<BeanT,OnWireValueT> {
    private final Accessor<BeanT,InMemValueT> core;
    private final Class<? extends XmlAdapter<OnWireValueT,InMemValueT>> adapter;

    /*pacakge*/ AdaptedAccessor(Class<OnWireValueT> targetType, Accessor<BeanT, InMemValueT> extThis, Class<? extends XmlAdapter<OnWireValueT, InMemValueT>> adapter) {
        super(targetType);
        this.core = extThis;
        this.adapter = adapter;
    }

    @Override
    public boolean isAdapted() {
        return true;
    }

    public OnWireValueT get(BeanT bean) throws AccessorException {
        InMemValueT v = core.get(bean);

        XmlAdapter<OnWireValueT,InMemValueT> a = getAdapter();
        try {
            return a.marshal(v);
        } catch (Exception e) {
            throw new AccessorException(e);
        }
    }

    public void set(BeanT bean, OnWireValueT o) throws AccessorException {
        XmlAdapter<OnWireValueT, InMemValueT> a = getAdapter();
        try {
            core.set(bean, (o == null ? null : a.unmarshal(o)));
        } catch (Exception e) {
            throw new AccessorException(e);
        }
    }

    public Object getUnadapted(BeanT bean) throws AccessorException {
        return core.getUnadapted(bean);
    }

    public void setUnadapted(BeanT bean, Object value) throws AccessorException {
        core.setUnadapted(bean,value);
    }

    /**
     * Sometimes Adapters are used directly by JAX-WS outside any
     * {@link Coordinator}. Use this lazily-created cached
     * {@link XmlAdapter} in such cases.
     */
    private XmlAdapter<OnWireValueT, InMemValueT> staticAdapter;

    private XmlAdapter<OnWireValueT, InMemValueT> getAdapter() {
        Coordinator coordinator = Coordinator._getInstance();
        if(coordinator!=null)
            return coordinator.getAdapter(adapter);
        else {
            synchronized(this) {
                if(staticAdapter==null)
                    staticAdapter = ClassFactory.create(adapter);
            }
            return staticAdapter;
        }
    }
}
