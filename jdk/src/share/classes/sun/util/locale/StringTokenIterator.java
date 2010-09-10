/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * Copyright (C) 2009, International Business Machines Corporation and         *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package sun.util.locale;

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

