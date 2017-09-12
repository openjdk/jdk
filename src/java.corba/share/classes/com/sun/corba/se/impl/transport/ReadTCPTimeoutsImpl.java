/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.transport;

import com.sun.corba.se.spi.transport.ReadTimeouts;

/**
 * @author Charlie Hunt
 */
public class ReadTCPTimeoutsImpl implements ReadTimeouts
{
    private int initial_time_to_wait;
    private int max_time_to_wait;
    private int max_giop_header_time_to_wait;
    private double backoff_factor;

    // constructor
    public ReadTCPTimeoutsImpl(int initial_time,
                            int max_time,
                            int max_giop_header_time,
                            int backoff_percent) {
        this.initial_time_to_wait = initial_time;
        this.max_time_to_wait = max_time;
        this.max_giop_header_time_to_wait = max_giop_header_time;
        this.backoff_factor = 1 + (double)(backoff_percent)/100;
    }

    public int get_initial_time_to_wait() { return initial_time_to_wait; }
    public int get_max_time_to_wait() { return max_time_to_wait; }
    public double get_backoff_factor() { return backoff_factor; }
    public int get_max_giop_header_time_to_wait() {
        return max_giop_header_time_to_wait; }
}

// End of file.
