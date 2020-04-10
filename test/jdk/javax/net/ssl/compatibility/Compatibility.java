/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @summary This test is used to check the interop compatibility on JSSE among
 *     different JDK releases.
 *     Note that, this is a manual test. For more details about the test and
 *     its usages, please look through README.
 *
 * @library /test/lib ../TLSCommon
 * @compile -source 1.7 -target 1.7 JdkUtils.java Server.java Client.java
 * @run main/manual Compatibility
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;

public class Compatibility {

    protected List<UseCase> getUseCases() {
        return UseCase.getAllUseCases();
    }

    protected Set<JdkInfo> getJdkInfos() {
        return jdkInfoList();
    }

    protected List<TestCase> runTest() throws Exception {
        Set<JdkInfo> jdkInfos = getJdkInfos();

        List<TestCase> testCases = new ArrayList<>();
        ExecutorService executor = Executors.newCachedThreadPool();
        PrintStream origStdOut = System.out;
        PrintStream origStdErr = System.err;

        try (PrintStream printStream = new PrintStream(
                new FileOutputStream(Utils.TEST_LOG, true))) {
            System.setOut(printStream);
            System.setErr(printStream);

            System.out.println(Utils.startHtml());
            System.out.println(Utils.startPre());

            JdkInfo targetJdkInfo = new JdkInfo(System.getProperty("test.jdk"));
            for (UseCase useCase : getUseCases()) {
                for (JdkInfo jdkInfo : jdkInfos) {
                    testCases.add(
                            runCase(targetJdkInfo, jdkInfo, useCase, executor));
                    testCases.add(
                            runCase(jdkInfo, targetJdkInfo, useCase, executor));
                }
            }

            System.out.println(Utils.endPre());
            System.out.println(Utils.endHtml());
        }
        System.setOut(origStdOut);
        System.setErr(origStdErr);
        executor.shutdown();

        return testCases;
    }

    private TestCase runCase(JdkInfo serverJdk, JdkInfo clientJdk,
            UseCase useCase, ExecutorService executor) throws Exception {
        Map<String, String> props = new LinkedHashMap<>();
        if (Utils.DEBUG) {
            props.put("javax.net.debug", "all");
        }

        props.put("java.security.properties", Utils.SECURITY_PROPERTIES_FILE);

        props.put(Utils.PROP_CERTS, Utils.join(Utils.VALUE_DELIMITER,
                Cert.getCerts(useCase.cipherSuite)));
        props.put(Utils.PROP_CLIENT_AUTH, String.valueOf(useCase.clientAuth));
        if (useCase.appProtocol != UseCase.AppProtocol.NONE) {
            props.put(Utils.PROP_APP_PROTOCOLS,
                    Utils.join(Utils.VALUE_DELIMITER,
                            useCase.appProtocol.appProtocols));
            props.put(Utils.PROP_NEGO_APP_PROTOCOL,
                    useCase.appProtocol.negoAppProtocol);
        }
        props.put(Utils.PROP_SERVER_JDK, serverJdk.version);

        props.put(Utils.PROP_SUPPORTS_SNI_ON_SERVER,
                serverJdk.supportsSNI + "");
        props.put(Utils.PROP_SUPPORTS_ALPN_ON_SERVER,
                serverJdk.supportsALPN + "");

        TestCase testCase = new TestCase(serverJdk, clientJdk, useCase);
        System.out.println(Utils.anchorName(testCase.toString(),
                "===== Case start ====="));
        System.out.println(testCase.toString());

        props.put(Utils.PROP_NEGATIVE_CASE_ON_SERVER,
                testCase.negativeCaseOnServer + "");
        props.put(Utils.PROP_NEGATIVE_CASE_ON_CLIENT,
                testCase.negativeCaseOnClient + "");

        Future<OutputAnalyzer> serverFuture = executor.submit(() -> {
            return runServer(serverJdk.jdkPath, props);
        });
        int port = waitForServerStarted();
        System.out.println("port=" + port);

        props.put(Utils.PROP_PORT, port + "");

        props.put(Utils.PROP_CLIENT_JDK, clientJdk.version);

        props.put(Utils.PROP_PROTOCOL, useCase.protocol.name);
        props.put(Utils.PROP_CIPHER_SUITE, useCase.cipherSuite.name());
        props.put(Utils.PROP_SUPPORTS_SNI_ON_CLIENT,
                clientJdk.supportsSNI + "");
        props.put(Utils.PROP_SUPPORTS_ALPN_ON_CLIENT,
                clientJdk.supportsALPN + "");
        if (useCase.serverName != UseCase.ServerName.NONE) {
            props.put(Utils.PROP_SERVER_NAME,
                    useCase.serverName.name);
        }

        Status clientStatus = null;
        if (port != -1) {
            String clientOutput = runClient(clientJdk.jdkPath,
                    props).getOutput();
            clientStatus = getStatus(clientOutput);
        }

        String serverOutput = serverFuture.get().getOutput();
        Status serverStatus = getStatus(serverOutput);
        testCase.setStatus(caseStatus(serverStatus, clientStatus));
        System.out.printf(
                "ServerStatus=%s, ClientStatus=%s, CaseStatus=%s%n",
                serverStatus, clientStatus, testCase.getStatus());

        System.out.println("===== Case end =====");
        return testCase;
    }

