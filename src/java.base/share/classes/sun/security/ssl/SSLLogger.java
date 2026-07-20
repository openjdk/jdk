/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;
import java.security.cert.Extension;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import jdk.internal.vm.annotation.ForceInline;
import sun.security.util.HexDumpEncoder;
import sun.security.util.Debug;
import sun.security.x509.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static sun.security.ssl.Utilities.LINE_SEP;

/**
 * Implementation of SSL logger.
 * <p>
 * If the system property "javax.net.debug" is not defined, the debug logging
 * is turned off.  If the system property "javax.net.debug" is defined as
 * empty, the debug logger is specified by System.getLogger("javax.net.ssl"),
 * and applications can customize and configure the logger or use external
 * logging mechanisms.  If the system property "javax.net.debug" is defined
 * and non-empty, a private debug logger which logs to System.err is used.
 */
public final class SSLLogger implements System.Logger {
    private static final System.Logger logger;
    // High level boolean to track whether logging is active (i.e. all/ssl).
    // Further checks may be necessary to determine if data is logged.
    private static final boolean logging;

    private final String loggerName;
    private final boolean useCompactFormat;

    static {
        String p = System.getProperty("javax.net.debug");
        if (p != null) {
            if (p.isEmpty()) {
                logger = System.getLogger("javax.net.ssl");
                Opt.ALL.on = true;
            } else {
                p = p.toLowerCase(Locale.ENGLISH);
                if (p.contains("help")) {
                    // help option calls exit(0)
                    help();
                }
                // configure expanded logging mode in constructor
                logger = new SSLLogger("javax.net.ssl", p);
                if (p.contains("all")) {
                    Opt.ALL.on = true;
                } else {
                    for (Opt o : Opt.values()) {
                        // deal with special "_" options later
                        if (o.component.contains("_")) {
                            continue;
                        }

                        if (p.contains(o.component)) {
                            o.on = true;
                            // remove pattern to avoid it being reused
                            // e.g. "ssl,sslctx" parsing
                            p = p.replaceFirst(o.component, "");
                        }
                    }

                    // "record" and "handshake" subcomponents allow
                    // extra configuration options
                    if (Opt.HANDSHAKE.on && p.contains("verbose")) {
                        Opt.HANDSHAKE_VERBOSE.on = true;
                    }

                    if (Opt.RECORD.on) {
                        if (p.contains("packet")) {
                            Opt.RECORD_PACKET.on = true;
                        }
                        if (p.contains("plaintext")) {
                            Opt.RECORD_PLAINTEXT.on = true;
                        }
                    }
                    // finally, if only "ssl" component is declared, then
                    // enable all subcomponents. "ssl" logs all activity
                    // except for the "data" and "packet" categories
                    if (Opt.SSL.on &&
                            EnumSet.allOf(Opt.class)
                                    .stream()
                                    .noneMatch(o -> o.on && o.isSubComponent)) {
                        for (Opt opt : Opt.values()) {
                            if (opt.isSubComponent) {
                                opt.on = true;
                            }
                        }
                    }
                }
            }

            // javax.net.debug would be misconfigured property with respect
            // to logging if value didn't contain "all" or "ssl"
            logging = Opt.ALL.on || Opt.SSL.on;
        } else {
            logger = null;
            logging = false;
        }
    }

    private SSLLogger(String loggerName, String options) {
        this.loggerName = loggerName;
        options = options.toLowerCase(Locale.ENGLISH);
        this.useCompactFormat = !options.contains("expand");
    }

    @ForceInline
    public static boolean isOn() {
        return logging;
    }

    /**
     * Return true if the specific DebugOption is enabled or ALL is enabled
     */

    public static boolean isOn(Opt option) {
        return Opt.ALL.on || option.on;
    }

    public static void severe(String msg, Object... params) {
        SSLLogger.log0(Level.ERROR, msg, params);
    }

    public static void warning(String msg, Object... params) {
        SSLLogger.log0(Level.WARNING, msg, params);
    }

    public static void info(String msg, Object... params) {
        SSLLogger.log0(Level.INFO, msg, params);
    }

