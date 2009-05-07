/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package org.jdesktop.synthdesigner.generator;

import javax.swing.plaf.synth.Region;
import javax.swing.plaf.synth.SynthConstants;

/**
 * GeneratorUtils
 *
 * @author  Richard Bair
 * @author  Jasper Potts
 */
class GeneratorUtils {
    private GeneratorUtils() {}

    /**
     * Given a synth state, create the appropriate name as it would be used for a ui default key.
     * <p/>
     * For example:
     * <p/>
     * enabled enabled+over enabled+over+selected
     */
    static String toUIDefaultKey(int state) {
        StringBuffer buffer = new StringBuffer();
        if ((state & SynthConstants.DEFAULT) == SynthConstants.DEFAULT) {
            buffer.append("default");
        }
        if ((state & SynthConstants.DISABLED) == SynthConstants.DISABLED) {
            if (buffer.length() > 0) buffer.append("+");
            buffer.append("disabled");
        }
        if ((state & SynthConstants.ENABLED) == SynthConstants.ENABLED) {
            if (buffer.length() > 0) buffer.append("+");
            buffer.append("enabled");
        }
        if ((state & SynthConstants.FOCUSED) == SynthConstants.FOCUSED) {
            if (buffer.length() > 0) buffer.append("+");
            buffer.append("focused");
        }
        if ((state & SynthConstants.MOUSE_OVER) == SynthConstants.MOUSE_OVER) {
            if (buffer.length() > 0) buffer.append("+");
            buffer.append("over");
        }
        if ((state & SynthConstants.PRESSED) == SynthConstants.PRESSED) {
            if (buffer.length() > 0) buffer.append("+");
            buffer.append("down");
        }
        if ((state & SynthConstants.SELECTED) == SynthConstants.SELECTED) {
            if (buffer.length() > 0) buffer.append("+");
            buffer.append("selected");
        }
        return buffer.toString();
    }

    //takes a states string of the form Enabled+Foo+Bar.
    //removes any whitespace. Replaces the + signs with And.
    static String toClassName(String states) {
        String s = states.replace(" ", "");
        s = states.replace("+", "And");
        return s;
    }

    //takes a states string of the form Enabled+Foo+Bar.
    //removes any whitespace. Replaces the + signs with _.
    //capitalizes the whole lot
    static String toConstantName(String states) {
        String s = states.replace(" ", "");
        s = states.replace("+", "_");
        return s.toUpperCase();
    }

    /**
     * Given a string "s" of the form:
     *
     * A.\"A.a\".B
     *
     * Make it such that:
     *
     * AAAB
     *
     * For example, ComboBox.\"ComboBox.arrowButton\" would become
     * ComboBoxComboBoxArrowButton
     *
     * @param s
     * @return
     */
    static String makePretty(String s) {
        char[] src = s.toCharArray();
        char[] dst = new char[src.length];
        int dstIndex = 0;
        for (int i=0; i<src.length; i++) {
            //if the src char is a period and there is a following character,
            //make sure the character is capitalized.
            if ((src[i] == '.' || src[i] == ':') && i < src.length -1) {
                src[i+1] = Character.toUpperCase(src[i+1]);
                continue;
            }
            //if the src char is one that is to be removed, skip it.
            if (src[i] == '.' || src[i] == ':' || src[i] == '\\' || src[i] == '"') {
                continue;
            }
            //copy over the current char.
            dst[dstIndex++] = src[i];
        }
        //at this point, dstIndex is 1 greater than the last valid index position in dst
        //or in other words it represents the count.
        return new String(dst, 0, dstIndex);
    }

