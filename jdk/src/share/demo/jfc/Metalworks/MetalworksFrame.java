/*
 * Copyright (c) 1998, 2004, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 */

import java.awt.*;
import java.io.*;
import java.awt.event.*;
import java.beans.*;
import javax.swing.*;
import javax.swing.border.*;

import javax.swing.plaf.metal.*;


/**
  * This is the main container frame for the Metalworks demo app
  *
  * @author Steve Wilson
  */
public class MetalworksFrame extends JFrame {

    JMenuBar menuBar;
    JDesktopPane desktop;
    JInternalFrame toolPalette;
    JCheckBoxMenuItem showToolPaletteMenuItem;

    static final Integer DOCLAYER = new Integer(5);
    static final Integer TOOLLAYER = new Integer(6);
    static final Integer HELPLAYER = new Integer(7);

    static final String ABOUTMSG = "Metalworks \n \nAn application written to show off the Java Look & Feel. \n \nWritten by the JavaSoft Look & Feel Team \n  Michael Albers\n  Tom Santos\n  Jeff Shapiro\n  Steve Wilson";


    public MetalworksFrame() {
        super("Metalworks");
        final int inset = 50;
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setBounds ( inset, inset, screenSize.width - inset*2, screenSize.height - inset*2 );
        buildContent();
        buildMenus();
        this.addWindowListener(new WindowAdapter() {
                               public void windowClosing(WindowEvent e) {
                                   quit();
                               }});
        UIManager.addPropertyChangeListener(new UISwitchListener((JComponent)getRootPane()));
    }

    protected void buildMenus() {
        menuBar = new JMenuBar();
        menuBar.setOpaque(true);
        JMenu file = buildFileMenu();
        JMenu edit = buildEditMenu();
        JMenu views = buildViewsMenu();
        JMenu speed = buildSpeedMenu();
        JMenu help = buildHelpMenu();

        // load a theme from a text file
        MetalTheme myTheme = null;
        try {
            InputStream istream = getClass().getResourceAsStream("/resources/MyTheme.theme");
            myTheme =  new PropertiesMetalTheme(istream);
        } catch (NullPointerException e) {System.out.println(e);}

        // build an array of themes
        MetalTheme[] themes = { new OceanTheme(),
                                new DefaultMetalTheme(),
                                new GreenMetalTheme(),
                                new AquaMetalTheme(),
                                new KhakiMetalTheme(),
                                new DemoMetalTheme(),
                                new ContrastMetalTheme(),
                                new BigContrastMetalTheme(),
                                myTheme };

        // put the themes in a menu
        JMenu themeMenu = new MetalThemeMenu("Theme", themes);

        menuBar.add(file);
        menuBar.add(edit);
        menuBar.add(views);
        menuBar.add(themeMenu);
        menuBar.add(speed);
        menuBar.add(help);
        setJMenuBar(menuBar);
    }

    protected JMenu buildFileMenu() {
        JMenu file = new JMenu("File");
        JMenuItem newWin = new JMenuItem("New");
        JMenuItem open = new JMenuItem("Open");
        JMenuItem quit = new JMenuItem("Quit");

        newWin.addActionListener(new ActionListener() {
                               public void actionPerformed(ActionEvent e) {
                                   newDocument();
                               }});

        open.addActionListener(new ActionListener() {
                               public void actionPerformed(ActionEvent e) {
                                   openDocument();
                               }});

        quit.addActionListener(new ActionListener() {
                               public void actionPerformed(ActionEvent e) {
                                   quit();
                               }});

        file.add(newWin);
        file.add(open);
        file.addSeparator();
        file.add(quit);
        return file;
    }

