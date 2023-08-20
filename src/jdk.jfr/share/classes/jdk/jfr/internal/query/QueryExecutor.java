/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.query;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import jdk.jfr.EventType;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.MetadataEvent;

final class QueryExecutor {
    private final List<QueryRun> queryRuns = new ArrayList<>();
    private final List<EventType> eventTypes = new ArrayList<>();
    private final EventStream stream;

    public QueryExecutor(EventStream stream) {
        this(stream, List.of());
    }

    public QueryExecutor(EventStream stream, Query query) {
        this(stream, List.of(query));
    }

    public QueryExecutor(EventStream stream, List<Query> queries) {
        this.stream = stream;
        for (Query query : queries) {
            queryRuns.add(new QueryRun(stream, query));
        }
        stream.setReuse(false);
        stream.setOrdered(true);
        stream.onMetadata(this::onMetadata);
    }

    public List<QueryRun> run() {
        stream.start();
        for (QueryRun run : queryRuns) {
            run.complete();
        }
        return queryRuns;
    }

    private void onMetadata(MetadataEvent e) {
        if (eventTypes.isEmpty()) {
            eventTypes.addAll(e.getEventTypes());
        }
        if (queryRuns.isEmpty()) {
            addQueryRuns();
        }
        for (QueryRun run : queryRuns) {
            run.onMetadata(e);
        }
    }

    private void addQueryRuns() {
        for (EventType type : eventTypes) {
            try {
                Query query = new Query("SELECT * FROM " + type.getName());
                QueryRun run = new QueryRun(stream, query);
                queryRuns.add(run);
            } catch (ParseException pe) {
                // The event name contained whitespace or similar, ignore.
            }
        }
    }

    public List<EventType> getEventTypes() {
        return eventTypes;
    }
}
