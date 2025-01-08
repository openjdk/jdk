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
package com.sun.hotspot.igv.view.actions;

import com.sun.hotspot.igv.view.EditorTopComponent;
import java.beans.PropertyChangeEvent;

public class EnableFreeLayoutAction extends EnableLayoutAction {

    public EnableFreeLayoutAction(EditorTopComponent etc) {
        super(etc);
    }

    @Override
    protected String iconResource() {
        return "com/sun/hotspot/igv/view/images/dynamic.png";
    }

    @Override
    protected String getDescription() {
        return "Show dynamic free layout";
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        editor.getModel().setShowFreeInteractive(this.isSelected());
    }
}
