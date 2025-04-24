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
package jdk.internal.vm;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import sun.nio.ch.Poller;

/**
 * The implementation for the jcmd Thread.vthread_* diagnostic commands. These methods are
 * called from the "Attach Listener" thread.
 */
public class JcmdVThreadCommands {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private JcmdVThreadCommands() { }

    /**
     * Invoked by the VM to print the virtual scheduler to a byte[].
     */
    private static byte[] printScheduler() {
        StringBuilder sb = new StringBuilder();

        // virtual thread scheduler
        sb.append(JLA.virtualThreadDefaultScheduler())
          .append(System.lineSeparator());

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Invoked by the VM to print the I/O pollers to a byte[].
     */
    private static byte[] printPollers() {
        StringBuilder sb = new StringBuilder();

        Poller masterPoller = Poller.masterPoller();
        List<Poller> readPollers = Poller.readPollers();
        List<Poller> writePollers = Poller.writePollers();

        if (masterPoller != null) {
            sb.append("Master I/O poller:")
              .append(System.lineSeparator())
              .append(masterPoller)
              .append(System.lineSeparator());

            // break
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

        // break
        sb.append(System.lineSeparator());

        sb.append("Write I/O pollers:");
        sb.append(System.lineSeparator());
        IntStream.range(0, writePollers.size())
                .forEach(i -> sb.append('[')
                                .append(i)
                                .append("] ")
                                .append(writePollers.get(i))
                                .append(System.lineSeparator()));

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
