/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.pipe;

import java.util.Map;

/**
 * Clones the whole pipeline.
 *
 * <p>
 * Since {@link Pipe}s may form an arbitrary directed graph, someone needs
 * to keep track of isomorphism for a clone to happen correctly. This class
 * serves that role.
 *
 * @deprecated
 *      Use {@link TubeCloner}.
 * @author Kohsuke Kawaguchi
 */
public abstract class PipeCloner extends TubeCloner {
    /**
     * {@link Pipe} version of {@link #clone(Tube)}
     */
    public static Pipe clone(Pipe p) {
        return new PipeClonerImpl().copy(p);
    }

    // no need to be constructed publicly. always use the static clone method.
    /*package*/ PipeCloner(Map<Object,Object> master2copy) {
        super(master2copy);
    }

    /**
     * {@link Pipe} version of {@link #copy(Tube)}
     */
    @SuppressWarnings("unchecked")
        public abstract <T extends Pipe> T copy(T p);


    /**
     * The {@link Pipe} version of {@link #add(Tube, Tube)}.
     */
    public abstract void add(Pipe original, Pipe copy);
}
