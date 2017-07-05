/*
 * Copyright 1996-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.util;

import java.io.*;


/**
 * Represent an ISO Object Identifier.
 *
 * <P>Object Identifiers are arbitrary length hierarchical identifiers.
 * The individual components are numbers, and they define paths from the
 * root of an ISO-managed identifier space.  You will sometimes see a
 * string name used instead of (or in addition to) the numerical id.
 * These are synonyms for the numerical IDs, but are not widely used
 * since most sites do not know all the requisite strings, while all
 * sites can parse the numeric forms.
 *
 * <P>So for example, JavaSoft has the sole authority to assign the
 * meaning to identifiers below the 1.3.6.1.4.1.42.2.17 node in the
 * hierarchy, and other organizations can easily acquire the ability
 * to assign such unique identifiers.
 *
 *
 * @author David Brownell
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 */
final public
class ObjectIdentifier implements Serializable
{
    /** use serialVersionUID from JDK 1.1. for interoperability */
    private static final long serialVersionUID = 8697030238860181294L;
    private static final int maxFirstComponent = 2;
    private static final int maxSecondComponent = 39;

    /**
     * Constructs an object identifier from a string.  This string
     * should be of the form 1.23.34.45.56 etc.
     */
    public ObjectIdentifier (String oid) throws IOException
    {
        int ch = '.';
        int     start = 0;
        int end = 0;

        // Calculate length of oid
        componentLen = 0;
        while ((end = oid.indexOf(ch,start)) != -1) {
            start = end + 1;
            componentLen += 1;
        }
        componentLen += 1;
        components = new int[componentLen];

        start = 0;
        int i = 0;
        String comp = null;
        try {
            while ((end = oid.indexOf(ch,start)) != -1) {
                comp = oid.substring(start,end);
                components[i++] = Integer.valueOf(comp).intValue();
                start = end + 1;
            }
            comp = oid.substring(start);
            components[i] = Integer.valueOf(comp).intValue();
        } catch (Exception e) {
            throw new IOException("ObjectIdentifier() -- Invalid format: "
                    + e.toString(), e);
        }
        checkValidOid(components, componentLen);
        this.stringForm = oid;
    }

    /**
     * Check if the values make a legal OID. There must be at least 2
     * components and they must be all non-negative. The first component
     * should be 0,1 or 2. When the first component is 0 or 1, the
     * second component should be less than or equal to 39
     *
     * @param values the components that will make the OID
     * @param len the number of components to check. Note that the allocation
     *        size of <code>values</code> may be longer than <code>len</code>.
     *        In this case, only the first <code>len</code> items are checked.
     * @exception IOException if this is not a legal OID
     */
    private void checkValidOid(int[] values, int len) throws IOException {
        if (values == null || len < 2) {
            throw new IOException("ObjectIdentifier() -- " +
                    "Must be at least two oid components ");
        }

        for (int i=0; i<len; i++) {
            if (values[i] < 0) {
                throw new IOException("ObjectIdentifier() -- " +
                        "oid component #" + (i+1) + " must be non-negative ");
            }
        }

        if (values[0] > maxFirstComponent) {
            throw new IOException("ObjectIdentifier() -- " +
                    "First oid component is invalid ");
        }

        if (values[0] < 2 && values[1] > maxSecondComponent) {
            throw new IOException("ObjectIdentifier() -- " +
                    "Second oid component is invalid ");
        }
    }
    /**
     * Constructs an object ID from an array of integers.  This
     * is used to construct constant object IDs.
     */
    public ObjectIdentifier (int values []) throws IOException
    {
        checkValidOid(values, values.length);
        components = values.clone();
        componentLen = values.length;
    }

