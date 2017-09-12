/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @summary This test is used to verify the compatibility on jarsigner cross
 *     different JDK releases. It also can be used to check jar signing (w/
 *     and w/o TSA) and verifying on some specific key algorithms and digest
 *     algorithms.
 *     Note that, this is a manual test. For more details about the test and
 *     its usages, please look through README.
 *
 * @modules java.base/sun.security.pkcs
 *          java.base/sun.security.timestamp
 *          java.base/sun.security.tools.keytool
 *          java.base/sun.security.util
 *          java.base/sun.security.x509
 * @library /test/lib /lib/testlibrary ../warnings
 * @compile -source 1.6 -target 1.6 JdkUtils.java
 * @run main/manual/othervm Compatibility
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.JarUtils;

public class Compatibility {

    private static final String TEST_JAR_NAME = "test.jar";

    private static final String TEST_SRC = System.getProperty("test.src");
    private static final String TEST_CLASSES = System.getProperty("test.classes");
    private static final String TEST_JDK = System.getProperty("test.jdk");
    private static final String TEST_JARSIGNER = jarsignerPath(TEST_JDK);

    private static final String PROXY_HOST = System.getProperty("proxyHost");
    private static final String PROXY_PORT = System.getProperty("proxyPort", "80");

    // An alternative security properties file.
    // The test provides a default one, which only contains two lines:
    // jdk.certpath.disabledAlgorithms=MD2, MD5
    // jdk.jar.disabledAlgorithms=MD2, MD5
    private static final String JAVA_SECURITY = System.getProperty(
            "javaSecurityFile", TEST_SRC + "/java.security");

    private static final String PASSWORD = "testpass";
    private static final String KEYSTORE = "testKeystore";

    private static final String RSA = "RSA";
    private static final String DSA = "DSA";
    private static final String EC = "EC";
    private static final String[] KEY_ALGORITHMS = new String[] {
            RSA,
            DSA,
            EC};

    private static final String SHA1 = "SHA-1";
    private static final String SHA256 = "SHA-256";
    private static final String SHA512 = "SHA-512";
    private static final String DEFAULT = "DEFAULT";
    private static final String[] DIGEST_ALGORITHMS = new String[] {
            SHA1,
            SHA256,
            SHA512,
            DEFAULT};

    private static final boolean[] EXPIRED = new boolean[] {
            false,
            true};

    private static final Calendar CALENDAR = Calendar.getInstance();
    private static final DateFormat DATE_FORMAT
            = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    // The certificate validity period in minutes. The default value is 1440
    // minutes, namely 1 day.
    private static final int CERT_VALIDITY
            = Integer.valueOf(System.getProperty("certValidity", "1440"));
    static {
        if (CERT_VALIDITY < 1 || CERT_VALIDITY > 1440) {
            throw new RuntimeException(
                    "certValidity if out of range [1, 1440]: " + CERT_VALIDITY);
        }
    }

    // If true, an additional verifying will be triggered after all of
    // valid certificates expire. The default value is false.
    public static final boolean DELAY_VERIFY
            = Boolean.valueOf(System.getProperty("delayVerify", "false"));

    private static long lastCertStartTime;

    private static DetailsOutputStream detailsOutput;

    public static void main(String[] args) throws Throwable {
        // Backups stdout and stderr.
        PrintStream origStdOut = System.out;
        PrintStream origStdErr = System.err;

        detailsOutput = new DetailsOutputStream();

        // Redirects the system output to a custom one.
        PrintStream printStream = new PrintStream(detailsOutput);
        System.setOut(printStream);
        System.setErr(printStream);

        List<TsaInfo> tsaList = tsaInfoList();
        if (tsaList.size() == 0) {
            throw new RuntimeException("TSA service is mandatory.");
        }

        List<JdkInfo> jdkInfoList = jdkInfoList();
        List<CertInfo> certList = createCertificates(jdkInfoList);
        createJar();
        List<SignItem> signItems = test(jdkInfoList, tsaList, certList);

        boolean failed = generateReport(tsaList, signItems);

        // Restores the original stdout and stderr.
        System.setOut(origStdOut);
        System.setErr(origStdErr);

        if (failed) {
            throw new RuntimeException("At least one test case failed. "
                    + "Please check the failed row(s) in report.html "
                    + "or failedReport.html.");
        }
    }

    // Creates a jar file that contains an empty file.
    private static void createJar() throws IOException {
        String testFile = "test";
        new File(testFile).createNewFile();
        JarUtils.createJar(TEST_JAR_NAME, testFile);
    }

