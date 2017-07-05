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
import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.ExecutionException;
import org.testng.Assert;
import org.testng.TestNG;
import org.testng.annotations.Test;

/*
 * @test
 * @library /lib/testlibrary
 * Test counting and JavaChild.spawning and counting of Processes.
 * @run testng/othervm InfoTest
 * @author Roger Riggs
 */
public class TreeTest extends ProcessUtil {
    // Main can be used to run the tests from the command line with only testng.jar.
    @SuppressWarnings("raw_types")
    public static void main(String[] args) {
        Class<?>[] testclass = {TreeTest.class};
        TestNG testng = new TestNG();
        testng.setTestClasses(testclass);
        testng.run();
    }

    /**
     * Test counting and spawning and counting of Processes.
     */
    @Test
    public static void test1() {
        final int MAXCHILDREN = 2;
        List<JavaChild> spawned = new ArrayList<>();

        try {
            ProcessHandle self = ProcessHandle.current();

            printf("self pid: %d%n", self.getPid());
            printDeep(self, "");
            long count = getChildren(self).size();
            Assert.assertEquals(count, 0, "Start with zero children");

            for (int i = 0; i < MAXCHILDREN; i++) {
                // spawn and wait for instructions
                spawned.add(JavaChild.spawnJavaChild("pid", "stdin"));
            }

            List<ProcessHandle> subprocesses = getChildren(self);
            subprocesses.forEach(ProcessUtil::printProcess);
            count = subprocesses.size();
            Assert.assertEquals(count, MAXCHILDREN, "Wrong number of spawned children");

            // Send exit command to each spawned Process
            spawned.forEach(p -> {
                    try {
                        p.sendAction("exit", "");
                    } catch (IOException ex) {
                        Assert.fail("IOException in sendAction", ex);
                    }
                });

            // Wait for each Process to exit
            spawned.forEach(p -> {
                    do {
                        try {
                            Assert.assertEquals(p.waitFor(), 0, "exit status incorrect");
                            break;
                        } catch (InterruptedException  ex) {
                            continue; // Retry
                        }
                    } while (true);
                });

            // Verify that ProcessHandle.isAlive sees each of them as not alive
            for (ProcessHandle ph : subprocesses) {
                Assert.assertFalse(ph.isAlive(),
                        "ProcessHandle.isAlive for exited process: " + ph);
            }

            // Verify no current children are visible
            count = getChildren(self).size();
            Assert.assertEquals(count, 0, "Children destroyed, should be zero");

        } catch (IOException ioe) {
            Assert.fail("unable to spawn process", ioe);
        } finally {
            // Cleanup any left over processes
            spawned.stream().map(Process::toHandle)
                    .filter(ProcessHandle::isAlive)
                    .forEach(ph -> printDeep(ph, "test1 cleanup: "));
            destroyProcessTree(ProcessHandle.current());
        }
    }

    /**
     * Test counting and spawning and counting of Processes.
     */
    @Test
    public static void test2() {
        ProcessHandle p1Handle = null;
        try {
            ProcessHandle self = ProcessHandle.current();
            List<ProcessHandle> initialChildren = getChildren(self);
            long count = initialChildren.size();
            if (count > 0) {
                initialChildren.forEach(p -> printDeep(p, "test2 initial unexpected: "));
                Assert.assertEquals(count, 0, "Start with zero children (except Windows conhost.exe)");
            }

            JavaChild p1 = JavaChild.spawnJavaChild("stdin");
            p1Handle = p1.toHandle();
            printf("  p1 pid: %d%n", p1.getPid());

            int spawnNew = 3;
            p1.sendAction("spawn", spawnNew, "stdin");

            // Wait for direct children to be created and save the list
            List<ProcessHandle> subprocesses = waitForAllChildren(p1Handle, spawnNew);
            for (ProcessHandle ph : subprocesses) {
                Assert.assertTrue(ph.isAlive(), "Child should be alive: " + ph);
            }

            // Each child spawns two processes and waits for commands
            int spawnNewSub = 2;
            p1.sendAction("child", "spawn", spawnNewSub, "stdin");

            // For each spawned child, wait for its children
            for (ProcessHandle p : subprocesses) {
                List<ProcessHandle> grandChildren = waitForChildren(p, spawnNewSub);
            }

            List<ProcessHandle> allChildren = getAllChildren(p1Handle);
            printf(" allChildren:  %s%n",
                    allChildren.stream().map(p -> p.getPid())
                            .collect(Collectors.toList()));
            for (ProcessHandle ph : allChildren) {
                Assert.assertEquals(ph.isAlive(), true, "Child should be alive: " + ph);
            }

            // Closing JavaChild's InputStream will cause all children to exit
            p1.getOutputStream().close();

            for (ProcessHandle p : allChildren) {
                try {
                    p.onExit().get();       // wait for the child to exit
                } catch (ExecutionException e) {
                    Assert.fail("waiting for process to exit", e);
                }
            }
            p1.waitFor();           // wait for spawned process to exit

            List<ProcessHandle> remaining = getChildren(self);
            remaining.forEach(ph -> Assert.assertFalse(ph.isAlive(),
                            "process should not be alive: " + ph));
        } catch (IOException | InterruptedException t) {
            t.printStackTrace();
            throw new RuntimeException(t);
        } finally {
            // Cleanup any left over processes
            if (p1Handle.isAlive()) {
                printDeep(p1Handle, "test2 cleanup: ");
            }
            destroyProcessTree(ProcessHandle.current());
        }
    }

