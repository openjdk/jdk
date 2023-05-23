// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2010, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package jdk.internal.icu.impl;

import java.text.CharacterIterator;

import jdk.internal.icu.text.UCharacterIterator;

/**
 * This class is a wrapper around UCharacterIterator and implements the
 * CharacterIterator protocol
 * @author ram
 */
public class UCharacterIteratorWrapper implements CharacterIterator{

    public UCharacterIteratorWrapper(UCharacterIterator iter){
        this.iterator = iter;
    }

    private UCharacterIterator iterator;


    /**
     * Sets the position to getBeginIndex() and returns the character at that
     * position.
     * @return the first character in the text, or DONE if the text is empty
     * @see #getBeginIndex()
     */
    @Override
    public char first(){
        //UCharacterIterator always iterates from 0 to length
        iterator.setToStart();
        return (char)iterator.current();
    }

    /**
     * Sets the position to getEndIndex()-1 (getEndIndex() if the text is empty)
     * and returns the character at that position.
     * @return the last character in the text, or DONE if the text is empty
     * @see #getEndIndex()
     */
    @Override
    public char last(){
        iterator.setToLimit();
        return (char)iterator.previous();
    }

    /**
     * Gets the character at the current position (as returned by getIndex()).
     * @return the character at the current position or DONE if the current
     * position is off the end of the text.
     * @see #getIndex()
     */
    @Override
    public char current(){
        return (char) iterator.current();
    }

    /**
     * Increments the iterator's index by one and returns the character
     * at the new index.  If the resulting index is greater or equal
     * to getEndIndex(), the current index is reset to getEndIndex() and
     * a value of DONE is returned.
     * @return the character at the new position or DONE if the new
     * position is off the end of the text range.
     */
    @Override
    public char next(){
        //pre-increment
        iterator.next();
        return (char) iterator.current();
    }

    /**
     * Decrements the iterator's index by one and returns the character
     * at the new index. If the current index is getBeginIndex(), the index
     * remains at getBeginIndex() and a value of DONE is returned.
     * @return the character at the new position or DONE if the current
     * position is equal to getBeginIndex().
     */
    @Override
    public char previous(){
        //pre-decrement
        return (char) iterator.previous();
    }

    /**
     * Sets the position to the specified position in the text and returns that
     * character.
     * @param position the position within the text.  Valid values range from
     * getBeginIndex() to getEndIndex().  An IllegalArgumentException is thrown
     * if an invalid value is supplied.
     * @return the character at the specified position or DONE if the specified position is equal to getEndIndex()
     */
    @Override
    public char setIndex(int position){
        iterator.setIndex(position);
        return (char) iterator.current();
    }

    /**
     * Returns the start index of the text.
     * @return the index at which the text begins.
     */
    @Override
    public int getBeginIndex(){
        //UCharacterIterator always starts from 0
        return 0;
    }

    /**
     * Returns the end index of the text.  This index is the index of the first
     * character following the end of the text.
     * @return the index after the last character in the text
     */
    @Override
    public int getEndIndex(){
        return iterator.getLength();
    }

    /**
     * Returns the current index.
     * @return the current index.
     */
    @Override
    public int getIndex(){
        return iterator.getIndex();
    }

    /**
     * Create a copy of this iterator
     * @return A copy of this
     */
    @Override
    public Object clone(){
        try {
            UCharacterIteratorWrapper result = (UCharacterIteratorWrapper) super.clone();
            result.iterator = (UCharacterIterator)this.iterator.clone();
            return result;
        } catch (CloneNotSupportedException e) {
            return null; // only invoked if bad underlying character iterator
        }
    }

}

