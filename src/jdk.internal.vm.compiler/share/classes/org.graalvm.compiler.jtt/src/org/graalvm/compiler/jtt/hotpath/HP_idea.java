/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
// Checkstyle: stop



package org.graalvm.compiler.jtt.hotpath;

import java.util.Random;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

public class HP_idea extends JTTTest {

    public boolean test() {
        buildTestData();
        Do();
        return verify();
    }

    // Declare class data. Byte buffer plain1 holds the original
    // data for encryption, crypt1 holds the encrypted data, and
    // plain2 holds the decrypted data, which should match plain1
    // byte for byte.

    int array_rows;

    byte[] plain1; // Buffer for plaintext data.
    byte[] crypt1; // Buffer for encrypted data.
    byte[] plain2; // Buffer for decrypted data.

    short[] userkey; // Key for encryption/decryption.
    int[] Z; // Encryption subkey (userkey derived).
    int[] DK; // Decryption subkey (userkey derived).

    void Do() {
        cipher_idea(plain1, crypt1, Z); // Encrypt plain1.
        cipher_idea(crypt1, plain2, DK); // Decrypt.
    }

    /*
     * buildTestData
     *
     * Builds the data used for the test -- each time the test is run.
     */

    void buildTestData() {
        // Create three byte arrays that will be used (and reused) for
        // encryption/decryption operations.

        plain1 = new byte[array_rows];
        crypt1 = new byte[array_rows];
        plain2 = new byte[array_rows];

        Random rndnum = new Random(136506717L); // Create random number generator.

        // Allocate three arrays to hold keys: userkey is the 128-bit key.
        // Z is the set of 16-bit encryption subkeys derived from userkey,
        // while DK is the set of 16-bit decryption subkeys also derived
        // from userkey. NOTE: The 16-bit values are stored here in
        // 32-bit int arrays so that the values may be used in calculations
        // as if they are unsigned. Each 64-bit block of plaintext goes
        // through eight processing rounds involving six of the subkeys
        // then a final output transform with four of the keys; (8 * 6)
        // + 4 = 52 subkeys.

        userkey = new short[8]; // User key has 8 16-bit shorts.
        Z = new int[52]; // Encryption subkey (user key derived).
        DK = new int[52]; // Decryption subkey (user key derived).

        // Generate user key randomly; eight 16-bit values in an array.

        for (int i = 0; i < 8; i++) {
            // Again, the random number function returns int. Converting
            // to a short type preserves the bit pattern in the lower 16
            // bits of the int and discards the rest.

            userkey[i] = (short) rndnum.nextInt();
        }

        // Compute encryption and decryption subkeys.

        calcEncryptKey();
        calcDecryptKey();

        // Fill plain1 with "text."
        for (int i = 0; i < array_rows; i++) {
            plain1[i] = (byte) i;

            // Converting to a byte
            // type preserves the bit pattern in the lower 8 bits of the
            // int and discards the rest.
        }
    }

    /*
     * calcEncryptKey
     *
     * Builds the 52 16-bit encryption subkeys Z[] from the user key and stores in 32-bit int array.
     * The routing corrects an error in the source code in the Schnier book. Basically, the sense of
     * the 7- and 9-bit shifts are reversed. It still works reversed, but would encrypted code would
     * not decrypt with someone else's IDEA code.
     */

    private void calcEncryptKey() {
        int j; // Utility variable.

        for (int i = 0; i < 52; i++) {
            // Zero out the 52-int Z array.
            Z[i] = 0;
        }

        for (int i = 0; i < 8; i++) // First 8 subkeys are userkey itself.
        {
            Z[i] = userkey[i] & 0xffff; // Convert "unsigned"
            // short to int.
        }

        // Each set of 8 subkeys thereafter is derived from left rotating
        // the whole 128-bit key 25 bits to left (once between each set of
        // eight keys and then before the last four). Instead of actually
        // rotating the whole key, this routine just grabs the 16 bits
        // that are 25 bits to the right of the corresponding subkey
        // eight positions below the current subkey. That 16-bit extent
        // straddles two array members, so bits are shifted left in one
        // member and right (with zero fill) in the other. For the last
        // two subkeys in any group of eight, those 16 bits start to
        // wrap around to the first two members of the previous eight.

        for (int i = 8; i < 52; i++) {
            j = i % 8;
            if (j < 6) {
                Z[i] = ((Z[i - 7] >>> 9) | (Z[i - 6] << 7)) // Shift and combine.
                                & 0xFFFF; // Just 16 bits.
                continue; // Next iteration.
            }

            if (j == 6) // Wrap to beginning for second chunk.
            {
                Z[i] = ((Z[i - 7] >>> 9) | (Z[i - 14] << 7)) & 0xFFFF;
                continue;
            }

            // j == 7 so wrap to beginning for both chunks.

            Z[i] = ((Z[i - 15] >>> 9) | (Z[i - 14] << 7)) & 0xFFFF;
        }
    }

    /*
     * calcDecryptKey
     *
     * Builds the 52 16-bit encryption subkeys DK[] from the encryption- subkeys Z[]. DK[] is a
     * 32-bit int array holding 16-bit values as unsigned.
     */

