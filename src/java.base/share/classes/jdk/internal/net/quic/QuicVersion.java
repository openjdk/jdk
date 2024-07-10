/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.quic;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the Quic versions defined in their corresponding RFCs
 */
public enum QuicVersion {
    // the version numbers are defined in their respective RFCs
    QUIC_V1(1, 1), // RFC-9000
    QUIC_V2(0x6b3343cf, 2); // RFC 9369

    private final int versionNumber;
    private final int inception;
    private QuicVersion(final int versionNumber, final int inception) {
        this.versionNumber = versionNumber;
        this.inception = inception;
    }

    /**
     * {@return the version number}
     */
    public int versionNumber() {
        return this.versionNumber;
    }

    /**
     * @param versionNumber The version number
     * {@return the QuicVersion corresponding to the {@code versionNumber} or
     * {@link Optional#empty() an empty Optional} if the {@code versionNumber}
     * doesn't correspond to a Quic version}
     */
    public static Optional<QuicVersion> of(int versionNumber) {
        for (QuicVersion qv : QuicVersion.values()) {
            if (qv.versionNumber == versionNumber) {
                return Optional.of(qv);
            }
        }
        return Optional.empty();
    }

    private int inception() {
        return this.inception;
    }

    public static QuicVersion lowestOf(final Collection<QuicVersion> quicVersions) {
        Objects.requireNonNull(quicVersions);
        if (quicVersions.isEmpty()) {
            throw new IllegalArgumentException("Empty quic versions");
        }
        final QuicVersion least;
        if (quicVersions.size() == 1) {
            least = quicVersions.iterator().next();
        } else {
            least = quicVersions.stream().filter(Objects::nonNull)
                    .min(Comparator.comparingInt(QuicVersion::inception)).orElse(null);
        }
        if (least == null) {
            // this means all items in that collection were null
            throw new IllegalArgumentException("null quic versions");
        }
        return least;
    }
}
