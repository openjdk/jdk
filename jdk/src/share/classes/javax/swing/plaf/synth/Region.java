/*
 * Copyright 2002-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
package javax.swing.plaf.synth;

import javax.swing.*;
import java.util.*;

/**
 * A distinct rendering area of a Swing component.  A component may
 * support one or more regions.  Specific component regions are defined
 * by the typesafe enumeration in this class.
 * <p>
 * Regions are typically used as a way to identify the <code>Component</code>s
 * and areas a particular style is to apply to. Synth's file format allows you
 * to bind styles based on the name of a <code>Region</code>.
 * The name is derived from the field name of the constant:
 * <ol>
 *  <li>Map all characters to lowercase.
 *  <li>Map the first character to uppercase.
 *  <li>Map the first character after underscores to uppercase.
 *  <li>Remove all underscores.
 * </ol>
 * For example, to identify the <code>SPLIT_PANE</code>
 * <code>Region</code> you would use <code>SplitPane</code>.
 * The following shows a custom <code>SynthStyleFactory</code>
 * that returns a specific style for split panes:
 * <pre>
 *    public SynthStyle getStyle(JComponent c, Region id) {
 *        if (id == Region.SPLIT_PANE) {
 *            return splitPaneStyle;
 *        }
 *        ...
 *    }
 * </pre>
 * The following <a href="doc-files/synthFileFormat.html">xml</a>
 * accomplishes the same thing:
 * <pre>
 * &lt;style id="splitPaneStyle">
 *   ...
 * &lt;/style>
 * &lt;bind style="splitPaneStyle" type="region" key="SplitPane"/>
 * </pre>
 *
 * @since 1.5
 * @author Scott Violet
 */
public class Region {
    private static final Map<String, Region> uiToRegionMap = new HashMap<String, Region>();
    private static final Map<Region, String> lowerCaseNameMap = new HashMap<Region, String>();

    /**
     * ArrowButton's are special types of buttons that also render a
     * directional indicator, typically an arrow. ArrowButtons are used by
     * composite components, for example ScrollBar's contain ArrowButtons.
     * To bind a style to this <code>Region</code> use the name
     * <code>ArrowButton</code>.
     */
    public static final Region ARROW_BUTTON = new Region("ArrowButton",
                                                         "ArrowButtonUI");

    /**
     * Button region. To bind a style to this <code>Region</code> use the name
     * <code>Button</code>.
     */
    public static final Region BUTTON = new Region("Button",
                                                   "ButtonUI");

    /**
     * CheckBox region. To bind a style to this <code>Region</code> use the name
     * <code>CheckBox</code>.
     */
    public static final Region CHECK_BOX = new Region("CheckBox",
                                                   "CheckBoxUI");

    /**
     * CheckBoxMenuItem region. To bind a style to this <code>Region</code> use
     * the name <code>CheckBoxMenuItem</code>.
     */
    public static final Region CHECK_BOX_MENU_ITEM = new Region(
                                     "CheckBoxMenuItem", "CheckBoxMenuItemUI");

    /**
     * ColorChooser region. To bind a style to this <code>Region</code> use
     * the name <code>ColorChooser</code>.
     */
    public static final Region COLOR_CHOOSER = new Region(
                                     "ColorChooser", "ColorChooserUI");

    /**
     * ComboBox region. To bind a style to this <code>Region</code> use
     * the name <code>ComboBox</code>.
     */
    public static final Region COMBO_BOX = new Region(
                                     "ComboBox", "ComboBoxUI");

    /**
     * DesktopPane region. To bind a style to this <code>Region</code> use
     * the name <code>DesktopPane</code>.
     */
    public static final Region DESKTOP_PANE = new Region("DesktopPane",
                                                         "DesktopPaneUI");
    /**
     * DesktopIcon region. To bind a style to this <code>Region</code> use
     * the name <code>DesktopIcon</code>.
     */
    public static final Region DESKTOP_ICON = new Region("DesktopIcon",
                                                         "DesktopIconUI");

    /**
     * EditorPane region. To bind a style to this <code>Region</code> use
     * the name <code>EditorPane</code>.
     */
    public static final Region EDITOR_PANE = new Region("EditorPane",
                                                        "EditorPaneUI");

    /**
     * FileChooser region. To bind a style to this <code>Region</code> use
     * the name <code>FileChooser</code>.
     */
    public static final Region FILE_CHOOSER = new Region("FileChooser",
                                                         "FileChooserUI");

