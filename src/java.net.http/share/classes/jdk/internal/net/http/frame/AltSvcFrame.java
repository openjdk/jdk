/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http.frame;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

public final class AltSvcFrame extends Http2Frame {

    public static final int TYPE = 0xa;

    private final int length;
    private final String origin;
    private final String altSvcValue;

    private static final Charset encoding = StandardCharsets.US_ASCII;

    // Strings should be US-ASCII. This is checked by the FrameDecoder.
    public AltSvcFrame(int streamid, int flags, Optional<String> originVal, String altValue) {
        super(streamid, flags);
        this.origin = originVal.orElse("");
        this.altSvcValue = Objects.requireNonNull(altValue);
        this.length = 2 + origin.length() + altValue.length();
        assert origin.length() == origin.getBytes(encoding).length;
        assert altSvcValue.length() == altSvcValue.getBytes(encoding).length;
    }

    @Override
    public int type() {
        return TYPE;
    }

    @Override
    int length() {
        return length;
    }

    public String getOrigin() {
        return origin;
    }

    public String getAltSvcValue() {
        return altSvcValue;
    }

    @Override
    public String toString() {
        return super.toString()
                + ", origin=" + this.origin
                + ", alt-svc: " + altSvcValue;
    }
}