    /**
     * Encodes the given synth state as if it were specified in java code, such as
     * <p/>
     * SynthConstants.ENABLED | SynthConstants.MOUSE_OVER
     */
    static String toJavaList(int state) {
        StringBuffer buffer = new StringBuffer();
        if ((state & SynthConstants.DEFAULT) == SynthConstants.DEFAULT) {
            buffer.append("SynthConstants.DEFAULT");
        }
        if ((state & SynthConstants.DISABLED) == SynthConstants.DISABLED) {
            if (buffer.length() > 0) buffer.append(" | ");
            buffer.append("SynthConstants.DISABLED");
        }
        if ((state & SynthConstants.ENABLED) == SynthConstants.ENABLED) {
            if (buffer.length() > 0) buffer.append(" | ");
            buffer.append("SynthConstants.ENABLED");
        }
        if ((state & SynthConstants.FOCUSED) == SynthConstants.FOCUSED) {
            if (buffer.length() > 0) buffer.append(" | ");
            buffer.append("SynthConstants.FOCUSED");
        }
        if ((state & SynthConstants.MOUSE_OVER) == SynthConstants.MOUSE_OVER) {
            if (buffer.length() > 0) buffer.append(" | ");
            buffer.append("SynthConstants.MOUSE_OVER");
        }
        if ((state & SynthConstants.PRESSED) == SynthConstants.PRESSED) {
            if (buffer.length() > 0) buffer.append(" | ");
            buffer.append("SynthConstants.PRESSED");
        }
        if ((state & SynthConstants.SELECTED) == SynthConstants.SELECTED) {
            if (buffer.length() > 0) buffer.append(" | ");
            buffer.append("SynthConstants.SELECTED");
        }
        return buffer.toString();
    }

