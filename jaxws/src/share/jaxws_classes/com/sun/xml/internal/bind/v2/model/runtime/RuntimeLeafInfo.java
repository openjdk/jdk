/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.model.runtime;

import java.lang.reflect.Type;

import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.v2.model.core.LeafInfo;
import com.sun.xml.internal.bind.v2.runtime.Transducer;

/**
 * @author Kohsuke Kawaguchi
 */
public interface RuntimeLeafInfo extends LeafInfo<Type,Class>, RuntimeNonElement {
    /**
     * {@inheritDoc}
     *
     * @return
     *      always non-null.
     */
    <V> Transducer<V> getTransducer();

    /**
     * The same as {@link #getType()} but returns the type as a {@link Class}.
     * <p>
     * Note that the returned {@link Class} object does not necessarily represents
     * a class declaration. It can be primitive types.
     */
    Class getClazz();

    /**
     * Returns all the type names recognized by this type for unmarshalling.
     *
     * <p>
     * While conceptually this method belongs to {@link RuntimeNonElement},
     * if we do that we have to put a lot of dummy implementations everywhere,
     * so it's placed here, where it's actually needed.
     *
     * @return
     *      Always non-null. Do not modify the returned array.
     */
    QName[] getTypeNames();
}
