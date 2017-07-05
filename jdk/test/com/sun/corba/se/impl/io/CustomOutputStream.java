/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.Serializable;

import org.omg.CORBA.Any;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.Principal;
import org.omg.CORBA_2_3.portable.OutputStream;
import org.omg.CORBA_2_3.portable.InputStream;

public class CustomOutputStream extends OutputStream {

    @Override
    public void write_value(Serializable value, Class clz) {
    }

    @Override
    public InputStream create_input_stream() {
        return null;
    }

    @Override
    public void write_boolean(boolean value) {
    }

    @Override
    public void write_char(char value) {
    }

    @Override
    public void write_wchar(char value) {
    }

    @Override
    public void write_octet(byte value) {
    }

    @Override
    public void write_short(short value) {
    }

    @Override
    public void write_ushort(short value) {
    }

    @Override
    public void write_long(int value) {
    }

    @Override
    public void write_ulong(int value) {
    }

    @Override
    public void write_longlong(long value) {
    }

    @Override
    public void write_ulonglong(long value) {
    }

    @Override
    public void write_float(float value) {
    }

    @Override
    public void write_double(double value) {
    }

    @Override
    public void write_string(String value) {
    }

    @Override
    public void write_wstring(String value) {
    }

    @Override
    public void write_boolean_array(boolean[] value, int offset,
            int length) {
    }

    @Override
    public void write_char_array(char[] value, int offset,
            int length) {
    }

    @Override
    public void write_wchar_array(char[] value, int offset,
            int length) {
    }

    @Override
    public void write_octet_array(byte[] value, int offset,
            int length) {
    }

    @Override
    public void write_short_array(short[] value, int offset,
            int length) {
    }

    @Override
    public void write_ushort_array(short[] value, int offset,
            int length) {
    }

    @Override
    public void write_long_array(int[] value, int offset,
            int length) {
    }

    @Override
    public void write_ulong_array(int[] value, int offset,
            int length) {
    }

    @Override
    public void write_longlong_array(long[] value, int offset,
            int length) {
    }

    @Override
    public void write_ulonglong_array(long[] value, int offset,
            int length) {
    }

    @Override
    public void write_float_array(float[] value, int offset,
            int length) {
    }

    @Override
    public void write_double_array(double[] value, int offset,
            int length) {
    }

    @Override
    public void write_Object(org.omg.CORBA.Object value) {
    }

    @Override
    public void write_TypeCode(TypeCode value) {
    }

    @Override
    public void write_any(Any value) {
    }

    @Override
    public void write_Principal(Principal value) {
    }

    @Override
    public void write(int b) throws java.io.IOException {
    }

    @Override
    public void write_fixed(java.math.BigDecimal value) {
    }

    @Override
    public void write_Context(org.omg.CORBA.Context ctx,
            org.omg.CORBA.ContextList contexts) {
    }

    @Override
    public org.omg.CORBA.ORB orb() {
        return null;
    }
}