    // Creates a key store that includes a set of valid/expired certificates
    // with various algorithms.
    private static List<CertInfo> createCertificates(List<JdkInfo> jdkInfoList)
            throws Throwable {
        List<CertInfo> certList = new ArrayList<CertInfo>();
        Set<String> expiredCertFilter = new HashSet<String>();

        for(JdkInfo jdkInfo : jdkInfoList) {
            for(String keyAlgorithm : KEY_ALGORITHMS) {
                for(String digestAlgorithm : DIGEST_ALGORITHMS) {
                    for(int keySize : keySizes(keyAlgorithm)) {
                        for(boolean expired : EXPIRED) {
                            // It creates only one expired certificate for one
                            // key algorithm.
                            if (expired
                                    && !expiredCertFilter.add(keyAlgorithm)) {
                                continue;
                            }

                            CertInfo certInfo = new CertInfo(
                                    jdkInfo.version,
                                    keyAlgorithm,
                                    digestAlgorithm,
                                    keySize,
                                    expired);
                            if (!certList.contains(certInfo)) {
                                String alias = createCertificate(
                                        jdkInfo.jdkPath, certInfo);
                                if (alias != null) {
                                    certList.add(certInfo);
                                }
                            }
                        }
                    }
                }
            }
        }

        return certList;
    }

    // Creates/Updates a key store that adds a certificate with specific algorithm.
    private static String createCertificate(String jdkPath, CertInfo certInfo)
            throws Throwable {
        String alias = certInfo.alias();

        List<String> arguments = new ArrayList<String>();
        arguments.add("-J-Djava.security.properties=" + JAVA_SECURITY);
        arguments.add("-v");
        arguments.add("-storetype");
        arguments.add("jks");
        arguments.add("-genkey");
        arguments.add("-keyalg");
        arguments.add(certInfo.keyAlgorithm);
        String sigalg = sigalg(certInfo.digestAlgorithm, certInfo.keyAlgorithm);
        if (sigalg != null) {
            arguments.add("-sigalg");
            arguments.add(sigalg);
        }
        if (certInfo.keySize != 0) {
            arguments.add("-keysize");
            arguments.add(certInfo.keySize + "");
        }
        arguments.add("-dname");
        arguments.add("CN=Test");
        arguments.add("-alias");
        arguments.add(alias);
        arguments.add("-keypass");
        arguments.add(PASSWORD);
        arguments.add("-storepass");
        arguments.add(PASSWORD);

        arguments.add("-startdate");
        arguments.add(startDate(certInfo.expired));
        arguments.add("-validity");
        arguments.add("1");
        arguments.add("-keystore");
        arguments.add(KEYSTORE);

        OutputAnalyzer outputAnalyzer = execTool(
                jdkPath + "/bin/keytool",
                arguments.toArray(new String[arguments.size()]));
        if (outputAnalyzer.getExitValue() == 0
                && !outputAnalyzer.getOutput().matches("[Ee]xception")) {
            return alias;
        } else {
            return null;
        }
    }

    private static String sigalg(String digestAlgorithm, String keyAlgorithm) {
        if (digestAlgorithm == DEFAULT) {
            return null;
        }

        String keyName = keyAlgorithm == EC ? "ECDSA" : keyAlgorithm;
        return digestAlgorithm.replace("-", "") + "with" + keyName;
    }

    // The validity period of a certificate always be 1 day. For creating an
    // expired certificate, the start date is the time before 1 day, then the
    // certificate expires immediately. And for creating a valid certificate,
    // the start date is the time before (1 day - CERT_VALIDITY minutes), then
    // the certificate will expires in CERT_VALIDITY minutes.
    private static String startDate(boolean expiredCert) {
        CALENDAR.setTime(new Date());
        CALENDAR.add(Calendar.DAY_OF_MONTH, -1);
        if (!expiredCert) {
            CALENDAR.add(Calendar.MINUTE, CERT_VALIDITY);
        }
        Date startDate = CALENDAR.getTime();
        lastCertStartTime = startDate.getTime();
        return DATE_FORMAT.format(startDate);
    }

    // Retrieves JDK info from the file which is specified by property jdkListFile,
    // or from property jdkList if jdkListFile is not available.
    private static List<JdkInfo> jdkInfoList() throws Throwable {
        String[] jdkList = list("jdkList");
        if (jdkList.length == 0) {
            jdkList = new String[] { TEST_JDK };
        }

        List<JdkInfo> jdkInfoList = new ArrayList<JdkInfo>();
        for (String jdkPath : jdkList) {
            JdkInfo jdkInfo = new JdkInfo(jdkPath);
            // The JDK version must be unique.
            if (!jdkInfoList.contains(jdkInfo)) {
                jdkInfoList.add(jdkInfo);
            } else {
                System.out.println("The JDK version is duplicate: " + jdkPath);
            }
        }
        return jdkInfoList;
    }

