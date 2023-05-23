// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2012-2015, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package jdk.internal.icu.text;

/**
 * Display context settings.
 * Note, the specific numeric values are internal and may change.
 * @stable ICU 51
 */
public enum DisplayContext {
    /**
     * ================================
     * Settings for DIALECT_HANDLING (use one)
     */
    /**
     * A possible setting for DIALECT_HANDLING:
     * use standard names when generating a locale name,
     * e.g. en_GB displays as 'English (United Kingdom)'.
     * @stable ICU 51
     */
    STANDARD_NAMES(Type.DIALECT_HANDLING, 0),
    /**
     * A possible setting for DIALECT_HANDLING:
     * use dialect names, when generating a locale name,
     * e.g. en_GB displays as 'British English'.
     * @stable ICU 51
     */
    DIALECT_NAMES(Type.DIALECT_HANDLING, 1),
    /**
     * ================================
     * Settings for CAPITALIZATION (use one)
     */
    /**
     * A possible setting for CAPITALIZATION:
     * The capitalization context to be used is unknown (this is the default value).
     * @stable ICU 51
     */
    CAPITALIZATION_NONE(Type.CAPITALIZATION, 0),
    /**
     * A possible setting for CAPITALIZATION:
     * The capitalization context if a date, date symbol or display name is to be
     * formatted with capitalization appropriate for the middle of a sentence.
     * @stable ICU 51
     */
    CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE(Type.CAPITALIZATION, 1),
    /**
     * A possible setting for CAPITALIZATION:
     * The capitalization context if a date, date symbol or display name is to be
     * formatted with capitalization appropriate for the beginning of a sentence.
     * @stable ICU 51
     */
    CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE(Type.CAPITALIZATION, 2),
    /**
     * A possible setting for CAPITALIZATION:
     * The capitalization context if a date, date symbol or display name is to be
     * formatted with capitalization appropriate for a user-interface list or menu item.
     * @stable ICU 51
     */
    CAPITALIZATION_FOR_UI_LIST_OR_MENU(Type.CAPITALIZATION, 3),
    /**
     * A possible setting for CAPITALIZATION:
     * The capitalization context if a date, date symbol or display name is to be
     * formatted with capitalization appropriate for stand-alone usage such as an
     * isolated name on a calendar page.
     * @stable ICU 51
     */
    CAPITALIZATION_FOR_STANDALONE(Type.CAPITALIZATION, 4),
    /**
     * ================================
     * Settings for DISPLAY_LENGTH (use one)
     */
    /**
     * A possible setting for DISPLAY_LENGTH:
     * use full names when generating a locale name,
     * e.g. "United States" for US.
     * @stable ICU 54
     */
    LENGTH_FULL(Type.DISPLAY_LENGTH, 0),
    /**
     * A possible setting for DISPLAY_LENGTH:
     * use short names when generating a locale name,
     * e.g. "U.S." for US.
     * @stable ICU 54
     */
    LENGTH_SHORT(Type.DISPLAY_LENGTH, 1),
    /**
     * ================================
     * Settings for SUBSTITUTE_HANDLING (choose one)
     */
    /**
     * A possible setting for SUBSTITUTE_HANDLING:
     * Returns a fallback value (e.g., the input code) when no data is available.
     * This is the default behavior.
     * @stable ICU 58
     */
    SUBSTITUTE(Type.SUBSTITUTE_HANDLING, 0),
    /**
     * A possible setting for SUBSTITUTE_HANDLING:
     * Returns a null value when no data is available.
     * @stable ICU 58
     */
    NO_SUBSTITUTE(Type.SUBSTITUTE_HANDLING, 1);

    /**
     * Type values for DisplayContext
     * @stable ICU 51
     */
    public enum Type {
        /**
         * DIALECT_HANDLING can be set to STANDARD_NAMES or DIALECT_NAMES.
         * @stable ICU 51
         */
        DIALECT_HANDLING,
        /**
         * CAPITALIZATION can be set to one of CAPITALIZATION_NONE through
         * CAPITALIZATION_FOR_STANDALONE.
         * @stable ICU 51
         */
        CAPITALIZATION,
        /**
         * DISPLAY_LENGTH can be set to LENGTH_FULL or LENGTH_SHORT.
         * @stable ICU 54
         */
        DISPLAY_LENGTH,
        /**
         * SUBSTITUTE_HANDLING can be set to SUBSTITUTE or NO_SUBSTITUTE.
         * @stable ICU 58
         */
        SUBSTITUTE_HANDLING
    }

    private final Type type;
    private final int value;
    private DisplayContext(Type type, int value) {
        this.type = type;
        this.value = value;
    }
    /**
     * Get the Type part of the enum item
     * (e.g. CAPITALIZATION)
     * @stable ICU 51
     */
    public Type type() {
        return type;
    }
    /**
     * Get the value part of the enum item
     * (e.g. CAPITALIZATION_FOR_STANDALONE)
     * @stable ICU 51
     */
    public int value() {
        return value;
    }
}
