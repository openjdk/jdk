/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.network.testvm;

import compiler.lib.ir_framework.driver.network.testvm.java.JavaMessages;
import compiler.lib.ir_framework.shared.TestFrameworkException;
import compiler.lib.ir_framework.shared.TestFrameworkSocket;
import compiler.lib.ir_framework.test.network.MessageTag;

import java.io.BufferedReader;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Dedicated reader for Test VM messages received by the {@link TestFrameworkSocket}. The reader is used as a task
 * wrapped in a {@link Future}. The received messages are returned in a new {@link JavaMessages} wrapper. Once the
 * Test VM is terminated, the client connection is closed and the parsed messages can be fetched with
 * {@link Future#get()} which calls {@link #call()}.
 */
public class TestVmMessageReader implements Callable<JavaMessages> {
    private final Socket socket;
    private final BufferedReader reader;
    private boolean receivedStdOut;

    public TestVmMessageReader(Socket socket, BufferedReader reader) {
        this.socket = socket;
        this.reader = reader;
        this.receivedStdOut = false;
    }

    @Override
    public JavaMessages call() {
        try (socket; reader) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append(System.lineSeparator());
                if (line.startsWith(MessageTag.STDOUT)) {
                    receivedStdOut = true;
                }
            }
            return new JavaMessages(builder.toString(), receivedStdOut);
        } catch (Exception e) {
            throw new TestFrameworkException("Error while reading Test VM socket messages", e);
        }
    }
}
