/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4825182
 * @summary Verifies DefaultBoundedRangeModel.setMinimum() doesn't change extent
 * @run main RangeTest
 */
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class RangeTest implements ChangeListener {

    DefaultBoundedRangeModel model;

    static public void main(String s[]) {
        new RangeTest();
    }

    public RangeTest() {
        model = new DefaultBoundedRangeModel(-32768, Integer.MAX_VALUE,
                                             Integer.MIN_VALUE,
                                             Integer.MAX_VALUE);
        model.addChangeListener(this);
        printState("Initial State");
        System.out.println("Set min to -32768");
        int extent = model.getExtent();
        model.setMinimum(-32768);
        if (model.getExtent() != extent) {
            throw new RuntimeException("extent is changed to " + model.getExtent());
        }
    }

    public void stateChanged(ChangeEvent e) {
        printState("... State Changed");
    }

    private void printState(String msg) {
        System.out.println(msg + ": value=" + model.getValue()
                           + ", extent=" + model.getExtent()
                           + ", min=" + model.getMinimum()
                           + ", max=" + model.getMaximum());
    }
}

