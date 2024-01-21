/*
 * Copyright (c) 2008, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
  @test
  @summary  To test if the DataFlavor.selectBestTextFlavor() method
         is selecting the correct best flavor from an array of flavors.
*/


import java.awt.datatransfer.DataFlavor;
import java.util.Vector;

public class BestTextFlavorTest {
    public static DataFlavor plainISOFlavor,
        plainAsciiFlavor,
        plainTextFlavor,
        enrichFlavor;
    public static DataFlavor[] bestFlavorArray1;
    public static DataFlavor[] bestFlavorArray2;
    public static DataFlavor bestFlavor1,bestFlavor2;
    private static Vector tmpFlavors;

    //Creating new flavors
    static {

        tmpFlavors = new Vector();
        try {
            tmpFlavors.addElement(DataFlavor.stringFlavor);
            tmpFlavors.addElement(new DataFlavor
                ("text/plain; charset=unicode"));
            tmpFlavors.addElement(
                new DataFlavor("text/plain; charset=us-ascii"));
            enrichFlavor=new DataFlavor("text/enriched; charset=ascii");
            tmpFlavors.addElement(enrichFlavor);
            plainTextFlavor=DataFlavor.getTextPlainUnicodeFlavor();
            tmpFlavors.addElement(plainTextFlavor);
            plainAsciiFlavor=new DataFlavor("text/plain; charset=ascii");
            tmpFlavors.addElement(plainAsciiFlavor);
            plainISOFlavor=new DataFlavor("text/plain; charset=iso8859-1");
            tmpFlavors.addElement(plainISOFlavor);
        }
        catch (ClassNotFoundException e) {
            // should never happen...
            System.out.println("ClassNotFound Exception is thrown when"+
                "flavors are created");
        }
    }

    public static void main(String[] args) {
        bestFlavorArray1 = new DataFlavor[tmpFlavors.size()];
        tmpFlavors.copyInto(bestFlavorArray1);

        //Selecting the best text flavor from a set of Data Flavors.
        bestFlavor1 = DataFlavor.selectBestTextFlavor(bestFlavorArray1);
        System.out.println("The Best Text Flavor is " + bestFlavor1);

        bestFlavorArray2 = reverseDataFlavor(bestFlavorArray1);
        bestFlavor2 = DataFlavor.selectBestTextFlavor(bestFlavorArray2);
        System.out.println("The Best Text Flavor is " + bestFlavor2);

        //Checking whether the selected flavors in both the arrays are same.
        if (bestFlavor2.match(bestFlavor1)) {
            System.out.println("The test is Passed");
        }
        else {
            System.out.println("The test is Failed");
            throw new RuntimeException("SelectBestTextFlavor doesn't return "+
                "the same best Text flavor  from a set of DataFlavors, "+
                "it always returns the first Text Flavor encountered.");
        }
    }

    //Returns the array of DataFlavor passed in reverse order.
    public static DataFlavor[] reverseDataFlavor(DataFlavor[] dataflavor) {

        DataFlavor[] tempFlavor = new DataFlavor[dataflavor.length];
        int j = 0;
        for (int i = dataflavor.length - 1  ; i >= 0; i--) {
            tempFlavor[j] = dataflavor[i];
            j++;
        }
        return tempFlavor;
    }
}
