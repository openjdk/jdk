/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http;

import java.net.http.HttpClient;

public enum HttpClientTimerAccess {;

    public static void assertNoResponseTimerEventRegistrations(HttpClient client) {
        assertTimerEventRegistrationCount(client, ResponseTimerEvent.class, 0);
    }

    private static void assertTimerEventRegistrationCount(
            HttpClient client,
            Class<? extends TimeoutEvent> clazz,
            long expectedCount) {
        var facade = assertType(HttpClientFacade.class, client);
        var actualCount = facade.impl.timers().stream().filter(clazz::isInstance).count();
        if (actualCount != 0) {
            throw new AssertionError(
                    "Found %s occurrences of `%s` timer event registrations while expecting %s.".formatted(
                            actualCount, clazz.getCanonicalName(), expectedCount));
        }
    }

    private static <T> T assertType(Class<T> expectedType, Object instance) {
        if (!expectedType.isInstance(instance)) {
            var expectedTypeName = expectedType.getCanonicalName();
            var actualTypeName = instance != null ? instance.getClass().getCanonicalName() : null;
            throw new AssertionError(
                    "Was expecting an instance of type `%s`, found: `%s`".formatted(
                            expectedTypeName, actualTypeName));
        }
        @SuppressWarnings("unchecked")
        T typedInstance = (T) instance;
        return typedInstance;
    }

}
