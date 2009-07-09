/*
 * Copyright 2000-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 *   - Neither the name of Sun Microsystems nor the names of its
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

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.*;

/**
 * RangeMenu.java
 *
 * @author Shinsuke Fukuda
 * @author Ankit Patel [Conversion to Swing - 01/07/30]
 */

/// Custom made choice menu that holds data for unicode range

public final class RangeMenu extends JComboBox implements ActionListener {

    /// Painfully extracted from java.lang.Character.UnicodeBlock.  Arrrgh!
    /// Unicode 5.1.0 data.

    private final int[][] UNICODE_RANGES = {
        { 0x000000, 0x00007f }, /// BASIC_LATIN
        { 0x000080, 0x0000ff }, /// LATIN_1_SUPPLEMENT
        { 0x000100, 0x00017f }, /// LATIN_EXTENDED_A
        { 0x000180, 0x00024f }, /// LATIN_EXTENDED_B
        { 0x000250, 0x0002af }, /// IPA_EXTENSIONS
        { 0x0002b0, 0x0002ff }, /// SPACING_MODIFIER_LETTERS
        { 0x000300, 0x00036f }, /// COMBINING_DIACRITICAL_MARKS
        { 0x000370, 0x0003ff }, /// GREEK_AND_COPTIC
        { 0x000400, 0x0004ff }, /// CYRILLIC
        { 0x000500, 0x00052f }, /// CYRILLIC_SUPPLEMENTARY
        { 0x000530, 0x00058f }, /// ARMENIAN
        { 0x000590, 0x0005ff }, /// HEBREW
        { 0x000600, 0x0006ff }, /// ARABIC
        { 0x000700, 0x00074f }, /// SYRIAC
        { 0x000750, 0x00077f }, /// ARABIC_SUPPLEMENT
        { 0x000780, 0x0007bf }, /// THAANA
        { 0x0007c0, 0x0007ff }, /// NKO
        { 0x000900, 0x00097f }, /// DEVANAGARI
        { 0x000980, 0x0009ff }, /// BENGALI
        { 0x000a00, 0x000a7f }, /// GURMUKHI
        { 0x000a80, 0x000aff }, /// GUJARATI
        { 0x000b00, 0x000b7f }, /// ORIYA
        { 0x000b80, 0x000bff }, /// TAMIL
        { 0x000c00, 0x000c7f }, /// TELUGU
        { 0x000c80, 0x000cff }, /// KANNADA
        { 0x000d00, 0x000d7f }, /// MALAYALAM
        { 0x000d80, 0x000dff }, /// SINHALA
        { 0x000e00, 0x000e7f }, /// THAI
        { 0x000e80, 0x000eff }, /// LAO
        { 0x000f00, 0x000fff }, /// TIBETAN
        { 0x001000, 0x00109f }, /// MYANMAR
        { 0x0010a0, 0x0010ff }, /// GEORGIAN
        { 0x001100, 0x0011ff }, /// HANGUL_JAMO
        { 0x001200, 0x00137f }, /// ETHIOPIC
        { 0x001380, 0x00139f }, /// ETHIOPIC_SUPPLEMENT
        { 0x0013a0, 0x0013ff }, /// CHEROKEE
        { 0x001400, 0x00167f }, /// UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS
        { 0x001680, 0x00169f }, /// OGHAM
        { 0x0016a0, 0x0016ff }, /// RUNIC
        { 0x001700, 0x00171f }, /// TAGALOG
        { 0x001720, 0x00173f }, /// HANUNOO
        { 0x001740, 0x00175f }, /// BUHID
        { 0x001760, 0x00177f }, /// TAGBANWA
        { 0x001780, 0x0017ff }, /// KHMER
        { 0x001800, 0x0018af }, /// MONGOLIAN
        { 0x001900, 0x00194f }, /// LIMBU
        { 0x001950, 0x00197f }, /// TAI_LE
        { 0x001980, 0x0019df }, /// NEW_TAI_LE
        { 0x0019e0, 0x0019ff }, /// KHMER_SYMBOLS
        { 0x001a00, 0x001a1f }, /// BUGINESE
        { 0x001b00, 0x001b7f }, /// BALINESE
        { 0x001b80, 0x001bbf }, /// SUNDANESE
        { 0x001c00, 0x001c4f }, /// LEPCHA
        { 0x001c50, 0x001c7f }, /// OL_CHIKI
        { 0x001d00, 0x001d7f }, /// PHONETIC_EXTENSIONS
        { 0x001d80, 0x001dbf }, /// PHONEITC EXTENSIONS SUPPLEMENT
        { 0x001dc0, 0x001dff }, /// COMBINING_DIACRITICAL_MAKRS_SUPPLEMENT
        { 0x001e00, 0x001eff }, /// LATIN_EXTENDED_ADDITIONAL
        { 0x001f00, 0x001fff }, /// GREEK_EXTENDED
        { 0x002000, 0x00206f }, /// GENERAL_PUNCTUATION
        { 0x002070, 0x00209f }, /// SUPERSCRIPTS_AND_SUBSCRIPTS
        { 0x0020a0, 0x0020cf }, /// CURRENCY_SYMBOLS
        { 0x0020d0, 0x0020ff }, /// COMBINING_MARKS_FOR_SYMBOLS
        { 0x002100, 0x00214f }, /// LETTERLIKE_SYMBOLS
        { 0x002150, 0x00218f }, /// NUMBER_FORMS
        { 0x002190, 0x0021ff }, /// ARROWS
        { 0x002200, 0x0022ff }, /// MATHEMATICAL_OPERATORS
        { 0x002300, 0x0023ff }, /// MISCELLANEOUS_TECHNICAL
        { 0x002400, 0x00243f }, /// CONTROL_PICTURES
        { 0x002440, 0x00245f }, /// OPTICAL_CHARACTER_RECOGNITION
        { 0x002460, 0x0024ff }, /// ENCLOSED_ALPHANUMERICS
        { 0x002500, 0x00257f }, /// BOX_DRAWING
        { 0x002580, 0x00259f }, /// BLOCK_ELEMENTS
        { 0x0025a0, 0x0025ff }, /// GEOMETRIC_SHAPES
        { 0x002600, 0x0026ff }, /// MISCELLANEOUS_SYMBOLS
        { 0x002700, 0x0027bf }, /// DINGBATS
        { 0x0027c0, 0x0027ef }, /// MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A
        { 0x0027f0, 0x0027ff }, /// SUPPLEMENTAL_ARROWS_A
        { 0x002800, 0x0028ff }, /// BRAILLE_PATTERNS
        { 0x002900, 0x00297f }, /// SUPPLEMENTAL_ARROWS_B
        { 0x002980, 0x0029ff }, /// MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B
        { 0x002a00, 0x002aff }, /// SUPPLEMENTAL_MATHEMATICAL_OPERATORS
        { 0x002b00, 0x002bff }, /// MISCELLANEOUS_SYMBOLS_AND_ARROWS
        { 0x002c00, 0x002c5f }, /// GLAGOLITIC
        { 0x002c60, 0x002c7f }, /// LATIN_EXTENDED-C
        { 0x002c80, 0x002cff }, /// COPTIC
        { 0x002d00, 0x002d2f }, /// GEORGIAN_SUPPLEMENT
        { 0x002d30, 0x002d7f }, /// TIFINAGH
        { 0x002d80, 0x002ddf }, /// ETHIOPIC_EXTENDED
        { 0x002de0, 0x002dff }, /// CYRILLIC_EXTENDED-A
        { 0x002e00, 0x002e7f }, /// SUPPLEMENTAL_PUNCTUATION
        { 0x002e80, 0x002eff }, /// CJK_RADICALS_SUPPLEMENT
        { 0x002f00, 0x002fdf }, /// KANGXI_RADICALS
        { 0x002ff0, 0x002fff }, /// IDEOGRAPHIC_DESCRIPTION_CHARACTERS
        { 0x003000, 0x00303f }, /// CJK_SYMBOLS_AND_PUNCTUATION
        { 0x003040, 0x00309f }, /// HIRAGANA
        { 0x0030a0, 0x0030ff }, /// KATAKANA
        { 0x003100, 0x00312f }, /// BOPOMOFO
        { 0x003130, 0x00318f }, /// HANGUL_COMPATIBILITY_JAMO
        { 0x003190, 0x00319f }, /// KANBUN
        { 0x0031a0, 0x0031bf }, /// BOPOMOFO_EXTENDED
        { 0x0031c0, 0x0031ef }, /// CJK_STROKES
        { 0x0031f0, 0x0031ff }, /// KATAKANA_PHONETIC_EXTENSIONS
        { 0x003200, 0x0032ff }, /// ENCLOSED_CJK_LETTERS_AND_MONTHS
        { 0x003300, 0x0033ff }, /// CJK_COMPATIBILITY
        { 0x003400, 0x004dbf }, /// CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
        { 0x004dc0, 0x004dff }, /// YIJING_HEXAGRAM_SYMBOLS
        { 0x004e00, 0x009fff }, /// CJK_UNIFIED_IDEOGRAPHS
        { 0x00a000, 0x00a48f }, /// YI_SYLLABLES
        { 0x00a490, 0x00a4cf }, /// YI_RADICALS
        { 0x00a500, 0x00a63f }, /// YAI
        { 0x00a640, 0x00a69f }, /// CYRILLIC_EXTENDED-B
        { 0x00a700, 0x00a71f }, /// MODIFIER_TONE_LETTERS
        { 0x00a720, 0x00a7ff }, /// LATIN_EXTENDED-D
        { 0x00a800, 0x00a82f }, /// SYLOTI_NAGRI
        { 0x00a840, 0x00a87f }, /// PHAGS-PA
        { 0x00a880, 0x00a8df }, /// SAURASHTRA
        { 0x00a900, 0x00a92f }, /// KAYAH_LI
        { 0x00a930, 0x00a95f }, /// REJANG
        { 0x00aa00, 0x00aa5f }, /// CHAM
        { 0x00ac00, 0x00d7af }, /// HANGUL_SYLLABLES
        { 0x00d800, 0x00db7f }, /// HIGH_SURROGATES_AREA
        { 0x00db80, 0x00dbff }, /// HIGH_PRIVATE_USE_SURROGATES_AREA
        { 0x00dc00, 0x00dfff }, /// LOW_SURROGATES_AREA
        { 0x00e000, 0x00f8ff }, /// PRIVATE_USE_AREA
        { 0x00f900, 0x00faff }, /// CJK_COMPATIBILITY_IDEOGRAPHS
        { 0x00fb00, 0x00fb4f }, /// ALPHABETIC_PRESENTATION_FORMS
        { 0x00fb50, 0x00fdff }, /// ARABIC_PRESENTATION_FORMS_A
        { 0x00fe00, 0x00fe0f }, /// VARIATION_SELECTORS
        { 0x00fe10, 0x00fe1f }, /// VERTICAL_FORMS
        { 0x00fe20, 0x00fe2f }, /// COMBINING_HALF_MARKS
        { 0x00fe30, 0x00fe4f }, /// CJK_COMPATIBILITY_FORMS
        { 0x00fe50, 0x00fe6f }, /// SMALL_FORM_VARIANTS
        { 0x00fe70, 0x00feff }, /// ARABIC_PRESENTATION_FORMS_B
        { 0x00ff00, 0x00ffef }, /// HALFWIDTH_AND_FULLWIDTH_FORMS
        { 0x00fff0, 0x00ffff }, /// SPECIALS
        { 0x010000, 0x01007f }, /// LINEAR_B_SYLLABARY
        { 0x010080, 0x0100ff }, /// LINEAR_B_IDEOGRAMS
        { 0x010100, 0x01013f }, /// AEGEAN_NUMBERS
        { 0x010140, 0x01018f }, /// ANCIENT_GREEK_NUMBERS
        { 0x010190, 0x0101cf }, /// ANCIENT_SYMBOLS
        { 0x0101d0, 0x0101ff }, /// PHAISTOS_DISC
        { 0x010280, 0x01029f }, /// LYCIAN
        { 0x0102a0, 0x0102df }, /// CARIAN
        { 0x010300, 0x01032f }, /// OLD_ITALIC
        { 0x010330, 0x01034f }, /// GOTHIC
        { 0x010380, 0x01039f }, /// UGARITIC
        { 0x0103a0, 0x0103df }, /// OLD_PERSIAN
        { 0x010400, 0x01044f }, /// DESERET
        { 0x010450, 0x01047f }, /// SHAVIAN
        { 0x010480, 0x0104af }, /// OSMANYA
        { 0x010800, 0x01083f }, /// CYPRIOT_SYLLABARY
        { 0x010900, 0x01091f }, /// PHOENICIAN
        { 0x010920, 0x01093f }, /// LYDIAN
        { 0x010a00, 0x010a5f }, /// KHAROSHTHI
        { 0x012000, 0x0123ff }, /// CUNEIFORM
        { 0x012400, 0x01247f }, /// CUNEIFORM_NUMBERS_AND_PUNCTUATION
        { 0x01d000, 0x01d0ff }, /// BYZANTINE_MUSICAL_SYMBOLS
        { 0x01d100, 0x01d1ff }, /// MUSICAL_SYMBOLS
        { 0x01d200, 0x01d24f }, /// ANCIENT_GREEK_MUSICAL_NOTATION
        { 0x01d300, 0x01d35f }, /// TAI_XUAN_JING_SYMBOLS
        { 0x01d360, 0x01d37f }, /// COUNTING_ROD_NUMERALS
        { 0x01d400, 0x01d7ff }, /// MATHEMATICAL_ALPHANUMERIC_SYMBOLS
        { 0x01f000, 0x01f02f }, /// MAHJONG_TILES
        { 0x01f030, 0x01f09f }, /// DOMINO_TILES
        { 0x020000, 0x02a6df }, /// CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
        { 0x02f800, 0x02fa1f }, /// CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
        { 0x0e0000, 0x0e007f }, /// TAGS
        { 0x0e0100, 0x0e01ef }, /// VARIATION_SELECTORS_SUPPLEMENT
        { 0x0f0000, 0x0fffff }, /// SUPPLEMENTARY_PRIVATE_USE_AREA_A
        { 0x100000, 0x10ffff }, /// SUPPLEMENTARY_PRIVATE_USE_AREA_B
        { 0x000000, 0x00007f }, /// OTHER [USER DEFINED RANGE]
     };

