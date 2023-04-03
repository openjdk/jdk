/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.font;

import java.awt.Font;
import java.util.Arrays;
import java.util.List;

/* Remind: need to enhance to extend component list with a fallback
 * list, which is not used in metrics or queries on the composite, but
 * is used in drawing primitives and queries which supply an actual string.
 * ie for a codepoint that is only in a fallback, font-wide queries such
 * as FontMetrics.getHeight() will not take it into account.
 * But getStringBounds(..) would take it into account.
 * Its fuzzier for queries such as "canDisplay". If this does not include
 * the fallback, then we probably want to add "canDisplayFallback()"
 * But its probably OK to include it so long as only composites include
 * fallbacks. If physicals do then it would be really confusing ..
 */
public final class CompositeFont extends Font2D {

    private boolean[] deferredInitialisation;
    String[] componentFileNames;
    String[] componentNames;
    /* because components can be lazily initialised the components field is
     * private, to ensure all clients call getSlotFont()
     */
    private Font2D[] components;
    int numSlots;
    int numMetricsSlots;
    int[] exclusionRanges;
    int[] maxIndices;
    int numGlyphs = 0;
    int localeSlot = -1; // primary slot for this locale.
    int slotShift, slotMask;

    /* See isStdComposite() for when/how this is used */
    boolean isStdComposite = true;

    public CompositeFont(String name, String[] compFileNames,
                         String[] compNames, int metricsSlotCnt,
                         int[] exclRanges, int[] maxIndexes,
                         boolean defer, SunFontManager fm) {

        handle = new Font2DHandle(this);
        fullName = name;
        componentFileNames = compFileNames;
        componentNames = compNames;
        /* We will limit the number of slots to 255.
         * Previously we stored the slot for a glyph
         * id in a byte, and we may create a composite
         * using this composite as a backup for a physical font.
         * That's not a problem anymore, as number of bits
         * reserved for a slot index can vary (see #slotShift),
         * but the more it takes, the less is left for actual
         * glyph code, so we'll keep this limit for now.
         */
        List<Font2D> additionalFallbackFonts = fm.getAdditionalFallbackFonts();
        numSlots = Math.min(255, additionalFallbackFonts.size() +
                (compNames != null ? compNames.length : compFileNames.length));

        /* Only the first "numMetricsSlots" slots are used for font metrics.
         * the rest are considered "fallback" slots".
         */
        numMetricsSlots = Math.min(metricsSlotCnt, numSlots);
        exclusionRanges = exclRanges;
        maxIndices = maxIndexes;

        components = new Font2D[numSlots];
        deferredInitialisation = new boolean[numSlots];
        if (defer) {
            Arrays.fill(deferredInitialisation, 0, numMetricsSlots, true);
            Arrays.fill(deferredInitialisation, numMetricsSlots +
                    additionalFallbackFonts.size(), numSlots, true);
        }
        /*
         * Insert additional fallback fonts if any.
         * The caller could be responsible for this, but for now it seems
         * better that it is handled internally to the CompositeFont class.
         */
        if (!additionalFallbackFonts.isEmpty()) {
            int msCnt = numMetricsSlots;
            int adCnt = additionalFallbackFonts.size();
            int fbCnt = numSlots - msCnt - adCnt;
            if (componentNames != null) {
                componentNames = new String[numSlots];
                System.arraycopy(compNames, 0, componentNames, 0, msCnt);
                for (int i = 0; i < adCnt; i++) {
                    componentNames[msCnt + i] =
                            additionalFallbackFonts.get(i).getFontName(null);
                }
                System.arraycopy(compNames, msCnt, componentNames, msCnt+adCnt, fbCnt);
            }
            if (componentFileNames != null) {
                componentFileNames = new String[numSlots];
                System.arraycopy(compFileNames, 0, componentFileNames, 0, msCnt);
                System.arraycopy(compFileNames, msCnt, componentFileNames, msCnt+adCnt, fbCnt);
            }
            for (int i = 0; i < adCnt; i++) {
                components[msCnt + i] = additionalFallbackFonts.get(i);
            }
        }

        fontRank = Font2D.FONT_CONFIG_RANK;

        int index = fullName.indexOf('.');
        if (index>0) {
            familyName = fullName.substring(0, index);
            /* composites don't call setStyle() as parsing the style
             * takes place at the same time as parsing the family name.
             * Do I really have to parse the style from the name?
             * Need to look into having the caller provide this. */
            if (index+1 < fullName.length()) {
                String styleStr = fullName.substring(index+1);
                if ("plain".equals(styleStr)) {
                    style = Font.PLAIN;
                } else if ("bold".equals(styleStr)) {
                    style = Font.BOLD;
                } else if ("italic".equals(styleStr)) {
                    style = Font.ITALIC;
                } else if ("bolditalic".equals(styleStr)) {
                    style = Font.BOLD | Font.ITALIC;
                }
            }
        } else {
            familyName = fullName;
        }
        initSlotMask();
    }

