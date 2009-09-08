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

package com.sun.xml.internal.bind.v2.runtime.reflect.opt;

import com.sun.xml.internal.bind.v2.runtime.reflect.TransducedAccessor;

/**
 * Used by {@link TransducedAccessor} templates.
 *
 * <p>
 * Fields needs to have a distinctive name.
 *
 * @author Kohsuke Kawaguchi
 */
final class Bean {
    public boolean f_boolean;
    public char f_char;
    public byte f_byte;
    public short f_short;
    int f_int;
    public long f_long;
    public float f_float;
    public double f_double;
    /**
     * Field of a reference type.
     * We need a distinctive type so that it can be easily replaced.
     */
    public Ref f_ref;

    public boolean get_boolean() { throw new UnsupportedOperationException(); }
    public void set_boolean(boolean b) { throw new UnsupportedOperationException(); }

    public char get_char() { throw new UnsupportedOperationException(); }
    public void set_char(char b) { throw new UnsupportedOperationException(); }

    public byte get_byte() { throw new UnsupportedOperationException(); }
    public void set_byte(byte b) { throw new UnsupportedOperationException(); }

    public short get_short() { throw new UnsupportedOperationException(); }
    public void set_short(short b) { throw new UnsupportedOperationException(); }

    public int get_int() { throw new UnsupportedOperationException(); }
    public void set_int(int b) { throw new UnsupportedOperationException(); }

    public long get_long() { throw new UnsupportedOperationException(); }
    public void set_long(long b) { throw new UnsupportedOperationException(); }

    public float get_float() { throw new UnsupportedOperationException(); }
    public void set_float(float b) { throw new UnsupportedOperationException(); }

    public double get_double() { throw new UnsupportedOperationException(); }
    public void set_double(double b) { throw new UnsupportedOperationException(); }

    public Ref get_ref() { throw new UnsupportedOperationException(); }
    public void set_ref(Ref r) { throw new UnsupportedOperationException(); }
}