    /**
     * Checks the given region name to discover if it is one of the standard synth regions. If so, return the name in
     * caps and such. Otherwise, return a big fat null.
     * <p/>
     * I have to do this because, unfortunately, synth's Region doesn't implement equals.
     */
    static String getRegionNameCaps(String regionName) {
        if (Region.ARROW_BUTTON.getName().equals(regionName)) {
            return "ARROW_BUTTON";
        } else if (Region.BUTTON.getName().equals(regionName)) {
            return "BUTTON";
        } else if (Region.CHECK_BOX.getName().equals(regionName)) {
            return "CHECK_BOX";
        } else if (Region.CHECK_BOX_MENU_ITEM.getName().equals(regionName)) {
            return "CHECK_BOX_MENU_ITEM";
        } else if (Region.COLOR_CHOOSER.getName().equals(regionName)) {
            return "COLOR_CHOOSER";
        } else if (Region.COMBO_BOX.getName().equals(regionName)) {
            return "COMBO_BOX";
        } else if (Region.DESKTOP_ICON.getName().equals(regionName)) {
            return "DESKTOP_ICON";
        } else if (Region.DESKTOP_PANE.getName().equals(regionName)) {
            return "DESKTOP_PANE";
        } else if (Region.EDITOR_PANE.getName().equals(regionName)) {
            return "EDITOR_PANE";
        } else if (Region.FILE_CHOOSER.getName().equals(regionName)) {
            return "FILE_CHOOSER";
        } else if (Region.FORMATTED_TEXT_FIELD.getName().equals(regionName)) {
            return "FORMATTED_TEXT_FIELD";
        } else if (Region.INTERNAL_FRAME.getName().equals(regionName)) {
            return "INTERNAL_FRAME";
        } else if (Region.INTERNAL_FRAME_TITLE_PANE.getName().equals(regionName)) {
            return "INTERNAL_FRAME_TITLE_PANE";
        } else if (Region.LABEL.getName().equals(regionName)) {
            return "LABEL";
        } else if (Region.LIST.getName().equals(regionName)) {
            return "LIST";
        } else if (Region.MENU.getName().equals(regionName)) {
            return "MENU";
        } else if (Region.MENU_BAR.getName().equals(regionName)) {
            return "MENU_BAR";
        } else if (Region.MENU_ITEM.getName().equals(regionName)) {
            return "MENU_ITEM";
        } else if (Region.MENU_ITEM_ACCELERATOR.getName().equals(regionName)) {
            return "MENU_ITEM_ACCELERATOR";
        } else if (Region.OPTION_PANE.getName().equals(regionName)) {
            return "OPTION_PANE";
        } else if (Region.PANEL.getName().equals(regionName)) {
            return "PANEL";
        } else if (Region.PASSWORD_FIELD.getName().equals(regionName)) {
            return "PASSWORD_FIELD";
        } else if (Region.POPUP_MENU.getName().equals(regionName)) {
            return "POPUP_MENU";
        } else if (Region.POPUP_MENU_SEPARATOR.getName().equals(regionName)) {
            return "POPUP_MENU_SEPARATOR";
        } else if (Region.PROGRESS_BAR.getName().equals(regionName)) {
            return "PROGRESS_BAR";
        } else if (Region.RADIO_BUTTON.getName().equals(regionName)) {
            return "RADIO_BUTTON";
        } else if (Region.RADIO_BUTTON_MENU_ITEM.getName().equals(regionName)) {
            return "RADIO_BUTTON_MENU_ITEM";
        } else if (Region.ROOT_PANE.getName().equals(regionName)) {
            return "ROOT_PANE";
        } else if (Region.SCROLL_BAR.getName().equals(regionName)) {
            return "SCROLL_BAR";
        } else if (Region.SCROLL_BAR_THUMB.getName().equals(regionName)) {
            return "SCROLL_BAR_THUMB";
        } else if (Region.SCROLL_BAR_TRACK.getName().equals(regionName)) {
            return "SCROLL_BAR_TRACK";
        } else if (Region.SCROLL_PANE.getName().equals(regionName)) {
            return "SCROLL_PANE";
        } else if (Region.SEPARATOR.getName().equals(regionName)) {
            return "SEPARATOR";
        } else if (Region.SLIDER.getName().equals(regionName)) {
            return "SLIDER";
        } else if (Region.SLIDER_THUMB.getName().equals(regionName)) {
            return "SLIDER_THUMB";
        } else if (Region.SLIDER_TRACK.getName().equals(regionName)) {
            return "SLIDER_TRACK";
        } else if (Region.SPINNER.getName().equals(regionName)) {
            return "SPINNER";
        } else if (Region.SPLIT_PANE.getName().equals(regionName)) {
            return "SPLIT_PANE";
        } else if (Region.SPLIT_PANE_DIVIDER.getName().equals(regionName)) {
            return "SPLIT_PANE_DIVIDER";
        } else if (Region.TABBED_PANE.getName().equals(regionName)) {
            return "TABBED_PANE";
        } else if (Region.TABBED_PANE_CONTENT.getName().equals(regionName)) {
            return "TABBED_PANE_CONTENT";
        } else if (Region.TABBED_PANE_TAB.getName().equals(regionName)) {
            return "TABBED_PANE_TAB";
        } else if (Region.TABBED_PANE_TAB_AREA.getName().equals(regionName)) {
            return "TABBED_PANE_TAB_AREA";
        } else if (Region.TABLE.getName().equals(regionName)) {
            return "TABLE";
        } else if (Region.TABLE_HEADER.getName().equals(regionName)) {
            return "TABLE_HEADER";
        } else if (Region.TEXT_AREA.getName().equals(regionName)) {
            return "TEXT_AREA";
        } else if (Region.TEXT_FIELD.getName().equals(regionName)) {
            return "TEXT_FIELD";
        } else if (Region.TEXT_PANE.getName().equals(regionName)) {
            return "TEXT_PANE";
        } else if (Region.TOGGLE_BUTTON.getName().equals(regionName)) {
            return "TOGGLE_BUTTON";
        } else if (Region.TOOL_BAR.getName().equals(regionName)) {
            return "TOOL_BAR";
        } else if (Region.TOOL_BAR_CONTENT.getName().equals(regionName)) {
            return "TOOL_BAR_CONTENT";
        } else if (Region.TOOL_BAR_DRAG_WINDOW.getName().equals(regionName)) {
            return "TOOL_BAR_DRAG_WINDOW";
        } else if (Region.TOOL_BAR_SEPARATOR.getName().equals(regionName)) {
            return "TOOL_BAR_SEPARATOR";
        } else if (Region.TOOL_TIP.getName().equals(regionName)) {
            return "TOOL_TIP";
        } else if (Region.TREE.getName().equals(regionName)) {
            return "TREE";
        } else if (Region.TREE_CELL.getName().equals(regionName)) {
            return "TREE_CELL";
        } else if (Region.VIEWPORT.getName().equals(regionName)) {
            return "VIEWPORT";
        }
        System.err.println("[Info] Couldn't find a Region for " + regionName);
        return null;
    }
}
