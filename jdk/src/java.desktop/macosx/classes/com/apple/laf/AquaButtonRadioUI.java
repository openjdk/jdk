/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.apple.laf;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;

import apple.laf.JRSUIConstants.*;

import com.apple.laf.AquaUtilControlSize.*;
import com.apple.laf.AquaUtils.*;

public class AquaButtonRadioUI extends AquaButtonLabeledUI {
    protected static final RecyclableSingleton<AquaButtonRadioUI> instance = new RecyclableSingletonFromDefaultConstructor<AquaButtonRadioUI>(AquaButtonRadioUI.class);
    protected static final RecyclableSingleton<ImageIcon> sizingIcon = new RecyclableSingleton<ImageIcon>() {
        protected ImageIcon getInstance() {
            return new ImageIcon(AquaNativeResources.getRadioButtonSizerImage());
        }
    };

    public static ComponentUI createUI(final JComponent b) {
        return instance.get();
    }

    public static Icon getSizingRadioButtonIcon(){
        return sizingIcon.get();
    }

    protected String getPropertyPrefix() {
        return "RadioButton" + ".";
    }

    protected AquaButtonBorder getPainter() {
        return new RadioButtonBorder();
    }

    public static class RadioButtonBorder extends LabeledButtonBorder {
        public RadioButtonBorder() {
            super(new SizeDescriptor(new SizeVariant().replaceMargins("RadioButton.margin")));
            painter.state.set(Widget.BUTTON_RADIO);
        }

        public RadioButtonBorder(final RadioButtonBorder other) {
            super(other);
        }
    }
}
