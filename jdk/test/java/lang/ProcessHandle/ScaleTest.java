/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import static org.testng.Assert.assertEquals;
import org.testng.TestNG;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/*
 * @test
 * @summary Scalibities test for checking scability of ProcessHandle
 * @run testng/othervm ScaleTest
 */
public class ScaleTest {
    /**
     * Scale max processes number to 5000.
     */
    private final static int MAX_PROCESSES_LIMIT = 1000;

    /**
     * Create test process number as 1, 2, 5, 10, 20, 50, 100, 200... until it reach
     * setting process limit.
     * @return iterator on how many processes can be created.
     * @throws IOException if can't create processes.
     */
    @DataProvider
    public Iterator<Object[]> processesNumbers() {
        // Limit spawn processes number less than total limitation.

        List<Object[]> testProcesses = new ArrayList<>();
        int i = 1, j = 0;
        while (i <= MAX_PROCESSES_LIMIT) {
            testProcesses.add(new Object[]{i});
            if ((j % 3) != 1)
                i *= 2;
            else
                i = i * 25 / 10;
            j++;
        }
        return testProcesses.iterator();
    }

    /**
     * Start process by given number, compare created processes with
     * ProcessHandle.children by order.
     * @param processNum processes number that will be created
     */
    @Test(dataProvider = "processesNumbers")
    public void scaleProcesses(int processNum) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command("sleep", "600");
            List<ProcessHandle> children = new ArrayList<>();

            int createdProcessNum = 0;
            for (int i = 0; i < processNum; i++) {
                try {
                    children.add(pb.start().toHandle());
                    createdProcessNum++;
                } catch (Throwable ignore) {
                    // Hard to control how many processes we can generate.
                    // Ignore every error when create new process
                }
            }
            List<ProcessHandle> phs = ProcessHandle.current().allChildren()
                    .filter(ph -> ph.info().command().orElse("").contains("sleep"))
                    .collect(Collectors.toList());
            assertEquals(phs.size(), createdProcessNum, "spawned processes vs allChildren");
            assertEqualsWithoutOrder(phs, children, ProcessHandle::compareTo, processNum);
        } finally {
            ProcessHandle.current().children().forEach(ProcessHandle::destroyForcibly);
        }
    }

    /**
     * Sort two list by given comparator and compare two list without order
     * @param actual Process handle list1
     * @param expected Process handle list1
     * @param comp ProcessHandle comparator for sorting
     * @param pn number of processes
     */
    private void assertEqualsWithoutOrder(List<ProcessHandle> actual,
            List<ProcessHandle> expected, Comparator<ProcessHandle> comp, int pn) {
        Collections.sort(actual, comp);
        Collections.sort(expected, comp);

        assertEquals(actual, expected);
    }

    // Main can be used to run the tests from the command line with only testng.jar.
    @SuppressWarnings("raw_types")
    public static void main(String[] args) {
        Class<?>[] testclass = {ScaleTest.class};
        TestNG testng = new TestNG();
        testng.setTestClasses(testclass);
        testng.run();
    }
}
