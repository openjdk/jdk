/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.encoding;

import org.omg.CORBA.TypeCode ;
import org.omg.CORBA.StructMember ;
import org.omg.CORBA.UnionMember ;
import org.omg.CORBA.ValueMember ;
import org.omg.CORBA.TCKind ;
import org.omg.CORBA.Any ;
import org.omg.CORBA.Principal ;
import org.omg.CORBA.CompletionStatus ;

import org.omg.CORBA.TypeCodePackage.BadKind ;

import org.omg.CORBA_2_3.portable.InputStream;
import org.omg.CORBA_2_3.portable.OutputStream;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.impl.encoding.OSFCodeSetRegistry;
import com.sun.corba.se.impl.encoding.MarshalInputStream;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.impl.encoding.CodeSetConversion;

import com.sun.corba.se.impl.encoding.CDRInputStream;
import com.sun.corba.se.impl.encoding.CDROutputStream;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.io.IOException;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;

public final class TypeCodeOutputStream extends EncapsOutputStream
{
    private OutputStream enclosure = null;
    private Map typeMap = null;
    private boolean isEncapsulation = false;

    public TypeCodeOutputStream(ORB orb) {
        super(orb, false);
    }

    public TypeCodeOutputStream(ORB orb, boolean littleEndian) {
        super(orb, littleEndian);
    }

    public org.omg.CORBA.portable.InputStream create_input_stream()
    {
        //return new TypeCodeInputStream((ORB)orb(), getByteBuffer(), getIndex(), isLittleEndian());
        TypeCodeInputStream tcis
            = new TypeCodeInputStream((ORB)orb(), getByteBuffer(), getIndex(), isLittleEndian(), getGIOPVersion());
        //if (TypeCodeImpl.debug) {
            //System.out.println("Created TypeCodeInputStream " + tcis + " with no parent");
            //tcis.printBuffer();
        //}
        return tcis;
    }

    public void setEnclosingOutputStream(OutputStream enclosure) {
        this.enclosure = enclosure;
    }

    /*
      public boolean isEncapsulatedIn(TypeCodeOutputStream outerEnclosure) {
      if (outerEnclosure == this)
      return true;
      if (enclosure == null)
      return false;
      if (enclosure instanceof TypeCodeOutputStream)
      return ((TypeCodeOutputStream)enclosure).isEncapsulatedIn(outerEnclosure);
      // Last chance! Recursion ends with first non TypeCodeOutputStream.
      return (enclosure == outerEnclosure);
      }
    */

    public TypeCodeOutputStream getTopLevelStream() {
        if (enclosure == null)
            return this;
        if (enclosure instanceof TypeCodeOutputStream)
            return ((TypeCodeOutputStream)enclosure).getTopLevelStream();
        return this;
    }

    public int getTopLevelPosition() {
        if (enclosure != null && enclosure instanceof TypeCodeOutputStream) {
            int pos = ((TypeCodeOutputStream)enclosure).getTopLevelPosition() + getPosition();
            // Add four bytes for the encaps length, not another 4 for the byte order
            // which is included in getPosition().
            if (isEncapsulation) pos += 4;
            //if (TypeCodeImpl.debug) {
                //System.out.println("TypeCodeOutputStream.getTopLevelPosition using getTopLevelPosition " +
                    //((TypeCodeOutputStream)enclosure).getTopLevelPosition() +
                    //" + getPosition() " + getPosition() +
                    //(isEncapsulation ? " + encaps length 4" : "") +
                    //" = " + pos);
            //}
            return pos;
        }
        //if (TypeCodeImpl.debug) {
            //System.out.println("TypeCodeOutputStream.getTopLevelPosition returning getPosition() = " +
                               //getPosition() + ", enclosure is " + enclosure);
        //}
        return getPosition();
    }

    public void addIDAtPosition(String id, int position) {
        if (typeMap == null)
            typeMap = new HashMap(16);
        //if (TypeCodeImpl.debug) System.out.println(this + " adding id " + id + " at position " + position);
        typeMap.put(id, new Integer(position));
    }

    public int getPositionForID(String id) {
        if (typeMap == null)
            throw wrapper.refTypeIndirType( CompletionStatus.COMPLETED_NO ) ;
        //if (TypeCodeImpl.debug) System.out.println("Getting position " + ((Integer)typeMap.get(id)).intValue() +
            //" for id " + id);
        return ((Integer)typeMap.get(id)).intValue();
    }

