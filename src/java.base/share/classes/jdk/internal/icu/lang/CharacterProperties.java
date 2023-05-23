// Copyright 2018 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package jdk.internal.icu.lang;

import jdk.internal.icu.impl.CharacterPropertiesImpl;
import jdk.internal.icu.impl.EmojiProps;
import jdk.internal.icu.text.UnicodeSet;
import jdk.internal.icu.util.CodePointMap;
import jdk.internal.icu.util.CodePointTrie;
import jdk.internal.icu.util.MutableCodePointTrie;

/**
 * Sets and maps for Unicode properties.
 * The methods here return an object per property:
 * A set for each ICU-supported binary property with all code points for which the property is true.
 * A map for each ICU-supported enumerated/catalog/int-valued property
 * which maps all Unicode code points to their values for that property.
 *
 * <p>For details see the method descriptions.
 * For lookup of property values by code point see class {@link UCharacter}.
 *
 * @stable ICU 63
 */
@SuppressWarnings("deprecation")
public final class CharacterProperties {
    private CharacterProperties() {}  // all-static

    private static final UnicodeSet sets[] = new UnicodeSet[UProperty.BINARY_LIMIT];
    private static final CodePointMap maps[] = new CodePointMap[UProperty.INT_LIMIT - UProperty.INT_START];

    private static UnicodeSet makeSet(int property) {
        UnicodeSet set = new UnicodeSet();
        if (UProperty.BASIC_EMOJI <= property && property <= UProperty.RGI_EMOJI) {
            // property of strings
            EmojiProps.INSTANCE.addStrings(property, set);
            if (property != UProperty.BASIC_EMOJI && property != UProperty.RGI_EMOJI) {
                // property of _only_ strings
                return set.freeze();
            }
        }

        UnicodeSet inclusions = CharacterPropertiesImpl.getInclusionsForProperty(property);
        int numRanges = inclusions.getRangeCount();
        int startHasProperty = -1;

        for (int i = 0; i < numRanges; ++i) {
            int rangeEnd = inclusions.getRangeEnd(i);
            for (int c = inclusions.getRangeStart(i); c <= rangeEnd; ++c) {
                // TODO: Get a UCharacterProperty.BinaryProperty to avoid the property dispatch.
                if (UCharacter.hasBinaryProperty(c, property)) {
                    if (startHasProperty < 0) {
                        // Transition from false to true.
                        startHasProperty = c;
                    }
                } else if (startHasProperty >= 0) {
                    // Transition from true to false.
                    set.add(startHasProperty, c - 1);
                    startHasProperty = -1;
                }
            }
        }
        if (startHasProperty >= 0) {
            set.add(startHasProperty, 0x10FFFF);
        }

        return set.freeze();
    }

    private static CodePointMap makeMap(int property) {
        int nullValue = property == UProperty.SCRIPT ? UScript.UNKNOWN : 0;
        MutableCodePointTrie mutableTrie = new MutableCodePointTrie(nullValue, nullValue);
        UnicodeSet inclusions = CharacterPropertiesImpl.getInclusionsForProperty(property);
        int numRanges = inclusions.getRangeCount();
        int start = 0;
        int value = nullValue;

        for (int i = 0; i < numRanges; ++i) {
            int rangeEnd = inclusions.getRangeEnd(i);
            for (int c = inclusions.getRangeStart(i); c <= rangeEnd; ++c) {
                // TODO: Get a UCharacterProperty.IntProperty to avoid the property dispatch.
                int nextValue = UCharacter.getIntPropertyValue(c, property);
                if (value != nextValue) {
                    if (value != nullValue) {
                        mutableTrie.setRange(start, c - 1, value);
                    }
                    start = c;
                    value = nextValue;
                }
            }
        }
        if (value != 0) {
            mutableTrie.setRange(start, 0x10FFFF, value);
        }

        CodePointTrie.Type type;
        if (property == UProperty.BIDI_CLASS || property == UProperty.GENERAL_CATEGORY) {
            type = CodePointTrie.Type.FAST;
        } else {
            type = CodePointTrie.Type.SMALL;
        }
        CodePointTrie.ValueWidth valueWidth;
        // TODO: UCharacterProperty.IntProperty
        int max = UCharacter.getIntPropertyMaxValue(property);
        if (max <= 0xff) {
            valueWidth = CodePointTrie.ValueWidth.BITS_8;
        } else if (max <= 0xffff) {
            valueWidth = CodePointTrie.ValueWidth.BITS_16;
        } else {
            valueWidth = CodePointTrie.ValueWidth.BITS_32;
        }
        return mutableTrie.buildImmutable(type, valueWidth);
    }

    /**
     * Returns a frozen UnicodeSet for a binary property.
     * Throws an exception if the property number is not one for a binary property.
     *
     * <p>The returned set contains all code points for which the property is true.
     *
     * @param property {@link UProperty#BINARY_START}..{@link UProperty#BINARY_LIMIT}-1
     * @return the property as a set
     * @see UProperty
     * @see UCharacter#hasBinaryProperty
     * @stable ICU 63
     */
    public static final UnicodeSet getBinaryPropertySet(int property) {
        if (property < 0 || UProperty.BINARY_LIMIT <= property) {
            throw new IllegalArgumentException("" + property +
                    " is not a constant for a UProperty binary property");
        }
        synchronized(sets) {
            UnicodeSet set = sets[property];
            if (set == null) {
                sets[property] = set = makeSet(property);
            }
            return set;
        }
    }

    /**
     * Returns an immutable CodePointMap for an enumerated/catalog/int-valued property.
     * Throws an exception if the property number is not one for an "int property".
     *
     * <p>The returned object maps all Unicode code points to their values for that property.
     * For documentation of the integer values see {@link UCharacter#getIntPropertyValue(int, int)}.
     *
     * <p>The actual type of the returned object differs between properties
     * and may change over time.
     *
     * @param property {@link UProperty#INT_START}..{@link UProperty#INT_LIMIT}-1
     * @return the property as a map
     * @see UProperty
     * @see UCharacter#getIntPropertyValue
     * @stable ICU 63
     */
    public static final CodePointMap getIntPropertyMap(int property) {
        if (property < UProperty.INT_START || UProperty.INT_LIMIT <= property) {
            throw new IllegalArgumentException("" + property +
                    " is not a constant for a UProperty int property");
        }
        synchronized(maps) {
            CodePointMap map = maps[property - UProperty.INT_START];
            if (map == null) {
                maps[property - UProperty.INT_START] = map = makeMap(property);
            }
            return map;
        }
    }
}