    // Retrieves TSA info from the file which is specified by property tsaListFile,
    // or from property tsaList if tsaListFile is not available.
    private static List<TsaInfo> tsaInfoList() throws IOException {
        String[] tsaList = list("tsaList");

        List<TsaInfo> tsaInfoList = new ArrayList<TsaInfo>();
        for (int i = 0; i < tsaList.length; i++) {
            String[] values = tsaList[i].split(";digests=");

            String[] digests = new String[0];
            if (values.length == 2) {
                digests = values[1].split(",");
            }

            TsaInfo bufTsa = new TsaInfo(i, values[0]);

            for (String digest : digests) {
                bufTsa.addDigest(digest);
            }

            tsaInfoList.add(bufTsa);
        }

        return tsaInfoList;
    }

    private static String[] list(String listProp)
            throws IOException {
        String listFileProp = listProp + "File";
        String listFile = System.getProperty(listFileProp);
        if (!isEmpty(listFile)) {
            System.out.println(listFileProp + "=" + listFile);
            List<String> list = new ArrayList<String>();
            BufferedReader reader = new BufferedReader(
                    new FileReader(listFile));
            String line;
            while ((line = reader.readLine()) != null) {
                String item = line.trim();
                if (!item.isEmpty()) {
                    list.add(item);
                }
            }
            reader.close();
            return list.toArray(new String[list.size()]);
        }

        String list = System.getProperty(listProp);
        System.out.println(listProp + "=" + list);
        return !isEmpty(list) ? list.split("#") : new String[0];
    }

    private static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    // A JDK (signer) signs a jar with a variety of algorithms, and then all of
    // JDKs (verifiers), including the signer itself, try to verify the signed
    // jars respectively.
    private static List<SignItem> test(List<JdkInfo> jdkInfoList,
            List<TsaInfo> tsaInfoList, List<CertInfo> certList)
            throws Throwable {
        detailsOutput.transferPhase();
        List<SignItem> signItems = signing(jdkInfoList, tsaInfoList, certList);

        detailsOutput.transferPhase();
        for (SignItem signItem : signItems) {
            for (JdkInfo verifierInfo : jdkInfoList) {
                // JDK 6 doesn't support EC
                if (!verifierInfo.isJdk6()
                        || signItem.certInfo.keyAlgorithm != EC) {
                    verifying(signItem, VerifyItem.build(verifierInfo));
                }
            }
        }

        if (DELAY_VERIFY) {
            detailsOutput.transferPhase();
            System.out.print("Waiting for delay verifying");
            long lastCertExpirationTime = lastCertStartTime + 24 * 60 * 60 * 1000;
            while (System.currentTimeMillis() < lastCertExpirationTime) {
                TimeUnit.SECONDS.sleep(30);
                System.out.print(".");
            }
            System.out.println();

            System.out.println("Delay verifying starts");
            for (SignItem signItem : signItems) {
                for (VerifyItem verifyItem : signItem.verifyItems) {
                    verifying(signItem, verifyItem);
                }
            }
        }

        detailsOutput.transferPhase();

        return signItems;
    }

