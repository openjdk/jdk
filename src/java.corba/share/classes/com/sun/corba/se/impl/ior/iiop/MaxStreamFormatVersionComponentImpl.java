/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
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

/**
 */
package com.sun.corba.se.impl.ior.iiop;

import org.omg.IOP.TAG_RMI_CUSTOM_MAX_STREAM_FORMAT;

import org.omg.CORBA_2_3.portable.OutputStream;

import javax.rmi.CORBA.Util;
import javax.rmi.CORBA.ValueHandler;
import javax.rmi.CORBA.ValueHandlerMultiFormat;

import com.sun.corba.se.impl.orbutil.ORBUtility;

import com.sun.corba.se.spi.ior.TaggedComponentBase;

import com.sun.corba.se.spi.ior.iiop.MaxStreamFormatVersionComponent;

// Java to IDL ptc 02-01-12 1.4.11
// TAG_RMI_CUSTOM_MAX_STREAM_FORMAT
public class MaxStreamFormatVersionComponentImpl extends TaggedComponentBase
    implements MaxStreamFormatVersionComponent
{
    private byte version;

    public static final MaxStreamFormatVersionComponentImpl singleton
        = new MaxStreamFormatVersionComponentImpl();

    public boolean equals(Object obj)
    {
        if (!(obj instanceof MaxStreamFormatVersionComponentImpl))
            return false ;

        MaxStreamFormatVersionComponentImpl other =
            (MaxStreamFormatVersionComponentImpl)obj ;

        return version == other.version ;
    }

    public int hashCode()
    {
        return version ;
    }

    public String toString()
    {
        return "MaxStreamFormatVersionComponentImpl[version=" + version + "]" ;
    }

    public MaxStreamFormatVersionComponentImpl()
    {
        version = ORBUtility.getMaxStreamFormatVersion();
    }

    public MaxStreamFormatVersionComponentImpl(byte streamFormatVersion) {
        version = streamFormatVersion;
    }

    public byte getMaxStreamFormatVersion()
    {
        return version;
    }

    public void writeContents(OutputStream os)
    {
        os.write_octet(version);
    }

    public int getId()
    {
        return TAG_RMI_CUSTOM_MAX_STREAM_FORMAT.value;
    }
}