    /*
     * Build a composite from a set of individual slot fonts.
     */
    CompositeFont(Font2D[] slotFonts) {

        isStdComposite = false;
        handle = new Font2DHandle(this);
        fullName = slotFonts[0].fullName;
        familyName = slotFonts[0].familyName;
        style = slotFonts[0].style;

        numMetricsSlots = 1; /* Only the physical Font */
        numSlots = slotFonts.length;

        components = new Font2D[numSlots];
        System.arraycopy(slotFonts, 0, components, 0, numSlots);
        deferredInitialisation = new boolean[numSlots]; // all false.
        initSlotMask();
    }

    /* This method is currently intended to be called only from
     * FontManager.getCompositeFontUIResource(Font)
     * It creates a new CompositeFont with the contents of the
     * one pre-pended as slot 0.
     */
    CompositeFont(Font2D font, CompositeFont compFont) {

        isStdComposite = false;
        handle = new Font2DHandle(this);
        fullName = font.fullName;
        familyName = font.familyName;
        style = font.style;

        numMetricsSlots = 1; /* Only the physical Font */
        numSlots = compFont.numSlots+1;

        /* Ugly though it is, we synchronize here on the FontManager class
         * because it is the lock used to do deferred initialisation.
         * We need to ensure that the arrays have consistent information.
         * But it may be possible to dispense with the synchronisation if
         * it is harmless that we do not know a slot is already initialised
         * and just need to discover that and mark it so.
         */
        synchronized (FontManagerFactory.getInstance()) {
            components = new Font2D[numSlots];
            components[0] = font;
            System.arraycopy(compFont.components, 0,
                             components, 1, compFont.numSlots);

            if (compFont.componentNames != null) {
                componentNames = new String[numSlots];
                componentNames[0] = font.fullName;
                System.arraycopy(compFont.componentNames, 0,
                                 componentNames, 1, compFont.numSlots);
            }
            if (compFont.componentFileNames != null) {
                componentFileNames = new String[numSlots];
                componentFileNames[0] = null;
                System.arraycopy(compFont.componentFileNames, 0,
                                  componentFileNames, 1, compFont.numSlots);
            }
            deferredInitialisation = new boolean[numSlots];
            deferredInitialisation[0] = false;
            System.arraycopy(compFont.deferredInitialisation, 0,
                             deferredInitialisation, 1, compFont.numSlots);
        }
        initSlotMask();
    }

    private void initSlotMask() {
        // slotShift = number of lowest bits encoding the slot index.
        slotShift = 32 - Integer.numberOfLeadingZeros(numSlots - 1); // ceil(log2(numSlots))
        slotMask = (1 << slotShift) - 1; // Mask for extracting the slot index.
    }

    int decodeSlot(int glyphCode) {
        return glyphCode & slotMask;
    }

    int decodeGlyphCode(int glyphCode) {
        return glyphCode >>> slotShift;
    }

    int compositeGlyphCode(int slot, int glyphCode) {
        return (glyphCode << slotShift) | (slot & slotMask);
    }

