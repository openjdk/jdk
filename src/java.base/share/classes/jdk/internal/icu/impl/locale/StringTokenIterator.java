// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009, International Business Machines Corporation and         *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package jdk.internal.icu.impl.locale;

public class StringTokenIterator {
    private String _text;
    private String _dlms;

    private String _token;
    private int _start;
    private int _end;
    private boolean _done;

    public StringTokenIterator(String text, String dlms) {
        _text = text;
        _dlms = dlms;
        setStart(0);
    }

    public String first() {
        setStart(0);
        return _token;
    }

    public String current() {
        return _token;
    }

    public int currentStart() {
        return _start;
    }

    public int currentEnd() {
        return _end;
    }

    public boolean isDone() {
        return _done;
    }

    public String next() {
        if (hasNext()) {
            _start = _end + 1;
            _end = nextDelimiter(_start);
            _token = _text.substring(_start, _end);
        } else {
            _start = _end;
            _token = null;
            _done = true;
        }
        return _token;
    }

    public boolean hasNext() {
        return (_end < _text.length());
    }

    public StringTokenIterator setStart(int offset) {
        if (offset > _text.length()) {
            throw new IndexOutOfBoundsException();
        }
        _start = offset;
        _end = nextDelimiter(_start);
        _token = _text.substring(_start, _end);
        _done = false;
        return this;
    }

    public StringTokenIterator setText(String text) {
        _text = text;
        setStart(0);
        return this;
    }

    private int nextDelimiter(int start) {
        int idx = start;
        outer: while (idx < _text.length()) {
            char c = _text.charAt(idx);
            for (int i = 0; i < _dlms.length(); i++) {
                if (c == _dlms.charAt(i)) {
                    break outer;
                }
            }
            idx++;
        }
        return idx;
    }
}

