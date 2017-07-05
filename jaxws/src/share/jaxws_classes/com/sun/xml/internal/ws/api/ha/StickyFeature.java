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

package com.sun.xml.internal.ws.api.ha;


/**
 * Provides a way to tell the runtime about stickiness of requests. In a
 * HA environment, a client's requests need to land on the same instance so
 * that a {@link com.sun.org.glassfish.ha.store.api.BackingStore} entry for a key is
 * accessed/modified from the same instance.
 *
 * <p>
 * A web service feature may implement this interface. JAX-WS runtime
 * checks if any feature needs stickiness of requests, and if HA is configured
 * ({@link HighAvailabilityProvider#isHaEnvironmentConfigured()}), it will take
 * an appropriate action. For example, in servlet transport, it would create
 * JSESSIONID cookie so that a typical loadbalancer would stick the subsequent
 * client requests to the same instance.
 *
 * @author Jitendra Kotamraju
 * @since JAX-WS RI 2.2.2
 */
public interface StickyFeature {
}
