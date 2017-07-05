/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @summary Test that SnmpTimeTicks wraps around when it is passed a long
 *          value
 * @bug     4955105
 * @build   TimeTicksWrapping
 * @run     main TimeTicksWrapping
 */
import com.sun.jmx.snmp.SnmpTimeticks;
import com.sun.jmx.snmp.SnmpUnsignedInt;

public class TimeTicksWrapping {
    public static final long[] oks = {
        0L, 1L, (long)Integer.MAX_VALUE, (long)Integer.MAX_VALUE*2,
        (long)Integer.MAX_VALUE*2+1L, (long)Integer.MAX_VALUE*2+2L,
        (long)Integer.MAX_VALUE*3,
        SnmpUnsignedInt.MAX_VALUE, SnmpUnsignedInt.MAX_VALUE+1L,
        SnmpUnsignedInt.MAX_VALUE*3-1L, Long.MAX_VALUE
    };

    public static final long[] kos = {
        -1L, (long)Integer.MIN_VALUE, (long)Integer.MIN_VALUE*2,
        (long)Integer.MIN_VALUE*2-1L, (long)Integer.MIN_VALUE*3,
        -SnmpUnsignedInt.MAX_VALUE, -(SnmpUnsignedInt.MAX_VALUE+1L),
        -(SnmpUnsignedInt.MAX_VALUE*3-1L), Long.MIN_VALUE
    };

    public static void main(String args[]) {
        try {
            SnmpTimeticks t;

            for (int i=0;i<oks.length;i++) {
                final long t1,t2,t3;
                t1 = (new SnmpTimeticks(oks[i])).longValue();
                t2 = (new SnmpTimeticks(new Long(oks[i]))).longValue();
                t3 = oks[i]%0x0100000000L;
                if (t1 != t3)
                    throw new Exception("Value should have wrapped: " +
                                        oks[i] + " expected: " + t3);
                if (t2 != t3)
                    throw new Exception("Value should have wrapped: " +
                                        "Long("+oks[i]+") expected: " + t3);

                if (t1 > SnmpUnsignedInt.MAX_VALUE)
                    throw new Exception("Value should have wrapped " +
                                        "for " + oks[i] + ": " +
                                        t1 + " exceeds max: " +
                                        SnmpUnsignedInt.MAX_VALUE);
                if (t2 > SnmpUnsignedInt.MAX_VALUE)
                    throw new Exception("Value should have wrapped " +
                                        "for " + oks[i] + ": " +
                                        t2 + " exceeds max: " +
                                        SnmpUnsignedInt.MAX_VALUE);

                if (t1 < 0)
                    throw new Exception("Value should have wrapped: " +
                                        "for " + oks[i] + ": " +
                                        t1 + " is negative");
                if (t2 < 0)
                    throw new Exception("Value should have wrapped: " +
                                        "for " + oks[i] + ": " +
                                        t2 + " is negative");

                System.out.println("TimeTicks[" + oks[i] +
                                   "] rightfully accepted: " + t3);
            }

            for (int i=0;i<kos.length;i++) {
                try {
                    t = new SnmpTimeticks(kos[i]);
                    throw new Exception("Value should have been rejected: " +
                                        kos[i]);
                } catch (IllegalArgumentException x) {
                    // OK!
                }
                try {
                    t = new SnmpTimeticks(new Long(kos[i]));
                    throw new Exception("Value should have been rejected: " +
                                        "Long("+kos[i]+")");
                } catch (IllegalArgumentException x) {
                    // OK!
                }

                System.out.println("TimeTicks[" + kos[i] +
                                   "] rightfully rejected.");
            }

        } catch(Exception x) {
            x.printStackTrace();
            System.exit(1);
        }
    }
}
