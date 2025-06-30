/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import jdk.internal.net.http.common.Log;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.quic.QuicTransportParametersConsumer;
import jdk.internal.net.quic.QuicVersion;

/**
 * This class models a collection of Quic transport parameters. This class is mutable
 * and not thread safe.
 *
 * A parameter is considered absent if {@link #getParameter(TransportParameterId)}
 * yields {@code null}. The parameter is present otherwise.
 * Parameters can be removed by calling {@link
 * #setParameter(TransportParameterId, byte[]) setParameter(id, null)}.
 * The methods {@link #getBooleanParameter(TransportParameterId)} and
 * {@link #getIntParameter(TransportParameterId)} allow easy access to
 * parameters whose type is boolean or int, respectively.
 * When such a parameter is absent, its default value is returned by
 * those methods.

 * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
 *     RFC 9000, section 18.2</a>:
 *
 * <blockquote>
 * <pre>{@code
 * Many transport parameters listed here have integer values.
 * Those transport parameters that are identified as integers use a
 * variable-length integer encoding; see Section 16. Transport parameters
 * have a default value of 0 if the transport parameter is absent, unless
 * otherwise stated.
 * }</pre>
 *
 * <p>[...]
 *
 * <pre>{@code
 * If present, transport parameters that set initial per-stream flow control limits
 * (initial_max_stream_data_bidi_local, initial_max_stream_data_bidi_remote, and
 * initial_max_stream_data_uni) are equivalent to sending a MAX_STREAM_DATA frame
 * (Section 19.10) on every stream of the corresponding type immediately after opening.
 * If the transport parameter is absent, streams of that type start with a flow control
 * limit of 0.
 *
 * A client MUST NOT include any server-only transport parameter:
 *        original_destination_connection_id,
 *        preferred_address,
 *        retry_source_connection_id, or
 *        stateless_reset_token.
 *
 * A server MUST treat receipt of any of these transport parameters as a connection error
 * of type TRANSPORT_PARAMETER_ERROR.
 * }</pre>
 * </blockquote>
 *
 * @see ParameterId
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public final class QuicTransportParameters {

    /**
     * An interface to model a transport parameter ID.
     * A transport parameter ID has a {@linkplain #name() name} (which is
     * not transmitted) and an {@linkplain #idx() identifier}.
     * Standard parameters are modeled by enum values in
     * {@link ParameterId}.
     */
    public sealed interface TransportParameterId {
        /**
         * {@return the transport parameter name}
         * This a human-readable string.
         */
        String name();

        /**
         * {@return the transport parameter identifier}
         */
        int idx();

        /**
         * {@return the parameter id corresponding to the given identifier, if
         * defined, an empty optional otherwise}
         * @param idx a parameter identifier
         */
        static Optional<ParameterId> valueOf(long idx) {
            return ParameterId.valueOf(idx);
        }
    }

    /**
     * Standard Quic transport parameter names and ids.
     * These are the transport parameters defined in IANA
     * "QUIC Transport Parameters" registry.
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#name-quic-transport-parameters-r">
     *     RFC 9000, Section 22.3</a>
     */
    public enum ParameterId implements TransportParameterId {
        /**
         * original_destination_connection_id (0x00).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     This parameter is the value of the Destination Connection ID field
         *     from the first Initial packet sent by the client; see Section 7.3.
         *     This transport parameter is only sent by a server.
         * }</pre></blockquote>
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-7.3">RFC 9000, Section 7.3</a>
         */
        original_destination_connection_id(0x00),

        /**
         * max_idle_timeout (0x01).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     The maximum idle timeout is a value in milliseconds that is encoded
         *     as an integer; see (Section 10.1).
         *     Idle timeout is disabled when both endpoints omit this transport
         *     parameter or specify a value of 0.
         * }</pre></blockquote>
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-10.1">RFC 9000, Section 10.1</a>
         */
        max_idle_timeout(0x01),

        /**
         * stateless_reset_token (0x02).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     A stateless reset token is used in verifying a stateless reset;
         *     see Section 10.3.
         *     This parameter is a sequence of 16 bytes. This transport parameter MUST NOT
         *     be sent by a client but MAY be sent by a server. A server that does not send
         *     this transport parameter cannot use stateless reset (Section 10.3) for
         *     the connection ID negotiated during the handshake.
         * }</pre></blockquote>
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-10.3">RFC 9000, Section 10.3</a>
         */
        stateless_reset_token(0x02),

        /**
         * max_udp_payload_size (0x03).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     The maximum UDP payload size parameter is an integer value that limits the
         *     size of UDP payloads that the endpoint is willing to receive. UDP datagrams
         *     with payloads larger than this limit are not likely to be processed by
         *     the receiver.
         *
         *     The default for this parameter is the maximum permitted UDP payload of 65527.
         *     Values below 1200 are invalid.
         *
         *     This limit does act as an additional constraint on datagram size
         *     in the same way as the path MTU, but it is a property of the endpoint
         *     and not the path; see Section 14.
         *     It is expected that this is the space an endpoint dedicates to
         *     holding incoming packets.
         * }</pre></blockquote>
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#datagram-size">RFC 9000, Section 14</a>
         */
        max_udp_payload_size(0x03),

        /**
         * initial_max_data (0x04).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     The initial maximum data parameter is an integer value that contains
         *     the initial value for the maximum amount of data that can be sent on
         *     the connection. This is equivalent to sending a MAX_DATA (Section 19.9)
         *     for the connection immediately after completing the handshake.
         * }</pre></blockquote>
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-19.9">RFC 9000, Section 19.9</a>
         */
        initial_max_data(0x04),

        /**
         * initial_max_stream_data_bidi_local (0x05).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     This parameter is an integer value specifying the initial flow control
         *     limit for locally initiated bidirectional streams. This limit applies to
         *     newly created bidirectional streams opened by the endpoint that
         *     sends the transport parameter.
         *     In client transport parameters, this applies to streams with an identifier
         *     with the least significant two bits set to 0x00;
         *     in server transport parameters, this applies to streams with the least
         *     significant two bits set to 0x01.
         * }</pre></blockquote>
         */
        initial_max_stream_data_bidi_local(0x05),

        /**
         * initial_max_stream_data_bidi_remote (0x06).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     This parameter is an integer value specifying the initial flow control
         *     limit for peer-initiated bidirectional streams. This limit applies to
         *     newly created bidirectional streams opened by the endpoint that receives
         *     the transport parameter. In client transport parameters, this applies to
         *     streams with an identifier with the least significant two bits set to 0x01;
         *     in server transport parameters, this applies to streams with the least
         *     significant two bits set to 0x00.
         * }</pre></blockquote>
         */
        initial_max_stream_data_bidi_remote(0x06),

        /**
         * initial_max_stream_data_uni (0x07).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     This parameter is an integer value specifying the initial flow control
         *     limit for unidirectional streams. This limit applies to newly created
         *     unidirectional streams opened by the endpoint that receives the transport
         *     parameter. In client transport parameters, this applies to streams with
         *     an identifier with the least significant two bits set to 0x03; in server
         *     transport parameters, this applies to streams with the least significant
         *     two bits set to 0x02.
         * }</pre></blockquote>
         */
        initial_max_stream_data_uni(0x07),

        /**
         * initial_max_streams_bidi (0x08).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     The initial maximum bidirectional streams parameter is an integer value
         *     that contains the initial maximum number of bidirectional streams the
         *     endpoint that receives this transport parameter is permitted to initiate.
         *     If this parameter is absent or zero, the peer cannot open bidirectional
         *     streams until a MAX_STREAMS frame is sent. Setting this parameter is equivalent
         *     to sending a MAX_STREAMS (Section 19.11) of the corresponding type with the
         *     same value.
         * }</pre></blockquote>
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-19.11">RFC 9000, Section 19.11</a>
         */
        initial_max_streams_bidi(0x08),

        /**
         * initial_max_streams_uni (0x09).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     The initial maximum unidirectional streams parameter is an integer value that
         *     contains the initial maximum number of unidirectional streams the endpoint
         *     that receives this transport parameter is permitted to initiate. If this parameter
         *     is absent or zero, the peer cannot open unidirectional streams until a MAX_STREAMS
         *     frame is sent. Setting this parameter is equivalent to sending a MAX_STREAMS
         *     (Section 19.11) of the corresponding type with the same value.
         * }</pre></blockquote>
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-19.11">RFC 9000, Section 19.11</a>
         */
        initial_max_streams_uni(0x09),

        /**
         * ack_delay_exponent (0x0a).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     The acknowledgment delay exponent is an integer value indicating an exponent
         *     used to decode the ACK Delay field in the ACK frame (Section 19.3). If this
         *     value is absent, a default value of 3 is assumed (indicating a multiplier of 8).
         *     Values above 20 are invalid.
         * }</pre></blockquote>
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-19.3">RFC 9000, Section 19.3</a>
         */
        ack_delay_exponent(0x0a),

        /**
         * max_ack_delay (0x0b).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     The maximum acknowledgment delay is an integer value indicating the maximum
         *     amount of time in milliseconds by which the endpoint will delay sending acknowledgments.
         *     This value SHOULD include the receiver's expected delays in alarms firing. For example,
         *     if a receiver sets a timer for 5ms and alarms commonly fire up to 1ms late, then it
         *     should send a max_ack_delay of 6ms. If this value is absent, a default of 25
         *     milliseconds is assumed. Values of 2^14 or greater are invalid.
         * }</pre></blockquote>
         */
        max_ack_delay(0x0b),

        /**
         * disable_active_migration (0x0c).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     The disable active migration transport parameter is included if the endpoint does not
         *     support active connection migration (Section 9) on the address being used during the
         *     handshake. An endpoint that receives this transport parameter MUST NOT use a new local
         *     address when sending to the address that the peer used during the handshake. This transport
         *     parameter does not prohibit connection migration after a client has acted on a
         *     preferred_address transport parameter. This parameter is a zero-length value.
         * }</pre></blockquote>
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-9">RFC 9000, Section 9</a>
         */
        disable_active_migration(0x0c),

        /**
         * preferred_address (0x0d).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     The server's preferred address is used to effect a change in server address at the
         *     end of the handshake, as described in Section 9.6. This transport parameter is only
         *     sent by a server.
         *     Servers MAY choose to only send a preferred address of one address family
         *     by sending an all-zero address and port (0.0.0.0:0 or [::]:0) for the
         *     other family. IP addresses are encoded in network byte order.
         *
         *     The preferred_address transport parameter contains an address and port for both
         *     IPv4 and IPv6. The four-byte IPv4 Address field is followed by the associated
         *     two-byte IPv4 Port field. This is followed by a 16-byte IPv6 Address field and
         *     two-byte IPv6 Port field. After address and port pairs, a Connection ID Length
         *     field describes the length of the following Connection ID field.
         *     Finally, a 16-byte Stateless Reset Token field includes the stateless reset
         *     token associated with the connection ID. The format of this transport parameter
         *     is shown in Figure 22 below.
         *
         *     The Connection ID field and the Stateless Reset Token field contain an alternative
         *     connection ID that has a sequence number of 1; see Section 5.1.1. Having these values
         *     sent alongside the preferred address ensures that there will be at least one
         *     unused active connection ID when the client initiates migration to the preferred
         *     address.
         *
         *     The Connection ID and Stateless Reset Token fields of a preferred address are
         *     identical in syntax and semantics to the corresponding fields of a NEW_CONNECTION_ID
         *     frame (Section 19.15). A server that chooses a zero-length connection ID MUST NOT
         *     provide a preferred address. Similarly, a server MUST NOT include a zero-length
         *     connection ID in this transport parameter. A client MUST treat a violation of
         *     these requirements as a connection error of type TRANSPORT_PARAMETER_ERROR.
         *
         * Preferred Address {
         *   IPv4 Address (32),
         *   IPv4 Port (16),
         *   IPv6 Address (128),
         *   IPv6 Port (16),
         *   Connection ID Length (8),
         *   Connection ID (..),
         *   Stateless Reset Token (128),
         * }
         *
         * Figure 22: Preferred Address Format
         * }</pre></blockquote>
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-5.1.1">RFC 9000, Section 5.1.1</a>
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-9.6">RFC 9000, Section 9.6</a>
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-19.15">RFC 9000, Section 19.15</a>
         */
        preferred_address(0x0d),

        /**
         * active_connection_id_limit (0x0e).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     This is an integer value specifying the maximum number of connection IDs from
         *     the peer that an endpoint is willing to store. This value includes the connection
         *     ID received during the handshake, that received in the preferred_address transport
         *     parameter, and those received in NEW_CONNECTION_ID frames. The value of the
         *     active_connection_id_limit parameter MUST be at least 2. An endpoint that receives
         *     a value less than 2 MUST close the connection with an error of type
         *     TRANSPORT_PARAMETER_ERROR. If this transport parameter is absent, a default of 2 is
         *     assumed. If an endpoint issues a zero-length connection ID, it will never send a
         *     NEW_CONNECTION_ID frame and therefore ignores the active_connection_id_limit value
         *     received from its peer.
         * }</pre></blockquote>
         */
        active_connection_id_limit(0x0e),

        /**
         * initial_source_connection_id (0x0f).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>:
         * <blockquote><pre>{@code
         *     This is the value that the endpoint included in the Source Connection ID field of
         *     the first Initial packet it sends for the connection; see Section 7.3.
         * }</pre></blockquote>
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-7.3">RFC 9000, Section 7.3</a>
         */
        initial_source_connection_id(0x0f),

        /**
         * retry_source_connection_id (0x10).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.2</a>
         * <blockquote><pre>{@code
         *     This is the value that the server included in the Source Connection ID field of a
         *     Retry packet; see Section 7.3. This transport parameter is only sent by a server.
         * }</pre></blockquote>
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-7.3">RFC 9000, Section 7.3</a>
         */
        retry_source_connection_id(0x10),

        /**
         * version_information (0x11).
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9368#name-version-information">
         *     RFC 9368, Section 3</a>
         * <blockquote><pre>{@code
         *     During the handshake, endpoints will exchange Version Information,
         *     which consists of a Chosen Version and a list of Available Versions.
         *     Any version of QUIC that supports this mechanism MUST provide a mechanism
         *     to exchange Version Information in both directions during the handshake,
         *     such that this data is authenticated.
         * }</pre></blockquote>
         */
        version_information(0x11);

        /*
         * Reserved Transport Parameters (31 * N + 27 for int values of N)
         * <p>
         * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
         *     RFC 9000, Section 18.1</a>
         * <blockquote><pre>{@code
         *     Transport parameters with an identifier of the form 31 * N + 27
         *     for integer values of N are reserved to exercise the requirement
         *     that unknown transport parameters be ignored. These transport
         *     parameters have no semantics and can carry arbitrary values.
         * }</pre></blockquote>
         */
        // No values are defined here, but these will be
        // ignored if received (see
        // sun.security.ssl.QuicTransportParametersExtension).

        /**
         * The number of known transport parameters.
         * This is also the number of enum values defined by the
         * {@link ParameterId} enumeration.
         */
        private static final int PARAMETERS_COUNT = ParameterId.values().length;

        ParameterId(int idx) {
            // idx() and valueOf() assume that idx = ordinal;
            // if that's no longer the case, update the implementation
            // and remove this assert.
            assert idx == ordinal();
        }

        @Override
        public int idx() {
            return ordinal();
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }

       private static Optional<ParameterId> valueOf(long idx) {
            if (idx < 0 || idx >= PARAMETERS_COUNT) return Optional.empty();
            return Optional.of(values()[(int)idx]);
        }
    }

    public record VersionInformation(int chosenVersion, int[] availableVersions) { }

    /**
     * A map to store transport parameter values.
     * Contains a byte array corresponding to the encoded value
     * of the parameter.
     */
    private final Map<ParameterId, byte[]> values;

    /**
     * Constructs a new empty array of Quic transport parameters.
     */
    public QuicTransportParameters() {
        values = new EnumMap<>(ParameterId.class);
    }

    /**
     * Constructs a new collection of Quic transport parameters initialized
     * from the specified collection.
     * @param params the parameter collection used to initialize this object     *
     */
    public QuicTransportParameters(QuicTransportParameters params) {
        values = new EnumMap<>(params.values);
    }

    /**
     * {@return true if the given parameter is present, false otherwise}
     * @apiNote
     * This is equivalent to {@link #getParameter(TransportParameterId)
     * getParameter(id) != null}, but avoids cloning the parameter value.
     * @param id the parameter id
     */
    public boolean isPresent(TransportParameterId id) {
        byte[] value = values.get((ParameterId) id);
        return value != null;
    }

    /**
     * {@return the value of the given parameter, as a byte array, or
     * {@code null} if the parameter is absent.
     * @param id the parameter id
     */
    public byte[] getParameter(TransportParameterId id) {
        byte[] value = values.get((ParameterId) id);
        return value == null ? null : value.clone();
    }

    /**
     * {@return true if the value of the given parameter matches the given connection ID}
     * @param id the transport parameter id
     * @param connectionId the connection id to match against
     */
    public boolean matches(TransportParameterId id, QuicConnectionId connectionId) {
        byte[] value = values.get((ParameterId) id);
        return connectionId.matches(ByteBuffer.wrap(value).asReadOnlyBuffer());
    }

    /**
     * Sets the value of the given parameter.
     * If the given value is {@code null}, the parameter is removed.
     * @param id the parameter id
     * @param value the new parameter value, or {@code null}.
     * @throws IllegalArgumentException if the given value is invalid for
     *         the given parameter id
     */
    public void setParameter(TransportParameterId id, byte[] value) {
        ParameterId pid = checkParameterValue(id, value);
        if (value != null) {
            values.put(pid, value.clone());
        } else {
            values.remove(pid);
        }
    }

    /**
     * {@return the value of the given parameter, as an unsigned int
     * in the range {@code [0, 2^62 - 1]}}
     * If the parameter is not present its default value (as specified in the RFC) is returned.
     * @param id the parameter id
     * @throws IllegalArgumentException if the value of the given parameter
     * cannot be decoded as a variable length unsigned int
     */
    public long getIntParameter(TransportParameterId id) {
        return getIntParameter((ParameterId)id);
    }

    private long getIntParameter(final ParameterId pid) {
        return switch (pid) {
            case max_idle_timeout, max_udp_payload_size, initial_max_data,
                    initial_max_stream_data_bidi_local, initial_max_stream_data_bidi_remote,
                    initial_max_stream_data_uni, initial_max_streams_bidi,
                    initial_max_streams_uni, ack_delay_exponent, max_ack_delay,
                    active_connection_id_limit -> {
                byte[] value = values.get(pid);
                final long res;
                if (value == null) {
                    res = switch (pid) {
                        case active_connection_id_limit -> 2;
                        case max_udp_payload_size -> 65527;
                        case ack_delay_exponent -> 3;
                        case max_ack_delay -> 25;
                        default -> 0;
                    };
                } else {
                    res = decodeVLIntFully(pid, ByteBuffer.wrap(value));
                }
                yield res;
            }
            default -> throw new IllegalArgumentException(String.valueOf(pid));

        };
    }

    /**
     * {@return the value of the given parameter, as an unsigned int
     * in the range {@code [0, 2^62 - 1]}}
     * If the parameter is not present then {@code defaultValue} is returned.
     * @param id the parameter id
     * @throws IllegalArgumentException if the value of the given parameter
     * cannot be decoded as a variable length unsigned int or if the {@code defaultValue}
     * exceeds the maximum allowed value for variable length integer
     */
    public long getIntParameter(TransportParameterId id, long defaultValue) {
        if (defaultValue > VariableLengthEncoder.MAX_ENCODED_INTEGER) {
            throw new IllegalArgumentException("default value " + defaultValue
                    + " exceeds maximum allowed variable length"
                    + " integer value " + VariableLengthEncoder.MAX_ENCODED_INTEGER);
        }
        ParameterId pid = (ParameterId)id;
        return switch (pid) {
            case max_idle_timeout, max_udp_payload_size, initial_max_data,
                    initial_max_stream_data_bidi_local, initial_max_stream_data_bidi_remote,
                    initial_max_stream_data_uni, initial_max_streams_bidi,
                    initial_max_streams_uni, ack_delay_exponent, max_ack_delay,
                    active_connection_id_limit -> {
                byte[] value = values.get(pid);
                final long res;
                if (value == null) {
                    res = defaultValue;
                } else {
                    res = decodeVLIntFully(pid, ByteBuffer.wrap(value));
                }
                yield res;
            }
            default -> throw new IllegalArgumentException(String.valueOf(pid));
        };
    }

    /**
     * Sets the value of the given parameter, as an unsigned int.
     * If a negative value is provided, the parameter is removed.
     *
     * @param id the parameter id
     * @param value the new value of the parameter, or a negative value
     *
     * @throws IllegalArgumentException if the value of the given parameter is
     * not an int, or if the provided value is out of range
     */
    public void setIntParameter(TransportParameterId id, long value) {
        ParameterId pid = (ParameterId)id;
        switch (pid) {
            case max_idle_timeout, max_udp_payload_size, initial_max_data,
                    initial_max_stream_data_bidi_local, initial_max_stream_data_bidi_remote,
                    initial_max_stream_data_uni, initial_max_streams_bidi,
                    initial_max_streams_uni, ack_delay_exponent, max_ack_delay,
                    active_connection_id_limit -> {
                byte[] v = null;
                if (value >= 0) {
                    int length = VariableLengthEncoder.getEncodedSize(value);
                    if (length <= 0) throw new IllegalArgumentException("failed to encode " + value);
                    int size = VariableLengthEncoder.encode(ByteBuffer.wrap(v = new byte[length]), value);
                    assert size == length;
                    checkParameterValue(pid, v);
                }
                setOrRemove(pid, v);
            }
            default -> throw new IllegalArgumentException(String.valueOf(pid));
        }
    }

    /**
     * {@return the value of the given parameter, as a boolean}
     * If the parameter is not present its default value (false)
     * is returned.
     *
     * @param id the parameter id
     *
     * @throws IllegalArgumentException if the value of the given parameter
     * is not a boolean
     */
    public boolean getBooleanParameter(TransportParameterId id) {
        ParameterId pid = (ParameterId)id;
        if (pid != ParameterId.disable_active_migration) {
            throw new IllegalArgumentException(String.valueOf(id));
        }
        return values.get(pid) != null;
    }

    /**
     * Sets the value of the given parameter, as a boolean.
     * @apiNote
     * It is not possible to distinguish between a boolean parameter
     * whose value is absent and a parameter whose value is false.
     * Both are represented by a {@code null} value in the parameter
     * array.
     * @param id the parameter id
     * @param value the new value of the parameter
     * @throws IllegalArgumentException if the value of the given parameter is
     * not a boolean
     */
    public void setBooleanParameter(TransportParameterId id, boolean value) {
        ParameterId pid = (ParameterId)id;
        if (pid != ParameterId.disable_active_migration) {
            throw new IllegalArgumentException(String.valueOf(id));
        }
        setOrRemove(pid, value ? NOBYTES : null);
    }

    private void setOrRemove(ParameterId pid, byte[] value) {
        if (value != null) {
            values.put(pid, value);
        } else {
            values.remove(pid);
        }
    }

    /**
     * {@return the value of the given parameter, as {@link VersionInformation}}
     * If the parameter is not present {@code null} is returned
     *
     * @param id the parameter id
     *
     * @throws IllegalArgumentException if the value of the given parameter
     * is not a version information
     * @throws QuicTransportException if the parameter value has incorrect length,
     *      or if any version is equal to zero
     */
    public VersionInformation getVersionInformationParameter(TransportParameterId id)
            throws QuicTransportException {
        ParameterId pid = (ParameterId)id;
        if (pid != ParameterId.version_information) {
            throw new IllegalArgumentException(String.valueOf(id));
        }
        byte[] val = values.get(pid);
        if (val == null) {
            return null;
        }
        if (val.length < 4 || (val.length & 3) != 0) {
            throw new QuicTransportException(
                    "Invalid version information length " + val.length,
                    null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        ByteBuffer bbval = ByteBuffer.wrap(val);
        assert bbval.order() == ByteOrder.BIG_ENDIAN;
        int chosen = bbval.getInt();
        if (chosen == 0) {
            throw new QuicTransportException(
                    "[version_information] Chosen Version = 0",
                    null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        int[] available = new int[bbval.remaining() / 4];
        for (int i = 0; i < available.length; i++) {
            int version = bbval.getInt();
            if (version == 0) {
                throw new QuicTransportException(
                        "[version_information] Available Version = 0",
                        null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            }
            available[i] = version;
        }
        return new VersionInformation(chosen, available);
    }

    /**
     * Sets the value of the given parameter, as {@link VersionInformation}.
     * @param id the parameter id
     * @param value the new value of the parameter
     * @throws IllegalArgumentException if the value of the given parameter is
     * not a version information
     */
    public void setVersionInformationParameter(TransportParameterId id, VersionInformation value) {
        ParameterId pid = (ParameterId)id;
        if (pid != ParameterId.version_information) {
            throw new IllegalArgumentException(String.valueOf(id));
        }
        byte[] val = new byte[value.availableVersions.length * 4 + 4];
        ByteBuffer bbval = ByteBuffer.wrap(val);
        assert bbval.order() == ByteOrder.BIG_ENDIAN;
        bbval.putInt(value.chosenVersion);
        for (int available : value.availableVersions) {
            bbval.putInt(available);
        }
        assert !bbval.hasRemaining();
        values.put(pid, val);
    }

    /**
     * {@return a {@link VersionInformation} object corresponding to the specified versions}
     * @param chosenVersion     chosen version
     * @param availableVersions available versions
     */
    public static VersionInformation buildVersionInformation(
            QuicVersion chosenVersion, List<QuicVersion> availableVersions) {
        int[] available = new int[availableVersions.size()];
        for (int i = 0; i < available.length; i++) {
            available[i] = availableVersions.get(i).versionNumber();
        }
        return new VersionInformation(chosenVersion.versionNumber(), available);
    }

    /**
     * Sets the value of a parameter whose format corresponds to the
     * {@link ParameterId#preferred_address} parameter.
     * @param id    the parameter id
     * @param ipv4  the preferred IPv4 address (or the IPv4 wildcard address)
     * @param port4 the preferred IPv4 port (or 0)
     * @param ipv6  the preferred IPv6 address (or the IPv6 wildcard address)
     * @param port6 the preferred IPv6 port (or 0)
     * @param connectionId    the connection id bytes
     * @param statelessToken  the stateless token
     * @throws IllegalArgumentException if any of the given parameters has an
     *    illegal value, or if the given parameter value is not of the
     *    {@link ParameterId#preferred_address} format
     * @see ParameterId#preferred_address
     */
    public void setPreferredAddressParameter(TransportParameterId id,
                                             Inet4Address ipv4, int port4,
                                             Inet6Address ipv6, int port6,
                                             ByteBuffer connectionId,
                                             ByteBuffer statelessToken) {
        ParameterId pid = (ParameterId)id;
        if (pid != ParameterId.preferred_address) {
            throw new IllegalArgumentException(String.valueOf(id));
        }
        int cidlen = connectionId.remaining();
        if (cidlen == 0 || cidlen > QuicConnectionId.MAX_CONNECTION_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "connection id len out of range [1..20]: " + cidlen);
        }
        int tklen = statelessToken.remaining();
        if (tklen != TOKEN_SIZE) {
            throw new IllegalArgumentException("bad stateless token length: expected 16, found " + tklen);
        }
        if (port4 < 0 || port4 > MAX_PORT)
            throw new IllegalArgumentException("IPv4 port out of range: " + port4);
        if (port6 < 0 || port6 > MAX_PORT)
            throw new IllegalArgumentException("IPv6 port out of range: " + port6);
        int size = MIN_PREF_ADDR_SIZE + cidlen;
        byte[] value = new byte[size];
        ByteBuffer buffer = ByteBuffer.wrap(value);
        if (!ipv4.isAnyLocalAddress()) {
            buffer.put(IPV4_ADDR_OFFSET, ipv4.getAddress());
        }
        buffer.putShort(IPV4_PORT_OFFSET, (short) port4);
        if (!ipv6.isAnyLocalAddress()) {
            buffer.put(IPV6_ADDR_OFFSET, ipv6.getAddress());
        }
        buffer.putShort(IPV6_PORT_OFFSET, (short)port6);
        buffer.put(CID_LEN_OFFSET, (byte) cidlen);
        buffer.put(CID_OFFSET, connectionId, connectionId.position(), cidlen);
        assert size - CID_OFFSET - cidlen == TOKEN_SIZE : (size - CID_OFFSET - cidlen);
        assert tklen == TOKEN_SIZE;
        buffer.put(CID_OFFSET + cidlen, statelessToken, statelessToken.position(), tklen);
        values.put(pid, value);
    }

    /**
     * {@return the size in bytes required to encode the parameter
     *  array}
     */
    public int size() {
        int size = 0;
        for (var kv : values.entrySet()) {
            var i = kv.getKey().idx();
            var value = kv.getValue();
            if (value == null) continue;
            assert value.length > 0 || i == ParameterId.disable_active_migration.idx();
            size += VariableLengthEncoder.getEncodedSize(i);
            size += VariableLengthEncoder.getEncodedSize(value.length);
            size += value.length;
        }
        return size;
    }

    /**
     * Encodes the transport parameters into the given byte buffer.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#transport-parameter-definitions">
     *     RFC 9000, Section 18.2</a>:
     * <blockquote><pre>{@code
     * The extension_data field of the quic_transport_parameters
     * extension defined in [QUIC-TLS] contains the QUIC transport
     * parameters. They are encoded as a sequence of transport
     * parameters, as shown in Figure 20:
     *
     * Transport Parameters {
     *   Transport Parameter (..) ...,
     * }
     *
     * Figure 20: Sequence of Transport Parameters
     *
     * Each transport parameter is encoded as an (identifier, length,
     * value) tuple, as shown in Figure 21:
     *
     * Transport Parameter {
     *   Transport Parameter ID (i),
     *   Transport Parameter Length (i),
     *   Transport Parameter Value (..),
     * }
     * }</pre></blockquote>
     *
     * @param buffer a byte buffer in which to encode the transport parameters
     * @return the number of bytes written
     * @throws BufferOverflowException if there is not enough space in the
     *         provided buffer
     * @see jdk.internal.net.quic.QuicTLSEngine#setLocalQuicTransportParameters(ByteBuffer)
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-18">
     *      RFC 9000, Section 18</a>
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9001">
     *      RFC 9001 [QUIC-TLS]</a>
     */
    public int encode(ByteBuffer buffer) {
        int start = buffer.position();
        for (var kv : values.entrySet()) {
            var i = kv.getKey().idx();
            var value = kv.getValue();
            if (value == null) continue;

            VariableLengthEncoder.encode(buffer, i);
            VariableLengthEncoder.encode(buffer, value.length);
            buffer.put(value);
        }
        var written = buffer.position() - start;
        if (QuicTransportParameters.class.desiredAssertionStatus()) {
            int size = size();
            assert written == size
                    : "unexpected number of bytes encoded: %d, expected %d"
                    .formatted(written, size);
        }
        return written;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Quic Transport Params[");
        for (var kv : values.entrySet()) {
            var param = kv.getKey();
            var value = kv.getValue();
            if (value != null) {
                // param is set
                // we just return the string representation of the param ids and don't include
                // the encoded values
                sb.append(param);
                sb.append(", ");
            }
        }
        return sb.append("]").toString();
    }

    // values for (variable length) integer params are decoded, for other params
    // that are set, the value is printed as a hex string.
    public String toStringWithValues() {
        final StringBuilder sb = new StringBuilder("Quic Transport Params[");
        for (var kv : values.entrySet()) {
            var param = kv.getKey();
            var value = kv.getValue();
            if (value != null) {
                // param is set, so include it in the string representation
                sb.append(param);
                final String valAsString = valueToString(param);
                sb.append("=").append(valAsString);
                sb.append(", ");
            }
        }
        return sb.append("]").toString();
    }

    private String valueToString(final ParameterId parameterId) {
        assert this.values.get(parameterId) != null : "param " + parameterId + " not set";
        try {
            return switch (parameterId) {
                // int params
                case max_idle_timeout, max_udp_payload_size, initial_max_data,
                     initial_max_stream_data_bidi_local,
                     initial_max_stream_data_bidi_remote,
                     initial_max_stream_data_uni, initial_max_streams_bidi,
                     initial_max_streams_uni, ack_delay_exponent, max_ack_delay,
                     active_connection_id_limit ->
                        String.valueOf(getIntParameter(parameterId));
                default ->
                        '"' + HexFormat.of().formatHex(values.get(parameterId)) + '"';
            };
        } catch (RuntimeException e) {
            // if the value was a malformed integer, return the hex representation
            return '"' + HexFormat.of().formatHex(values.get(parameterId)) + '"';
        }
    }

    /**
     * Decodes the quic transport parameters from the given buffer.
     * Parameters which are not supported are silently discarded.
     *
     * @param buffer a byte buffer containing the transport parameters
     *
     * @return the decoded transport parameters
     * @throws QuicTransportException if the parameters couldn't be decoded
     *
     * @see jdk.internal.net.quic.QuicTLSEngine#setRemoteQuicTransportParametersConsumer(QuicTransportParametersConsumer) (ByteBuffer)
     * @see jdk.internal.net.quic.QuicTransportParametersConsumer#accept(ByteBuffer)
     * @see #encode(ByteBuffer)
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-18">
     *      RFC 9000, Section 18</a>
     */
    public static QuicTransportParameters decode(ByteBuffer buffer)
            throws QuicTransportException {
        QuicTransportParameters parameters = new QuicTransportParameters();
        while (buffer.hasRemaining()) {
            final long id = VariableLengthEncoder.decode(buffer);
            final ParameterId pid = TransportParameterId.valueOf(id)
                    .orElse(null);
            final String name = pid == null ? String.valueOf(id) : pid.toString();
            long length = VariableLengthEncoder.decode(buffer);
            if (length < 0) {
                throw new QuicTransportException(
                        "Can't decode length for transport parameter " + name,
                        null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            }
            if (length > buffer.remaining()) {
                throw new QuicTransportException("Transport parameter truncated",
                        null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            }
            byte[] value = new byte[(int) length];
            buffer.get(value);
            if (pid == null) {
                // RFC-9000, section 7.4.2: An endpoint MUST ignore transport parameters
                // that it does not support.
                if (Log.quicControl()) {
                    Log.logQuic("ignoring unsupported transport parameter: " + name);
                }
                continue;
            }
            try {
                checkParameterValue(pid, value);
            } catch (RuntimeException e) {
                throw new QuicTransportException(e.getMessage(),
                        null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            }
            var oldValue = parameters.values.putIfAbsent(pid, value);
            if (oldValue != null) {
                throw new QuicTransportException(
                        "Duplicate transport parameter " + name,
                        null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            }
        }
        return parameters;
    }

    /**
     * Reads the preferred address encoded in the value
     * of a parameter whose format corresponds to the {@link
     * ParameterId#preferred_address} parameter.
     * If the given {@code value} is {@code null}, this
     * method returns {@code null}.
     * Otherwise, the returned list contains
     * at most one IPv4 address and/or one IPv6 address.
     *
     * @apiNote
     * To obtain the list of addresses encoded in the
     * {@link ParameterId#preferred_address} parameter, use
     * {@link #getPreferredAddress(TransportParameterId, byte[])
     * getPreferredAddress(ParameterId.preferred_address,}
     * {@link #getParameter(TransportParameterId)
     * parameters.getParameter(ParameterId.preferred_address)}.
     *
     * @param id the parameter id
     * @param value the value of the parameter
     * @return a list of {@link InetSocketAddress}, or {@code null} if the
     *         given value is {@code null}.
     * @see ParameterId#preferred_address
     */
    public static List<InetSocketAddress> getPreferredAddress(
            TransportParameterId id, byte[] value) {
        if (value == null) return null;
        if (value.length < MIN_PREF_ADDR_SIZE) {
            throw new IllegalArgumentException(id +
                    ": not enough bytes in value; found " + value.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(value);
        int ipv4port = buffer.getShort(IPV4_PORT_OFFSET) & 0xFFFF;
        int ipv6port = buffer.getShort(IPV6_PORT_OFFSET) & 0xFFFF;

        byte[] ipv4 = new byte[IPV4_SIZE];
        buffer.get(IPV4_ADDR_OFFSET, ipv4);
        byte[] ipv6 = new byte[IPV6_SIZE];
        buffer.get(IPV6_ADDR_OFFSET, ipv6);
        InetSocketAddress ipv4addr = new InetSocketAddress(getByAddress(id, ipv4), ipv4port);
        InetSocketAddress ipv6addr = new InetSocketAddress(getByAddress(id, ipv6), ipv6port);
        return Stream.of(ipv4addr, ipv6addr)
                .filter((isa) -> !isa.getAddress().isAnyLocalAddress())
                .toList();
    }

    /**
     * Reads the connection id bytes from the value of a parameter
     * whose format corresponds to the {@link ParameterId#preferred_address}
     * parameter.
     * If the given {@code value} is {@code null}, this
     * method returns {@code null}.
     *
     * @param preferredAddressValue  the value of {@link ParameterId#preferred_address} param
     * @return the connection id bytes
     * @see ParameterId#preferred_address
     */
    public static ByteBuffer getPreferredConnectionId(final byte[] preferredAddressValue) {
        if (preferredAddressValue == null) {
            return null;
        }
        final int length = getPreferredConnectionIdLength(ParameterId.preferred_address,
                preferredAddressValue);
        return ByteBuffer.wrap(preferredAddressValue, CID_OFFSET, length);
    }

    /**
     * Reads the stateless token bytes from the value of a parameter
     * whose format corresponds to the {@link ParameterId#preferred_address}
     * parameter.
     *
     * If the given {@code value} is {@code null}, this
     * method returns {@code null}.
     *
     * @param preferredAddressValue  the value of {@link ParameterId#preferred_address} param
     * @return the stateless reset token bytes
     * @see ParameterId#preferred_address
     */
    public static byte[] getPreferredStatelessResetToken(final byte[] preferredAddressValue) {
        if (preferredAddressValue == null) {
            return null;
        }
        final int length = getPreferredConnectionIdLength(ParameterId.preferred_address,
                preferredAddressValue);
        final int offset = CID_OFFSET + length;
        final byte[] statelessResetToken = new byte[TOKEN_SIZE];
        System.arraycopy(preferredAddressValue, offset, statelessResetToken, 0, TOKEN_SIZE);
        return statelessResetToken;
    }

    static final byte[] NOBYTES = new byte[0];
    static final int IPV6_SIZE = 16;
    static final int IPV4_SIZE = 4;
    static final int PORT_SIZE = 2;
    static final int TOKEN_SIZE = 16;
    static final int CIDLEN_SIZE = 1;
    static final int IPV4_ADDR_OFFSET = 0;
    static final int IPV4_PORT_OFFSET = IPV4_ADDR_OFFSET + IPV4_SIZE;
    static final int IPV6_ADDR_OFFSET = IPV4_PORT_OFFSET + PORT_SIZE;
    static final int IPV6_PORT_OFFSET = IPV6_ADDR_OFFSET + IPV6_SIZE;
    static final int CID_LEN_OFFSET   = IPV6_PORT_OFFSET + PORT_SIZE;
    static final int CID_OFFSET       = CID_LEN_OFFSET + CIDLEN_SIZE;
    static final int MIN_PREF_ADDR_SIZE = CID_OFFSET + TOKEN_SIZE;
    static final int MAX_PORT         = 0xFFFF;

    private static int getPreferredConnectionIdLength(TransportParameterId id, byte[] value) {
        if (value.length < MIN_PREF_ADDR_SIZE) {
            throw new IllegalArgumentException(id +
                    ": not enough bytes in value; found " + value.length);
        }
        int length = value[CID_LEN_OFFSET] & 0xFF;
        if (length > QuicConnectionId.MAX_CONNECTION_ID_LENGTH || length == 0) {
            throw new IllegalArgumentException(id +
                    ": invalid preferred connection ID length: " + length);
        }
        if (length != value.length - MIN_PREF_ADDR_SIZE) {
            throw new IllegalArgumentException(id +
                    ": invalid preferred address length: " + value.length +
                    ", expected: " + (MIN_PREF_ADDR_SIZE + length));
        }
        return length;
    }

    private static InetAddress getByAddress(TransportParameterId id, byte[] address) {
        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException x) {
            // should not happen
            throw new IllegalArgumentException(id +
                    "Invalid address: " + HexFormat.of().formatHex(address));
        }
    }

    /**
     * verifies that the {@code value} is acceptable (as specified in the RFC) for the
     * {@code tpid}
     *
     * @param tpid  the transport parameter id
     * @param value the value
     * @return the corresponding parameter id if the value is acceptable, else throws a
     * {@link IllegalArgumentException}
     */
    private static ParameterId checkParameterValue(TransportParameterId tpid, byte[] value) {
        ParameterId id = (ParameterId)tpid;
        if (value != null) {
            switch (id) {
                case disable_active_migration -> {
                    if (value.length > 0)
                        throw new IllegalArgumentException(id
                                + ": value must be null or 0-length; found "
                                + value.length + " bytes");
                }
                case stateless_reset_token -> {
                    if (value.length != 16)
                        throw new IllegalArgumentException(id +
                                ": value must be null or 16 bytes long; found "
                                + value.length + " bytes");
                }
                case initial_source_connection_id, original_destination_connection_id,
                        retry_source_connection_id -> {
                    if (value.length > QuicConnectionId.MAX_CONNECTION_ID_LENGTH) {
                        throw new IllegalArgumentException(id +
                                ": value must not exceed " +
                                QuicConnectionId.MAX_CONNECTION_ID_LENGTH +
                                "bytes; found " + value.length + " bytes");
                    }
                }
                case preferred_address -> getPreferredConnectionIdLength(id, value);
                case version_information -> {
                    if (value.length < 4 || value.length % 4 != 0) {
                        throw new IllegalArgumentException(id +
                                ": value length must be a positive multiple of 4 " +
                                "bytes; found " + value.length + " bytes");
                    }
                }
                default -> {
                    long intvalue;
                    try {
                        intvalue = decodeVLIntFully(id, ByteBuffer.wrap(value));
                    } catch (IllegalArgumentException x) {
                        throw x;
                    } catch (Exception x) {
                        throw new IllegalArgumentException(id +
                                ": value is not a valid variable length integer", x);
                    }
                    if (intvalue < 0)
                        throw new IllegalArgumentException(id +
                                ": value is not a valid variable length integer");
                    switch (id) {
                        case max_udp_payload_size -> {
                            if (intvalue < 1200 || intvalue > 65527) {
                                throw new IllegalArgumentException(id +
                                        ": value out of range [1200, 65527]; found "
                                        + intvalue);
                            }
                        }
                        case ack_delay_exponent -> {
                            if (intvalue > 20) {
                                throw new IllegalArgumentException(id +
                                        ": value out of range [0, 20]; found "
                                        + intvalue);
                            }
                        }
                        case max_ack_delay -> {
                            if (intvalue >= (1 << 14)) {
                                throw new IllegalArgumentException(id +
                                        ": value out of range [0, 2^14); found "
                                        + intvalue);
                            }
                        }
                        case active_connection_id_limit -> {
                            if (intvalue < 2) {
                                throw new IllegalArgumentException(id +
                                        ": value out of range [2...]; found "
                                        + intvalue);
                            }
                        }
                        case initial_max_streams_bidi, initial_max_streams_uni -> {
                            if (intvalue >= 1L << 60) {
                                throw new IllegalArgumentException(id +
                                        ": value out of range [0,2^60); found "
                                        + intvalue);
                            }
                        }
                    }
                }
            }
        }
        return id;
    }

    private static long decodeVLIntFully(ParameterId id, ByteBuffer buffer) {
        long value = VariableLengthEncoder.decode(buffer);
        if (value < 0 || value > (1L << 62) - 1) {
            throw new IllegalArgumentException(id +
                    ": failed to decode variable length integer");
        }
        if (buffer.hasRemaining())
            throw new IllegalArgumentException(id +
                    ": extra bytes in provided value at index "
                    + buffer.position());
        return value;
    }
}