    // Generates the test result report.
    protected boolean generateReport(List<TestCase> testCases)
            throws IOException {
        boolean failed = false;
        StringBuilder report = new StringBuilder();
        report.append(Utils.startHtml());
        report.append(Utils.tableStyle());
        report.append(Utils.startTable());
        report.append(Utils.row(
                "No.",
                "Client",
                "Server",
                "Protocol",
                "Cipher suite",
                "Client auth",
                "SNI",
                "ALPN",
                "Status",
                "Reason"));
        for (int i = 0, size = testCases.size(); i < size; i++) {
            TestCase testCase = testCases.get(i);

            report.append(Utils.row(
                    Utils.anchorLink(
                            Utils.TEST_LOG,
                            testCase.toString(),
                            i + ""),
                    testCase.clientJdk.version,
                    testCase.serverJdk.version,
                    testCase.useCase.protocol.name,
                    testCase.useCase.cipherSuite,
                    Utils.boolToStr(
                            testCase.useCase.clientAuth),
                    Utils.boolToStr(
                            testCase.useCase.serverName == UseCase.ServerName.EXAMPLE),
                    Utils.boolToStr(
                            testCase.useCase.appProtocol == UseCase.AppProtocol.EXAMPLE),
                    testCase.getStatus(),
                    testCase.reason()));

            failed = failed || testCase.isFailed();
        }
        report.append(Utils.endTable());
        report.append(Utils.endHtml());

        generateFile("report.html", report.toString());
        return failed;
    }

    protected void run() throws Exception {
        System.out.println("Test start");
        List<TestCase> testCases= runTest();
        System.out.println("Test end");

        boolean failed = generateReport(testCases);
        System.out.println("Report was generated.");

        if (failed) {
            throw new RuntimeException("At least one case failed. "
                    + "Please check logs for more details.");
        }
    }

    public static void main(String[] args) throws Throwable {
        new Compatibility().run();;
    }

    private static Status getStatus(String log) {
        if (log.contains(Status.UNEXPECTED_SUCCESS.name())) {
            return Status.UNEXPECTED_SUCCESS;
        } else if (log.contains(Status.SUCCESS.name())) {
            return Status.SUCCESS;
        } else if (log.contains(Status.EXPECTED_FAIL.name())) {
            return Status.EXPECTED_FAIL;
        } else if (log.contains(Status.TIMEOUT.name())) {
            return Status.TIMEOUT;
        } else {
            return Status.FAIL;
        }
    }

    private static Status caseStatus(Status serverStatus, Status clientStatus) {
        return (serverStatus == Status.EXPECTED_FAIL && clientStatus != Status.SUCCESS)
                || (clientStatus == Status.EXPECTED_FAIL && serverStatus != Status.SUCCESS)
                ? Status.EXPECTED_FAIL
                : (serverStatus == clientStatus ? serverStatus : Status.FAIL);
    }

    // Retrieves JDK info from the file which is specified by jdkListFile.
    private static Set<JdkInfo> jdkInfoList() {
        List<String> jdkList = jdkList();

        Set<JdkInfo> jdkInfoList = new LinkedHashSet<>();
        for (String jdkPath : jdkList) {
            JdkInfo jdkInfo = new JdkInfo(jdkPath);
            // JDK version must be unique.
            if (!jdkInfoList.add(jdkInfo)) {
                System.out.println("The JDK version is duplicate: " + jdkPath);
            }
        }
        return jdkInfoList;
    }

    private static List<String> jdkList() {
        String listFile = System.getProperty("jdkListFile");
        System.out.println("jdk list file: " + listFile);
        if (listFile != null && Files.exists(Paths.get(listFile))) {
            try (Stream<String> lines = Files.lines(Paths.get(listFile))) {
                return lines.filter(line -> {
                    return !line.trim().isEmpty();
                }).collect(Collectors.toList());
            } catch (IOException e) {
                throw new RuntimeException("Cannot get jdk list", e);
            }
        } else {
            return new ArrayList<>();
        }
    }

    // Checks if server is already launched, and returns server port.
    private static int waitForServerStarted()
            throws IOException, InterruptedException {
        System.out.print("Waiting for server");
        long deadline = System.currentTimeMillis() + Utils.TIMEOUT;
        int port;
        while ((port = getServerPort()) == -1
                && System.currentTimeMillis() < deadline) {
            System.out.print(".");
            TimeUnit.SECONDS.sleep(1);
        }
        System.out.println();

        return port;
    }

    // Retrieves the latest server port from port.log.
    private static int getServerPort() throws IOException {
        if (!Files.exists(Paths.get(Utils.PORT_LOG))) {
            return -1;
        }

        try (Stream<String> lines = Files.lines(Paths.get(Utils.PORT_LOG))) {
            return Integer.valueOf(lines.findFirst().get());
        }
    }

    private static OutputAnalyzer runServer(String jdkPath,
            Map<String, String> props) {
        return ProcessUtils.java(jdkPath, props, Server.class);
    }

    private static OutputAnalyzer runClient(String jdkPath,
            Map<String, String> props) {
        return ProcessUtils.java(jdkPath, props, Client.class);
    }

    private static void generateFile(String path, String content)
            throws IOException {
        try(FileWriter writer = new FileWriter(new File(path))) {
            writer.write(content);
        }
    }
}