    private void calcDecryptKey() {
        int j, k; // Index counters.
        int t1, t2, t3; // Temps to hold decrypt subkeys.

        t1 = inv(Z[0]); // Multiplicative inverse (mod x10001).
        t2 = -Z[1] & 0xffff; // Additive inverse, 2nd encrypt subkey.
        t3 = -Z[2] & 0xffff; // Additive inverse, 3rd encrypt subkey.

        DK[51] = inv(Z[3]); // Multiplicative inverse (mod x10001).
        DK[50] = t3;
        DK[49] = t2;
        DK[48] = t1;

        j = 47; // Indices into temp and encrypt arrays.
        k = 4;
        for (int i = 0; i < 7; i++) {
            t1 = Z[k++];
            DK[j--] = Z[k++];
            DK[j--] = t1;
            t1 = inv(Z[k++]);
            t2 = -Z[k++] & 0xffff;
            t3 = -Z[k++] & 0xffff;
            DK[j--] = inv(Z[k++]);
            DK[j--] = t2;
            DK[j--] = t3;
            DK[j--] = t1;
        }

        t1 = Z[k++];
        DK[j--] = Z[k++];
        DK[j--] = t1;
        t1 = inv(Z[k++]);
        t2 = -Z[k++] & 0xffff;
        t3 = -Z[k++] & 0xffff;
        DK[j--] = inv(Z[k++]);
        DK[j--] = t3;
        DK[j--] = t2;
        DK[j--] = t1;
    }

    /*
     * cipher_idea
     *
     * IDEA encryption/decryption algorithm. It processes plaintext in 64-bit blocks, one at a time,
     * breaking the block into four 16-bit unsigned subblocks. It goes through eight rounds of
     * processing using 6 new subkeys each time, plus four for last step. The source text is in
     * array text1, the destination text goes into array text2 The routine represents 16-bit
     * subblocks and subkeys as type int so that they can be treated more easily as unsigned.
     * Multiplication modulo 0x10001 interprets a zero sub-block as 0x10000; it must to fit in 16
     * bits.
     */

    @SuppressWarnings("static-method")
    private void cipher_idea(byte[] text1, byte[] text2, int[] key) {

        int i1 = 0; // Index into first text array.
        int i2 = 0; // Index into second text array.
        int ik; // Index into key array.
        int x1, x2, x3, x4, t1, t2; // Four "16-bit" blocks, two temps.
        int r; // Eight rounds of processing.

        for (int i = 0; i < text1.length; i += 8) {

            ik = 0; // Restart key index.
            r = 8; // Eight rounds of processing.

            // Load eight plain1 bytes as four 16-bit "unsigned" integers.
            // Masking with 0xff prevents sign extension with cast to int.

            x1 = text1[i1++] & 0xff; // Build 16-bit x1 from 2 bytes,
            x1 |= (text1[i1++] & 0xff) << 8; // assuming low-order byte first.
            x2 = text1[i1++] & 0xff;
            x2 |= (text1[i1++] & 0xff) << 8;
            x3 = text1[i1++] & 0xff;
            x3 |= (text1[i1++] & 0xff) << 8;
            x4 = text1[i1++] & 0xff;
            x4 |= (text1[i1++] & 0xff) << 8;

            do {
                // 1) Multiply (modulo 0x10001), 1st text sub-block
                // with 1st key sub-block.

                x1 = (int) ((long) x1 * key[ik++] % 0x10001L & 0xffff);

                // 2) Add (modulo 0x10000), 2nd text sub-block
                // with 2nd key sub-block.

                x2 = x2 + key[ik++] & 0xffff;

                // 3) Add (modulo 0x10000), 3rd text sub-block
                // with 3rd key sub-block.

                x3 = x3 + key[ik++] & 0xffff;

                // 4) Multiply (modulo 0x10001), 4th text sub-block
                // with 4th key sub-block.

                x4 = (int) ((long) x4 * key[ik++] % 0x10001L & 0xffff);

                // 5) XOR results from steps 1 and 3.

                t2 = x1 ^ x3;

                // 6) XOR results from steps 2 and 4.
                // Included in step 8.

                // 7) Multiply (modulo 0x10001), result of step 5
                // with 5th key sub-block.

                t2 = (int) ((long) t2 * key[ik++] % 0x10001L & 0xffff);

                // 8) Add (modulo 0x10000), results of steps 6 and 7.

                t1 = t2 + (x2 ^ x4) & 0xffff;

                // 9) Multiply (modulo 0x10001), result of step 8
                // with 6th key sub-block.

                t1 = (int) ((long) t1 * key[ik++] % 0x10001L & 0xffff);

                // 10) Add (modulo 0x10000), results of steps 7 and 9.

                t2 = t1 + t2 & 0xffff;

                // 11) XOR results from steps 1 and 9.

                x1 ^= t1;

                // 14) XOR results from steps 4 and 10. (Out of order).

                x4 ^= t2;

                // 13) XOR results from steps 2 and 10. (Out of order).

                t2 ^= x2;

                // 12) XOR results from steps 3 and 9. (Out of order).

                x2 = x3 ^ t1;

                x3 = t2; // Results of x2 and x3 now swapped.

            } while (--r != 0); // Repeats seven more rounds.

            // Final output transform (4 steps).

            // 1) Multiply (modulo 0x10001), 1st text-block
            // with 1st key sub-block.

            x1 = (int) ((long) x1 * key[ik++] % 0x10001L & 0xffff);

            // 2) Add (modulo 0x10000), 2nd text sub-block
            // with 2nd key sub-block. It says x3, but that is to undo swap
            // of subblocks 2 and 3 in 8th processing round.

            x3 = x3 + key[ik++] & 0xffff;

            // 3) Add (modulo 0x10000), 3rd text sub-block
            // with 3rd key sub-block. It says x2, but that is to undo swap
            // of subblocks 2 and 3 in 8th processing round.

            x2 = x2 + key[ik++] & 0xffff;

            // 4) Multiply (modulo 0x10001), 4th text-block
            // with 4th key sub-block.

            x4 = (int) ((long) x4 * key[ik++] % 0x10001L & 0xffff);

            // Repackage from 16-bit sub-blocks to 8-bit byte array text2.

            text2[i2++] = (byte) x1;
            text2[i2++] = (byte) (x1 >>> 8);
            text2[i2++] = (byte) x3; // x3 and x2 are switched
            text2[i2++] = (byte) (x3 >>> 8); // only in name.
            text2[i2++] = (byte) x2;
            text2[i2++] = (byte) (x2 >>> 8);
            text2[i2++] = (byte) x4;
            text2[i2++] = (byte) (x4 >>> 8);

        } // End for loop.

    } // End routine.

