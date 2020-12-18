/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.security;

import org.openjdk.jmh.annotations.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * Tests different ways to get instances of MD5 {@link MessageDigest}.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3)
public class Md5MessageDigestLookup {

    @Benchmark
    public MessageDigest messageDigestGetInstance() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException nsae) {
            throw new InternalError("MD5 not supported", nsae);
        }
    }

    @Benchmark
    public MessageDigest messageDigestGetInstanceNoTryCatch() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("MD5");
    }

    @Benchmark
    public MessageDigest md5DigestOrThrow() {
        return Md5Digest.orThrow();
    }

    private static final class Md5Digest {
        private static final ThreadLocal<MessageDigest> MD5_DIGEST = ThreadLocal.withInitial(() -> {
            try {
                return MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                return null;
            }
        });

        static MessageDigest orThrow() {
            final MessageDigest digest;
            if ((digest = MD5_DIGEST.get()) == null) throw new InternalError("MD5 not supported");

            return digest;
        }
    }
}
