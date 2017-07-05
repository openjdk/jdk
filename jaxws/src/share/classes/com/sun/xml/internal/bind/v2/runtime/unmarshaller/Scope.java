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

package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;

import org.xml.sax.SAXException;

/**
 * Holds the information about packing scope.
 *
 * <p>
 * When no packing is started yet, all the fields should be set to null.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Scope<BeanT,PropT,ItemT,PackT> {

    public final UnmarshallingContext context;

    private BeanT bean;
    private Accessor<BeanT,PropT> acc;
    private PackT pack;
    private Lister<BeanT,PropT,ItemT,PackT> lister;

    Scope(UnmarshallingContext context) {
        this.context = context;
    }

    /**
     * Returns true if this scope object is filled by a packing in progress.
     */
    public boolean hasStarted() {
        return bean!=null;
    }

    /**
     * Initializes all the fields to null.
     */
    public void reset() {
        if(bean==null) {
            // already initialized
            assert clean();
            return;
        }

        bean = null;
        acc = null;
        pack = null;
        lister = null;
    }

    /**
     * Finishes up the current packing in progress (if any) and
     * resets this object.
     */
    public void finish() throws AccessorException {
        if(hasStarted()) {
            lister.endPacking(pack,bean,acc);
            reset();
        }
        assert clean();
    }

    private boolean clean() {
        return bean==null && acc==null && pack==null && lister==null;
    }

    /**
     * Adds a new item to this packing scope.
     */
    public void add( Accessor<BeanT,PropT> acc, Lister<BeanT,PropT,ItemT,PackT> lister, ItemT value) throws SAXException{
        try {
            if(!hasStarted()) {
                this.bean = (BeanT)context.getCurrentState().target;
                this.acc = acc;
                this.lister = lister;
                this.pack = lister.startPacking(bean,acc);
            }

            lister.addToPack(pack,value);
        } catch (AccessorException e) {
            Loader.handleGenericException(e,true);
            // recover from this error by ignoring future items.
            this.lister = Lister.getErrorInstance();
            this.acc = Accessor.getErrorInstance();
        }
    }

    /**
     * Starts the packing scope, without adding any item.
     *
     * This allows us to return an empty pack, thereby allowing the user
     * to distinguish empty array vs null array.
     */
    public void start( Accessor<BeanT,PropT> acc, Lister<BeanT,PropT,ItemT,PackT> lister) throws SAXException{
        try {
            if(!hasStarted()) {
                this.bean = (BeanT)context.getCurrentState().target;
                this.acc = acc;
                this.lister = lister;
                this.pack = lister.startPacking(bean,acc);
            }
        } catch (AccessorException e) {
            Loader.handleGenericException(e,true);
            // recover from this error by ignoring future items.
            this.lister = Lister.getErrorInstance();
            this.acc = Accessor.getErrorInstance();
        }
    }
}
