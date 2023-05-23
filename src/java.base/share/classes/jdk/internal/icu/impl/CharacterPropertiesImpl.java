// Copyright 2018 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
package jdk.internal.icu.impl;

import jdk.internal.icu.lang.UCharacter;
import jdk.internal.icu.lang.UProperty;
import jdk.internal.icu.text.UnicodeSet;

/**
 * Properties functionality above class UCharacterProperty
 * but below class CharacterProperties and class UnicodeSet.
 */
@SuppressWarnings("deprecation")
public final class CharacterPropertiesImpl {
    private static final int NUM_INCLUSIONS = UCharacterProperty.SRC_COUNT +
            UProperty.INT_LIMIT - UProperty.INT_START;

    /**
     * A set of all characters _except_ the second through last characters of
     * certain ranges. These ranges are ranges of characters whose
     * properties are all exactly alike, e.g. CJK Ideographs from
     * U+4E00 to U+9FA5.
     */
    private static final UnicodeSet inclusions[] = new UnicodeSet[NUM_INCLUSIONS];

    /** For {@link UnicodeSet#setDefaultXSymbolTable}. */
    public static synchronized void clear() {
        for (int i = 0; i < inclusions.length; ++i) {
            inclusions[i] = null;
        }
    }

    private static UnicodeSet getInclusionsForSource(int src) {
        if (inclusions[src] == null) {
            UnicodeSet incl = new UnicodeSet();
            switch(src) {
            case UCharacterProperty.SRC_CHAR:
                UCharacterProperty.INSTANCE.addPropertyStarts(incl);
                break;
            case UCharacterProperty.SRC_PROPSVEC:
                UCharacterProperty.INSTANCE.upropsvec_addPropertyStarts(incl);
                break;
            case UCharacterProperty.SRC_CHAR_AND_PROPSVEC:
                UCharacterProperty.INSTANCE.addPropertyStarts(incl);
                UCharacterProperty.INSTANCE.upropsvec_addPropertyStarts(incl);
                break;
            case UCharacterProperty.SRC_CASE_AND_NORM:
                Norm2AllModes.getNFCInstance().impl.addPropertyStarts(incl);
                UCaseProps.INSTANCE.addPropertyStarts(incl);
                break;
            case UCharacterProperty.SRC_NFC:
                Norm2AllModes.getNFCInstance().impl.addPropertyStarts(incl);
                break;
            case UCharacterProperty.SRC_NFKC:
                Norm2AllModes.getNFKCInstance().impl.addPropertyStarts(incl);
                break;
            case UCharacterProperty.SRC_NFKC_CF:
                Norm2AllModes.getNFKC_CFInstance().impl.addPropertyStarts(incl);
                break;
            case UCharacterProperty.SRC_NFC_CANON_ITER:
                Norm2AllModes.getNFCInstance().impl.addCanonIterPropertyStarts(incl);
                break;
            case UCharacterProperty.SRC_CASE:
                UCaseProps.INSTANCE.addPropertyStarts(incl);
                break;
            case UCharacterProperty.SRC_BIDI:
                UBiDiProps.INSTANCE.addPropertyStarts(incl);
                break;
            case UCharacterProperty.SRC_INPC:
            case UCharacterProperty.SRC_INSC:
            case UCharacterProperty.SRC_VO:
                UCharacterProperty.ulayout_addPropertyStarts(src, incl);
                break;
            case UCharacterProperty.SRC_EMOJI: {
                EmojiProps.INSTANCE.addPropertyStarts(incl);
                break;
            }
            default:
                throw new IllegalStateException("getInclusions(unknown src " + src + ")");
            }
            // We do not freeze() the set because we only iterate over it,
            // rather than testing contains(),
            // so the extra time and memory to optimize that are not necessary.
            inclusions[src] = incl.compact();
        }
        return inclusions[src];
    }

    private static UnicodeSet getIntPropInclusions(int prop) {
        assert(UProperty.INT_START <= prop && prop < UProperty.INT_LIMIT);
        int inclIndex = UCharacterProperty.SRC_COUNT + prop - UProperty.INT_START;
        if (inclusions[inclIndex] != null) {
            return inclusions[inclIndex];
        }
        int src = UCharacterProperty.INSTANCE.getSource(prop);
        UnicodeSet incl = getInclusionsForSource(src);

        UnicodeSet intPropIncl = new UnicodeSet(0, 0);
        int numRanges = incl.getRangeCount();
        int prevValue = 0;
        for (int i = 0; i < numRanges; ++i) {
            int rangeEnd = incl.getRangeEnd(i);
            for (int c = incl.getRangeStart(i); c <= rangeEnd; ++c) {
                // TODO: Get a UCharacterProperty.IntProperty to avoid the property dispatch.
                int value = UCharacter.getIntPropertyValue(c, prop);
                if (value != prevValue) {
                    intPropIncl.add(c);
                    prevValue = value;
                }
            }
        }

        // Compact for caching.
        return inclusions[inclIndex] = intPropIncl.compact();
    }

    /**
     * Returns a mutable UnicodeSet -- do not modify!
     */
    public static synchronized UnicodeSet getInclusionsForProperty(int prop) {
        if (UProperty.INT_START <= prop && prop < UProperty.INT_LIMIT) {
            return getIntPropInclusions(prop);
        } else {
            int src = UCharacterProperty.INSTANCE.getSource(prop);
            return getInclusionsForSource(src);
        }
    }
}
