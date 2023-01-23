/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @summary Constant for testing public fields in AccessibleRole.
 */

public interface AccessibleRoleConstants {

    /**
     * Fully-qualified name of the class.
     */
    String CLASS_NAME = "javax.accessibility.AccessibleRole";

    /**
     * Public fields values in AccessibleRole class.
     */
    String[][] FIELDS = new String[][] { { "ALERT", "alert" },
        { "AWT_COMPONENT", "AWT component" }, { "CANVAS", "canvas" },
        { "CHECK_BOX", "check box" }, { "COLOR_CHOOSER", "color chooser" },
        { "COLUMN_HEADER", "column header" }, { "COMBO_BOX", "combo box" },
        { "DATE_EDITOR", "dateeditor" }, { "DESKTOP_ICON", "desktop icon" },
        { "DESKTOP_PANE", "desktop pane" }, { "DIALOG", "dialog" },
        { "DIRECTORY_PANE", "directory pane" }, { "EDITBAR", "editbar" },
        { "FILE_CHOOSER", "file chooser" }, { "FILLER", "filler" },
        { "FONT_CHOOSER", "fontchooser" }, { "FOOTER", "footer" },
        { "FRAME", "frame" }, { "GLASS_PANE", "glass pane" },
        { "GROUP_BOX", "groupbox" }, { "HEADER", "header" },
        { "HTML_CONTAINER", "HTML container" }, { "HYPERLINK", "hyperlink" },
        { "ICON", "icon" }, { "INTERNAL_FRAME", "internal frame" },
        { "LABEL", "label" }, { "LAYERED_PANE", "layered pane" },
        { "LIST", "list" }, { "LIST_ITEM", "list item" }, { "MENU", "menu" },
        { "MENU_BAR", "menu bar" }, { "MENU_ITEM", "menu item" },
        { "OPTION_PANE", "option pane" }, { "PAGE_TAB", "page tab" },
        { "PAGE_TAB_LIST", "page tab list" }, { "PANEL", "panel" },
        { "PARAGRAPH", "paragraph" }, { "PASSWORD_TEXT", "password text" },
        { "POPUP_MENU", "popup menu" }, { "PROGRESS_BAR", "progress bar" },
        { "PROGRESS_MONITOR", "progress monitor" },
        { "PUSH_BUTTON", "push JButton" }, { "RADIO_BUTTON", "radio JButton" },
        { "ROOT_PANE", "root pane" }, { "ROW_HEADER", "row header" },
        { "RULER", "ruler" }, { "SCROLL_BAR", "scroll bar" },
        { "SCROLL_PANE", "scroll pane" }, { "SEPARATOR", "separator" },
        { "SLIDER", "slider" }, { "SPIN_BOX", "spinbox" },
        { "SPLIT_PANE", "split pane" }, { "STATUS_BAR", "statusbar" },
        { "SWING_COMPONENT", "swing component" }, { "TABLE", "table" },
        { "TEXT", "text" }, { "TOGGLE_BUTTON", "toggle JButton" },
        { "TOOL_BAR", "tool bar" }, { "TOOL_TIP", "tool tip" },
        { "TREE", "tree" }, { "UNKNOWN", "unknown" },
        { "VIEWPORT", "viewport" }, { "WINDOW", "window" } };

        /**
         * Old(removed) fields in AccessibleRole class.
         */
        String[] OLD_FIELDS = new String[] {};
}
