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
 * @summary Test that SnmpOid hashCode is consistent with equals.
 * @bug     4955105
 * @build   SnmpOidHashCode
 * @run     main SnmpOidHashCode
 */
import com.sun.jmx.snmp.SnmpOid;

public class SnmpOidHashCode {
    public static final String[] oids = {
        "1.1.1.1.1.1.1.1.1",
        "1.1.1.1.1.1.1.1",
        "1.1.1.1.2.1.1.1.1",
        "1.1.1.1.1.2.1.1.1",
        "1.3.2",
        "2.3.1",
        "1.2.67."+Integer.MAX_VALUE+"."+Integer.MAX_VALUE+
        "."+Integer.MAX_VALUE+"."+Integer.MAX_VALUE+"."+Integer.MAX_VALUE+
        "1",
        "1.2."+0xFFFFFFFFL+".3."+0xFFFFFFFFL+".4."+0xFFFFFFFFL+
        ".5."+0xFFFFFFFFL+".6."+0xFFFFFFFFL+".7."+0xFFFFFFFFL+
        ".8."+0xFFFFFFFFL+".9."+0xFFFFFFFFL+".10."+0xFFFFFFFFL+
        ".11."+0xFFFFFFFFL+".12."+0xFFFFFFFFL+".13."+0xFFFFFFFFL+
        ".14."+0xFFFFFFFFL+".15."+0xFFFFFFFFL+".16."+0xFFFFFFFFL+
        ".17."+0xFFFFFFFFL+".18."+0xFFFFFFFFL+".19."+0xFFFFFFFFL+
        ".20."+0xFFFFFFFFL+".21."+0xFFFFFFFFL+".22."+0xFFFFFFFFL+
        ".23."+0xFFFFFFFFL+".24."+0xFFFFFFFFL+".25."+0xFFFFFFFFL+
        ".26."+0xFFFFFFFFL+".27."+0xFFFFFFFFL+".28."+0xFFFFFFFFL+
        ".29."+0xFFFFFFFFL+
        ".30."+0xFFFFFFFFL+".31."+0xFFFFFFFFL+".32."+0xFFFFFFFFL+
        ".33."+0xFFFFFFFFL+".34."+0xFFFFFFFFL+".35."+0xFFFFFFFFL+
        ".36."+0xFFFFFFFFL+".37."+0xFFFFFFFFL+".38."+0xFFFFFFFFL+
        ".39."+0xFFFFFFFFL
    };

    public static void main(String args[]) {
        try {
            int errCount=0;
            int collisions=0;
            for (int i=0;i<oids.length;i++) {
                System.out.println("Testing " + oids[i]);
                final SnmpOid o1 = new SnmpOid(oids[i]);
                final int startCount=errCount;
                for (int j=0;j<oids.length;j++) {
                    final SnmpOid o2 = new SnmpOid(oids[j]);
                    if (o1.equals(o2)) {
                        if (!(oids[i].equals(oids[j]))) {
                            System.err.println("OIDs differ but " +
                                               "equals yields true: " +
                                               "\n\to1="+oids[i]+
                                               "\n\to2="+oids[j]);
                            errCount++;
                        }
                        if (o1.hashCode() != o2.hashCode()) {
                            System.err.println("OIDs are equal but " +
                                               "hashCode differ:" +
                                               "\n\thashCode("+o1+")="+
                                               o1.hashCode()+", "+
                                               "\n\thashCode("+o2+")="+
                                               o2.hashCode());
                            errCount++;
                        }
                    } else {
                        if (oids[i].equals(oids[j])) {
                            System.err.println("OIDs are equal but " +
                                               "equals yields false: " +
                                               "\n\to1="+oids[i]+
                                               "\n\to2="+oids[j]);
                            errCount++;
                        }
                        if (o1.hashCode() == o2.hashCode()) collisions++;
                    }
                }
                if (errCount == startCount)
                    System.out.println("*** Test Passed for: " + o1);
                else
                    System.out.println("*** Test Failed (" +
                                       (errCount - startCount) + ") for: "
                                       + o1);
            }

            if (errCount == 0) {
                System.out.println("*** -----------------------------------");
                System.out.println("*** Test SnmpOidHashCode " +
                                   "succesfully passed (" + collisions +
                                   " collisions).");
                System.out.println("*** -----------------------------------");
            } else {
                System.err.println("*** -----------------------------------");
                System.err.println("*** Test SnmpOidHashCode failed: " +
                                   errCount + " failures (" + collisions +
                                   " collisions).");
                System.err.println("*** -----------------------------------");
                System.exit(1);
            }
        } catch(Exception x) {
            x.printStackTrace();
            System.exit(2);
        }
    }
}
