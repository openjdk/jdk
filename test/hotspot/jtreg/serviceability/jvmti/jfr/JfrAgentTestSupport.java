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
 *
 */

import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;

final class JfrAgentTestSupport {
    static String library(String name) {
        return AgentTestSupport.library(name);
    }

    static void attach(String library, String options) throws Exception {
        AgentTestSupport.attach(library, options);
    }

    static OutputAnalyzer launch(Class<?> main, List<String> startup, String... args) throws Exception {
        List<String> options = new ArrayList<>(startup);
        options.add("-XX:+UnlockExperimentalVMOptions");
        options.add("-XX:-UseFastUnorderedTimeStamps");
        return AgentTestSupport.launch(main, options, args);
    }

    static void addNativeLibraryPath(ProcessBuilder process) {
        AgentTestSupport.addNativeLibraryPath(process);
    }

    static void unloadedOnce(OutputAnalyzer output, String library) {
        AgentTestSupport.unloadedOnce(output, library);
    }

    private JfrAgentTestSupport() { }
}
