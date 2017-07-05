/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.servicecontext;

import org.omg.CORBA.SystemException;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA_2_3.portable.InputStream ;
import org.omg.CORBA_2_3.portable.OutputStream ;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.impl.encoding.CDRInputStream ;
import com.sun.corba.se.impl.encoding.EncapsInputStream ;
import com.sun.corba.se.impl.encoding.EncapsOutputStream ;
import com.sun.corba.se.impl.orbutil.ORBUtility ;

/** Base class for all ServiceContext classes.
* There is a derived ServiceContext class for each service context that
* the ORB supports.  Each subclass encapsulates the representation of
* the service context and provides any needed methods for manipulating
* the service context.  Each subclass must provide the following
* members:
* <ul>
* <li>a public static final int SERVICE_CONTEXT_ID that gives the OMG
* (or other) defined id for the service context.  This is needed for the
* registration mechanism defined in ServiceContexts. OMG defined
* service context ids are taken from section 13.6.7 of ptc/98-12-04.</li>
* <li>a public constructor that takes an InputStream as its argument.</li>
* <li>Appropriate definitions of getId() and writeData().  getId() must
* return SERVICE_CONTEXT_ID.</li>
* </ul>
* <p>
* The subclass can be constructed either directly from the service context
* representation, or by reading the representation from an input stream.
* These cases are needed when the service context is created and written to
* the request or reply, and when the service context is read from the
* received request or reply.
*/
public abstract class ServiceContext {
    /** Simple default constructor used when subclass is constructed
     * from its representation.
     */
    protected ServiceContext() { }

    private void dprint( String msg )
    {
        ORBUtility.dprint( this, msg ) ;
    }

    /** Stream constructor used when subclass is constructed from an
     * InputStream.  This constructor must be called by super( stream )
     * in the subclass.  After this constructor completes, the service
     * context representation can be read from in.
     * Note that the service context id has been consumed from the input
     * stream before this object is constructed.
     */
    protected ServiceContext(InputStream s, GIOPVersion gv) throws SystemException
    {
        in = s;
    }

    /** Returns Service context id.  Must be overloaded in subclass.
     */
    public abstract int getId() ;

    /** Write the service context to an output stream.  This method
     * must be used for writing the service context to a request or reply
     * header.
     */
    public void write(OutputStream s, GIOPVersion gv) throws SystemException
    {
        EncapsOutputStream os =
            sun.corba.OutputStreamFactory.newEncapsOutputStream((ORB)(s.orb()), gv);
        os.putEndian() ;
        writeData( os ) ;
        byte[] data = os.toByteArray() ;

        s.write_long(getId());
        s.write_long(data.length);
        s.write_octet_array(data, 0, data.length);
    }

    /** Writes the data used to represent the subclasses service context
     * into an encapsulation stream.  Must be overloaded in subclass.
     */
    protected abstract void writeData( OutputStream os ) ;

    /** in is the stream containing the service context representation.
     * It is constructed by the stream constructor, and available for use
     * in the subclass stream constructor.
     */
    protected InputStream in = null ;

    public String toString()
    {
        return "ServiceContext[ id=" + getId() + " ]" ;
    }
}
