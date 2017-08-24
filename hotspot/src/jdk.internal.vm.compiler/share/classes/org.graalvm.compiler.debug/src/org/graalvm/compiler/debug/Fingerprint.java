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
package org.graalvm.compiler.debug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionValue;

/**
 * Facility for fingerprinting execution.
 */
public class Fingerprint implements AutoCloseable {

    public static class Options {
        @Option(help = "Enables execution fingerprinting.")//
        public static final OptionValue<Boolean> UseFingerprinting = new OptionValue<>(false);

        @Option(help = "Limit number of events shown in fingerprinting error message.")//
        public static final OptionValue<Integer> FingerprintErrorEventTailLength = new OptionValue<>(50);

        @Option(help = "Fingerprinting event at which to execute breakpointable code.")//
        public static final OptionValue<Integer> FingerprintingBreakpointEvent = new OptionValue<>(-1);
    }

    /**
     * Determines whether fingerprinting is enabled.
     */
    public static final boolean ENABLED = Options.UseFingerprinting.getValue();

    private static final ThreadLocal<Fingerprint> current = ENABLED ? new ThreadLocal<>() : null;

    private final List<String> events;
    private int index;

    /**
     * Creates an object to record a fingerprint.
     */
    public Fingerprint() {
        events = new ArrayList<>();
        index = -1;
    }

    /**
     * Creates an object to verify execution matches a given fingerprint.
     *
     * @param toVerifyAgainst the fingerprint events to verify against
     */
    public Fingerprint(List<String> toVerifyAgainst) {
        this.events = toVerifyAgainst;
        index = 0;
    }

    /**
     * Creates an object to verify execution matches a given fingerprint.
     *
     * @param toVerifyAgainst the fingerprint to verify against
     */
    public Fingerprint(Fingerprint toVerifyAgainst) {
        this(toVerifyAgainst.events);
    }

    public Collection<String> getEvents() {
        return Collections.unmodifiableCollection(events);
    }

    /**
     * Starts fingerprint recording or verification for the current thread. At most one fingerprint
     * object can be active for any thread.
     */
    public Fingerprint open() {
        if (ENABLED) {
            assert current.get() == null;
            current.set(this);
            return this;
        }
        return null;
    }

    /**
     * Finishes fingerprint recording or verification for the current thread.
     */
    @Override
    public void close() {
        if (ENABLED) {
            assert current.get() == this;
            current.set(null);
        }
    }

    private static final int BREAKPOINT_EVENT = Options.FingerprintingBreakpointEvent.getValue();

    /**
     * Submits an execution event for the purpose of recording or verifying a fingerprint. This must
     * only be called if {@link #ENABLED} is {@code true}.
     */
    public static void submit(String format, Object... args) {
        assert ENABLED : "fingerprinting must be enabled (-Dgraal." + Options.UseFingerprinting.getName() + "=true)";
        Fingerprint fingerprint = current.get();
        if (fingerprint != null) {
            int eventId = fingerprint.nextEventId();
            if (eventId == BREAKPOINT_EVENT) {
                // Set IDE breakpoint on the following line and set the relevant
                // system property to debug a fingerprint verification error.
                System.console();
            }
            fingerprint.event(String.format(eventId + ": " + format, args));
        }
    }

    private int nextEventId() {
        return index == -1 ? events.size() : index;
    }

    private static final int MAX_EVENT_TAIL_IN_ERROR_MESSAGE = Options.FingerprintErrorEventTailLength.getValue();

    private String tail() {
        int start = Math.max(index - MAX_EVENT_TAIL_IN_ERROR_MESSAGE, 0);
        return events.subList(start, index).stream().collect(Collectors.joining(String.format("%n")));
    }

    private void event(String entry) {
        if (index == -1) {
            events.add(entry);
        } else {
            if (index > events.size()) {
                throw new InternalError(String.format("%s%nOriginal fingerprint limit reached", tail()));
            }
            String l = events.get(index);
            if (!l.equals(entry)) {
                throw new InternalError(String.format("%s%nFingerprint differs at event %d%nexpected: %s%n  actual: %s", tail(), index, l, entry));
            }
            index++;
        }
    }
}
