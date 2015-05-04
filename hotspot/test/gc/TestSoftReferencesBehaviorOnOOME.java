/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestSoftReferencesBehaviorOnOOME
 * @key gc
 * @summary Tests that all SoftReferences has been cleared at time of OOM.
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.management
 * @ignore 8073669
 * @build TestSoftReferencesBehaviorOnOOME
 * @run main/othervm -Xmx128m TestSoftReferencesBehaviorOnOOME 512 2k
 * @run main/othervm -Xmx128m TestSoftReferencesBehaviorOnOOME 128k 256k
 * @run main/othervm -Xmx128m TestSoftReferencesBehaviorOnOOME 2k 32k 10
 */
import jdk.test.lib.Utils;
import java.lang.ref.SoftReference;
import java.util.LinkedList;
import java.util.Random;

public class TestSoftReferencesBehaviorOnOOME {

    private static final Random rndGenerator = Utils.getRandomInstance();

    public static void main(String[] args) {
        int semiRefAllocFrequency = DEFAULT_FREQUENCY;
        long minSize = DEFAULT_MIN_SIZE,
                maxSize = DEFAULT_MAX_SIZE;

        if ( args.length >= 3 ) {
            semiRefAllocFrequency = Integer.parseInt(args[2]);
        }

        if ( args.length >= 2) {
            maxSize = getBytesCount(args[1]);
        }

        if ( args.length >= 1) {
            minSize = getBytesCount(args[0]);
        }

        new TestSoftReferencesBehaviorOnOOME().softReferencesOom(minSize, maxSize, semiRefAllocFrequency);
    }

    /**
     * Test that all SoftReferences has been cleared at time of OOM.
     */
    void softReferencesOom(long minSize, long maxSize, int semiRefAllocFrequency) {
        System.out.format( "minSize = %d, maxSize = %d, freq = %d%n", minSize, maxSize, semiRefAllocFrequency );
        long counter = 0;

        long multiplier = maxSize - minSize;
        LinkedList<SoftReference> arrSoftRefs = new LinkedList();
        LinkedList arrObjects = new LinkedList();
        long numberOfNotNulledObjects = 0;
        long oomSoftArraySize = 0;

        try {
            while (true) {
                // Keep every Xth object to make sure we hit OOM pretty fast
                if (counter % semiRefAllocFrequency != 0) {
                    long allocationSize = ((int) (rndGenerator.nextDouble() * multiplier))
                            + minSize;
                    arrObjects.add(new byte[(int)allocationSize]);
                } else {
                    arrSoftRefs.add(new SoftReference(new Object()));
                }

                counter++;
                if (counter == Long.MAX_VALUE) {
                    counter = 0;
                }
            }
        } catch (OutOfMemoryError oome) {
            // Clear allocated ballast, so we don't get another OOM.

            arrObjects = null;

            // Get the number of soft refs first, so we don't trigger
            // another OOM.
            oomSoftArraySize = arrSoftRefs.size();

            for (SoftReference sr : arrSoftRefs) {
                Object o = sr.get();

                if (o != null) {
                    numberOfNotNulledObjects++;
                }
            }

            // Make sure we clear all refs before we return failure
            arrSoftRefs = null;

            if (numberOfNotNulledObjects > 0) {
                throw new RuntimeException(numberOfNotNulledObjects + " out of "
                        + oomSoftArraySize + " SoftReferences was not "
                        + "null at time of OutOfMemoryError");
            }
        } finally {
            arrSoftRefs = null;
            arrObjects = null;
        }
    }

    private static final long getBytesCount(String arg) {
        String postfixes = "kMGT";
        long mod = 1;

        if (arg.trim().length() >= 2) {
            mod = postfixes.indexOf(
                    arg.trim().charAt(arg.length() - 1)
            );

            if (mod != -1) {
                mod = (long) Math.pow(1024, mod+1);
                arg = arg.substring(0, arg.length() - 1);
            } else {
                mod = 1; // 10^0
            }
        }

        return Long.parseLong(arg) * mod;
    }

    private static final long DEFAULT_MIN_SIZE = 512;
    private static final long DEFAULT_MAX_SIZE = 1024;
    private static final int DEFAULT_FREQUENCY = 4;
}
