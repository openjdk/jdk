/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.threaddump;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.test.lib.json.JSONValue;

/**
 * Represents a thread dump that is obtained by parsing JSON text. A thread dump in JSON
 * format is generated with the {@code com.sun.management.HotSpotDiagnosticMXBean} API or
 * using {@code jcmd <pid> Thread.dump_to_file -format=json <file>}. The thread dump
 * format is documented in {@code
 * src/jdk.management/share/classes/com/sun/management/doc-files/threadDump.schema.json}.
 *
 * <p> The following is an example thread dump that is parsed by this class. Many of the
 * objects are collapsed to reduce the size.
 *
 * <pre>{@code
 * {
 *   "threadDump": {
 *     "formatVersion": 2,
 *     "processId": 63406,
 *     "time": "2026-03-25T09:20:08.591503Z",
 *     "runtimeVersion": "27",
 *     "threadContainers": [
 *       {
 *         "container": "<root>",
 *         "parent": null,
 *         "owner": null,
 *         "threads": [
 *          {
 *            "tid": 1,
 *            "name": "main",
 *            "stack": [...]
 *          },
 *          {
 *            "tid": 8,
 *            "name": "Reference Handler",
 *            "state": "RUNNABLE",
 *            "stack": [
 *               "java.base\/java.lang.ref.Reference.waitForReferencePendingList(Native Method)",
 *               "java.base\/java.lang.ref.Reference.processPendingReferences(Reference.java:245)",
 *               "java.base\/java.lang.ref.Reference$ReferenceHandler.run(Reference.java:207)"
 *            ]
 *          },
 *          {"name": "Finalizer"...},
 *          {"name": "Signal Dispatcher"...},
 *          {"name": "Common-Cleaner"...},
 *          {"name": "Monitor Ctrl-Break"...},
 *          {"name": "Notification Thread"...}
 *         ],
 *         "threadCount": 7
 *       },
 *       {
 *         "container": "ForkJoinPool.commonPool\/jdk.internal.vm.SharedThreadContainer@56aac163",
 *         "parent": "<root>",
 *         "owner": null,
 *         "threads": [...],
 *         "threadCount": 1
 *       },
 *       {
 *         "container": "java.util.concurrent.ThreadPoolExecutor@20322d26\/jdk.internal.vm.SharedThreadContainer@184f6be2",
 *         "parent": "<root>",
 *         "owner": null,
 *         "threads": [...],
 *         "threadCount": 1
 *       }
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <p> The following is an example using this class to print the tree of thread containers
 * (grouping of threads) and the threads in each container:
 *
 * <pre>{@code
 *    void printThreadDump(Path file) throws IOException {
 *         String json = Files.readString(file);
 *         ThreadDump dump = ThreadDump.parse(json);
 *         printThreadContainer(dump.rootThreadContainer(), 0);
 *     }
 *
 *     void printThreadContainer(ThreadDump.ThreadContainer container, int indent) {
 *         out.printf("%s%s%n", " ".repeat(indent), container);
 *         container.threads().forEach(t -> out.printf("%s%s%n", " ".repeat(indent), t.name()));
 *         container.children().forEach(c -> printThreadContainer(c, indent + 2));
 *     }
 * }</pre>
 */
public final class ThreadDump {
    private final ThreadContainer rootThreadContainer;
    private final Map<String, ThreadContainer> nameToThreadContainer;
    private final JSONValue threadDumpObj;

    private ThreadDump(ThreadContainer rootThreadContainer,
                       Map<String, ThreadContainer> nameToThreadContainer,
                       JSONValue threadDumpObj) {
        this.rootThreadContainer = rootThreadContainer;
        this.nameToThreadContainer = nameToThreadContainer;
        this.threadDumpObj = threadDumpObj;
    }

    /**
     * Represents an element in the threadDump/threadContainers array.
     */
    public static class ThreadContainer {
        private final String name;
        private final ThreadContainer parent;
        private final Set<ThreadContainer> children = new HashSet<>();
        private final JSONValue containerObj;