    public static void fine(String msg, Object... params) {
        SSLLogger.log0(Level.DEBUG, msg, params);
    }

    public static void finer(String msg, Object... params) {
        SSLLogger.log0(Level.TRACE, msg, params);
    }

    public static void finest(String msg, Object... params) {
        SSLLogger.log0(Level.TRACE, msg, params);
    }

    private static void log0(Level level, String msg, Object... params) {
        if (logger != null && logger.isLoggable(level)) {
            if (params == null || params.length == 0) {
                logger.log(level, msg);
            } else {
                try {
                    String formatted =
                            SSLSimpleFormatter.formatParameters(params);
                    // use the customized log method for SSLLogger
                    if (logger instanceof SSLLogger) {
                        logger.log(level, msg, formatted);
                    } else {
                        logger.log(level, msg + ":" + LINE_SEP + formatted);
                    }
                } catch (Exception exp) {
                    // ignore it, just for debugging.
                }
            }
        }
    }

    private static void help() {
        System.err.printf("%n%-16s %s%n", "help",
                "print this help message and exit");
        System.err.printf("%-16s %s%n%n", "expand",
                "expanded (less compact) output format");
        System.err.printf("%-16s %s%n", "all", "turn on all debugging");
        System.err.printf("%-16s %s%n%n", "ssl", "turn on ssl debugging");
        System.err.printf("The following filters can be used with ssl:%n%n");
        System.err.printf("    %-14s %s%n", "defaultctx",
                "print default SSL initialization");
        System.err.printf("    %-14s %s%n", "handshake",
                "print each handshake message");
        System.err.printf("      %-12s   %s%n", "verbose",
                "verbose handshake message printing (widens handshake)");
        System.err.printf("    %-14s %s%n", "keymanager",
                "print key manager tracing");
        System.err.printf("    %-14s %s%n", "record",
                "enable per-record tracing");
        System.err.printf("      %-12s   %s%n", "packet",
                "print raw SSL/TLS packets (widens record)");
        System.err.printf("      %-12s   %s%n", "plaintext",
                "hex dump of record plaintext (widens record)");
        System.err.printf("    %-14s %s%n", "respmgr",
                "print OCSP response tracing");
        System.err.printf("    %-14s %s%n", "session",
                "print session activity");
        System.err.printf("    %-14s %s%n", "sessioncache",
                "print session cache tracing");
        System.err.printf("    %-14s %s%n", "sslctx",
                "print SSLContext tracing");
        System.err.printf("    %-14s %s%n", "trustmanager",
                "print trust manager tracing");
        System.err.printf("%nIf \"ssl\" is specified by itself," +
                " all non-widening filters are enabled.%n");
        System.err.printf("%nSpecifying \"ssl\" with additional filter" +
                " options produces general%nSSL debug messages plus just" +
                " the selected categories.%n%n");
        System.exit(0);
    }

    static String toString(Object... params) {
        try {
            return SSLSimpleFormatter.formatParameters(params);
        } catch (Exception exp) {
            return "unexpected exception thrown: " + exp.getMessage();
        }
    }

    // Logs a warning message and always returns false. This method
    // can be used as an OR Predicate to add a log in a stream filter.
    static boolean logWarning(Opt option, String s) {
        if (SSLLogger.isOn() && option.on) {
            SSLLogger.warning(s);
        }
        return false;
    }

    @Override
    public String getName() {
        return loggerName;
    }

    @Override
    public void log(Level level,
                    ResourceBundle rb, String message, Throwable thrwbl) {
        if (isLoggable(level)) {
            try {
                String formatted =
                        SSLSimpleFormatter.format(this, level, message, thrwbl);
                System.err.write(formatted.getBytes(UTF_8));
            } catch (Exception exp) {
                // ignore it, just for debugging.
            }
        }
    }

    @Override
    public void log(Level level,
                    ResourceBundle rb, String message, Object... params) {
        if (isLoggable(level)) {
            try {
                String formatted =
                        SSLSimpleFormatter.format(this, level, message, params);
                System.err.write(formatted.getBytes(UTF_8));
            } catch (Exception exp) {
                // ignore it, just for debugging.
            }
        }
    }