    /**
     * Constructs an object ID from an ASN.1 encoded input stream.
     * The encoding of the ID in the stream uses "DER", a BER/1 subset.
     * In this case, that means a triple { typeId, length, data }.
     *
     * <P><STRONG>NOTE:</STRONG>  When an exception is thrown, the
     * input stream has not been returned to its "initial" state.
     *
     * @param in DER-encoded data holding an object ID
     * @exception IOException indicates a decoding error
     */
    public ObjectIdentifier (DerInputStream in)
        throws IOException
    {
        byte    type_id;
        int     bufferEnd;

        /*
         * Object IDs are a "universal" type, and their tag needs only
         * one byte of encoding.  Verify that the tag of this datum
         * is that of an object ID.
         *
         * Then get and check the length of the ID's encoding.  We set
         * up so that we can use in.available() to check for the end of
         * this value in the data stream.
         */
        type_id = (byte) in.getByte ();
        if (type_id != DerValue.tag_ObjectId)
            throw new IOException (
                "ObjectIdentifier() -- data isn't an object ID"
                + " (tag = " +  type_id + ")"
                );

        bufferEnd = in.available () - in.getLength () - 1;
        if (bufferEnd < 0)
            throw new IOException (
                "ObjectIdentifier() -- not enough data");

        initFromEncoding (in, bufferEnd);
    }

    /*
     * Build the OID from the rest of a DER input buffer; the tag
     * and length have been removed/verified
     */
    ObjectIdentifier (DerInputBuffer buf) throws IOException
    {
        initFromEncoding (new DerInputStream (buf), 0);
    }

    /**
     * Private constructor for use by newInternal(). Dummy argument
     * to avoid clash with the public constructor.
     */
    private ObjectIdentifier(int[] components, boolean dummy) {
        this.components = components;
        this.componentLen = components.length;
    }

    /**
     * Create a new ObjectIdentifier for internal use. The values are
     * neither checked nor cloned.
     */
    public static ObjectIdentifier newInternal(int[] values) {
        return new ObjectIdentifier(values, true);
    }

    /*
     * Helper function -- get the OID from a stream, after tag and
     * length are verified.
     */
    private void initFromEncoding (DerInputStream in, int bufferEnd)
        throws IOException
    {

        /*
         * Now get the components ("sub IDs") one at a time.  We fill a
         * temporary buffer, resizing it as needed.
         */
        int             component;
        boolean         first_subid = true;

        for (components = new int [allocationQuantum], componentLen = 0;
                in.available () > bufferEnd;
        ) {
            component = getComponent (in);
            if (component < 0) {
                throw new IOException(
                    "ObjectIdentifier() -- " +
                    "component values must be nonnegative");
            }
            if (first_subid) {
                int     X, Y;

                /*
                 * NOTE:  the allocation quantum is large enough that we know
                 * we don't have to reallocate here!
                 */
                if (component < 40)
                    X = 0;
                else if (component < 80)
                    X = 1;
                else
                    X = 2;
                Y = component - ( X * 40);
                components [0] = X;
                components [1] = Y;
                componentLen = 2;

                first_subid = false;

            } else {

                /*
                 * Other components are encoded less exotically.  The only
                 * potential trouble is the need to grow the array.
                 */
                if (componentLen >= components.length) {
                    int         tmp_components [];

                    tmp_components = new int [components.length
                                        + allocationQuantum];
                    System.arraycopy (components, 0, tmp_components, 0,
                            components.length);
                    components = tmp_components;
                }
                components [componentLen++] = component;
            }
        }

        checkValidOid(components, componentLen);

        /*
         * Final sanity check -- if we didn't use exactly the number of bytes
         * specified, something's quite wrong.
         */
        if (in.available () != bufferEnd) {
            throw new IOException (
                    "ObjectIdentifier() -- malformed input data");
        }
    }


    /*
     * n.b. the only public interface is DerOutputStream.putOID()
     */
    void encode (DerOutputStream out) throws IOException
    {
        DerOutputStream bytes = new DerOutputStream ();
        int i;

        // According to ISO X.660, when the 1st component is 0 or 1, the 2nd
        // component is restricted to be less than or equal to 39, thus make
        // it small enough to be encoded into one single byte.
        if (components[0] < 2) {
            bytes.write ((components [0] * 40) + components [1]);
        } else {
            putComponent(bytes, (components [0] * 40) + components [1]);
        }
        for (i = 2; i < componentLen; i++)
            putComponent (bytes, components [i]);

        /*
         * Now that we've constructed the component, encode
         * it in the stream we were given.
         */
        out.write (DerValue.tag_ObjectId, bytes);
    }