    /**
     * FormattedTextField region. To bind a style to this <code>Region</code> use
     * the name <code>FormattedTextField</code>.
     */
    public static final Region FORMATTED_TEXT_FIELD = new Region(
                            "FormattedTextField", "FormattedTextFieldUI");

    /**
     * InternalFrame region. To bind a style to this <code>Region</code> use
     * the name <code>InternalFrame</code>.
     */
    public static final Region INTERNAL_FRAME = new Region("InternalFrame",
                                                           "InternalFrameUI");
    /**
     * TitlePane of an InternalFrame. The TitlePane typically
     * shows a menu, title, widgets to manipulate the internal frame.
     * To bind a style to this <code>Region</code> use the name
     * <code>InternalFrameTitlePane</code>.
     */
    public static final Region INTERNAL_FRAME_TITLE_PANE =
                         new Region("InternalFrameTitlePane",
                                    "InternalFrameTitlePaneUI");

    /**
     * Label region. To bind a style to this <code>Region</code> use the name
     * <code>Label</code>.
     */
    public static final Region LABEL = new Region("Label", "LabelUI");

    /**
     * List region. To bind a style to this <code>Region</code> use the name
     * <code>List</code>.
     */
    public static final Region LIST = new Region("List", "ListUI");

    /**
     * Menu region. To bind a style to this <code>Region</code> use the name
     * <code>Menu</code>.
     */
    public static final Region MENU = new Region("Menu", "MenuUI");

    /**
     * MenuBar region. To bind a style to this <code>Region</code> use the name
     * <code>MenuBar</code>.
     */
    public static final Region MENU_BAR = new Region("MenuBar", "MenuBarUI");

    /**
     * MenuItem region. To bind a style to this <code>Region</code> use the name
     * <code>MenuItem</code>.
     */
    public static final Region MENU_ITEM = new Region("MenuItem","MenuItemUI");

    /**
     * Accelerator region of a MenuItem. To bind a style to this
     * <code>Region</code> use the name <code>MenuItemAccelerator</code>.
     */
    public static final Region MENU_ITEM_ACCELERATOR = new Region(
                                         "MenuItemAccelerator");

    /**
     * OptionPane region. To bind a style to this <code>Region</code> use
     * the name <code>OptionPane</code>.
     */
    public static final Region OPTION_PANE = new Region("OptionPane",
                                                        "OptionPaneUI");

    /**
     * Panel region. To bind a style to this <code>Region</code> use the name
     * <code>Panel</code>.
     */
    public static final Region PANEL = new Region("Panel", "PanelUI");

    /**
     * PasswordField region. To bind a style to this <code>Region</code> use
     * the name <code>PasswordField</code>.
     */
    public static final Region PASSWORD_FIELD = new Region("PasswordField",
                                                           "PasswordFieldUI");

    /**
     * PopupMenu region. To bind a style to this <code>Region</code> use
     * the name <code>PopupMenu</code>.
     */
    public static final Region POPUP_MENU = new Region("PopupMenu",
                                                       "PopupMenuUI");

    /**
     * PopupMenuSeparator region. To bind a style to this <code>Region</code>
     * use the name <code>PopupMenuSeparator</code>.
     */
    public static final Region POPUP_MENU_SEPARATOR = new Region(
                           "PopupMenuSeparator", "PopupMenuSeparatorUI");

    /**
     * ProgressBar region. To bind a style to this <code>Region</code>
     * use the name <code>ProgressBar</code>.
     */
    public static final Region PROGRESS_BAR = new Region("ProgressBar",
                                                         "ProgressBarUI");

    /**
     * RadioButton region. To bind a style to this <code>Region</code>
     * use the name <code>RadioButton</code>.
     */
    public static final Region RADIO_BUTTON = new Region(
                               "RadioButton", "RadioButtonUI");

    /**
     * RegionButtonMenuItem region. To bind a style to this <code>Region</code>
     * use the name <code>RadioButtonMenuItem</code>.
     */
    public static final Region RADIO_BUTTON_MENU_ITEM = new Region(
                               "RadioButtonMenuItem", "RadioButtonMenuItemUI");

    /**
     * RootPane region. To bind a style to this <code>Region</code> use
     * the name <code>RootPane</code>.
     */
    public static final Region ROOT_PANE = new Region("RootPane",
                                                      "RootPaneUI");

