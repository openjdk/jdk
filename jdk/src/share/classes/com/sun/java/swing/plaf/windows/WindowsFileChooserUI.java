/*
 * Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.java.swing.plaf.windows;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.*;
import javax.swing.event.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.beans.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import sun.awt.shell.ShellFolder;
import sun.awt.OSInfo;
import sun.swing.*;

import javax.accessibility.*;

/**
 * Windows L&F implementation of a FileChooser.
 *
 * @author Jeff Dinkins
 */
public class WindowsFileChooserUI extends BasicFileChooserUI {

    // The following are private because the implementation of the
    // Windows FileChooser L&F is not complete yet.

    private static final OSInfo.WindowsVersion OS_VERSION = OSInfo.getWindowsVersion();

    private JPanel centerPanel;

    private JLabel lookInLabel;
    private JComboBox directoryComboBox;
    private DirectoryComboBoxModel directoryComboBoxModel;
    private ActionListener directoryComboBoxAction = new DirectoryComboBoxAction();

    private FilterComboBoxModel filterComboBoxModel;

    private JTextField filenameTextField;
    private FilePane filePane;
    private WindowsPlacesBar placesBar;
    private boolean useShellFolder;

    private JButton approveButton;
    private JButton cancelButton;

    private JPanel buttonPanel;
    private JPanel bottomPanel;

    private JComboBox filterComboBox;

    private static final Dimension hstrut10 = new Dimension(10, 1);

    private static final Dimension vstrut4  = new Dimension(1, 4);
    private static final Dimension vstrut6  = new Dimension(1, 6);
    private static final Dimension vstrut8  = new Dimension(1, 8);

    private static final Insets shrinkwrap = new Insets(0,0,0,0);

    // Preferred and Minimum sizes for the dialog box
    private static int PREF_WIDTH = 425;
    private static int PREF_HEIGHT = 245;
    private static Dimension PREF_SIZE = new Dimension(PREF_WIDTH, PREF_HEIGHT);

    private static int MIN_WIDTH = 425;
    private static int MIN_HEIGHT = 245;
    private static Dimension MIN_SIZE = new Dimension(MIN_WIDTH, MIN_HEIGHT);

    private static int LIST_PREF_WIDTH = 444;
    private static int LIST_PREF_HEIGHT = 138;
    private static Dimension LIST_PREF_SIZE = new Dimension(LIST_PREF_WIDTH, LIST_PREF_HEIGHT);

    // Labels, mnemonics, and tooltips (oh my!)
    private int    lookInLabelMnemonic = 0;
    private String lookInLabelText = null;
    private String saveInLabelText = null;

    private int    fileNameLabelMnemonic = 0;
    private String fileNameLabelText = null;
    private int    folderNameLabelMnemonic = 0;
    private String folderNameLabelText = null;

    private int    filesOfTypeLabelMnemonic = 0;
    private String filesOfTypeLabelText = null;

    private String upFolderToolTipText = null;
    private String upFolderAccessibleName = null;

    private String homeFolderToolTipText = null;
    private String homeFolderAccessibleName = null;

    private String newFolderToolTipText = null;
    private String newFolderAccessibleName = null;

    private String listViewButtonToolTipText = null;
    private String listViewButtonAccessibleName = null;

    private String detailsViewButtonToolTipText = null;
    private String detailsViewButtonAccessibleName = null;

    private String viewMenuButtonToolTipText = null;
    private String viewMenuButtonAccessibleName = null;

    private BasicFileView fileView = new WindowsFileView();

    private JLabel fileNameLabel;

    private void populateFileNameLabel() {
        if (getFileChooser().getFileSelectionMode() == JFileChooser.DIRECTORIES_ONLY) {
            fileNameLabel.setText(folderNameLabelText);
            fileNameLabel.setDisplayedMnemonic(folderNameLabelMnemonic);
        } else {
            fileNameLabel.setText(fileNameLabelText);
            fileNameLabel.setDisplayedMnemonic(fileNameLabelMnemonic);
        }
    }

    //
    // ComponentUI Interface Implementation methods
    //
    public static ComponentUI createUI(JComponent c) {
        return new WindowsFileChooserUI((JFileChooser) c);
    }

    public WindowsFileChooserUI(JFileChooser filechooser) {
        super(filechooser);
    }

    public void installUI(JComponent c) {
        super.installUI(c);
    }

    public void uninstallComponents(JFileChooser fc) {
        fc.removeAll();
    }

    private class WindowsFileChooserUIAccessor implements FilePane.FileChooserUIAccessor {
        public JFileChooser getFileChooser() {
            return WindowsFileChooserUI.this.getFileChooser();
        }

        public BasicDirectoryModel getModel() {
            return WindowsFileChooserUI.this.getModel();
        }

        public JPanel createList() {
            return WindowsFileChooserUI.this.createList(getFileChooser());
        }

        public JPanel createDetailsView() {
            return WindowsFileChooserUI.this.createDetailsView(getFileChooser());
        }

        public boolean isDirectorySelected() {
            return WindowsFileChooserUI.this.isDirectorySelected();
        }

        public File getDirectory() {
            return WindowsFileChooserUI.this.getDirectory();
        }

        public Action getChangeToParentDirectoryAction() {
            return WindowsFileChooserUI.this.getChangeToParentDirectoryAction();
        }

        public Action getApproveSelectionAction() {
            return WindowsFileChooserUI.this.getApproveSelectionAction();
        }

        public Action getNewFolderAction() {
            return WindowsFileChooserUI.this.getNewFolderAction();
        }

        public MouseListener createDoubleClickListener(JList list) {
            return WindowsFileChooserUI.this.createDoubleClickListener(getFileChooser(),
                                                                       list);
        }

        public ListSelectionListener createListSelectionListener() {
            return WindowsFileChooserUI.this.createListSelectionListener(getFileChooser());
        }

