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
package org.jdesktop.synthdesigner.synthmodel;

import org.jdesktop.beans.AbstractBean;
import org.jdesktop.swingx.designer.utils.HasResources;
import org.jdesktop.swingx.designer.utils.HasUIDefaults;
import org.jibx.runtime.IUnmarshallingContext;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDesktopPane;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JToolTip;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.UIDefaults;
import javax.swing.plaf.basic.BasicLookAndFeel;
import javax.swing.plaf.metal.MetalLookAndFeel;
import static javax.swing.plaf.synth.SynthConstants.*;
import javax.swing.table.JTableHeader;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Models a Synth look and feel. Contains all of the colors, fonts, painters, states, etc that compose a synth look and
 * feel.
 * <p/>
 * To model Synth properly, I need to both Model the way Synth works (with styles, and so forth) and the way a look and
 * feel works (UIDefaults table, etc) since both of these are supported ways of doing things in Synth.
 * <p/>
 * One important (but non-visual) thing that needs to be configurable is the support for InputMaps per component. In
 * Synth, an input map can be associated with the main Synth element, meaning it applies to everything. Or it can be
 * associated with a single style. An Inputmap can have an id, and it can contain multiple key/action pairs (where
 * actions are denoted by name).
 * <p/>
 * It looks like Regions can have InputMaps? Sounds fishy to me. I think only Components really have input maps.
 * <p/>
 * I would like some way of denoting special keys between mac and other platforms. For example, cut, copy, paste etc
 * should be different. In general, the ctrl key and apple (meta) key are reversed from what is typically on windows.
 *
 * @author  Richard Bair
 * @author  Jasper Potts
 */
public class SynthModel extends AbstractBean implements HasUIDefaults, HasUIStyle, HasResources {
    //I'm going to want:
    //entries related to the Control color, and other colors of the Basic LAF
    //entries related to fonts (standard fonts) used in the Basic LAF
    //entries related to standard insets, borders, dimensions, icons
    //entries related to component specific entries in the LAF
    private List<UIPaint> colors;
    private List<UIFont> fonts;
    private List<UIInsets> insets;
    private List<UIBorder> borders;
    private List<UIDimension> dimensions;
    private List<UIIcon> icons;
    private List<UIComponent> components;
    /**
     * This is a local UIDefaults that contains all the UIDefaults in this synth model. It is kept uptodate by the
     * indervidual UIDefaults nodes
     */
    private transient UIDefaults modelDefaults = new UIDefaults();
    private transient UIStyle globalStyle = new UIStyle();

    private transient File resourcesDir;
    private transient File imagesDir;
    private transient File templatesDir;

    /** Default constructor used by JIBX to create new empty SynthModel */
    protected SynthModel() {
        this(false);
    }

