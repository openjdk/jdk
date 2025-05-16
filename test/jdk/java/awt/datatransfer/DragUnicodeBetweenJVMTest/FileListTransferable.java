/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.File;
import java.util.Arrays;
import java.util.List;

class FileListTransferable implements Transferable {

    public static File [] files = new File [] {
        new File ("Я сразу смазал" +
                " карту будня"),
        new File ("плеснувши крас" +
                "ку из стакана"),
        new File ("я показал на б" +
                "люде студня"),
        new File ("косые скулы ок" +
                "еана"),
        new File ("На чешуе жестя" +
                "ной рыбы"),
        new File ("прочел я зовы " +
                "новых губ"),
        new File ("А вы"),
        new File ("ноктюрн сыграт" +
                "ь"),
        new File ("могли бы"),
        new File ("на флейте водо" +
                "сточных труб"),
    };

    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor [] {DataFlavor.javaFileListFlavor};
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor.equals(DataFlavor.javaFileListFlavor) ;
    }

    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        List<File> list = Arrays.asList(files);
        return list;

    }
}
