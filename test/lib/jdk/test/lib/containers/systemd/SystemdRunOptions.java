/*
 * Copyright (c) 2024, Red Hat, Inc.
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

package jdk.test.lib.containers.systemd;

import static jdk.test.lib.Asserts.assertNotNull;

import java.util.ArrayList;
import java.util.Collections;


// This class represents options for running java inside systemd slices
// in test environment.
public class SystemdRunOptions {
    public ArrayList<String> javaOpts = new ArrayList<>();
    public String classToRun;  // class or "-version"
    public ArrayList<String> classParams = new ArrayList<>();
    public String memoryLimit; // used in slice for MemoryLimit property
    public String cpuLimit;    // used in slice for CPUQuota property
    public String sliceName;   // name of the slice (nests CPU in memory)
    public String sliceDMemoryLimit; // used in jdk_internal.slice.d
    public String sliceDCpuLimit;    // used in jdk_internal.slice.d

    /**
     * Convenience constructor for most common use cases in testing.
     * @param classToRun  a class to run, or "-version"
     * @param javaOpts  java options to use
     *
     * @return Default docker run options
     */
    public SystemdRunOptions(String classToRun, String... javaOpts) {
        this.classToRun = classToRun;
        Collections.addAll(this.javaOpts, javaOpts);
        this.sliceName = defaultSliceName();
    }

    private static String defaultSliceName() {
        // Create a unique name for a systemd slice
        // jtreg guarantees that test.name is unique among all concurrently executing
        // tests. For example, if you have two test roots:
        //
        //     $ find test -type f
        //     test/foo/TEST.ROOT
        //     test/foo/my/TestCase.java
        //     test/bar/TEST.ROOT
        //     test/bar/my/TestCase.java
        //     $ jtreg -concur:2 test/foo test/bar
        //
        // jtreg will first run all the tests under test/foo. When they are all finished, then
        // jtreg will run all the tests under test/bar. So you will never have two concurrent
        // test cases whose test.name is "my/TestCase.java"
        String testname = System.getProperty("test.name");
        assertNotNull(testname, "must be set by jtreg");
        testname = testname.replace(".java", "");
        testname = testname.replace("/", "_");
        testname = testname.replace("\\", "_");
        testname = testname.replace("-", "_");

        // Example:
        //  Memory: "test_containers_systemd_TestMemoryAwareness"
        //  CPU:    "test_containers_systemd_TestMemoryAwareness-cpu" => derived
        return testname;
    }

    /**
     * The memory limit set with a .slice file in the systemd
     * config directory.
     *
     * @param memLimit The memory limit to set (e.g. 1000M).
     * @return The run options.
     */
    public SystemdRunOptions memoryLimit(String memLimit) {
        this.memoryLimit = memLimit;
        return this;
    }

    /**
     * The memory limit to set in the top-level jdk_internal.slice.d
     * systemd config directory.
     *
     * @param memoryLimit The memory limit to set.
     * @return The run options.
     */
    public SystemdRunOptions sliceDMemoryLimit(String memoryLimit) {
        this.sliceDMemoryLimit = memoryLimit;
        return this;
    }

    /**
     * The CPU limit set with a .slice file in the systemd
     * config directory.
     *
     * @param cpuLimit
     * @return The run options.
     */
    public SystemdRunOptions cpuLimit(String cpuLimit) {
        this.cpuLimit = cpuLimit;
        return this;
    }

    /**
     * The Cpu limit set in the top-level jdk_internal.slice.d
     * systemd config directory.
     *
     * @param cpuLimit The CPU limit to set to.
     * @return The run options.
     */
    public SystemdRunOptions sliceDCpuLimit(String cpuLimit) {
        this.sliceDCpuLimit = cpuLimit;
        return this;
    }

    public SystemdRunOptions sliceName(String name) {
        this.sliceName = name;
        return this;
    }

    public SystemdRunOptions addJavaOpts(String... opts) {
        Collections.addAll(javaOpts, opts);
        return this;
    }

    public SystemdRunOptions addClassOptions(String... opts) {
        Collections.addAll(classParams,opts);
        return this;
    }

    public boolean hasSliceDLimit() {
        return this.sliceDMemoryLimit != null ||
                this.sliceDCpuLimit != null;
    }
}
