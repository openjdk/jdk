/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8355323
 * @summary Verify local execution can stop execution when there are no backward branches
 * @modules jdk.jshell/jdk.internal.jshell.tool
 * @build KullaTesting TestingInputStream
 * @run testng LocalStopExecutionTest
 */

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import jdk.internal.jshell.tool.StopDetectingInputStream;
import jdk.internal.jshell.tool.StopDetectingInputStream.State;
import jdk.jshell.JShell;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

@Test
public class LocalStopExecutionTest extends AbstractStopExecutionTest {

    @BeforeMethod
    @Override
    public void setUp() {
        setUp(b -> b.executionEngine("local"));
    }

    @Test
    public void testVeryLongRecursion() throws InterruptedException {
        scheduleStop(
            """
            // Note: there are no backward branches in this class
            new Runnable() {
                public void run() {
                    recurse(1);
                    recurse(10);
                    recurse(100);
                    recurse(1000);
                    recurse(10000);
                    recurse(100000);
                    recurse(1000000);
                }
                public void recurse(int depth) {
                    if (depth == 0)
                        return;
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                    recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1); recurse(depth - 1);
                }
            }.run();
            """
        );
    }
}
