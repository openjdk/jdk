/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

public class AltSvcFrame extends Http2Frame {

    public static final int TYPE = 0xa;


    private int originLength;
    private Optional<String> origin;
    private String altSvcValue;

    private static final Charset encoding = StandardCharsets.US_ASCII;


    // TODO: if the encoding is really US_ASCII then a string that contains
    // characters outside of the ASCII range (outside of [0-127]) is illegal;
    // should this be checked?
    public AltSvcFrame(int streamid, int flags, int oriLength, Optional<String> originVal, String altValue) {
        this(streamid, flags);
        this.originLength = oriLength;
        this.origin = originVal;
        this.altSvcValue = Objects.requireNonNull(altValue);
    }

    public AltSvcFrame(int streamid, int flags) {
        super(streamid, flags);
    }

    @Override
    public int type() {
        return TYPE;
    }

    @Override
    int length() {
        int originLen = origin.map(s-> s.getBytes(encoding).length).orElse(0);
        return 2 + originLen + altSvcValue.getBytes(encoding).length;
    }

    public int getOriginLength() {
        return originLength;
    }

    public Optional<String> getOrigin() {
        return origin;
    }

    public String getAltSvcValue() {
        return altSvcValue;
    }

}
