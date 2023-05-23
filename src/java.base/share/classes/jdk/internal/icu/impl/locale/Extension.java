// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009-2010, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package jdk.internal.icu.impl.locale;


public class Extension {
    private char _key;
    protected String _value;

    protected Extension(char key) {
        _key = key;
    }

    Extension(char key, String value) {
        _key = key;
        _value = value;
    }

    public char getKey() {
        return _key;
    }

    public String getValue() {
        return _value;
    }

    public String getID() {
        return _key + LanguageTag.SEP + _value;
    }

    @Override
    public String toString() {
        return getID();
    }
}
