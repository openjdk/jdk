/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.salver;

import static org.graalvm.compiler.salver.SalverOptions.SalverAddress;
import static org.graalvm.compiler.salver.SalverOptions.SalverPort;

import java.net.InetSocketAddress;

import org.graalvm.compiler.salver.util.ECIDUtil;

public final class Salver {

    /**
     * The Execution Context Identifier is a unique identifier that simplifies the grouping of
     * events created in different DumpHandlers or Threads. It should be added as a special property
     * to all :begin trace events.
     */
    public static final String ECID = ECIDUtil.random();

    private Salver() {
    }

    public static InetSocketAddress getSocketAddress() {
        return new InetSocketAddress(SalverAddress.getValue(), SalverPort.getValue());
    }
}