    public SynthModel(boolean populateWithDefaults) {
        // create observable lists that fire changes on as property changes
        colors = new ArrayList<UIPaint>();
        fonts = new ArrayList<UIFont>();
        insets = new ArrayList<UIInsets>();
        borders = new ArrayList<UIBorder>();
        dimensions = new ArrayList<UIDimension>();
        icons = new ArrayList<UIIcon>();
        components = new ArrayList<UIComponent>();

        if (populateWithDefaults) {
            //get the ui defaults from the SynthLookAndFeel. Using the UIDefaults table,
            //pre initialize everything.
//        SynthLookAndFeel synth = new SynthLookAndFeel();
            BasicLookAndFeel synth = new MetalLookAndFeel();
            UIDefaults defaults = synth.getDefaults();

            //pre-init the palettes
            colors.add(new UIColor("desktop", defaults.getColor("desktop"), modelDefaults));
            colors.add(new UIColor("activeCaption", defaults.getColor("activeCaption"), modelDefaults));
            colors.add(new UIColor("activeCaptionText", defaults.getColor("activeCaptionText"), modelDefaults));
            colors.add(new UIColor("activeCaptionBorder", defaults.getColor("activeCaptionBorder"), modelDefaults));
            colors.add(new UIColor("inactiveCaption", defaults.getColor("inactiveCaption"), modelDefaults));
            colors.add(new UIColor("inactiveCaptionText", defaults.getColor("inactiveCaptionText"), modelDefaults));
            colors.add(new UIColor("inactiveCaptionBorder", defaults.getColor("inactiveCaptionBorder"), modelDefaults));
            colors.add(new UIColor("window", defaults.getColor("window"), modelDefaults));
            colors.add(new UIColor("windowBorder", defaults.getColor("windowBorder"), modelDefaults));
            colors.add(new UIColor("windowText", defaults.getColor("windowText"), modelDefaults));
            colors.add(new UIColor("menu", defaults.getColor("menu"), modelDefaults));
            colors.add(new UIColor("menuText", defaults.getColor("menuText"), modelDefaults));
            colors.add(new UIColor("text", defaults.getColor("text"), modelDefaults));
            colors.add(new UIColor("textText", defaults.getColor("textText"), modelDefaults));
            colors.add(new UIColor("textHighlight", defaults.getColor("textHighlight"), modelDefaults));
            colors.add(new UIColor("textHighlightText", defaults.getColor("textHighlightText"), modelDefaults));
            colors.add(new UIColor("textInactiveText", defaults.getColor("textInactiveText"), modelDefaults));
            colors.add(new UIColor("control", defaults.getColor("control"), modelDefaults));
            colors.add(new UIColor("controlText", defaults.getColor("controlText"), modelDefaults));
            colors.add(new UIColor("controlHighlight", defaults.getColor("controlHighlight"), modelDefaults));
            colors.add(new UIColor("controlLHighlight", defaults.getColor("controlLHighlight"), modelDefaults));
            colors.add(new UIColor("controlShadow", defaults.getColor("controlShadow"), modelDefaults));
            colors.add(new UIColor("controlDkShadow", defaults.getColor("controlDkShadow"), modelDefaults));
            colors.add(new UIColor("scrollbar", defaults.getColor("scrollbar"), modelDefaults));
            colors.add(new UIColor("info", defaults.getColor("info"), modelDefaults));
            colors.add(new UIColor("infoText", defaults.getColor("infoText"), modelDefaults));

            fonts.add(new UIFont("dialogPlain", defaults.getFont("Button.font"), modelDefaults));
            fonts.add(new UIFont("serifPlain", defaults.getFont("TextPane.font"), modelDefaults));
            fonts.add(new UIFont("sansSerifPlain", defaults.getFont("ToolTip.font"), modelDefaults));
            fonts.add(new UIFont("monospacedPlain", defaults.getFont("TextArea.font"), modelDefaults));
            fonts.add(new UIFont("dialogBold", defaults.getFont("InternalFrame.titleFont"), modelDefaults));

            insets.add(new UIInsets("zeroInsets", new Insets(0, 0, 0, 0)));
            insets.add(new UIInsets("twoInsets", new Insets(2, 2, 2, 2)));
            insets.add(new UIInsets("threeInsets", new Insets(3, 3, 3, 3)));

            borders.add(new UIBorder("marginBorder", defaults.getBorder("MenuItem.border")));
            borders.add(new UIBorder("etchedBorder", defaults.getBorder("TitledBorder.border")));
            borders.add(new UIBorder("loweredBevelBorder", defaults.getBorder("Table.scrollPaneBorder")));
            borders.add(new UIBorder("blackLineBorder", defaults.getBorder("ToolTip.border")));

            //TODO have to deal with the special arrow button region

            //pre-init the list of UI components
            UIComponent button = new UIComponent("Button", JButton.class.getName(), "ButtonUI");
            addStates(this, button, DEFAULT, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(button);

            UIComponent toggleButton =
                    new UIComponent("ToggleButton", JToggleButton.class.getName(), "ToggleButtonUI");
            addStates(this, toggleButton, DEFAULT, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED, SELECTED,
                    SELECTED | PRESSED, SELECTED | MOUSE_OVER, DISABLED | SELECTED);
            components.add(toggleButton);

            UIComponent radioButton =
                    new UIComponent("RadioButton", JRadioButton.class.getName(), "RadioButtonUI");
            addStates(this, radioButton, DEFAULT, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED, SELECTED,
                    SELECTED | PRESSED, SELECTED | MOUSE_OVER, DISABLED | SELECTED);
            components.add(radioButton);

            UIComponent checkBox =
                    new UIComponent("CheckBox", JCheckBox.class.getName(), "CheckBoxUI");
            addStates(this, checkBox, DEFAULT, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED, SELECTED,
                    SELECTED | PRESSED, SELECTED | MOUSE_OVER, DISABLED | SELECTED);
            components.add(checkBox);

            UIComponent colorChooser =
                    new UIComponent("ColorChooser", JColorChooser.class.getName(), "ColorChooserUI");
            addStates(this, colorChooser, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(colorChooser);

            UIComponent comboBox =
                    new UIComponent("ComboBox", JComboBox.class.getName(), "ComboBoxUI");
            addStates(this, comboBox, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(comboBox);

            UIComponent fileChooser =
                    new UIComponent("FileChooser", JFileChooser.class.getName(), "FileChooserUI");
            addStates(this, fileChooser, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(fileChooser);

            //not represented in Synth
//        UIComponent  fileView = new UIComponent ("FileView",
//                list(DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);

            UIComponent internalFrame =
                    new UIComponent("InternalFrame", JInternalFrame.class.getName(), "InternalFrameUI");
            addStates(this, internalFrame, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            //has an internal frame title pane region
            components.add(internalFrame);

            //TODO DesktopIcon ???

            UIComponent desktop =
                    new UIComponent("Desktop", JDesktopPane.class.getName(), "DesktopPaneUI");
            addStates(this, desktop, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(desktop);

            UIComponent label = new UIComponent("Label", JLabel.class.getName(), "LabelUI");
            addStates(this, label, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(label);

            UIComponent list = new UIComponent("List", JList.class.getName(), "ListUI");
            addStates(this, list, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(list);

            UIComponent menuBar = new UIComponent("MenuBar", JMenuBar.class.getName(), "MenuBarUI");
            addStates(this, menuBar, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(menuBar);

            UIComponent menuItem =
                    new UIComponent("MenuItem", JMenuItem.class.getName(), "MenuItemUI");
            addStates(this, menuItem, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            //has a menuItemAccelerator region
            components.add(menuItem);

            UIComponent radioButtonMenuItem =
                    new UIComponent("RadioButtonMenuItem", JRadioButtonMenuItem.class.getName(),
                            "RadioButtonMenuItemUI");
            addStates(this, radioButtonMenuItem, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(radioButtonMenuItem);

            UIComponent checkBoxMenuItem =
                    new UIComponent("CheckBoxMenuItem", JCheckBoxMenuItem.class.getName(),
                            "CheckBoxMenuItemUI");
            addStates(this, checkBoxMenuItem, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(checkBoxMenuItem);

            UIComponent menu = new UIComponent("Menu", JMenu.class.getName(), "MenuUI");
            addStates(this, menu, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(menu);

            UIComponent popupMenu =
                    new UIComponent("PopupMenu", JPopupMenu.class.getName(), "PopupMenuUI");
            addStates(this, popupMenu, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            //has a popupMenuSeparator region
            components.add(popupMenu);

            UIComponent optionPane =
                    new UIComponent("OptionPane", JOptionPane.class.getName(), "OptionPaneUI");
            addStates(this, optionPane, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(optionPane);

            UIComponent panel = new UIComponent("Panel", JPanel.class.getName(), "PanelUI");
            addStates(this, panel, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(panel);

            UIComponent progressBar =
                    new UIComponent("ProgressBar", JProgressBar.class.getName(), "ProgressBarUI");
            addStates(this, progressBar, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(progressBar);

            UIComponent separator =
                    new UIComponent("Separator", JSeparator.class.getName(), "SeparatorUI");
            addStates(this, separator, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(separator);

            UIRegion scrollBarThumb = new UIRegion("ScrollBar.Thumb");
            addStates(this, scrollBarThumb, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            UIRegion scrollBarTrack = new UIRegion("ScrollBar.Track");
            addStates(this, scrollBarTrack, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            UIComponent scrollBar =
                    new UIComponent("ScrollBar", JScrollBar.class.getName(), "ScrollBarUI", scrollBarThumb,
                            scrollBarTrack);
            addStates(this, scrollBar, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(scrollBar);

            UIComponent scrollPane =
                    new UIComponent("ScrollPane", JScrollPane.class.getName(), "ScrollPaneUI");
            addStates(this, scrollPane, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(scrollPane);

            UIComponent viewport =
                    new UIComponent("Viewport", JViewport.class.getName(), "ViewportUI");
            addStates(this, viewport, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(viewport);

            UIComponent slider = new UIComponent("Slider", JSlider.class.getName(), "SliderUI");
            addStates(this, slider, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            //has sliderThumb and sliderTrack sub regions
            components.add(slider);

            UIComponent spinner = new UIComponent("Spinner", JSpinner.class.getName(), "SpinnerUI");
            addStates(this, spinner, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(spinner);

            UIComponent splitPane =
                    new UIComponent("SplitPane", JSplitPane.class.getName(), "SplitPaneUI");
            addStates(this, splitPane, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            //has splitPaneDivider sub region
            components.add(splitPane);

            UIComponent tabbedPane =
                    new UIComponent("TabbedPane", JTabbedPane.class.getName(), "TabbedPaneUI");
            addStates(this, tabbedPane, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            //has tabbedPaneContent and tabbedPaneTab and TabbedPaneTabArea sub regions
            components.add(tabbedPane);

            UIComponent table = new UIComponent("Table", JTable.class.getName(), "TableUI");
            addStates(this, table, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(table);

            UIComponent tableHeader =
                    new UIComponent("TableHeader", JTableHeader.class.getName(), "TableHeaderUI");
            addStates(this, tableHeader, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(tableHeader);

            UIComponent textField =
                    new UIComponent("TextField", JTextField.class.getName(), "TextFieldUI");
            addStates(this, textField, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(textField);

            UIComponent formattedTextField =
                    new UIComponent("FormattedTextField", JFormattedTextField.class.getName(),
                            "FormattedTextFieldUI");
            addStates(this, formattedTextField, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(formattedTextField);

            UIComponent passwordField =
                    new UIComponent("PasswordField", JPasswordField.class.getName(), "PasswordFieldUI");
            addStates(this, passwordField, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(passwordField);

            UIComponent textArea =
                    new UIComponent("TextArea", JTextArea.class.getName(), "TextAreaUI");
            addStates(this, textArea, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(textArea);

            UIComponent textPane =
                    new UIComponent("TextPane", JTextPane.class.getName(), "TextPaneUI");
            addStates(this, textPane, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(textPane);

            UIComponent editorPane =
                    new UIComponent("EditorPane", JEditorPane.class.getName(), "EditorPaneUI");
            addStates(this, editorPane, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(editorPane);

            /*
            * The only thing not represented in Synth as a region. I suppose we'll have
            * to make it a CustomUIComponent
            */
//        UIComponent  titledBorder = new UIComponent ("TitledBorder",
//                list(DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);

            UIComponent toolBar = new UIComponent("ToolBar", JToolBar.class.getName(), "ToolBarUI");
            addStates(this, toolBar, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            //toolBarContent, toolBarDragWindow, toolBarSeparator sub regions
            components.add(toolBar);

            UIComponent toolTip = new UIComponent("ToolTip", JToolTip.class.getName(), "ToolTipUI");
            addStates(this, toolTip, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(toolTip);

            //tooltip manager

            UIComponent tree = new UIComponent("Tree", JTree.class.getName(), "TreeUI");
            addStates(this, tree, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            //treeCell sub region
            components.add(tree);

            UIComponent rootPane =
                    new UIComponent("RootPane", JRootPane.class.getName(), "RootPaneUI");
            addStates(this, rootPane, DISABLED, ENABLED, FOCUSED, MOUSE_OVER, PRESSED);
            components.add(rootPane);
        }
    }

    public List<UIPaint> getColorPalette() {
        return colors;
    }

    public List<UIFont> getFontPalette() {
        return fonts;
    }

    public List<UIInsets> getInsetPalette() {
        return insets;
    }

    public List<UIBorder> getBorderPalette() {
        return borders;
    }

    public List<UIDimension> getDimensionPalette() {
        return dimensions;
    }

    public List<UIIcon> getIconPalette() {
        return icons;
    }

    public List<UIComponent> getComponents() {
        return components;
    }

    /**
     * Get the local UIDefaults that contains all the UIDefaults in this synth model. It is kept uptodate by the
     * indervidual UIDefaults nodes
     *
     * @return The UIDefaults for the synth model
     */
    public UIDefaults getUiDefaults() {
        return modelDefaults;
    }

    public UIStyle getStyle() {
        return globalStyle;
    }

    // by default there are no painters assigned to the various states
    private static void addStates(SynthModel model, UIRegion parentRegion, int... states) {
        for (int state : states) {
            List<String> stateList = new ArrayList<String>();
            if ((state & ENABLED) != 0) {
                stateList.add(UIStateType.ENABLED_KEY);
            }
            if ((state & MOUSE_OVER) != 0) {
                stateList.add(UIStateType.MOUSE_OVER_KEY);
            }
            if ((state & PRESSED) != 0) {
                stateList.add(UIStateType.PRESSED_KEY);
            }
            if ((state & DISABLED) != 0) {
                stateList.add(UIStateType.DISABLED_KEY);
            }
            if ((state & FOCUSED) != 0) {
                stateList.add(UIStateType.FOCUSED_KEY);
            }
            if ((state & SELECTED) != 0) {
                stateList.add(UIStateType.SELECTED_KEY);
            }
            if ((state & DEFAULT) != 0) {
                stateList.add(UIStateType.DEFAULT_KEY);
            }
            parentRegion.addBackgroundState(new UIState(model, parentRegion, stateList.toArray(new String[stateList.size()])));
        }
    }

    public File getResourcesDir() {
        return resourcesDir;
    }

    public void setResourcesDir(File resourcesDir) {
        System.out.println("SynthModel.setResourcesDir(" + resourcesDir + ")");
        File old = getResourcesDir();
        this.resourcesDir = resourcesDir;
        firePropertyChange("resourcesDir", old, getResourcesDir());
    }

    public File getImagesDir() {
        return imagesDir;
    }

    public void setImagesDir(File imagesDir) {
        System.out.println("SynthModel.setImagesDir(" + imagesDir + ")");
        File old = getImagesDir();
        this.imagesDir = imagesDir;
        firePropertyChange("imagesDir", old, getImagesDir());
    }

    public File getTemplatesDir() {
        return templatesDir;
    }

    public void setTemplatesDir(File templatesDir) {
        System.out.println("SynthModel.setTemplatesDir(" + templatesDir + ")");
        File old = getTemplatesDir();
        this.templatesDir = templatesDir;
        firePropertyChange("templatesDir", old, getTemplatesDir());
    }

    // =================================================================================================================
    // JIBX Methods

    public void preSet(IUnmarshallingContext context) {
        File resourcesDir = (File) context.getUserContext();
        this.resourcesDir = resourcesDir;
        this.imagesDir = new File(resourcesDir, "images");
        this.templatesDir = new File(resourcesDir, "templates");
    }
}