    /* This is used for deferred initialisation, so that the components of
     * a logical font are initialised only when the font is used.
     * This can have a positive impact on start-up of most UI applications.
     * Note that this technique cannot be used with a TTC font as it
     * doesn't know which font in the collection is needed. The solution to
     * this is that the initialisation checks if the returned font is
     * really the one it wants by comparing the name against the name that
     * was passed in (if none was passed in then you aren't using a TTC
     * as you would have to specify the name in such a case).
     * Assuming there's only two or three fonts in a collection then it
     * may be sufficient to verify the returned name is the expected one.
     * But half the time it won't be. However since initialisation of the
     * TTC will initialise all its components then just do a findFont2D call
     * to locate the right one.
     * This code allows for initialisation of each slot on demand.
     * There are two issues with this.
     * 1) All metrics slots probably may be initialised anyway as many
     * apps will query the overall font metrics. However this is not an
     * absolute requirement
     * 2) Some font configuration files on Solaris reference two versions
     * of a TT font: a Latin-1 version, then a Pan-European version.
     * One from /usr/openwin/lib/X11/fonts/TrueType, the other from
     * a euro_fonts directory which is symlinked from numerous locations.
     * This is difficult to avoid because the two do not share XLFDs so
     * both will be consequently mapped by separate XLFDs needed by AWT.
     * The difficulty this presents for lazy initialisation is that if
     * all the components are not mapped at once, the smaller version may
     * have been used only to be replaced later, and what is the consequence
     * for a client that displayed the contents of this font already.
     * After some thought I think this will not be a problem because when
     * client tries to display a glyph only in the Euro font, the composite
     * will ask all components of this font for that glyph and will get
     * the euro one. Subsequent uses will all come from the 100% compatible
     * euro one.
     */
    private void doDeferredInitialisation(int slot) {
        if (deferredInitialisation[slot] == false) {
            return;
        }

        /* Synchronize on FontManager so that is the global lock
         * to update its static set of deferred fonts.
         * This global lock is rarely likely to be an issue as there
         * are only going to be a few calls into this code.
         */
        SunFontManager fm = SunFontManager.getInstance();
        synchronized (fm) {
            if (componentNames == null) {
                componentNames = new String[numSlots];
            }
            if (components[slot] == null) {
                /* Warning: it is possible that the returned component is
                 * not derived from the file name argument, this can happen if:
                 * - the file can't be found
                 * - the file has a bad font
                 * - the font in the file is superseded by a more complete one
                 * This should not be a problem for composite font as it will
                 * make no further use of this file, but code debuggers/
                 * maintainers need to be conscious of this possibility.
                 */
                if (componentFileNames != null &&
                    componentFileNames[slot] != null) {
                    components[slot] =
                        fm.initialiseDeferredFont(componentFileNames[slot]);
                }

                if (components[slot] == null) {
                    components[slot] = fm.getDefaultPhysicalFont();
                }
                String name = components[slot].getFontName(null);
                if (componentNames[slot] == null) {
                    componentNames[slot] = name;
                } else if (!componentNames[slot].equalsIgnoreCase(name)) {
                    /* If a component specifies the file with a bad font,
                     * the corresponding slot will be initialized by
                     * default physical font. In such case findFont2D may
                     * return composite font which cannot be casted to
                     * physical font.
                     */
                    components[slot] =
                        fm.findFont2D(componentNames[slot],
                                                     style,
                                            FontManager.PHYSICAL_FALLBACK);
                }
            }
            deferredInitialisation[slot] = false;
        }
    }

    /* To called only by FontManager.replaceFont */
    void replaceComponentFont(Font2D oldFont, Font2D newFont) {
        if (components == null) {
            return;
        }
        for (int slot=0; slot<numSlots; slot++) {
            if (components[slot] == oldFont) {
                components[slot] = newFont;
                if (componentNames != null) {
                    componentNames[slot] = newFont.getFontName(null);
                }
            }
        }
    }

    public boolean isExcludedChar(int slot, int charcode) {

        if (exclusionRanges == null || maxIndices == null ||
            slot >= numMetricsSlots) {
            return false;
        }

        int minIndex = 0;
        int maxIndex = maxIndices[slot];
        if (slot > 0) {
            minIndex = maxIndices[slot - 1];
        }
        int curIndex = minIndex;
        while (maxIndex > curIndex) {
            if ((charcode >= exclusionRanges[curIndex])
                && (charcode <= exclusionRanges[curIndex+1])) {
                return true;      // excluded
            }
            curIndex += 2;
        }
        return false;
    }

    public void getStyleMetrics(float pointSize, float[] metrics, int offset) {
        Font2D font = getSlotFont(0);
        if (font == null) { // possible?
            super.getStyleMetrics(pointSize, metrics, offset);
        } else {
            font.getStyleMetrics(pointSize, metrics, offset);
        }
    }

    public int getNumSlots() {
        return numSlots;
    }

