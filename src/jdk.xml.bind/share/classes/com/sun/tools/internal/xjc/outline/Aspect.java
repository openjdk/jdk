/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.outline;

import com.sun.tools.internal.xjc.generator.bean.ImplStructureStrategy;

/**
 * Sometimes a single JAXB-generated bean spans across multiple Java classes/interfaces.
 * We call them "aspects of a bean".
 *
 * <p>
 * This is an enumeration of all possible aspects.
 *
 * @author Kohsuke Kawaguchi
 *
 * TODO: move this to the model package. We cannot do this before JAXB3 because of old plugins
 */
public enum Aspect {
    /**
     * The exposed part of the bean.
     * <p>
     * This corresponds to the content interface when we are geneting one.
     * This would be the same as the {@link #IMPLEMENTATION} when we are
     * just generating beans.
     *
     * <p>
     * This could be an interface, or it could be a class.
     *
     * We don't have any other {@link ImplStructureStrategy} at this point,
     * but generally you can't assume anything about where this could be
     * or whether that's equal to {@link #IMPLEMENTATION}.
     */
    EXPOSED,
    /**
     * The part of the bean that holds all the implementations.
     *
     * <p>
     * This is always a class, never an interface.
     */
    IMPLEMENTATION
}
