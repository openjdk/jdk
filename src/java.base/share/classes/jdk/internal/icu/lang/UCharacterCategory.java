// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/**
 *******************************************************************************
 * Copyright (C) 1996-2004, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package jdk.internal.icu.lang;

import jdk.internal.icu.lang.UCharacterEnums.ECharacterCategory;

/**
 * Enumerated Unicode category types from the UnicodeData.txt file.
 * Used as return results from <a href=UCharacter.html>UCharacter</a>
 * Equivalent to icu's UCharCategory.
 * Refer to <a href="http://www.unicode.org/Public/UNIDATA/UCD.html">
 * Unicode Consortium</a> for more information about UnicodeData.txt.
 * <p>
 * <em>NOTE:</em> the UCharacterCategory values are <em>not</em> compatible with
 * those returned by java.lang.Character.getType.  UCharacterCategory values
 * match the ones used in ICU4C, while java.lang.Character type
 * values, though similar, skip the value 17.</p>
 * <p>
 * This class is not subclassable
 * </p>
 * @author Syn Wee Quek
 * @stable ICU 2.1
 */

public final class UCharacterCategory implements ECharacterCategory
{
    /**
     * Gets the name of the argument category
     * @param category to retrieve name
     * @return category name
     * @stable ICU 2.1
     */
    public static String toString(int category)
    {
        switch (category) {
        case UPPERCASE_LETTER :
            return "Letter, Uppercase";
        case LOWERCASE_LETTER :
            return "Letter, Lowercase";
        case TITLECASE_LETTER :
            return "Letter, Titlecase";
        case MODIFIER_LETTER :
            return "Letter, Modifier";
        case OTHER_LETTER :
            return "Letter, Other";
        case NON_SPACING_MARK :
            return "Mark, Non-Spacing";
        case ENCLOSING_MARK : 
            return "Mark, Enclosing";
        case COMBINING_SPACING_MARK :
            return "Mark, Spacing Combining";
        case DECIMAL_DIGIT_NUMBER :
            return "Number, Decimal Digit";
        case LETTER_NUMBER :
            return "Number, Letter";
        case OTHER_NUMBER :
            return "Number, Other";
        case SPACE_SEPARATOR :
            return "Separator, Space";
        case LINE_SEPARATOR :
            return "Separator, Line";
        case PARAGRAPH_SEPARATOR :
            return "Separator, Paragraph";
        case CONTROL :
            return "Other, Control";
        case FORMAT :
            return "Other, Format";
        case PRIVATE_USE :
            return "Other, Private Use";
        case SURROGATE :
            return "Other, Surrogate";
        case DASH_PUNCTUATION :
            return "Punctuation, Dash";
        case START_PUNCTUATION :
            return "Punctuation, Open";
        case END_PUNCTUATION :
            return "Punctuation, Close";
        case CONNECTOR_PUNCTUATION :
            return "Punctuation, Connector";
        case OTHER_PUNCTUATION :
            return "Punctuation, Other";
        case MATH_SYMBOL :
            return "Symbol, Math";
        case CURRENCY_SYMBOL :
            return "Symbol, Currency";
        case MODIFIER_SYMBOL :
            return "Symbol, Modifier";
        case OTHER_SYMBOL :
            return "Symbol, Other";
        case INITIAL_PUNCTUATION :
            return "Punctuation, Initial quote";
        case FINAL_PUNCTUATION :
            return "Punctuation, Final quote";
        }
        return "Unassigned";
    }
        
    // private constructor -----------------------------------------------
    ///CLOVER:OFF 
    /**
     * Private constructor to prevent initialization
     */
    private UCharacterCategory()
    {
    }
    ///CLOVER:ON
}
