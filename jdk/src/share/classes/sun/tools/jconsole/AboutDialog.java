/*
 * Copyright (c) 2006, 2007, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.jconsole;

import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyVetoException;
import java.net.URI;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;


import static java.awt.BorderLayout.*;
import static sun.tools.jconsole.Utilities.*;

@SuppressWarnings("serial")
public class AboutDialog extends InternalDialog {

    private static final Color textColor     = new Color(87,   88,  89);
    private static final Color bgColor       = new Color(232, 237, 241);
    private static final Color borderColor   = Color.black;

    private Icon mastheadIcon =
        new MastheadIcon(Messages.HELP_ABOUT_DIALOG_MASTHEAD_TITLE);

    private static AboutDialog aboutDialog;

    private JLabel statusBar;
    private Action closeAction;

    public AboutDialog(JConsole jConsole) {
        super(jConsole, Messages.HELP_ABOUT_DIALOG_TITLE, false);

        setAccessibleDescription(this, Messages.HELP_ABOUT_DIALOG_ACCESSIBLE_DESCRIPTION);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(false);
        JComponent cp = (JComponent)getContentPane();

        createActions();

        JLabel mastheadLabel = new JLabel(mastheadIcon);
        setAccessibleName(mastheadLabel,
                Messages.HELP_ABOUT_DIALOG_MASTHEAD_ACCESSIBLE_NAME);

        JPanel mainPanel = new TPanel(0, 0);
        mainPanel.add(mastheadLabel, NORTH);

        String jConsoleVersion = Version.getVersion();
        String vmName = System.getProperty("java.vm.name");
        String vmVersion = System.getProperty("java.vm.version");
        String urlStr = Messages.HELP_ABOUT_DIALOG_USER_GUIDE_LINK_URL;
        if (isBrowseSupported()) {
            urlStr = "<a style='color:#35556b' href=\"" + urlStr + "\">" + urlStr + "</a>";
        }

        JPanel infoAndLogoPanel = new JPanel(new BorderLayout(10, 10));
        infoAndLogoPanel.setBackground(bgColor);

        String colorStr = String.format("%06x", textColor.getRGB() & 0xFFFFFF);
        JEditorPane helpLink = new JEditorPane("text/html",
                                "<html><font color=#"+ colorStr + ">" +
                        Resources.format(Messages.HELP_ABOUT_DIALOG_JCONSOLE_VERSION, jConsoleVersion) +
                "<p>" + Resources.format(Messages.HELP_ABOUT_DIALOG_JAVA_VERSION, (vmName +", "+ vmVersion)) +
                "<p>" + Resources.format(Messages.HELP_ABOUT_DIALOG_USER_GUIDE_LINK, urlStr) +
                                                 "</html>");
        helpLink.setOpaque(false);
        helpLink.setEditable(false);
        helpLink.setForeground(textColor);
        mainPanel.setBorder(BorderFactory.createLineBorder(borderColor));
        infoAndLogoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        helpLink.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    browse(e.getDescription());
                }
            }
        });
        infoAndLogoPanel.add(helpLink, NORTH);

        ImageIcon brandLogoIcon = new ImageIcon(getClass().getResource("resources/brandlogo.png"));
        JLabel brandLogo = new JLabel(brandLogoIcon, JLabel.LEADING);

        JButton closeButton = new JButton(closeAction);

        JPanel bottomPanel = new TPanel(0, 0);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING));
        buttonPanel.setOpaque(false);

        mainPanel.add(infoAndLogoPanel, CENTER);
        cp.add(bottomPanel, SOUTH);

        infoAndLogoPanel.add(brandLogo, SOUTH);

        buttonPanel.setBorder(new EmptyBorder(2, 12, 2, 12));
        buttonPanel.add(closeButton);
        bottomPanel.add(buttonPanel, NORTH);

        statusBar = new JLabel(" ");
        bottomPanel.add(statusBar, SOUTH);

        cp.add(mainPanel, NORTH);

        pack();
        setLocationRelativeTo(jConsole);
        Utilities.updateTransparency(this);
    }

    public void showDialog() {
        statusBar.setText(" ");
        setVisible(true);
        try {
            // Bring to front of other dialogs
            setSelected(true);
        } catch (PropertyVetoException e) {
            // ignore
        }
    }

    private static AboutDialog getAboutDialog(JConsole jConsole) {
        if (aboutDialog == null) {
            aboutDialog = new AboutDialog(jConsole);
        }
        return aboutDialog;
    }

    static void showAboutDialog(JConsole jConsole) {
        getAboutDialog(jConsole).showDialog();
    }

    static void browseUserGuide(JConsole jConsole) {
        getAboutDialog(jConsole).browse(Messages.HELP_ABOUT_DIALOG_USER_GUIDE_LINK_URL);
    }

    static boolean isBrowseSupported() {
        return (Desktop.isDesktopSupported() &&
                Desktop.getDesktop().isSupported(Desktop.Action.BROWSE));
    }

    void browse(String urlStr) {
        try {
            Desktop.getDesktop().browse(new URI(urlStr));
        } catch (Exception ex) {
            showDialog();
            statusBar.setText(ex.getLocalizedMessage());
            if (JConsole.isDebug()) {
                ex.printStackTrace();
            }
        }
    }

    private void createActions() {
        closeAction = new AbstractAction(Messages.CLOSE) {
            public void actionPerformed(ActionEvent ev) {
                setVisible(false);
                statusBar.setText("");
            }
        };
    }

    private static class TPanel extends JPanel {
        TPanel(int hgap, int vgap) {
            super(new BorderLayout(hgap, vgap));
            setOpaque(false);
        }
    }
}