        public boolean usesShellFolder() {
            return useShellFolder;
        }
    }

    public void installComponents(JFileChooser fc) {
        filePane = new FilePane(new WindowsFileChooserUIAccessor());
        fc.addPropertyChangeListener(filePane);

        FileSystemView fsv = fc.getFileSystemView();

        fc.setBorder(new EmptyBorder(4, 10, 10, 10));
        fc.setLayout(new BorderLayout(8, 8));

        updateUseShellFolder();

        // ********************************* //
        // **** Construct the top panel **** //
        // ********************************* //

        // Directory manipulation buttons
        JToolBar topPanel = new JToolBar();
        topPanel.setFloatable(false);
        if (OS_VERSION.compareTo(OSInfo.WINDOWS_ME) >= 0) {
            topPanel.putClientProperty("JToolBar.isRollover", Boolean.TRUE);
        }

        // Add the top panel to the fileChooser
        fc.add(topPanel, BorderLayout.NORTH);

        // ComboBox Label
        lookInLabel = new JLabel(lookInLabelText, JLabel.TRAILING) {
            public Dimension getPreferredSize() {
                return getMinimumSize();
            }

            public Dimension getMinimumSize() {
                Dimension d = super.getPreferredSize();
                if (placesBar != null) {
                    d.width = Math.max(d.width, placesBar.getWidth());
                }
                return d;
            }
        };
        lookInLabel.setDisplayedMnemonic(lookInLabelMnemonic);
        lookInLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        lookInLabel.setAlignmentY(JComponent.CENTER_ALIGNMENT);
        topPanel.add(lookInLabel);
        topPanel.add(Box.createRigidArea(new Dimension(8,0)));

        // CurrentDir ComboBox
        directoryComboBox = new JComboBox() {
            public Dimension getMinimumSize() {
                Dimension d = super.getMinimumSize();
                d.width = 60;
                return d;
            }

            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                // Must be small enough to not affect total width.
                d.width = 150;
                return d;
            }
        };
        directoryComboBox.putClientProperty( "JComboBox.lightweightKeyboardNavigation", "Lightweight" );
        lookInLabel.setLabelFor(directoryComboBox);
        directoryComboBoxModel = createDirectoryComboBoxModel(fc);
        directoryComboBox.setModel(directoryComboBoxModel);
        directoryComboBox.addActionListener(directoryComboBoxAction);
        directoryComboBox.setRenderer(createDirectoryComboBoxRenderer(fc));
        directoryComboBox.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        directoryComboBox.setAlignmentY(JComponent.CENTER_ALIGNMENT);
        directoryComboBox.setMaximumRowCount(8);

        topPanel.add(directoryComboBox);
        topPanel.add(Box.createRigidArea(hstrut10));

