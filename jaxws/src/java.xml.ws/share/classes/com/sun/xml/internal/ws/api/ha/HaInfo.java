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

package com.sun.xml.internal.ws.api.ha;

import com.sun.xml.internal.ws.api.message.Packet;

/**
 * This class has HA information
 * <p>
 *
 * This would help a loadbalancer to put the request(in case of a fail-over)
 * on a replica instance that has all the related data. Even if there is no
 * loadbalancer, a backing store could locate the information by directly
 * going to the correct replica instance. This would also help any part of
 * the runtime to know about failover case(and in-turn may invalidate
 * local caches).
 *
 * <p>
 * To achieve this functionality, it carries two pieces of information:
 * <ol>
 * <li>key - Related {@link com.sun.org.glassfish.ha.store.api.BackingStore} keys can
 * use this info for their HashableKey impl. First store creates this object,
 * and subsequent related stores use the same key.
 * <li>replicaInstance - where the related info is replicated
 * </ol>
 *
 * <p>
 * This can be accessed from {@link Packet} using {@link Packet#HA_INFO}
 * property by the runtime. This object is created typically
 * <ul>
 * <li> When a store happens for the first time
 * <li> A subsequent inbound transport creates from cookies
 * <li> A fail-over request stores the data to a different replica
 * </ul>
 *
 * @author Jitendra Kotamraju
 * @since JAX-WS RI 2.2.2
 */
public class HaInfo {
    private final String replicaInstance;
    private final String key;
    private final boolean failOver;

    public HaInfo(String key, String replicaInstance, boolean failOver) {
        this.key = key;
        this.replicaInstance = replicaInstance;
        this.failOver = failOver;
    }

    public String getReplicaInstance() {
        return replicaInstance;
    }

    public String getKey() {
        return key;
    }

    public boolean isFailOver() {
        return failOver;
    }
}
