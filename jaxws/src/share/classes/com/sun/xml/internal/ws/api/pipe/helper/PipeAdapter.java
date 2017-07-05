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

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Fiber;
import com.sun.xml.internal.ws.api.pipe.NextAction;
import com.sun.xml.internal.ws.api.pipe.Pipe;
import com.sun.xml.internal.ws.api.pipe.PipeCloner;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;

/**
 * {@link Tube} that invokes {@link Pipe}.
 *
 * <p>
 * This can be used to make a {@link Pipe} look like a {@link Tube}.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public class PipeAdapter extends AbstractTubeImpl {
    private final Pipe next;

    public static Tube adapt(Pipe p) {
        if (p instanceof Tube) {
            return (Tube) p;
        } else {
            return new PipeAdapter(p);
        }
    }

    public static Pipe adapt(Tube p) {
        if (p instanceof Pipe) {
            return (Pipe) p;
        } else {
            class TubeAdapter extends AbstractPipeImpl {
                private final Tube t;

                public TubeAdapter(Tube t) {
                    this.t = t;
                }

                private TubeAdapter(TubeAdapter that, PipeCloner cloner) {
                    super(that, cloner);
                    this.t = cloner.copy(that.t);
                }

                public Packet process(Packet request) {
                    return Fiber.current().runSync(t,request);
                }

                public Pipe copy(PipeCloner cloner) {
                    return new TubeAdapter(this,cloner);
                }
            }

            return new TubeAdapter(p);
        }
    }


    private PipeAdapter(Pipe next) {
        this.next = next;
    }

    /**
     * Copy constructor
     */
    private PipeAdapter(PipeAdapter that, TubeCloner cloner) {
        super(that,cloner);
        this.next = ((PipeCloner)cloner).copy(that.next);
    }

    /**
     * Uses the current fiber and runs the whole pipe to the completion
     * (meaning everything from now on will run synchronously.)
     */
    public @NotNull NextAction processRequest(@NotNull Packet p) {
        return doReturnWith(next.process(p));
    }

    public @NotNull NextAction processResponse(@NotNull Packet p) {
        throw new IllegalStateException();
    }

    @NotNull
    public NextAction processException(@NotNull Throwable t) {
        throw new IllegalStateException();
    }

    public void preDestroy() {
        next.preDestroy();
    }

    public PipeAdapter copy(TubeCloner cloner) {
        return new PipeAdapter(this,cloner);
    }

    public String toString() {
        return super.toString()+"["+next.toString()+"]";
    }
}