    private static List<SignItem> signing(List<JdkInfo> jdkInfos,
            List<TsaInfo> tsaList, List<CertInfo> certList) throws Throwable {
        List<SignItem> signItems = new ArrayList<SignItem>();

        Set<String> signFilter = new HashSet<String>();

        for (JdkInfo signerInfo : jdkInfos) {
            for (String keyAlgorithm : KEY_ALGORITHMS) {
                // JDK 6 doesn't support EC
                if (signerInfo.isJdk6() && keyAlgorithm == EC) {
                    continue;
                }

                for (String digestAlgorithm : DIGEST_ALGORITHMS) {
                    String sigalg = sigalg(digestAlgorithm, keyAlgorithm);
                    // If the signature algorithm is not supported by the JDK,
                    // it cannot try to sign jar with this algorithm.
                    if (sigalg != null && !signerInfo.isSupportedSigalg(sigalg)) {
                        continue;
                    }

                    // If the JDK doesn't support option -tsadigestalg, the
                    // associated cases just be ignored.
                    if (digestAlgorithm != DEFAULT
                            && !signerInfo.supportsTsadigestalg) {
                        continue;
                    }

                    for (int keySize : keySizes(keyAlgorithm)) {
                        for (boolean expired : EXPIRED) {
                            CertInfo certInfo = new CertInfo(
                                    signerInfo.version,
                                    keyAlgorithm,
                                    digestAlgorithm,
                                    keySize,
                                    expired);
                            if (!certList.contains(certInfo)) {
                                continue;
                            }

                            String tsadigestalg = digestAlgorithm != DEFAULT
                                                ? digestAlgorithm
                                                : null;

                            for (TsaInfo tsaInfo : tsaList) {
                                // It has to ignore the digest algorithm, which
                                // is not supported by the TSA server.
                                if(!tsaInfo.isDigestSupported(tsadigestalg)) {
                                    continue;
                                }

                                String tsaUrl = tsaInfo.tsaUrl;
                                if (TsaFilter.filter(
                                        signerInfo.version,
                                        digestAlgorithm,
                                        expired,
                                        tsaInfo.index)) {
                                    tsaUrl = null;
                                }

                                String signedJar = "JDK_"
                                        + signerInfo.version + "-CERT_"
                                        + certInfo
                                        + (tsaUrl == null
                                           ? ""
                                           : "-TSA_" + tsaInfo.index);

                                // It has to ignore the same jar signing.
                                if (!signFilter.add(signedJar)) {
                                    continue;
                                }

                                SignItem signItem = SignItem.build()
                                        .certInfo(certInfo)
                                        .version(signerInfo.version)
                                        .signatureAlgorithm(sigalg)
                                        .tsaDigestAlgorithm(
                                                tsaUrl == null
                                                ? null
                                                : tsadigestalg)
                                        .tsaIndex(
                                                tsaUrl == null
                                                ? -1
                                                : tsaInfo.index)
                                        .signedJar(signedJar);
                                String signingId = signingId(signItem);
                                detailsOutput.writeAnchorName(signingId,
                                        "Signing: " + signingId);

                                OutputAnalyzer signOA = signJar(
                                        signerInfo.jarsignerPath,
                                        sigalg,
                                        tsadigestalg,
                                        tsaUrl,
                                        certInfo.alias(),
                                        signedJar);
                                Status signingStatus = signingStatus(signOA);
                                signItem.status(signingStatus);

                                if (signingStatus != Status.ERROR) {
                                    // Using the testing JDK, which is specified
                                    // by jtreg option "-jdk", to verify the
                                    // signed jar and extract the signature
                                    // algorithm and timestamp digest algorithm.
                                    String output = verifyJar(TEST_JARSIGNER,
                                            signedJar).getOutput();
                                    signItem.extractedSignatureAlgorithm(
                                            extract(output,
                                                    " *Signature algorithm.*",
                                                    ".*: |,.*"));
                                    signItem.extractedTsaDigestAlgorithm(
                                            extract(output,
                                                    " *Timestamp digest algorithm.*",
                                                    ".*: "));
                                }

                                signItems.add(signItem);
                            }
                        }
                    }
                }
            }
        }

        return signItems;
    }

    private static void verifying(SignItem signItem, VerifyItem verifyItem)
            throws Throwable {
        boolean delayVerify = verifyItem.status == Status.NONE;
        String verifyingId = verifyingId(signItem, verifyItem, !delayVerify);
        detailsOutput.writeAnchorName(verifyingId, "Verifying: " + verifyingId);

        OutputAnalyzer verifyOA = verifyJar(verifyItem.jdkInfo.jarsignerPath,
                signItem.signedJar);
        Status verifyingStatus = verifyingStatus(verifyOA);

        // It checks if the default timestamp digest algorithm is SHA-256.
        if (verifyingStatus != Status.ERROR
                && signItem.tsaDigestAlgorithm == null) {
            verifyingStatus = signItem.extractedTsaDigestAlgorithm != null
                                    && !signItem.extractedTsaDigestAlgorithm.matches("SHA-?256")
                            ? Status.ERROR
                            : verifyingStatus;
            if (verifyingStatus == Status.ERROR) {
                System.out.println("The default tsa digest is not SHA-256: "
                    + signItem.extractedTsaDigestAlgorithm);
            }
        }

        if (delayVerify) {
            signItem.addVerifyItem(verifyItem.status(verifyingStatus));
        } else {
            verifyItem.delayStatus(verifyingStatus);
        }
    }

    // Return key sizes according to the specified key algorithm.
    private static int[] keySizes(String keyAlgorithm) {
        if (keyAlgorithm == RSA || keyAlgorithm == DSA) {
            return new int[] { 1024, 2048, 0 };
        } else if (keyAlgorithm == EC) {
            return new int[] { 384, 571, 0 };
        }

        return null;
    }