    /*
     * Tricky OID component parsing technique ... note that one bit
     * per octet is lost, this returns at most 28 bits of component.
     * Also, notice this parses in big-endian format.
     */
    private static int getComponent (DerInputStream in)
    throws IOException
    {
        int retval, i, tmp;

        for (i = 0, retval = 0; i < 4; i++) {
            retval <<= 7;
            tmp = in.getByte ();
            retval |= (tmp & 0x07f);
            if ((tmp & 0x080) == 0)
                return retval;
        }

        throw new IOException ("ObjectIdentifier() -- component value too big");
    }

    /*
     * Reverse of the above routine.  Notice it needs to emit in
     * big-endian form, so it buffers the output until it's ready.
     * (Minimum length encoding is a DER requirement.)
     */
    private static void putComponent (DerOutputStream out, int val)
    throws IOException
    {
        int     i;
        // TODO: val must be <128*128*128*128 here, otherwise, 4 bytes is not
        // enough to hold it. Will address this later.
        byte    buf [] = new byte [4] ;

        for (i = 0; i < 4; i++) {
            buf [i] = (byte) (val & 0x07f);
            val >>>= 7;
            if (val == 0)
                break;
        }
        for ( ; i > 0; --i)
            out.write (buf [i] | 0x080);
        out.write (buf [0]);
    }

    // XXX this API should probably facilitate the JDK sort utility

    /**
     * Compares this identifier with another, for sorting purposes.
     * An identifier does not precede itself.
     *
     * @param other identifer that may precede this one.
     * @return true iff <em>other</em> precedes this one
     *          in a particular sorting order.
     */
    public boolean precedes (ObjectIdentifier other)
    {
        int             i;

        // shorter IDs go first
        if (other == this || componentLen < other.componentLen)
            return false;
        if (other.componentLen < componentLen)
            return true;

        // for each component, the lesser component goes first
        for (i = 0; i < componentLen; i++) {
            if (other.components [i] < components [i])
                return true;
        }

        // identical IDs don't precede each other
        return false;
    }

    /**
     * @deprecated Use equals((Object)oid)
     */
    @Deprecated
    public boolean equals(ObjectIdentifier other) {
        return equals((Object)other);
    }

    /**
     * Compares this identifier with another, for equality.
     *
     * @return true iff the names are identical.
     */
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ObjectIdentifier == false) {
            return false;
        }
        ObjectIdentifier other = (ObjectIdentifier)obj;
        if (componentLen != other.componentLen) {
            return false;
        }
        for (int i = 0; i < componentLen; i++) {
            if (components[i] != other.components[i]) {
                return false;
            }
        }
        return true;
    }

    public int hashCode() {
        int h = componentLen;
        for (int i = 0; i < componentLen; i++) {
            h += components[i] * 37;
        }
        return h;
    }

    /**
     * Returns a string form of the object ID.  The format is the
     * conventional "dot" notation for such IDs, without any
     * user-friendly descriptive strings, since those strings
     * will not be understood everywhere.
     */
    public String toString() {
        String s = stringForm;
        if (s == null) {
            StringBuffer sb = new StringBuffer(componentLen * 4);
            for (int i = 0; i < componentLen; i++) {
                if (i != 0) {
                    sb.append('.');
                }
                sb.append(components[i]);
            }
            s = sb.toString();
            stringForm = s;
        }
        return s;
    }

    /*
     * To simplify, we assume no individual component of an object ID is
     * larger than 32 bits.  Then we represent the path from the root as
     * an array that's (usually) only filled at the beginning.
     */
    private int         components [];                  // path from root
    private int         componentLen;                   // how much is used.

    private transient volatile String stringForm;

    private static final int allocationQuantum = 5;     // >= 2
}
