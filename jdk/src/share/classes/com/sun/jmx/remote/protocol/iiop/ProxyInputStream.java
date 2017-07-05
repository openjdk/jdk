/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.remote.protocol.iiop;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;

import org.omg.CORBA.Any;
import org.omg.CORBA.Context;
import org.omg.CORBA.NO_IMPLEMENT;
import org.omg.CORBA.ORB;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.portable.BoxedValueHelper;

@SuppressWarnings({"deprecation", "rawtypes"})
public class ProxyInputStream extends org.omg.CORBA_2_3.portable.InputStream {
    public ProxyInputStream(org.omg.CORBA.portable.InputStream in) {
        this.in = in;
    }

    public boolean read_boolean() {
        return in.read_boolean();
    }

    public char read_char() {
        return in.read_char();
    }

    public char read_wchar() {
        return in.read_wchar();
    }

    public byte read_octet() {
        return in.read_octet();
    }

    public short read_short() {
        return in.read_short();
    }

    public short read_ushort() {
        return in.read_ushort();
    }

    public int read_long() {
        return in.read_long();
    }

    public int read_ulong() {
        return in.read_ulong();
    }

    public long read_longlong() {
        return in.read_longlong();
    }

    public long read_ulonglong() {
        return in.read_ulonglong();
    }

    public float read_float() {
        return in.read_float();
    }

    public double read_double() {
        return in.read_double();
    }

    public String read_string() {
        return in.read_string();
    }

    public String read_wstring() {
        return in.read_wstring();
    }

    public void read_boolean_array(boolean[] value, int offset, int length) {
        in.read_boolean_array(value, offset, length);
    }

    public void read_char_array(char[] value, int offset, int length) {
        in.read_char_array(value, offset, length);
    }

    public void read_wchar_array(char[] value, int offset, int length) {
        in.read_wchar_array(value, offset, length);
    }

    public void read_octet_array(byte[] value, int offset, int length) {
        in.read_octet_array(value, offset, length);
    }

    public void read_short_array(short[] value, int offset, int length) {
        in.read_short_array(value, offset, length);
    }

    public void read_ushort_array(short[] value, int offset, int length) {
        in.read_ushort_array(value, offset, length);
    }

    public void read_long_array(int[] value, int offset, int length) {
        in.read_long_array(value, offset, length);
    }

    public void read_ulong_array(int[] value, int offset, int length) {
        in.read_ulong_array(value, offset, length);
    }

    public void read_longlong_array(long[] value, int offset, int length) {
        in.read_longlong_array(value, offset, length);
    }

    public void read_ulonglong_array(long[] value, int offset, int length) {
        in.read_ulonglong_array(value, offset, length);
    }

    public void read_float_array(float[] value, int offset, int length) {
        in.read_float_array(value, offset, length);
    }

    public void read_double_array(double[] value, int offset, int length) {
        in.read_double_array(value, offset, length);
    }

    public org.omg.CORBA.Object read_Object() {
        return in.read_Object();
    }

    public TypeCode read_TypeCode() {
        return in.read_TypeCode();
    }

    public Any read_any() {
        return in.read_any();
    }

    /**
     * @deprecated
     */
    @Override
    @Deprecated
    public org.omg.CORBA.Principal read_Principal() {
        return in.read_Principal();
    }

    @Override
    public int read() throws IOException {
        return in.read();
    }

    @Override
    public BigDecimal read_fixed() {
        return in.read_fixed();
    }

    @Override
    public Context read_Context() {
        return in.read_Context();
    }

    @Override
    public org.omg.CORBA.Object read_Object(java.lang.Class clz) {
        return in.read_Object(clz);
    }

    @Override
    public ORB orb() {
        return in.orb();
    }

    @Override
    public Serializable read_value() {
        return narrow().read_value();
    }

    @Override
    public Serializable read_value(Class clz) {
        return narrow().read_value(clz);
    }

    @Override
    public Serializable read_value(BoxedValueHelper factory) {
        return narrow().read_value(factory);
    }

    @Override
    public Serializable read_value(String rep_id) {
        return narrow().read_value(rep_id);
    }

    @Override
    public Serializable read_value(Serializable value) {
        return narrow().read_value(value);
    }

    @Override
    public Object read_abstract_interface() {
        return narrow().read_abstract_interface();
    }

    @Override
    public Object read_abstract_interface(Class clz) {
        return narrow().read_abstract_interface(clz);
    }

    protected org.omg.CORBA_2_3.portable.InputStream narrow() {
        if (in instanceof org.omg.CORBA_2_3.portable.InputStream)
            return (org.omg.CORBA_2_3.portable.InputStream) in;
        throw new NO_IMPLEMENT();
    }

    public org.omg.CORBA.portable.InputStream getProxiedInputStream() {
        return in;
    }

    protected final org.omg.CORBA.portable.InputStream in;
}
