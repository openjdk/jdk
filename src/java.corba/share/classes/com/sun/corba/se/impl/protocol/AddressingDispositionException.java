/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.protocol;

import com.sun.corba.se.impl.protocol.giopmsgheaders.KeyAddr;

/**
 * This exception is thrown while reading GIOP 1.2 Request, LocateRequest
 * to indicate that a TargetAddress disposition is unacceptable.
 * If this exception is caught explicitly, this need to be rethrown. This
 * is eventually handled within RequestPRocessor and an appropriate reply
 * is sent back to the client.
 *
 * GIOP 1.2 allows three dispositions : KeyAddr (ObjectKey), ProfileAddr (ior
 * profile), IORAddressingInfo (IOR). If the ORB does not support the
 * disposition contained in the GIOP Request / LocateRequest 1.2 message,
 * then it sends a Reply / LocateReply indicating the correct disposition,
 * which the client ORB shall use to transparently retry the request
 * with the correct disposition.
 *
 */
public class AddressingDispositionException extends RuntimeException {

    private short expectedAddrDisp = KeyAddr.value;

    public AddressingDispositionException(short expectedAddrDisp) {
        this.expectedAddrDisp = expectedAddrDisp;
    }

    public short expectedAddrDisp() {
        return this.expectedAddrDisp;
    }
}