    /*
     * mul
     *
     * Performs multiplication, modulo (2**16)+1. This code is structured on the assumption that
     * untaken branches are cheaper than taken branches, and that the compiler doesn't schedule
     * branches. Java: Must work with 32-bit int and one 64-bit long to keep 16-bit values and their
     * products "unsigned." The routine assumes that both a and b could fit in 16 bits even though
     * they come in as 32-bit ints. Lots of "& 0xFFFF" masks here to keep things 16-bit. Also,
     * because the routine stores mod (2**16)+1 results in a 2**16 space, the result is truncated to
     * zero whenever the result would zero, be 2**16. And if one of the multiplicands is 0, the
     * result is not zero, but (2**16) + 1 minus the other multiplicand (sort of an additive inverse
     * mod 0x10001).
     *
     * NOTE: The java conversion of this routine works correctly, but is half the speed of using
     * Java's modulus division function (%) on the multiplication with a 16-bit masking of the
     * result--running in the Symantec Caje IDE. So it's not called for now; the test uses Java %
     * instead.
     */

    /*
     * private int mul(int a, int b) throws ArithmeticException { long p; // Large enough to catch
     * 16-bit multiply // without hitting sign bit. if (a != 0) { if (b != 0) { p = (long) a * b; b
     * = (int) p & 0xFFFF; // Lower 16 bits. a = (int) p >>> 16; // Upper 16 bits.
     *
     * return (b - a + (b < a ? 1 : 0) & 0xFFFF); } else return ((1 - a) & 0xFFFF); // If b = 0,
     * then same as // 0x10001 - a. } else // If a = 0, then return return((1 - b) & 0xFFFF); //
     * same as 0x10001 - b. }
     */

    /*
     * inv
     *
     * Compute multiplicative inverse of x, modulo (2**16)+1 using extended Euclid's GCD (greatest
     * common divisor) algorithm. It is unrolled twice to avoid swapping the meaning of the
     * registers. And some subtracts are changed to adds. Java: Though it uses signed 32-bit ints,
     * the interpretation of the bits within is strictly unsigned 16-bit.
     */

    public int inv(int x) {
        int x2 = x;
        int t0, t1;
        int q, y;

        if (x2 <= 1) {
            return (x2); // 0 and 1 are self-inverse.
        }

        t1 = 0x10001 / x2; // (2**16+1)/x; x is >= 2, so fits 16 bits.
        y = 0x10001 % x2;
        if (y == 1) {
            return ((1 - t1) & 0xFFFF);
        }

        t0 = 1;
        do {
            q = x2 / y;
            x2 = x2 % y;
            t0 += q * t1;
            if (x2 == 1) {
                return (t0);
            }
            q = y / x2;
            y = y % x2;
            t1 += q * t0;
        } while (y != 1);

        return ((1 - t1) & 0xFFFF);
    }

    boolean verify() {
        boolean error;
        for (int i = 0; i < array_rows; i++) {
            error = (plain1[i] != plain2[i]);
            if (error) {
                return false;
            }
        }
        return true;
    }

    /*
     * freeTestData
     *
     * Nulls arrays and forces garbage collection to free up memory.
     */

    void freeTestData() {
        plain1 = null;
        crypt1 = null;
        plain2 = null;
        userkey = null;
        Z = null;
        DK = null;
    }

    public HP_idea() {
        array_rows = 3000;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }

    @Test
    public void runInv() {
        runTest("inv", 724);
    }
}