    /**
     * Test destroy of processes.
     */
    @Test
    public static void test3() {
        try {
            ProcessHandle self = ProcessHandle.current();

            JavaChild p1 = JavaChild.spawnJavaChild("stdin");
            ProcessHandle p1Handle = p1.toHandle();
            printf(" p1: %s%n", p1.getPid());
            long count = getChildren(self).size();
            Assert.assertEquals(count, 1, "Wrong number of spawned children");

            int newChildren = 3;
            // Spawn children and have them wait
            p1.sendAction("spawn", newChildren, "stdin");

            // Wait for the new processes and save the list
            List<ProcessHandle> subprocesses = waitForAllChildren(p1Handle, newChildren);
            printDeep(p1Handle, "allChildren");

            Assert.assertEquals(subprocesses.size(), newChildren, "Wrong number of children");

            p1.children().filter(TreeTest::isNotWindowsConsole)
                    .forEach(ProcessHandle::destroyForcibly);

            self.children().filter(TreeTest::isNotWindowsConsole)
                    .forEach(ProcessHandle::destroyForcibly);

            do {
                Thread.sleep(500L);      // It will happen but don't burn the cpu
                Object[] children = self.allChildren()
                        .filter(TreeTest::isNotWindowsConsole)
                        .toArray();
                count = children.length;
                printf(" waiting for subprocesses of %s to terminate," +
                                " expected: 0, current: %d, children: %s%n", self, count,
                        Arrays.toString(children));
                printDeep(self, "");
            } while (count > 0);

            boolean ex1 = p1.waitFor(5, TimeUnit.SECONDS);
            Assert.assertTrue(ex1, "Subprocess should have exited: " + p1);

            for (ProcessHandle p : subprocesses) {
                Assert.assertFalse(p.isAlive(), "Destroyed process.isAlive: " + p +
                        ", parent: " + p.parent() +
                        ", info: " + p.info().toString());
            }

        } catch (IOException ioe) {
            Assert.fail("Spawn of subprocess failed", ioe);
        } catch (InterruptedException inte) {
            Assert.fail("InterruptedException", inte);
        }
    }

    /**
     * Test (Not really a test) that dumps the list of all Processes.
     */
    @Test
    public static void test4() {
        printf("    Parent     Child  Info%n");
        Stream<ProcessHandle> s = ProcessHandle.allProcesses();
        ProcessHandle[] processes = s.toArray(ProcessHandle[]::new);
        int len = processes.length;
        ProcessHandle[] parent = new ProcessHandle[len];
        Set<ProcessHandle> processesSet =
                Arrays.stream(processes).collect(Collectors.toSet());
        Integer[] sortindex = new Integer[len];
        for (int i = 0; i < len; i++) {
            sortindex[i] = i;
         }
        for (int i = 0; i < len; i++) {
            parent[sortindex[i]] = processes[sortindex[i]].parent().orElse(null);
        }
        Arrays.sort(sortindex, (i1, i2) -> {
            int cmp = Long.compare((parent[i1] == null ? 0L : parent[i1].getPid()),
                    (parent[i2] == null ? 0L : parent[i2].getPid()));
            if (cmp == 0) {
                cmp = Long.compare((processes[i1] == null ? 0L : processes[i1].getPid()),
                        (processes[i2] == null ? 0L : processes[i2].getPid()));
            }
            return cmp;
        });
        boolean fail = false;
        for (int i = 0; i < len; i++) {
            ProcessHandle p = processes[sortindex[i]];
            ProcessHandle p_parent = parent[sortindex[i]];
            ProcessHandle.Info info = p.info();
            String indent = "    ";
            if (p_parent != null) {
                if (!processesSet.contains(p_parent)) {
                    fail = true;
                    indent = "*** ";
                }
            }
            printf("%s %7s, %7s, %s%n", indent, p_parent, p, info);
        }
        Assert.assertFalse(fail, "Parents missing from all Processes");

    }

    /**
     * A test for scale; launch a large number (39) of subprocesses.
     */
    @Test
    public static void test5() {
        int factor = 2;
        ProcessHandle p1Handle = null;
        Instant start = Instant.now();
        try {
            JavaChild p1 = JavaChild.spawnJavaChild("stdin");
            p1Handle = p1.toHandle();

            printf("Spawning %d x %d x %d processes, pid: %d%n",
                    factor, factor, factor, p1.getPid());

            // Start the first tier of subprocesses
            p1.sendAction("spawn", factor, "stdin");

            // Start the second tier of subprocesses
            p1.sendAction("child", "spawn", factor, "stdin");

            // Start the third tier of subprocesses
            p1.sendAction("child", "child", "spawn", factor, "stdin");

            int newChildren = factor * (1 + factor * (1 + factor));
            List<ProcessHandle> children = ProcessUtil.waitForAllChildren(p1Handle, newChildren);

            Assert.assertEquals(p1.children()
                    .filter(ProcessUtil::isNotWindowsConsole)
                    .count(), factor, "expected direct children");
            Assert.assertEquals(p1.allChildren()
                    .filter(ProcessUtil::isNotWindowsConsole)
                    .count(),
                    factor * factor * factor + factor * factor + factor,
                    "expected all children");

            List<ProcessHandle> subprocesses = p1.allChildren()
                    .filter(ProcessUtil::isNotWindowsConsole)
                    .collect(Collectors.toList());
            printf(" allChildren:  %s%n",
                    subprocesses.stream().map(p -> p.getPid())
                    .collect(Collectors.toList()));

            p1.getOutputStream().close();  // Close stdin for the controlling p1
            p1.waitFor();
        } catch (InterruptedException | IOException ex) {
            Assert.fail("Unexpected Exception", ex);
        } finally {
            printf("Duration: %s%n", Duration.between(start, Instant.now()));
            // Cleanup any left over processes
            if (p1Handle.isAlive()) {
                printDeep(p1Handle, "test5 cleanup: ");
            }
            destroyProcessTree(ProcessHandle.current());
        }
    }

}
