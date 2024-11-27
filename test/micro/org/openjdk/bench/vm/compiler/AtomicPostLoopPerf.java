/*
 * Copyright (c) 2024, Arm Limited. All rights reserved.
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

package org.openjdk.bench.vm.compiler;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(value = 3)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@CompilerControl(CompilerControl.Mode.DONT_INLINE)

public class AtomicPostLoopPerf {
    @Param({ "1",  "2",  "3",  "4",  "5",  "6",  "7",  "8",  "9",
             "10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
             "20", "21", "22", "23", "24", "25", "26", "27", "28", "29",
             "30", "31", "32", "33", "34", "35", "36", "37", "38", "39",
             "40", "41", "42", "43", "44", "45", "46", "47", "48", "49",
             "50", "51", "52", "53", "54", "55", "56", "57", "58", "59",
             "60", "61", "62", "63", "64", "65", "66", "67", "68", "69",
             "70", "71", "72", "73", "74", "75", "76", "77", "78", "79",
             "80", "81", "82", "83", "84", "85", "86", "87", "88", "89",
             "90", "91", "92", "93", "94", "95", "96", "97", "98", "99",
             "100","101","102","103","104","105","106","107","108","109",
             "110","111","112","113","114","115","116","117","118","119",
             "120","121","122","123","124","125","126","127","128","129",
             "130","131","132","133","134","135","136","137","138","139",
             "140","141","142","143","144","145","146","147","148","149",
             "150","151","152","153","154","155","156","157","158","159",
             "160","161","162","163","164","165","166","167","168","169",
             "170","171","172","173","174","175","176","177","178","179",
             "180","181","182","183","184","185","186","187","188","189",
             "190","191","192","193","194","195","196","197","198","199",
             "200","201","202","203","204","205","206","207","208","209",
             "210","211","212","213","214","215","216","217","218","219",
             "220","221","222","223","224","225","226","227","228","229",
             "230","231","232","233","234","235","236","237","238","239",
             "240","241","242","243","244","245","246","247","248","249",
             "250"})
    public int length;

    private byte[] ba;
    private byte[] bb;
    private byte[] bc;
    private short[] sa;
    private short[] sb;
    private short[] sc;
    private int[] ia;
    private int[] ib;
    private int[] ic;
    private long[] la;
    private long[] lb;
    private long[] lc;
    private byte[] lba;
    private byte[] lbb;
    private byte[] lbc;
    private short[] lsa;
    private short[] lsb;
    private short[] lsc;
    private int[] lia;
    private int[] lib;
    private int[] lic;
    private long[] lla;
    private long[] llb;
    private long[] llc;

    private Random random = new Random(0);
    private int SIZE = 2048;
    private int warm_up = 100_000;

    /**
     * Initialize. New array objects and set initial values.
     */
    @Setup(Level.Trial)
    public void init() {
        ba = new byte[length];
        bb = new byte[length];
        bc = new byte[length];
        sa = new short[length];
        sb = new short[length];
        sc = new short[length];
        ia = new int[length];
        ib = new int[length];
        ic = new int[length];
        la = new long[length];
        lb = new long[length];
        lc = new long[length];

        for (int i = 0; i < length; i++) {
            ba[i] = (byte) random.nextInt();
            bb[i] = (byte) random.nextInt();
            sa[i] = (short) random.nextInt();
            sb[i] = (short) random.nextInt();
            ia[i] = random.nextInt();
            ib[i] = random.nextInt(length) + 1; // non zero
            la[i] = random.nextLong();
            lb[i] = random.nextLong();
        }

        lba = new byte[SIZE];
        lbb = new byte[SIZE];
        lbc = new byte[SIZE];
        lsa = new short[SIZE];
        lsb = new short[SIZE];
        lsc = new short[SIZE];
        lia = new int[SIZE];
        lib = new int[SIZE];
        lic = new int[SIZE];
        lla = new long[SIZE];
        llb = new long[SIZE];
        llc = new long[SIZE];

        for (int i = 0; i < SIZE; i++) {
            lba[i] = (byte) random.nextInt();
            lbb[i] = (byte) random.nextInt();
            lsa[i] = (short) random.nextInt();
            lsb[i] = (short) random.nextInt();
            lia[i] = random.nextInt();
            lib[i] = random.nextInt(SIZE) + 1; // non zero
            lla[i] = random.nextLong();
            llb[i] = random.nextLong();
        }

        for (int i = 0; i < warm_up; i++) {
            byteadd(lba, lbb, lbc);
            shortadd(lsa, lsb, lsc);
            intadd(lia, lib, lic);
            longadd(lla, llb, llc);
        }

    }

    public void byteadd(byte[] ba, byte[] bb, byte[] bc) {
        for (int i = 0; i < ba.length; i++) {
            bc[i] = (byte) (ba[i] + bb[i]);
        }
    }

    public void shortadd(short[] sa, short[] sb, short[] sc) {
        for (int i = 0; i < sa.length; i++) {
            sc[i] = (short) (sa[i] + sb[i]);
        }
    }

    public void intadd(int[] a, int[] b, int[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public void longadd(long[] a, long[] b, long[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    @Benchmark
    public void addB() {
        byteadd(ba, bb, bc);
    }

    @Benchmark
    public void addS() {
        shortadd(sa, sb, sc);
    }

    @Benchmark
    public void addI() {
        intadd(ia, ib, ic);
    }

    @Benchmark
    public void addL() {
        longadd(la, lb, lc);
    }

}
