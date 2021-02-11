/*
 * Copyright (c) 2020, SAP SE. All rights reserved.
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
 * @summary Test of diagnostic command VM.vitals
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run testng/othervm -XX:VitalsSampleInterval=1 VitalsDCmdTest
 */

import org.testng.Assert;
import org.testng.annotations.Test;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;

public class VitalsDCmdTest {

    public void run(CommandExecutor executor) {

        OutputAnalyzer output = executor.execute("VM.vitals");
        output.shouldContain("--jvm--");
        output.shouldContain("--heap--");
        output.shouldContain("--meta--");
        output.shouldNotContain("Now"); // off by default

        output = executor.execute("VM.vitals reverse");
        output.shouldContain("--jvm--");
        output.shouldContain("--heap--");
        output.shouldContain("--meta--");
        output.shouldNotContain("Now"); // off by default

        output = executor.execute("VM.vitals scale=m");
        output.shouldContain("--jvm--");
        output.shouldContain("--heap--");
        output.shouldContain("--meta--");
        output.shouldNotContain("Now"); // off by default

        output = executor.execute("VM.vitals scale=1");
        output.shouldContain("--jvm--");
        output.shouldContain("--heap--");
        output.shouldContain("--meta--");
        output.shouldNotContain("Now"); // off by default

        output = executor.execute("VM.vitals raw");
        output.shouldContain("--jvm--");
        output.shouldContain("--heap--");
        output.shouldContain("--meta--");
        output.shouldNotContain("Now"); // off by default

        output = executor.execute("VM.vitals now");
        output.shouldContain("--jvm--");
        output.shouldContain("--heap--");
        output.shouldContain("--meta--");
        output.shouldContain("Now");

        output = executor.execute("VM.vitals reverse now");
        output.shouldContain("--jvm--");
        output.shouldContain("--heap--");
        output.shouldContain("--meta--");
        output.shouldContain("Now");

        output = executor.execute("VM.vitals csv");
        output.shouldContain("jvm-heap-comm,jvm-heap-used,jvm-meta-comm,jvm-meta-used");

        output = executor.execute("VM.vitals csv now");
        output.shouldContain("jvm-heap-comm,jvm-heap-used,jvm-meta-comm,jvm-meta-used");
        output.shouldContain("Now");

        output = executor.execute("VM.vitals csv reverse");
        output.shouldContain("jvm-heap-comm,jvm-heap-used,jvm-meta-comm,jvm-meta-used");

        output = executor.execute("VM.vitals csv reverse raw");
        output.shouldContain("jvm-heap-comm,jvm-heap-used,jvm-meta-comm,jvm-meta-used");

        output = executor.execute("VM.vitals csv now reverse raw");
        output.shouldContain("jvm-heap-comm,jvm-heap-used,jvm-meta-comm,jvm-meta-used");
        output.shouldContain("Now");

    }

    @Test
    public void jmx() {
        run(new JMXExecutor());
        // wait two seconds to collect some samples, then repeat with filled tables
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        run(new JMXExecutor());
    }

}