    @Override
    public boolean isLoggable(Level level) {
        return level != Level.OFF;
    }

    /**
     * Enum representing possible debug options for JSSE debugging.
     * <p>
     * ALL and SSL are considered master components. Entries without an
     * underscore ("_"), and not ALL or SSL, are subcomponents. Entries
     * with an underscore ("_") denote options specific to subcomponents.
     * <p>
     * Fields:
     * - 'component': Lowercase name of the option.
     * - 'isSubComponent': True for subcomponents.
     * - 'on': Indicates whether the option is enabled. Some rule based logic
     *         is used to determine value of this field.
     * <p>
     * Enabling subcomponents fine-tunes (filters) debug output.
     */
    public enum Opt {
        ALL,
        DEFAULTCTX,
        HANDSHAKE,
        HANDSHAKE_VERBOSE,
        KEYMANAGER,
        RECORD,
        RECORD_PACKET,
        RECORD_PLAINTEXT,
        RESPMGR,
        SESSION,
        SESSIONCACHE, // placeholder for 8344685
        SSLCTX,
        TRUSTMANAGER,
        SSL; // define ssl last, helps with sslctx matching later.

        final String component;
        final boolean isSubComponent;
        boolean on;

        Opt() {
            this.component = this.toString().toLowerCase(Locale.ENGLISH);
            this.isSubComponent = !(component.contains("_") ||
                    component.equals("all") ||
                    component.equals("ssl"));
        }
    }

    private static class SSLSimpleFormatter {
        private static final String PATTERN = "yyyy-MM-dd kk:mm:ss.SSS z";
        private static final DateTimeFormatter dateTimeFormat =
                DateTimeFormatter.ofPattern(PATTERN, Locale.ENGLISH)
                        .withZone(ZoneId.systemDefault());

        private static final MessageFormat basicCertFormat = new MessageFormat(
                """
                        "version"            : "v{0}",
                        "serial number"      : "{1}",
                        "signature algorithm": "{2}",
                        "issuer"             : "{3}",
                        "not before"         : "{4}",
                        "not  after"         : "{5}",
                        "subject"            : "{6}",
                        "subject public key" : "{7}"
                        """,
                Locale.ENGLISH);

        private static final MessageFormat extendedCertFormat =
            new MessageFormat(
                    """
                            "version"            : "v{0}",
                            "serial number"      : "{1}",
                            "signature algorithm": "{2}",
                            "issuer"             : "{3}",
                            "not before"         : "{4}",
                            "not  after"         : "{5}",
                            "subject"            : "{6}",
                            "subject public key" : "{7}",
                            "extensions"         : [
                            {8}
                            ]
                            """,
                Locale.ENGLISH);

        private static final MessageFormat messageFormatNoParas =
            new MessageFormat(
                    """
                            '{'
                              "logger"      : "{0}",
                              "level"       : "{1}",
                              "thread id"   : "{2}",
                              "thread name" : "{3}",
                              "time"        : "{4}",
                              "caller"      : "{5}",
                              "message"     : "{6}"
                            '}'
                            """,
                Locale.ENGLISH);

        private static final MessageFormat messageCompactFormatNoParas =
            new MessageFormat(
                "{0}|{1}|{2}|{3}|{4}|{5}|{6}" + LINE_SEP,
                Locale.ENGLISH);

        private static final MessageFormat messageFormatWithParas =
            new MessageFormat(
                    """
                            '{'
                              "logger"      : "{0}",
                              "level"       : "{1}",
                              "thread id"   : "{2}",
                              "thread name" : "{3}",
                              "time"        : "{4}",
                              "caller"      : "{5}",
                              "message"     : "{6}",
                              "specifics"   : [
                            {7}
                              ]
                            '}'
                            """,
                Locale.ENGLISH);

        private static final MessageFormat messageCompactFormatWithParas =
            new MessageFormat(
                    """
                            {0}|{1}|{2}|{3}|{4}|{5}|{6} (
                            {7}
                            )
                            """,
                Locale.ENGLISH);

        private static final MessageFormat keyObjectFormat = new MessageFormat(
                """
                        "{0}" : '{'
                        {1}'}'
                        """,
                Locale.ENGLISH);