    public Font2D getSlotFont(int slot) {
        /* This is essentially the runtime overhead for deferred font
         * initialisation: a boolean test on obtaining a slot font,
         * which will happen per slot, on initialisation of a strike
         * (as that is the only frequent call site of this method.
         */
        if (deferredInitialisation[slot]) {
            doDeferredInitialisation(slot);
        }
        SunFontManager fm = SunFontManager.getInstance();
        try {
            Font2D font = components[slot];
            if (font == null) {
                font = fm.
                    findFont2D(componentNames[slot], style,
                               FontManager.PHYSICAL_FALLBACK);
                components[slot] = font;
            }
            return font;
        } catch (Exception e) {
            return fm.getDefaultPhysicalFont();
        }
    }

    FontStrike createStrike(FontStrikeDesc desc) {
        return new CompositeStrike(this, desc);
    }

    /* This is set false when the composite is created using a specified
     * physical font as the first slot and called by code which
     * selects composites by locale preferences to know that this
     * isn't a font which should be adjusted.
     */
    public boolean isStdComposite() {
        return isStdComposite;
    }

    /* This isn't very efficient but its infrequently used.
     * StandardGlyphVector uses it when the client assigns the glyph codes.
     * These may not be valid. This validates them substituting the missing
     * glyph elsewhere.
     */
    protected int getValidatedGlyphCode(int glyphCode) {
        int slot = decodeSlot(glyphCode);
        if (slot >= numSlots) {
            return getMapper().getMissingGlyphCode();
        }

        int slotglyphCode = decodeGlyphCode(glyphCode);
        Font2D slotFont = getSlotFont(slot);
        if (slotFont.getValidatedGlyphCode(slotglyphCode) ==
            slotFont.getMissingGlyphCode()) {
            return getMapper().getMissingGlyphCode();
        } else {
            return glyphCode;
        }
    }

    public CharToGlyphMapper getMapper() {
        if (mapper == null) {
            mapper = new CompositeGlyphMapper(this);
        }
        return mapper;
    }

    public boolean hasSupplementaryChars() {
        for (int i=0; i<numSlots; i++) {
            if (getSlotFont(i).hasSupplementaryChars()) {
                return true;
            }
        }
        return false;
    }

    public int getNumGlyphs() {
        if (numGlyphs == 0) {
            numGlyphs = getMapper().getNumGlyphs();
        }
        return numGlyphs;
    }

    public int getMissingGlyphCode() {
        return getMapper().getMissingGlyphCode();
    }

    public boolean canDisplay(char c) {
        return getMapper().canDisplay(c);
    }

    public boolean useAAForPtSize(int ptsize) {
        /* Find the first slot that supports the default encoding and use
         * that to decide the "gasp" behaviour of the composite font.
         * REMIND "default encoding" isn't applicable to a Unicode locale
         * and we need to replace this with a better mechanism for deciding
         * if a font "supports" the user's language. See TrueTypeFont.java
         */
        if (localeSlot == -1) {
            /* Ordinarily check numMetricsSlots, but non-standard composites
             * set that to "1" whilst not necessarily supporting the default
             * encoding with that first slot. In such a case check all slots.
             */
            int numCoreSlots = numMetricsSlots;
            if (numCoreSlots == 1 && !isStdComposite()) {
                numCoreSlots = numSlots;
            }
            for (int slot=0; slot<numCoreSlots; slot++) {
                 if (getSlotFont(slot).supportsEncoding(null)) {
                     localeSlot = slot;
                     break;
                 }
            }
            if (localeSlot == -1) {
                localeSlot = 0;
            }
        }
        return getSlotFont(localeSlot).useAAForPtSize(ptsize);
    }

    @Override
    public SlotInfo getSlotInfoForGlyph(int glyphCode) {
        SlotInfo info = getSlotFont(decodeSlot(glyphCode))
                .getSlotInfoForGlyph(decodeGlyphCode(glyphCode));
        info.slotShift += slotShift;
        return info;
    }

    public String toString() {
        String ls = System.lineSeparator();
        String componentsStr = "";
        for (int i=0; i<numSlots; i++) {
            componentsStr += "    Slot["+i+"]="+getSlotFont(i)+ls;
        }
        return "** Composite Font: Family=" + familyName +
            " Name=" + fullName + " style=" + style + ls + componentsStr;
    }
}
