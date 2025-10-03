/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http;

import jdk.internal.net.http.common.Utils;

import static jdk.internal.net.http.http3.frames.SettingsFrame.DEFAULT_SETTINGS_MAX_FIELD_SECTION_SIZE;
import static jdk.internal.net.http.http3.frames.SettingsFrame.DEFAULT_SETTINGS_QPACK_BLOCKED_STREAMS;
import static jdk.internal.net.http.http3.frames.SettingsFrame.DEFAULT_SETTINGS_QPACK_MAX_TABLE_CAPACITY;

/**
 * A class that groups initial values for HTTP/3 client properties.
 * <p>
 * Properties starting with {@code jdk.internal.} are not exposed and
 * typically reserved for testing. They could be removed, and their name,
 * semantics, or values, could be changed at any time.
 * <p>
 * Properties that are exposed are JDK specifics and typically documented
 * in the {@link java.net.http} module API documentation.
 * <ol>
 *   <li><Properties specific to HTTP/3 typically start with {@code jdk.httpclient.http3.}</li>
 *   <li><Properties specific to Qpack typically start with {@code jdk.httpclient.qpack.}</li>
 * </ol>
 *
 * @apiNote
 * Not all properties are exposed. Properties that are not included in
 * the {@link java.net.http} module API documentation are subject to
 * change, and should be considered internal, though we might also consider
 * exposing them in the future if needed.
 *
 */
public final class Http3ClientProperties {

    private Http3ClientProperties() {
        throw new InternalError("should not come here");
    }

    // The maximum timeout to wait for a reply to the first INITIAL
    // packet when attempting a direct connection
    public static final long MAX_DIRECT_CONNECTION_TIMEOUT;

    // The maximum timeout to wait for a MAX_STREAM frame
    // before throwing StreamLimitException
    public static final long MAX_STREAM_LIMIT_WAIT_TIMEOUT;

    // The maximum number of concurrent push streams
    // by connection
    public static final long MAX_HTTP3_PUSH_STREAMS;

    // Limit for dynamic table capacity that the encoder is allowed
    // to set. Its capacity is also limited by the QPACK_MAX_TABLE_CAPACITY
    // HTTP/3 setting value received from the peer decoder.
    public static final long QPACK_ENCODER_TABLE_CAPACITY_LIMIT;

    // The value of SETTINGS_QPACK_MAX_TABLE_CAPACITY HTTP/3 setting that is
    // negotiated by HTTP client's decoder
    public static final long QPACK_DECODER_MAX_TABLE_CAPACITY;

    // The value of SETTINGS_MAX_FIELD_SECTION_SIZE HTTP/3 setting that is
    // negotiated by HTTP client's decoder
    public static final long QPACK_DECODER_MAX_FIELD_SECTION_SIZE;

    // Decoder upper bound on the number of streams that can be blocked
    public static final long QPACK_DECODER_BLOCKED_STREAMS;

    // of available space in the dynamic table

    // Percentage of occupied space in the dynamic table that controls when
    // the draining index starts increasing. This index determines which entries
    // are too close to eviction, and can be referenced by the encoder.
    public static final int QPACK_ENCODER_DRAINING_THRESHOLD;

    // If set to "true" allows the encoder to insert a header with a dynamic
    // name reference and reference it in a field line section without awaiting
    // decoder's acknowledgement.
    public static final boolean QPACK_ALLOW_BLOCKING_ENCODING = Utils.getBooleanProperty(
            "jdk.internal.httpclient.qpack.allowBlockingEncoding", false);

    // whether localhost is acceptable as an alternative service origin
    public static final boolean ALTSVC_ALLOW_LOCAL_HOST_ORIGIN = Utils.getBooleanProperty(
            "jdk.httpclient.altsvc.allowLocalHostOrigin", true);

    // whether concurrent HTTP/3 requests to the same host should wait for
    // first connection to succeed (or fail) instead of attempting concurrent
    // connections. Where concurrent connections are attempted, only one of
    // them will be offered to the connection pool. The others will serve a
    // single request.
    public static final boolean WAIT_FOR_PENDING_CONNECT = Utils.getBooleanProperty(
            "jdk.httpclient.http3.waitForPendingConnect", true);


