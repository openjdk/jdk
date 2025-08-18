package java.lang;

import jdk.internal.java.lang.CaseFolding;

abstract class StringCaseFoldedCharIterator {

    protected final byte[] value;  // underlying byte array
    protected int index;           // current position in byte array
    protected char[] folded;       // buffer for folded expansion
    protected int foldedIndex;     // position in folded[]

    StringCaseFoldedCharIterator(byte[] value) {
        this.value = value;
        this.index = 0;
        this.folded = null;
        this.foldedIndex = 0;
    }

    public boolean hasNext() {
        return (folded != null && foldedIndex < folded.length) || index < value.length;
    }

    public int nextChar() {
        if (folded != null && foldedIndex < folded.length) {
            return folded[foldedIndex++];
        }
        if (index >= value.length) {
            return -1;
        }
        int cp = codePointAt(value, index);
        index += Character.charCount(cp);
        folded = CaseFolding.fold(cp);
        foldedIndex = 0;
        return folded[foldedIndex++];
    }

    protected abstract int codePointAt(byte[] value, int index);

    // Factory for Latin1
    static StringCaseFoldedCharIterator ofLatin1(byte[] value) {
        return new StringCaseFoldedCharIterator(value) {
            @Override
            protected int codePointAt(byte[] value, int index) {
                return StringLatin1.codePointAt(value, index, value.length);
            }
        };
    }

    // Factory for UTF16
    static StringCaseFoldedCharIterator ofUTF16(byte[] value) {
        return new StringCaseFoldedCharIterator(value) {
            @Override
            protected int codePointAt(byte[] value, int index) {
                return StringUTF16.codePointAt(value, index, value.length);
            }
        };
    }
}