    // Determines the status of signing.
    private static Status signingStatus(OutputAnalyzer outputAnalyzer) {
        if (outputAnalyzer.getExitValue() == 0) {
            if (outputAnalyzer.getOutput().contains(Test.WARNING)) {
                return Status.WARNING;
            } else {
                return Status.NORMAL;
            }
        } else {
            return Status.ERROR;
        }
    }

    // Determines the status of verifying.
    private static Status verifyingStatus(OutputAnalyzer outputAnalyzer) {
        if (outputAnalyzer.getExitValue() == 0) {
            String output = outputAnalyzer.getOutput();
            if (!output.contains(Test.JAR_VERIFIED)) {
                return Status.ERROR;
            } else if (output.contains(Test.WARNING)) {
                return Status.WARNING;
            } else {
                return Status.NORMAL;
            }
        } else {
            return Status.ERROR;
        }
    }

    // Extracts string from text by specified patterns.
    private static String extract(String text, String linePattern,
            String replacePattern) {
        Matcher lineMatcher = Pattern.compile(linePattern).matcher(text);
        if (lineMatcher.find()) {
            String line = lineMatcher.group(0);
            return line.replaceAll(replacePattern, "");
        } else {
            return null;
        }
    }

    // Using specified jarsigner to sign the pre-created jar with specified
    // algorithms.
    private static OutputAnalyzer signJar(String jarsignerPath, String sigalg,
            String tsadigestalg, String tsa, String alias, String signedJar)
            throws Throwable {
        List<String> arguments = new ArrayList<String>();

        if (PROXY_HOST != null && PROXY_PORT != null) {
            arguments.add("-J-Dhttp.proxyHost=" + PROXY_HOST);
            arguments.add("-J-Dhttp.proxyPort=" + PROXY_PORT);
            arguments.add("-J-Dhttps.proxyHost=" + PROXY_HOST);
            arguments.add("-J-Dhttps.proxyPort=" + PROXY_PORT);
        }
        arguments.add("-J-Djava.security.properties=" + JAVA_SECURITY);
        arguments.add("-debug");
        arguments.add("-verbose");
        if (sigalg != null) {
            arguments.add("-sigalg");
            arguments.add(sigalg);
        }
        if (tsa != null) {
            arguments.add("-tsa");
            arguments.add(tsa);
        }
        if (tsadigestalg != null) {
            arguments.add("-tsadigestalg");
            arguments.add(tsadigestalg);
        }
        arguments.add("-keystore");
        arguments.add(KEYSTORE);
        arguments.add("-storepass");
        arguments.add(PASSWORD);
        arguments.add("-signedjar");
        arguments.add(signedJar + ".jar");
        arguments.add(TEST_JAR_NAME);
        arguments.add(alias);

        OutputAnalyzer outputAnalyzer = execTool(
                jarsignerPath,
                arguments.toArray(new String[arguments.size()]));
        return outputAnalyzer;
    }

    // Using specified jarsigner to verify the signed jar.
    private static OutputAnalyzer verifyJar(String jarsignerPath,
            String signedJar) throws Throwable {
        OutputAnalyzer outputAnalyzer = execTool(
                jarsignerPath,
                "-J-Djava.security.properties=" + JAVA_SECURITY,
                "-debug",
                "-verbose",
                "-certs",
                "-keystore", KEYSTORE,
                "-verify", signedJar + ".jar");
        return outputAnalyzer;
    }

    // Generates the test result report.
    private static boolean generateReport(List<TsaInfo> tsaList,
            List<SignItem> signItems) throws IOException {
        System.out.println("Report is being generated...");

        StringBuilder report = new StringBuilder();
        report.append(HtmlHelper.startHtml());
        report.append(HtmlHelper.startPre());
        // Generates TSA URLs
        report.append("TSA list:\n");
        for(TsaInfo tsaInfo : tsaList) {
            report.append(
                    String.format("%d=%s%n", tsaInfo.index, tsaInfo.tsaUrl));
        }
        report.append(HtmlHelper.endPre());

        report.append(HtmlHelper.startTable());
        // Generates report headers.
        List<String> headers = new ArrayList<String>();
        headers.add("[Certificate]");
        headers.add("[Signer JDK]");
        headers.add("[Signature Algorithm]");
        headers.add("[TSA Digest]");
        headers.add("[TSA]");
        headers.add("[Signing Status]");
        headers.add("[Verifier JDK]");
        headers.add("[Verifying Status]");
        if (DELAY_VERIFY) {
            headers.add("[Delay Verifying Status]");
        }
        headers.add("[Failed]");
        report.append(HtmlHelper.htmlRow(
                headers.toArray(new String[headers.size()])));

        StringBuilder failedReport = new StringBuilder(report.toString());

        boolean failed = false;

        // Generates report rows.
        for (SignItem signItem : signItems) {
            for (VerifyItem verifyItem : signItem.verifyItems) {
                String reportRow = reportRow(signItem, verifyItem);
                report.append(reportRow);
                boolean isFailedCase = isFailed(signItem, verifyItem);
                if (isFailedCase) {
                    failedReport.append(reportRow);
                }
                failed = failed || isFailedCase;
            }
        }

        report.append(HtmlHelper.endTable());
        report.append(HtmlHelper.endHtml());
        generateFile("report.html", report.toString());
        if (failed) {
            failedReport.append(HtmlHelper.endTable());
            failedReport.append(HtmlHelper.endPre());
            failedReport.append(HtmlHelper.endHtml());
            generateFile("failedReport.html", failedReport.toString());
        }

        System.out.println("Report is generated.");
        return failed;
    }

