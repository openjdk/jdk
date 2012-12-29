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

package com.sun.xml.internal.bind.v2.util;

import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
public class TypeCast {
    /**
     * Makes sure that a map contains the right type, and returns it to the desirable type.
     */
    public static <K,V> Map<K,V> checkedCast( Map<?,?> m, Class<K> keyType, Class<V> valueType ) {
        if(m==null)
            return null;
        for (Map.Entry e : m.entrySet()) {
            if(!keyType.isInstance(e.getKey()))
                throw new ClassCastException(e.getKey().getClass().toString());
            if(!valueType.isInstance(e.getValue()))
                throw new ClassCastException(e.getValue().getClass().toString());
        }
        return (Map<K,V>)m;
    }
}
