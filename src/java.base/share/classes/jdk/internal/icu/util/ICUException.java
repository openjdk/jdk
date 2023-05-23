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
 * Base class for unchecked, ICU-specific exceptions.
 *
 * @stable ICU 53
 */
public class ICUException extends RuntimeException {
    private static final long serialVersionUID = -3067399656455755650L;

    /**
     * Default constructor.
     *
     * @stable ICU 53
     */
    public ICUException() {
    }

    /**
     * Constructor.
     *
     * @param message exception message string
     * @stable ICU 53
     */
    public ICUException(String message) {
        super(message);
    }

    /**
     * Constructor.
     *
     * @param cause original exception
     * @stable ICU 53
     */
    public ICUException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor.
     *
     * @param message exception message string
     * @param cause original exception
     * @stable ICU 53
     */
    public ICUException(String message, Throwable cause) {
        super(message, cause);
    }
}
