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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ref.Reference;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.net.http.common.Utils;

import static java.nio.charset.StandardCharsets.UTF_8;

/*
 * @test
 * @summary Verify the behaviour of the debug logger.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters jdk.test.lib.net.SimpleSSLContext
 *        DebugLoggerTest
 * @run main/othervm  DebugLoggerTest
 * @run main/othervm -Djdk.internal.httpclient.debug=errr  DebugLoggerTest
 * @run main/othervm -Djdk.internal.httpclient.debug=err  DebugLoggerTest ERR
 * @run main/othervm -Djdk.internal.httpclient.debug=out  DebugLoggerTest OUT
 * @run main/othervm -Djdk.internal.httpclient.debug=log  DebugLoggerTest LOG
 * @run main/othervm -Djdk.internal.httpclient.debug=true DebugLoggerTest ERR LOG
 * @run main/othervm -Djdk.internal.httpclient.debug=err,OUT  DebugLoggerTest ERR OUT
 * @run main/othervm -Djdk.internal.httpclient.debug=err,out,log DebugLoggerTest ERR OUT LOG
 * @run main/othervm -Djdk.internal.httpclient.debug=true,log DebugLoggerTest ERR LOG
 * @run main/othervm -Djdk.internal.httpclient.debug=true,out DebugLoggerTest ERR OUT LOG
 * @run main/othervm -Djdk.internal.httpclient.debug=err,OUT,foo  DebugLoggerTest ERR OUT
 */
public class DebugLoggerTest {
    static final PrintStream stdErr = System.err;
    static final PrintStream stdOut = System.out;
    static final String LOGGER_NAME = "jdk.internal.httpclient.debug";
    static final String MESSAGE = "May the luck of the Irish be with you!";
    static final String MESSAGE2 = "May the wind be at your back!";
    static final String MESSAGE3 = "May the sun shine warm upon your face!";

    static RecordingPrintStream err = null;
    static RecordingPrintStream out = null;

    /**
     * A {@code RecordingPrintStream} is a {@link PrintStream} that makes
     * it possible to record part of the data stream in memory while still
     * forwarding everything to a delegated {@link OutputStream}.
     * @apiNote
     * For instance, a {@code RecordingPrintStream} might be used as an
     * interceptor to record anything printed on {@code System.err}
     * at specific times. Recording can be started and stopped
     * at any time, and multiple times. For instance, a typical
     * usage might be:
     * <pre>static final PrintStream stderr = System.err;
     * static final RecordingPrintString recorder =
     *     new RecordingPrintStream(stderr, true, UTF_8);
     * static {
     *     System.setErr(recorder);
     * }
     *
     * ...
     *      // ....
     *      recorder.startRecording();
     *      try {
     *          // do something
     *          String str1 = recorder.drainRecordedData();
     *          // do something else
     *          String str2 = recorder.drainRecordedData();
     *          // ....
     *      } finally {
     *          recorder.stopRecording();
     *      }
     *      // ....
     * ...
     * </pre>
     * <p>Though the methods are thread safe, {@link #startRecording()}
     * {@link #stopRecording()} and {@link #drainRecordedData()} must
     * not be called concurrently by different threads without external
     * orchestration, as calling these methods mutate the state of
     * the recorder in a way that can be globally observed by all
     * threads.
     */
    public static final class RecordingPrintStream extends PrintStream {
        private final Charset charset;
        private final ByteArrayOutputStream recordedData;
        private volatile boolean recording;

        /**
         * Creates a new {@code RecordingPrintStream} instance that wraps
         * the provided {@code OutputStream}.
         * @implSpec Calls {@link PrintStream#PrintStream(
         *            OutputStream, boolean, Charset)}.
         * @param out An {@code OutputStream} instance to which all bytes will
         *            be forwarded.
         * @param autoFlush Whether {@linkplain PrintStream#PrintStream(
         *            OutputStream, boolean, Charset) autoFlush} is on.
         * @param charset A {@linkplain Charset} used to transform text to
         *                bytes and bytes to string.
         */
        public RecordingPrintStream(OutputStream out, boolean autoFlush, Charset charset) {
            super(out, autoFlush, charset);
            this.charset = charset;
            recordedData = new ByteArrayOutputStream();
        }

        /**
         * Flushes the stream and starts recording.
         * If recording is already started, this method has no effect beyond
         * {@linkplain PrintStream#flush() flushing} the stream.
         */
        public void startRecording() {
            flush(); // make sure everything that was printed before is flushed
            synchronized (recordedData) {
                recording = true;
            }
        }

        /**
         * Flushes the stream and stops recording.
         * If recording is already stopped, this method has no effect beyond
         * {@linkplain PrintStream#flush() flushing} the stream.
         */
        public void stopRecording() {
            flush(); // make sure everything that was printed before is flushed
            synchronized (recordedData) {
                recording = false;
            }
        }

        /**
         * Flushes the stream, drains the recorded data, convert it
         * to a string, and returns it. This has the effect of
         * flushing any recorded data from memory: the next call
         * to {@code drainRecordedData()} will not return any data
         * previously returned.
         * This method can be called regardless of whether recording
         * is started or stopped.
         */
        public String drainRecordedData() {
            flush(); // make sure everything that was printed before is flushed
            synchronized (recordedData) {
                String data = recordedData.toString(charset);
                recordedData.reset();
                return data;
            }
        }