    private static void generateFile(String path, String content)
            throws IOException {
        FileWriter writer = new FileWriter(new File(path));
        writer.write(content);
        writer.close();
    }

    private static String jarsignerPath(String jdkPath) {
        return jdkPath + "/bin/jarsigner";
    }

    // Executes the specified function on JdkUtils by the specified JDK.
    private static String execJdkUtils(String jdkPath, String method,
            String... args) throws Throwable {
        String[] cmd = new String[args.length + 5];
        cmd[0] = jdkPath + "/bin/java";
        cmd[1] = "-cp";
        cmd[2] = TEST_CLASSES;
        cmd[3] = JdkUtils.class.getName();
        cmd[4] = method;
        System.arraycopy(args, 0, cmd, 5, args.length);
        return ProcessTools.executeCommand(cmd).getOutput();
    }

    // Executes the specified JDK tools, such as keytool and jarsigner, and
    // ensures the output is in US English.
    private static OutputAnalyzer execTool(String toolPath, String... args)
            throws Throwable {
        String[] cmd = new String[args.length + 4];
        cmd[0] = toolPath;
        cmd[1] = "-J-Duser.language=en";
        cmd[2] = "-J-Duser.country=US";
        cmd[3] = "-J-Djava.security.egd=file:/dev/./urandom";
        System.arraycopy(args, 0, cmd, 4, args.length);
        return ProcessTools.executeCommand(cmd);
    }

    private static class JdkInfo {

        private final String jdkPath;
        private final String jarsignerPath;
        private final String version;
        private final boolean supportsTsadigestalg;

        private Map<String, Boolean> sigalgMap = new HashMap<String, Boolean>();

        private JdkInfo(String jdkPath) throws Throwable {
            this.jdkPath = jdkPath;
            version = execJdkUtils(jdkPath, JdkUtils.M_JAVA_RUNTIME_VERSION);
            if (version == null || version.trim().isEmpty()) {
                throw new RuntimeException(
                        "Cannot determine the JDK version: " + jdkPath);
            }
            jarsignerPath = jarsignerPath(jdkPath);
            supportsTsadigestalg = execTool(jarsignerPath, "-help")
                    .getOutput().contains("-tsadigestalg");
        }

        private boolean isSupportedSigalg(String sigalg) throws Throwable {
            if (!sigalgMap.containsKey(sigalg)) {
                boolean isSupported = "true".equalsIgnoreCase(
                        execJdkUtils(
                                jdkPath,
                                JdkUtils.M_IS_SUPPORTED_SIGALG,
                                sigalg));
                sigalgMap.put(sigalg, isSupported);
            }

            return sigalgMap.get(sigalg);
        }

        private boolean isJdk6() {
            return version.startsWith("1.6");
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((version == null) ? 0 : version.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            JdkInfo other = (JdkInfo) obj;
            if (version == null) {
                if (other.version != null)
                    return false;
            } else if (!version.equals(other.version))
                return false;
            return true;
        }
    }

    private static class TsaInfo {

        private final int index;
        private final String tsaUrl;
        private Set<String> digestList = new HashSet<String>();

        private TsaInfo(int index, String tsa) {
            this.index = index;
            this.tsaUrl = tsa;
        }

        private void addDigest(String digest) {
            if (!ignore(digest)) {
                digestList.add(digest);
            }
        }

        private static boolean ignore(String digest) {
            return !SHA1.equalsIgnoreCase(digest)
                    && !SHA256.equalsIgnoreCase(digest)
                    && !SHA512.equalsIgnoreCase(digest);
        }

