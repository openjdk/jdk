/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package com.sun.hotspot.igv.data;

import com.sun.hotspot.igv.data.Properties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Thomas Wuerthinger
 */
public class InputMethod extends Properties.Entity {

    private String name;
    private int bci;
    private String shortName;
    private List<InputMethod> inlined;
    private InputMethod parentMethod;
    private Group group;
    private List<InputBytecode> bytecodes;

    /** Creates a new instance of InputMethod */
    public InputMethod(Group parent, String name, String shortName, int bci) {
        this.group = parent;
        this.name = name;
        this.bci = bci;
        this.shortName = shortName;
        inlined = new ArrayList<InputMethod>();
        bytecodes = new ArrayList<InputBytecode>();
    }

    public List<InputBytecode> getBytecodes() {
        return Collections.unmodifiableList(bytecodes);
    }

    public List<InputMethod> getInlined() {
        return Collections.unmodifiableList(inlined);
    }

    public void addInlined(InputMethod m) {

        // assert bci unique
        for (InputMethod m2 : inlined) {
            assert m2.getBci() != m.getBci();
        }

        inlined.add(m);
        assert m.parentMethod == null;
        m.parentMethod = this;

        for (InputBytecode bc : bytecodes) {
            if (bc.getBci() == m.getBci()) {
                bc.setInlined(m);
            }
        }
    }

    public Group getGroup() {
        return group;
    }

    public String getShortName() {
        return shortName;
    }

    public void setBytecodes(String text) {

        String[] strings = text.split("\n");
        int oldNumber = -1;
        for (String s : strings) {

            if (s.length() > 0 && Character.isDigit(s.charAt(0))) {
                s = s.trim();
                int spaceIndex = s.indexOf(' ');
                String numberString = s.substring(0, spaceIndex);
                String tmpName = s.substring(spaceIndex + 1, s.length());

                int number = -1;
                number = Integer.parseInt(numberString);

                // assert correct order of bytecodes
                assert number > oldNumber;

                InputBytecode bc = new InputBytecode(number, tmpName);
                bytecodes.add(bc);

                for (InputMethod m : inlined) {
                    if (m.getBci() == number) {
                        bc.setInlined(m);
                        break;
                    }
                }
            }
        }
    }

    public String getName() {
        return name;
    }

    public int getBci() {
        return bci;
    }
}
