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

package com.sun.xml.internal.bind.v2.runtime.property;


import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimePropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;


/**
 * {@link Property} implementation for multi-value properties
 * (including arrays and collections.)
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
abstract class ArrayProperty<BeanT,ListT,ItemT> extends PropertyImpl<BeanT> {
    protected final Accessor<BeanT,ListT> acc;
    protected final Lister<BeanT,ListT,ItemT,Object> lister;

    protected ArrayProperty(JAXBContextImpl context, RuntimePropertyInfo prop) {
        super(context,prop);

        assert prop.isCollection();
        lister = Lister.create(
            Utils.REFLECTION_NAVIGATOR.erasure(prop.getRawType()),prop.id(),prop.getAdapter());
        assert lister!=null;
        acc = prop.getAccessor().optimize(context);
        assert acc!=null;
    }

    public void reset(BeanT o) throws AccessorException {
        lister.reset(o,acc);
    }

    public final String getIdValue(BeanT bean) {
        // mutli-value property can't be ID
        return null;
    }
}
