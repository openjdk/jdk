/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 *******************************************************************************
 * Copyright (C) 1996-2004, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package jdk.internal.icu.lang;

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

@SuppressWarnings("deprecation")
public final class UCharacterCategory implements UCharacterEnums.ECharacterCategory
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
    /**
     * Private constructor to prevent initialization
     */
    private UCharacterCategory()
    {
    }
}
