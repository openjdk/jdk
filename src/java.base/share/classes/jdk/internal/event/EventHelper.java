/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.event;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A helper class to have events logged to a JDK Event Logger.
 */

public final class EventHelper {

    private static final System.Logger.Level LOG_LEVEL = System.Logger.Level.DEBUG;

    // helper class used for logging security related events for now
    private static final String SECURITY_LOGGER_NAME = "jdk.event.security";
    private static final System.Logger SECURITY_LOGGER =
            System.getLogger(SECURITY_LOGGER_NAME);
    private static final boolean LOGGING_SECURITY =
            SECURITY_LOGGER.isLoggable(LOG_LEVEL);

    public static void logTLSHandshakeEvent(Instant start,
                                            String peerHost,
                                            int peerPort,
                                            String cipherSuite,
                                            String protocolVersion,
                                            long peerCertId) {
        String prepend = getDurationString(start);
        SECURITY_LOGGER.log(LOG_LEVEL, prepend +
        " TLSHandshake: {0}:{1,number,#}, {2}, {3}, {4,number,#}",
        peerHost, peerPort, protocolVersion, cipherSuite, peerCertId);
    }

    public static void logSecurityPropertyEvent(String key,
                                                String value) {

        if (isLoggingSecurity()) {
            SECURITY_LOGGER.log(LOG_LEVEL,
                "SecurityPropertyModification: key:{0}, value:{1}", key, value);
        }
    }

    public static void logX509ValidationEvent(int anchorCertId,
                                         int[] certIds) {
        String codes = IntStream.of(certIds)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(", "));
        SECURITY_LOGGER.log(LOG_LEVEL,
                "ValidationChain: {0,number,#}, {1}", anchorCertId, codes);
    }

    public static void logX509CertificateEvent(String algId,
                                               String serialNum,
                                               String subject,
                                               String issuer,
                                               String keyType,
                                               int length,
                                               long certId,
                                               long beginDate,
                                               long endDate) {
        SECURITY_LOGGER.log(LOG_LEVEL, "X509Certificate: Alg:{0}, Serial:{1}" +
            ", Subject:{2}, Issuer:{3}, Key type:{4}, Length:{5,number,#}" +
            ", Cert Id:{6,number,#}, Valid from:{7}, Valid until:{8}",
            algId, serialNum, subject, issuer, keyType, length,
            certId, new Date(beginDate), new Date(endDate));
    }

    /**
     * Method to calculate a duration timestamp for events which measure
     * the start and end times of certain operations.
     * @param start Instant indicating when event started recording
     * @return A string representing duraction from start time to
     * time of this method call. Empty string is start is null.
     */
    private static String getDurationString(Instant start) {
        if (start != null) {
            if (start.equals(Instant.MIN)) {
                return "N/A";
            }
            Duration duration = Duration.between(start, Instant.now());
            long micros = duration.toNanos() / 1_000;
            if (micros < 1_000_000) {
                return "duration = " + (micros / 1_000.0) + " ms:";
            } else {
                return "duration = " + ((micros / 1_000) / 1_000.0) + " s:";
            }
        } else {
            return "";
        }
    }

    /**
     * Helper to determine if security events are being logged
     * at a preconfigured logging level. The configuration value
     * is read once at class initialization.
     *
     * @return boolean indicating whether an event should be logged
     */
    public static boolean isLoggingSecurity() {
        return LOGGING_SECURITY;
    }
}
