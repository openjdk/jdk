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

package com.sun.xml.internal.ws.api.pipe;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Interception for {@link Fiber} context switch.
 *
 * <p>
 * Even though pipeline runs asynchronously, sometimes it's desirable
 * to bind some state to the current thread running a fiber. Such state
 * may include security subject (in terms of {@link AccessController#doPrivileged}),
 * or a transaction.
 *
 * <p>
 * This mechanism makes it possible to do such things, by allowing
 * some code to be executed before and after a thread executes a fiber.
 *
 * <p>
 * The design also encapsulates the entire fiber execution in a single
 * opaque method invocation {@link Work#execute}, allowing the use of
 * <tt>finally</tt> block.
 *
 *
 * @author Kohsuke Kawaguchi
 */
public interface FiberContextSwitchInterceptor {
    /**
     * Allows the interception of the fiber execution.
     *
     * <p>
     * This method needs to be implemented like this:
     *
     * <pre>
     * &lt;R,P> R execute( Fiber f, P p, Work&lt;R,P> work ) {
     *   // do some preparation work
     *   ...
     *   try {
     *     // invoke
     *     return work.execute(p);
     *   } finally {
     *     // do some clean up work
     *     ...
     *   }
     * }
     * </pre>
     *
     * <p>
     * While somewhat unintuitive,
     * this interception mechanism enables the interceptor to wrap
     * the whole fiber execution into a {@link AccessController#doPrivileged(PrivilegedAction)},
     * for example.
     *
     * @param f
     *      {@link Fiber} to be executed.
     * @param p
     *      The opaque parameter value for {@link Work}. Simply pass this value to
     *      {@link Work#execute(Object)}.
     * @return
     *      The opaque return value from the the {@link Work}. Simply return
     *      the value from {@link Work#execute(Object)}.
     */
    <R,P> R execute( Fiber f, P p, Work<R,P> work );

    /**
     * Abstraction of the execution that happens inside the interceptor.
     */
    interface Work<R,P> {
        /**
         * Have the current thread executes the current fiber,
         * and returns when it stops doing so.
         *
         * <p>
         * The parameter and the return value is controlled by the
         * JAX-WS runtime, and interceptors should simply treat
         * them as opaque values.
         */
        R execute(P param);
    }
}
