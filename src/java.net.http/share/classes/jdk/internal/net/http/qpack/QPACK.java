/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.qpack;

import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.SettingsFrame;
import jdk.internal.net.http.http3.streams.QueuingStreamPair;
import jdk.internal.net.http.qpack.QPACK.Logger.Level;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.lang.String.format;
import static jdk.internal.net.http.Http3ClientProperties.QPACK_ALLOW_BLOCKING_ENCODING;
import static jdk.internal.net.http.Http3ClientProperties.QPACK_DECODER_BLOCKED_STREAMS;
import static jdk.internal.net.http.Http3ClientProperties.QPACK_DECODER_MAX_FIELD_SECTION_SIZE;
import static jdk.internal.net.http.Http3ClientProperties.QPACK_DECODER_MAX_TABLE_CAPACITY;
import static jdk.internal.net.http.Http3ClientProperties.QPACK_ENCODER_DRAINING_THRESHOLD;
import static jdk.internal.net.http.Http3ClientProperties.QPACK_ENCODER_TABLE_CAPACITY_LIMIT;
import static jdk.internal.net.http.http3.frames.SettingsFrame.SETTINGS_MAX_FIELD_SECTION_SIZE;
import static jdk.internal.net.http.http3.frames.SettingsFrame.SETTINGS_QPACK_BLOCKED_STREAMS;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.EXTRA;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.NONE;
import static jdk.internal.net.http.qpack.QPACK.Logger.Level.NORMAL;

/**
 * Internal utilities and stuff.
 */
public final class QPACK {


    // A dynamic table capacity that the encoder is allowed to set given that it doesn't
    // exceed the max capacity value negotiated by the decoder. If the max capacity
    // less than this limit the encoder's dynamic table capacity is set to the max capacity
    // value.
    public static final long ENCODER_TABLE_CAPACITY_LIMIT = QPACK_ENCODER_TABLE_CAPACITY_LIMIT;

    // The value of SETTINGS_QPACK_MAX_TABLE_CAPACITY HTTP/3 setting that is
    // negotiated by HTTP client's decoder
    public static final long DECODER_MAX_TABLE_CAPACITY = QPACK_DECODER_MAX_TABLE_CAPACITY;

    // The value of SETTINGS_MAX_FIELD_SECTION_SIZE HTTP/3 setting that is
    // negotiated by HTTP client's decoder
    public static final long DECODER_MAX_FIELD_SECTION_SIZE = QPACK_DECODER_MAX_FIELD_SECTION_SIZE;

    // Decoder upper bound on the number of streams that can be blocked
    public static final long DECODER_BLOCKED_STREAMS = QPACK_DECODER_BLOCKED_STREAMS;

    // If set to "true" allows the encoder to insert a header with a dynamic
    // name reference and reference it in a field line section without awaiting
    // decoder's acknowledgement.
    public static final boolean ALLOW_BLOCKING_ENCODING = QPACK_ALLOW_BLOCKING_ENCODING;

    // Threshold of available dynamic table space after which the draining
    // index starts increasing. This index determines which entries are
    // too close to eviction, and can be referenced by the encoder
    public static final int ENCODER_DRAINING_THRESHOLD = QPACK_ENCODER_DRAINING_THRESHOLD;

    private static final RootLogger LOGGER;
    private static final Map<String, Level> logLevels =
            Map.of("NORMAL", NORMAL, "EXTRA", EXTRA);

    static {
        String PROPERTY = "jdk.internal.httpclient.qpack.log.level";
        String value = Utils.getProperty(PROPERTY);

        if (value == null) {
            LOGGER = new RootLogger(NONE);
        } else {
            String upperCasedValue = value.toUpperCase();
            Level l = logLevels.get(upperCasedValue);
            if (l == null) {
                LOGGER = new RootLogger(NONE);
                LOGGER.log(System.Logger.Level.INFO,
                        () -> format("%s value '%s' not recognized (use %s); logging disabled",
                                     PROPERTY, value, String.join(", ", logLevels.keySet())));
            } else {
                LOGGER = new RootLogger(l);
                LOGGER.log(System.Logger.Level.DEBUG,
                        () -> format("logging level %s", l));
            }
        }
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public static SettingsFrame updateDecoderSettings(SettingsFrame defaultSettingsFrame) {
        SettingsFrame settingsFrame = defaultSettingsFrame;
        settingsFrame.setParameter(SETTINGS_QPACK_BLOCKED_STREAMS, DECODER_BLOCKED_STREAMS);
        settingsFrame.setParameter(SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY, DECODER_MAX_TABLE_CAPACITY);
        settingsFrame.setParameter(SETTINGS_MAX_FIELD_SECTION_SIZE, DECODER_MAX_FIELD_SECTION_SIZE);
        return settingsFrame;
    }

    private QPACK() { }

    /**
     * The purpose of this logger is to provide means of diagnosing issues _in
     * the QPACK implementation_. It's not a general purpose logger.
     */
    // implements System.Logger to make it possible to skip this class
    // when looking for the Caller.
    public static class Logger implements System.Logger {

        /**
         * Log detail level.
         */
        public enum Level {

            NONE(0, System.Logger.Level.OFF),
            NORMAL(1, System.Logger.Level.DEBUG),
            EXTRA(2, System.Logger.Level.TRACE);

            private final int level;
            final System.Logger.Level systemLevel;

            Level(int i, System.Logger.Level system) {
                level = i;
                systemLevel = system;
            }

            public final boolean implies(Level other) {
                return this.level >= other.level;
            }
        }

        private final String name;
        private final Level level;
        private final String path;
        private final System.Logger logger;

        private Logger(String path, String name, Level level) {
            this.path = path;
            this.name = name;
            this.level = level;
            this.logger = Utils.getHpackLogger(path::toString, level.systemLevel);
        }

        public final String getName() {
            return name;
        }

        @Override
        public boolean isLoggable(System.Logger.Level level) {
            return logger.isLoggable(level);
        }

        @Override
        public void log(System.Logger.Level level, ResourceBundle bundle, String msg, Throwable thrown) {
            logger.log(level, bundle, msg,thrown);
        }

        @Override
        public void log(System.Logger.Level level, ResourceBundle bundle, String format, Object... params) {
            logger.log(level, bundle, format, params);
        }

        /*
         * Usual performance trick for logging, reducing performance overhead in
         * the case where logging with the specified level is a NOP.
         */

        public boolean isLoggable(Level level) {
            return this.level.implies(level);
        }

        public void log(Level level, Supplier<String> s) {
            if (this.level.implies(level)) {
                logger.log(level.systemLevel, s);
            }
        }

        public Logger subLogger(String name) {
            return new Logger(path + "/" + name, name, level);
        }

    }

    private static final class RootLogger extends Logger {

        protected RootLogger(Level level) {
            super("qpack", "qpack", level);
        }

    }

    // -- low-level utilities --

    /**
     * An interface used to obtain the encoder or decoder stream pair
     * from the enclosing HTTP/3 connection.
     */
    @FunctionalInterface
    public interface StreamPairSupplier {
        QueuingStreamPair create(Consumer<ByteBuffer> receiver);
    }

    public interface QPACKErrorHandler {
        void closeOnError(Throwable throwable, Http3Error error);
    }
}
