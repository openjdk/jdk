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

package com.sun.xml.internal.ws.api.pipe;

import com.sun.istack.internal.NotNull;

/**
 * Creates a tubeline.
 *
 * <p>
 * This pluggability layer enables the upper layer to
 * control exactly how the tubeline is composed.
 *
 * <p>
 * JAX-WS is going to have its own default implementation
 * when used all by itself, but it can be substituted by
 * other implementations.
 *
 * <p>
 * See {@link TubelineAssemblerFactory} for how {@link TubelineAssembler}s
 * are located.
 *
 *
 * @see com.sun.xml.internal.ws.api.pipe.ClientTubeAssemblerContext
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public interface TubelineAssembler {
    /**
     * Creates a new tubeline for clients.
     *
     * <p>
     * When a JAX-WS client creates a proxy or a {@link javax.xml.ws.Dispatch} from
     * a {@link javax.xml.ws.Service}, JAX-WS runtime internally uses this method
     * to create a new tubeline as a part of the initilization.
     *
     * @param context
     *      Object that captures various contextual information
     *      that can be used to determine the tubeline to be assembled.
     *
     * @return
     *      non-null freshly created tubeline.
     *
     * @throws javax.xml.ws.WebServiceException
     *      if there's any configuration error that prevents the
     *      tubeline from being constructed. This exception will be
     *      propagated into the application, so it must have
     *      a descriptive error.
     */
    @NotNull Tube createClient(@NotNull ClientTubeAssemblerContext context);

    /**
     * Creates a new tubeline for servers.
     *
     * <p>
     * When a JAX-WS server deploys a new endpoint, it internally
     * uses this method to create a new tubeline as a part of the
     * initialization.
     *
     * <p>
     * Note that this method is called only once to set up a
     * 'master tubeline', and it gets {@link Tube#copy(TubeCloner) copied}
     * from it.
     *
     * @param context
     *      Object that captures various contextual information
     *      that can be used to determine the tubeline to be assembled.
     *
     * @return
     *      non-null freshly created tubeline.
     *
     * @throws javax.xml.ws.WebServiceException
     *      if there's any configuration error that prevents the
     *      tubeline from being constructed. This exception will be
     *      propagated into the container, so it must have
     *      a descriptive error.
     *
     */
    @NotNull Tube createServer(@NotNull ServerTubeAssemblerContext context);
}
