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
 */

package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import java.io.IOException;

import com.sun.xml.internal.bind.v2.runtime.output.Pcdata;
import com.sun.xml.internal.bind.v2.runtime.output.UTF8XmlOutput;

/**
 * {@link Pcdata} that represents a single integer.
 *
 * @author Kohsuke Kawaguchi
 */
public class IntData extends Pcdata {
    /**
     * The int value that this {@link Pcdata} represents.
     *
     * Modifiable.
     */
    private int data;

    /**
     * Length of the {@link #data} in ASCII string.
     * For example if data=-10, then length=3
     */
    private int length;

    public void reset(int i) {
        this.data = i;
        if(i==Integer.MIN_VALUE)
            length = 11;
        else
            length = (i < 0) ? stringSizeOfInt(-i) + 1 : stringSizeOfInt(i);
    }

    private final static int [] sizeTable = { 9, 99, 999, 9999, 99999, 999999, 9999999,
                                     99999999, 999999999, Integer.MAX_VALUE };

    // Requires positive x
    private static int stringSizeOfInt(int x) {
        for (int i=0; ; i++)
            if (x <= sizeTable[i])
                return i+1;
    }

    public String toString() {
        return String.valueOf(data);
    }


    public int length() {
        return length;
    }

    public char charAt(int index) {
        return toString().charAt(index);
    }

    public CharSequence subSequence(int start, int end) {
        return toString().substring(start,end);
    }

    public void writeTo(UTF8XmlOutput output) throws IOException {
        output.text(data);
    }
}
