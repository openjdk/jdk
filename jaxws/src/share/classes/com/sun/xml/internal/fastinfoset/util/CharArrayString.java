/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */



package com.sun.xml.internal.fastinfoset.util;

public class CharArrayString extends CharArray {
    protected String _s;

    public CharArrayString(String s) {
        this(s, true);
    }

    public CharArrayString(String s, boolean createArray) {
        _s = s;
        if (createArray) {
            ch = _s.toCharArray();
            start = 0;
            length = ch.length;
        }
    }

    public String toString() {
        return _s;
    }

    public int hashCode() {
        return _s.hashCode();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof CharArrayString) {
            CharArrayString chas = (CharArrayString)obj;
            return _s.equals(chas._s);
        } else if (obj instanceof CharArray) {
            CharArray cha = (CharArray)obj;
            if (length == cha.length) {
                int n = length;
                int i = start;
                int j = cha.start;
                while (n-- != 0) {
                    if (ch[i++] != cha.ch[j++])
                        return false;
                }
                return true;
            }
        }
        return false;
    }

}
