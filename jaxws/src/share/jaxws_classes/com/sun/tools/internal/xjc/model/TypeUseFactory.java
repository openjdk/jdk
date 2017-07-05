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

package com.sun.tools.internal.xjc.model;

import javax.activation.MimeType;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.sun.xml.internal.bind.v2.TODO;
import com.sun.xml.internal.bind.v2.model.core.Adapter;
import com.sun.xml.internal.bind.v2.model.core.ID;

/**
 * Factory methods to create a new {@link TypeUse} from an existing one.
 *
 * @author Kohsuke Kawaguchi
 */
public final class TypeUseFactory {
    private TypeUseFactory() {}

    public static TypeUse makeID( TypeUse t, ID id ) {
        if(t.idUse()!=ID.NONE)
            // I don't think we let users tweak the idness, so
            // this error must indicate an inconsistency within the RI/spec.
            throw new IllegalStateException();
        return new TypeUseImpl( t.getInfo(), t.isCollection(), id, t.getExpectedMimeType(), t.getAdapterUse() );
    }

    public static TypeUse makeMimeTyped( TypeUse t, MimeType mt ) {
        if(t.getExpectedMimeType()!=null)
            // I don't think we let users tweak the idness, so
            // this error must indicate an inconsistency within the RI/spec.
            throw new IllegalStateException();
        return new TypeUseImpl( t.getInfo(), t.isCollection(), t.idUse(), mt, t.getAdapterUse() );
    }

    public static TypeUse makeCollection( TypeUse t ) {
        if(t.isCollection())    return t;
        CAdapter au = t.getAdapterUse();
        if(au!=null && !au.isWhitespaceAdapter()) {
            // we can't process this right now.
            // for now bind to a weaker type
            TODO.checkSpec();
            return CBuiltinLeafInfo.STRING_LIST;
        }
        return new TypeUseImpl( t.getInfo(), true, t.idUse(), t.getExpectedMimeType(), null );
    }

    public static TypeUse adapt(TypeUse t, CAdapter adapter) {
        assert t.getAdapterUse()==null;    // TODO: we don't know how to handle double adapters yet.
        return new TypeUseImpl(t.getInfo(),t.isCollection(),t.idUse(),t.getExpectedMimeType(),adapter);
    }

    /**
     * Creates a new adapter {@link TypeUse} by using the existing {@link Adapter} class.
     */
    public static TypeUse adapt( TypeUse t, Class<? extends XmlAdapter> adapter, boolean copy ) {
        return adapt( t, new CAdapter(adapter,copy) );
    }
}
