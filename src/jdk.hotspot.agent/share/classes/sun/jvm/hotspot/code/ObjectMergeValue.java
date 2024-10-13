/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.code;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

class ObjectMergeValue extends ObjectValue {

    private ScopeValue selector;
    private ScopeValue mergePointer;
    private List<ScopeValue> possibleObjects;

    public ObjectMergeValue(int id) {
        super(id);
    }

    public boolean isObjectMerge() { return true; }

    void readObject(DebugInfoReadStream stream) {
        selector = ScopeValue.readFrom(stream);
        mergePointer = ScopeValue.readFrom(stream);
        possibleObjects = new ArrayList<>();
        int length = stream.readInt();
        for (int i = 0; i < length; i++) {
            ScopeValue val = readFrom(stream);
            possibleObjects.add(val);
        }
    }

    @Override
    public void printOn(PrintStream tty) {
        tty.print("merge_obj[" + id + "]");
        tty.print(" selector=\"");
        selector.printOn(tty);
        tty.print("\"");

        tty.print(" merge_pointer=\"");
        mergePointer.printOn(tty);
        tty.print("\"");

        tty.print(", candidate_objs=[" + ((ObjectValue) possibleObjects.get(0)).id);
        for (int i = 1; i < possibleObjects.size(); i++) {
            tty.print("," + ((ObjectValue) possibleObjects.get(i)).id);
        }
        tty.print("]");
    }
}
