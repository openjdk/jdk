/*
 * Copyright 1997 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.security.x509;

import java.io.IOException;
import java.math.BigInteger;

import sun.misc.HexDumpEncoder;
import sun.security.util.*;

/**
 * This class defines the UniqueIdentity class used by certificates.
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 */
public class UniqueIdentity {
    // Private data members
    private BitArray    id;

    /**
     * The default constructor for this class.
     *
     * @param id the byte array containing the unique identifier.
     */
    public UniqueIdentity(BitArray id) {
        this.id = id;
    }

    /**
     * The default constructor for this class.
     *
     * @param id the byte array containing the unique identifier.
     */
    public UniqueIdentity(byte[] id) {
        this.id = new BitArray(id.length*8, id);
    }

    /**
     * Create the object, decoding the values from the passed DER stream.
     *
     * @param in the DerInputStream to read the UniqueIdentity from.
     * @exception IOException on decoding errors.
     */
    public UniqueIdentity(DerInputStream in) throws IOException {
        DerValue derVal = in.getDerValue();
        id = derVal.getUnalignedBitString(true);
    }

    /**
     * Create the object, decoding the values from the passed DER stream.
     *
     * @param derVal the DerValue decoded from the stream.
     * @param tag the tag the value is encoded under.
     * @exception IOException on decoding errors.
     */
    public UniqueIdentity(DerValue derVal) throws IOException {
        id = derVal.getUnalignedBitString(true);
    }

    /**
     * Return the UniqueIdentity as a printable string.
     */
    public String toString() {
        return ("UniqueIdentity:" + id.toString() + "\n");
    }

    /**
     * Encode the UniqueIdentity in DER form to the stream.
     *
     * @param out the DerOutputStream to marshal the contents to.
     * @param tag enocode it under the following tag.
     * @exception IOException on errors.
     */
    public void encode(DerOutputStream out, byte tag) throws IOException {
        byte[] bytes = id.toByteArray();
        int excessBits = bytes.length*8 - id.length();

        out.write(tag);
        out.putLength(bytes.length + 1);

        out.write(excessBits);
        out.write(bytes);
    }

    /**
     * Return the unique id.
     */
    public boolean[] getId() {
        if (id == null) return null;

        return id.toBooleanArray();
    }
}
