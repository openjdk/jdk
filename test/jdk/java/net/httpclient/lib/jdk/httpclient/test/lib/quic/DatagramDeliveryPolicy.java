/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.httpclient.test.lib.quic;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import jdk.internal.net.http.quic.packets.QuicPacket;

/**
 * Used by the {@link QuicServer Quic server} and the {@link QuicServerConnection Quic server
 * connection} to decide whether an incoming datagram needs to be dropped
 */
public interface DatagramDeliveryPolicy {

    /**
     * System property for configuring the incoming datagram delivery policy for Quic server
     */
    public static final String SYS_PROP_INCOMING_DELIVERY_POLICY =
            "jdk.internal.httpclient.test.quic.incoming";

    /**
     * System property for configuring the outgoing datagram delivery policy for Quic server
     */
    public static final String SYS_PROP_OUTGOING_DELIVERY_POLICY =
            "jdk.internal.httpclient.test.quic.outgoing";

    /**
     * Will be called to decide if an incoming or an outgoing datagram should be dropped
     *
     * @param address    The source or the destination address for the datagram
     * @param payload    The datagram payload
     * @param connection The connection which was chosen to handle the Quic packet
     * @return true if the datagram should be dropped, false otherwise
     */
    boolean shouldDrop(SocketAddress address, ByteBuffer payload, QuicServerConnection connection,
                       QuicPacket.HeadersType headersType);

    /**
     * Will be called to decide if an incoming datagram, which wasn't matched against
     * any specific Quic connection, should be dropped
     *
     * @param source  The source address which transmitted the datagram
     * @param payload The datagram payload
     * @return true if the datagram should be dropped, false otherwise
     */
    boolean shouldDrop(SocketAddress source, ByteBuffer payload, QuicPacket.HeadersType headersType);

    static final DatagramDeliveryPolicy ALWAYS_DELIVER = new DatagramDeliveryPolicy() {
        @Override
        public boolean shouldDrop(final SocketAddress address,
                                  final ByteBuffer payload,
                                  final QuicServerConnection connection,
                                  final QuicPacket.HeadersType headersType) {
            return false;
        }

        @Override
        public boolean shouldDrop(final SocketAddress source, final ByteBuffer payload,
                                  final QuicPacket.HeadersType headersType) {
            return false;
        }

        @Override
        public String toString() {
            return "[DatagramDeliveryPolicy=always deliver]";
        }
    };

    static final DatagramDeliveryPolicy NEVER_DELIVER = new DatagramDeliveryPolicy() {
        @Override
        public boolean shouldDrop(final SocketAddress address,
                                  final ByteBuffer payload,
                                  final QuicServerConnection connection,
                                  final QuicPacket.HeadersType headersType) {
            return true;
        }

        @Override
        public boolean shouldDrop(final SocketAddress source, final ByteBuffer payload,
                                  final QuicPacket.HeadersType headersType) {
            return true;
        }

        @Override
        public String toString() {
            return "[DatagramDeliveryPolicy=never deliver]";
        }
    };

    static final class FixedRate implements DatagramDeliveryPolicy {
        private final int n;
        private final AtomicLong counter = new AtomicLong();

        FixedRate(final int n) {
            if (n <= 0) {
                throw new IllegalArgumentException("n should be greater than 0");
            }
            this.n = n;
        }

        @Override
        public boolean shouldDrop(final SocketAddress address,
                                  final ByteBuffer payload,
                                  final QuicServerConnection connection,
                                  final QuicPacket.HeadersType headersType) {
            final long current = counter.incrementAndGet();
            return current % n == 0; // drop every nth
        }

        @Override
        public boolean shouldDrop(final SocketAddress source, final ByteBuffer payload,
                                  final QuicPacket.HeadersType headersType) {
            final long current = counter.incrementAndGet();
            return current % n == 0; // drop every nth
        }

        @Override
        public String toString() {
            return "[DatagramDeliveryPolicy=drop every " + n + "]";
        }
    }

    static final class RandomDrop implements DatagramDeliveryPolicy {
        private final long seed;
        private final Random random;

        RandomDrop() {
            Long s = null;
            try {
                // note that Long.valueOf(null) also throws a
                // NumberFormatException so if the property is undefined this
                // will still work correctly
                s = Long.valueOf(System.getProperty("seed"));
            } catch (NumberFormatException e) {
                // do nothing: seed is still null
            }
            this.seed = s != null ? s : new Random().nextLong();
            this.random = new Random(seed);
        }

        @Override
        public boolean shouldDrop(final SocketAddress address,
                                  final ByteBuffer payload,
                                  final QuicServerConnection connection,
                                  final QuicPacket.HeadersType headersType) {
            return this.random.nextLong() % 42 == 0;
        }

        @Override
        public boolean shouldDrop(final SocketAddress source, final ByteBuffer payload,
                                  final QuicPacket.HeadersType headersType) {
            return this.random.nextLong() % 42 == 0;
        }

