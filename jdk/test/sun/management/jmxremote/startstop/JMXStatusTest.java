/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.net.BindException;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.testng.annotations.*;
import static org.testng.Assert.*;

import jdk.testlibrary.ProcessTools;

/**
 * @test
 * @bug 8023093
 * @summary Performs a sanity test for the ManagementAgent.status diagnostic command.
 *          Management agent may be disable, started (only local connections) and started.
 *          The test asserts that the expected text is being printed.
 * @library /lib/testlibrary
 * @modules java.management/sun.management
 * @build jdk.testlibrary.* PortAllocator TestApp ManagementAgentJcmd
 * @run testng/othervm -XX:+UsePerfData JMXStatusTest
 */
public class JMXStatusTest {
    private final static String TEST_APP_NAME = "TestApp";

    private final static Pattern DISABLE_AGENT_STATUS = Pattern.compile(
        "Agent\\s*\\: disabled$"
    );

    private final static Pattern LOCAL_AGENT_STATUS = Pattern.compile(
        "Agent\\s*\\:\\s*enabled\\n+" +
        "Connection Type\\s*\\:\\s*local\\n+" +
        "Protocol\\s*\\:\\s*[a-z]+\\n+" +
        "Host\\s*\\:\\s*.+\\n+" +
        "URL\\s*\\:\\s*service\\:jmx\\:.+",
        Pattern.MULTILINE
    );

    private final static Pattern REMOTE_AGENT_STATUS = Pattern.compile(
        "Agent\\s*\\: enabled\\n+" +
        "Connection Type\\s*\\: remote\\n+" +
        "Protocol\\s*\\: [a-z]+\\n+" +
        "Host\\s*\\: .+\\n+" +
        "URL\\s*\\: service\\:jmx\\:.+\\n+" +
        "Properties\\s*\\:\\n+(\\s*\\S+\\s*=\\s*\\S+\\n*)+",
        Pattern.MULTILINE
    );

    private static ProcessBuilder testAppPb;
    private Process testApp;

    private ManagementAgentJcmd jcmd;

    @BeforeClass
    public static void setupClass() throws Exception {
        testAppPb = ProcessTools.createJavaProcessBuilder(
            "-cp", System.getProperty("test.class.path"),
            "-XX:+UsePerfData",
            TEST_APP_NAME
        );
    }

    @BeforeTest
    public void setup() {
        jcmd = new ManagementAgentJcmd(TEST_APP_NAME, false);
    }

    @BeforeMethod
    public void startTestApp() throws Exception {
        testApp = ProcessTools.startProcess(
            TEST_APP_NAME, testAppPb,
            (Predicate<String>)l->l.trim().equals("main enter")
        );
    }

    @AfterMethod
    public void stopTestApp() throws Exception {
        testApp.getOutputStream().write(1);
        testApp.getOutputStream().flush();
        testApp.waitFor();
        testApp = null;
    }

    @Test
    public void testAgentDisabled() throws Exception {
        String status = jcmd.status();
        assertStatusMatches(DISABLE_AGENT_STATUS, status);
    }

    @Test
    public void testAgentLocal() throws Exception {
        jcmd.startLocal();
        String status = jcmd.status();

        assertStatusMatches(LOCAL_AGENT_STATUS, status);
    }

    @Test
    public void testAgentRemote() throws Exception {
        while (true) {
            try {
                int[] ports = PortAllocator.allocatePorts(1);
                jcmd.start(
                    "jmxremote.port=" + ports[0],
                    "jmxremote.authenticate=false",
                    "jmxremote.ssl=false"
                );
                String status = jcmd.status();

                assertStatusMatches(REMOTE_AGENT_STATUS, status);
                return;
            } catch (BindException e) {
                System.out.println("Failed to allocate ports. Retrying ...");
            }
        }
    }

    private void assertStatusMatches(Pattern expected, String value) {
        assertStatusMatches(expected, value, "");
    }

    private void assertStatusMatches(Pattern expected, String value, String msg) {
        int idx = value.indexOf('\n');
        if (idx > -1) {
            value = value.substring(idx + 1).trim();
            assertTrue(expected.matcher(value).find(), msg);
        } else {
            fail("The management agent status must contain more then one line of text");
        }
    }
}
