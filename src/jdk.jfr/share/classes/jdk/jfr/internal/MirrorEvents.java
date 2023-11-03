/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal;

import java.util.HashMap;
import java.util.Map;

import jdk.jfr.Event;
import jdk.jfr.events.DeserializationEvent;
import jdk.jfr.events.ProcessStartEvent;
import jdk.jfr.events.SecurityPropertyModificationEvent;
import jdk.jfr.events.SecurityProviderServiceEvent;
import jdk.jfr.events.SocketReadEvent;
import jdk.jfr.events.SocketWriteEvent;
import jdk.jfr.events.TLSHandshakeEvent;
import jdk.jfr.events.ThreadSleepEvent;
import jdk.jfr.events.VirtualThreadEndEvent;
import jdk.jfr.events.VirtualThreadPinnedEvent;
import jdk.jfr.events.VirtualThreadStartEvent;
import jdk.jfr.events.VirtualThreadSubmitFailedEvent;
import jdk.jfr.events.X509CertificateEvent;
import jdk.jfr.events.X509ValidationEvent;

public final class MirrorEvents {
    private static final Class<?>[] mirrorEventClasses = {
        DeserializationEvent.class,
        ProcessStartEvent.class,
        SecurityPropertyModificationEvent.class,
        SecurityProviderServiceEvent.class,
        SocketReadEvent.class,
        SocketWriteEvent.class,
        ThreadSleepEvent.class,
        TLSHandshakeEvent.class,
        VirtualThreadStartEvent.class,
        VirtualThreadEndEvent.class,
        VirtualThreadPinnedEvent.class,
        VirtualThreadSubmitFailedEvent.class,
        X509CertificateEvent.class,
        X509ValidationEvent.class
    };

    private static final Map<String, Class<? extends Event>> mirrorLookup = createLookup();

    public static Class<? extends Event> find(String name) {
        // When <clinit> of this class is executed it may lead
        // to a JVM up call and invocation of this method before
        // the mirrorLookup field has been set. This is fine,
        // mirrors should not be instrumented.
        if (mirrorLookup != null) {
            return mirrorLookup.get(name);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Class<? extends Event>> createLookup() {
        Map<String, Class<? extends Event>> mirrors = new HashMap<>();
        for (Class<?> eventClass : mirrorEventClasses) {
            MirrorEvent me = eventClass.getAnnotation(MirrorEvent.class);
            if (me == null) {
                throw new InternalError("Mirror class must have annotation " + MirrorEvent.class.getName());
            }
            String fullName = me.module() + ":" + me.className();
            mirrors.put(fullName, (Class<? extends Event>) eventClass);
        }
        return mirrors;
    }
}