        // INFO: [TH: 123450] 2011-08-20 23:12:32.3225 PDT
        //     log message
        //     log message
        //     ...
        private static String format(SSLLogger logger, Level level,
                    String message, Object ... parameters) {

            if (parameters == null || parameters.length == 0) {
                Object[] messageFields = {
                    logger.loggerName,
                    level.getName(),
                    Utilities.toHexString(Thread.currentThread().threadId()),
                    Thread.currentThread().getName(),
                    dateTimeFormat.format(Instant.now()),
                    formatCaller(),
                    message
                };

                if (logger.useCompactFormat) {
                    return messageCompactFormatNoParas.format(messageFields);
                } else {
                    return messageFormatNoParas.format(messageFields);
                }
            }

            Object[] messageFields = {
                    logger.loggerName,
                    level.getName(),
                    Utilities.toHexString(Thread.currentThread().threadId()),
                    Thread.currentThread().getName(),
                    dateTimeFormat.format(Instant.now()),
                    formatCaller(),
                    message,
                    (logger.useCompactFormat ?
                            formatParameters(parameters) :
                            Utilities.indent(formatParameters(parameters)))
            };

            if (logger.useCompactFormat) {
                return messageCompactFormatWithParas.format(messageFields);
            } else {
                return messageFormatWithParas.format(messageFields);
            }
        }

        private static String formatCaller() {
            return StackWalker.getInstance().walk(s ->
                s.dropWhile(f ->
                    f.getClassName().startsWith("sun.security.ssl.SSLLogger") ||
                    f.getClassName().startsWith("java.lang.System"))
                .map(f -> f.getFileName() + ":" + f.getLineNumber())
                .findFirst().orElse("unknown caller"));
        }

        private static String formatParameters(Object ... parameters) {
            StringBuilder builder = new StringBuilder(512);
            boolean isFirst = true;
            for (Object parameter : parameters) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    builder.append("," + LINE_SEP);
                }

                if (parameter instanceof Throwable t) {
                    builder.append(formatThrowable(t));
                } else if (parameter instanceof Certificate c) {
                    builder.append(formatCertificate(c));
                } else if (parameter instanceof ByteArrayInputStream bis) {
                    builder.append(formatByteArrayInputStream(bis));
                } else if (parameter instanceof ByteBuffer bb) {
                    builder.append(formatByteBuffer((bb)));
                } else if (parameter instanceof byte[] bytes) {
                    builder.append(formatByteArrayInputStream(
                        new ByteArrayInputStream(bytes)));
                } else if (parameter instanceof Map.Entry) {
                    @SuppressWarnings("unchecked")
                    Map.Entry<String, ?> mapParameter =
                        (Map.Entry<String, ?>)parameter;
                    builder.append(formatMapEntry(mapParameter));
                } else {
                    builder.append(formatObject(parameter));
                }
            }

