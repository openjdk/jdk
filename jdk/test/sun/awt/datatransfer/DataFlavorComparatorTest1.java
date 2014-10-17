/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 8058473
   @summary "Comparison method violates its general contract" when using Clipboard
            Ensure that DataFlavorComparator conforms to Comparator contract
   @author Anton Nashatyrev
   @run main DataFlavorComparatorTest1
*/
import sun.datatransfer.DataFlavorUtil;

import java.awt.datatransfer.DataFlavor;
import java.util.Comparator;

public class DataFlavorComparatorTest1 {

    public static void main(String[] args) throws Exception {
        String[] mimes = new String[] {
                "text/plain",
                "text/plain; charset=unicode",
                "text/plain; charset=cp1251",
                "text/plain; charset=unicode; class=java.io.InputStream",
                "text/plain; charset=unicode; class=java.io.Serializable",
                "text/plain; charset=unicode; class=java.lang.Object",
                "text/plain; class=java.lang.String",
                "text/plain; class=java.io.Reader",
                "text/plain; class=java.lang.Object",
                "text/html",
                "text/html; charset=unicode",
                "text/html; charset=cp1251",
                "text/html; charset=unicode; class=java.io.InputStream",
                "text/html; charset=unicode; class=java.io.Serializable",
                "text/html; charset=unicode; class=java.lang.Object",
                "text/html; class=java.lang.String",
                "text/html; class=java.io.Reader",
                "text/html; class=java.lang.Object",
                "text/unknown",
                "text/unknown; charset=unicode",
                "text/unknown; charset=cp1251",
                "text/unknown; charset=unicode; class=java.io.InputStream",
                "text/unknown; charset=unicode; class=java.io.Serializable",
                "text/unknown; charset=unicode; class=java.lang.Object",
                "text/unknown; class=java.lang.String",
                "text/unknown; class=java.io.Reader",
                "text/unknown; class=java.lang.Object",
                "application/unknown; class=java.io.InputStream",
                "application/unknown; class=java.lang.Object",
                "application/unknown",
                "application/x-java-jvm-local-objectref; class=java.io.InputStream",
                "application/x-java-jvm-local-objectref; class=java.lang.Object",
                "application/x-java-jvm-local-objectref",
                "unknown/flavor",
                "unknown/flavor; class=java.io.InputStream",
                "unknown/flavor; class=java.lang.Object",
        };

        DataFlavor[] flavors = new DataFlavor[mimes.length];
        for (int i = 0; i < flavors.length; i++) {
            flavors[i] = new DataFlavor(mimes[i]);
        }

        testComparator(DataFlavorUtil.getDataFlavorComparator(), flavors);

        System.out.println("Passed.");
    }

    private static void testComparator(Comparator cmp, DataFlavor[] flavs)
            throws ClassNotFoundException {

        for (DataFlavor x: flavs) {
            for (DataFlavor y: flavs) {
                if (Math.signum(cmp.compare(x,y)) != -Math.signum(cmp.compare(y,x))) {
                    throw new RuntimeException("Antisymmetry violated: " + x + ", " + y);
                }
                if (cmp.compare(x,y) == 0 && !x.equals(y)) {
                    throw new RuntimeException("Equals rule violated: " + x + ", " + y);
                }
                for (DataFlavor z: flavs) {
                    if (cmp.compare(x,y) == 0) {
                        if (Math.signum(cmp.compare(x, z)) != Math.signum(cmp.compare(y, z))) {
                            throw new RuntimeException("Transitivity (1) violated: " + x + ", " + y + ", " + z);
                        }
                    } else {
                        if (Math.signum(cmp.compare(x, y)) == Math.signum(cmp.compare(y, z))) {
                            if (Math.signum(cmp.compare(x, y)) != Math.signum(cmp.compare(x, z))) {
                                throw new RuntimeException("Transitivity (2) violated: " + x + ", " + y + ", " + z);
                            }
                        }
                    }
                }
            }
        }
    }
}
