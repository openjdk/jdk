/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.protocol ;

// Introduce more information about WHY we are re-trying a request
// so we can properly handle the two cases:
// - BEFORE_RESPONSE means that the retry is caused by
//   something that happened BEFORE the message was sent: either
//   an exception from the SocketFactory, or one from the
//   Client side send_request interceptor point.
// - AFTER_RESPONSE means that the retry is a result either of the
//   request sent to the server (from the response), or from the
//   Client side receive_xxx interceptor point.
public enum RetryType {
    NONE( false ),
    BEFORE_RESPONSE( true ),
    AFTER_RESPONSE( true ) ;

    private final boolean isRetry ;

    RetryType( boolean isRetry ) {
        this.isRetry = isRetry ;
    }

    public boolean isRetry() {
        return this.isRetry ;
    }
} ;

