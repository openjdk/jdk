/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8286030
 * @key cgroups
 * @summary Test for hsperfdata file name conflict when two containers share the same /tmp directory
 * @requires container.support
 * @requires !vm.asan
 * @library /test/lib
 * @build WaitForFlagFile
 * @run driver ShareTmpDir
 */

import java.io.File;
import java.io.FileOutputStream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;

public class ShareTmpDir {
    private static final String imageName = Common.imageName("sharetmpdir");

    public static void main(String[] args) throws Exception {
        if (!DockerTestUtils.canTestDocker()) {
            return;
        }

        DockerTestUtils.buildJdkContainerImage(imageName);

        try {
            test();
        } finally {
            if (!DockerTestUtils.RETAIN_IMAGE_AFTER_TEST) {
                DockerTestUtils.removeDockerImage(imageName);
            }
        }
    }

    static OutputAnalyzer out1, out2;

    private static void test() throws Exception {
        File sharedtmpdir = new File("sharedtmpdir");
        File flag = new File(sharedtmpdir, "flag");
        File started = new File(sharedtmpdir, "started");
        sharedtmpdir.mkdir();
        flag.delete();
        started.delete();
        DockerRunOptions opts = new DockerRunOptions(imageName, "/jdk/bin/java", "WaitForFlagFile");
        opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/");
        opts.addDockerOpts("--volume", sharedtmpdir.getAbsolutePath() + ":/tmp/");
        opts.addJavaOpts("-Xlog:os+container=trace", "-Xlog:perf+memops=debug", "-cp", "/test-classes/");

        Thread t1 = new Thread() {
                public void run() {
                    try { out1 = Common.run(opts); } catch (Exception e) { e.printStackTrace(); }
                }
            };
        t1.start();

        Thread t2 = new Thread() {
                public void run() {
                    try { out2 = Common.run(opts); } catch (Exception e) { e.printStackTrace(); }
                }
            };
        t2.start();

        while (!started.exists()) {
            System.out.println("Wait for at least one JVM to start");
            Thread.sleep(1000);
        }

        // Set the flag for the two JVMs to exit
        FileOutputStream fout = new FileOutputStream(flag);
        fout.close();

        t1.join();
        t2.join();

        Pattern pattern = Pattern.compile("perf,memops.*Trying to open (/tmp/hsperfdata_[a-z0-9]*/[0-9]*)");
        Matcher matcher;

        matcher = pattern.matcher(out1.getStdout());
        Asserts.assertTrue(matcher.find());
        String file1 =  matcher.group(1);

        matcher = pattern.matcher(out2.getStdout());
        Asserts.assertTrue(matcher.find());
        String file2 =  matcher.group(1);

        Asserts.assertTrue(file1 != null);
        Asserts.assertTrue(file2 != null);

        if (file1.equals(file2)) {
            // This should be the common case -- the first started process in a container should
            // have pid==1.
            // One of the two containers must fail to create the hsperf file.
            String s = "Cannot use file " + file1 + " because it is locked by another process";
            Asserts.assertTrue(out1.getStdout().contains(s) ||
                               out2.getStdout().contains(s));
        } else {
            throw new SkippedException("Java in the two containers don't have the same pid: " + file1 + " vs " + file2);
        }
    }
}
