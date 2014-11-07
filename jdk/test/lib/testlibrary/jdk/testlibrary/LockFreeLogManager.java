/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.testlibrary;

import java.util.Collection;
import java.util.Formatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * A log manager designed specifically to allow collecting ordered log messages
 * in a multi-threaded environment without involving any kind of locking.
 * <p>
 * It is particularly useful in situations when one needs to assert various
 * details about the tested thread state or the locks it hold while also wanting
 * to produce diagnostic log messages.
 * <p>
 * The log manager does not provide any guarantees about the completness of the
 * logs written from different threads - it is up to the caller to make sure
 * {@code toString()} method is called only when all the activity has ceased
 * and the per-thread logs contain all the necessary data.
 *
 * @author Jaroslav Bachorik
 **/
public class LockFreeLogManager {
    private final AtomicInteger logCntr = new AtomicInteger(0);
    private final Collection<Map<Integer, String>> allRecords = new ConcurrentLinkedQueue<>();
    private final ThreadLocal<Map<Integer, String>> records = new ThreadLocal<Map<Integer, String>>() {
        @Override
        protected Map<Integer, String> initialValue() {
            Map<Integer, String> m = new ConcurrentHashMap<>();
            allRecords.add(m);
            return m;
        }

    };

    /**
     * Log a message
     * @param format Message format
     * @param params Message parameters
     */
    public void log(String format, Object ... params) {
        int id = logCntr.getAndIncrement();
        try (Formatter formatter = new Formatter()) {
            records.get().put(id, formatter.format(format, params).toString());
        }
    }

    /**
     * Will generate an aggregated log of chronologically ordered messages.
     * <p>
     * Make sure that you call this method only when all the related threads
     * have finished; otherwise you might get incomplete data.
     *
     * @return An aggregated log of chronologically ordered messages
     */
    @Override
    public String toString() {
        return allRecords.stream()
            .flatMap(m->m.entrySet().stream())
            .sorted((l, r)->l.getKey().compareTo(r.getKey()))
            .map(e->e.getValue())
            .collect(Collectors.joining());
    }
}