    /**
     * ScrollBar region. To bind a style to this <code>Region</code> use
     * the name <code>ScrollBar</code>.
     */
    public static final Region SCROLL_BAR = new Region("ScrollBar",
                                                       "ScrollBarUI");
    /**
     * Track of the ScrollBar. To bind a style to this <code>Region</code> use
     * the name <code>ScrollBarTrack</code>.
     */
    public static final Region SCROLL_BAR_TRACK = new Region("ScrollBarTrack");
    /**
     * Thumb of the ScrollBar. The thumb is the region of the ScrollBar
     * that gives a graphical depiction of what percentage of the View is
     * currently visible. To bind a style to this <code>Region</code> use
     * the name <code>ScrollBarThumb</code>.
     */
    public static final Region SCROLL_BAR_THUMB = new Region("ScrollBarThumb");

    /**
     * ScrollPane region. To bind a style to this <code>Region</code> use
     * the name <code>ScrollPane</code>.
     */
    public static final Region SCROLL_PANE = new Region("ScrollPane",
                                                        "ScrollPaneUI");

    /**
     * Separator region. To bind a style to this <code>Region</code> use
     * the name <code>Separator</code>.
     */
    public static final Region SEPARATOR = new Region("Separator",
                                                      "SeparatorUI");

    /**
     * Slider region. To bind a style to this <code>Region</code> use
     * the name <code>Slider</code>.
     */
    public static final Region SLIDER = new Region("Slider", "SliderUI");
    /**
     * Track of the Slider. To bind a style to this <code>Region</code> use
     * the name <code>SliderTrack</code>.
     */
    public static final Region SLIDER_TRACK = new Region("SliderTrack");
    /**
     * Thumb of the Slider. The thumb of the Slider identifies the current
     * value. To bind a style to this <code>Region</code> use the name
     * <code>SliderThumb</code>.
     */
    public static final Region SLIDER_THUMB = new Region("SliderThumb");

    /**
     * Spinner region. To bind a style to this <code>Region</code> use the name
     * <code>Spinner</code>.
     */
    public static final Region SPINNER = new Region("Spinner", "SpinnerUI");

    /**
     * SplitPane region. To bind a style to this <code>Region</code> use the name
     * <code>SplitPane</code>.
     */
    public static final Region SPLIT_PANE = new Region("SplitPane",
                                                      "SplitPaneUI");

    /**
     * Divider of the SplitPane. To bind a style to this <code>Region</code>
     * use the name <code>SplitPaneDivider</code>.
     */
    public static final Region SPLIT_PANE_DIVIDER = new Region(
                                        "SplitPaneDivider");

    /**
     * TabbedPane region. To bind a style to this <code>Region</code> use
     * the name <code>TabbedPane</code>.
     */
    public static final Region TABBED_PANE = new Region("TabbedPane",
                                                        "TabbedPaneUI");
    /**
     * Region of a TabbedPane for one tab. To bind a style to this
     * <code>Region</code> use the name <code>TabbedPaneTab</code>.
     */
    public static final Region TABBED_PANE_TAB = new Region("TabbedPaneTab");
    /**
     * Region of a TabbedPane containing the tabs. To bind a style to this
     * <code>Region</code> use the name <code>TabbedPaneTabArea</code>.
     */
    public static final Region TABBED_PANE_TAB_AREA =
                                 new Region("TabbedPaneTabArea");
    /**
     * Region of a TabbedPane containing the content. To bind a style to this
     * <code>Region</code> use the name <code>TabbedPaneContent</code>.
     */
    public static final Region TABBED_PANE_CONTENT =
                                 new Region("TabbedPaneContent");

    /**
     * Table region. To bind a style to this <code>Region</code> use
     * the name <code>Table</code>.
     */
    public static final Region TABLE = new Region("Table", "TableUI");

    /**
     * TableHeader region. To bind a style to this <code>Region</code> use
     * the name <code>TableHeader</code>.
     */
    public static final Region TABLE_HEADER = new Region("TableHeader",
                                                         "TableHeaderUI");
    /**
     * TextArea region. To bind a style to this <code>Region</code> use
     * the name <code>TextArea</code>.
     */
    public static final Region TEXT_AREA = new Region("TextArea",
                                                      "TextAreaUI");

    /**
     * TextField region. To bind a style to this <code>Region</code> use
     * the name <code>TextField</code>.
     */
    public static final Region TEXT_FIELD = new Region("TextField",
                                                       "TextFieldUI");