    static {
        // 375 is ~ to the initial loss timer
        // 1000 is ~ the initial PTO
        // We will set a timeout of 2*1375 ms to wait for the reply to our
        // first initial packet for a direct connection
        long defaultMaxDirectConnectionTimeout = 1375 << 1; // ms
        long maxDirectConnectionTimeout = Utils.getLongProperty(
                "jdk.httpclient.http3.maxDirectConnectionTimeout",
                        defaultMaxDirectConnectionTimeout);
        long maxStreamLimitTimeout = Utils.getLongProperty(
                "jdk.httpclient.http3.maxStreamLimitTimeout",
                defaultMaxDirectConnectionTimeout);
        int defaultMaxHttp3PushStreams = Utils.getIntegerProperty(
                "jdk.httpclient.maxstreams",
                100);
        int maxHttp3PushStreams = Utils.getIntegerProperty(
                "jdk.httpclient.http3.maxConcurrentPushStreams",
                defaultMaxHttp3PushStreams);
        long defaultDecoderMaxCapacity = 0;
        long decoderMaxTableCapacity = Utils.getLongProperty(
                "jdk.httpclient.qpack.decoderMaxTableCapacity",
                defaultDecoderMaxCapacity);
        long decoderBlockedStreams = Utils.getLongProperty(
                "jdk.httpclient.qpack.decoderBlockedStreams",
                DEFAULT_SETTINGS_QPACK_BLOCKED_STREAMS);
        long defaultEncoderTableCapacityLimit = 4096;
        long encoderTableCapacityLimit = Utils.getLongProperty(
                "jdk.httpclient.qpack.encoderTableCapacityLimit",
                defaultEncoderTableCapacityLimit);
        int defaultDecoderMaxFieldSectionSize = 393216; // 384kB
        long decoderMaxFieldSectionSize = Utils.getIntegerNetProperty(
                "jdk.http.maxHeaderSize", Integer.MIN_VALUE, Integer.MAX_VALUE,
                defaultDecoderMaxFieldSectionSize, true);
        // Percentage of occupied space in the dynamic table that when
        // exceeded the dynamic table draining index starts increasing
        int drainingThreshold = Utils.getIntegerProperty(
                "jdk.internal.httpclient.qpack.encoderDrainingThreshold",
                75);

        MAX_DIRECT_CONNECTION_TIMEOUT = maxDirectConnectionTimeout <= 0
                ? defaultMaxDirectConnectionTimeout : maxDirectConnectionTimeout;
        MAX_STREAM_LIMIT_WAIT_TIMEOUT = maxStreamLimitTimeout < 0
                ? defaultMaxDirectConnectionTimeout
                : maxStreamLimitTimeout;
        MAX_HTTP3_PUSH_STREAMS = Math.max(maxHttp3PushStreams, 0);
        QPACK_ENCODER_TABLE_CAPACITY_LIMIT = encoderTableCapacityLimit < 0
                ? defaultEncoderTableCapacityLimit : encoderTableCapacityLimit;
        QPACK_DECODER_MAX_TABLE_CAPACITY = decoderMaxTableCapacity < 0 ?
                DEFAULT_SETTINGS_QPACK_MAX_TABLE_CAPACITY : decoderMaxTableCapacity;
        QPACK_DECODER_MAX_FIELD_SECTION_SIZE = decoderMaxFieldSectionSize < 0 ?
                DEFAULT_SETTINGS_MAX_FIELD_SECTION_SIZE : decoderMaxFieldSectionSize;
        QPACK_DECODER_BLOCKED_STREAMS = decoderBlockedStreams < 0 ?
                DEFAULT_SETTINGS_QPACK_BLOCKED_STREAMS : decoderBlockedStreams;
        QPACK_ENCODER_DRAINING_THRESHOLD = Math.clamp(drainingThreshold, 10, 90);
    }

}