        private boolean isDigestSupported(String digest) {
            return digest == null || digestList.isEmpty()
                    || digestList.contains(digest);
        }
    }

    private static class CertInfo {

        private final String jdkVersion;
        private final String keyAlgorithm;
        private final String digestAlgorithm;
        private final int keySize;
        private final boolean expired;

        private CertInfo(String jdkVersion, String keyAlgorithm,
                String digestAlgorithm, int keySize, boolean expired) {
            this.jdkVersion = jdkVersion;
            this.keyAlgorithm = keyAlgorithm;
            this.digestAlgorithm = digestAlgorithm;
            this.keySize = keySize;
            this.expired = expired;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((digestAlgorithm == null) ? 0 : digestAlgorithm.hashCode());
            result = prime * result + (expired ? 1231 : 1237);
            result = prime * result
                    + ((jdkVersion == null) ? 0 : jdkVersion.hashCode());
            result = prime * result
                    + ((keyAlgorithm == null) ? 0 : keyAlgorithm.hashCode());
            result = prime * result + keySize;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CertInfo other = (CertInfo) obj;
            if (digestAlgorithm == null) {
                if (other.digestAlgorithm != null)
                    return false;
            } else if (!digestAlgorithm.equals(other.digestAlgorithm))
                return false;
            if (expired != other.expired)
                return false;
            if (jdkVersion == null) {
                if (other.jdkVersion != null)
                    return false;
            } else if (!jdkVersion.equals(other.jdkVersion))
                return false;
            if (keyAlgorithm == null) {
                if (other.keyAlgorithm != null)
                    return false;
            } else if (!keyAlgorithm.equals(other.keyAlgorithm))
                return false;
            if (keySize != other.keySize)
                return false;
            return true;
        }

        private String alias() {
            return jdkVersion + "_" + toString();
        }

        @Override
        public String toString() {
            return keyAlgorithm + "_" + digestAlgorithm
                    + (keySize == 0 ? "" : "_" + keySize)
                    + (expired ? "_Expired" : "");
        }
    }

    // It does only one timestamping for the same JDK, digest algorithm and
    // TSA service with an arbitrary valid/expired certificate.
    private static class TsaFilter {

        private static final Set<Condition> SET = new HashSet<Condition>();

        private static boolean filter(String signerVersion,
                String digestAlgorithm, boolean expiredCert, int tsaIndex) {
            return !SET.add(new Condition(signerVersion, digestAlgorithm,
                    expiredCert, tsaIndex));
        }

        private static class Condition {

            private final String signerVersion;
            private final String digestAlgorithm;
            private final boolean expiredCert;
            private final int tsaIndex;

            private Condition(String signerVersion, String digestAlgorithm,
                    boolean expiredCert, int tsaIndex) {
                this.signerVersion = signerVersion;
                this.digestAlgorithm = digestAlgorithm;
                this.expiredCert = expiredCert;
                this.tsaIndex = tsaIndex;
            }

            @Override
            public int hashCode() {
                final int prime = 31;
                int result = 1;
                result = prime * result
                        + ((digestAlgorithm == null) ? 0 : digestAlgorithm.hashCode());
                result = prime * result + (expiredCert ? 1231 : 1237);
                result = prime * result
                        + ((signerVersion == null) ? 0 : signerVersion.hashCode());
                result = prime * result + tsaIndex;
                return result;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj)
                    return true;
                if (obj == null)
                    return false;
                if (getClass() != obj.getClass())
                    return false;
                Condition other = (Condition) obj;
                if (digestAlgorithm == null) {
                    if (other.digestAlgorithm != null)
                        return false;
                } else if (!digestAlgorithm.equals(other.digestAlgorithm))
                    return false;
                if (expiredCert != other.expiredCert)
                    return false;
                if (signerVersion == null) {
                    if (other.signerVersion != null)
                        return false;
                } else if (!signerVersion.equals(other.signerVersion))
                    return false;
                if (tsaIndex != other.tsaIndex)
                    return false;
                return true;
            }
        }}

    private static enum Status {

        // No action due to pre-action fails.
        NONE,

        // jar is signed/verified with error
        ERROR,

        // jar is signed/verified with warning
        WARNING,

        // jar is signed/verified without any warning and error
        NORMAL
    }

    private static class SignItem {

        private CertInfo certInfo;
        private String version;
        private String signatureAlgorithm;
        // Signature algorithm that is extracted from verification output.
        private String extractedSignatureAlgorithm;
        private String tsaDigestAlgorithm;
        // TSA digest algorithm that is extracted from verification output.
        private String extractedTsaDigestAlgorithm;
        private int tsaIndex;
        private Status status;
        private String signedJar;

