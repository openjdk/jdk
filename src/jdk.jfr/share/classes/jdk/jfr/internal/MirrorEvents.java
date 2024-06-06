/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jdk.jfr.events.DeserializationEvent;
import jdk.jfr.events.ErrorThrownEvent;
import jdk.jfr.events.ExceptionStatisticsEvent;
import jdk.jfr.events.ExceptionThrownEvent;
import jdk.jfr.events.FileForceEvent;
import jdk.jfr.events.FileReadEvent;
import jdk.jfr.events.FileWriteEvent;
import jdk.jfr.events.ProcessStartEvent;
import jdk.jfr.events.SecurityPropertyModificationEvent;
import jdk.jfr.events.SecurityProviderServiceEvent;
import jdk.jfr.events.SerializationMisdeclarationEvent;
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
import jdk.jfr.internal.util.Utils;

/**
 * This class registers all mirror events.
 */
final class MirrorEvents {
    private static final Map<String, Class<? extends MirrorEvent>> mirrorLookup = new ConcurrentHashMap<>();

    // Add mirror event mapping here. See MirrorEvent class for details.
    static {
        register("jdk.internal.event.DeserializationEvent", DeserializationEvent.class);
        register("jdk.internal.event.FileForceEvent", FileForceEvent.class);
        register("jdk.internal.event.FileReadEvent", FileReadEvent.class);
        register("jdk.internal.event.FileWriteEvent", FileWriteEvent.class);
        register("jdk.internal.event.ProcessStartEvent", ProcessStartEvent.class);
        register("jdk.internal.event.SecurityPropertyModificationEvent", SecurityPropertyModificationEvent.class);
        register("jdk.internal.event.SecurityProviderServiceEvent", SecurityProviderServiceEvent.class);
        register("jdk.internal.event.SerializationMisdeclarationEvent", SerializationMisdeclarationEvent.class);
        register("jdk.internal.event.SocketReadEvent", SocketReadEvent.class);
        register("jdk.internal.event.SocketWriteEvent", SocketWriteEvent.class);
        register("jdk.internal.event.ThreadSleepEvent", ThreadSleepEvent.class);
        register("jdk.internal.event.TLSHandshakeEvent", TLSHandshakeEvent.class);
        register("jdk.internal.event.VirtualThreadStartEvent", VirtualThreadStartEvent.class);
        register("jdk.internal.event.VirtualThreadEndEvent", VirtualThreadEndEvent.class);
        register("jdk.internal.event.VirtualThreadPinnedEvent", VirtualThreadPinnedEvent.class);
        register("jdk.internal.event.VirtualThreadSubmitFailedEvent", VirtualThreadSubmitFailedEvent.class);
        register("jdk.internal.event.X509CertificateEvent", X509CertificateEvent.class);
        register("jdk.internal.event.X509ValidationEvent", X509ValidationEvent.class);
        register("jdk.internal.event.ErrorThrownEvent", ErrorThrownEvent.class);
        register("jdk.internal.event.ExceptionStatisticsEvent", ExceptionStatisticsEvent.class);
        register("jdk.internal.event.ExceptionThrownEvent", ExceptionThrownEvent.class);
    };

    private static void register(String eventClassName, Class<? extends MirrorEvent> mirrorClass) {
        mirrorLookup.put(eventClassName, mirrorClass);
    }

    static Class<? extends MirrorEvent> find(Class<? extends jdk.internal.event.Event> eventClass) {
        return find(Utils.isJDKClass(eventClass), eventClass.getName());
    }

    static Class<? extends MirrorEvent> find(boolean bootClassLoader, String name) {
        if (bootClassLoader) {
            return mirrorLookup.get(name);
        }
        return null;
    }
}
