/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.periodic;

import jdk.internal.event.Event;
import jdk.jfr.EventType;
import jdk.jfr.internal.MetadataRepository;
import jdk.jfr.internal.PlatformEventType;
import jdk.jfr.internal.PrivateAccess;
/**
 * Class for periodic Java events.
 */
final class JavaEventTask extends EventTask {
    private final Runnable runnable;

    public JavaEventTask(Class<? extends Event> eventClass, Runnable runnable) {
        super(toPlatformEventType(eventClass), new LookupKey(runnable));
        this.runnable = runnable;
        if (getEventType().isJVM()) {
            throw new InternalError("Must not be a JVM event");
        }
    }

    private static PlatformEventType toPlatformEventType(Class<? extends Event> eventClass) {
        EventType eventType = MetadataRepository.getInstance().getEventType(eventClass);
        return PrivateAccess.getInstance().getPlatformEventType(eventType);
    }

    @Override
    public void execute(long timestamp, PeriodicType periodicType) {
        runnable.run();
    }
}
