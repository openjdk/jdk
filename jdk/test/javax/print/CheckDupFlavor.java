/*
 * Copyright 2004-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 4996318 6731937
 * @summary  There should be no duplicates returned by getSupportedDocFlavors.
 * @run main CheckDupFlavor
 */
import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import java.util.ArrayList;


public class CheckDupFlavor {
    public static void main(String[] args){
        PrintService pservice =
                     PrintServiceLookup.lookupDefaultPrintService();

        if (pservice == null) {
                System.out.println("No default PrintService found. Test ABORTED.");
            return;
        }

        System.out.println("Default service = "+pservice);

        DocFlavor[] flavors = pservice.getSupportedDocFlavors();
        if (flavors==null) {
            System.out.println("No flavors supported. Test PASSED.");
            return;
        }


        ArrayList flavorList = new ArrayList();
        for (int i=0; i<flavors.length; i++) {
                if (flavors[i] == null) {
                        throw new RuntimeException("Null flavor. Test FAILED.");
                } else if (flavorList.contains(flavors[i])) {
                        throw new RuntimeException("\n\tDuplicate flavor found : "+flavors[i]+" : Test FAILED.");
                } else {
                        flavorList.add(flavors[i]);
                }
        }
        System.out.println("No duplicate found. Test PASSED.");
    }
}
