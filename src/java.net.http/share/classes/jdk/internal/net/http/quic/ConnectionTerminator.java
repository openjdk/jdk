/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Responsible for managing the connection termination of a QUIC connection
 */
public sealed interface ConnectionTerminator permits ConnectionTerminatorImpl {

    /**
     * Instructs the connection terminator to consider the connection as active
     * at the present point in time. The connection terminator will then restart its
     * idle timeout timer from this point in time.
     * <p>
     * This method must be called when an incoming packet is processed successfully
     * or when an ack-eliciting packet is sent by the local endpoint on the connection.
     */
    void markActive();

    /**
     * Terminates the connection, if not already terminated, with the given cause.
     * <p>
     * A connection is terminated only once with a {@link TerminationCause}. However, this method
     * can be called any number of times. If the connection is not already terminated,
     * then this method does the necessary work to terminate the connection. Any subsequent
     * invocations of this method, after the connection has been terminated, will not
     * change the termination cause of the connection.
     *
     * @param cause the termination cause
     */
    void terminate(TerminationCause cause);

    /**
     * Returns {@code true} if the connection is allowed for use, {@code false} otherwise.
     * <p>
     * This method is typically called when a connection that has been idle, is about to be used
     * for handling some request. This method allows for co-ordination between the connection usage
     * and the connection terminator to prevent the connection from being idle timed out when it is
     * about to be used for some request. The connection must only be used if this method
     * returns {@code true}.
     *
     * @return true if the connection can be used, false otherwise
     */
    boolean tryReserveForUse();

    /**
     * Instructs the connection terminator that the application layer allows the
     * connection to stay idle for the given {@code maxIdle} duration. If the QUIC
     * layer has negotiated an idle timeout for the connection, that's lower than
     * the application's {@code maxIdle} duration, then the connection terminator
     * upon noticing absence of traffic over the connection for certain duration,
     * calls the {@code trafficGenerationCheck} to check if the QUIC layer should
     * explicitly generate some traffic to prevent the connection
     * from idle terminating.
     * <p>
     * When the {@code trafficGenerationCheck} is invoked, the application layer
     * must return {@code true} only if explicit traffic generation is necessary
     * to keep the connection alive.
     * <p>
     * If the application layer wishes to never idle terminate the connection, then
     * a {@code maxIdle} duration of {@linkplain Duration#MAX Duration.MAX} is recommended.
     *
     * @param maxIdle                the maximum idle duration of the connection,
     *                               at the application layer
     * @param trafficGenerationCheck the callback that will be invoked by the connection
     *                               terminator to decide if the QUIC layer should generate
     *                               any traffic to prevent the connection from idle terminating
     * @throws NullPointerException     if either {@code maxIdle} or {@code trafficGenerationCheck}
     *                                  is null
     * @throws IllegalArgumentException if {@code maxIdle} is
     *                                  {@linkplain Duration#isNegative() negative} or
     *                                  {@linkplain Duration#isZero() zero}
     */
    void appLayerMaxIdle(Duration maxIdle, Supplier<Boolean> trafficGenerationCheck);
}
