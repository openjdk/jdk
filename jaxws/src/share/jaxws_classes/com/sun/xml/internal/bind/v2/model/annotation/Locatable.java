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

package com.sun.xml.internal.bind.v2.model.annotation;

import com.sun.xml.internal.bind.v2.runtime.Location;

/**
 * {@link Location} that is chained.
 *
 * <p>
 * {@link Locatable} forms a tree structure, where each {@link Locatable}
 * points back to the upstream {@link Locatable}.
 * For example, imagine {@link Locatable} X that points to a particular annotation,
 * whose upstream is {@link Locatable} Y, which points to a particular method
 * (on which the annotation is put), whose upstream is {@link Locatable} Z,
 * which points to a particular class (in which the method is defined),
 * whose upstream is {@link Locatable} W,
 * which points to another class (which refers to the class Z), and so on.
 *
 * <p>
 * This chain will be turned into a list when we report the error to users.
 * This allows them to know where the error happened
 * and why that place became relevant.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Locatable {
    /**
     * Gets the upstream {@link Location} information.
     *
     * @return
     *      can be null.
     */
    Locatable getUpstream();

    /**
     * Gets the location object that this object points to.
     *
     * This operation could be inefficient and costly.
     */
    Location getLocation();
}