        // Up Button
        JButton upFolderButton = new JButton(getChangeToParentDirectoryAction());
        upFolderButton.setText(null);
        upFolderButton.setIcon(upFolderIcon);
        upFolderButton.setToolTipText(upFolderToolTipText);
        upFolderButton.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
                                         upFolderAccessibleName);
        upFolderButton.putClientProperty(WindowsLookAndFeel.HI_RES_DISABLED_ICON_CLIENT_KEY,
                                         Boolean.TRUE);
        upFolderButton.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        upFolderButton.setAlignmentY(JComponent.CENTER_ALIGNMENT);
        upFolderButton.setMargin(shrinkwrap);
        upFolderButton.setFocusPainted(false);
        topPanel.add(upFolderButton);
        if (OS_VERSION.compareTo(OSInfo.WINDOWS_ME) < 0) {
            topPanel.add(Box.createRigidArea(hstrut10));
        }

        JButton b;

        if (OS_VERSION == OSInfo.WINDOWS_98) {
            // Desktop Button
            File homeDir = fsv.getHomeDirectory();
            String toolTipText = homeFolderToolTipText;
            if (fsv.isRoot(homeDir)) {
                toolTipText = getFileView(fc).getName(homeDir); // Probably "Desktop".
            }
            b = new JButton(getFileView(fc).getIcon(homeDir));
            b.setToolTipText(toolTipText);
            b.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
                                toolTipText);
            b.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            b.setAlignmentY(JComponent.CENTER_ALIGNMENT);
            b.setMargin(shrinkwrap);
            b.setFocusPainted(false);
            b.addActionListener(getGoHomeAction());
            topPanel.add(b);
            topPanel.add(Box.createRigidArea(hstrut10));
        }

        // New Directory Button
        if (!UIManager.getBoolean("FileChooser.readOnly")) {
            b = new JButton(filePane.getNewFolderAction());
            b.setText(null);
            b.setIcon(newFolderIcon);
            b.setToolTipText(newFolderToolTipText);
            b.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
                                newFolderAccessibleName);
            b.putClientProperty(WindowsLookAndFeel.HI_RES_DISABLED_ICON_CLIENT_KEY,
                                Boolean.TRUE);
            b.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            b.setAlignmentY(JComponent.CENTER_ALIGNMENT);
            b.setMargin(shrinkwrap);
            b.setFocusPainted(false);
            topPanel.add(b);
        }
        if (OS_VERSION.compareTo(OSInfo.WINDOWS_ME) < 0) {
            topPanel.add(Box.createRigidArea(hstrut10));

            // View button group
            ButtonGroup viewButtonGroup = new ButtonGroup();

            // List Button
            final JToggleButton listViewButton = new JToggleButton(listViewIcon);
            listViewButton.setToolTipText(listViewButtonToolTipText);
            listViewButton.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
                    listViewButtonAccessibleName);
            listViewButton.putClientProperty(WindowsLookAndFeel.HI_RES_DISABLED_ICON_CLIENT_KEY,
                    Boolean.TRUE);
            listViewButton.setFocusPainted(false);
            listViewButton.setSelected(true);
            listViewButton.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            listViewButton.setAlignmentY(JComponent.CENTER_ALIGNMENT);
            listViewButton.setMargin(shrinkwrap);
            listViewButton.addActionListener(filePane.getViewTypeAction(FilePane.VIEWTYPE_LIST));
            topPanel.add(listViewButton);
            viewButtonGroup.add(listViewButton);

            // Details Button
            final JToggleButton detailsViewButton = new JToggleButton(detailsViewIcon);
            detailsViewButton.setToolTipText(detailsViewButtonToolTipText);
            detailsViewButton.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
                    detailsViewButtonAccessibleName);
            detailsViewButton.putClientProperty(WindowsLookAndFeel.HI_RES_DISABLED_ICON_CLIENT_KEY,
                    Boolean.TRUE);
            detailsViewButton.setFocusPainted(false);
            detailsViewButton.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            detailsViewButton.setAlignmentY(JComponent.CENTER_ALIGNMENT);
            detailsViewButton.setMargin(shrinkwrap);
            detailsViewButton.addActionListener(filePane.getViewTypeAction(FilePane.VIEWTYPE_DETAILS));
            topPanel.add(detailsViewButton);
            viewButtonGroup.add(detailsViewButton);

            topPanel.add(Box.createRigidArea(new Dimension(60, 0)));

            filePane.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent e) {
                    if ("viewType".equals(e.getPropertyName())) {
                        int viewType = filePane.getViewType();

                        switch (viewType) {
                            case FilePane.VIEWTYPE_LIST:
                                listViewButton.setSelected(true);
                                break;

                            case FilePane.VIEWTYPE_DETAILS:
                                detailsViewButton.setSelected(true);
                                break;
                        }
                    }
                }
            });
        } else { // After Windows Me
            // View button group
            ButtonGroup viewButtonGroup = new ButtonGroup();

            // Popup Menu
            final JPopupMenu viewTypePopupMenu = new JPopupMenu();

            final JRadioButtonMenuItem listViewMenuItem = new JRadioButtonMenuItem(
                    filePane.getViewTypeAction(FilePane.VIEWTYPE_LIST));
            listViewMenuItem.setSelected(filePane.getViewType() == FilePane.VIEWTYPE_LIST);
            viewTypePopupMenu.add(listViewMenuItem);
            viewButtonGroup.add(listViewMenuItem);

            final JRadioButtonMenuItem detailsViewMenuItem = new JRadioButtonMenuItem(
                    filePane.getViewTypeAction(FilePane.VIEWTYPE_DETAILS));
            detailsViewMenuItem.setSelected(filePane.getViewType() == FilePane.VIEWTYPE_DETAILS);
            viewTypePopupMenu.add(detailsViewMenuItem);
            viewButtonGroup.add(detailsViewMenuItem);

            // Create icon for viewMenuButton
            BufferedImage image = new BufferedImage(viewMenuIcon.getIconWidth() + 7, viewMenuIcon.getIconHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics graphics = image.getGraphics();
            viewMenuIcon.paintIcon(filePane, graphics, 0, 0);
            int x = image.getWidth() - 5;
            int y = image.getHeight() / 2 - 1;
            graphics.setColor(Color.BLACK);
            graphics.fillPolygon(new int[]{x, x + 5, x + 2}, new int[]{y, y, y + 3}, 3);

            // Details Button
            final JButton viewMenuButton = new JButton(new ImageIcon(image));
            viewMenuButton.setToolTipText(viewMenuButtonToolTipText);
            viewMenuButton.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, viewMenuButtonAccessibleName);
            viewMenuButton.putClientProperty(WindowsLookAndFeel.HI_RES_DISABLED_ICON_CLIENT_KEY, Boolean.TRUE);
            viewMenuButton.setFocusable(false);
            viewMenuButton.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            viewMenuButton.setAlignmentY(JComponent.CENTER_ALIGNMENT);
            viewMenuButton.setMargin(shrinkwrap);
            viewMenuButton.setModel(new DefaultButtonModel() {
                public void setPressed(boolean b) {
                }

                public void setArmed(boolean b) {
                }

                public void setSelected(boolean b) {
                    super.setSelected(b);

                    if (b) {
                        stateMask |= PRESSED | ARMED;
                    } else {
                        stateMask &= ~(PRESSED | ARMED);
                    }
                }
            });
            viewMenuButton.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        viewMenuButton.setSelected(!viewMenuButton.isSelected());

                        if (viewMenuButton.isSelected()) {
                            viewTypePopupMenu.show(viewMenuButton, 0, viewMenuButton.getHeight());
                        }
                    }
                }
            });
            viewTypePopupMenu.addPopupMenuListener(new PopupMenuListener() {
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                }

                public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            viewMenuButton.setSelected(false);
                        }
                    });
                }

                public void popupMenuCanceled(PopupMenuEvent e) {
                }
            });
            topPanel.add(viewMenuButton);

            topPanel.add(Box.createRigidArea(new Dimension(80, 0)));

            filePane.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent e) {
                    if ("viewType".equals(e.getPropertyName())) {
                        switch (filePane.getViewType()) {
                            case FilePane.VIEWTYPE_LIST:
                                listViewMenuItem.setSelected(true);
                                break;

                            case FilePane.VIEWTYPE_DETAILS:
                                detailsViewMenuItem.setSelected(true);
                                break;
                        }
                    }
                }
            });
        }

        // ************************************** //
        // ******* Add the directory pane ******* //
        // ************************************** //
        centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(getAccessoryPanel(), BorderLayout.AFTER_LINE_ENDS);
        JComponent accessory = fc.getAccessory();
        if(accessory != null) {
            getAccessoryPanel().add(accessory);
        }
        filePane.setPreferredSize(LIST_PREF_SIZE);
        centerPanel.add(filePane, BorderLayout.CENTER);
        fc.add(centerPanel, BorderLayout.CENTER);

        // ********************************** //
        // **** Construct the bottom panel ** //
        // ********************************** //
        getBottomPanel().setLayout(new BoxLayout(getBottomPanel(), BoxLayout.LINE_AXIS));

        // Add the bottom panel to file chooser
        centerPanel.add(getBottomPanel(), BorderLayout.SOUTH);

        // labels
        JPanel labelPanel = new JPanel();
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.PAGE_AXIS));
        labelPanel.add(Box.createRigidArea(vstrut4));

        fileNameLabel = new JLabel();
        populateFileNameLabel();
        fileNameLabel.setAlignmentY(0);
        labelPanel.add(fileNameLabel);

        labelPanel.add(Box.createRigidArea(new Dimension(1,12)));

        JLabel ftl = new JLabel(filesOfTypeLabelText);
        ftl.setDisplayedMnemonic(filesOfTypeLabelMnemonic);
        labelPanel.add(ftl);

        getBottomPanel().add(labelPanel);
        getBottomPanel().add(Box.createRigidArea(new Dimension(15, 0)));

        // file entry and filters
        JPanel fileAndFilterPanel = new JPanel();
        fileAndFilterPanel.add(Box.createRigidArea(vstrut8));
        fileAndFilterPanel.setLayout(new BoxLayout(fileAndFilterPanel, BoxLayout.Y_AXIS));


        filenameTextField = new JTextField(35) {
            public Dimension getMaximumSize() {
                return new Dimension(Short.MAX_VALUE, super.getPreferredSize().height);
            }
        };

        fileNameLabel.setLabelFor(filenameTextField);
        filenameTextField.addFocusListener(
            new FocusAdapter() {
                public void focusGained(FocusEvent e) {
                    if (!getFileChooser().isMultiSelectionEnabled()) {
                        filePane.clearSelection();
                    }
                }
            }
        );

        if (fc.isMultiSelectionEnabled()) {
            setFileName(fileNameString(fc.getSelectedFiles()));
        } else {
            setFileName(fileNameString(fc.getSelectedFile()));
        }

        fileAndFilterPanel.add(filenameTextField);
        fileAndFilterPanel.add(Box.createRigidArea(vstrut8));

        filterComboBoxModel = createFilterComboBoxModel();
        fc.addPropertyChangeListener(filterComboBoxModel);
        filterComboBox = new JComboBox(filterComboBoxModel);
        ftl.setLabelFor(filterComboBox);
        filterComboBox.setRenderer(createFilterComboBoxRenderer());
        fileAndFilterPanel.add(filterComboBox);

        getBottomPanel().add(fileAndFilterPanel);
        getBottomPanel().add(Box.createRigidArea(new Dimension(30, 0)));

        // buttons
        getButtonPanel().setLayout(new BoxLayout(getButtonPanel(), BoxLayout.Y_AXIS));

        approveButton = new JButton(getApproveButtonText(fc)) {
            public Dimension getMaximumSize() {
                return approveButton.getPreferredSize().width > cancelButton.getPreferredSize().width ?
                       approveButton.getPreferredSize() : cancelButton.getPreferredSize();
            }
        };
        Insets buttonMargin = approveButton.getMargin();
        buttonMargin = new InsetsUIResource(buttonMargin.top,    buttonMargin.left  + 5,
                                            buttonMargin.bottom, buttonMargin.right + 5);
        approveButton.setMargin(buttonMargin);
        approveButton.setMnemonic(getApproveButtonMnemonic(fc));
        approveButton.addActionListener(getApproveSelectionAction());
        approveButton.setToolTipText(getApproveButtonToolTipText(fc));
        getButtonPanel().add(Box.createRigidArea(vstrut6));
        getButtonPanel().add(approveButton);
        getButtonPanel().add(Box.createRigidArea(vstrut4));

        cancelButton = new JButton(cancelButtonText) {
            public Dimension getMaximumSize() {
                return approveButton.getPreferredSize().width > cancelButton.getPreferredSize().width ?
                       approveButton.getPreferredSize() : cancelButton.getPreferredSize();
            }
        };
        cancelButton.setMargin(buttonMargin);
        cancelButton.setToolTipText(cancelButtonToolTipText);
        cancelButton.addActionListener(getCancelSelectionAction());
        getButtonPanel().add(cancelButton);

        if(fc.getControlButtonsAreShown()) {
            addControlButtons();
        }
    }

    private void updateUseShellFolder() {
        // Decide whether to use the ShellFolder class to populate shortcut
        // panel and combobox.
        JFileChooser fc = getFileChooser();
        Boolean prop =
            (Boolean)fc.getClientProperty("FileChooser.useShellFolder");
        if (prop != null) {
            useShellFolder = prop.booleanValue();
        } else {
            useShellFolder = fc.getFileSystemView().equals(FileSystemView.getFileSystemView());
        }
        if (OS_VERSION.compareTo(OSInfo.WINDOWS_ME) >= 0) {
            if (useShellFolder) {
                if (placesBar == null && !UIManager.getBoolean("FileChooser.noPlacesBar")) {
                    placesBar = new WindowsPlacesBar(fc, XPStyle.getXP() != null);
                    fc.add(placesBar, BorderLayout.BEFORE_LINE_BEGINS);
                    fc.addPropertyChangeListener(placesBar);
                }
            } else {
                if (placesBar != null) {
                    fc.remove(placesBar);
                    fc.removePropertyChangeListener(placesBar);
                    placesBar = null;
                }
            }
        }
    }

    protected JPanel getButtonPanel() {
        if(buttonPanel == null) {
            buttonPanel = new JPanel();
        }
        return buttonPanel;
    }

    protected JPanel getBottomPanel() {
        if(bottomPanel == null) {
            bottomPanel = new JPanel();
        }
        return bottomPanel;
    }

    protected void installStrings(JFileChooser fc) {
        super.installStrings(fc);

        Locale l = fc.getLocale();

        lookInLabelMnemonic = UIManager.getInt("FileChooser.lookInLabelMnemonic");
        lookInLabelText = UIManager.getString("FileChooser.lookInLabelText",l);
        saveInLabelText = UIManager.getString("FileChooser.saveInLabelText",l);

        fileNameLabelMnemonic = UIManager.getInt("FileChooser.fileNameLabelMnemonic");
        fileNameLabelText = UIManager.getString("FileChooser.fileNameLabelText",l);
        folderNameLabelMnemonic = UIManager.getInt("FileChooser.folderNameLabelMnemonic");
        folderNameLabelText = UIManager.getString("FileChooser.folderNameLabelText",l);

        filesOfTypeLabelMnemonic = UIManager.getInt("FileChooser.filesOfTypeLabelMnemonic");
        filesOfTypeLabelText = UIManager.getString("FileChooser.filesOfTypeLabelText",l);

        upFolderToolTipText =  UIManager.getString("FileChooser.upFolderToolTipText",l);
        upFolderAccessibleName = UIManager.getString("FileChooser.upFolderAccessibleName",l);

        homeFolderToolTipText =  UIManager.getString("FileChooser.homeFolderToolTipText",l);
        homeFolderAccessibleName = UIManager.getString("FileChooser.homeFolderAccessibleName",l);

        newFolderToolTipText = UIManager.getString("FileChooser.newFolderToolTipText",l);
        newFolderAccessibleName = UIManager.getString("FileChooser.newFolderAccessibleName",l);

        listViewButtonToolTipText = UIManager.getString("FileChooser.listViewButtonToolTipText",l);
        listViewButtonAccessibleName = UIManager.getString("FileChooser.listViewButtonAccessibleName",l);

        detailsViewButtonToolTipText = UIManager.getString("FileChooser.detailsViewButtonToolTipText",l);
        detailsViewButtonAccessibleName = UIManager.getString("FileChooser.detailsViewButtonAccessibleName",l);

        viewMenuButtonToolTipText = UIManager.getString("FileChooser.viewMenuButtonToolTipText",l);
        viewMenuButtonAccessibleName = UIManager.getString("FileChooser.viewMenuButtonAccessibleName",l);
    }

    protected void installListeners(JFileChooser fc) {
        super.installListeners(fc);
        ActionMap actionMap = getActionMap();
        SwingUtilities.replaceUIActionMap(fc, actionMap);
    }

    protected ActionMap getActionMap() {
        return createActionMap();
    }

    protected ActionMap createActionMap() {
        ActionMap map = new ActionMapUIResource();
        FilePane.addActionsToMap(map, filePane.getActions());
        return map;
    }

    protected JPanel createList(JFileChooser fc) {
        return filePane.createList();
    }

    protected JPanel createDetailsView(JFileChooser fc) {
        return filePane.createDetailsView();
    }

    /**
     * Creates a selection listener for the list of files and directories.
     *
     * @param fc a <code>JFileChooser</code>
     * @return a <code>ListSelectionListener</code>
     */
    public ListSelectionListener createListSelectionListener(JFileChooser fc) {
        return super.createListSelectionListener(fc);
    }

    // Obsolete class, not used in this version.
    protected class WindowsNewFolderAction extends NewFolderAction {
    }

    // Obsolete class, not used in this version.
    protected class SingleClickListener extends MouseAdapter {
    }

    // Obsolete class, not used in this version.
    protected class FileRenderer extends DefaultListCellRenderer  {
    }

    public void uninstallUI(JComponent c) {
        // Remove listeners
        c.removePropertyChangeListener(filterComboBoxModel);
        c.removePropertyChangeListener(filePane);
        if (placesBar != null) {
            c.removePropertyChangeListener(placesBar);
        }
        cancelButton.removeActionListener(getCancelSelectionAction());
        approveButton.removeActionListener(getApproveSelectionAction());
        filenameTextField.removeActionListener(getApproveSelectionAction());

        if (filePane != null) {
            filePane.uninstallUI();
            filePane = null;
        }

        super.uninstallUI(c);
    }

    /**
     * Returns the preferred size of the specified
     * <code>JFileChooser</code>.
     * The preferred size is at least as large,
     * in both height and width,
     * as the preferred size recommended
     * by the file chooser's layout manager.
     *
     * @param c  a <code>JFileChooser</code>
     * @return   a <code>Dimension</code> specifying the preferred
     *           width and height of the file chooser
     */
    public Dimension getPreferredSize(JComponent c) {
        int prefWidth = PREF_SIZE.width;
        Dimension d = c.getLayout().preferredLayoutSize(c);
        if (d != null) {
            return new Dimension(d.width < prefWidth ? prefWidth : d.width,
                                 d.height < PREF_SIZE.height ? PREF_SIZE.height : d.height);
        } else {
            return new Dimension(prefWidth, PREF_SIZE.height);
        }
    }

    /**
     * Returns the minimum size of the <code>JFileChooser</code>.
     *
     * @param c  a <code>JFileChooser</code>
     * @return   a <code>Dimension</code> specifying the minimum
     *           width and height of the file chooser
     */
    public Dimension getMinimumSize(JComponent c) {
        return MIN_SIZE;
    }

    /**
     * Returns the maximum size of the <code>JFileChooser</code>.
     *
     * @param c  a <code>JFileChooser</code>
     * @return   a <code>Dimension</code> specifying the maximum
     *           width and height of the file chooser
     */
    public Dimension getMaximumSize(JComponent c) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private String fileNameString(File file) {
        if (file == null) {
            return null;
        } else {
            JFileChooser fc = getFileChooser();
            if ((fc.isDirectorySelectionEnabled() && !fc.isFileSelectionEnabled()) ||
                (fc.isDirectorySelectionEnabled() && fc.isFileSelectionEnabled() && fc.getFileSystemView().isFileSystemRoot(file))){
                return file.getPath();
            } else {
                return file.getName();
            }
        }
    }

    private String fileNameString(File[] files) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; files != null && i < files.length; i++) {
            if (i > 0) {
                buf.append(" ");
            }
            if (files.length > 1) {
                buf.append("\"");
            }
            buf.append(fileNameString(files[i]));
            if (files.length > 1) {
                buf.append("\"");
            }
        }
        return buf.toString();
    }

    /* The following methods are used by the PropertyChange Listener */

    private void doSelectedFileChanged(PropertyChangeEvent e) {
        File f = (File) e.getNewValue();
        JFileChooser fc = getFileChooser();
        if (f != null
            && ((fc.isFileSelectionEnabled() && !f.isDirectory())
                || (f.isDirectory() && fc.isDirectorySelectionEnabled()))) {

            setFileName(fileNameString(f));
        }
    }

    private void doSelectedFilesChanged(PropertyChangeEvent e) {
        File[] files = (File[]) e.getNewValue();
        JFileChooser fc = getFileChooser();
        if (files != null
            && files.length > 0
            && (files.length > 1 || fc.isDirectorySelectionEnabled() || !files[0].isDirectory())) {
            setFileName(fileNameString(files));
        }
    }

    private void doDirectoryChanged(PropertyChangeEvent e) {
        JFileChooser fc = getFileChooser();
        FileSystemView fsv = fc.getFileSystemView();

        clearIconCache();
        File currentDirectory = fc.getCurrentDirectory();
        if(currentDirectory != null) {
            directoryComboBoxModel.addItem(currentDirectory);

            if (fc.isDirectorySelectionEnabled() && !fc.isFileSelectionEnabled()) {
                if (fsv.isFileSystem(currentDirectory)) {
                    setFileName(currentDirectory.getPath());
                } else {
                    setFileName(null);
                }
            }
        }
    }

    private void doFilterChanged(PropertyChangeEvent e) {
        clearIconCache();
    }

    private void doFileSelectionModeChanged(PropertyChangeEvent e) {
        if (fileNameLabel != null) {
            populateFileNameLabel();
        }
        clearIconCache();

        JFileChooser fc = getFileChooser();
        File currentDirectory = fc.getCurrentDirectory();
        if (currentDirectory != null
            && fc.isDirectorySelectionEnabled()
            && !fc.isFileSelectionEnabled()
            && fc.getFileSystemView().isFileSystem(currentDirectory)) {

            setFileName(currentDirectory.getPath());
        } else {
            setFileName(null);
        }
    }

    private void doAccessoryChanged(PropertyChangeEvent e) {
        if(getAccessoryPanel() != null) {
            if(e.getOldValue() != null) {
                getAccessoryPanel().remove((JComponent) e.getOldValue());
            }
            JComponent accessory = (JComponent) e.getNewValue();
            if(accessory != null) {
                getAccessoryPanel().add(accessory, BorderLayout.CENTER);
            }
        }
    }

    private void doApproveButtonTextChanged(PropertyChangeEvent e) {
        JFileChooser chooser = getFileChooser();
        approveButton.setText(getApproveButtonText(chooser));
        approveButton.setToolTipText(getApproveButtonToolTipText(chooser));
        approveButton.setMnemonic(getApproveButtonMnemonic(chooser));
    }

    private void doDialogTypeChanged(PropertyChangeEvent e) {
        JFileChooser chooser = getFileChooser();
        approveButton.setText(getApproveButtonText(chooser));
        approveButton.setToolTipText(getApproveButtonToolTipText(chooser));
        approveButton.setMnemonic(getApproveButtonMnemonic(chooser));
        if (chooser.getDialogType() == JFileChooser.SAVE_DIALOG) {
            lookInLabel.setText(saveInLabelText);
        } else {
            lookInLabel.setText(lookInLabelText);
        }
    }

    private void doApproveButtonMnemonicChanged(PropertyChangeEvent e) {
        approveButton.setMnemonic(getApproveButtonMnemonic(getFileChooser()));
    }

    private void doControlButtonsChanged(PropertyChangeEvent e) {
        if(getFileChooser().getControlButtonsAreShown()) {
            addControlButtons();
        } else {
            removeControlButtons();
        }
    }

    /*
     * Listen for filechooser property changes, such as
     * the selected file changing, or the type of the dialog changing.
     */
    public PropertyChangeListener createPropertyChangeListener(JFileChooser fc) {
        return new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                String s = e.getPropertyName();
                if(s.equals(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY)) {
                    doSelectedFileChanged(e);
                } else if (s.equals(JFileChooser.SELECTED_FILES_CHANGED_PROPERTY)) {
                    doSelectedFilesChanged(e);
                } else if(s.equals(JFileChooser.DIRECTORY_CHANGED_PROPERTY)) {
                    doDirectoryChanged(e);
                } else if(s.equals(JFileChooser.FILE_FILTER_CHANGED_PROPERTY)) {
                    doFilterChanged(e);
                } else if(s.equals(JFileChooser.FILE_SELECTION_MODE_CHANGED_PROPERTY)) {
                    doFileSelectionModeChanged(e);
                } else if(s.equals(JFileChooser.ACCESSORY_CHANGED_PROPERTY)) {
                    doAccessoryChanged(e);
                } else if (s.equals(JFileChooser.APPROVE_BUTTON_TEXT_CHANGED_PROPERTY) ||
                           s.equals(JFileChooser.APPROVE_BUTTON_TOOL_TIP_TEXT_CHANGED_PROPERTY)) {
                    doApproveButtonTextChanged(e);
                } else if(s.equals(JFileChooser.DIALOG_TYPE_CHANGED_PROPERTY)) {
                    doDialogTypeChanged(e);
                } else if(s.equals(JFileChooser.APPROVE_BUTTON_MNEMONIC_CHANGED_PROPERTY)) {
                    doApproveButtonMnemonicChanged(e);
                } else if(s.equals(JFileChooser.CONTROL_BUTTONS_ARE_SHOWN_CHANGED_PROPERTY)) {
                    doControlButtonsChanged(e);
                } else if (s == "FileChooser.useShellFolder") {
                    updateUseShellFolder();
                    doDirectoryChanged(e);
                } else if (s.equals("componentOrientation")) {
                    ComponentOrientation o = (ComponentOrientation)e.getNewValue();
                    JFileChooser cc = (JFileChooser)e.getSource();
                    if (o != e.getOldValue()) {
                        cc.applyComponentOrientation(o);
                    }
                } else if (s.equals("ancestor")) {
                    if (e.getOldValue() == null && e.getNewValue() != null) {
                        // Ancestor was added, set initial focus
                        filenameTextField.selectAll();
                        filenameTextField.requestFocus();
                    }
                }
            }
        };
    }


    protected void removeControlButtons() {
        getBottomPanel().remove(getButtonPanel());
    }

    protected void addControlButtons() {
        getBottomPanel().add(getButtonPanel());
    }

    public void ensureFileIsVisible(JFileChooser fc, File f) {
        filePane.ensureFileIsVisible(fc, f);
    }

    public void rescanCurrentDirectory(JFileChooser fc) {
        filePane.rescanCurrentDirectory();
    }

    public String getFileName() {
        if(filenameTextField != null) {
            return filenameTextField.getText();
        } else {
            return null;
        }
    }

    public void setFileName(String filename) {
        if(filenameTextField != null) {
            filenameTextField.setText(filename);
        }
    }

    /**
     * Property to remember whether a directory is currently selected in the UI.
     * This is normally called by the UI on a selection event.
     *
     * @param directorySelected if a directory is currently selected.
     * @since 1.4
     */
    protected void setDirectorySelected(boolean directorySelected) {
        super.setDirectorySelected(directorySelected);
        JFileChooser chooser = getFileChooser();
        if(directorySelected) {
            approveButton.setText(directoryOpenButtonText);
            approveButton.setToolTipText(directoryOpenButtonToolTipText);
            approveButton.setMnemonic(directoryOpenButtonMnemonic);
        } else {
            approveButton.setText(getApproveButtonText(chooser));
            approveButton.setToolTipText(getApproveButtonToolTipText(chooser));
            approveButton.setMnemonic(getApproveButtonMnemonic(chooser));
        }
    }

    public String getDirectoryName() {
        // PENDING(jeff) - get the name from the directory combobox
        return null;
    }

    public void setDirectoryName(String dirname) {
        // PENDING(jeff) - set the name in the directory combobox
    }

    protected DirectoryComboBoxRenderer createDirectoryComboBoxRenderer(JFileChooser fc) {
        return new DirectoryComboBoxRenderer();
    }

    //
    // Renderer for DirectoryComboBox
    //
    class DirectoryComboBoxRenderer extends DefaultListCellRenderer  {
        IndentIcon ii = new IndentIcon();
        public Component getListCellRendererComponent(JList list, Object value,
                                                      int index, boolean isSelected,
                                                      boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value == null) {
                setText("");
                return this;
            }
            File directory = (File)value;
            setText(getFileChooser().getName(directory));
            Icon icon = getFileChooser().getIcon(directory);
            ii.icon = icon;
            ii.depth = directoryComboBoxModel.getDepth(index);
            setIcon(ii);

            return this;
        }
    }

    final static int space = 10;
    class IndentIcon implements Icon {

        Icon icon = null;
        int depth = 0;

        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (c.getComponentOrientation().isLeftToRight()) {
                icon.paintIcon(c, g, x+depth*space, y);
            } else {
                icon.paintIcon(c, g, x, y);
            }
        }

        public int getIconWidth() {
            return icon.getIconWidth() + depth*space;
        }

        public int getIconHeight() {
            return icon.getIconHeight();
        }

    }

    //
    // DataModel for DirectoryComboxbox
    //
    protected DirectoryComboBoxModel createDirectoryComboBoxModel(JFileChooser fc) {
        return new DirectoryComboBoxModel();
    }

    /**
     * Data model for a type-face selection combo-box.
     */
    protected class DirectoryComboBoxModel extends AbstractListModel implements ComboBoxModel {
        Vector<File> directories = new Vector<File>();
        int[] depths = null;
        File selectedDirectory = null;
        JFileChooser chooser = getFileChooser();
        FileSystemView fsv = chooser.getFileSystemView();

        public DirectoryComboBoxModel() {
            // Add the current directory to the model, and make it the
            // selectedDirectory
            File dir = getFileChooser().getCurrentDirectory();
            if(dir != null) {
                addItem(dir);
            }
        }

        /**
         * Adds the directory to the model and sets it to be selected,
         * additionally clears out the previous selected directory and
         * the paths leading up to it, if any.
         */
        private void addItem(File directory) {

            if(directory == null) {
                return;
            }

            directories.clear();

            File[] baseFolders;
            if (useShellFolder) {
                baseFolders = (File[])ShellFolder.get("fileChooserComboBoxFolders");
            } else {
                baseFolders = fsv.getRoots();
            }
            directories.addAll(Arrays.asList(baseFolders));

            // Get the canonical (full) path. This has the side
            // benefit of removing extraneous chars from the path,
            // for example /foo/bar/ becomes /foo/bar
            File canonical;
            try {
                canonical = directory.getCanonicalFile();
            } catch (IOException e) {
                // Maybe drive is not ready. Can't abort here.
                canonical = directory;
            }

            // create File instances of each directory leading up to the top
            try {
                File sf = useShellFolder ? ShellFolder.getShellFolder(canonical)
                                         : canonical;
                File f = sf;
                Vector<File> path = new Vector<File>(10);
                do {
                    path.addElement(f);
                } while ((f = f.getParentFile()) != null);

                int pathCount = path.size();
                // Insert chain at appropriate place in vector
                for (int i = 0; i < pathCount; i++) {
                    f = path.get(i);
                    if (directories.contains(f)) {
                        int topIndex = directories.indexOf(f);
                        for (int j = i-1; j >= 0; j--) {
                            directories.insertElementAt(path.get(j), topIndex+i-j);
                        }
                        break;
                    }
                }
                calculateDepths();
                setSelectedItem(sf);
            } catch (FileNotFoundException ex) {
                calculateDepths();
            }
        }

        private void calculateDepths() {
            depths = new int[directories.size()];
            for (int i = 0; i < depths.length; i++) {
                File dir = directories.get(i);
                File parent = dir.getParentFile();
                depths[i] = 0;
                if (parent != null) {
                    for (int j = i-1; j >= 0; j--) {
                        if (parent.equals(directories.get(j))) {
                            depths[i] = depths[j] + 1;
                            break;
                        }
                    }
                }
            }
        }

        public int getDepth(int i) {
            return (depths != null && i >= 0 && i < depths.length) ? depths[i] : 0;
        }

        public void setSelectedItem(Object selectedDirectory) {
            this.selectedDirectory = (File)selectedDirectory;
            fireContentsChanged(this, -1, -1);
        }

        public Object getSelectedItem() {
            return selectedDirectory;
        }

        public int getSize() {
            return directories.size();
        }

        public Object getElementAt(int index) {
            return directories.elementAt(index);
        }
    }

    //
    // Renderer for Types ComboBox
    //
    protected FilterComboBoxRenderer createFilterComboBoxRenderer() {
        return new FilterComboBoxRenderer();
    }

    /**
     * Render different type sizes and styles.
     */
    public class FilterComboBoxRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList list,
            Object value, int index, boolean isSelected,
            boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value != null && value instanceof FileFilter) {
                setText(((FileFilter)value).getDescription());
            }

            return this;
        }
    }

    //
    // DataModel for Types Comboxbox
    //
    protected FilterComboBoxModel createFilterComboBoxModel() {
        return new FilterComboBoxModel();
    }

    /**
     * Data model for a type-face selection combo-box.
     */
    protected class FilterComboBoxModel extends AbstractListModel implements ComboBoxModel, PropertyChangeListener {
        protected FileFilter[] filters;
        protected FilterComboBoxModel() {
            super();
            filters = getFileChooser().getChoosableFileFilters();
        }

        public void propertyChange(PropertyChangeEvent e) {
            String prop = e.getPropertyName();
            if(prop == JFileChooser.CHOOSABLE_FILE_FILTER_CHANGED_PROPERTY) {
                filters = (FileFilter[]) e.getNewValue();
                fireContentsChanged(this, -1, -1);
            } else if (prop == JFileChooser.FILE_FILTER_CHANGED_PROPERTY) {
                fireContentsChanged(this, -1, -1);
            }
        }

        public void setSelectedItem(Object filter) {
            if(filter != null) {
                getFileChooser().setFileFilter((FileFilter) filter);
                fireContentsChanged(this, -1, -1);
            }
        }

        public Object getSelectedItem() {
            // Ensure that the current filter is in the list.
            // NOTE: we shouldnt' have to do this, since JFileChooser adds
            // the filter to the choosable filters list when the filter
            // is set. Lets be paranoid just in case someone overrides
            // setFileFilter in JFileChooser.
            FileFilter currentFilter = getFileChooser().getFileFilter();
            boolean found = false;
            if(currentFilter != null) {
                for (FileFilter filter : filters) {
                    if (filter == currentFilter) {
                        found = true;
                    }
                }
                if(found == false) {
                    getFileChooser().addChoosableFileFilter(currentFilter);
                }
            }
            return getFileChooser().getFileFilter();
        }

        public int getSize() {
            if(filters != null) {
                return filters.length;
            } else {
                return 0;
            }
        }

        public Object getElementAt(int index) {
            if(index > getSize() - 1) {
                // This shouldn't happen. Try to recover gracefully.
                return getFileChooser().getFileFilter();
            }
            if(filters != null) {
                return filters[index];
            } else {
                return null;
            }
        }
    }

    public void valueChanged(ListSelectionEvent e) {
        JFileChooser fc = getFileChooser();
        File f = fc.getSelectedFile();
        if (!e.getValueIsAdjusting() && f != null && !getFileChooser().isTraversable(f)) {
            setFileName(fileNameString(f));
        }
    }

    /**
     * Acts when DirectoryComboBox has changed the selected item.
     */
    protected class DirectoryComboBoxAction implements ActionListener {




        public void actionPerformed(ActionEvent e) {
            File f = (File)directoryComboBox.getSelectedItem();
            getFileChooser().setCurrentDirectory(f);
        }
    }

    protected JButton getApproveButton(JFileChooser fc) {
        return approveButton;
    }

    public FileView getFileView(JFileChooser fc) {
        return fileView;
    }

    // ***********************
    // * FileView operations *
    // ***********************
    protected class WindowsFileView extends BasicFileView {
        /* FileView type descriptions */

        public Icon getIcon(File f) {
            Icon icon = getCachedIcon(f);
            if (icon != null) {
                return icon;
            }
            if (f != null) {
                icon = getFileChooser().getFileSystemView().getSystemIcon(f);
            }
            if (icon == null) {
                icon = super.getIcon(f);
            }
            cacheIcon(f, icon);
            return icon;
        }
    }
}
