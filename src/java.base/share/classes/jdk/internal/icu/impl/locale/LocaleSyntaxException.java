// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009, International Business Machines Corporation and         *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package jdk.internal.icu.impl.locale;

public class LocaleSyntaxException extends Exception {

    private static final long serialVersionUID = 1L;

    private int _index = -1;

    public LocaleSyntaxException(String msg) {
        this(msg, 0);
    }

    public LocaleSyntaxException(String msg, int errorIndex) {
        super(msg);
        _index = errorIndex;
    }

    public int getErrorIndex() {
        return _index;
    }
}