    public void writeRawBuffer(org.omg.CORBA.portable.OutputStream s, int firstLong) {
        // Writes this streams buffer to the given OutputStream
        // without byte order flag and length as is the case for encapsulations.

        // Make sure to align s to 4 byte boundaries.
        // Unfortunately we can't do just this:
        // s.alignAndReserve(4, 4);
        // So we have to take the first four bytes given in firstLong and write them
        // with a call to write_long which will trigger the alignment.
        // Then write the rest of the byte array.

        //if (TypeCodeImpl.debug) {
            //System.out.println(this + ".writeRawBuffer(" + s + ", " + firstLong + ")");
            //if (s instanceof CDROutputStream) {
                //System.out.println("Parent position before writing kind = " + ((CDROutputStream)s).getIndex());
            //}
        //}
        s.write_long(firstLong);
        //if (TypeCodeImpl.debug) {
            //if (s instanceof CDROutputStream) {
                //System.out.println("Parent position after writing kind = " + ((CDROutputStream)s).getIndex());
            //}
        //}
        ByteBuffer byteBuffer = getByteBuffer();
        if (byteBuffer.hasArray())
        {
             s.write_octet_array(byteBuffer.array(), 4, getIndex() - 4);
        }
        else
        {
             // get bytes from DirectByteBuffer
             // NOTE: Microbenchmarks are showing it is faster to do
             //       a loop of ByteBuffer.get(int) than it is to do
             //       a bulk ByteBuffer.get(byte[], offset, length)
             byte[] buf = new byte[byteBuffer.limit()];
             for (int i = 0; i < buf.length; i++)
                  buf[i] = byteBuffer.get(i);
             s.write_octet_array(buf, 4, getIndex() - 4);
        }
        //if (TypeCodeImpl.debug) {
            //if (s instanceof CDROutputStream) {
                //System.out.println("Parent position after writing all " + getIndex() + " bytes = " + ((CDROutputStream)s).getIndex());
            //}
        //}
    }

    public TypeCodeOutputStream createEncapsulation(org.omg.CORBA.ORB _orb) {
        TypeCodeOutputStream encap = new TypeCodeOutputStream((ORB)_orb, isLittleEndian());
        encap.setEnclosingOutputStream(this);
        encap.makeEncapsulation();
        //if (TypeCodeImpl.debug) System.out.println("Created TypeCodeOutputStream " + encap + " with parent " + this);
        return encap;
    }

    protected void makeEncapsulation() {
        // first entry in an encapsulation is the endianess
        putEndian();
        isEncapsulation = true;
    }

    public static TypeCodeOutputStream wrapOutputStream(OutputStream os) {
        boolean littleEndian = ((os instanceof CDROutputStream) ? ((CDROutputStream)os).isLittleEndian() : false);
        TypeCodeOutputStream tos = new TypeCodeOutputStream((ORB)os.orb(), littleEndian);
        tos.setEnclosingOutputStream(os);
        //if (TypeCodeImpl.debug) System.out.println("Created TypeCodeOutputStream " + tos + " with parent " + os);
        return tos;
    }

    public int getPosition() {
        return getIndex();
    }

    public int getRealIndex(int index) {
        int topPos = getTopLevelPosition();
        //if (TypeCodeImpl.debug) System.out.println("TypeCodeOutputStream.getRealIndex using getTopLevelPosition " +
            //topPos + " instead of getPosition " + getPosition());
        return topPos;
    }
/*
    protected void printBuffer() {
        super.printBuffer();
    }
*/
    public byte[] getTypeCodeBuffer() {
        // Returns the buffer trimmed of the trailing zeros and without the
        // known _kind value at the beginning.
        ByteBuffer theBuffer = getByteBuffer();
        //System.out.println("outBuffer length = " + (getIndex() - 4));
        byte[] tcBuffer = new byte[getIndex() - 4];
        // Micro-benchmarks show that DirectByteBuffer.get(int) is faster
        // than DirectByteBuffer.get(byte[], offset, length).
        // REVISIT - May want to check if buffer is direct or non-direct
        //           and use array copy if ByteBuffer is non-direct.
        for (int i = 0; i < tcBuffer.length; i++)
            tcBuffer[i] = theBuffer.get(i+4);
        return tcBuffer;
    }

    public void printTypeMap() {
        System.out.println("typeMap = {");
        Iterator i = typeMap.keySet().iterator();
        while (i.hasNext()) {
            String id = (String)i.next();
            Integer pos = (Integer)typeMap.get(id);
            System.out.println("  key = " + id + ", value = " + pos);
        }
        System.out.println("}");
    }
}
