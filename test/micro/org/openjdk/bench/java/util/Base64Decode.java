/*
 * Copyright (c) 2021, Huawei Technologies Co., Ltd. All rights reserved.
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

package org.openjdk.micro.bench.java.util;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Base64;
import java.util.Random;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 4, time = 2)
@Fork(value = 3)
public class Base64Decode {

    private Base64.Encoder encoder, mimeEncoder, urlEncoder;
    private Base64.Decoder decoder, mimeDecoder, urlDecoder;
    private ArrayList<byte[]> encoded, mimeEncoded, urlEncoded, errorEncoded;
    private byte[] decoded, mimeDecoded, urlDecoded, errorDecoded;

    private static final int TESTSIZE = 1000;

    @Param({"1", "3", "7", "32", "64", "80", "96",
            "112", "512", "1000", "20000", "50000"})
    private int maxNumBytes;

    @Param({"4", "32", "76", "128"})
    private int lineSize;

    private byte[] lineSeparator = {'\r', '\n'};

    /* Other value can be tested by passing parameters to the JMH
       tests: -p errorIndex=3,64,144,208,272,1000,20000. */
    @Param({"144"})
    private int errorIndex;

    @Param({"0"})
    private int addSpecial;

    @Setup
    public void setup() {
        Random r = new Random(1123);

        decoded = new byte[maxNumBytes + 1];
        encoder = Base64.getEncoder();
        decoder = Base64.getDecoder();
        encoded = new ArrayList<byte[]> ();

        mimeDecoded = new byte[maxNumBytes + 1];
        mimeEncoder = Base64.getMimeEncoder(lineSize, lineSeparator);
        mimeDecoder = Base64.getMimeDecoder();
        mimeEncoded = new ArrayList<byte[]> ();

        urlDecoded = new byte[maxNumBytes + 1];
        urlEncoder = Base64.getUrlEncoder();
        urlDecoder = Base64.getUrlDecoder();
        urlEncoded = new ArrayList<byte[]> ();

        errorDecoded = new byte[errorIndex + 100];
        errorEncoded = new ArrayList<byte[]> ();

        for (int i = 0; i < TESTSIZE; i++) {
            int srcLen = 1 + r.nextInt(maxNumBytes);
            byte[] src = new byte[srcLen];
            byte[] dst = new byte[((srcLen + 2) / 3) * 4];
            r.nextBytes(src);
            encoder.encode(src, dst);
            if(addSpecial != 0){
              dst[0] = '/';
              dst[1] = '+';
            }
            encoded.add(dst);

            int mimeSrcLen = 1 + r.nextInt(maxNumBytes);
            byte[] mimeSrc = new byte[mimeSrcLen];
            byte[] mimeDst = new byte[((mimeSrcLen + 2) / 3) * 4 * (lineSize + lineSeparator.length) / lineSize];
            r.nextBytes(mimeSrc);
            mimeEncoder.encode(mimeSrc, mimeDst);
            mimeEncoded.add(mimeDst);

            int urlSrcLen = 1 + r.nextInt(maxNumBytes);
            byte[] urlSrc = new byte[urlSrcLen];
            byte[] urlDst = new byte[((urlSrcLen + 2) / 3) * 4];
            r.nextBytes(urlSrc);
            urlEncoder.encode(urlSrc, urlDst);
            if(addSpecial != 0){
              urlDst[0] = '_';
              urlDst[1] = '-';
            }
            urlEncoded.add(urlDst);

            int errorSrcLen = errorIndex + r.nextInt(100);
            byte[] errorSrc = new byte[errorSrcLen];
            byte[] errorDst = new byte[(errorSrcLen + 2) / 3 * 4];
            r.nextBytes(errorSrc);
            encoder.encode(errorSrc, errorDst);
            errorDst[errorIndex] = (byte) '?';
            errorEncoded.add(errorDst);
        }
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testBase64Decode(Blackhole bh) {
        for (byte[] s : encoded) {
            decoder.decode(s, decoded);
            bh.consume(decoded);
        }
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testBase64MIMEDecode(Blackhole bh) {
        for (byte[] s : mimeEncoded) {
            mimeDecoder.decode(s, mimeDecoded);
            bh.consume(mimeDecoded);
        }
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testBase64URLDecode(Blackhole bh) {
        for (byte[] s : urlEncoded) {
            urlDecoder.decode(s, urlDecoded);
            bh.consume(urlDecoded);
        }
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public void testBase64WithErrorInputsDecode (Blackhole bh) {
        for (byte[] s : errorEncoded) {
            try {
                 decoder.decode(s, errorDecoded);
                 bh.consume(errorDecoded);
            } catch (IllegalArgumentException e) {
                 bh.consume(e);
            }
        }
    }
}