    private final String[] UNICODE_RANGE_NAMES = {
        "Basic Latin",
        "Latin-1 Supplement",
        "Latin Extended-A",
        "Latin Extended-B",
        "IPA Extensions",
        "Spacing Modifier Letters",
        "Combining Diacritical Marks",
        "Greek and Coptic",
        "Cyrillic",
        "Cyrillic Supplement",
        "Armenian",
        "Hebrew",
        "Arabic",
        "Syriac",
        "Arabic Supplement",
        "Thaana",
        "NKo",
        "Devanagari",
        "Bengali",
        "Gurmukhi",
        "Gujarati",
        "Oriya",
        "Tamil",
        "Telugu",
        "Kannada",
        "Malayalam",
        "Sinhala",
        "Thai",
        "Lao",
        "Tibetan",
        "Myanmar",
        "Georgian",
        "Hangul Jamo",
        "Ethiopic",
        "Ethiopic Supplement",
        "Cherokee",
        "Unified Canadian Aboriginal Syllabics",
        "Ogham",
        "Runic",
        "Tagalog",
        "Hanunoo",
        "Buhid",
        "Tagbanwa",
        "Khmer",
        "Mongolian",
        "Limbu",
        "Tai Le",
        "New Tai Lue",
        "Khmer Symbols",
        "Buginese",
        "Balinese",
        "Sundanese",
        "Lepcha",
        "Ol Chiki",
        "Phonetic Extensions",
        "Phonetic Extensions Supplement",
        "Combining Diacritical Marks Supplement",
        "Latin Extended Additional",
        "Greek Extended",
        "General Punctuation",
        "Superscripts and Subscripts",
        "Currency Symbols",
        "Combining Diacritical Marks for Symbols",
        "Letterlike Symbols",
        "Number Forms",
        "Arrows",
        "Mathematical Operators",
        "Miscellaneous Technical",
        "Control Pictures",
        "Optical Character Recognition",
        "Enclosed Alphanumerics",
        "Box Drawing",
        "Block Elements",
        "Geometric Shapes",
        "Miscellaneous Symbols",
        "Dingbats",
        "Miscellaneous Mathematical Symbols-A",
        "Supplemental Arrows-A",
        "Braille Patterns",
        "Supplemental Arrows-B",
        "Miscellaneous Mathematical Symbols-B",
        "Supplemental Mathematical Operators",
        "Miscellaneous Symbols and Arrows",
        "Glagolitic",
        "Latin Extended-C",
        "Coptic",
        "Georgian Supplement",
        "Tifinagh",
        "Ethiopic Extended",
        "Cyrillic Extended-A",
        "Supplemental Punctuation",
        "CJK Radicals Supplement",
        "Kangxi Radicals",
        "Ideographic Description Characters",
        "CJK Symbols and Punctuation",
        "Hiragana",
        "Katakana",
        "Bopomofo",
        "Hangul Compatibility Jamo",
        "Kanbun",
        "Bopomofo Extended",
        "CJK Strokes",
        "Katakana Phonetic Extensions",
        "Enclosed CJK Letters and Months",
        "CJK Compatibility",
        "CJK Unified Ideographs Extension A",
        "Yijing Hexagram Symbols",
        "CJK Unified Ideographs",
        "Yi Syllables",
        "Yi Radicals",
        "Vai",
        "Cyrillic Extended-B",
        "Modifier Tone Letters",
        "Latin Extended-D",
        "Syloti Nagri",
        "Phags-pa",
        "Saurashtra",
        "Kayah Li",
        "Rejang",
        "Cham",
        "Hangul Syllables",
        "High Surrogates",
        "High Private Use Surrogates",
        "Low Surrogates",
        "Private Use Area",
        "CJK Compatibility Ideographs",
        "Alphabetic Presentation Forms",
        "Arabic Presentation Forms-A",
        "Variation Selectors",
        "Vertical Forms",
        "Combining Half Marks",
        "CJK Compatibility Forms",
        "Small Form Variants",
        "Arabic Presentation Forms-B",
        "Halfwidth and Fullwidth Forms",
        "Specials",
        "Linear B Syllabary",
        "Linear B Ideograms",
        "Aegean Numbers",
        "Ancient Greek Numbers",
        "Ancient Symbols",
        "Phaistos Disc",
        "Lycian",
        "Carian",
        "Old Italic",
        "Gothic",
        "Ugaritic",
        "Old Persian",
        "Deseret",
        "Shavian",
        "Osmanya",
        "Cypriot Syllabary",
        "Phoenician",
        "Lydian",
        "Kharoshthi",
        "Cuneiform",
        "Cuneiform Numbers and Punctuation",
        "Byzantine Musical Symbols",
        "Musical Symbols",
        "Ancient Greek Musical Notation",
        "Tai Xuan Jing Symbols",
        "Counting Rod Numerals",
        "Mathematical Alphanumeric Symbols",
        "Mahjong Tiles",
        "Domino Tiles",
        "CJK Unified Ideographs Extension B",
        "CJK Compatibility Ideographs Supplement",
        "Tags",
        "Variation Selectors Supplement",
        "Supplementary Private Use Area-A",
        "Supplementary Private Use Area-B",
        "Custom...",
    };

