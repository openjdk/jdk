/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.xml.internal.ws.client;

import com.sun.xml.internal.ws.util.CompletedFuture;

import javax.xml.ws.AsyncHandler;
import javax.xml.ws.Response;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * {@link Response} implementation.
 *
 * @author Kohsuke Kawaguchi
 * @author Kathy Walsh
 */
public final class ResponseImpl<T> extends FutureTask<T> implements Response<T>, ResponseContextReceiver {

    /**
     * Optional {@link AsyncHandler} that gets invoked
     * at the completion of the task.
     */
    private final AsyncHandler<T> handler;
    private ResponseContext responseContext;

    private final Callable<T> callable;

    /**
     *
     * @param callable
     *      This {@link Callable} is executed asynchronously.
     * @param handler
     *      Optional {@link AsyncHandler} to invoke at the end
     *      of the processing. Can be null.
     */
    public ResponseImpl(Callable<T> callable, AsyncHandler<T> handler) {
        super(callable);
        this.callable = callable;
        this.handler = handler;
    }

    @Override
    public void run() {
        // override so that we call set()
        try {
            set(callable.call(), null);
        } catch (Throwable t) {
            set(null, t);
        }
    }

    protected void set(final T v, final Throwable t) {
        // call the handler before we mark the future as 'done'
        if (handler!=null) {
            try {
                /**
                 * {@link Response} object passed into the callback.
                 * We need a separate {@link Future} because we don't want {@link ResponseImpl}
                 * to be marked as 'done' before the callback finishes execution.
                 * (That would provide implicit synchronization between the application code
                 * in the main thread and the callback code, and is compatible with the JAX-RI 2.0 FCS.
                 */
                class CallbackFuture<T> extends CompletedFuture<T> implements Response<T> {
                    public CallbackFuture(T v, Throwable t) {
                        super(v, t);
                    }

                    public Map<String, Object> getContext() {
                        return ResponseImpl.this.getContext();
                    }
                }
                handler.handleResponse(new CallbackFuture<T>(v, t));
            } catch (Throwable e) {
                super.setException(e);
                return;
            }
        }
        if (t != null) {
            super.setException(t);
        } else {
            super.set(v);
        }
    }

    public ResponseContext getContext() {
        return responseContext;
    }

    public void setResponseContext(ResponseContext rc) {
        responseContext = rc;
    }
}