    /**
     * TextPane region. To bind a style to this <code>Region</code> use
     * the name <code>TextPane</code>.
     */
    public static final Region TEXT_PANE = new Region("TextPane",
                                                      "TextPaneUI");

    /**
     * ToggleButton region. To bind a style to this <code>Region</code> use
     * the name <code>ToggleButton</code>.
     */
    public static final Region TOGGLE_BUTTON = new Region("ToggleButton",
                                                          "ToggleButtonUI");

    /**
     * ToolBar region. To bind a style to this <code>Region</code> use
     * the name <code>ToolBar</code>.
     */
    public static final Region TOOL_BAR = new Region("ToolBar", "ToolBarUI");
    /**
     * Region of the ToolBar containing the content. To bind a style to this
     * <code>Region</code> use the name <code>ToolBarContent</code>.
     */
    public static final Region TOOL_BAR_CONTENT = new Region("ToolBarContent");
    /**
     * Region for the Window containing the ToolBar. To bind a style to this
     * <code>Region</code> use the name <code>ToolBarDragWindow</code>.
     */
    public static final Region TOOL_BAR_DRAG_WINDOW = new Region(
                                        "ToolBarDragWindow", null, false);

    /**
     * ToolTip region. To bind a style to this <code>Region</code> use
     * the name <code>ToolTip</code>.
     */
    public static final Region TOOL_TIP = new Region("ToolTip", "ToolTipUI");

    /**
     * ToolBar separator region. To bind a style to this <code>Region</code> use
     * the name <code>ToolBarSeparator</code>.
     */
    public static final Region TOOL_BAR_SEPARATOR = new Region(
                          "ToolBarSeparator", "ToolBarSeparatorUI");

    /**
     * Tree region. To bind a style to this <code>Region</code> use the name
     * <code>Tree</code>.
     */
    public static final Region TREE = new Region("Tree", "TreeUI");
    /**
     * Region of the Tree for one cell. To bind a style to this
     * <code>Region</code> use the name <code>TreeCell</code>.
     */
    public static final Region TREE_CELL = new Region("TreeCell");

    /**
     * Viewport region. To bind a style to this <code>Region</code> use
     * the name <code>Viewport</code>.
     */
    public static final Region VIEWPORT = new Region("Viewport", "ViewportUI");


    private String name;
    private boolean subregion;


    static Region getRegion(JComponent c) {
        return uiToRegionMap.get(c.getUIClassID());
    }

    static void registerUIs(UIDefaults table) {
        for (String key : uiToRegionMap.keySet()) {
            table.put(key, "javax.swing.plaf.synth.SynthLookAndFeel");
        }
    }


    Region(String name) {
        this(name, null, true);
    }

    Region(String name, String ui) {
        this(name, ui, false);
    }

    /**
     * Creates a Region with the specified name. This should only be
     * used if you are creating your own <code>JComponent</code> subclass
     * with a custom <code>ComponentUI</code> class.
     *
     * @param name Name of the region
     * @param ui String that will be returned from
     *           <code>component.getUIClassID</code>. This will be null
     *           if this is a subregion.
     * @param subregion Whether or not this is a subregion.
     */
    protected Region(String name, String ui, boolean subregion) {
        if (name == null) {
            throw new NullPointerException("You must specify a non-null name");
        }
        this.name = name;
        if (ui != null) {
            uiToRegionMap.put(ui, this);
        }
        this.subregion = subregion;
    }

    /**
     * Returns true if the Region is a subregion of a Component, otherwise
     * false. For example, <code>Region.BUTTON</code> corresponds do a
     * <code>Component</code> so that <code>Region.BUTTON.isSubregion()</code>
     * returns false.
     *
     * @return true if the Region is a subregion of a Component.
     */
    public boolean isSubregion() {
        return subregion;
    }

    /**
     * Returns the name of the region.
     *
     * @return name of the Region.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the name, in lowercase.
     */
    String getLowerCaseName() {
        synchronized(lowerCaseNameMap) {
            String lowerCaseName = lowerCaseNameMap.get(this);
            if (lowerCaseName == null) {
                lowerCaseName = getName().toLowerCase();
                lowerCaseNameMap.put(this, lowerCaseName);
            }
            return lowerCaseName;
        }
    }

    /**
     * Returns the name of the Region.
     *
     * @return name of the Region.
     */
    public String toString() {
        return name;
    }
}