        private List<VerifyItem> verifyItems = new ArrayList<VerifyItem>();

        private static SignItem build() {
            return new SignItem();
        }

        private SignItem certInfo(CertInfo certInfo) {
            this.certInfo = certInfo;
            return this;
        }

        private SignItem version(String version) {
            this.version = version;
            return this;
        }

        private SignItem signatureAlgorithm(String signatureAlgorithm) {
            this.signatureAlgorithm = signatureAlgorithm;
            return this;
        }

        private SignItem extractedSignatureAlgorithm(
                String extractedSignatureAlgorithm) {
            this.extractedSignatureAlgorithm = extractedSignatureAlgorithm;
            return this;
        }

        private SignItem tsaDigestAlgorithm(String tsaDigestAlgorithm) {
            this.tsaDigestAlgorithm = tsaDigestAlgorithm;
            return this;
        }

        private SignItem extractedTsaDigestAlgorithm(
                String extractedTsaDigestAlgorithm) {
            this.extractedTsaDigestAlgorithm = extractedTsaDigestAlgorithm;
            return this;
        }

        private SignItem tsaIndex(int tsaIndex) {
            this.tsaIndex = tsaIndex;
            return this;
        }

        private SignItem status(Status status) {
            this.status = status;
            return this;
        }

        private SignItem signedJar(String signedJar) {
            this.signedJar = signedJar;
            return this;
        }

        private void addVerifyItem(VerifyItem verifyItem) {
            verifyItems.add(verifyItem);
        }
    }

    private static class VerifyItem {

        private JdkInfo jdkInfo;
        private Status status = Status.NONE;
        private Status delayStatus = Status.NONE;

        private static VerifyItem build(JdkInfo jdkInfo) {
            VerifyItem verifyItem = new VerifyItem();
            verifyItem.jdkInfo = jdkInfo;
            return verifyItem;
        }

        private VerifyItem status(Status status) {
            this.status = status;
            return this;
        }

        private VerifyItem delayStatus(Status status) {
            this.delayStatus = status;
            return this;
        }
    }

    // The identifier for a specific signing.
    private static String signingId(SignItem signItem) {
        return signItem.signedJar;
    }

    // The identifier for a specific verifying.
    private static String verifyingId(SignItem signItem, VerifyItem verifyItem,
            boolean delayVerify) {
        return "S_" + signingId(signItem) + "-" + (delayVerify ? "DV" : "V")
                + "_" + verifyItem.jdkInfo.version;
    }

    private static String reportRow(SignItem signItem, VerifyItem verifyItem) {
        List<String> values = new ArrayList<String>();
        values.add(signItem.certInfo.toString());
        values.add(signItem.version);
        values.add(null2Default(signItem.signatureAlgorithm,
                signItem.extractedSignatureAlgorithm));
        values.add(signItem.tsaIndex == -1
                   ? ""
                   : null2Default(signItem.tsaDigestAlgorithm,
                        signItem.extractedTsaDigestAlgorithm));
        values.add(signItem.tsaIndex == -1 ? "" : signItem.tsaIndex + "");
        values.add(HtmlHelper.anchorLink(
                PhaseOutputStream.fileName(PhaseOutputStream.Phase.SIGNING),
                signingId(signItem),
                signItem.status.toString()));
        values.add(verifyItem.jdkInfo.version);
        values.add(HtmlHelper.anchorLink(
                PhaseOutputStream.fileName(PhaseOutputStream.Phase.VERIFYING),
                verifyingId(signItem, verifyItem, false),
                verifyItem.status.toString()));
        if (DELAY_VERIFY) {
            values.add(HtmlHelper.anchorLink(
                    PhaseOutputStream.fileName(
                            PhaseOutputStream.Phase.DELAY_VERIFYING),
                    verifyingId(signItem, verifyItem, true),
                    verifyItem.delayStatus.toString()));
        }
        values.add(isFailed(signItem, verifyItem) ? "X" : "");
        return HtmlHelper.htmlRow(values.toArray(new String[values.size()]));
    }

    private static boolean isFailed(SignItem signItem,
            VerifyItem verifyItem) {
        return signItem.status == Status.ERROR
                || verifyItem.status == Status.ERROR
                || verifyItem.delayStatus == Status.ERROR;
    }

    // If a value is null, then displays the default value or N/A.
    private static String null2Default(String value, String defaultValue) {
        return value == null
               ? DEFAULT + "(" + (defaultValue == null
                                  ? "N/A"
                                  : defaultValue) + ")"
               : value;
    }
}
