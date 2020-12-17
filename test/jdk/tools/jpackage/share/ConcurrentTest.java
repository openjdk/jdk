/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Date;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.Annotations.Test;

/**
 * Concurrent test.  Using ToolProvider, run several jpackage test concurrently
 */

/*
 * @test
 * @summary Concurrent jpackage command runs using ToolProvider
 * @library ../helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @compile ConcurrentTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=ConcurrentTest
 */
public class ConcurrentTest {

    @Test
    public static void test() {

        final JPackageCommand cmd1 =
                JPackageCommand.helloAppImage("com.other/com.other.Hello")
        .useToolProvider(true)
        .setPackageType(PackageType.getDefault())
        .setArgumentValue("--name", "ConcurrentOtherInstaller");

        final JPackageCommand cmd2 =
                JPackageCommand.helloAppImage("Hello")
        .useToolProvider(true)
        .setPackageType(PackageType.IMAGE)
        .setArgumentValue("--name", "ConcurrentAppImage");

        Date[] times = race(cmd1, cmd2);
        TKit.assertTrue(times[0].after(times[1]),
                "We expected app-image command to finish first, but times[0] is "
                + times[0] + " and times[1] is" + times[1]);

        cmd1.useToolProvider(false);
        cmd1.useToolProvider(false);

        times = race(cmd1, cmd2);
        TKit.assertTrue(times[0].after(times[1]),
                "We expected app-image command to finish first, but times[0] is "
                + times[0] + " and times[1] is" + times[1]);
    }

    private static Date[] race(JPackageCommand cmd1, JPackageCommand cmd2) {
        final Date[] times = new Date[2];

        Thread t1 = new Thread(new Runnable() {
            public void run() {
                cmd1.execute();
                times[0] = new Date();
            }
        });

        Thread t2 = new Thread(new Runnable() {
            public void run() {
                cmd2.execute();
                times[1] = new Date();
            }
        });
        try {
            t1.start();
            t2.start();

            t1.join();
            t2.join();
            return times;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