        ThreadContainer(String name, ThreadContainer parent, JSONValue containerObj) {
            this.name = name;
            this.parent = parent;
            this.containerObj = containerObj;
        }

        /**
         * Add a child thread container.
         */
        void addChild(ThreadContainer container) {
            children.add(container);
        }

        /**
         * Returns the thread container name.
         */
        public String name() {
            return name;
        }

        /**
         * Return the thread identifier of the owner or empty OptionalLong if not owned.
         */
        public OptionalLong owner() {
            return containerObj.get("owner")  // number or null
                    .valueOrNull()
                    .map(v -> OptionalLong.of(v.asLong()))
                    .orElse(OptionalLong.empty());
        }

        /**
         * Returns the parent thread container or empty Optional if this is the root.
         */
        public Optional<ThreadContainer> parent() {
            return Optional.ofNullable(parent);
        }

        /**
         * Returns a stream of the children thread containers.
         */
        public Stream<ThreadContainer> children() {
            return children.stream();
        }

        /**
         * Returns a stream of {@code ThreadInfo} objects for the threads in this container.
         */
        public Stream<ThreadInfo> threads() {
            return containerObj.get("threads")
                    .elements()
                    .stream()
                    .map(ThreadInfo::new);
        }

        /**
         * Finds the thread in this container with the given thread identifier.
         */
        public Optional<ThreadInfo> findThread(long tid) {
            return threads()
                    .filter(ti -> ti.tid() == tid)
                    .findAny();
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ThreadContainer other) {
                return name.equals(other.name);
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Represents an element in the threadDump/threadContainers/threads array.
     */
    public static final class ThreadInfo {
        private final long tid;
        private final JSONValue threadObj;

        ThreadInfo(JSONValue threadObj) {
            this.tid = threadObj.get("tid").asLong();
            this.threadObj = threadObj;
        }

        /**
         * Returns the thread identifier.
         */
        public long tid() {
            return tid;
        }

        /**
         * Returns the thread name.
         */
        public String name() {
            return threadObj.get("name").asString();
        }

        /**
         * Returns the thread state.
         */
        public String state() {
            return threadObj.get("state").asString();
        }

        /**
         * Returns true if virtual thread.
         */
        public boolean isVirtual() {
            return threadObj.getOrAbsent("virtual")
                    .map(JSONValue::asBoolean)
                    .orElse(false);
        }

        /**
         * Returns the thread's parkBlocker or null.
         */
        public String parkBlocker() {
            return threadObj.getOrAbsent("parkBlocker")
                    .map(v -> v.get("object").asString())
                    .orElse(null);
        }

        /**
         * Returns the owner of the parkBlocker if the parkBlocker is an AbstractOwnableSynchronizer.
         */
        public OptionalLong parkBlockerOwner() {
            return threadObj.getOrAbsent("parkBlocker")
                    .map(v -> OptionalLong.of(v.get("owner").asLong()))
                    .orElse(OptionalLong.empty());
        }

        /**
         * Returns the object that the thread is blocked entering its monitor or null.
         */
        public String blockedOn() {
            return threadObj.getOrAbsent("blockedOn")
                    .map(JSONValue::asString)
                    .orElse(null);
        }

        /**
         * Return the object that is the thread is waiting on with Object.wait or null.
         */
        public String waitingOn() {
            return threadObj.getOrAbsent("waitingOn")
                    .map(JSONValue::asString)
                    .orElse(null);
        }

        /**
         * Returns the thread stack.
         */
        public Stream<String> stack() {
            return threadObj.get("stack")
                    .elements()
                    .stream()
                    .map(JSONValue::asString);
        }

        /**
         * Return a map of monitors owned.
         */
        public Map<Integer, List<String>> ownedMonitors() {
            Map<Integer, List<String>> result = new HashMap<>();
            threadObj.getOrAbsent("monitorsOwned")
                    .map(JSONValue::elements)
                    .orElse(List.of())
                    .forEach(e -> {
                        int depth = e.get("depth").asInt();
                        List<String> locks = e.get("locks")
                                .elements()
                                .stream()
                                .map(v -> v.valueOrNull()  // string or null
                                        .map(JSONValue::asString)
                                        .orElse(null))
                                .toList();
                        result.computeIfAbsent(depth, _ -> new ArrayList<>()).addAll(locks);
                    });

            return result;
        }

        /**
         * If the thread is a mounted virtual thread, return the thread identifier of
         * its carrier.
         */
        public OptionalLong carrier() {
            return threadObj.getOrAbsent("carrier")
                    .map(v -> OptionalLong.of(v.asLong()))
                    .orElse(OptionalLong.empty());
        }

        @Override
        public int hashCode() {
            return Long.hashCode(tid);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ThreadInfo other) {
                return this.tid == other.tid;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("#");
            sb.append(tid);
            String name = name();
            if (name.length() > 0) {
                sb.append(",")
                  .append(name);
            }
            return sb.toString();
        }
    }

    /**
     * Returns the value of threadDump/processId.
     */
    public long processId() {
        return threadDumpObj.get("processId").asLong(); }

    /**
     * Returns the value of threadDump/time.
     */
    public String time() {
        return threadDumpObj.get("time").asString();
    }

    /**
     * Returns the value of threadDump/runtimeVersion.
     */
    public String runtimeVersion() {
        return threadDumpObj.get("runtimeVersion").asString();
    }

    /**
     * Returns the root container in the threadDump/threadContainers array.
     */
    public ThreadContainer rootThreadContainer() {
        return rootThreadContainer;
    }

    /**
     * Finds a container in the threadDump/threadContainers array with the given name.
     */
    public Optional<ThreadContainer> findThreadContainer(String name) {
        ThreadContainer container = nameToThreadContainer.get(name);
        if (container == null) {
            // may be name/identity format
            container = nameToThreadContainer.entrySet()
                    .stream()
                    .filter(e -> e.getKey().startsWith(name + "/"))
                    .map(e -> e.getValue())
                    .findAny()
                    .orElse(null);
        }
        return Optional.of(container);
    }

    /**
     * Parses JSON text as a thread dump.
     * @throws RuntimeException if an error occurs
     */
    public static ThreadDump parse(String json) {
        JSONValue threadDumpObj = JSONValue.parse(json).get("threadDump");
        int formatVersion = threadDumpObj.get("formatVersion").asInt();
        if (formatVersion != 2) {
            fail("Format " + formatVersion + " not supported");
        }

        // threadContainers array, preserve insertion order (parents are added before children)
        Map<String, JSONValue> containerObjs = threadDumpObj.get("threadContainers")
                .elements()
                .stream()
                .collect(Collectors.toMap(
                        c -> c.get("container").asString(),
                        Function.identity(),
                        (a, b) -> { fail("Duplicate container"); return null; },
                        LinkedHashMap::new
                ));

        // find root and create tree of thread containers
        ThreadContainer root = null;
        Map<String, ThreadContainer> map = new HashMap<>();
        for (String name : containerObjs.keySet()) {
            JSONValue containerObj = containerObjs.get(name);
            JSONValue parentObj = containerObj.get("parent");
            if (parentObj instanceof JSONValue.JSONNull) {
                if (root != null) {
                    fail("More than one root container");
                }
                root = new ThreadContainer(name, null, containerObj);
                map.put(name, root);
            } else {
                String parentName = parentObj.asString();
                ThreadContainer parent = map.get(parentName);
                if (parent == null) {
                    fail("Thread container " + name + " found before " + parentName);
                }
                var container = new ThreadContainer(name, parent, containerObj);
                parent.addChild(container);
                map.put(name, container);
            }
        }
        if (root == null) {
            fail("No root container");
        }

        return new ThreadDump(root, map, threadDumpObj);
    }

    private static void fail(String message) {
        throw new RuntimeException(message);
    }
}
