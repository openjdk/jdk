// Copyright 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
package jdk.internal.icu.impl;

import java.text.CharacterIterator;

/**
 * Implement the Java CharacterIterator interface on a CharSequence.
 * Intended for internal use by ICU only.
 */
public class CSCharacterIterator implements CharacterIterator {

    private int index;
    private CharSequence seq;


    /**
     * Constructor.
     * @param text The CharSequence to iterate over.
     */
    public CSCharacterIterator(CharSequence text) {
        if (text == null) {
            throw new NullPointerException();
        }
        seq = text;
        index = 0;
    }

    /** @{inheritDoc} */
    @Override
    public char first() {
        index = 0;
        return current();
    }

    /** @{inheritDoc} */
    @Override
    public char last() {
        index = seq.length();
        return previous();
    }

    /** @{inheritDoc} */
    @Override
    public char current() {
        if (index == seq.length()) {
            return DONE;
        }
        return seq.charAt(index);
    }

    /** @{inheritDoc} */
    @Override
    public char next() {
        if (index < seq.length()) {
            ++index;
        }
        return current();
    }

    /** @{inheritDoc} */
    @Override
    public char previous() {
        if (index == 0) {
            return DONE;
        }
        --index;
        return current();
    }

    /** @{inheritDoc} */
    @Override
    public char setIndex(int position) {
        if (position < 0 || position > seq.length()) {
            throw new IllegalArgumentException();
        }
        index = position;
        return current();
    }

    /** @{inheritDoc} */
    @Override
    public int getBeginIndex() {
        return 0;
    }

    /** @{inheritDoc} */
    @Override
    public int getEndIndex() {
        return seq.length();
    }

    /** @{inheritDoc} */
    @Override
    public int getIndex() {
        return index;
    }

    /** @{inheritDoc} */
    @Override
    public Object clone() {
        CSCharacterIterator copy = new CSCharacterIterator(seq);
        copy.setIndex(index);
        return copy;
    }
}