    private boolean useCustomRange = false;
    private int[] customRange = { 0x0000, 0x007f };

    /// Custom range dialog variables
    private final JDialog customRangeDialog;
    private final JTextField customRangeStart = new JTextField( "0000", 4 );
    private final JTextField customRangeEnd   = new JTextField( "007F", 4 );
    private final int CUSTOM_RANGE_INDEX = UNICODE_RANGE_NAMES.length - 1;

    /// Parent Font2DTest Object holder
    private final Font2DTest parent;

    public static final int SURROGATES_AREA_INDEX = 91;

    public RangeMenu( Font2DTest demo, JFrame f ) {
        super();
        parent = demo;

        for ( int i = 0; i < UNICODE_RANGE_NAMES.length; i++ )
          addItem( UNICODE_RANGE_NAMES[i] );

        setSelectedIndex( 0 );
        addActionListener( this );

        /// Set up custom range dialog...
        customRangeDialog = new JDialog( f, "Custom Unicode Range", true );
        customRangeDialog.setResizable( false );

        JPanel dialogTop = new JPanel();
        JPanel dialogBottom = new JPanel();
        JButton okButton = new JButton("OK");
        JLabel from = new JLabel( "From:" );
        JLabel to = new JLabel("To:");
        Font labelFont = new Font( "dialog", Font.BOLD, 12 );
        from.setFont( labelFont );
        to.setFont( labelFont );
        okButton.setFont( labelFont );

        dialogTop.add( from );
        dialogTop.add( customRangeStart );
        dialogTop.add( to );
        dialogTop.add( customRangeEnd );
        dialogBottom.add( okButton );
        okButton.addActionListener( this );

        customRangeDialog.getContentPane().setLayout( new BorderLayout() );
        customRangeDialog.getContentPane().add( "North", dialogTop );
        customRangeDialog.getContentPane().add( "South", dialogBottom );
        customRangeDialog.pack();
    }

