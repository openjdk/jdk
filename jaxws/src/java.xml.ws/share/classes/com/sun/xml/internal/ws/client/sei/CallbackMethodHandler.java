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

package com.sun.xml.internal.ws.client.sei;

import com.sun.xml.internal.ws.api.databinding.ClientCallBridge;
import com.sun.xml.internal.ws.model.JavaMethodImpl;
import javax.xml.ws.AsyncHandler;
import javax.xml.ws.WebServiceException;

import java.lang.reflect.Method;
import java.util.concurrent.Future;

/**
 * {@link MethodHandler} that uses {@link AsyncHandler}.
 *
 * @author Kohsuke Kawaguchi
 */
final class CallbackMethodHandler extends AsyncMethodHandler {

    /**
     * Position of the argument that takes {@link AsyncHandler}.
     */
    private final int handlerPos;

    CallbackMethodHandler(SEIStub owner, Method m, int handlerPos) {
        super(owner, m);
        this.handlerPos = handlerPos;
    }

//    CallbackMethodHandler(SEIStub owner, JavaMethodImpl jm, JavaMethodImpl core, int handlerPos) {
//        super(owner,jm,core);
//        this.handlerPos = handlerPos;
//    }

    Future<?> invoke(Object proxy, Object[] args) throws WebServiceException {
        // the spec requires the last argument
        final AsyncHandler handler = (AsyncHandler)args[handlerPos];

        return doInvoke(proxy, args, handler);
    }
}
