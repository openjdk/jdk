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

package compiler.lib.ir_framework.driver.irmatching.parser.hotspot;

import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.parser.ApplicableIRRulesParser;
import compiler.lib.ir_framework.driver.irmatching.parser.TestMethods;
import compiler.lib.ir_framework.shared.TestFrameworkException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Class to parse the ideal compile phases and PrintOptoAssembly outputs of the test class from the hotspot_pid* file
 * of all methods identified by {@link ApplicableIRRulesParser}.
 *
 * @see IRMethod
 * @see ApplicableIRRulesParser
 */
public class HotSpotPidFileParser {
    private final State state;

    public HotSpotPidFileParser(String testClass, TestMethods testMethods) {
        this.state = new State(testClass, testMethods);
    }

    /**
     * Parse the hotspot_pid*.log file from the Test VM. Read the ideal compile phase and PrintOptoAssembly outputs for
     * all methods defined by the Applicable IR Rules.
     */
    public LoggedMethods parse(String hotspotPidFileName) {
        try (var reader = Files.newBufferedReader(Paths.get(hotspotPidFileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                state.update(line);
            }
            return state.loggedMethods();
        } catch (IOException e) {
            throw new TestFrameworkException("Error while reading " + hotspotPidFileName, e);
        }
    }
}
