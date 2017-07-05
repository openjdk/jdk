/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.client;

import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.Dispatch;

import com.sun.xml.internal.ws.api.pipe.ThrowableContainerPropertySet;

/**
 * When using {@link Dispatch}<{@link Packet}> and the invocation completes with a Throwable, it is
 * useful to be able to inspect the Packet in addition to the Throwable as the Packet contains
 * meta-data about the request and/or response.  However, the default behavior is that the caller
 * only receives the Throwable.
 *
 * When an instance of this feature is enabled on the binding, any Throwable generated will be available
 * on the Packet on the satellite {@link ThrowableContainerPropertySet}.
 *
 * @see ThrowableContainerPropertySet
 */
public class ThrowableInPacketCompletionFeature extends WebServiceFeature {

    public ThrowableInPacketCompletionFeature() {
        this.enabled = true;
    }

    @Override
    public String getID() {
        return ThrowableInPacketCompletionFeature.class.getName();
    }

}
