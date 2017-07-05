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

package com.sun.xml.internal.ws.api.pipe.helper;

import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Fiber;
import com.sun.xml.internal.ws.api.pipe.NextAction;
import com.sun.xml.internal.ws.api.pipe.Pipe;
import com.sun.xml.internal.ws.api.pipe.PipeCloner;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;

/**
 * Base class for {@link Tube} implementation.
 *
 * <p>
 * This can be also used as a {@link Pipe}, and thus effectively
 * making every {@link Tube} usable as a {@link Pipe}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractTubeImpl implements Tube, Pipe {

    /**
     * Default constructor.
     */
    protected AbstractTubeImpl() {
    }

    /**
     * Copy constructor.
     */
    protected AbstractTubeImpl(AbstractTubeImpl that, TubeCloner cloner) {
        cloner.add(that,this);
    }

    protected final NextAction doInvoke(Tube next, Packet packet) {
        NextAction na = new NextAction();
        na.invoke(next,packet);
        return na;
    }

    protected final NextAction doInvokeAndForget(Tube next, Packet packet) {
        NextAction na = new NextAction();
        na.invokeAndForget(next,packet);
        return na;
    }

    protected final NextAction doReturnWith(Packet response) {
        NextAction na = new NextAction();
        na.returnWith(response);
        return na;
    }

    protected final NextAction doSuspend() {
        NextAction na = new NextAction();
        na.suspend();
        return na;
    }

    protected final NextAction doSuspend(Tube next) {
        NextAction na = new NextAction();
        na.suspend(next);
        return na;
    }

    protected final NextAction doThrow(Throwable t) {
        NextAction na = new NextAction();
        na.throwException(t);
        return na;
    }

    /**
     * "Dual stack" compatibility mechanism.
     * Allows {@link Tube} to be invoked from a {@link Pipe}.
     */
    public Packet process(Packet p) {
        return Fiber.current().runSync(this,p);
    }

    /**
     * Needs to be implemented by the derived class, but we can't make it abstract
     * without upsetting javac.
     */
    public final AbstractTubeImpl copy(PipeCloner cloner) {
        return copy((TubeCloner)cloner);
    }

    public abstract AbstractTubeImpl copy(TubeCloner cloner);
}
