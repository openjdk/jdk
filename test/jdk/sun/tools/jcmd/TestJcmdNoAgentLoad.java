/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.test.lib.Asserts.*;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.List;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;
import jdk.test.lib.util.JarUtils;

/*
 * @test id=default
 * @bug 8304438
 * @summary JVMTI.agent_load should obey EnableDynamicAgentLoading (by default)
 * @modules jdk.attach jdk.jcmd
 * @library /test/lib
 * @build JavaAgent
 * @run main/othervm TestJcmdNoAgentLoad
 */

/*
 * @test id=disabled
 * @bug 8304438
 * @summary JVMTI.agent_load should obey EnableDynamicAgentLoading (disabled)
 * @modules jdk.attach jdk.jcmd
 * @library /test/lib
 * @build JavaAgent
 * @run main/othervm -XX:-EnableDynamicAgentLoading TestJcmdNoAgentLoad
 */

/*
 * @test id=enabled
 * @bug 8304438
 * @summary JVMTI.agent_load should obey EnableDynamicAgentLoading (enabled)
 * @modules jdk.attach jdk.jcmd
 * @library /test/lib
 * @build JavaAgent
 * @run main/othervm -XX:+EnableDynamicAgentLoading TestJcmdNoAgentLoad
 */


public class TestJcmdNoAgentLoad {
    private static final String PTRN = "Dynamic agent loading is not enabled";
    private static boolean dynamicLoadingEnabled = true;
    private static final String TEST_CLASSES = System.getProperty("test.classes");
    private static String javaAgent;

    static {
        // get VM option EnableDynamicAgentLoading value
        HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        VMOption dynamicLoadingEnabledOpt = bean.getVMOption("EnableDynamicAgentLoading");
        dynamicLoadingEnabled = dynamicLoadingEnabledOpt.getValue().equals("true");
    }

    public static void main(String[] args) throws Exception {
        setup();
        testNoAgentLoad(new String[] { "JVMTI.agent_load", javaAgent });
    }

    private static void setup() throws Exception {
        // create JAR file with Java agent
        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(new Attributes.Name("Agent-Class"), "JavaAgent");
        Path jarfile = Path.of("javaagent.jar");
        Path classes = Path.of(TEST_CLASSES);
        JarUtils.createJarFile(jarfile, man, classes, Path.of("JavaAgent.class"));
        javaAgent = jarfile.toString();
    }

    private static void testNoAgentLoad(String... jcmdArgs) throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd(jcmdArgs);

        output.shouldHaveExitValue(0);
        if (dynamicLoadingEnabled) {
            output.shouldNotContain(PTRN);
        } else {
            output.shouldContain(PTRN);
        }
    }
}
