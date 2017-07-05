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

import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.sun.xml.internal.bind.v2.model.annotation.AnnotationReader;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;

/**
 * {@link Adapter} that wraps {@link XmlJavaTypeAdapter}.
 *
 * @author Kohsuke Kawaguchi
 */
public class Adapter<TypeT,ClassDeclT> {
    /**
     * The adapter class. Always non-null.
     *
     * A class that derives from {@link javax.xml.bind.annotation.adapters.XmlAdapter}.
     */
    public final ClassDeclT adapterType;

    /**
     * The type that the JAXB can handle natively.
     * The <tt>Default</tt> parameter of <tt>XmlAdapter&lt;Default,Custom></tt>.
     *
     * Always non-null.
     */
    public final TypeT defaultType;

    /**
     * The type that is stored in memory.
     * The <tt>Custom</tt> parameter of <tt>XmlAdapter&lt;Default,Custom></tt>.
     */
    public final TypeT customType;



    public Adapter(
        XmlJavaTypeAdapter spec,
        AnnotationReader<TypeT,ClassDeclT,?,?> reader,
        Navigator<TypeT,ClassDeclT,?,?> nav) {

        this( nav.asDecl(reader.getClassValue(spec,"value")), nav );
    }

    public Adapter(ClassDeclT adapterType,Navigator<TypeT,ClassDeclT,?,?> nav) {
        this.adapterType = adapterType;
        TypeT baseClass = nav.getBaseClass(nav.use(adapterType), nav.asDecl(XmlAdapter.class));

        // because the parameterization of XmlJavaTypeAdapter requires that the class derives from XmlAdapter.
        assert baseClass!=null;

        if(nav.isParameterizedType(baseClass))
            defaultType = nav.getTypeArgument(baseClass,0);
        else
            defaultType = nav.ref(Object.class);

        if(nav.isParameterizedType(baseClass))
            customType = nav.getTypeArgument(baseClass,1);
        else
            customType = nav.ref(Object.class);
    }
}