        @Override
        public void write(int b) {
            super.write(b);
            if (recording) {
                synchronized (recordedData) {
                    if (recording) recordedData.write(b);
                }
            }
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            super.write(buf, off, len);
            if (recording) {
                synchronized (recordedData) {
                    if (recording) recordedData.write(buf, off, len);
                }
            }
        }
    }

    static class TestHandler extends Handler {
        final CopyOnWriteArrayList logs = new CopyOnWriteArrayList();
        TestHandler() {
            setLevel(Level.ALL);
        }
        @Override
        public void publish(LogRecord record) {
            logs.add(record);
        }
        @Override
        public void flush() {
        }
        @Override
        public void close() throws SecurityException {
        }
    }

    enum Destination {OUT, ERR, LOG}

    static Set<Destination> getDestinations(String prop) {
        if (prop == null) return Set.of();
        String[] values = prop.split(",");
        if (values.length == 0) return Set.of();
        Set<Destination> dest = HashSet.newHashSet(3);
        for (var v : values) {
            v = v.trim().toLowerCase(Locale.ROOT);
            if ("log".equals(v)) dest.add(Destination.LOG);
            if ("err".equals(v)) dest.add(Destination.ERR);
            if ("out".equals(v)) dest.add(Destination.OUT);
            if ("true".equals(v)) dest.addAll(EnumSet.of(Destination.ERR, Destination.LOG));
        }
        return Set.copyOf(dest);
    }

    public static void main(String[] args) {
        err =  new RecordingPrintStream(stdErr, true, UTF_8);
        out =  new RecordingPrintStream(stdOut, true, UTF_8);
        System.setErr(err);
        System.setOut(out);
        TestHandler logHandler = new TestHandler();
        Logger julLogger = Logger.getLogger(LOGGER_NAME);
        julLogger.setLevel(Level.ALL);
        julLogger.setUseParentHandlers(false);
        julLogger.addHandler(logHandler);
        System.Logger sysLogger = System.getLogger(LOGGER_NAME);
        var debug = Utils.getDebugLogger(() -> "DebugLoggerTest", Utils.DEBUG);
        String prop = System.getProperty(LOGGER_NAME);
        stdOut.printf("DebugLoggerTest: [\"%s\", %s] start%n", prop, Arrays.asList(args));
        var dest = getDestinations(prop);
        var dest2 = Stream.of(args)
                .map(Destination::valueOf)
                .collect(Collectors.toSet());
        assertEquals(debug.on(), !dest.isEmpty());
        assertEquals(dest, dest2);

        Predicate<LogRecord> matcher1 = (r) -> r.getMessage() != null && r.getMessage().contains(MESSAGE);
        doTest(() -> debug.log(MESSAGE), debug, logHandler, dest, MESSAGE, matcher1);
        Exception thrown = new Exception(MESSAGE3);
        Predicate<LogRecord> matcher2 = (r) -> r.getMessage() != null
                && r.getMessage().contains(MESSAGE2)
                && thrown.equals(r.getThrown());
        doTest(() -> debug.log(MESSAGE2, thrown), debug, logHandler, dest, MESSAGE2, matcher2);
        stdOut.printf("Test [\"%s\", %s] passed%n", prop, Arrays.asList(args));
        Reference.reachabilityFence(julLogger);
    }

    private static void doTest(Runnable test,
                               System.Logger logger,
                               TestHandler logHandler,
                               Set<Destination> dest,
                               String msg,
                               Predicate<LogRecord> logMatcher) {
        logHandler.logs.clear();
        err.drainRecordedData();
        out.drainRecordedData();

        err.startRecording();
        out.startRecording();
        test.run();
        err.stopRecording();
        out.stopRecording();
        String outStr = out.drainRecordedData();
        String errStr = err.drainRecordedData();
        List<LogRecord> logs = logHandler.logs.stream().toList();

        if (!(logger instanceof jdk.internal.net.http.common.Logger debug)) {
            throw new AssertionError("Unexpected logger type for: " + logger);
        }
        assertEquals(debug.on(), !dest.isEmpty(), "Unexpected debug.on() for " + dest);
        assertEquals(debug.isLoggable(System.Logger.Level.DEBUG), !dest.isEmpty());
        if (dest.contains(Destination.ERR)) {
            if (!errStr.contains(msg)) {
                throw new AssertionError("stderr does not contain the expected message");
            }
        } else if (errStr.contains(msg)) {
            throw new AssertionError("stderr should not contain the log message");
        }
        if (dest.contains(Destination.OUT)) {
            if (!outStr.contains(msg)) {
                throw new AssertionError("stdout does not contain the expected message");
            }
        } else if (outStr.contains(msg)) {
            throw new AssertionError("stdout should not contain the log message");
        }
        boolean logMatches = logs.stream().anyMatch(logMatcher);
        if (dest.contains(Destination.LOG)) {
            if (!logMatches) {
                throw new AssertionError("expected message not found in logs");
            }
        } else {
            if (logMatches) {
                throw new AssertionError("logs should not contain the message!");
            }
        }
    }

    static void assertEquals(Object o1, Object o2) {
        if (!Objects.equals(o1, o2)) {
            throw new AssertionError("Not equals: \""
                    + o1 + "\" != \"" + o2 + "\"");
        }
    }
    static void assertEquals(Object o1, Object o2, String message) {
        if (!Objects.equals(o1, o2)) {
            throw new AssertionError(message + ": \""
                    + o1 + "\" != \"" + o2 + "\"");
        }
    }


}
