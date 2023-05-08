/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.Vector;


public class TransferableObject implements Transferable {
    private Object data;
    private String stringText;
    public static DataFlavor stringFlavor,localObjectFlavor;

    static
    {
        // Data Flavor for Java Local Object
        try {
                localObjectFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType);
                stringFlavor = DataFlavor.stringFlavor;
        }
        catch (ClassNotFoundException e) {
                System.out.println("Exception " + e);
        }
    }

    DataFlavor[] dfs;

    public TransferableObject(Object data) {
        super();
        Vector v = new Vector();
            if(data instanceof String) {
                v.addElement(stringFlavor);
                stringText = (String)data;
            }
            else {
                v.addElement(localObjectFlavor);
            }

            dfs = new DataFlavor[v.size()];
        v.copyInto(dfs);

        this.data = data;
    }

    // Retrieve the data based on the flavor
    public Object getTransferData(DataFlavor flavor)
        throws UnsupportedFlavorException {

        System.out.println("\n ***************************************");
        System.out.println(" The Flavor passed to retrieve the data : "
                + flavor.getHumanPresentableName());
        System.out.println(" The Flavors supported");
        for (int j = 0; j < dfs.length; j++)
            System.out.println(" Flavor : " + dfs[j].getHumanPresentableName());

        System.out.println(" ***************************************\n");

        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        } else if (flavor.equals(stringFlavor)) {
            return stringText;
        } else if (localObjectFlavor.isMimeTypeEqual(flavor)) {
            return data;
        }
        return null;
    }

    public DataFlavor[] getTransferDataFlavors(){
        return dfs;
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        for (int i = 0 ; i < dfs.length; i++) {
            if (dfs[i].match(flavor)) {
                return true;
            }
        }
        return false;
    }

}
