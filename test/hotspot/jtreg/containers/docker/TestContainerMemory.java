/*
 * Copyright (c) 2025, Red Hat, Inc.
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
 * @bug 8360651
 * @key cgroups
 * @summary Test JVM's correct detection of memory limit in a container
 * @requires container.support
 * @library /test/lib ../
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.platform
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @build ContainerMemory jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:whitebox.jar -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI TestContainerMemory
 */
import java.util.function.Consumer;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.whitebox.WhiteBox;

import static jdk.test.lib.Asserts.assertNotNull;

public class TestContainerMemory {
    private static final String imageName = Common.imageName("container-memory");
    private static final WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String[] args) throws Exception {
        if (!DockerTestUtils.canTestDocker()) {
            return;
        }

        Common.prepareWhiteBox();
        DockerTestUtils.buildJdkContainerImage(imageName);

        try {
            testWithMemoryLimit();
            testWithoutMemoryLimit();
        } finally {
            if (!DockerTestUtils.RETAIN_IMAGE_AFTER_TEST) {
                DockerTestUtils.removeDockerImage(imageName);
            }
        }
    }

    private static void testWithMemoryLimit() throws Exception {
        String memLimit = "100m"; // Any limit will do
        Common.logNewTestCase("testing container memory with limit: " + memLimit);

        DockerRunOptions opts = Common.newOpts(imageName, "ContainerMemory");
        opts.addClassOptions("hasMemoryLimit", "true");
        Common.addWhiteBoxOpts(opts);
        Common.addTestClassPath(opts);
        opts.addDockerOpts("--memory", memLimit);
        // We are interested in the default option when run in a container, so
        // don't append test java options
        opts.appendTestJavaOptions = false;
        Common.run(opts)
            .shouldContain("hasMemoryLimit=true");
    }

    private static void testWithoutMemoryLimit() throws Exception {
        Common.logNewTestCase("testing container without limit");

        DockerRunOptions opts = Common.newOpts(imageName, "ContainerMemory");
        opts.addClassOptions("hasMemoryLimit", "false");
        Common.addWhiteBoxOpts(opts);
        Common.addTestClassPath(opts);
        // We are interested in the default option when run in a container, so
        // don't append test java options
        opts.appendTestJavaOptions = false;
        Common.run(opts)
            .shouldContain("hasMemoryLimit=false");
    }
}
