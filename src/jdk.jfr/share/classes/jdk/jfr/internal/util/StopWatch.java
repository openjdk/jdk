/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.util;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class StopWatch {
    private record Timing(String name, Instant start) {
    }

    private final List<Timing> timings = new ArrayList<>();

    public void beginQueryValidation() {
        beginTask("query-validation");
    }

    public void beginAggregation() {
        beginTask("aggregation");
    }

    public void beginFormatting() {
        beginTask("formatting");
    }

    public void beginTask(String name) {
        timings.add(new Timing(name, Instant.now()));
    }

    public void finish() {
        beginTask("end");
    }

    @Override
    public String toString() {
        StringJoiner sb = new StringJoiner(", ");
        for (int i = 0; i < timings.size() - 1; i++) {
            Timing current = timings.get(i);
            Timing next = timings.get(i + 1);
            Duration d = current.start().until(next.start());
            sb.add(current.name() + "=" + ValueFormatter.formatDuration(d));
        }
        return sb.toString();
    }
}
