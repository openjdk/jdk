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

package com.sun.xml.internal.ws.server.sei;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractTubeImpl;

/**
 * Base class for {@link com.sun.xml.internal.ws.server.InvokerTube} restored
 * to allow for containers to specify alternate implementations of SEI-based
 * invoking.
 */
public abstract class InvokerTube<T extends Invoker>
        extends AbstractTubeImpl implements InvokerSource<T> {

    protected final T invoker;

    protected InvokerTube(T invoker) {
        this.invoker = invoker;
    }

    /**
     * Copy constructor.
     */
    protected InvokerTube(InvokerTube<T> that, TubeCloner cloner) {
        cloner.add(that,this);
        this.invoker = that.invoker;
    }

    /**
     * Returns the {@link Invoker} object that serves the request.
     */
    public @NotNull T getInvoker(Packet request) {
        return invoker;
    }
}
