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
package com.sun.xml.internal.bind.v2.model.core;

import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.sun.xml.internal.bind.v2.model.annotation.AnnotationReader;
import com.sun.xml.internal.bind.v2.model.impl.ModelBuilder;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;

/**
 * Reference to a type in a model.
 *
 * TODO: isn't there a similarity between this and TypeUse in XJC?
 *
 * @author Kohsuke Kawaguchi
 */
public final class Ref<T,C> {
    /**
     * The type being referenced.
     * <p>
     * If the type is adapted, this field is the same as the adapter's default type.
     */
    public final T type;
    /**
     * If the reference has an adapter, non-null.
     */
    public final Adapter<T,C> adapter;
    /**
     * If the {@link #type} is an array and it is a value list,
     * true.
     */
    public final boolean valueList;

    public Ref(T type) {
        this(type,null,false);
    }

    public Ref(T type, Adapter<T, C> adapter, boolean valueList) {
        this.adapter = adapter;
        if(adapter!=null)
            type=adapter.defaultType;
        this.type = type;
        this.valueList = valueList;
    }

    public Ref(ModelBuilder<T,C,?,?> builder, T type, XmlJavaTypeAdapter xjta, XmlList xl ) {
        this(builder.reader,builder.nav,type,xjta,xl);
    }

    public Ref(AnnotationReader<T,C,?,?> reader,
               Navigator<T,C,?,?> nav,
               T type, XmlJavaTypeAdapter xjta, XmlList xl ) {
        Adapter<T,C> adapter=null;
        if(xjta!=null) {
            adapter = new Adapter<T,C>(xjta,reader,nav);
            type = adapter.defaultType;
        }

        this.type = type;
        this.adapter = adapter;
        this.valueList = xl!=null;
    }
}
