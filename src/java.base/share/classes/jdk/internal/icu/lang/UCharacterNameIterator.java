// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
******************************************************************************
* Copyright (C) 1996-2011, International Business Machines Corporation and   *
* others. All Rights Reserved.                                               *
******************************************************************************
*/

package jdk.internal.icu.lang;

import jdk.internal.icu.impl.UCharacterName;
import jdk.internal.icu.impl.UCharacterNameChoice;
import jdk.internal.icu.util.ValueIterator;

/**
 * <p>Class enabling iteration of the codepoints and their names.</p>
 * <p>Result of each iteration contains a valid codepoint that has valid
 * name.</p>
 * <p>See UCharacter.getNameIterator() for an example of use.</p>
 * @author synwee
 * @since release 2.1, March 5 2002
 */
class UCharacterNameIterator implements ValueIterator
{
    // public methods ----------------------------------------------------

    /**
    * <p>Gets the next result for this iteration and returns
    * true if we are not at the end of the iteration, false otherwise.</p>
    * <p>If the return boolean is a false, the contents of elements will not
    * be updated.</p>
    * @param element for storing the result codepoint and name
    * @return true if we are not at the end of the iteration, false otherwise.
    * @see jdk.internal.icu.util.ValueIterator.Element
    */
    @Override
    public boolean next(ValueIterator.Element element)
    {
        if (m_current_ >= m_limit_) {
            return false;
        }

        if (m_choice_ == UCharacterNameChoice.UNICODE_CHAR_NAME ||
            m_choice_ == UCharacterNameChoice.EXTENDED_CHAR_NAME
        ) {
            int length = m_name_.getAlgorithmLength();
            if (m_algorithmIndex_ < length) {
                while (m_algorithmIndex_ < length) {
                    // find the algorithm range that could contain m_current_
                    if (m_algorithmIndex_ < 0 ||
                        m_name_.getAlgorithmEnd(m_algorithmIndex_) <
                        m_current_) {
                        m_algorithmIndex_ ++;
                    }
                    else {
                        break;
                    }
                }

                if (m_algorithmIndex_ < length) {
                    // interleave the data-driven ones with the algorithmic ones
                    // iterate over all algorithmic ranges; assume that they are
                    // in ascending order
                    int start = m_name_.getAlgorithmStart(m_algorithmIndex_);
                    if (m_current_ < start) {
                        // this should get rid of those codepoints that are not
                        // in the algorithmic range
                        int end = start;
                        if (m_limit_ <= start) {
                            end = m_limit_;
                        }
                        if (!iterateGroup(element, end)) {
                            m_current_ ++;
                            return true;
                        }
                    }
                    /*
                    // "if (m_current_ >= m_limit_)" would not return true
                    // because it can never be reached due to:
                    // 1) It has already been checked earlier
                    // 2) When m_current_ is updated earlier, it returns true
                    // 3) No updates on m_limit_*/
                    if (m_current_ >= m_limit_) {
                        // after iterateGroup fails, current codepoint may be
                        // greater than limit
                        return false;
                    }

                    element.integer = m_current_;
                    element.value   = m_name_.getAlgorithmName(m_algorithmIndex_,
                                                                   m_current_);
                    // reset the group index if we are in the algorithmic names
                    m_groupIndex_ = -1;
                    m_current_ ++;
                    return true;
                }
            }
        }
        // enumerate the character names after the last algorithmic range
        if (!iterateGroup(element, m_limit_)) {
            m_current_ ++;
            return true;
        }
        else if (m_choice_ == UCharacterNameChoice.EXTENDED_CHAR_NAME) {
            if (!iterateExtended(element, m_limit_)) {
                m_current_ ++;
                return true;
            }
        }

        return false;
    }

    /**
    * <p>Resets the iterator to start iterating from the integer index
    * UCharacter.MIN_VALUE or X if a setRange(X, Y) has been called previously.
    * </p>
    */
    @Override
    public void reset()
    {
        m_current_        = m_start_;
        m_groupIndex_     = -1;
        m_algorithmIndex_ = -1;
    }

    /**
     * <p>Restricts the range of integers to iterate and resets the iteration
     * to begin at the index argument start.</p>
     * <p>If setRange(start, end) is not performed before next(element) is
     * called, the iteration will start from the integer index
     * UCharacter.MIN_VALUE and end at UCharacter.MAX_VALUE.</p>
     * <p>
     * If this range is set outside the range of UCharacter.MIN_VALUE and
     * UCharacter.MAX_VALUE, next(element) will always return false.
     * </p>
     * @param start first integer in range to iterate
     * @param limit 1 integer after the last integer in range
     * @exception IllegalArgumentException thrown when attempting to set an
     *            illegal range. E.g limit <= start
     */
    @Override
    public void setRange(int start, int limit)
    {
        if (start >= limit) {
            throw new IllegalArgumentException(
                "start or limit has to be valid Unicode codepoints and start < limit");
        }
        if (start < UCharacter.MIN_VALUE) {
            m_start_ = UCharacter.MIN_VALUE;
        }
        else {
            m_start_ = start;
        }

        if (limit > UCharacter.MAX_VALUE + 1) {
            m_limit_ = UCharacter.MAX_VALUE + 1;
        }
        else {
            m_limit_ = limit;
        }
        m_current_ = m_start_;
    }

    // protected constructor ---------------------------------------------

