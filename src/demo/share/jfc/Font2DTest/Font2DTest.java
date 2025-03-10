/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */



import java.awt.Color;
import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.CheckboxGroup;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;

import static java.nio.charset.StandardCharsets.UTF_16;

public final class Font2DTest extends JPanel
    implements ActionListener, ItemListener, ChangeListener {

    /// JFrame that will contain Font2DTest
    private final JFrame parent;
    /// FontPanel class that will contain all graphical output
    private final FontPanel fp;
    /// RangeMenu class that contains info about the unicode ranges
    private final RangeMenu rm;

    /// Other menus to set parameters for text drawing
    private final ChoiceV2 fontMenu;
    private JPanel fontMenuPanel;
    private JPanel stylePanel;
    private LabelV2 fontMenuLabel = null;
    private LabelV2 styleLabel = null;
    private ChoiceV2 fontNameMenu;
    private ChoiceV2 fontSubFamilyMenu;
    private final JTextField sizeField;
    private final ChoiceV2 styleMenu;
    private final ChoiceV2 textMenu;
    private int currentTextChoice = 0;
    private final ChoiceV2 transformMenu;
    private final ChoiceV2 transformMenuG2;
    private final ChoiceV2 methodsMenu;
    private final JComboBox<FontPanel.AAValues> antiAliasMenu;
    private final JComboBox<FontPanel.FMValues> fracMetricsMenu;

    private final JSlider contrastSlider;

    /// CheckboxMenuItems
    private CheckboxMenuItemV2 displayGridCBMI;
    private CheckboxMenuItemV2 force16ColsCBMI;
    private CheckboxMenuItemV2 showFontInfoCBMI;
    private JRadioButtonMenuItem familyAndStyleRBMI;
    private JRadioButtonMenuItem familyAndSubFamilyRBMI;
    private JRadioButtonMenuItem fontNameRBMI;

    /// JDialog boxes
    private JDialog userTextDialog;
    private JTextArea userTextArea;
    private JDialog printDialog;
    private JDialog fontInfoDialog;
    private LabelV2[] fontInfos = new LabelV2[2];
    private JFileChooser filePromptDialog = null;

    private ButtonGroup printCBGroup;
    private JRadioButton[] printModeCBs = new JRadioButton[3];

    /// Status bar
    private final LabelV2 statusBar;

    private String currentFontName = Font.DIALOG;
    private int[] fontStyles  = {Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC};

    /// Text filename
    private String tFileName;

    // Enabled or disabled status of canDisplay check
    private static boolean canDisplayCheck = true;
    private static final Locale l = Locale.getDefault();

    /// Initialize GUI variables and its layouts
    public Font2DTest( JFrame f) {
        parent = f;

        rm = new RangeMenu( this, parent );
        fp = new FontPanel( this, parent );
        statusBar = new LabelV2("");

        fontMenu = new ChoiceV2( this, canDisplayCheck );
        fontNameMenu = new ChoiceV2( this, false );
        fontSubFamilyMenu = new ChoiceV2( this, false );
        sizeField = new JTextField( "12", 3 );
        sizeField.addActionListener( this );
        styleMenu = new ChoiceV2( this );
        textMenu = new ChoiceV2( ); // listener added later
        transformMenu = new ChoiceV2( this );
        transformMenuG2 = new ChoiceV2( this );
        methodsMenu = new ChoiceV2( this );

        antiAliasMenu =
            new JComboBox<>(FontPanel.AAValues.values());
        antiAliasMenu.addActionListener(this);
        fracMetricsMenu =
            new JComboBox<>(FontPanel.FMValues.values());
        fracMetricsMenu.addActionListener(this);

        contrastSlider = new JSlider(JSlider.HORIZONTAL, 100, 250,
                                 FontPanel.getDefaultLCDContrast().intValue());
        contrastSlider.setEnabled(false);
        contrastSlider.setMajorTickSpacing(20);
        contrastSlider.setMinorTickSpacing(10);
        contrastSlider.setPaintTicks(true);
        contrastSlider.setPaintLabels(true);
        contrastSlider.addChangeListener(this);
        setupPanel();
        setupMenu();
        setupDialog();

        if(canDisplayCheck) {
            fireRangeChanged();
        }
    }

    private void addFontMenuToGBL(String labelText,
                                  JComponent menuContainer,
                                  GridBagLayout gbl,
                                  GridBagConstraints gbc,
                                  int leftInset,
                                  Container target) {

        fontMenuLabel = new LabelV2(labelText);
        fontMenuLabel.setLabelFor(menuContainer);
        GridBagConstraints gbcLabel = (GridBagConstraints) gbc.clone();
        gbcLabel.insets = new Insets(2, leftInset, 2, 0);
        gbcLabel.gridwidth = 1;
        gbcLabel.weightx = 0;
        gbcLabel.anchor = GridBagConstraints.EAST;
        gbl.setConstraints(fontMenuLabel, gbcLabel);
        target.add(fontMenuLabel);
        gbl.setConstraints(menuContainer, gbc);
        target.add( menuContainer );
    }

    private void addStyleMenuToGBL(String labelText,
                                  JComponent menuContainer,
                                  GridBagLayout gbl,
                                  GridBagConstraints gbc,
                                  int leftInset,
                                  Container target) {

        styleLabel = new LabelV2(labelText);
        styleLabel.setLabelFor(menuContainer);
        GridBagConstraints gbcLabel = (GridBagConstraints) gbc.clone();
        gbcLabel.insets = new Insets(2, leftInset, 2, 0);
        gbcLabel.gridwidth = 1;
        gbcLabel.weightx = 0;
        gbcLabel.anchor = GridBagConstraints.EAST;
        gbl.setConstraints(styleLabel, gbcLabel);
        target.add(styleLabel);
        gbl.setConstraints(menuContainer, gbc);
        target.add(menuContainer);
    }

    /// Set up the main interface panel
    private void setupPanel() {
        GridBagLayout gbl = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets( 2, 0, 2, 2 );
        this.setLayout( gbl );

        fontMenuPanel = new JPanel();
        fontMenuPanel.setLayout(new GridLayout());
        fontMenuPanel.add(fontMenu);
        addFontMenuToGBL(FAMILY_LABEL_TEXT, fontMenuPanel, gbl, gbc, 2, this );

        stylePanel = new JPanel();
        stylePanel.setLayout(new GridLayout());
        stylePanel.add(styleMenu);
        addStyleMenuToGBL(STYLE_LABEL_TEXT, stylePanel, gbl, gbc, 40, this );

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        addLabeledComponentToGBL( "Font Transform:",
                                  transformMenu, gbl, gbc, 20, this );
        gbc.gridwidth = 1;

        addLabeledComponentToGBL( "Range: ", rm, gbl, gbc, 2, this );
        addLabeledComponentToGBL( "Size: ", sizeField, gbl, gbc, 40, this );
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        addLabeledComponentToGBL( "Graphics Transform: ",
                                  transformMenuG2, gbl, gbc, 20, this );
        gbc.gridwidth = 1;

        gbc.anchor = GridBagConstraints.WEST;
        addLabeledComponentToGBL( "Method: ", methodsMenu, gbl, gbc, 2, this );
        addLabeledComponentToGBL("", null, gbl, gbc, 40, this);
        gbc.anchor = GridBagConstraints.EAST;

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        addLabeledComponentToGBL( "Text to use:", textMenu, gbl, gbc, 20, this );

        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        addLabeledComponentToGBL("LCD contrast: ",
                                  contrastSlider, gbl, gbc, 2, this);

        gbc.gridwidth = 1;
        addLabeledComponentToGBL("Antialiasing: ",
                                  antiAliasMenu, gbl, gbc, 40, this);

        gbc.anchor = GridBagConstraints.EAST;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        addLabeledComponentToGBL("Fractional metrics: ",
                                  fracMetricsMenu, gbl, gbc, 20, this);

        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets( 2, 0, 0, 2 );
        gbc.fill = GridBagConstraints.BOTH;
        gbl.setConstraints( fp, gbc );
        this.add( fp );

        gbc.weighty = 0;
        gbc.insets = new Insets( 0, 2, 0, 0 );
        gbl.setConstraints( statusBar, gbc );
        this.add( statusBar );
    }

    /// Adds a component to a container with a label to its left in GridBagLayout
    private void addLabeledComponentToGBL( String name,
                                           JComponent c,
                                           GridBagLayout gbl,
                                           GridBagConstraints gbc,
                                           int leftInset,
                                           Container target ) {
        LabelV2 l = new LabelV2( name );
        l.setLabelFor(c);
        GridBagConstraints gbcLabel = (GridBagConstraints) gbc.clone();
        if (gbcLabel.gridwidth == GridBagConstraints.REMAINDER) {
            gbcLabel.gridwidth = GridBagConstraints.RELATIVE;
        }

        gbcLabel.insets = new Insets( 2, leftInset, 2, 0 );
        gbcLabel.gridwidth = 1;
        gbcLabel.weightx = 0;

        if ( c == null )
          c = new JLabel( "" );

        gbcLabel.anchor = GridBagConstraints.EAST;
        gbl.setConstraints( l, gbcLabel );
        target.add( l );
        gbl.setConstraints( c, gbc );
        target.add( c );
    }

    /// Sets up menu entries
    private void setupMenu() {
        JMenu fileMenu = new JMenu( "File" );
        JMenu optionMenu = new JMenu( "Option" );

        fileMenu.add( new MenuItemV2( "Save Selected Options...", this ));
        fileMenu.add( new MenuItemV2( "Load Options...", this ));
        fileMenu.addSeparator();
        fileMenu.add( new MenuItemV2( "Save as PNG...", this ));
        fileMenu.add( new MenuItemV2( "Load PNG File to Compare...", this ));
        fileMenu.add( new MenuItemV2( "Page Setup...", this ));
        fileMenu.add( new MenuItemV2( "Print...", this ));
        fileMenu.addSeparator();
        fileMenu.add( new MenuItemV2( "Exit", this ));
        displayGridCBMI = new CheckboxMenuItemV2( "Display Grid", true, this );
        force16ColsCBMI = new CheckboxMenuItemV2( "Force 16 Columns", false, this );
        showFontInfoCBMI = new CheckboxMenuItemV2( "Display Font Info", false, this );
        optionMenu.add( displayGridCBMI );
        optionMenu.add( force16ColsCBMI );
        optionMenu.add( showFontInfoCBMI );
        optionMenu.addSeparator();
        familyAndStyleRBMI = new JRadioButtonMenuItem("Select font using Family Name and Style");
        familyAndStyleRBMI.addActionListener(this);
        familyAndSubFamilyRBMI = new JRadioButtonMenuItem("Select font using Family Name and SubFamily");
        familyAndSubFamilyRBMI.addActionListener(this);
        fontNameRBMI = new JRadioButtonMenuItem("Select font using Full Name");
        fontNameRBMI.addActionListener(this);
        ButtonGroup bg = new ButtonGroup();
        bg.add(familyAndStyleRBMI);
        bg.add(familyAndSubFamilyRBMI);
        bg.add(fontNameRBMI);
        familyAndStyleRBMI.setSelected(true);
        optionMenu.add(familyAndStyleRBMI);
        optionMenu.add(familyAndSubFamilyRBMI);
        optionMenu.add(fontNameRBMI);

        JMenuBar mb = parent.getJMenuBar();
        if ( mb == null )
          mb = new JMenuBar();
        mb.add( fileMenu );
        mb.add( optionMenu );

        parent.setJMenuBar( mb );

        String[] fontList = getAllFamilyNames();
        for (int i = 0; i < fontList.length; i++ ) {
            fontMenu.addItem( fontList[i] );
        }
        fontMenu.setSelectedItem("Dialog");

        fontList = getAllFontNames();
        for (int i = 0; i < fontList.length; i++ ) {
           fontNameMenu.addItem( fontList[i] );
        }
        fontNameMenu.setSelectedItem("Dialog");

        styleMenu.addItem( "Plain" );
        styleMenu.addItem( "Bold" );
        styleMenu.addItem( "Italic" );
        styleMenu.addItem( "Bold Italic" );

        transformMenu.addItem( "None" );
        transformMenu.addItem( "Scale" );
        transformMenu.addItem( "Shear" );
        transformMenu.addItem( "Rotate" );

        transformMenuG2.addItem( "None" );
        transformMenuG2.addItem( "Scale" );
        transformMenuG2.addItem( "Shear" );
        transformMenuG2.addItem( "Rotate" );

        methodsMenu.addItem( "drawString" );
        methodsMenu.addItem( "drawChars" );
        methodsMenu.addItem( "drawBytes" );
        methodsMenu.addItem( "drawGlyphVector" );
        methodsMenu.addItem( "TextLayout.draw" );
        methodsMenu.addItem( "GlyphVector.getOutline + draw" );
        methodsMenu.addItem( "TextLayout.getOutline + draw" );

        textMenu.addItem( "Unicode Range" );
        textMenu.addItem( "All Glyphs" );
        textMenu.addItem( "User Text" );
        textMenu.addItem( "Text File" );
        textMenu.addActionListener ( this ); // listener added later so unneeded events not thrown
    }

    /// Sets up the all dialogs used in Font2DTest...
    private void setupDialog() {
        filePromptDialog = new JFileChooser();

        /// Prepare user text dialog...
        userTextDialog = new JDialog( parent, "User Text", false );
        JPanel dialogTopPanel = new JPanel();
        JPanel dialogBottomPanel = new JPanel();
        LabelV2 message1 = new LabelV2( "Enter text below and then press update" );
        LabelV2 message2 = new LabelV2( "(Unicode char can be denoted by \\uXXXX)" );
        LabelV2 message3 = new LabelV2( "(Supplementary chars can be denoted by \\UXXXXXX)" );
        userTextArea = new JTextArea( "Font2DTest!" );
        ButtonV2 bUpdate = new ButtonV2( "Update", this );
        userTextArea.setFont( new Font( "dialog", Font.PLAIN, 12 ));
        dialogTopPanel.setLayout( new GridLayout( 3, 1 ));
        dialogTopPanel.add( message1 );
        dialogTopPanel.add( message2 );
        dialogTopPanel.add( message3 );
        dialogBottomPanel.add( bUpdate );
        //ABP
        JScrollPane userTextAreaSP = new JScrollPane(userTextArea);
        userTextAreaSP.setPreferredSize(new Dimension(300, 100));

        userTextDialog.getContentPane().setLayout( new BorderLayout() );
        userTextDialog.getContentPane().add( "North", dialogTopPanel );
        userTextDialog.getContentPane().add( "Center", userTextAreaSP );
        userTextDialog.getContentPane().add( "South", dialogBottomPanel );
        userTextDialog.pack();
        userTextDialog.addWindowListener( new WindowAdapter() {
            public void windowClosing( WindowEvent e ) {
                userTextDialog.setVisible(false);
            }
        });

        /// Prepare printing dialog...
        printCBGroup = new ButtonGroup();
        printModeCBs[ fp.ONE_PAGE ] =
          new JRadioButton( "Print one page from currently displayed character/line",
                         true );
        printModeCBs[ fp.CUR_RANGE ] =
          new JRadioButton( "Print all characters in currently selected range",
                         false );
        printModeCBs[ fp.ALL_TEXT ] =
          new JRadioButton( "Print all lines of text",
                         false );
        LabelV2 l =
          new LabelV2( "Note: Page range in native \"Print\" dialog will not affect the result" );
        JPanel buttonPanel = new JPanel();
        printModeCBs[ fp.ALL_TEXT ].setEnabled( false );
        buttonPanel.add( new ButtonV2( "Print", this ));
        buttonPanel.add( new ButtonV2( "Cancel", this ));

        printDialog = new JDialog( parent, "Print...", true );
        printDialog.setResizable( false );
        printDialog.addWindowListener( new WindowAdapter() {
            public void windowClosing( WindowEvent e ) {
                printDialog.setVisible(false);
            }
        });
        printDialog.getContentPane().setLayout( new GridLayout( printModeCBs.length + 2, 1 ));
        printDialog.getContentPane().add( l );
        for ( int i = 0; i < printModeCBs.length; i++ ) {
            printCBGroup.add( printModeCBs[i] );
            printDialog.getContentPane().add( printModeCBs[i] );
        }
        printDialog.getContentPane().add( buttonPanel );
        printDialog.pack();

        /// Prepare font information dialog...
        fontInfoDialog = new JDialog( parent, "Font info", false );
        fontInfoDialog.setResizable( false );
        fontInfoDialog.addWindowListener( new WindowAdapter() {
            public void windowClosing( WindowEvent e ) {
                fontInfoDialog.setVisible(false);
                showFontInfoCBMI.setState( false );
            }
        });
        JPanel fontInfoPanel = new JPanel();
        fontInfoPanel.setLayout( new GridLayout( fontInfos.length, 1 ));
        for ( int i = 0; i < fontInfos.length; i++ ) {
            fontInfos[i] = new LabelV2("");
            fontInfoPanel.add( fontInfos[i] );
        }
        fontInfoDialog.getContentPane().add( fontInfoPanel );

        /// Move the location of the dialog...
        userTextDialog.setLocation( 200, 300 );
        fontInfoDialog.setLocation( 0, 400 );
    }

    /// RangeMenu object signals using this function
    /// when Unicode range has been changed and text needs to be redrawn
    public void fireRangeChanged() {
        int[] range = rm.getSelectedRange();
        fp.setTextToDraw( fp.RANGE_TEXT, range, null, null );
        if(canDisplayCheck) {
            setupFontList(range[0], range[1]);
        }
        if ( showFontInfoCBMI.getState() )
          fireUpdateFontInfo();
    }

    /// Changes the message on the status bar
    public void fireChangeStatus( String message, boolean error ) {
        statusBar.setText( message );
        if ( error )
          fp.showingError = true;
        else
          fp.showingError = false;
    }

    /// Updates the information about the selected font
    public void fireUpdateFontInfo() {
        if ( showFontInfoCBMI.getState() ) {
            String[] infos = fp.getFontInfo();
            for ( int i = 0; i < fontInfos.length; i++ )
              fontInfos[i].setText( infos[i] );
            fontInfoDialog.pack();
        }
    }

    private void setupFontList(int rangeStart, int rangeEnd) {

        int listCount = fontMenu.getItemCount();
        int size = 16;

        try {
            size =  Float.valueOf(sizeField.getText()).intValue();
        }
        catch ( Exception e ) {
            System.out.println("Invalid font size in the size textField. Using default value of 16");
        }

        int style = fontStyles[styleMenu.getSelectedIndex()];
        Font f;
        for (int i = 0; i < listCount; i++) {
            String fontName = fontMenu.getItemAt(i);
            f = new Font(fontName, style, size);
            if ((rm.getSelectedIndex() != RangeMenu.SURROGATES_AREA_INDEX) &&
                canDisplayRange(f, rangeStart, rangeEnd)) {
                fontMenu.setBit(i, true);
            }
            else {
                fontMenu.setBit(i, false);
            }
        }

        fontMenu.repaint();
    }

    protected boolean canDisplayRange(Font font, int rangeStart, int rangeEnd) {
        for (int i = rangeStart; i < rangeEnd; i++) {
            if (font.canDisplay(i)) {
                return true;
            }
        }
        return false;
    }

    /// Displays a file load/save dialog and returns the specified file
    private String promptFile( boolean isSave, String initFileName ) {
        int retVal;
        String str;

        /// ABP
        if ( filePromptDialog == null)
                return null;

        if ( isSave ) {
            filePromptDialog.setDialogType( JFileChooser.SAVE_DIALOG );
            filePromptDialog.setDialogTitle( "Save..." );
            str = "Save";


        }
        else {
            filePromptDialog.setDialogType( JFileChooser.OPEN_DIALOG );
            filePromptDialog.setDialogTitle( "Load..." );
            str = "Load";
        }

        if (initFileName != null)
                filePromptDialog.setSelectedFile( new File( initFileName ) );
        retVal = filePromptDialog.showDialog( this, str );

        if ( retVal == JFileChooser.APPROVE_OPTION ) {
                File file = filePromptDialog.getSelectedFile();
                String fileName = file.getAbsolutePath();
                if ( fileName != null ) {
                        return fileName;
                }
        }

        return null;
    }

    /// Converts user text into arrays of String, delimited at newline character
    /// Also replaces any valid escape sequence with appropriate unicode character
    /// Support \\UXXXXXX notation for surrogates
    private String[] parseUserText( String orig ) {
        int length = orig.length();
        StringTokenizer perLine = new StringTokenizer( orig, "\n" );
        String[] textLines = new String[ perLine.countTokens() ];
        int lineNumber = 0;

        while ( perLine.hasMoreElements() ) {
            StringBuffer converted = new StringBuffer();
            String oneLine = perLine.nextToken();
            int lineLength = oneLine.length();
            int prevEscapeEnd = 0;
            int nextEscape = -1;
            do {
                int nextBMPEscape = oneLine.indexOf( "\\u", prevEscapeEnd );
                int nextSupEscape = oneLine.indexOf( "\\U", prevEscapeEnd );
                nextEscape = (nextBMPEscape < 0)
                    ? ((nextSupEscape < 0)
                       ? -1
                       : nextSupEscape)
                    : ((nextSupEscape < 0)
                       ? nextBMPEscape
                       : Math.min(nextBMPEscape, nextSupEscape));

                if ( nextEscape != -1 ) {
                    if ( prevEscapeEnd < nextEscape )
                        converted.append( oneLine.substring( prevEscapeEnd, nextEscape ));

                    prevEscapeEnd = nextEscape + (nextEscape == nextBMPEscape ? 6 : 8);
                    try {
                        String hex = oneLine.substring( nextEscape + 2, prevEscapeEnd );
                        if (nextEscape == nextBMPEscape) {
                            converted.append( (char) Integer.parseInt( hex, 16 ));
                        } else {
                            converted.append( new String( Character.toChars( Integer.parseInt( hex, 16 ))));
                        }
                    }
                    catch ( Exception e ) {
                        int copyLimit = Math.min(lineLength, prevEscapeEnd);
                        converted.append( oneLine.substring( nextEscape, copyLimit ));
                    }
                }
            } while (nextEscape != -1);
            if ( prevEscapeEnd < lineLength )
              converted.append( oneLine.substring( prevEscapeEnd, lineLength ));
            textLines[ lineNumber++ ] = converted.toString();
        }
        return textLines;
    }

    /// Reads the text from specified file, detecting UTF-16 encoding
    /// Then breaks the text into String array, delimited at every line break
    private void readTextFile( String fileName ) {
        try {
            String fileText;
            String[] textLines;
            BufferedInputStream bis =
              new BufferedInputStream( new FileInputStream( fileName ));
            int numBytes = bis.available();
            if (numBytes == 0) {
                throw new Exception("Text file " + fileName + " is empty");
            }
            byte[] byteData = new byte[ numBytes ];
            bis.read( byteData, 0, numBytes );
            bis.close();

            /// If byte mark is found, then use UTF-16 encoding to convert bytes...
            if (numBytes >= 2 &&
                (( byteData[0] == (byte) 0xFF && byteData[1] == (byte) 0xFE ) ||
                 ( byteData[0] == (byte) 0xFE && byteData[1] == (byte) 0xFF )))
              fileText = new String(byteData, UTF_16);
            /// Otherwise, use system default encoding
            else
              fileText = new String( byteData );

            int length = fileText.length();
            StringTokenizer perLine = new StringTokenizer( fileText, "\n" );
            /// Determine "Return Char" used in this file
            /// This simply finds first occurrence of CR, CR+LF or LF...
            for ( int i = 0; i < length; i++ ) {
                char iTh = fileText.charAt( i );
                if ( iTh == '\r' ) {
                    if ( i < length - 1 && fileText.charAt( i + 1 ) == '\n' )
                      perLine = new StringTokenizer( fileText, "\r\n" );
                    else
                      perLine = new StringTokenizer( fileText, "\r" );
                    break;
                }
                else if ( iTh == '\n' )
                  /// Use the one already created
                  break;
            }
            int lineNumber = 0, numLines = perLine.countTokens();
            textLines = new String[ numLines ];

            while ( perLine.hasMoreElements() ) {
                String oneLine = perLine.nextToken();
                if ( oneLine == null )
                  /// To make LineBreakMeasurer to return a valid TextLayout
                  /// on an empty line, simply feed it a space char...
                  oneLine = " ";
                textLines[ lineNumber++ ] = oneLine;
            }
            fp.setTextToDraw( fp.FILE_TEXT, null, null, textLines );
            rm.setEnabled( false );
            methodsMenu.setEnabled( false );
        }
        catch ( Exception ex ) {
            fireChangeStatus( "ERROR: Failed to Read Text File; See Stack Trace", true );
            ex.printStackTrace();
        }
    }

    /// Returns a String storing current configuration
    private void writeCurrentOptions( String fileName ) {
        try {
            String curOptions = fp.getCurrentOptions();
            BufferedOutputStream bos =
              new BufferedOutputStream( new FileOutputStream( fileName ));
            /// Prepend title and the option that is only obtainable here
            int[] range = rm.getSelectedRange();
            String completeOptions =
              ( "Font2DTest Option File\n" +
                displayGridCBMI.getState() + "\n" +
                force16ColsCBMI.getState() + "\n" +
                showFontInfoCBMI.getState() + "\n" +
                fontSelectionType + "\n" +
                (String)fontMenu.getSelectedItem() + "\n" +
                (String)fontNameMenu.getSelectedItem() + "\n" +
                (String)fontSubFamilyMenu.getSelectedItem() + "\n" +
                rm.getSelectedItem() + "\n" +
                range[0] + "\n" + range[1] + "\n" + curOptions + tFileName);
            byte[] toBeWritten = completeOptions.getBytes(UTF_16);
            bos.write( toBeWritten, 0, toBeWritten.length );
            bos.close();
        }
        catch ( Exception ex ) {
            fireChangeStatus( "ERROR: Failed to Save Options File; See Stack Trace", true );
            ex.printStackTrace();
        }
    }

    /// Updates GUI visibility/status after some parameters have changed
    private void updateGUI() {
        int selectedText = textMenu.getSelectedIndex();

        /// Set the visibility of User Text dialog
        if ( selectedText == fp.USER_TEXT )
          userTextDialog.setVisible(true);
        else
          userTextDialog.setVisible(false);
        /// Change the visibility/status/availability of Print JDialog buttons
        printModeCBs[ fp.ONE_PAGE ].setSelected( true );
        if ( selectedText == fp.FILE_TEXT || selectedText == fp.USER_TEXT ) {
            /// ABP
            /// update methodsMenu to show that TextLayout.draw is being used
            /// when we are in FILE_TEXT mode
            if ( selectedText == fp.FILE_TEXT )
                methodsMenu.setSelectedItem("TextLayout.draw");
            methodsMenu.setEnabled( selectedText == fp.USER_TEXT );
            printModeCBs[ fp.CUR_RANGE ].setEnabled( false );
            printModeCBs[ fp.ALL_TEXT ].setEnabled( true );
        }
        else {
            /// ABP
            /// update methodsMenu to show that drawGlyph is being used
            /// when we are in ALL_GLYPHS mode
            if ( selectedText == fp.ALL_GLYPHS )
                methodsMenu.setSelectedItem("drawGlyphVector");
            methodsMenu.setEnabled( selectedText == fp.RANGE_TEXT );
            printModeCBs[ fp.CUR_RANGE ].setEnabled( true );
            printModeCBs[ fp.ALL_TEXT ].setEnabled( false );
        }
        /// Modify RangeMenu and fontInfo label availabilty
        if ( selectedText == fp.RANGE_TEXT ) {
            fontInfos[1].setVisible( true );
            rm.setEnabled( true );
        }
        else {
            fontInfos[1].setVisible( false );
            rm.setEnabled( false );
        }
    }

    /// Loads saved options and applies them
    private void loadOptions( String fileName ) {
        try {
            BufferedInputStream bis =
              new BufferedInputStream( new FileInputStream( fileName ));
            int numBytes = bis.available();
            byte[] byteData = new byte[ numBytes ];
            bis.read( byteData, 0, numBytes );
            bis.close();
            if ( numBytes < 2 ||
                (byteData[0] != (byte) 0xFE || byteData[1] != (byte) 0xFF) )
              throw new Exception( "Not a Font2DTest options file" );

            String options = new String(byteData, UTF_16);
            StringTokenizer perLine = new StringTokenizer( options, "\n" );
            String title = perLine.nextToken();
            if ( !title.equals( "Font2DTest Option File" ))
              throw new Exception( "Not a Font2DTest options file" );

            /// Parse all options
            boolean displayGridOpt = Boolean.parseBoolean( perLine.nextToken() );
            boolean force16ColsOpt = Boolean.parseBoolean( perLine.nextToken() );
            boolean showFontInfoOpt = Boolean.parseBoolean( perLine.nextToken() );
            int fontSelType = Integer.parseInt( perLine.nextToken() );
            String fmItem = perLine.nextToken();
            String fnmItem = perLine.nextToken();
            String fsmItem = perLine.nextToken();
            String rangeNameOpt = perLine.nextToken();
            int rangeStartOpt = Integer.parseInt( perLine.nextToken() );
            int rangeEndOpt = Integer.parseInt( perLine.nextToken() );
            String fontNameOpt = perLine.nextToken();
            float fontSizeOpt = Float.parseFloat( perLine.nextToken() );
            int fontStyleOpt = Integer.parseInt( perLine.nextToken() );
            int fontTransformOpt = Integer.parseInt( perLine.nextToken() );
            int g2TransformOpt = Integer.parseInt( perLine.nextToken() );
            int textToUseOpt = Integer.parseInt( perLine.nextToken() );
            int drawMethodOpt = Integer.parseInt( perLine.nextToken() );
            int antialiasOpt = Integer.parseInt(perLine.nextToken());
            int fractionalOpt = Integer.parseInt(perLine.nextToken());
            int lcdContrast = Integer.parseInt(perLine.nextToken());
            String[] userTextOpt = { "Font2DTest!" };
            String dialogEntry = "Font2DTest!";
            if (textToUseOpt == fp.USER_TEXT )  {
                int numLines = perLine.countTokens(), lineNumber = 0;
                if ( numLines != 0 ) {
                    userTextOpt = new String[ numLines ];
                    dialogEntry = "";
                    for ( ; perLine.hasMoreElements(); lineNumber++ ) {
                        userTextOpt[ lineNumber ] = perLine.nextToken();
                        dialogEntry += userTextOpt[ lineNumber ] + "\n";
                    }
                }
            }

            /// Reset GUIs
            displayGridCBMI.setState( displayGridOpt );
            force16ColsCBMI.setState( force16ColsOpt );
            showFontInfoCBMI.setState( showFontInfoOpt );
            rm.setSelectedRange( rangeNameOpt, rangeStartOpt, rangeEndOpt );
            currentFontName = fontNameOpt;
            setFontSelectionType(fontSelType);
            fontMenu.setSelectedItem( fmItem );
            fontNameMenu.setSelectedItem( fnmItem );
            fontSubFamilyMenu.setSelectedItem( fsmItem );
            sizeField.setText( String.valueOf( fontSizeOpt ));
            styleMenu.setSelectedIndex( fontStyleOpt );
            transformMenu.setSelectedIndex( fontTransformOpt );
            transformMenuG2.setSelectedIndex( g2TransformOpt );
            textMenu.setSelectedIndex( textToUseOpt );
            methodsMenu.setSelectedIndex( drawMethodOpt );
            antiAliasMenu.setSelectedIndex( antialiasOpt );
            fracMetricsMenu.setSelectedIndex( fractionalOpt );
            contrastSlider.setValue(lcdContrast);

            userTextArea.setText( dialogEntry );
            updateGUI();

            if ( textToUseOpt == fp.FILE_TEXT ) {
              tFileName = perLine.nextToken();
              readTextFile(tFileName );
            }

            /// Reset option variables and repaint
            fp.loadOptions( displayGridOpt, force16ColsOpt,
                            rangeStartOpt, rangeEndOpt,
                            fontNameOpt, fontSizeOpt,
                            fontStyleOpt, fontTransformOpt, g2TransformOpt,
                            textToUseOpt, drawMethodOpt,
                            antialiasOpt, fractionalOpt,
                            lcdContrast, userTextOpt );
            if ( showFontInfoOpt ) {
                fireUpdateFontInfo();
                fontInfoDialog.setVisible(true);
            }
            else
              fontInfoDialog.setVisible(false);
        }
        catch ( Exception ex ) {
            fireChangeStatus( "ERROR: Failed to Load Options File; See Stack Trace", true );
            ex.printStackTrace();
        }
    }

    /// Loads a previously saved image
    private void loadComparisonPNG( String fileName ) {
        try {
            BufferedImage image =
                javax.imageio.ImageIO.read(new File(fileName));
            JFrame f = new JFrame( "Comparison PNG" );
            ImagePanel ip = new ImagePanel( image );
            f.setResizable( false );
            f.getContentPane().add( ip );
            f.addWindowListener( new WindowAdapter() {
                public void windowClosing( WindowEvent e ) {
                    ( (JFrame) e.getSource() ).dispose();
                }
            });
            f.pack();
            f.setVisible(true);
        }
        catch ( Exception ex ) {
            fireChangeStatus( "ERROR: Failed to Load PNG File; See Stack Trace", true );
            ex.printStackTrace();
        }
    }

    static final int FAMILY_AND_STYLE     = 1;
    static final int FONT_NAME            = 2;
    static final int FAMILY_AND_SUBFAMILY = 3;
    static int fontSelectionType = FAMILY_AND_STYLE;

    static final String FAMILY_LABEL_TEXT = "Font Family:";
    static final String NAME_LABEL_TEXT = "Font Name:";
    static final String STYLE_LABEL_TEXT = "Style:";
    static final String SUBFAMILY_LABEL_TEXT = "Subfamily:";

    void setUseFamilyAndStyle() {
        if (fontSelectionType == FAMILY_AND_STYLE) {
            return;
        }
        fontMenuLabel.setText(FAMILY_LABEL_TEXT);
        fontMenuPanel.removeAll();
        fontMenuPanel.add(fontMenu);
        if (fontSelectionType == FAMILY_AND_SUBFAMILY) {
            styleLabel.setText(STYLE_LABEL_TEXT);
            stylePanel.removeAll();
            stylePanel.add(styleMenu);
        }
        fontSelectionType = FAMILY_AND_STYLE;
        if (!familyAndStyleRBMI.isSelected()) {
           familyAndStyleRBMI.setSelected(true);
        }
        styleMenu.setSelectedIndex(0);
        currentFontName = (String)fontMenu.getSelectedItem();
        fp.setFontParams(currentFontName,
                         Float.parseFloat(sizeField.getText()),
                         0, // want to reset style to PLAIN
                         transformMenu.getSelectedIndex());
        revalidate();
        repaint();
    }

    void setUseFontName() {
        if (fontSelectionType == FONT_NAME) {
            return;
        }
        fontMenuLabel.setText(NAME_LABEL_TEXT);
        fontMenuPanel.removeAll();
        fontMenuPanel.add(fontNameMenu);
        if (fontSelectionType == FAMILY_AND_SUBFAMILY) {
            styleLabel.setText(STYLE_LABEL_TEXT);
            stylePanel.removeAll();
            stylePanel.add(styleMenu);
        }
        fontSelectionType = FONT_NAME;
        if (!fontNameRBMI.isSelected()) {
           fontNameRBMI.setSelected(true);
        }
        styleMenu.setSelectedIndex(0);
        currentFontName = (String)fontNameMenu.getSelectedItem();
        fp.setFontParams(currentFontName,
                         Float.parseFloat(sizeField.getText()),
                         0, // want to reset style to PLAIN
                         transformMenu.getSelectedIndex());
        revalidate();
        repaint();
    }

    void setUseFamilyAndSubFamily() {
        if (fontSelectionType == FAMILY_AND_SUBFAMILY) {
            return;
        }
        fontMenuLabel.setText(FAMILY_LABEL_TEXT);
        fontMenuPanel.removeAll();
        fontMenuPanel.add(fontMenu);
        styleLabel.setText(SUBFAMILY_LABEL_TEXT);
        stylePanel.removeAll();
        styleMenu.setSelectedIndex(0);
        String family = (String)fontMenu.getSelectedItem();
        updateSubFamilyMenu(family);
        stylePanel.add(fontSubFamilyMenu);
        fontSelectionType = FAMILY_AND_SUBFAMILY;
        if (!familyAndSubFamilyRBMI.isSelected()) {
           familyAndSubFamilyRBMI.setSelected(true);
        }
        String subname = (String)fontSubFamilyMenu.getSelectedItem();
        Font font = FontFamily.getFont(family, subname);
        currentFontName = (font != null) ? font.getFontName(l) : family;
        fp.setFontParams(currentFontName,
                         Float.parseFloat(sizeField.getText()),
                         0, // want to reset style to PLAIN
                         transformMenu.getSelectedIndex());
        revalidate();
        repaint();
    }

    void setFontSelectionType(int fsType) {
         switch (fsType) {
             case FAMILY_AND_STYLE     :
                  setUseFamilyAndStyle();
                  break;
             case FONT_NAME            :
                  setUseFontName();
                  break;
             case FAMILY_AND_SUBFAMILY :
                  setUseFamilyAndSubFamily();
                  break;
         }
    }

    /// Interface functions...

    /// ActionListener interface function
    /// Responds to JMenuItem, JTextField and JButton actions
    public void actionPerformed( ActionEvent e ) {
        Object source = e.getSource();

        if ( source instanceof JMenuItem ) {
            JMenuItem mi = (JMenuItem) source;
            String itemName = mi.getText();

            if (source == familyAndStyleRBMI) {
               setUseFamilyAndStyle();
            } else if (source == familyAndSubFamilyRBMI) {
               setUseFamilyAndSubFamily();
            } else if (source == fontNameRBMI) {
               setUseFontName();
            }
            else if ( itemName.equals( "Save Selected Options..." )) {
                String fileName = promptFile( true, "options.txt" );
                if ( fileName != null )
                  writeCurrentOptions( fileName );
            }
            else if ( itemName.equals( "Load Options..." )) {
                String fileName = promptFile( false, "options.txt" );
                if ( fileName != null )
                  loadOptions( fileName );
            }
            else if ( itemName.equals( "Save as PNG..." )) {
                String fileName = promptFile( true, fontMenu.getSelectedItem() + ".png" );
                if ( fileName != null )
                  fp.doSavePNG( fileName );
            }
            else if ( itemName.equals( "Load PNG File to Compare..." )) {
                String fileName = promptFile( false, null );
                if ( fileName != null )
                  loadComparisonPNG( fileName );
            }
            else if ( itemName.equals( "Page Setup..." ))
              fp.doPageSetup();
            else if ( itemName.equals( "Print..." ))
              printDialog.setVisible(true);
            else if ( itemName.equals( "Close" ))
              parent.dispose();
            else if ( itemName.equals( "Exit" ))
              System.exit(0);
        }

        else if ( source instanceof JTextField ) {
            JTextField tf = (JTextField) source;
            float sz = 12f;
            try {
                 sz = Float.parseFloat(sizeField.getText());
                 if (sz < 1f || sz > 120f) {
                      sz = 12f;
                      sizeField.setText("12");
                 }
            } catch (Exception se) {
                 sizeField.setText("12");
            }
            if ( tf == sizeField ) {
               fp.setFontParams(currentFontName,
                                sz,
                                styleMenu.getSelectedIndex(),
                                transformMenu.getSelectedIndex() );
               }
        }

        else if ( source instanceof JButton ) {
            String itemName = ( (JButton) source ).getText();
            /// Print dialog buttons...
            if ( itemName.equals( "Print" )) {
                for ( int i = 0; i < printModeCBs.length; i++ )
                  if ( printModeCBs[i].isSelected() ) {
                      printDialog.setVisible(false);
                      fp.doPrint( i );
                  }
            }
            else if ( itemName.equals( "Cancel" ))
              printDialog.setVisible(false);
            /// Update button from Usert Text JDialog...
            else if ( itemName.equals( "Update" ))
              fp.setTextToDraw( fp.USER_TEXT, null,
                                parseUserText( userTextArea.getText() ), null );
        }
        else if ( source instanceof JComboBox ) {
            JComboBox<?> c = (JComboBox<?>) source;

            /// RangeMenu handles actions by itself and then calls fireRangeChanged,
            /// so it is not listed or handled here
            if ( c == fontMenu || c == fontNameMenu || c == fontSubFamilyMenu ||
                 c == styleMenu || c == transformMenu )
            {
                if (c == fontNameMenu) {
                   currentFontName = (String)fontNameMenu.getSelectedItem();
                }
                else if ((c == fontMenu) && (fontSelectionType == FAMILY_AND_STYLE)) {
                   currentFontName = (String)fontMenu.getSelectedItem();
                }
                else if ((c == fontMenu) && (fontSelectionType == FAMILY_AND_SUBFAMILY)) {
                   String family = (String)fontMenu.getSelectedItem();
                   updateSubFamilyMenu(family);
                   String subname = (String)fontSubFamilyMenu.getSelectedItem();
                   Font font = FontFamily.getFont(family, subname);
                   if (font == null) return;
                   currentFontName = font.getFontName(l);
                }
                else if (c == fontSubFamilyMenu) {
                   /*
                    * When switching families, all items are removed from the sub family list.
                    * This triggers a synchronous recursive ActionEvent on the EDT, which should
                    * be ignored here, the code removes them adds the new items and will then
                    *  use the new default selected item.
                    * If we do not return, we'll not find a match and can get an NPE.
                    * This feels unsatisfactory, but it works.
                    */
                   if (fontSubFamilyMenu.getItemCount() == 0) {
                       return;
                   }
                   String family = (String)fontMenu.getSelectedItem();
                   String subname = (String)fontSubFamilyMenu.getSelectedItem();
                   Font font = FontFamily.getFont(family, subname);
                   if (font == null) return;
                   currentFontName = font.getFontName(l);
                }
                float sz = 12f;
                try {
                    sz = Float.parseFloat(sizeField.getText());
                    if (sz < 1f || sz > 120f) {
                        sz = 12f;
                        sizeField.setText("12");
                    }
                } catch (Exception se) {
                    sizeField.setText("12");
                }
                fp.setFontParams(currentFontName,
                                 sz,
                                 styleMenu.getSelectedIndex(),
                                 transformMenu.getSelectedIndex());
            } else if ( c == methodsMenu )
              fp.setDrawMethod( methodsMenu.getSelectedIndex() );
            else if ( c == textMenu ) {

                if(canDisplayCheck) {
                    fireRangeChanged();
                }

                int selected = textMenu.getSelectedIndex();

                if ( selected == fp.RANGE_TEXT )
                  fp.setTextToDraw( fp.RANGE_TEXT, rm.getSelectedRange(),
                                    null, null );
                else if ( selected == fp.USER_TEXT )
                  fp.setTextToDraw( fp.USER_TEXT, null,
                                    parseUserText( userTextArea.getText() ), null );
                else if ( selected == fp.FILE_TEXT ) {
                    String fileName = promptFile( false, null );
                    if ( fileName != null ) {
                      tFileName = fileName;
                      readTextFile( fileName );
                    } else {
                        /// User cancelled selection; reset to previous choice
                        c.setSelectedIndex( currentTextChoice );
                        return;
                    }
                }
                else if ( selected == fp.ALL_GLYPHS )
                  fp.setTextToDraw( fp.ALL_GLYPHS, null, null, null );

                updateGUI();
                currentTextChoice = selected;
            }
            else if ( c == transformMenuG2 ) {
                fp.setTransformG2( transformMenuG2.getSelectedIndex() );
            }
            else if (c == antiAliasMenu || c == fracMetricsMenu) {
                if (c == antiAliasMenu) {
                    boolean enabled = FontPanel.AAValues.
                        isLCDMode(antiAliasMenu.getSelectedItem());
                        contrastSlider.setEnabled(enabled);
                }
                fp.setRenderingHints(antiAliasMenu.getSelectedItem(),
                                     fracMetricsMenu.getSelectedItem(),
                                     contrastSlider.getValue());
            }
        }
    }

    public void stateChanged(ChangeEvent e) {
         Object source = e.getSource();
         if (source instanceof JSlider) {
             fp.setRenderingHints(antiAliasMenu.getSelectedItem(),
                                  fracMetricsMenu.getSelectedItem(),
                                  contrastSlider.getValue());
         }
    }

    /// ItemListener interface function
    /// Responds to JCheckBoxMenuItem, JComboBox and JCheckBox actions
    public void itemStateChanged( ItemEvent e ) {
        Object source = e.getSource();

        if ( source instanceof JCheckBoxMenuItem ) {
            JCheckBoxMenuItem cbmi = (JCheckBoxMenuItem) source;
            if ( cbmi == displayGridCBMI )
              fp.setGridDisplay( displayGridCBMI.getState() );
            else if ( cbmi == force16ColsCBMI )
              fp.setForce16Columns( force16ColsCBMI.getState() );
            else if ( cbmi == showFontInfoCBMI ) {
                if ( showFontInfoCBMI.getState() ) {
                    fireUpdateFontInfo();
                    fontInfoDialog.setVisible(true);
                }
                else
                  fontInfoDialog.setVisible(false);
            }
        }
    }

    private static void printUsage() {
        String usage = "Usage: java -jar Font2DTest.jar [options]\n" +
            "\nwhere options include:\n" +
            "    -dcdc | -disablecandisplaycheck disable canDisplay check for font\n" +
            "    -?    | -help                   print this help message\n" +
            "\nExample :\n" +
            "     To disable canDisplay check on font for ranges\n" +
            "     java -jar Font2DTest.jar -dcdc";
        System.out.println(usage);
        System.exit(0);
    }

    static class FontFamily {

        static Map<String, FontFamily> familyMap = new HashMap<>();
        private static Locale l = Locale.getDefault();
        private List<Font> fonts = new ArrayList<>();
        private List<String> subFamilyNames = new ArrayList<>();
        private Map<String, Font> nameToFontMap = new HashMap<>();
        private String familyName;

        private FontFamily(String name) {
           this.familyName = name;
       }

       String stripFamily(String family, String fullName) {
           if (family.equals(fullName)) {
               return "";
           }
           char[] familyChars = family.toCharArray();
           char[] fullChars = fullName.toCharArray();
           int familyIndex = 0;
           int fullIndex = 0;
           // there's probably a clever regexp way to do this
           // iterate over the chars in the family , if they are the same
           // keep going, if there's a '-' or ' ', skip it. In the font name,
           // do the same. If you reach the end of the family without some
           // other diff, return what's left of the fullName.
           while (familyIndex < familyChars.length && fullIndex < fullChars.length) {
           //while (familyIndex < familyChars.length) {
              if (fullIndex == fullChars.length) {
                  System.err.println("WEIRD FONT " + family + " " + fullName);
                  break;
              }
              if (familyChars[familyIndex] == fullChars[fullIndex]) {
                  familyIndex++; fullIndex++;
              }
              else if (familyChars[familyIndex] == ' ' && fullChars[fullIndex] == '-') {
                  familyIndex++; fullIndex++;
              }
              else if (familyChars[familyIndex] == '-' && fullChars[fullIndex] == ' ') {
                  familyIndex++; fullIndex++;
              }
              else if (familyChars[familyIndex] == ' ' || familyChars[familyIndex] == '-') {
                  familyIndex++;
              }
              else if (fullChars[fullIndex] == ' ' || fullChars[fullIndex] == '-') {
                  fullIndex++;
              } else {
                  break;
              }
           }
           if (fullIndex == fullChars.length) {
               return fullName;
           } else {
               return fullName.substring(fullIndex);
           }
       }

       /*
        * Getting the string to display here can be an art.
        * If the family is "Arial Black", then for a regular font, the
        * full name may be "Arial Black", or "Arial-Black", as reported on macOS.
        * For this case for the specific font might want to display the
        * full name, or synthesise "Regular". But to do that we have to
        * recognise that ' ' to '-' mapping.
        * For "Arial Black Italic" (Arial-Black-Italic) we want to be able to
        * trim so we display just "Italic".
        * Then we need to be able to map the text selection back to the
        * right font.
        */
       void add(Font f) {
           String fontName = f.getFontName(l);
           int flen = familyName.length();
           int nlen = fontName.length();
           String sfn;

           if (fontName.equals(familyName)) {
               sfn = "Regular";
           }
           else {
               sfn = stripFamily(familyName, fontName);
               sfn = sfn.replace('-', ' ');
           }

           fonts.add(f);
           subFamilyNames.add(sfn);
           nameToFontMap.put(sfn, f);
       }

       String[] getSubFamilyNames() {
           return subFamilyNames.stream().sorted().toArray(String[]::new);
       }


       Font getFontForSubFamilyName(String name) {
           return nameToFontMap.get(name);
       }

       static FontFamily getFontFamily(String name) {
           return familyMap.get(name);
       }

       static FontFamily createFontFamily(String name) {
           FontFamily f = familyMap.get(name);
           if (f == null) {
               f = new FontFamily(name);
               familyMap.put(name, f);
           }
           return f;
       }

       /*
        * familyName must be a name of an existing FontFamily
        * name, must be a valid "subFamilyName" within that FontFamily
        * as returned by getSubFamilyNames()
        */
       static Font getFont(String familyName, String subFamilyName) {
           FontFamily family = getFontFamily(familyName);
           return family.getFontForSubFamilyName(subFamilyName);
        }
    }

    static String[] familyNames;
    static Font[] allFonts;
    static List<String> allFontNames;
    static Map<String, FontFamily> familyMap = new HashMap<>();

    private static void buildFontInfo() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        familyNames = ge.getAvailableFontFamilyNames();
        allFonts = ge.getAllFonts();
        allFontNames = new ArrayList<String>();
        Locale l = Locale.getDefault();
        for (Font f : allFonts) {
           allFontNames.add(f.getFontName(l));
           String family = f.getFamily(l);
           FontFamily ff = FontFamily.getFontFamily(family);
           if (ff == null) {
              ff = FontFamily.createFontFamily(family);
           }
           ff.add(f);
        }
    }

    String getFontNameFor(String family, String subFamily) {
         return family + " " + subFamily;
    }

    void updateSubFamilyMenu(String name) {
         FontFamily family = FontFamily.getFontFamily(name);
         fontSubFamilyMenu.removeAllItems();

         String [] sfNames = family.getSubFamilyNames();
         for (int i=0; i<sfNames.length; i++) {
             fontSubFamilyMenu.addItem(sfNames[i]);
         }
         fontSubFamilyMenu.setSelectedIndex(0); // better be at least one !
    }

    static String[] getAllFontNames() {
        return allFontNames.stream().sorted().toArray(String[]::new);
    }

   static String[] getAllFamilyNames() {
       return familyNames;
   }

    /// Main function
    public static void main(String[] argv) {

        if (argv.length > 0) {
            if (argv[0].equalsIgnoreCase("-disablecandisplaycheck") ||
               argv[0].equalsIgnoreCase("-dcdc")) {
                canDisplayCheck = false;
            }
            else {
                printUsage();
            }
        }

        buildFontInfo();
        try {
           UIManager.setLookAndFeel(new NimbusLookAndFeel());
            SwingUtilities.invokeAndWait(() -> {
                final JFrame f = new JFrame( "Font2DTest" );
                final Font2DTest f2dt = new Font2DTest( f);
                f.addWindowListener( new WindowAdapter() {
                    public void windowOpening( WindowEvent e ) { f2dt.repaint(); }
                    public void windowClosing( WindowEvent e ) { System.exit(0); }
                });

                f.getContentPane().add( f2dt );
                f.pack();
                f.setVisible(true);
            });
        } catch (UnsupportedLookAndFeelException|InterruptedException|InvocationTargetException e) {
        }
    }

    /// Inner class definitions...

    /// Class to display just an image file
    /// Used to show the comparison PNG image
    private final class ImagePanel extends JPanel {
        private final BufferedImage bi;

        public ImagePanel( BufferedImage image ) {
            bi = image;
        }

        public Dimension getPreferredSize() {
            return new Dimension( bi.getWidth(), bi.getHeight() );
        }

        public void paintComponent( Graphics g ) {
            g.drawImage( bi, 0, 0, this );
        }
    }

    /// Classes made to avoid repetitive calls... (being lazy)
    private final class ButtonV2 extends JButton {
        public ButtonV2( String name, ActionListener al ) {
            super( name );
            this.addActionListener( al );
        }
    }

    private final class ChoiceV2 extends JComboBox<String> {

        private BitSet bitSet = null;

        public ChoiceV2() {;}

        public ChoiceV2( ActionListener al ) {
            super();
            this.addActionListener( al );
        }

        public ChoiceV2( ActionListener al, boolean fontChoice) {
            this(al);
            if(fontChoice) {
                //Register this component in ToolTipManager
                setToolTipText("");
                bitSet = new BitSet();
                setRenderer(new ChoiceV2Renderer(this));
            }
        }

        public String getToolTipText() {
            int index = this.getSelectedIndex();
            String fontName = (String) this.getSelectedItem();
            if(fontName != null &&
               (textMenu.getSelectedIndex() == fp.RANGE_TEXT)) {
                if (getBit(index)) {
                    return "Font \"" + fontName + "\" can display some characters in \"" +
                        rm.getSelectedItem() + "\" range";
                }
                else {
                    return "Font \"" + fontName + "\" cannot display any characters in \"" +
                        rm.getSelectedItem() + "\" range";
                }
            }
            return super.getToolTipText();
        }

        public void setBit(int bitIndex, boolean value) {
            bitSet.set(bitIndex, value);
        }

        public boolean getBit(int bitIndex) {
            return bitSet.get(bitIndex);
        }
    }

    private final class ChoiceV2Renderer extends DefaultListCellRenderer {

        private ImageIcon yesImage, blankImage;
        private ChoiceV2 choice = null;

        public ChoiceV2Renderer(ChoiceV2 choice) {
            BufferedImage yes =
                new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = yes.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.BLUE);
            g.drawLine(0, 5, 3, 10);
            g.drawLine(1, 5, 4, 10);
            g.drawLine(3, 10, 10, 0);
            g.drawLine(4, 9, 9, 0);
            g.dispose();
            BufferedImage blank =
                new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
            yesImage = new ImageIcon(yes);
            blankImage = new ImageIcon(blank);
            this.choice = choice;
        }

        public Component getListCellRendererComponent(JList<?> list,
                                                      Object value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {

            if(textMenu.getSelectedIndex() == fp.RANGE_TEXT) {

                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                //For JComboBox if index is -1, its rendering the selected index.
                if(index == -1) {
                    index = choice.getSelectedIndex();
                }

                if(choice.getBit(index)) {
                    setIcon(yesImage);
                }
                else {
                    setIcon(blankImage);
                }

            } else {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setIcon(blankImage);
            }

            return this;
        }
    }

    private final class LabelV2 extends JLabel {
        public LabelV2( String name ) {
            super( name );
        }
    }

    private final class MenuItemV2 extends JMenuItem {
        public MenuItemV2( String name, ActionListener al ) {
            super( name );
            this.addActionListener( al );
        }
    }

    private final class CheckboxMenuItemV2 extends JCheckBoxMenuItem {
        public CheckboxMenuItemV2( String name, boolean b, ItemListener il ) {
            super( name, b );
            this.addItemListener( il );
        }
    }
}