        @Override
        public String toString() {
            return "[DatagramDeliveryPolicy=drop randomly, seed=" + seed + "]";
        }
    }

    /**
     * {@return a DatagramDeliveryPolicy which always returns false from the {@code shouldDrop}
     * methods}
     */
    public static DatagramDeliveryPolicy alwaysDeliver() {
        return ALWAYS_DELIVER;
    }

    /**
     * {@return a DatagramDeliveryPolicy which always returns true from the {@code shouldDrop}
     * methods}
     */
    public static DatagramDeliveryPolicy neverDeliver() {
        return NEVER_DELIVER;
    }

    /**
     * @param n the repeat count at which the datagram will be dropped
     * @return a DatagramDeliveryPolicy which will return true on every {@code n}th call to
     * either of the {@code shouldDrop} methods
     */
    public static DatagramDeliveryPolicy dropEveryNth(final int n) {
        return new FixedRate(n);
    }

    /**
     * @return a DatagramDeliveryPolicy which will randomly return true from the {@code shouldDrop}
     * methods. If the {@code seed} system property is set then the {@code Random} instance used by
     * this policy will use that seed.
     */
    public static DatagramDeliveryPolicy dropRandomly() {
        return new RandomDrop();
    }

    private static String privilegedGetProperty(String property) {
        return privilegedGetProperty(property, null);
    }

    private static String privilegedGetProperty(String property, String defval) {
        return System.getProperty(property, defval);
    }

    /**
     * Reads the system property {@code sysPropName} and parses the value into a
     * {@link DatagramDeliveryPolicy}. If the {@code sysPropName} system property isn't set or
     * is set to a value of {@link String#isBlank() blank}, then this method returns a
     * {@link DatagramDeliveryPolicy#alwaysDeliver() always deliver policy}.
     * <p>
     * The {@code sysPropName} if set is expected to have either of the following values:
     * <ul>
     *     <li>{@code always} - this returns a {@link DatagramDeliveryPolicy#alwaysDeliver()
     *     always deliver policy}</li>
     *     <li>{@code never} - this returns a {@link DatagramDeliveryPolicy#neverDeliver()
     *     never deliver policy}</li>
     *     <li>{@code fixed=<n>} - where n is a positive integer, this returns a
     *     {@link DatagramDeliveryPolicy#dropEveryNth(int) dropEveryNth policy}</li>
     *     <li>{@code random} - this returns a
     *     {@link DatagramDeliveryPolicy#dropRandomly() dropRandomly policy}</li>
     * </ul>
     * </p>
     *
     * @param sysPropName The system property name to use
     * @return a DatagramDeliveryPolicy
     * @throws ParseException If the system property value cannot be parsed into a
     *                        DatagramDeliveryPolicy
     */
    private static DatagramDeliveryPolicy fromSystemProperty(final String sysPropName)
            throws ParseException {
        String val = privilegedGetProperty(sysPropName);
        if (val == null || val.isBlank()) {
            return ALWAYS_DELIVER;
        }
        val = val.trim();
        if (val.startsWith("fixed=")) {
            // read the characters following "fixed="
            String rateVal = val.substring("fixed=".length());
            final int n;
            try {
                n = Integer.parseInt(rateVal);
            } catch (NumberFormatException nfe) {
                throw new ParseException("Unexpected value: " + val, "fixed=".length());
            }
            return dropEveryNth(n);
        } else if (val.equals("random")) {
            return dropRandomly();
        } else if (val.equals("always")) {
            return ALWAYS_DELIVER;
        } else if (val.equals("never")) {
            return NEVER_DELIVER;
        } else {
            throw new ParseException("Unexpected value: " + val, 0);
        }
    }

    /**
     * Returns the default incoming datagram delivery policy. This takes into account the
     * {@link DatagramDeliveryPolicy#SYS_PROP_INCOMING_DELIVERY_POLICY} system property to decide
     * the default policy
     *
     * @return the default incoming datagram delivery policy
     * @throws ParseException If the {@link DatagramDeliveryPolicy#SYS_PROP_INCOMING_DELIVERY_POLICY}
     *                        was configured and there was a problem parsing its value
     */
    public static DatagramDeliveryPolicy defaultIncomingPolicy() throws ParseException {
        try {
            return fromSystemProperty(SYS_PROP_INCOMING_DELIVERY_POLICY);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Returns the default outgoing datagram delivery policy. This takes into account the
     * {@link DatagramDeliveryPolicy#SYS_PROP_OUTGOING_DELIVERY_POLICY} system property to decide
     * the default policy
     *
     * @return the default outgoing datagram delivery policy
     * @throws ParseException If the {@link DatagramDeliveryPolicy#SYS_PROP_OUTGOING_DELIVERY_POLICY}
     *                        was configured and there was a problem parsing its value
     */

    public static DatagramDeliveryPolicy defaultOutgoingPolicy() throws ParseException {
        try {
            return fromSystemProperty(SYS_PROP_OUTGOING_DELIVERY_POLICY);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }
}
