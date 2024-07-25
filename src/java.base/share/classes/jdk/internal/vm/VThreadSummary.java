/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.vm;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import sun.nio.ch.Poller;

/**
 * The implementation for the jcmd Thread.vthread_summary diagnostic command.
 */
public class VThreadSummary {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    // maximum number of thread containers to print
    private static final int MAX_THREAD_CONTAINERS = 256;

    // set to true if I/O poller in use
    private static volatile boolean pollerInitialized;

    private VThreadSummary() { }

    /**
     * Invoked by the poller I/O mechanism when it initializes.
     */
    public static void pollerInitialized() {
        pollerInitialized = true;
    }

    /**
     * Invoked by the VM to print virtual thread summary information.
     * @return the UTF-8 encoded information to print
     */
    private static byte[] print() {
        StringBuilder sb = new StringBuilder();

        // print thread containers (thread groupings)
        new ThreadContainersPrinter(sb, MAX_THREAD_CONTAINERS).run();
        sb.append(System.lineSeparator());

        // print virtual thread scheduler
        printSchedulerInfo(sb);
        sb.append(System.lineSeparator());

        // print I/O pollers if initialized
        if (pollerInitialized) {
            printPollerInfo(sb);
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Prints the tree of thread containers starting from the root container.
     */
    private static class ThreadContainersPrinter {
        private final StringBuilder sb;
        private final int max;
        private int count;

        ThreadContainersPrinter(StringBuilder sb, int max) {
            this.sb = sb;
            this.max = max;
        }

        void run() {
            printThreadContainers(ThreadContainers.root(), 0);
        }

        /**
         * Prints the given thread container and its children.
         * @return true if the thread container and all its children were printed,
         *   false if the output was truncated because the max was reached
         */
        private boolean printThreadContainers(ThreadContainer container, int depth) {
            if (!printThreadContainer(container, depth)) {
                return false;
            }
            boolean truncated = container.children()
                    .map(c -> printThreadContainers(c, depth + 1))
                    .anyMatch(b -> b == false);
            return !truncated;
        }

        /**
         * Prints the given thread container or a "truncated" message if the maximum
         * number of thread containers has already been printed.
         * @param container the thread container
         * @param depth the depth in the tree, for indentation purposes
         * @return true if the thread container was printed, false if beyond max
         */
        private boolean printThreadContainer(ThreadContainer container, int depth) {
            count++;
            if (count > max) {
                sb.append("<truncated ...>")
                        .append(System.lineSeparator());
                return false;
            }

            Map<Boolean, Long> threadCounts = container.threads()
                    .collect(Collectors.partitioningBy(Thread::isVirtual, Collectors.counting()));
            long platformThreadCount = threadCounts.get(Boolean.FALSE);
            long virtualThreadCount = threadCounts.get(Boolean.TRUE);
            if (depth > 0) {
                int indent = depth * 4;
                sb.append(" ".repeat(indent)).append("+-- ");
            }
            sb.append(container)
                    .append(" [platform threads = ")
                    .append(platformThreadCount)
                    .append(", virtual threads = ")
                    .append(virtualThreadCount)
                    .append("]")
                    .append(System.lineSeparator());

            return true;
        }
    }

    /**
     * Print information on the virtual thread schedulers to given string buffer.
     */
    static void printSchedulerInfo(StringBuilder sb) {
        sb.append("Default virtual thread scheduler:")
                .append(System.lineSeparator());
        sb.append(JLA.virtualThreadDefaultScheduler())
                .append(System.lineSeparator());

        sb.append(System.lineSeparator());

        sb.append("Timeout schedulers:")
                .append(System.lineSeparator());
        var schedulers = JLA.virtualThreadDelayedTaskSchedulers().toList();
        for (int i = 0; i < schedulers.size(); i++) {
            sb.append('[')
                    .append(i)
                    .append("] ")
                    .append(schedulers.get(i))
                    .append(System.lineSeparator());
        }
    }

    /**
     * Print information on threads registered for I/O to the given string buffer.
     */
    private static void printPollerInfo(StringBuilder sb) {
        Poller masterPoller = Poller.masterPoller();
        List<Poller> readPollers = Poller.readPollers();
        List<Poller> writePollers = Poller.writePollers();

        if (masterPoller != null) {
            sb.append("Master I/O poller:")
                    .append(System.lineSeparator())
                    .append(masterPoller)
                    .append(System.lineSeparator());

            sb.append(System.lineSeparator());
        }

        sb.append("Read I/O pollers:");
        sb.append(System.lineSeparator());
        IntStream.range(0, readPollers.size())
                .forEach(i -> sb.append('[')
                        .append(i)
                        .append("] ")
                        .append(readPollers.get(i))
                        .append(System.lineSeparator()));

        sb.append(System.lineSeparator());

        sb.append("Write I/O pollers:");
        sb.append(System.lineSeparator());
        IntStream.range(0, writePollers.size())
                .forEach(i -> sb.append('[')
                        .append(i)
                        .append("] ")
                        .append(writePollers.get(i))
                        .append(System.lineSeparator()));
    }
}
