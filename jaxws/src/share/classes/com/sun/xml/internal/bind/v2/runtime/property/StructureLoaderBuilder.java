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
package com.sun.xml.internal.bind.v2.runtime.property;

import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.v2.util.QNameMap;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.ChildLoader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Loader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.StructureLoader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.ValuePropertyLoader;

/**
 * Component that contributes element unmarshallers into
 * {@link StructureLoader}.
 *
 * TODO: think of a better name.
 *
 * @author Bhakti Mehta
 */
public interface StructureLoaderBuilder {
    /**
     * Every Property class has an implementation of buildChildElementUnmarshallers
     * which will fill in the specified {@link QNameMap} by elements that are expected
     * by this property.
     */
    void buildChildElementUnmarshallers(UnmarshallerChain chain, QNameMap<ChildLoader> handlers);

    /**
     * Magic {@link QName} used to store a handler for the text.
     *
     * <p>
     * To support the mixed content model, {@link StructureLoader} can have
     * at most one {@link ValuePropertyLoader} for processing text
     * found amoung elements.
     *
     * This special text handler is put into the {@link QNameMap} parameter
     * of the {@link #buildChildElementUnmarshallers} method by using
     * this magic token as the key.
     */
    public static final QName TEXT_HANDLER = new QName("\u0000","text");

    /**
     * Magic {@link QName} used to store a handler for the rest of the elements.
     *
     * <p>
     * To support the wildcard, {@link StructureLoader} can have
     * at most one {@link Loader} for processing elements
     * that didn't match any of the named elements.
     *
     * This special text handler is put into the {@link QNameMap} parameter
     * of the {@link #buildChildElementUnmarshallers} method by using
     * this magic token as the key.
     */
    public static final QName CATCH_ALL = new QName("\u0000","catchAll");
}
