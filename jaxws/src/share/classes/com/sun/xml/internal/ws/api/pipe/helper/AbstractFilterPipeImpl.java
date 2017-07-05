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

import com.sun.xml.internal.ws.api.pipe.Pipe;
import com.sun.xml.internal.ws.api.pipe.PipeCloner;
import com.sun.xml.internal.ws.api.message.Packet;

/**
 * Default implementation of {@link Pipe} that is used as a filter.
 *
 * <p>
 * A filter pipe works on a {@link Packet}, then pass it onto the next pipe.
 *
 *
 * <h2>How do I implement a filter?</h2>
 * <p>
 * Filter {@link Pipe}s are ideal for those components that wish to
 * do some of the followings:
 *
 * <dl>
 * <dt><b>
 * To read an incoming message and perform some work before the
 * application (or more precisely the next pipe sees it)
 * </b>
 * <dd>
 * Implement the {@link #process} method and do some processing before
 * you pass the packet to the next pipe:
 * <pre>
 * process(request) {
 *   doSomethingWith(request);
 *   return next.process(request);
 * }
 * </pre>
 *
 *
 * <dt><b>
 * To intercept an incoming message and prevent the next pipe from seeing it.
 * </b>
 * <dd>
 * Implement the {@link #process} method and do some processing,
 * then do NOT pass the request onto the next pipe.
 * <pre>
 * process(request) {
 *   if(isSomethingWrongWith(request))
 *     return createErrorMessage();
 *   else
 *     return next.proces(request);
 * }
 * </pre>
 *
 * <dt><b>
 * To post process a reply and possibly modify a message:
 * </b>
 * <dd>
 * Implement the {@link #process} method and do some processing,
 * then do NOT pass the request onto the next pipe.
 * <pre>
 * process(request) {
 *   op = request.getMessage().getOperation();
 *   reply = next.proces(request);
 *   if(op is something I care) {
 *     reply = playWith(reply);
 *   }
 *   return reply;
 * }
 * </pre>
 *
 * </dl>
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractFilterPipeImpl extends AbstractPipeImpl {
    /**
     * Next pipe to call.
     */
    protected final Pipe next;

    protected AbstractFilterPipeImpl(Pipe next) {
        this.next = next;
        assert next!=null;
    }

    protected AbstractFilterPipeImpl(AbstractFilterPipeImpl that, PipeCloner cloner) {
        super(that, cloner);
        this.next = cloner.copy(that.next);
        assert next!=null;
    }

    public Packet process(Packet packet) {
        return next.process(packet);
    }

    @Override
    public void preDestroy() {
        next.preDestroy();
    }
}
