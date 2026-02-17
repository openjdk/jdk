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

package compiler.lib.ir_framework.driver.network;

import compiler.lib.ir_framework.driver.irmatching.IRMatcher;
import compiler.lib.ir_framework.driver.network.testvm.java.JavaMessages;
import compiler.lib.ir_framework.shared.TestFrameworkSocket;
import compiler.lib.ir_framework.test.network.MessageTag;

import java.util.Scanner;

/**
 * This class collects all the parsed data received over the {@link TestFrameworkSocket}. This data is required later
 * in the {@link IRMatcher}.
 */
public class TestVMData {
    private final boolean allowNotCompilable;
    private final String hotspotPidFileName;
    private final String applicableIRRules;

    public TestVMData(JavaMessages javaMessages, String hotspotPidFileName, boolean allowNotCompilable) {
        this.applicableIRRules = processOutput(javaMessages);
        this.hotspotPidFileName = hotspotPidFileName;
        this.allowNotCompilable = allowNotCompilable;
    }

    public String hotspotPidFileName() {
        return hotspotPidFileName;
    }

    public boolean allowNotCompilable() {
        return allowNotCompilable;
    }

    public String applicableIRRules() {
        return applicableIRRules;
    }

    /**
     * Process the socket output: All prefixed lines are dumped to the standard output while the remaining lines
     * represent the Applicable IR Rules used for IR matching later.
     */
    private String processOutput(JavaMessages javaMessages) {
        String output = javaMessages.output();
        if (javaMessages.hasStdOut()) {
            StringBuilder testListBuilder = new StringBuilder();
            StringBuilder messagesBuilder = new StringBuilder();
            StringBuilder nonStdOutBuilder = new StringBuilder();
            Scanner scanner = new Scanner(output);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith(MessageTag.STDOUT)) {
                    // Exclude [STDOUT] from message.
                    line = line.substring(MessageTag.STDOUT.length());
                    if (line.startsWith(MessageTag.TEST_LIST)) {
                        // Exclude [TEST_LIST] from message for better formatting.
                        line = "> " + line.substring(MessageTag.TEST_LIST.length() + 1);
                        testListBuilder.append(line).append(System.lineSeparator());
                    } else {
                        messagesBuilder.append(line).append(System.lineSeparator());
                    }
                } else {
                    nonStdOutBuilder.append(line).append(System.lineSeparator());
                }
            }
            System.out.println();
            if (!testListBuilder.isEmpty()) {
                System.out.println("Run flag defined test list");
                System.out.println("--------------------------");
                System.out.println(testListBuilder);
                System.out.println();
            }
            if (!messagesBuilder.isEmpty()) {
                System.out.println("Messages from Test VM");
                System.out.println("---------------------");
                System.out.println(messagesBuilder);
            }
            return nonStdOutBuilder.toString();
        } else {
            return output;
        }
    }
}
