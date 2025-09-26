/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8156101 8159935 8159122 8168615
 * @summary Tests for ExecutionControl SPI
 * @build KullaTesting ExecutionControlTestBase
 * @run junit UserExecutionControlTest
 */


import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UserExecutionControlTest extends ExecutionControlTestBase {

    @BeforeEach
    @Override
    public void setUp() {
        setUp(builder -> builder.executionEngine("local"));
    }

    @Test
    public void verifyLocal() throws ClassNotFoundException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        System.setProperty("LOCAL_CHECK", "TBD");
        assertEquals("TBD", System.getProperty("LOCAL_CHECK"));
        assertEval("System.getProperty(\"LOCAL_CHECK\")", "\"TBD\"");
        assertEval("System.setProperty(\"LOCAL_CHECK\", \"local\")");
        assertEquals("local", System.getProperty("LOCAL_CHECK"));
    }

}
