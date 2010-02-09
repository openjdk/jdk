/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6578538
 * @summary com.sun.crypto.provider.SunJCE instance leak using KRB5 and
 *     LoginContext
 * @author Brad Wetmore
 *
 * @run main/othervm -Xmx2m -XX:OldSize=1m -XX:NewSize=512k TestProviderLeak
 *
 * The original test invocation is below, but had to use the above
 * workaround for bug 6923123.
 *
 * run main/othervm -Xmx2m TestProviderLeak
 */

/*
 * We force the leak to become a problem by specifying the minimum
 * size heap we can (above).  In current runs on a server and client
 * machine, it took roughly 220-240 iterations to have the memory leak
 * shut down other operations.  It complained about "Unable to verify
 * the SunJCE provider."
 */

import javax.crypto.*;
import javax.crypto.spec.*;

public class TestProviderLeak {
    private static void dumpMemoryStats(String s) throws Exception {
        Runtime rt = Runtime.getRuntime();
        System.out.println(s + ":\t" +
            rt.freeMemory() + " bytes free");
    }

    public static void main(String [] args) throws Exception {
        SecretKeyFactory skf =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1", "SunJCE");
        PBEKeySpec pbeKS = new PBEKeySpec(
            "passPhrase".toCharArray(), new byte [] { 0 }, 5, 512);
        for (int i = 0; i <= 1000; i++) {
            try {
                skf.generateSecret(pbeKS);
                if ((i % 20) == 0) {
                     // Calling gc() isn't dependable, but doesn't hurt.
                     // Gives better output in leak cases.
                    System.gc();
                    dumpMemoryStats("Iteration " + i);
                }
            } catch (Exception e) {
                dumpMemoryStats("\nException seen at iteration " + i);
                throw e;
            }
        }
    }
}
