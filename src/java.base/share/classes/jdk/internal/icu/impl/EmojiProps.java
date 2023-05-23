// Copyright 2021 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

// emojiprops.h
// created: 2021sep06 Markus W. Scherer

package jdk.internal.icu.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

import jdk.internal.icu.lang.UProperty;
import jdk.internal.icu.text.UnicodeSet;
import jdk.internal.icu.util.BytesTrie;
import jdk.internal.icu.util.CharsTrie;
import jdk.internal.icu.util.CodePointMap;
import jdk.internal.icu.util.CodePointTrie;

public final class EmojiProps {
    private static final class IsAcceptable implements ICUBinary.Authenticate {
        @Override
        public boolean isDataVersionAcceptable(byte version[]) {
            return version[0] == 1;
        }
    }
    private static final IsAcceptable IS_ACCEPTABLE = new IsAcceptable();
    private static final int DATA_FORMAT = 0x456d6f6a;  // "Emoj"

    // Byte offsets from the start of the data, after the generic header,
    // in ascending order.
    // UCPTrie=CodePointTrie, follows the indexes
    private static final int IX_CPTRIE_OFFSET = 0;

    // UCharsTrie=CharsTrie
    private static final int IX_BASIC_EMOJI_TRIE_OFFSET = 4;
    //ivate static final int IX_EMOJI_KEYCAP_SEQUENCE_TRIE_OFFSET = 5;
    //ivate static final int IX_RGI_EMOJI_MODIFIER_SEQUENCE_TRIE_OFFSET = 6;
    //ivate static final int IX_RGI_EMOJI_FLAG_SEQUENCE_TRIE_OFFSET = 7;
    //ivate static final int IX_RGI_EMOJI_TAG_SEQUENCE_TRIE_OFFSET = 8;
    private static final int IX_RGI_EMOJI_ZWJ_SEQUENCE_TRIE_OFFSET = 9;

    // Properties in the code point trie.
    // https://www.unicode.org/reports/tr51/#Emoji_Properties
    private static final int BIT_EMOJI = 0;
    private static final int BIT_EMOJI_PRESENTATION = 1;
    private static final int BIT_EMOJI_MODIFIER = 2;
    private static final int BIT_EMOJI_MODIFIER_BASE = 3;
    private static final int BIT_EMOJI_COMPONENT = 4;
    private static final int BIT_EXTENDED_PICTOGRAPHIC = 5;
    // https://www.unicode.org/reports/tr51/#Emoji_Sets
    private static final int BIT_BASIC_EMOJI = 6;

    public static final EmojiProps INSTANCE = new EmojiProps();

    private CodePointTrie.Fast8 cpTrie = null;
    private String stringTries[] = new String[6];

    /** Input i: One of the IX_..._TRIE_OFFSET indexes into the data file indexes[] array. */
    private static int getStringTrieIndex(int i) {
        return i - IX_BASIC_EMOJI_TRIE_OFFSET;
    }

