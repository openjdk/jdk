/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
   @bug 4244034
   @summary Tests that AbstractAction has method getKeys()
   @run main bug4244034
*/

import java.util.Arrays;
import java.util.List;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.AbstractAction;

public class bug4244034 {

    /** Auxilliary class extending AbstractAction
     */
    static class NullAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {}
    }

    public static void main(String[] args) {
        AbstractAction action = new NullAction();
        action.putValue(Action.SHORT_DESCRIPTION, "my short descr");
        action.putValue(Action.LONG_DESCRIPTION, "my long descr");
        action.putValue(Action.NAME, "my name");

        Object[] keys = action.getKeys();
        List keysList = Arrays.asList(keys);
        if (! keysList.contains(Action.SHORT_DESCRIPTION) ||
            ! keysList.contains(Action.LONG_DESCRIPTION) ||
            ! keysList.contains(Action.NAME)) {

            throw new Error("Failed: getKeys() works improperly");
        }
    }
}
