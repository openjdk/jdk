/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.scripts;

import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * Empty object class for object-only fields.
 */
public class JO extends ScriptObject {

    private static final PropertyMap map$ = PropertyMap.newMap(JO.class);

    /**
     * Returns the initial property map to be used.
     * @return the initial property map.
     */
    public static PropertyMap getInitialMap() {
        return map$;
    }

    /**
     * Constructor given an initial property map
     *
     * @param map the property map
     */
    public JO(final PropertyMap map) {
        super(map);
    }

    /**
     * Constructor given an initial prototype and the default initial property map.
     *
     * @param proto the prototype object
     */
    public JO(final ScriptObject proto) {
        super(proto, getInitialMap());
    }

    /**
     * Constructor that takes a pre-initialized spill pool. Used by
     * {@link jdk.nashorn.internal.codegen.SpillObjectCreator} and
     * {@link jdk.nashorn.internal.parser.JSONParser} for initializing object literals
     *
     * @param map            property map
     * @param primitiveSpill primitive spill pool
     * @param objectSpill    reference spill pool
     */
    public JO(final PropertyMap map, final long[] primitiveSpill, final Object[] objectSpill) {
        super(map, primitiveSpill, objectSpill);
    }

    /**
     * A method handle of this method is passed to the ScriptFunction constructor.
     *
     * @param map  the property map to use for allocatorMap
     *
     * @return newly allocated ScriptObject
     */
    public static ScriptObject allocate(final PropertyMap map) {
        return new JO(map);
    }
}

