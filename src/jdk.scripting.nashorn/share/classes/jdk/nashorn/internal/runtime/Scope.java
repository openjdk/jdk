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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.codegen.CompilerConstants.virtualCallNoLookup;

import java.util.concurrent.atomic.LongAdder;
import jdk.nashorn.internal.codegen.CompilerConstants;

/**
 * A {@link ScriptObject} subclass for objects that act as scope.
 */
public class Scope extends ScriptObject {

    /* This is used to store return state of split functions. */
    private int splitState = -1;

    /** This is updated only in debug mode - counts number of {@code ScriptObject} instances created that are scope */
    private static final LongAdder count = Context.DEBUG ? new LongAdder() : null;

    /** Method handle that points to {@link Scope#getSplitState}. */
    public static final CompilerConstants.Call GET_SPLIT_STATE = virtualCallNoLookup(Scope.class, "getSplitState", int.class);
    /** Method handle that points to {@link Scope#setSplitState(int)}. */
    public static final CompilerConstants.Call SET_SPLIT_STATE = virtualCallNoLookup(Scope.class, "setSplitState", void.class, int.class);

    /**
     * Constructor
     *
     * @param map initial property map
     */
    public Scope(final PropertyMap map) {
        super(map);
        incrementCount();
    }

    /**
     * Constructor
     *
     * @param proto parent scope
     * @param map   initial property map
     */
    public Scope(final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
        incrementCount();
    }

    /**
     * Constructor
     *
     * @param map            property map
     * @param primitiveSpill primitive spill array
     * @param objectSpill    reference spill array
     */
    public Scope(final PropertyMap map, final long[] primitiveSpill, final Object[] objectSpill) {
        super(map, primitiveSpill, objectSpill);
        incrementCount();
    }

    @Override
    public boolean isScope() {
        return true;
    }

    @Override
    boolean hasWithScope() {
        for (ScriptObject obj = this; obj != null; obj = obj.getProto()) {
            if (obj instanceof WithObject) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the scope's split method state.
     *
     * @return current split state
     */
    public int getSplitState() {
        return splitState;
    }

    /**
     * Set the scope's split method state.
     *
     * @param state current split state
     */
    public void setSplitState(final int state) {
        splitState = state;
    }

    /**
     * Get number of {@code Scope} instances created. If not running in debug
     * mode this is always 0.
     *
     * @return number of scope ScriptObjects created
     */
    public static long getScopeCount() {
        return count != null ? count.sum() : 0;
    }

    private static void incrementCount() {
        if (Context.DEBUG) {
            count.increment();
        }
    }
}
