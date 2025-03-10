/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, BELLSOFT. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.print;

import java.io.Serial;
import java.util.ArrayList;

import javax.print.attribute.EnumSyntax;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.OutputBin;

public final class CustomOutputBin extends OutputBin {
    private static ArrayList<String> customStringTable = new ArrayList<>();
    private static ArrayList<CustomOutputBin> customEnumTable = new ArrayList<>();
    private String choiceName;

    private CustomOutputBin(int x) {
        super(x);
    }

    private static synchronized int nextValue(String name) {
      customStringTable.add(name);
      return (customStringTable.size()-1);
    }

    private CustomOutputBin(String name, String choice) {
        super(nextValue(name));
        choiceName = choice;
        customEnumTable.add(this);
    }

    /**
     * Creates a custom output bin
     */
    public static synchronized CustomOutputBin createOutputBin(String name, String choice) {
        for (CustomOutputBin bin : customEnumTable) {
            if (bin.getChoiceName().equals(choice) && bin.getCustomName().equals(name)) {
                return bin;
            }
        }
        return new CustomOutputBin(name, choice);
    }

    private static final long serialVersionUID = 3018751086294120717L;

    /**
     * Returns the command string for this media tray.
     */
    public String getChoiceName() {
        return choiceName;
    }

    /**
     * Returns the string table for super class MediaTray.
     */
    public OutputBin[] getSuperEnumTable() {
      return (OutputBin[])super.getEnumValueTable();
    }

    /**
     * Returns the string table for class CustomOutputBin.
     */
    @Override
    protected String[] getStringTable() {
      String[] nameTable = new String[customStringTable.size()];
      return customStringTable.toArray(nameTable);
    }

    /**
     * Returns a custom bin name
     */
    public String getCustomName() {
        return customStringTable.get(getValue() - getOffset());
    }

    /**
     * Returns the enumeration value table for class CustomOutputBin.
     */
    @Override
    protected CustomOutputBin[] getEnumValueTable() {
        CustomOutputBin[] enumTable = new CustomOutputBin[customEnumTable.size()];
      return customEnumTable.toArray(enumTable);
    }
}
