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

package com.sun.xml.internal.ws.assembler.dev;

import java.util.ArrayList;
import java.util.Collection;

import com.sun.xml.internal.ws.api.pipe.Tube;

/**
 * Decorate Tubes during tubeline assembly
 *
 * @since 2.2.7
 */
public class TubelineAssemblyDecorator {
    /**
     * Composite decorator
     * @param decorators decorators
     * @return composite that delegates to a list of decorators
     */
    public static TubelineAssemblyDecorator composite(Iterable<TubelineAssemblyDecorator> decorators) {
        return new CompositeTubelineAssemblyDecorator(decorators);
    }

    /**
     * Decorate client tube
     * @param tube tube
     * @param context client context
     * @return updated tube for tubeline or return tube parameter to no-op
     */
    public Tube decorateClient(Tube tube, ClientTubelineAssemblyContext context) {
        return tube;
    }

    /**
     * Decorate client head tube.  The decorateClient method will have been called first.
     * @param tube tube
     * @param context client context
     * @return updated tube for tubeline or return tube parameter to no-op
     */
    public Tube decorateClientHead(
            Tube tube, ClientTubelineAssemblyContext context) {
        return tube;
    }

    /**
     * Decorate client tail tube.  The decorateClient method will have been called first.
     * @param tube tube
     * @param context client context
     * @return updated tube for tubeline or return tube parameter to no-op
     */
    public Tube decorateClientTail(
            Tube tube,
            ClientTubelineAssemblyContext context) {
        return tube;
    }

    /**
     * Decorate server tube
     * @param tube tube
     * @param context server context
     * @return updated tube for tubeline or return tube parameter to no-op
     */
    public Tube decorateServer(Tube tube, ServerTubelineAssemblyContext context) {
        return tube;
    }

    /**
     * Decorate server tail tube.  The decorateServer method will have been called first.
     * @param tube tube
     * @param context server context
     * @return updated tube for tubeline or return tube parameter to no-op
     */
    public Tube decorateServerTail(
            Tube tube, ServerTubelineAssemblyContext context) {
        return tube;
    }

    /**
     * Decorate server head tube.  The decorateServer method will have been called first
     * @param tube tube
     * @param context server context
     * @return updated tube for tubeline or return tube parameter to no-op
     */
    public Tube decorateServerHead(
            Tube tube,
            ServerTubelineAssemblyContext context) {
        return tube;
    }

    private static class CompositeTubelineAssemblyDecorator extends TubelineAssemblyDecorator {
        private Collection<TubelineAssemblyDecorator> decorators = new ArrayList<TubelineAssemblyDecorator>();

        public CompositeTubelineAssemblyDecorator(Iterable<TubelineAssemblyDecorator> decorators) {
            for (TubelineAssemblyDecorator decorator : decorators) {
                this.decorators.add(decorator);
            }
        }

        @Override
        public Tube decorateClient(Tube tube, ClientTubelineAssemblyContext context) {
            for (TubelineAssemblyDecorator decorator : decorators) {
                tube = decorator.decorateClient(tube, context);
            }
            return tube;
        }

        @Override
        public Tube decorateClientHead(
                Tube tube, ClientTubelineAssemblyContext context) {
            for (TubelineAssemblyDecorator decorator : decorators) {
                tube = decorator.decorateClientHead(tube, context);
            }
            return tube;
        }

        @Override
        public Tube decorateClientTail(
                Tube tube,
                ClientTubelineAssemblyContext context) {
            for (TubelineAssemblyDecorator decorator : decorators) {
                tube = decorator.decorateClientTail(tube, context);
            }
            return tube;
        }

        public Tube decorateServer(Tube tube, ServerTubelineAssemblyContext context) {
            for (TubelineAssemblyDecorator decorator : decorators) {
                tube = decorator.decorateServer(tube, context);
            }
            return tube;
        }

        @Override
        public Tube decorateServerTail(
                Tube tube, ServerTubelineAssemblyContext context) {
            for (TubelineAssemblyDecorator decorator : decorators) {
                tube = decorator.decorateServerTail(tube, context);
            }
            return tube;
        }

        @Override
        public Tube decorateServerHead(
                Tube tube,
                ServerTubelineAssemblyContext context) {
            for (TubelineAssemblyDecorator decorator : decorators) {
                tube = decorator.decorateServerHead(tube, context);
            }
            return tube;
        }
    }
}