    /**
    * Constructor
    * @param name name data
    * @param choice name choice from the class
    *               jdk.internal.icu.lang.UCharacterNameChoice
    */
    protected UCharacterNameIterator(UCharacterName name, int choice)
    {
        if(name==null){
            throw new IllegalArgumentException("UCharacterName name argument cannot be null. Missing unames.icu?");
        }
        m_name_    = name;
        // no explicit choice in UCharacter so no checks on choice
        m_choice_  = choice;
        m_start_   = UCharacter.MIN_VALUE;
        m_limit_   = UCharacter.MAX_VALUE + 1;
        m_current_ = m_start_;
    }

    // private data members ---------------------------------------------

    /**
     * Name data
     */
    private UCharacterName m_name_;
    /**
     * Name choice
     */
    private int m_choice_;
     /**
     * Start iteration range
     */
    private int m_start_;
    /**
     * End + 1 iteration range
     */
    private int m_limit_;
    /**
     * Current codepoint
     */
    private int m_current_;
    /**
     * Group index
     */
    private int m_groupIndex_ = -1;
    /**
     * Algorithm index
     */
    private int m_algorithmIndex_ = -1;
    /**
    * Group use
    */
    private static char GROUP_OFFSETS_[] =
                                new char[UCharacterName.LINES_PER_GROUP_ + 1];
    private static char GROUP_LENGTHS_[] =
                                new char[UCharacterName.LINES_PER_GROUP_ + 1];

    // private methods --------------------------------------------------

    /**
     * Group name iteration, iterate all the names in the current 32-group and
     * returns the first codepoint that has a valid name.
     * @param result stores the result codepoint and name
     * @param limit last codepoint + 1 in range to search
     * @return false if a codepoint with a name is found in group and we can
     *         bail from further iteration, true to continue on with the
     *         iteration
     */
    private boolean iterateSingleGroup(ValueIterator.Element result, int limit)
    {
        synchronized(GROUP_OFFSETS_) {
        synchronized(GROUP_LENGTHS_) {
            int index = m_name_.getGroupLengths(m_groupIndex_, GROUP_OFFSETS_,
                                                GROUP_LENGTHS_);
            while (m_current_ < limit) {
                int    offset = UCharacterName.getGroupOffset(m_current_);
                String name   = m_name_.getGroupName(
                                          index + GROUP_OFFSETS_[offset],
                                          GROUP_LENGTHS_[offset], m_choice_);
                if ((name == null || name.length() == 0) &&
                    m_choice_ == UCharacterNameChoice.EXTENDED_CHAR_NAME) {
                    name = m_name_.getExtendedName(m_current_);
                }
                if (name != null && name.length() > 0) {
                    result.integer = m_current_;
                    result.value   = name;
                    return false;
                }
                ++ m_current_;
            }
        }
        }
        return true;
    }

    /**
     * Group name iteration, iterate all the names in the current 32-group and
     * returns the first codepoint that has a valid name.
     * @param result stores the result codepoint and name
     * @param limit last codepoint + 1 in range to search
     * @return false if a codepoint with a name is found in group and we can
     *         bail from further iteration, true to continue on with the
     *         iteration
     */
    private boolean iterateGroup(ValueIterator.Element result, int limit)
    {
        if (m_groupIndex_ < 0) {
            m_groupIndex_ = m_name_.getGroup(m_current_);
        }

        while (m_groupIndex_ < m_name_.m_groupcount_ &&
               m_current_ < limit) {
            // iterate till the last group or the last codepoint
            int startMSB = UCharacterName.getCodepointMSB(m_current_);
            int gMSB     = m_name_.getGroupMSB(m_groupIndex_); // can be -1
            if (startMSB == gMSB) {
                if (startMSB == UCharacterName.getCodepointMSB(limit - 1)) {
                    // if start and limit - 1 are in the same group, then enumerate
                    // only in that one
                    return iterateSingleGroup(result, limit);
                }
                // enumerate characters in the partial start group
                // if (m_name_.getGroupOffset(m_current_) != 0) {
                if (!iterateSingleGroup(result,
                                        UCharacterName.getGroupLimit(gMSB))) {
                    return false;
                }
                ++ m_groupIndex_; // continue with the next group
            }
            else if (startMSB > gMSB) {
                    // make sure that we start enumerating with the first group
                    // after start
                    m_groupIndex_ ++;
            }
            else {
                int gMIN = UCharacterName.getGroupMin(gMSB);
                if (gMIN > limit) {
                    gMIN = limit;
                }
                if (m_choice_ == UCharacterNameChoice.EXTENDED_CHAR_NAME) {
                    if (!iterateExtended(result, gMIN)) {
                        return false;
                    }
                }
                m_current_ = gMIN;
            }
        }

        return true;
    }

    /**
     * Iterate extended names.
     * @param result stores the result codepoint and name
     * @param limit last codepoint + 1 in range to search
     * @return false if a codepoint with a name is found and we can
     *         bail from further iteration, true to continue on with the
     *         iteration (this will always be false for valid codepoints)
     */
    private boolean iterateExtended(ValueIterator.Element result,
                                    int limit)
    {
        while (m_current_ < limit) {
            String name = m_name_.getExtendedOr10Name(m_current_);
            if (name != null && name.length() > 0) {
                result.integer = m_current_;
                result.value   = name;
                return false;
            }
            ++ m_current_;
        }
        return true;
    }
}