    /// Return the range that is currently selected

    public int[] getSelectedRange() {
        if ( useCustomRange ) {
            int startIndex, endIndex;
            String startText, endText;
            String empty = "";
            try {
                startText = customRangeStart.getText().trim();
                endText = customRangeEnd.getText().trim();
                if ( startText.equals(empty) && !endText.equals(empty) ) {
                    endIndex = Integer.parseInt( endText, 16 );
                    startIndex = endIndex - 7*25;
                }
                else if ( !startText.equals(empty) && endText.equals(empty) ) {
                    startIndex = Integer.parseInt( startText, 16 );
                    endIndex = startIndex + 7*25;
                }
                else {
                    startIndex = Integer.parseInt( customRangeStart.getText(), 16 );
                    endIndex = Integer.parseInt( customRangeEnd.getText(), 16 );
                }
            }
            catch ( Exception e ) {
                /// Error in parsing the hex number ---
                /// Reset the range to what it was before and return that
                customRangeStart.setText( Integer.toString( customRange[0], 16 ));
                customRangeEnd.setText( Integer.toString( customRange[1], 16 ));
                return customRange;
            }

            if ( startIndex < 0 )
              startIndex = 0;
            if ( endIndex > 0xffff )
              endIndex = 0xffff;
            if ( startIndex > endIndex )
              startIndex = endIndex;

            customRange[0] = startIndex;
            customRange[1] = endIndex;
            return customRange;
        }
        else
          return UNICODE_RANGES[ getSelectedIndex() ];
    }

    /// Function used by loadOptions in Font2DTest main panel
    /// to reset setting and range selection
    public void setSelectedRange( String name, int start, int end ) {
        setSelectedItem( name );
        customRange[0] = start;
        customRange[1] = end;
        parent.fireRangeChanged();
    }

    /// ActionListener interface function
    /// ABP
    /// moved JComboBox event code into this fcn from
    /// itemStateChanged() method. Part of change to Swing.
    public void actionPerformed( ActionEvent e ) {
        Object source = e.getSource();

        if ( source instanceof JComboBox ) {
                String rangeName = (String)((JComboBox)source).getSelectedItem();

                if ( rangeName.equals("Custom...") ) {
                    useCustomRange = true;
                    customRangeDialog.setLocationRelativeTo(parent);
                    customRangeDialog.show();
                }
                else {
                  useCustomRange = false;
                }
                parent.fireRangeChanged();
        }
        else if ( source instanceof JButton ) {
                /// Since it is only "OK" button that sends any action here...
                customRangeDialog.hide();
        }
    }
}
