// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009, Google, International Business Machines Corporation and         *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package jdk.internal.icu.impl;

/**
 * @author markdavis
 *
 */
public class IllegalIcuArgumentException extends IllegalArgumentException {
    private static final long serialVersionUID = 3789261542830211225L;

    public IllegalIcuArgumentException(String errorMessage) {
        super(errorMessage);
    }

    public IllegalIcuArgumentException(Throwable cause) {
        super(cause);
    }

    public IllegalIcuArgumentException(String errorMessage, Throwable cause) {
        super(errorMessage, cause);
    }

    @Override
    public synchronized IllegalIcuArgumentException initCause(Throwable cause) {
        return (IllegalIcuArgumentException) super.initCause(cause);
    }

}