    private EmojiProps() {
        ByteBuffer bytes = ICUBinary.getRequiredData("uemoji.icu");
        try {
            ICUBinary.readHeaderAndDataVersion(bytes, DATA_FORMAT, IS_ACCEPTABLE);
            int startPos = bytes.position();

            int cpTrieOffset = bytes.getInt();  // inIndexes[IX_CPTRIE_OFFSET]
            int indexesLength = cpTrieOffset / 4;
            if (indexesLength <= IX_RGI_EMOJI_ZWJ_SEQUENCE_TRIE_OFFSET) {
                throw new UncheckedIOException(new IOException(
                        "Emoji properties data: not enough indexes"));
            }

            int[] inIndexes = new int[indexesLength];
            inIndexes[0] = cpTrieOffset;
            for (int i = 1; i < indexesLength; ++i) {
                inIndexes[i] = bytes.getInt();
            }

            int i = IX_CPTRIE_OFFSET;
            int offset = inIndexes[i++];
            int nextOffset = inIndexes[i];
            cpTrie = CodePointTrie.Fast8.fromBinary(bytes);
            int pos = bytes.position() - startPos;
            assert nextOffset >= pos;
            ICUBinary.skipBytes(bytes, nextOffset - pos);  // skip padding after trie bytes

            offset = nextOffset;
            nextOffset = inIndexes[IX_BASIC_EMOJI_TRIE_OFFSET];
            ICUBinary.skipBytes(bytes, nextOffset - offset);  // skip unknown bytes

            for (i = IX_BASIC_EMOJI_TRIE_OFFSET; i <= IX_RGI_EMOJI_ZWJ_SEQUENCE_TRIE_OFFSET; ++i) {
                offset = inIndexes[i];
                nextOffset = inIndexes[i + 1];
                // Set/leave null if there is no CharsTrie.
                if (nextOffset > offset) {
                    stringTries[getStringTrieIndex(i)] =
                            ICUBinary.getString(bytes, (nextOffset - offset) / 2, 0);
                }
            }
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public UnicodeSet addPropertyStarts(UnicodeSet set) {
        // Add the start code point of each same-value range of the trie.
        CodePointMap.Range range = new CodePointMap.Range();
        int start = 0;
        while (cpTrie.getRange(start, null, range)) {
            set.add(start);
            start = range.getEnd() + 1;
        }
        return set;
    }

    // Note: REGIONAL_INDICATOR is a single, hardcoded range implemented elsewhere.
    private static final byte[] bitFlags = {
        BIT_EMOJI,                  // UCHAR_EMOJI=57
        BIT_EMOJI_PRESENTATION,     // UCHAR_EMOJI_PRESENTATION=58
        BIT_EMOJI_MODIFIER,         // UCHAR_EMOJI_MODIFIER=59
        BIT_EMOJI_MODIFIER_BASE,    // UCHAR_EMOJI_MODIFIER_BASE=60
        BIT_EMOJI_COMPONENT,        // UCHAR_EMOJI_COMPONENT=61
        -1,                         // UCHAR_REGIONAL_INDICATOR=62
        -1,                         // UCHAR_PREPENDED_CONCATENATION_MARK=63
        BIT_EXTENDED_PICTOGRAPHIC,  // UCHAR_EXTENDED_PICTOGRAPHIC=64
        BIT_BASIC_EMOJI,            // UCHAR_BASIC_EMOJI=65
        -1,                         // UCHAR_EMOJI_KEYCAP_SEQUENCE=66
        -1,                         // UCHAR_RGI_EMOJI_MODIFIER_SEQUENCE=67
        -1,                         // UCHAR_RGI_EMOJI_FLAG_SEQUENCE=68
        -1,                         // UCHAR_RGI_EMOJI_TAG_SEQUENCE=69
        -1,                         // UCHAR_RGI_EMOJI_ZWJ_SEQUENCE=70
        BIT_BASIC_EMOJI,            // UCHAR_RGI_EMOJI=71
    };

    public boolean hasBinaryProperty(int c, int which) {
        if (which < UProperty.EMOJI || UProperty.RGI_EMOJI < which) {
            return false;
        }
        int bit = bitFlags[which - UProperty.EMOJI];
        if (bit < 0) {
            return false;  // not a property that we support in this function
        }
        int bits = cpTrie.get(c);
        return ((bits >> bit) & 1) != 0;
    }

    public boolean hasBinaryProperty(CharSequence s, int which) {
        int length = s.length();
        if (length == 0) { return false; }  // empty string
        // The caller should have delegated single code points to hasBinaryProperty(c, which).
        if (which < UProperty.BASIC_EMOJI || UProperty.RGI_EMOJI < which) {
            return false;
        }
        int firstProp = which, lastProp = which;
        if (which == UProperty.RGI_EMOJI) {
            // RGI_Emoji is the union of the other emoji properties of strings.
            firstProp = UProperty.BASIC_EMOJI;
            lastProp = UProperty.RGI_EMOJI_ZWJ_SEQUENCE;
        }
        for (int prop = firstProp; prop <= lastProp; ++prop) {
            String trieUChars = stringTries[prop - UProperty.BASIC_EMOJI];
            if (trieUChars != null) {
                CharsTrie trie = new CharsTrie(trieUChars, 0);
                BytesTrie.Result result = trie.next(s, 0, length);
                if (result.hasValue()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void addStrings(int which, UnicodeSet set) {
        if (which < UProperty.BASIC_EMOJI || UProperty.RGI_EMOJI < which) {
            return;
        }
        int firstProp = which, lastProp = which;
        if (which == UProperty.RGI_EMOJI) {
            // RGI_Emoji is the union of the other emoji properties of strings.
            firstProp = UProperty.BASIC_EMOJI;
            lastProp = UProperty.RGI_EMOJI_ZWJ_SEQUENCE;
        }
        for (int prop = firstProp; prop <= lastProp; ++prop) {
            String trieUChars = stringTries[prop - UProperty.BASIC_EMOJI];
            if (trieUChars != null) {
                CharsTrie trie = new CharsTrie(trieUChars, 0);
                for (CharsTrie.Entry entry : trie) {
                    set.add(entry.chars);
                }
            }
        }
    }
}