            return builder.toString();
        }

        // "throwable": {
        //   ...
        // }
        private static String formatThrowable(Throwable throwable) {
            StringBuilder builder = new StringBuilder(512);
            ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
            try (PrintStream out = new PrintStream(bytesOut)) {
                throwable.printStackTrace(out);
                builder.append(Utilities.indent(bytesOut.toString()));
            }
            Object[] fields = {
                    "throwable",
                    builder.toString()
            };

            return keyObjectFormat.format(fields);
        }

        // "certificate": {
        //   ...
        // }
        private static String formatCertificate(Certificate certificate) {

            if (!(certificate instanceof X509Certificate)) {
                return Utilities.indent(certificate.toString());
            }

            StringBuilder builder = new StringBuilder(512);
            try {
                X509CertImpl x509 =
                        X509CertImpl.toImpl((X509Certificate) certificate);
                X509CertInfo certInfo = x509.getInfo();
                CertificateExtensions certExts = certInfo.getExtensions();
                if (certExts == null) {
                    Object[] certFields = {
                        x509.getVersion(),
                        Debug.toString(x509.getSerialNumber()),
                        x509.getSigAlgName(),
                        x509.getIssuerX500Principal().toString(),
                        dateTimeFormat.format(x509.getNotBefore().toInstant()),
                        dateTimeFormat.format(x509.getNotAfter().toInstant()),
                        x509.getSubjectX500Principal().toString(),
                        x509.getPublicKey().getAlgorithm()
                        };
                    builder.append(Utilities.indent(
                            basicCertFormat.format(certFields)));
                } else {
                    StringBuilder extBuilder = new StringBuilder(512);
                    boolean isFirst = true;
                    for (Extension certExt : certExts.getAllExtensions()) {
                        if (isFirst) {
                            isFirst = false;
                        } else {
                            extBuilder.append("," + LINE_SEP);
                        }
                        extBuilder.append("{" + LINE_SEP +
                            Utilities.indent(certExt.toString()) + LINE_SEP +"}");
                    }
                    Object[] certFields = {
                        x509.getVersion(),
                        Debug.toString(x509.getSerialNumber()),
                        x509.getSigAlgName(),
                        x509.getIssuerX500Principal().toString(),
                        dateTimeFormat.format(x509.getNotBefore().toInstant()),
                        dateTimeFormat.format(x509.getNotAfter().toInstant()),
                        x509.getSubjectX500Principal().toString(),
                        x509.getPublicKey().getAlgorithm(),
                        Utilities.indent(extBuilder.toString())
                        };
                    builder.append(Utilities.indent(
                            extendedCertFormat.format(certFields)));
                }
            } catch (Exception ce) {
                // ignore the exception
            }

            Object[] fields = {
                    "certificate",
                    builder.toString()
            };

            return Utilities.indent(keyObjectFormat.format(fields));
        }

        private static String formatByteArrayInputStream(
                ByteArrayInputStream bytes) {
            StringBuilder builder = new StringBuilder(512);

            try (ByteArrayOutputStream bytesOut = new ByteArrayOutputStream()) {
                HexDumpEncoder hexEncoder = new HexDumpEncoder();
                hexEncoder.encodeBuffer(bytes, bytesOut);

                builder.append(Utilities.indent(bytesOut.toString()));
            } catch (IOException ioe) {
                // ignore it, just for debugging.
            }

            return builder.toString();
        }

        private static String formatByteBuffer(ByteBuffer byteBuffer) {
            StringBuilder builder = new StringBuilder(512);
            try (ByteArrayOutputStream bytesOut = new ByteArrayOutputStream()) {
                HexDumpEncoder hexEncoder = new HexDumpEncoder();
                hexEncoder.encodeBuffer(byteBuffer.duplicate(), bytesOut);
                builder.append(Utilities.indent(bytesOut.toString()));
            } catch (IOException ioe) {
                // ignore it, just for debugging.
            }

            return builder.toString();
        }

        private static String formatMapEntry(Map.Entry<String, ?> entry) {
            String key = entry.getKey();
            Object value = entry.getValue();

            String formatted;
            if (value instanceof String) {
                // "key": "value"
                formatted = "\"" + key + "\": \"" + value + "\"";
            } else if (value instanceof String[] strings) {
                // "key": [ "string a",
                //          "string b",
                //          "string c"
                //        ]
                StringBuilder builder = new StringBuilder(512);
                builder.append("\"" + key + "\": [" + LINE_SEP);
                int len = strings.length;
                for (int i = 0; i < len; i++) {
                    String string = strings[i];
                    builder.append("      \"" + string + "\"");
                    if (i != len - 1) {
                        builder.append(",");
                    }
                    builder.append(LINE_SEP);
                }
                builder.append("      ]");

                formatted = builder.toString();
            } else if (value instanceof byte[] bytes) {
                formatted = "\"" + key + "\": \"" +
                    Utilities.toHexString((bytes)) + "\"";
            } else if (value instanceof Byte b) {
                formatted = "\"" + key + "\": \"" +
                    HexFormat.of().toHexDigits(b) + "\"";
            } else {
                formatted = "\"" + key + "\": " +
                    "\"" + value.toString() + "\"";
            }

            return Utilities.indent(formatted);
        }

        private static String formatObject(Object obj) {
            return obj.toString();
        }
    }
}
