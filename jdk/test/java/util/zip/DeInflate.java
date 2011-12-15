/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 7110149
 * @summary Test basic deflater & inflater functionality
 */

import java.io.*;
import java.util.*;
import java.util.zip.*;

public class DeInflate {

    static void check(Deflater compresser, byte[] in, int len,
                      byte[] out1, byte[] out2, boolean nowrap)
        throws Throwable
    {
        Arrays.fill(out1, (byte)0);
        Arrays.fill(out2, (byte)0);

        compresser.setInput(in, 0, len);
        compresser.finish();
        int m = compresser.deflate(out1);

        Inflater decompresser = new Inflater(nowrap);
        decompresser.setInput(out1, 0, m);
        int n = decompresser.inflate(out2);

        if (n != len ||
            !Arrays.equals(Arrays.copyOf(in, len), Arrays.copyOf(out2, len)) ||
            decompresser.inflate(out2) != 0) {
            System.out.printf("m=%d, n=%d, len=%d, eq=%b%n",
                              m, n, len, Arrays.equals(in, out2));
            throw new RuntimeException("De/inflater failed:" + compresser);
        }
    }

    public static void main(String[] args) throws Throwable {
        byte[] dataIn = new byte[1024 * 512];
        new Random().nextBytes(dataIn);
        byte[] dataOut1 = new byte[dataIn.length + 1024];
        byte[] dataOut2 = new byte[dataIn.length];
        boolean wrap[] = new boolean[] { false, true };

        for (int level = Deflater.DEFAULT_COMPRESSION;
                 level <= Deflater.BEST_COMPRESSION; level++) {
            System.out.print("level=" + level + ", strategy= ");
            for (int strategy = Deflater.DEFAULT_STRATEGY;
                     strategy <= Deflater.HUFFMAN_ONLY; strategy++) {
                System.out.print(" " + strategy + " nowrap[");
                for (int dowrap = 0; dowrap <= 1; dowrap++) {
                    System.out.print(" " + wrap[dowrap]);
                    for (int i = 0; i < 5; i++) {
                        Deflater def = new Deflater(level, wrap[dowrap]);
                        if (strategy != Deflater.DEFAULT_STRATEGY) {
                            def.setStrategy(strategy);
                            // The first invocation after setLevel/Strategy()
                            // with a different level/stragety returns 0, if
                            // there is no need to flush out anything for the
                            // previous setting/"data", this is tricky and
                            // appears un-documented.
                            def.deflate(dataOut2);
                        }
                        int len = (i == 0)? dataIn.length
                                          : new Random().nextInt(dataIn.length);
                            check(def, dataIn, len, dataOut1, dataOut2, wrap[dowrap]);
                    }
                }
                System.out.print("] ");
            }
            System.out.println();
        }
    }
}
