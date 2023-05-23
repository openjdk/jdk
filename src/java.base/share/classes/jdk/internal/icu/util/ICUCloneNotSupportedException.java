// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2014-2015, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */
package jdk.internal.icu.util;

/**
 * Unchecked version of {@link CloneNotSupportedException}.
 * Some ICU APIs do not throw the standard exception but instead wrap it
 * into this unchecked version.
 *
 * @stable ICU 53
 */
public class ICUCloneNotSupportedException extends ICUException {
    private static final long serialVersionUID = -4824446458488194964L;

    /**
     * Default constructor.
     *
     * @stable ICU 53
     */
    public ICUCloneNotSupportedException() {
    }

    /**
     * Constructor.
     *
     * @param message exception message string
     * @stable ICU 53
     */
    public ICUCloneNotSupportedException(String message) {
        super(message);
    }

    /**
     * Constructor.
     *
     * @param cause original exception (normally a {@link CloneNotSupportedException})
     * @stable ICU 53
     */
    public ICUCloneNotSupportedException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor.
     *
     * @param message exception message string
     * @param cause original exception (normally a {@link CloneNotSupportedException})
     * @stable ICU 53
     */
    public ICUCloneNotSupportedException(String message, Throwable cause) {
        super(message, cause);
    }
}
