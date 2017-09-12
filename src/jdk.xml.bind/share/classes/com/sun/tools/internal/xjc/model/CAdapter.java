/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.model;

import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.NormalizedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.sun.codemodel.internal.JClass;
import com.sun.tools.internal.xjc.model.nav.EagerNClass;
import com.sun.tools.internal.xjc.model.nav.NClass;
import com.sun.tools.internal.xjc.model.nav.NType;
import com.sun.tools.internal.xjc.model.nav.NavigatorImpl;
import com.sun.tools.internal.xjc.outline.Aspect;
import com.sun.tools.internal.xjc.outline.Outline;
import com.sun.xml.internal.bind.v2.model.core.Adapter;

/**
 * Extended {@link Adapter} for use within XJC.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CAdapter extends Adapter<NType,NClass> {

    /**
     * If non-null, the same as {@link #adapterType} but more conveniently typed.
     */
    private JClass adapterClass1;

    /**
     * If non-null, the same as {@link #adapterType} but more conveniently typed.
     */
    private Class<? extends XmlAdapter> adapterClass2;

    /**
     * When the adapter class is statically known to us.
     *
     * @param copy
     *      true to copy the adapter class into the user package,
     *      or otherwise just refer to the class specified via the
     *      adapter parameter.
     */
    public CAdapter(Class<? extends XmlAdapter> adapter, boolean copy) {
        super(getRef(adapter,copy),NavigatorImpl.theInstance);
        this.adapterClass1 = null;
        this.adapterClass2 = adapter;
    }

    static NClass getRef( final Class<? extends XmlAdapter> adapter, boolean copy ) {
        if(copy) {
            // TODO: this is a hack. the code generation should be defered until
            // the backend. (right now constant generation happens in the front-end)
            return new EagerNClass(adapter) {
                @Override
                public JClass toType(Outline o, Aspect aspect) {
                    return o.addRuntime(adapter);
                }
                public String fullName() {
                    // TODO: implement this method later
                    throw new UnsupportedOperationException();
                }
            };
        } else {
            return NavigatorImpl.theInstance.ref(adapter);
        }
    }

    public CAdapter(JClass adapter) {
        super( NavigatorImpl.theInstance.ref(adapter), NavigatorImpl.theInstance);
        this.adapterClass1 = adapter;
        this.adapterClass2 = null;
    }

    public JClass getAdapterClass(Outline o) {
        if(adapterClass1==null)
            adapterClass1 = o.getCodeModel().ref(adapterClass2);
        return adapterType.toType(o, Aspect.EXPOSED);
    }

    /**
     * Returns true if the adapter is for whitespace normalization.
     * Such an adapter can be ignored when producing a list.
     */
    public boolean isWhitespaceAdapter() {
        return adapterClass2==CollapsedStringAdapter.class || adapterClass2==NormalizedStringAdapter.class;
    }

    /**
     * Returns the adapter class if the adapter type is statically known to XJC.
     * <p>
     * This method is mostly for enabling certain optimized code generation.
     */
    public Class<? extends XmlAdapter> getAdapterIfKnown() {
        return adapterClass2;
    }
}
