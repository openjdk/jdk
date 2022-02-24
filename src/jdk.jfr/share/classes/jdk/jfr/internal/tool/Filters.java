/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordedEvent;

/**
 * Helper class for creating filters.
 */
public class Filters {
    private static final Predicate<RecordedThread> FALSE_THREAD_PREDICATE = e -> false;

    static Predicate<EventType> createCategoryFilter(String filterText) throws UserSyntaxException {
        List<String> filters = explodeFilter(filterText);
        Predicate<EventType> f = eventType -> {
            for (String category : eventType.getCategoryNames()) {
                for (String filter : filters) {
                    if (match(category, filter)) {
                        return true;
                    }
                    if (category.contains(" ") && acronymify(category).equals(filter)) {
                        return true;
                    }
                }
            }
            return false;
        };
        return createCache(f, EventType::getId);
    }

    static Predicate<EventType> createEventTypeFilter(String filterText) throws UserSyntaxException {
        List<String> filters = explodeFilter(filterText);
        Predicate<EventType> f = eventType -> {
            for (String filter : filters) {
                String fullEventName = eventType.getName();
                if (match(fullEventName, filter)) {
                    return true;
                }
                String eventName = fullEventName.substring(fullEventName.lastIndexOf(".") + 1);
                if (match(eventName, filter)) {
                    return true;
                }
            }
            return false;
        };
        return createCache(f, EventType::getId);
    }

    public static <T> Predicate<T> matchAny(List<Predicate<T>> filters) {
        if (filters.isEmpty()) {
            return t -> true;
        }
        if (filters.size() == 1) {
            return filters.get(0);
        }
        return t -> {
            for (Predicate<T> p : filters) {
                if (!p.test(t)) {
                    return false;
                }
            }
            return true;
        };
    }

    static Predicate<RecordedEvent> fromEventType(Predicate<EventType> filter) {
        return e -> filter.test(e.getEventType());
    }

    static Predicate<RecordedEvent> fromRecordedThread(Predicate<RecordedThread> filter) {
        Predicate<RecordedThread> cachePredicate = createCache(filter, RecordedThread::getId);
        return event -> {
            RecordedThread t = event.getThread();
            if (t == null || t.getJavaName() == null) {
                return false;
            }
            return cachePredicate.test(t);
        };
    }

    static Predicate<RecordedThread> createThreadFilter(String filterText) throws UserSyntaxException {
        List<String> filters = explodeFilter(filterText);
        return thread -> {
            String threadName = thread.getJavaName();
            for (String filter : filters) {
                if (match(threadName, filter)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static final <T, X> Predicate<T> createCache(final Predicate<T> filter, Function<T, X> cacheFunction) {
        Map<X, Boolean> cache = new HashMap<>();
        return t -> cache.computeIfAbsent(cacheFunction.apply(t), x -> filter.test(t));
    }

    private static String acronymify(String multipleWords) {
        boolean newWord = true;
        String acronym = "";
        for (char c : multipleWords.toCharArray()) {
            if (newWord) {
                if (Character.isAlphabetic(c) && Character.isUpperCase(c)) {
                    acronym += c;
                }
            }
            newWord = Character.isWhitespace(c);
        }
        return acronym;
    }

    private static boolean match(String text, String filter) {
        if (filter.length() == 0) {
            // empty filter string matches if string is empty
            return text.length() == 0;
        }
        if (filter.charAt(0) == '*') { // recursive check
            filter = filter.substring(1);
            for (int n = 0; n <= text.length(); n++) {
                if (match(text.substring(n), filter))
                    return true;
            }
        } else if (text.length() == 0) {
            // empty string and non-empty filter does not match
            return false;
        } else if (filter.charAt(0) == '?') {
            // eat any char and move on
            return match(text.substring(1), filter.substring(1));
        } else if (filter.charAt(0) == text.charAt(0)) {
            // eat chars and move on
            return match(text.substring(1), filter.substring(1));
        }
        return false;
    }

    private static List<String> explodeFilter(String filter) throws UserSyntaxException {
        List<String> list = new ArrayList<>();
        for (String s : filter.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) {
                list.add(s);
            }
        }
        return list;
    }
}
