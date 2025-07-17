/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.stream.Stream;
import jdk.test.lib.json.JSONValue;

/**
 * Represents a thread dump that is obtained by parsing JSON text. A thread dump in JSON
 * format is generated with the {@code com.sun.management.HotSpotDiagnosticMXBean} API or
 * using {@code jcmd <pid> Thread.dump_to_file -format=json <file>}.
 *
 * <p> The following is an example thread dump that is parsed by this class. Many of the
 * objects are collapsed to reduce the size.
 *
 * <pre>{@code
 * {
 *   "threadDump": {
 *     "processId": "63406",
 *     "time": "2022-05-20T07:37:16.308017Z",
 *     "runtimeVersion": "19",
 *     "threadContainers": [
 *       {
 *         "container": "<root>",
 *         "parent": null,
 *         "owner": null,
 *         "threads": [
 *          {
 *            "tid": "1",
 *            "name": "main",
 *            "stack": [...]
 *          },
 *          {
 *            "tid": "8",
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
 *         "threadCount": "7"
 *       },
 *       {
 *         "container": "ForkJoinPool.commonPool\/jdk.internal.vm.SharedThreadContainer@56aac163",
 *         "parent": "<root>",
 *         "owner": null,
 *         "threads": [...],
 *         "threadCount": "1"
 *       },
 *       {
 *         "container": "java.util.concurrent.ThreadPoolExecutor@20322d26\/jdk.internal.vm.SharedThreadContainer@184f6be2",
 *         "parent": "<root>",
 *         "owner": null,
 *         "threads": [...],
 *         "threadCount": "1"
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
         * Returns the value of a property of this thread container, as a string.
         */
        private String getStringProperty(String propertyName) {
            JSONValue value = containerObj.get(propertyName);
            return (value != null) ? value.asString() : null;
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
            String owner = getStringProperty("owner");
            return (owner != null)
                    ? OptionalLong.of(Long.parseLong(owner))
                    : OptionalLong.empty();
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
            JSONValue.JSONArray threadsObj = containerObj.get("threads").asArray();
            Set<ThreadInfo> threadInfos = new HashSet<>();
            for (JSONValue threadObj : threadsObj) {
                threadInfos.add(new ThreadInfo(threadObj));
            }
            return threadInfos.stream();
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
            this.tid = Long.parseLong(threadObj.get("tid").asString());
            this.threadObj = threadObj;
        }

        /**
         * Returns the value of a property of this thread object, as a string.
         */
        private String getStringProperty(String propertyName) {
            JSONValue value = threadObj.get(propertyName);
            return (value != null) ? value.asString() : null;
        }

        /**
         * Returns the value of a property of an object in this thread object, as a string.
         */
        private String getStringProperty(String objectName, String propertyName) {
            if (threadObj.get(objectName) instanceof JSONValue.JSONObject obj
                    && obj.get(propertyName) instanceof JSONValue value) {
                return value.asString();
            }
            return null;
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
            return getStringProperty("name");
        }

        /**
         * Returns the thread state.
         */
        public String state() {
            return getStringProperty("state");
        }

        /**
         * Returns true if virtual thread.
         */
        public boolean isVirtual() {
            String s = getStringProperty("virtual");
            return (s != null) ? Boolean.parseBoolean(s) : false;
        }

        /**
         * Returns the thread's parkBlocker.
         */
        public String parkBlocker() {
            return getStringProperty("parkBlocker", "object");
        }

        /**
         * Returns the object that the thread is blocked entering its monitor.
         */
        public String blockedOn() {
            return getStringProperty("blockedOn");
        }

        /**
         * Return the object that is the therad is waiting on with Object.wait.
         */
        public String waitingOn() {
            return getStringProperty("waitingOn");
        }

        /**
         * Returns the thread stack.
         */
        public Stream<String> stack() {
            JSONValue.JSONArray stackObj = threadObj.get("stack").asArray();
            List<String> stack = new ArrayList<>();
            for (JSONValue steObject : stackObj) {
                stack.add(steObject.asString());
            }
            return stack.stream();
        }

        /**
         * Return a map of monitors owned.
         */
        public Map<Integer, List<String>> ownedMonitors() {
            Map<Integer, List<String>> ownedMonitors = new HashMap<>();
            JSONValue monitorsOwnedObj = threadObj.get("monitorsOwned");
            if (monitorsOwnedObj != null) {
                for (JSONValue obj : monitorsOwnedObj.asArray()) {
                    int depth = Integer.parseInt(obj.get("depth").asString());
                    for (JSONValue lock : obj.get("locks").asArray()) {
                        ownedMonitors.computeIfAbsent(depth, _ -> new ArrayList<>())
                                .add(lock.asString());
                    }
                }
            }
            return ownedMonitors;
        }

        /**
         * If the thread is a mounted virtual thread, return the thread identifier of
         * its carrier.
         */
        public OptionalLong carrier() {
            String s = getStringProperty("carrier");
            return (s != null)
                    ? OptionalLong.of(Long.parseLong(s))
                    : OptionalLong.empty();
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
     * Returns the value of a property of this thread dump, as a string.
     */
    private String getStringProperty(String propertyName) {
        JSONValue value = threadDumpObj.get(propertyName);
        return (value != null) ? value.asString() : null;
    }

    /**
     * Returns the value of threadDump/processId.
     */
    public long processId() {
        return Long.parseLong(getStringProperty("processId"));
    }

    /**
     * Returns the value of threadDump/time.
     */
    public String time() {
        return getStringProperty("time");
    }

    /**
     * Returns the value of threadDump/runtimeVersion.
     */
    public String runtimeVersion() {
        return getStringProperty("runtimeVersion");
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

        // threadContainers array, preserve insertion order (parents are added before children)
        Map<String, JSONValue> containerObjs = new LinkedHashMap<>();
        JSONValue threadContainersObj = threadDumpObj.get("threadContainers");
        for (JSONValue containerObj : threadContainersObj.asArray()) {
            String name = containerObj.get("container").asString();
            containerObjs.put(name, containerObj);
        }

        // find root and create tree of thread containers
        ThreadContainer root = null;
        Map<String, ThreadContainer> map = new HashMap<>();
        for (String name : containerObjs.keySet()) {
            JSONValue containerObj = containerObjs.get(name);
            String parentName = containerObj.get("parent").asString();
            if (parentName == null) {
                root = new ThreadContainer(name, null, containerObj);
                map.put(name, root);
            } else {
                var parent = map.get(parentName);
                if (parent == null) {
                    throw new RuntimeException("Thread container " + name + " found before " + parentName);
                }
                var container = new ThreadContainer(name, parent, containerObj);
                parent.addChild(container);
                map.put(name, container);
            }
        }

        return new ThreadDump(root, map, threadDumpObj);
    }
}