    protected JMenu buildEditMenu() {
        JMenu edit = new JMenu("Edit");
        JMenuItem undo = new JMenuItem("Undo");
        JMenuItem copy = new JMenuItem("Copy");
        JMenuItem cut = new JMenuItem("Cut");
        JMenuItem paste = new JMenuItem("Paste");
        JMenuItem prefs = new JMenuItem("Preferences...");

        undo.setEnabled(false);
        copy.setEnabled(false);
        cut.setEnabled(false);
        paste.setEnabled(false);

        prefs.addActionListener(new ActionListener() {
                               public void actionPerformed(ActionEvent e) {
                                   openPrefsWindow();
                               }});

        edit.add(undo);
        edit.addSeparator();
        edit.add(cut);
        edit.add(copy);
        edit.add(paste);
        edit.addSeparator();
        edit.add(prefs);
        return edit;
    }

    protected JMenu buildViewsMenu() {
        JMenu views = new JMenu("Views");

        JMenuItem inBox = new JMenuItem("Open In-Box");
        JMenuItem outBox = new JMenuItem("Open Out-Box");
        outBox.setEnabled(false);

        inBox.addActionListener(new ActionListener() {
                               public void actionPerformed(ActionEvent e) {
                                   openInBox();
                               }});

        views.add(inBox);
        views.add(outBox);
        return views;
    }

     protected JMenu buildSpeedMenu() {
        JMenu speed = new JMenu("Drag");

        JRadioButtonMenuItem live = new JRadioButtonMenuItem("Live");
        JRadioButtonMenuItem outline = new JRadioButtonMenuItem("Outline");

        JRadioButtonMenuItem slow = new JRadioButtonMenuItem("Old and Slow");

        ButtonGroup group = new ButtonGroup();

        group.add(live);
        group.add(outline);
        group.add(slow);

        live.setSelected(true);

        slow.addActionListener(new ActionListener(){
                               public void actionPerformed(ActionEvent e){
                                // for right now I'm saying if you set the mode
                                 // to something other than a specified mode
                                 // it will revert to the old way
                                 // This is mostly for comparison's sake
                               desktop.setDragMode(-1);}});

        live.addActionListener(new ActionListener(){
                         public void actionPerformed(ActionEvent e){
                         desktop.setDragMode(JDesktopPane.LIVE_DRAG_MODE);}});

        outline.addActionListener(new ActionListener(){
                         public void actionPerformed(ActionEvent e){
                         desktop.setDragMode(JDesktopPane.OUTLINE_DRAG_MODE);}});


        speed.add(live);
        speed.add(outline);
        speed.add(slow);
        return speed;
     }

    protected JMenu buildHelpMenu() {
        JMenu help = new JMenu("Help");
        JMenuItem about = new JMenuItem("About Metalworks...");
        JMenuItem openHelp = new JMenuItem("Open Help Window");

        about.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showAboutBox();
            }
        });

        openHelp.addActionListener(new ActionListener() {
                               public void actionPerformed(ActionEvent e) {
                                   openHelpWindow();
                               }});

        help.add(about);
        help.add(openHelp);

        return help;
    }

    protected void buildContent() {
        desktop = new JDesktopPane();
        getContentPane().add(desktop);
    }

    public void quit() {
        System.exit(0);
    }

    public void newDocument() {
        JInternalFrame doc = new MetalworksDocumentFrame();
        desktop.add(doc, DOCLAYER);
        try {
            doc.setVisible(true);
            doc.setSelected(true);
        } catch (java.beans.PropertyVetoException e2) {}
    }

    public void openDocument() {
        JFileChooser chooser = new JFileChooser();
        chooser.showOpenDialog(this);
    }

    public void openHelpWindow() {
        JInternalFrame help = new MetalworksHelp();
        desktop.add(help, HELPLAYER);
        try {
            help.setVisible(true);
            help.setSelected(true);
        } catch (java.beans.PropertyVetoException e2) {}
    }

    public void showAboutBox() {
        JOptionPane.showMessageDialog(this, ABOUTMSG);
    }

    public void openPrefsWindow() {
        MetalworksPrefs dialog = new MetalworksPrefs(this);
        dialog.show();

    }

    public void openInBox() {
        JInternalFrame doc = new MetalworksInBox();
        desktop.add(doc, DOCLAYER);
        try {
            doc.setVisible(true);
            doc.setSelected(true);
        } catch (java.beans.PropertyVetoException e2) {}
    }
}
