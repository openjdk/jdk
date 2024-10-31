/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import jdk.test.lib.process.OutputAnalyzer;
import org.testng.annotations.Test;

import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;

/*
 * @test
 * @summary Test of diagnostic command VM.usage_metadata
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run testng/othervm UsageMetadataTest
 */
public class UsageMetadataTest {

    private static Map.Entry<String, Predicate<String>> newEntry(String field, Predicate<String> predicate) {
        return Map.entry(field, predicate);
    };

    private static Map.Entry<String, Predicate<String>> newEntry(String field) {
        return newEntry(field, (line) -> line.contains(field));
    };

    private static final Map<String, Predicate<String>> FIELDS = Map.ofEntries(
         newEntry("timestamp"),
         newEntry("jvm.starttime"),
         newEntry("java.home"),
         newEntry("java.version"),
         newEntry("java.vm.version"),
         newEntry("sun.java.launcher"),
         newEntry("jvm.flags"),
         newEntry("jvm.args"),
         newEntry("sun.java.command"),
         newEntry("java.class.path"),
         // newEntry("jdk.module.path"),
         // newEntry("jdk.main.module"),
         // newEntry("jdk.main.module.class"),
         // newEntry("jdk.upgrade.module.path"),
         newEntry("jvm.pid"),
         newEntry("jvm.uptime.ms"),
         // newEntry("jvm.ctr.info.type"),
         // newEntry("jvm.ctr.info.name"),
         // newEntry("jvm.ctr.info.memory.limit"),
         // newEntry("jvm.ctr.info.active.cpus"),
         newEntry("os.hostname"),
         newEntry("os.name"),
         newEntry("os.version"),
         newEntry("os.arch")
    );

    public void run(CommandExecutor executor) {
        OutputAnalyzer output = executor.execute("VM.usage_metadata");

        output.stderrShouldBeEmpty();

        /*
         * the default o/p fields for VM.usage_metadata are:
         *
         * - timestamp
         * - jvm.starttime
         * - java.home
         * - java.version
         * - java.vm.version
         * - sun.java.launcher
         * - jvm.flags
         * - jvm.args
         * - sun.java.command
         * - java.classpath
         * - jdk.module.path (if present)
         * - jdk.main.module (if present)
         * - jdk.main.module.class (if present)
         * - jdk.upgrade.module.path (if present)
         * - jvm.pid
         * - jvm.uptime
         * - jvm.ctr.info.* (if present)
         * - user.name
         * - user.dir
         * - os.hostname
         * - os.name
         * - os.version
         * - os.arch
         */

        final var stdout =  output.getStdout();

        for (var field : FIELDS.entrySet()) {
            if (!field.getValue().test(stdout)) {
                output.reportDiagnosticSummary();

                throw new RuntimeException("'" + field.getKey() + "' misssing from stdout");
            }
        }
    }

    @Test
    public void jmx() {
        run(new JMXExecutor());
    }
}
