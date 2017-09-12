/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.provider.AbstractDrbg;
import sun.security.provider.EntropySource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.DrbgParameters;
import java.security.SecureRandom;
import java.security.Security;

/**
 * @test
 * @bug 8051408
 * @modules java.base/java.lang.reflect:open
 *          java.base/sun.security.provider:+open
 * @run main/othervm CommonSeeder
 * @summary check entropy reading of DRBGs
 */
public class CommonSeeder {

    static class MyES implements EntropySource {
        int count = 100;
        int lastCount = 100;

        @Override
        public byte[] getEntropy(int minEntropy, int minLength,
                                 int maxLength, boolean pr) {
            count--;
            return new byte[minLength];
        }

        /**
         * Confirms genEntropy() has been called {@code less} times
         * since last check.
         */
        public void checkUsage(int less) throws Exception {
            if (lastCount != count + less) {
                throw new Exception(String.format(
                        "lastCount = %d, count = %d, less = %d",
                        lastCount, count, less));
            }
            lastCount = count;
        }
    }

    public static void main(String[] args) throws Exception {

        byte[] result = new byte[10];
        MyES es = new MyES();

        // Set es as the default entropy source, overriding SeedGenerator.
        setDefaultSeeder(es);

        // Nothing happened yet
        es.checkUsage(0);

        SecureRandom sr;
        sr = SecureRandom.getInstance("DRBG");

        // No entropy reading if only getInstance
        es.checkUsage(0);

        // Entropy is read at 1st nextBytes of the 1st DRBG
        sr.nextInt();
        es.checkUsage(1);

        for (String mech : new String[]{"Hash_DRBG", "HMAC_DRBG", "CTR_DRBG"}) {
            System.out.println("Testing " + mech + "...");

            // DRBG with pr_false will never read entropy again no matter
            // if nextBytes or reseed is called.

            Security.setProperty("securerandom.drbg.config", mech);
            sr = SecureRandom.getInstance("DRBG");
            sr.nextInt();
            sr.reseed();
            es.checkUsage(0);

            // DRBG with pr_true always read from default entropy, and
            // its nextBytes always reseed itself

            Security.setProperty("securerandom.drbg.config",
                    mech + ",pr_and_reseed");
            sr = SecureRandom.getInstance("DRBG");

            sr.nextInt();
            es.checkUsage(2); // one instantiate, one reseed
            sr.nextInt();
            es.checkUsage(1); // one reseed in nextBytes
            sr.reseed();
            es.checkUsage(1); // one reseed
            sr.nextBytes(result, DrbgParameters.nextBytes(-1, false, null));
            es.checkUsage(0); // pr_false for this call
            sr.nextBytes(result, DrbgParameters.nextBytes(-1, true, null));
            es.checkUsage(1); // pr_true for this call
            sr.reseed(DrbgParameters.reseed(true, null));
            es.checkUsage(1); // reseed from es
            sr.reseed(DrbgParameters.reseed(false, null));
            es.checkUsage(0); // reseed from AbstractDrbg.SeederHolder.seeder
        }
    }

    static void setDefaultSeeder(EntropySource es) throws Exception {
        Field f = AbstractDrbg.class.getDeclaredField("defaultES");
        f.setAccessible(true);  // no more private
        Field f2 = Field.class.getDeclaredField("modifiers");
        f2.setAccessible(true);
        f2.setInt(f, f2.getInt(f) - Modifier.FINAL);    // no more final
        f.set(null, es);
    }
}
