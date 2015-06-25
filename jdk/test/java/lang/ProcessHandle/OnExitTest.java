/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.InterruptedException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import org.testng.annotations.Test;
import org.testng.Assert;
import org.testng.TestNG;

/*
 * @test
 * @summary Functions of Process.onExit and ProcessHandle.onExit
 * @author Roger Riggs
 */

public class OnExitTest extends ProcessUtil {

    @SuppressWarnings("raw_types")
    public static void main(String[] args) {
        Class<?>[] testclass = { OnExitTest.class};
        TestNG testng = new TestNG();
        testng.setTestClasses(testclass);
        testng.run();
    }

    /**
     * Basic test of exitValue and onExit.
     */
    @Test
    public static void test1() {
        try {
            int[] exitValues = {0, 1, 10};
            for (int value : exitValues) {
                Process p = JavaChild.spawn("exit", Integer.toString(value));
                CompletableFuture<Process> future = p.onExit();
                future.thenAccept( (ph) -> {
                    int actualExitValue = ph.exitValue();
                    printf(" javaChild done: %s, exitStatus: %d%n",
                            ph, actualExitValue);
                    Assert.assertEquals(actualExitValue, value, "actualExitValue incorrect");
                    Assert.assertEquals(ph, p, "Different Process passed to thenAccept");
                });

                Process h = future.get();
                Assert.assertEquals(h, p);
                Assert.assertEquals(p.exitValue(), value);
                Assert.assertFalse(p.isAlive(), "Process should not be alive");
                p.waitFor();
            }
        } catch (IOException | InterruptedException | ExecutionException ex) {
            Assert.fail(ex.getMessage(), ex);
        } finally {
            destroyProcessTree(ProcessHandle.current());
        }
    }

    /**
     * Test of Completion handler when parent is killed.
     * Spawn 1 child to spawn 3 children each with 2 children.
     */
    @Test
    public static void test2() {
        try {
            ConcurrentHashMap<ProcessHandle, ProcessHandle> processes = new ConcurrentHashMap<>();
            List<ProcessHandle> children = getChildren(ProcessHandle.current());
            children.forEach(ProcessUtil::printProcess);
            Assert.assertEquals(children.size(), 0,
                    "Expected to start with zero children; " + children);

            JavaChild proc = JavaChild.spawnJavaChild("stdin");
            ProcessHandle procHandle = proc.toHandle();
            printf(" spawned: %d%n", proc.getPid());

            proc.forEachOutputLine((s) -> {
                String[] split = s.trim().split(" ");
                if (split.length == 3 && split[1].equals("spawn")) {
                    Long child = Long.valueOf(split[2]);
                    Long parent = Long.valueOf(split[0].split(":")[0]);
                    processes.put(ProcessHandle.of(child).get(), ProcessHandle.of(parent).get());
                }
            });

            proc.sendAction("spawn", "3", "stdin");

            proc.sendAction("child", "spawn", "2", "stdin");

            // Poll until all 9 child processes exist or the timeout is reached
            int expected = 9;
            Instant endTimeout = Instant.now().plusSeconds(10L);
            do {
                Thread.sleep(200L);
                printf(" subprocess count: %d, waiting for %d%n", processes.size(), expected);
            } while (processes.size() < expected &&
                    Instant.now().isBefore(endTimeout));

            children = getAllChildren(procHandle);

            ArrayBlockingQueue<ProcessHandle> completions = new ArrayBlockingQueue<>(expected + 1);
            Instant startTime = Instant.now();
            // Create a future for each of the 9 children
            processes.forEach( (p, parent) -> {
                        p.onExit().whenComplete((ph, ex) -> {
                            Duration elapsed = Duration.between(startTime, Instant.now());
                            completions.add(ph);
                            printf("whenComplete: pid: %s, exception: %s, thread: %s, elapsed: %s%n",
                                    ph, ex, Thread.currentThread(), elapsed);
                        });
                    });

            // Check that each of the spawned processes is included in the children
            List<ProcessHandle> remaining = new ArrayList<>(children);
            processes.forEach((p, parent) -> {
                Assert.assertTrue(remaining.remove(p), "spawned process should have been in children");
            });

            // Remove Win32 system spawned conhost.exe processes
            remaining.removeIf(ProcessUtil::isWindowsConsole);

            remaining.forEach(p -> printProcess(p, "unexpected: "));
            if (remaining.size() > 0) {
                // Show full list for debugging
                ProcessUtil.logTaskList();
            }

            proc.destroy();  // kill off the parent
            proc.waitFor();

            // Wait for all the processes to be completed
            processes.forEach((p, parent) -> {
                try {
                    p.onExit().get();
                } catch (InterruptedException | ExecutionException ex) {
                    // ignore
                }
            });

            // Verify that all 9 exit handlers were called
            processes.forEach((p, parent) ->
                Assert.assertTrue(completions.contains(p), "Child onExit not called: " + p
                        + ", parent: " + parent
                        + ": " + p.info()));

            // Show the status of the original children
            children.forEach(p -> printProcess(p, "after onExit:"));

            Assert.assertEquals(proc.isAlive(), false, "destroyed process is alive:: %s%n" + proc);

            List<ProcessHandle> children2 = getAllChildren(procHandle);
            printf(" children2: %s%n", children2.toString());
            Assert.assertEquals(children2.size(), 0, "After onExit, expected no children");

            Assert.assertEquals(remaining.size(), 0, "Unaccounted for children");

        } catch (IOException | InterruptedException ex) {
            Assert.fail(ex.getMessage());
        } finally {
            destroyProcessTree(ProcessHandle.current());
        }
    }

}
