/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

import jdk.nashorn.internal.objects.annotations.SpecializedFunction;
import jdk.nashorn.internal.objects.annotations.SpecializedFunction.LinkLogic;

/**
 * This is an interface for classes that need custom linkage logic. This means Native objects
 * that contain optimistic native methods, that need special/extra rules for linking, guards and
 * SwitchPointing, known and internal to the Native object for its linkage
 */
public interface OptimisticBuiltins {

    /**
     * Return an instance of the linking logic we need for a particular LinkLogic
     * subclass, gotten from the compile time annotation of a specialized builtin method
     * No assumptions can be made about the lifetime of the instance. The receiver may
     * keep it as a perpetual final instance field or create new linking logic depending
     * on its current state for each call, depending on if the global state has changed
     * or other factors
     *
     * @param clazz linking logic class
     * @return linking logic instance for this class
     */
    public SpecializedFunction.LinkLogic getLinkLogic(final Class<? extends LinkLogic> clazz);

    /**
     * Does this link logic vary depending on which instance we are working with.
     * Then we have to sort out certain primitives, as they are created as new
     * objects in the wrapFilter by JavaScript semantics. An example of instance only
     * assumptions are switchPoints per instance, as in NativeArray. NativeString is
     * fine, as it's only static.
     *
     * TODO: finer granularity on this, on the function level so certain functions
     * are forbidden only. Currently we don't have enough specialization to bump into this
     *
     * @return true if there are per instance assumptions for the optimism
     */
    public boolean hasPerInstanceAssumptions();

}
