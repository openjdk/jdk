/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package com.sun.corba.se.impl.orbutil;

import javax.rmi.CORBA.Util;
import javax.rmi.PortableRemoteObject;

import java.util.Hashtable;
import java.util.Stack;
import java.io.IOException;
import java.util.EmptyStackException;

import com.sun.corba.se.impl.util.Utility;
import com.sun.corba.se.impl.io.IIOPInputStream;
import com.sun.corba.se.impl.io.IIOPOutputStream;
import com.sun.corba.se.impl.util.RepositoryId;
import com.sun.corba.se.impl.util.Utility;

import org.omg.CORBA.TCKind;
import org.omg.CORBA.MARSHAL;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.portable.IndirectionException;
import com.sun.org.omg.SendingContext.CodeBase;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * This class overrides behavior of our current ValueHandlerImpl to
 * provide backwards compatibility with JDK 1.3.0.
 */
public class ValueHandlerImpl_1_3 extends com.sun.corba.se.impl.io.ValueHandlerImpl {

    public ValueHandlerImpl_1_3(){
        super();
    }

    public ValueHandlerImpl_1_3(boolean isInputStream) {
        super(isInputStream);
    }

    /**
     * Writes the value to the stream using java semantics.
     * @param out The stream to write the value to
     * @param value The value to be written to the stream
     **/
    public void writeValue(org.omg.CORBA.portable.OutputStream _out, java.io.Serializable value) {
        super.writeValue(_out, value);
    }

    /**
     * Reads a value from the stream using java semantics.
     * @param in The stream to read the value from
     * @param clazz The type of the value to be read in
     * @param sender The sending context runtime
     **/
    public java.io.Serializable readValue(org.omg.CORBA.portable.InputStream _in,
                                          int offset,
                                          java.lang.Class clazz,
                                          String repositoryID,
                                          org.omg.SendingContext.RunTime _sender)
    {
        return super.readValue(_in, offset, clazz, repositoryID, _sender);
    }

    /**
     * Returns the repository ID for the given RMI value Class.
     * @param clz The class to return a repository ID for.
     * @return the repository ID of the Class.
     **/
    public java.lang.String getRMIRepositoryID(java.lang.Class clz) {
        return RepositoryId_1_3.createForJavaType(clz);
    }

    /**
     * Indicates whether the given Class performs custom or
     * default marshaling.
     * @param clz The class to test for custom marshaling.
     * @return True if the class performs custom marshaling, false
     * if it does not.
     **/
    public boolean isCustomMarshaled(java.lang.Class clz) {
        return super.isCustomMarshaled(clz);
    }

    /**
     * Returns the CodeBase for this ValueHandler.  This is used by
     * the ORB runtime.  The server sends the service context containing
     * the IOR for this CodeBase on the first GIOP reply.  The clients
     * do the same on the first GIOP request.
     * @return the SendingContext.CodeBase of this ValueHandler.
     **/
    public org.omg.SendingContext.RunTime getRunTimeCodeBase() {
        return super.getRunTimeCodeBase();
    }

    /**
     * If the value contains a writeReplace method then the result
     * is returned.  Otherwise, the value itself is returned.
     * @return the true value to marshal on the wire.
     **/
    public java.io.Serializable writeReplace(java.io.Serializable value) {
        return super.writeReplace(value);
    }

    // methods supported for backward compatability so that the appropriate
    // Rep-id calculations take place based on the ORB version

    /**
     *  Returns a boolean of whether or not RepositoryId indicates
     *  FullValueDescriptor.
     *  used for backward compatability
     */

     public boolean useFullValueDescription(Class clazz, String repositoryID)
        throws IOException

     {
        return RepositoryId_1_3.useFullValueDescription(clazz, repositoryID);
     }

     public String getClassName(String id)
     {
        RepositoryId_1_3 repID = RepositoryId_1_3.cache.getId(id);
        return repID.getClassName();
     }

     public Class getClassFromType(String id)
        throws ClassNotFoundException
     {
        RepositoryId_1_3 repId = RepositoryId_1_3.cache.getId(id);
        return repId.getClassFromType();
     }

     public Class getAnyClassFromType(String id)
        throws ClassNotFoundException
     {
        RepositoryId_1_3 repId = RepositoryId_1_3.cache.getId(id);
        return repId.getAnyClassFromType();
     }

     public String createForAnyType(Class cl)
     {
        return RepositoryId_1_3.createForAnyType(cl);
     }

     public String getDefinedInId(String id)
     {
        RepositoryId_1_3 repId = RepositoryId_1_3.cache.getId(id);
        return repId.getDefinedInId();
     }

     public String getUnqualifiedName(String id)
     {
        RepositoryId_1_3 repId = RepositoryId_1_3.cache.getId(id);
        return repId.getUnqualifiedName();
     }

     public String getSerialVersionUID(String id)
     {
        RepositoryId_1_3 repId = RepositoryId_1_3.cache.getId(id);
        return repId.getSerialVersionUID();
     }

     public boolean isAbstractBase(Class clazz)
     {
        return RepositoryId_1_3.isAbstractBase(clazz);
     }

     public boolean isSequence(String id)
     {
        RepositoryId_1_3 repId = RepositoryId_1_3.cache.getId(id);
        return repId.isSequence();
     }

    /**
     * Preserves the incorrect 1.3 behavior which truncates Java chars in
     * arrays to 8-bit CORBA chars.  Bug 4367783.  This enables us to
     * continue interoperating with our legacy ORBs.  If this goes into
     * Ladybird, then Ladybird and Kestrel will interoperate as long as
     * people don't use chars greater than 8-bits.
     */
    protected void writeCharArray(org.omg.CORBA_2_3.portable.OutputStream out,
                                char[] array,
                                int offset,
                                int length)
    {
        out.write_char_array(array, offset, length);
    }

    /**
     * Preserves the incorrect 1.3 behavior which truncates Java chars in
     * arrays to 8-bit CORBA chars.  Bug 4367783.  This enables us to
     * continue interoperating with our legacy ORBs.  If this goes into
     * Ladybird, then Ladybird and Kestrel will interoperate as long as
     * people don't use chars greater than 8-bits.
     */
    protected void readCharArray(org.omg.CORBA_2_3.portable.InputStream in,
                                 char[] array,
                                 int offset,
                                 int length)
    {
        in.read_char_array(array, offset, length);
    }

    protected final String getOutputStreamClassName() {
        return "com.sun.corba.se.impl.orbutil.IIOPOutputStream_1_3";
    }

    protected final String getInputStreamClassName() {
        return "com.sun.corba.se.impl.orbutil.IIOPInputStream_1_3";
    }

    /**
     * Our JDK 1.3 and JDK 1.3.1 behavior subclasses override this.
     * The correct behavior is for a Java char to map to a CORBA wchar,
     * but our older code mapped it to a CORBA char.
     */
    protected TCKind getJavaCharTCKind() {
        return TCKind.tk_char;
    }
}
