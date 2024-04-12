/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jpackage.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MacCertificate {
    private final String certificate;

    public MacCertificate(String certificate) {
        this.certificate = certificate;
    }

    public boolean isValid() {
        return verifyCertificate(this.certificate);
    }

    public static String findCertificateKey(String keyPrefix, String teamName,
                                            String keychainName) {

        String matchedKey = null;
        boolean useAsIs = (keyPrefix == null)
                || teamName.startsWith(keyPrefix)
                || teamName.startsWith("Developer ID")
                || teamName.startsWith("3rd Party Mac");

        String name = (useAsIs) ? teamName : (keyPrefix + teamName);

        String output = getFindCertificateOutput(name, keychainName);
        if (output == null) {
            Log.error(MessageFormat.format(I18N.getString(
                        "error.cert.not.found"), name, keychainName));
            return null;
        }

        // Check and warn user if multiple certificates found
        // We will use different regex to count certificates.
        // ASCII case: "alis"<blob>="NAME"
        // UNICODE case: "alis"<blob>=0xSOMEHEXDIGITS  "NAME (\SOMEDIGITS)"
        // In UNICODE case name will contain octal sequence representing UTF-8
        // characters.
        // Just look for at least two '"alis"<blob>'.
        Pattern p = Pattern.compile("\"alis\"<blob>");
        Matcher m = p.matcher(output);
        if (m.find() && m.find()) {
            Log.error(MessageFormat.format(I18N.getString(
                        "error.multiple.certs.found"), name, keychainName));
        }

        // Try to get ASCII only certificate first. This aproach only works
        // if certificate name has ASCII only characters in name. For certificates
        // with UNICODE characters in name we will use combination of "security"
        // and "openssl". We keeping ASCII only aproach to avoid regressions and
        // it works for many use cases.
        p = Pattern.compile("\"alis\"<blob>=\"([^\"]+)\"");
        m = p.matcher(output);
        if (m.find()) {
            matchedKey = m.group(1);;
        }

        // Maybe it has UNICODE characters in name. In this case use "security"
        // and "openssl" to exctract name. We cannot use just "security", since
        // name can be truncated.
        if (matchedKey == null) {
            Path  file = null;
            try {
                file = getFindCertificateOutputPEM(name, keychainName);
                if (file != null) {
                    matchedKey = findCertificateSubject(
                                    file.toFile().getCanonicalPath());
                }
            } catch (IOException ioe) {
                Log.verbose(ioe);
            } finally {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {}
            }
        }

        if (matchedKey == null) {
            Log.error(MessageFormat.format(I18N.getString(
                "error.cert.not.found"), name, keychainName));
        }

        return matchedKey;
    }

    private static String getFindCertificateOutput(String name,
                                                   String keychainName) {
        try (ByteArrayOutputStream baos = getFindCertificateOutput(name,
                                                                   keychainName,
                                                                   false)) {
            if (baos != null) {
                return baos.toString();
            }
        } catch (IOException ioe) {
            Log.verbose(ioe);
        }

        return null;
    }

    private static Path getFindCertificateOutputPEM(String name,
                                                    String keychainName) {
        Path output = null;
        try (ByteArrayOutputStream baos = getFindCertificateOutput(name,
                                                                   keychainName,
                                                                   true)) {
            if (baos != null) {
                output = Files.createTempFile("tempfile", ".tmp");
                Files.copy(new ByteArrayInputStream(baos.toByteArray()),
                        output, StandardCopyOption.REPLACE_EXISTING);
                return output;
            }
        } catch (IOException ioe) {
            Log.verbose(ioe);
            try {
                Files.deleteIfExists(output);
            } catch (IOException ignored) {}
        }

        return null;
    }

    private static ByteArrayOutputStream getFindCertificateOutput(String name,
                                                                  String keychainName,
                                                                  boolean isPEMFormat) {
        List<String> args = new ArrayList<>();
        args.add("/usr/bin/security");
        args.add("find-certificate");
        args.add("-c");
        args.add(name);
        args.add("-a");
        if (isPEMFormat) {
            args.add("-p");
        }
        if (keychainName != null && !keychainName.isEmpty()) {
            args.add(keychainName);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos)) {
            ProcessBuilder pb = new ProcessBuilder(args);
            IOUtils.exec(pb, false, ps);
            return baos;
        } catch (IOException ioe) {
            Log.verbose(ioe);
            return null;
        }
    }

    private static String findCertificateSubject(String filename) {
        String result = null;

        List<String> args = new ArrayList<>();
        args.add("/usr/bin/openssl");
        args.add("x509");
        args.add("-noout");
        args.add("-subject");
        args.add("-in");
        args.add(filename);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos)) {
            ProcessBuilder security = new ProcessBuilder(args);
            IOUtils.exec(security, false, ps);
            String output = baos.toString().strip();
            // Example output:
            // subject= /UID=ABCDABCD/CN=jpackage.openjdk.java.net (\xC3\xB6) (ABCDABCD)/C=US
            // We need 'CN' value
            String [] pairs = output.split("/");
            for (String pair : pairs) {
                if (pair.startsWith("CN=")) {
                    result = pair.substring(3);
                    // Convert escaped UTF-8 code points to characters
                    result = convertHexToChar(result);
                    break;
                }
            }
        } catch (IOException ex) {
            Log.verbose(ex);
        }

        return result;
    }

    // Certificate name with Unicode will be:
    // Developer ID Application: jpackage.openjdk.java.net (\xHH\xHH)
    // Convert UTF-8 code points '\xHH\xHH' to character.
    private static String convertHexToChar(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        if (!input.contains("\\x")) {
            return input;
        }

        StringBuilder output = new StringBuilder();
        try {
            int len = input.length();
            for (int i = 0; i < len; i++) {
                if (input.codePointAt(i) == '\\' &&
                    (i + 8) <= len &&
                    input.codePointAt(i + 1) == 'x' &&
                    input.codePointAt(i + 4) == '\\' &&
                    input.codePointAt(i + 5) == 'x') {
                        // We found '\xHH\xHH'
                        // HEX code points to byte array
                        byte [] bytes = HexFormat.of().parseHex(
                            input.substring(i + 2, i + 4) + input.substring(i + 6, i + 8));
                        // Byte array with UTF-8 code points to character
                        output.append(new String(bytes, "UTF-8"));
                        i += 7; // Skip '\xHH\xHH'
                } else {
                    output.appendCodePoint(input.codePointAt(i));
                }
            }
        } catch (Exception ex) {
            Log.verbose(ex);
            // We will consider any excpetions during conversion as
            // certificate not found.
            return null;
        }

        return output.toString();
    }

    private Date findCertificateDate(String filename) {
        Date result = null;

        List<String> args = new ArrayList<>();
        args.add("/usr/bin/openssl");
        args.add("x509");
        args.add("-noout");
        args.add("-enddate");
        args.add("-in");
        args.add(filename);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos)) {
            ProcessBuilder security = new ProcessBuilder(args);
            IOUtils.exec(security, false, ps);
            String output = baos.toString();
            output = output.substring(output.indexOf("=") + 1);
            DateFormat df = new SimpleDateFormat(
                    "MMM dd kk:mm:ss yyyy z", Locale.ENGLISH);
            result = df.parse(output);
        } catch (IOException | ParseException ex) {
            Log.verbose(ex);
        }

        return result;
    }

    private boolean verifyCertificate(String certificate) {
        boolean result = false;

        try {
            Path file = null;
            Date certificateDate = null;

            try {
                file = getFindCertificateOutputPEM(certificate, null);

                if (file != null) {
                    certificateDate = findCertificateDate(
                            file.toFile().getCanonicalPath());
                }
            } finally {
                if (file != null) {
                    Files.deleteIfExists(file);
                }
            }

            if (certificateDate != null) {
                Calendar c = Calendar.getInstance();
                Date today = c.getTime();

                if (certificateDate.after(today)) {
                    result = true;
                }
            }
        }
        catch (IOException ignored) {}

        return result;
    }
}
