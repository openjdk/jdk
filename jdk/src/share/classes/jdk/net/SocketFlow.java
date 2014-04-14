/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.net;

import java.lang.annotation.Native;

/**
 * Represents the service level properties for the platform specific socket
 * option {@link ExtendedSocketOptions#SO_FLOW_SLA}.
 * <p>
 * The priority and bandwidth parameters must be set before
 * setting the socket option.
 * <p>
 * When the {@code SO_FLOW_SLA} option is set then it may not take effect
 * immediately. If the value of the socket option is obtained with
 * {@code getOption()} then the status may be returned as {@code INPROGRESS}
 * until it takes effect. The priority and bandwidth values are only valid when
 * the status is returned as OK.
 * <p>
 * When a security manager is installed, a {@link NetworkPermission}
 * is required to set or get this option.
 *
 * @since 1.9
 */
@jdk.Exported
public class SocketFlow {

    private static final int UNSET = -1;
    @Native public static final int NORMAL_PRIORITY = 1;
    @Native public static final int HIGH_PRIORITY = 2;

    private int priority = NORMAL_PRIORITY;

    private long bandwidth = UNSET;

    private Status status = Status.NO_STATUS;

    private SocketFlow() {}

    /**
     * Enumeration of the return values from the SO_FLOW_SLA
     * socket option. Both setting and getting the option return
     * one of these statuses, which reflect the state of socket's
     * flow.
     *
     * @since 1.9
     */
    @jdk.Exported
    public enum Status {
        /**
         * Set or get socket option has not been called yet. Status
         * values can only be retrieved after calling set or get.
         */
        NO_STATUS,
        /**
         * Flow successfully created.
         */
        OK,
        /**
         * Caller has no permission to create flow.
         */
        NO_PERMISSION,
        /**
         * Flow can not be created because socket is not connected.
         */
        NOT_CONNECTED,
        /**
         * Flow creation not supported for this socket.
         */
        NOT_SUPPORTED,
        /**
         * A flow already exists with identical attributes.
         */
        ALREADY_CREATED,
        /**
         * A flow is being created.
         */
        IN_PROGRESS,
        /**
         * Some other unspecified error.
         */
        OTHER
    }

    /**
     * Creates a new SocketFlow that can be used to set the SO_FLOW_SLA
     * socket option and create a socket flow.
     */
    public static SocketFlow create() {
        return new SocketFlow();
    }

    /**
     * Sets this SocketFlow's priority. Must be either NORMAL_PRIORITY
     * HIGH_PRIORITY. If not set, a flow's priority is normal.
     *
     * @throws IllegalArgumentException if priority is not NORMAL_PRIORITY or
     *         HIGH_PRIORITY.
     */
    public SocketFlow priority(int priority) {
        if (priority != NORMAL_PRIORITY && priority != HIGH_PRIORITY) {
            throw new IllegalArgumentException("invalid priority");
        }
        this.priority = priority;
        return this;
    }

    /**
     * Sets this SocketFlow's bandwidth. Must be greater than or equal to zero.
     * A value of zero drops all packets for the socket.
     *
     * @throws IllegalArgumentException if bandwidth is less than zero.
     */
    public SocketFlow bandwidth(long bandwidth) {
        if (bandwidth < 0) {
            throw new IllegalArgumentException("invalid bandwidth");
        } else {
            this.bandwidth = bandwidth;
        }
        return this;
    }

    /**
     * Returns this SocketFlow's priority.
     */
    public int priority() {
        return priority;
    }

    /**
     * Returns this SocketFlow's bandwidth.
     *
     * @return this SocketFlow's bandwidth, or {@code -1} if status is not OK.
     */
    public long bandwidth() {
        return bandwidth;
    }

    /**
     * Returns the Status value of this SocketFlow. NO_STATUS is returned
     * if the object was not used in a call to set or get the option.
     */
    public Status status() {
        return status;
    }
}
