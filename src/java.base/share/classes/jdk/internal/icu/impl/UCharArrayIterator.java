// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/**
*******************************************************************************
* Copyright (C) 1996-2010, International Business Machines Corporation and    *
* others. All Rights Reserved.                                                *
*******************************************************************************
*/

package jdk.internal.icu.impl;

import jdk.internal.icu.text.UCharacterIterator;


/**
 * @author Doug Felt
 *
 */

public final class UCharArrayIterator extends UCharacterIterator {
    private final char[] text;
    private final int start;
    private final int limit;
    private int pos;

    public UCharArrayIterator(char[] text, int start, int limit) {
        if (start < 0 || limit > text.length || start > limit) {
            throw new IllegalArgumentException("start: " + start + " or limit: "
                                               + limit + " out of range [0, "
                                               + text.length + ")");
        }
        this.text = text;
        this.start = start;
        this.limit = limit;

        this.pos = start;
    }

    @Override
    public int current() {
        return pos < limit ? text[pos] : DONE;
    }

    @Override
    public int getLength() {
        return limit - start;
    }

    @Override
    public int getIndex() {
        return pos - start;
    }

    @Override
    public int next() {
        return pos < limit ? text[pos++] : DONE;
    }

    @Override
    public int previous() {
        return pos > start ? text[--pos] : DONE;
    }

    @Override
    public void setIndex(int index) {
        if (index < 0 || index > limit - start) {
            throw new IndexOutOfBoundsException("index: " + index +
                                                " out of range [0, "
                                                + (limit - start) + ")");
        }
        pos = start + index;
    }

    @Override
    public int getText(char[] fillIn, int offset) {
        int len = limit - start;
        System.arraycopy(text, start, fillIn, offset, len);
        return len;
    }

    /**
     * Creates a copy of this iterator, does not clone the underlying
     * <code>Replaceable</code>object
     * @return copy of this iterator
     */
    @Override
    public Object clone(){
        try {
          return super.clone();
        } catch (CloneNotSupportedException e) {
            return null; // never invoked
        }
    }
}
