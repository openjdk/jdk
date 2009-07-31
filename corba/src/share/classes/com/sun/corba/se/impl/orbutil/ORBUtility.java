/*
 * Copyright 2000-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.impl.orbutil;

import java.lang.Character;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.rmi.NoSuchObjectException;
import java.security.AccessController;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.Map.Entry;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.Properties;
import java.util.IdentityHashMap;
import java.util.StringTokenizer;
import java.util.NoSuchElementException;

import javax.rmi.CORBA.ValueHandler;
import javax.rmi.CORBA.ValueHandlerMultiFormat;
import javax.rmi.CORBA.Util;

import org.omg.CORBA.StructMember ;
import org.omg.CORBA.TypeCode ;
import org.omg.CORBA.Any ;
import org.omg.CORBA.TCKind ;
import org.omg.CORBA.SystemException ;
import org.omg.CORBA.CompletionStatus ;
import org.omg.CORBA.DATA_CONVERSION ;
import org.omg.CORBA.BAD_PARAM ;
import org.omg.CORBA.BAD_OPERATION ;
import org.omg.CORBA.INTERNAL ;
import org.omg.CORBA.TypeCodePackage.BadKind ;
import org.omg.CORBA.portable.OutputStream ;
import org.omg.CORBA.portable.InputStream ;

import com.sun.corba.se.pept.transport.ContactInfoList ;

import com.sun.corba.se.spi.ior.IOR ;
import com.sun.corba.se.spi.presentation.rmi.StubAdapter ;
import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.orb.ORBVersion ;
import com.sun.corba.se.spi.orb.ORBVersionFactory ;
import com.sun.corba.se.spi.protocol.CorbaClientDelegate ;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;
import com.sun.corba.se.spi.transport.CorbaContactInfoList ;
import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.spi.ior.iiop.IIOPProfile;
import com.sun.corba.se.spi.ior.iiop.IIOPProfileTemplate;

import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;
import com.sun.corba.se.impl.corba.CORBAObjectImpl ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;
import com.sun.corba.se.impl.logging.OMGSystemException ;
import com.sun.corba.se.impl.ior.iiop.JavaSerializationComponent;

/**
 *  Handy class full of static functions that don't belong in util.Utility for pure ORB reasons.
 */
public final class ORBUtility {
    private ORBUtility() {}

    private static ORBUtilSystemException wrapper = ORBUtilSystemException.get(
        CORBALogDomains.UTIL ) ;
    private static OMGSystemException omgWrapper = OMGSystemException.get(
        CORBALogDomains.UTIL ) ;

    private static StructMember[] members = null;

    private static StructMember[] systemExceptionMembers (ORB orb) {
        if (members == null) {
            members = new StructMember[3];
            members[0] = new StructMember("id", orb.create_string_tc(0), null);
            members[1] = new StructMember("minor", orb.get_primitive_tc(TCKind.tk_long), null);
            members[2] = new StructMember("completed", orb.get_primitive_tc(TCKind.tk_long), null);
        }
        return members;
    }

    private static TypeCode getSystemExceptionTypeCode(ORB orb, String repID, String name) {
        synchronized (TypeCode.class) {
            return orb.create_exception_tc(repID, name, systemExceptionMembers(orb));
        }
    }

    private static boolean isSystemExceptionTypeCode(TypeCode type, ORB orb) {
        StructMember[] systemExceptionMembers = systemExceptionMembers(orb);
        try {
            return (type.kind().value() == TCKind._tk_except &&
                    type.member_count() == 3 &&
                    type.member_type(0).equal(systemExceptionMembers[0].type) &&
                    type.member_type(1).equal(systemExceptionMembers[1].type) &&
                    type.member_type(2).equal(systemExceptionMembers[2].type));
        } catch (BadKind ex) {
            return false;
        } catch (org.omg.CORBA.TypeCodePackage.Bounds ex) {
            return false;
        }
    }

    /**
     * Static method for writing a CORBA standard exception to an Any.
     * @param any The Any to write the SystemException into.
     */
    public static void insertSystemException(SystemException ex, Any any) {
        OutputStream out = any.create_output_stream();
        ORB orb = (ORB)(out.orb());
        String name = ex.getClass().getName();
        String repID = ORBUtility.repositoryIdOf(name);
        out.write_string(repID);
        out.write_long(ex.minor);
        out.write_long(ex.completed.value());
        any.read_value(out.create_input_stream(),
            getSystemExceptionTypeCode(orb, repID, name));
    }

    public static SystemException extractSystemException(Any any) {
        InputStream in = any.create_input_stream();
        ORB orb = (ORB)(in.orb());
        if ( ! isSystemExceptionTypeCode(any.type(), orb)) {
            throw wrapper.unknownDsiSysex(CompletionStatus.COMPLETED_MAYBE);
        }
        return ORBUtility.readSystemException(in);
    }

    /**
     * Creates the correct ValueHandler for the given ORB,
     * querying ORBVersion information.  If the ORB or
     * ORBVersion is null, gets the ValueHandler from
     * Util.createValueHandler.
     */
    public static ValueHandler createValueHandler(ORB orb) {

        if (orb == null)
            return Util.createValueHandler();

        ORBVersion version = orb.getORBVersion();

        if (version == null)
            return Util.createValueHandler();

        if (version.equals(ORBVersionFactory.getOLD()))
            return new ValueHandlerImpl_1_3();
        if (version.equals(ORBVersionFactory.getNEW()))
            return new ValueHandlerImpl_1_3_1();

        return Util.createValueHandler();
    }

    /**
     * Returns true if the given ORB could accurately be determined to be a
     * Kestrel or earlier ORB.  Note: If passed the ORBSingleton, this will return
     * false.
     */
    public static boolean isLegacyORB(ORB orb)
    {
        try {
            ORBVersion currentORB = orb.getORBVersion();
            return currentORB.equals( ORBVersionFactory.getOLD() ) ;
        } catch (SecurityException se) {
            return false;
        }
    }

    /**
     * Returns true if it was accurately determined that the remote ORB is
     * a foreign (non-JavaSoft) ORB.  Note:  If passed the ORBSingleton, this
     * will return false.
     */
    public static boolean isForeignORB(ORB orb)
    {
        if (orb == null)
            return false;

        try {
            return orb.getORBVersion().equals(ORBVersionFactory.getFOREIGN());
        } catch (SecurityException se) {
            return false;
        }
    }

    /** Unmarshal a byte array to an integer.
        Assume the bytes are in BIGENDIAN order.
        i.e. array[offset] is the most-significant-byte
        and  array[offset+3] is the least-significant-byte.
        @param array The array of bytes.
        @param offset The offset from which to start unmarshalling.
    */
    public static int bytesToInt(byte[] array, int offset)
    {
        int b1, b2, b3, b4;

        b1 = (array[offset++] << 24) & 0xFF000000;
        b2 = (array[offset++] << 16) & 0x00FF0000;
        b3 = (array[offset++] << 8)  & 0x0000FF00;
        b4 = (array[offset++] << 0)  & 0x000000FF;

        return (b1 | b2 | b3 | b4);
    }

    /** Marshal an integer to a byte array.
        The bytes are in BIGENDIAN order.
        i.e. array[offset] is the most-significant-byte
        and  array[offset+3] is the least-significant-byte.
        @param array The array of bytes.
        @param offset The offset from which to start marshalling.
    */
    public static void intToBytes(int value, byte[] array, int offset)
    {
        array[offset++] = (byte)((value >>> 24) & 0xFF);
        array[offset++] = (byte)((value >>> 16) & 0xFF);
        array[offset++] = (byte)((value >>> 8) & 0xFF);
        array[offset++] = (byte)((value >>> 0) & 0xFF);
    }

    /** Converts an Ascii Character into Hexadecimal digit
     */
    public static int hexOf( char x )
    {
        int val;

        val = x - '0';
        if (val >=0 && val <= 9)
            return val;

        val = (x - 'a') + 10;
        if (val >= 10 && val <= 15)
            return val;

        val = (x - 'A') + 10;
        if (val >= 10 && val <= 15)
            return val;

        throw wrapper.badHexDigit() ;
    }

    // method moved from util.Utility

    /**
     * Static method for writing a CORBA standard exception to a stream.
     * @param strm The OutputStream to use for marshaling.
     */
    public static void writeSystemException(SystemException ex, OutputStream strm)
    {
        String s;

        s = repositoryIdOf(ex.getClass().getName());
        strm.write_string(s);
        strm.write_long(ex.minor);
        strm.write_long(ex.completed.value());
    }

    /**
     * Static method for reading a CORBA standard exception from a stream.
     * @param strm The InputStream to use for unmarshaling.
     */
    public static SystemException readSystemException(InputStream strm)
    {
        try {
            String name = classNameOf(strm.read_string());
            SystemException ex
                = (SystemException)ORBClassLoader.loadClass(name).newInstance();
            ex.minor = strm.read_long();
            ex.completed = CompletionStatus.from_int(strm.read_long());
            return ex;
        } catch ( Exception ex ) {
            throw wrapper.unknownSysex( CompletionStatus.COMPLETED_MAYBE, ex );
        }
    }

    /**
     * Get the class name corresponding to a particular repository Id.
     * This is used by the system to unmarshal (instantiate) the
     * appropriate exception class for an marshaled as the value of
     * its repository Id.
     * @param repositoryId The repository Id for which we want a class name.
     */
    public static String classNameOf(String repositoryId)
    {
        String className=null;

        className = (String) exceptionClassNames.get(repositoryId);
        if (className == null)
            className = "org.omg.CORBA.UNKNOWN";

        return className;
    }

    /**
     * Return true if this repositoryId is a SystemException.
     * @param repositoryId The repository Id to check.
     */
    public static boolean isSystemException(String repositoryId)
    {
        String className=null;

        className = (String) exceptionClassNames.get(repositoryId);
        if (className == null)
            return false;
        else
            return true;
    }

    /**
     * @return the Java serialization encoding version.
     */
    public static byte getEncodingVersion(ORB orb, IOR ior) {

        // Is Java serialization enabled?
        // Check the JavaSerializationComponent (tagged component)
        // in the IIOPProfile. If present, the peer ORB's GIOP is capable
        // of using Java serialization instead of CDR serialization.
        // In such a case, use Java serialization, iff the java serialization
        // versions match.

        if (orb.getORBData().isJavaSerializationEnabled()) {
            IIOPProfile prof = ior.getProfile();
            IIOPProfileTemplate profTemp =
                (IIOPProfileTemplate) prof.getTaggedProfileTemplate();
            java.util.Iterator iter = profTemp.iteratorById(
                                  ORBConstants.TAG_JAVA_SERIALIZATION_ID);
            if (iter.hasNext()) {
                JavaSerializationComponent jc =
                    (JavaSerializationComponent) iter.next();
                byte jcVersion = jc.javaSerializationVersion();
                if (jcVersion >= Message.JAVA_ENC_VERSION) {
                    return Message.JAVA_ENC_VERSION;
                } else if (jcVersion > Message.CDR_ENC_VERSION) {
                    return jc.javaSerializationVersion();
                } else {
                    // throw error?
                    // Since encodingVersion is <= 0 (CDR_ENC_VERSION).
                }
            }
        }
        return Message.CDR_ENC_VERSION; // default
    }

    /**
     * Get the repository id corresponding to a particular class.
     * This is used by the system to write the
     * appropriate repository id for a system exception.
     * @param name The class name of the system exception.
     */
    public static String repositoryIdOf(String name)
    {
        String id;

        id = (String) exceptionRepositoryIds.get(name);
        if (id == null)
            id = "IDL:omg.org/CORBA/UNKNOWN:1.0";

        return id;
    }

    private static final Hashtable exceptionClassNames = new Hashtable();
    private static final Hashtable exceptionRepositoryIds = new Hashtable();

    static {

        //
        // construct repositoryId -> className hashtable
        //
        exceptionClassNames.put("IDL:omg.org/CORBA/BAD_CONTEXT:1.0",
                                "org.omg.CORBA.BAD_CONTEXT");
        exceptionClassNames.put("IDL:omg.org/CORBA/BAD_INV_ORDER:1.0",
                                "org.omg.CORBA.BAD_INV_ORDER");
        exceptionClassNames.put("IDL:omg.org/CORBA/BAD_OPERATION:1.0",
                                "org.omg.CORBA.BAD_OPERATION");
        exceptionClassNames.put("IDL:omg.org/CORBA/BAD_PARAM:1.0",
                                "org.omg.CORBA.BAD_PARAM");
        exceptionClassNames.put("IDL:omg.org/CORBA/BAD_TYPECODE:1.0",
                                "org.omg.CORBA.BAD_TYPECODE");
        exceptionClassNames.put("IDL:omg.org/CORBA/COMM_FAILURE:1.0",
                                "org.omg.CORBA.COMM_FAILURE");
        exceptionClassNames.put("IDL:omg.org/CORBA/DATA_CONVERSION:1.0",
                                "org.omg.CORBA.DATA_CONVERSION");
        exceptionClassNames.put("IDL:omg.org/CORBA/IMP_LIMIT:1.0",
                                "org.omg.CORBA.IMP_LIMIT");
        exceptionClassNames.put("IDL:omg.org/CORBA/INTF_REPOS:1.0",
                                "org.omg.CORBA.INTF_REPOS");
        exceptionClassNames.put("IDL:omg.org/CORBA/INTERNAL:1.0",
                                "org.omg.CORBA.INTERNAL");
        exceptionClassNames.put("IDL:omg.org/CORBA/INV_FLAG:1.0",
                                "org.omg.CORBA.INV_FLAG");
        exceptionClassNames.put("IDL:omg.org/CORBA/INV_IDENT:1.0",
                                "org.omg.CORBA.INV_IDENT");
        exceptionClassNames.put("IDL:omg.org/CORBA/INV_OBJREF:1.0",
                                "org.omg.CORBA.INV_OBJREF");
        exceptionClassNames.put("IDL:omg.org/CORBA/MARSHAL:1.0",
                                "org.omg.CORBA.MARSHAL");
        exceptionClassNames.put("IDL:omg.org/CORBA/NO_MEMORY:1.0",
                                "org.omg.CORBA.NO_MEMORY");
        exceptionClassNames.put("IDL:omg.org/CORBA/FREE_MEM:1.0",
                                "org.omg.CORBA.FREE_MEM");
        exceptionClassNames.put("IDL:omg.org/CORBA/NO_IMPLEMENT:1.0",
                                "org.omg.CORBA.NO_IMPLEMENT");
        exceptionClassNames.put("IDL:omg.org/CORBA/NO_PERMISSION:1.0",
                                "org.omg.CORBA.NO_PERMISSION");
        exceptionClassNames.put("IDL:omg.org/CORBA/NO_RESOURCES:1.0",
                                "org.omg.CORBA.NO_RESOURCES");
        exceptionClassNames.put("IDL:omg.org/CORBA/NO_RESPONSE:1.0",
                                "org.omg.CORBA.NO_RESPONSE");
        exceptionClassNames.put("IDL:omg.org/CORBA/OBJ_ADAPTER:1.0",
                                "org.omg.CORBA.OBJ_ADAPTER");
        exceptionClassNames.put("IDL:omg.org/CORBA/INITIALIZE:1.0",
                                "org.omg.CORBA.INITIALIZE");
        exceptionClassNames.put("IDL:omg.org/CORBA/PERSIST_STORE:1.0",
                                "org.omg.CORBA.PERSIST_STORE");
        exceptionClassNames.put("IDL:omg.org/CORBA/TRANSIENT:1.0",
                                "org.omg.CORBA.TRANSIENT");
        exceptionClassNames.put("IDL:omg.org/CORBA/UNKNOWN:1.0",
                                "org.omg.CORBA.UNKNOWN");
        exceptionClassNames.put("IDL:omg.org/CORBA/OBJECT_NOT_EXIST:1.0",
                                "org.omg.CORBA.OBJECT_NOT_EXIST");

        // SystemExceptions from OMG Transactions Service Spec
        exceptionClassNames.put("IDL:omg.org/CORBA/INVALID_TRANSACTION:1.0",
                                "org.omg.CORBA.INVALID_TRANSACTION");
        exceptionClassNames.put("IDL:omg.org/CORBA/TRANSACTION_REQUIRED:1.0",
                                "org.omg.CORBA.TRANSACTION_REQUIRED");
        exceptionClassNames.put("IDL:omg.org/CORBA/TRANSACTION_ROLLEDBACK:1.0",
                                "org.omg.CORBA.TRANSACTION_ROLLEDBACK");

        // from portability RTF 98-07-01.txt
        exceptionClassNames.put("IDL:omg.org/CORBA/INV_POLICY:1.0",
                                "org.omg.CORBA.INV_POLICY");

        // from orbrev/00-09-01 (CORBA 2.4 Draft Specification)
        exceptionClassNames.
            put("IDL:omg.org/CORBA/TRANSACTION_UNAVAILABLE:1.0",
                                "org.omg.CORBA.TRANSACTION_UNAVAILABLE");
        exceptionClassNames.put("IDL:omg.org/CORBA/TRANSACTION_MODE:1.0",
                                "org.omg.CORBA.TRANSACTION_MODE");

        // Exception types introduced between CORBA 2.4 and 3.0
        exceptionClassNames.put("IDL:omg.org/CORBA/CODESET_INCOMPATIBLE:1.0",
                                "org.omg.CORBA.CODESET_INCOMPATIBLE");
        exceptionClassNames.put("IDL:omg.org/CORBA/REBIND:1.0",
                                "org.omg.CORBA.REBIND");
        exceptionClassNames.put("IDL:omg.org/CORBA/TIMEOUT:1.0",
                                "org.omg.CORBA.TIMEOUT");
        exceptionClassNames.put("IDL:omg.org/CORBA/BAD_QOS:1.0",
                                "org.omg.CORBA.BAD_QOS");

        // Exception types introduced in CORBA 3.0
        exceptionClassNames.put("IDL:omg.org/CORBA/INVALID_ACTIVITY:1.0",
                                "org.omg.CORBA.INVALID_ACTIVITY");
        exceptionClassNames.put("IDL:omg.org/CORBA/ACTIVITY_COMPLETED:1.0",
                                "org.omg.CORBA.ACTIVITY_COMPLETED");
        exceptionClassNames.put("IDL:omg.org/CORBA/ACTIVITY_REQUIRED:1.0",
                                "org.omg.CORBA.ACTIVITY_REQUIRED");

        //
        // construct className -> repositoryId hashtable
        //
        Enumeration keys = exceptionClassNames.keys();
        java.lang.Object s;
        String rId;
        String cName;

        try{
            while (keys.hasMoreElements()) {
                s = keys.nextElement();
                rId = (String) s;
                cName = (String) exceptionClassNames.get(rId);
                exceptionRepositoryIds.put (cName, rId);
            }
        } catch (NoSuchElementException e) { }
    }

    /** Parse a version string such as "1.1.6" or "jdk1.2fcs" into
        a version array of integers {1, 1, 6} or {1, 2}.
        A string of "n." or "n..m" is equivalent to "n.0" or "n.0.m" respectively.
    */
    public static int[] parseVersion(String version) {
        if (version == null)
            return new int[0];
        char[] s = version.toCharArray();
        //find the maximum span of the string "n.n.n..." where n is an integer
        int start = 0;
        for (; start < s.length  && (s[start] < '0' || s[start] > '9'); ++start)
            if (start == s.length)      //no digit found
                return new int[0];
        int end = start + 1;
        int size = 1;
        for (; end < s.length; ++end)
            if (s[end] == '.')
                ++size;
            else if (s[end] < '0' || s[end] > '9')
                break;
        int[] val = new int[size];
        for (int i = 0; i < size; ++i) {
            int dot = version.indexOf('.', start);
            if (dot == -1 || dot > end)
                dot = end;
            if (start >= dot)   //cases like "n." or "n..m"
                val[i] = 0;     //convert equivalent to "n.0" or "n.0.m"
            else
                val[i] = Integer.parseInt(version.substring(start, dot));
            start = dot + 1;
        }
        return val;
    }

    /** Compare two version arrays.
        Return 1, 0 or -1 if v1 is greater than, equal to, or less than v2.
    */
    public static int compareVersion(int[] v1, int[] v2) {
        if (v1 == null)
            v1 = new int[0];
        if (v2 == null)
            v2 = new int[0];
        for (int i = 0; i < v1.length; ++i) {
            if (i >= v2.length || v1[i] > v2[i])        //v1 is longer or greater than v2
                return 1;
            if (v1[i] < v2[i])
                return -1;
        }
        return v1.length == v2.length ? 0 : -1;
    }

    /** Compare two version strings.
        Return 1, 0 or -1 if v1 is greater than, equal to, or less than v2.
    */
    public static synchronized int compareVersion(String v1, String v2) {
        return compareVersion(parseVersion(v1), parseVersion(v2));
    }

    private static String compressClassName( String name )
    {
        // Note that this must end in . in order to be renamed correctly.
        String prefix = "com.sun.corba.se." ;
        if (name.startsWith( prefix ) ) {
            return "(ORB)." + name.substring( prefix.length() ) ;
        } else
            return name ;
    }

    // Return a compressed representation of the thread name.  This is particularly
    // useful on the server side, where there are many SelectReaderThreads, and
    // we need a short unambiguous name for such threads.
    public static String getThreadName( Thread thr )
    {
        if (thr == null)
            return "null" ;

        // This depends on the formatting in SelectReaderThread and CorbaConnectionImpl.
        // Pattern for SelectReaderThreads:
        // SelectReaderThread CorbaConnectionImpl[ <host> <post> <state>]
        // Any other pattern in the Thread's name is just returned.
        String name = thr.getName() ;
        StringTokenizer st = new StringTokenizer( name ) ;
        int numTokens = st.countTokens() ;
        if (numTokens != 5)
            return name ;

        String[] tokens = new String[numTokens] ;
        for (int ctr=0; ctr<numTokens; ctr++ )
            tokens[ctr] = st.nextToken() ;

        if( !tokens[0].equals("SelectReaderThread"))
            return name ;

        return "SelectReaderThread[" + tokens[2] + ":" + tokens[3] + "]" ;
    }

    private static String formatStackTraceElement( StackTraceElement ste )
    {
        return compressClassName( ste.getClassName() ) + "." + ste.getMethodName() +
            (ste.isNativeMethod() ? "(Native Method)" :
             (ste.getFileName() != null && ste.getLineNumber() >= 0 ?
              "(" + ste.getFileName() + ":" + ste.getLineNumber() + ")" :
              (ste.getFileName() != null ?  "("+ste.getFileName()+")" : "(Unknown Source)")));
    }

    private static void printStackTrace( StackTraceElement[] trace )
    {
        System.out.println( "    Stack Trace:" ) ;
        // print the stack trace, ommitting the zeroth element, which is
        // always this method.
        for ( int ctr = 1; ctr < trace.length; ctr++ ) {
            System.out.print( "        >" ) ;
            System.out.println( formatStackTraceElement( trace[ctr] ) ) ;
        }
    }

    //
    // Implements all dprint calls in this package.
    //
    public static synchronized void dprint(java.lang.Object obj, String msg) {
        System.out.println(
            compressClassName( obj.getClass().getName() ) + "("  +
            getThreadName( Thread.currentThread() ) + "): " + msg);
    }

    public static synchronized void dprint(String className, String msg) {
        System.out.println(
            compressClassName( className ) + "("  +
            getThreadName( Thread.currentThread() ) + "): " + msg);
    }

    public synchronized void dprint(String msg) {
        ORBUtility.dprint(this, msg);
    }

    public static synchronized void dprintTrace(Object obj, String msg) {
        ORBUtility.dprint(obj, msg);

        Throwable thr = new Throwable() ;
        printStackTrace( thr.getStackTrace() ) ;
    }

    public static synchronized void dprint(java.lang.Object caller,
        String msg, Throwable t)
    {
        System.out.println(
            compressClassName( caller.getClass().getName() ) +
            '(' + Thread.currentThread() + "): " + msg);

        if (t != null)
            printStackTrace( t.getStackTrace() ) ;
    }

    public static String[] concatenateStringArrays( String[] arr1, String[] arr2 )
    {
        String[] result = new String[
            arr1.length + arr2.length ] ;

        for (int ctr = 0; ctr<arr1.length; ctr++)
            result[ctr] = arr1[ctr] ;

        for (int ctr = 0; ctr<arr2.length; ctr++)
            result[ctr + arr1.length] = arr2[ctr] ;

        return result ;
    }

    /**
     * Throws the CORBA equivalent of a java.io.NotSerializableException
     *
     * Duplicated from util/Utility for Pure ORB reasons.  There are two
     * reasons for this:
     *
     * 1) We can't introduce dependencies on the util version from outside
     * of the io/util packages since it will not exist in the pure ORB
     * build running on JDK 1.3.x.
     *
     * 2) We need to pick up the correct minor code from OMGSystemException.
     */
    public static void throwNotSerializableForCorba(String className) {
        throw omgWrapper.notSerializable( CompletionStatus.COMPLETED_MAYBE,
            className ) ;
    }

    /**
     * Returns the maximum stream format version supported by our
     * ValueHandler.
     */
    public static byte getMaxStreamFormatVersion() {
        ValueHandler vh = Util.createValueHandler();

        if (!(vh instanceof javax.rmi.CORBA.ValueHandlerMultiFormat))
            return ORBConstants.STREAM_FORMAT_VERSION_1;
        else
            return ((ValueHandlerMultiFormat)vh).getMaximumStreamFormatVersion();
    }

    public static CorbaClientDelegate makeClientDelegate( IOR ior )
    {
        ORB orb = ior.getORB() ;
        CorbaContactInfoList ccil = orb.getCorbaContactInfoListFactory().create( ior ) ;
        CorbaClientDelegate del = orb.getClientDelegateFactory().create(ccil);
        return del ;
    }

    /** This method is used to create untyped object references.
    */
    public static org.omg.CORBA.Object makeObjectReference( IOR ior )
    {
        CorbaClientDelegate del = makeClientDelegate( ior ) ;
        org.omg.CORBA.Object objectImpl = new CORBAObjectImpl() ;
        StubAdapter.setDelegate( objectImpl, del ) ;
        return objectImpl ;
    }

    /** This method obtains an IOR from a CORBA object reference.
    * It will return null if obj is a local object, a null object,
    * or an object implemented by a different ORB.  It will
    * throw BAD_OPERATION if obj is an unconnected RMI-IIOP object.
    * @return IOR the IOR that represents this objref.  This will
    * never be null.
    * @exception BAD_OPERATION (from oi._get_delegate) if obj is a
    * normal objref, but does not have a delegate set.
    * @exception BAD_PARAM if obj is a local object, or else was
    * created by a foreign ORB.
    */
    public static IOR getIOR( org.omg.CORBA.Object obj )
    {
        if (obj == null)
            throw wrapper.nullObjectReference() ;

        IOR ior = null ;
        if (StubAdapter.isStub(obj)) {
            org.omg.CORBA.portable.Delegate del = StubAdapter.getDelegate(
                obj ) ;

            if (del instanceof CorbaClientDelegate) {
                CorbaClientDelegate cdel = (CorbaClientDelegate)del ;
                ContactInfoList cil = cdel.getContactInfoList() ;

                if (cil instanceof CorbaContactInfoList) {
                    CorbaContactInfoList ccil = (CorbaContactInfoList)cil ;
                    ior = ccil.getTargetIOR() ;
                    if (ior == null)
                        throw wrapper.nullIor() ;

                    return ior ;
                } else {
                    // This is our code, but the ContactInfoList is not a
                    // CorbaContactInfoList.  This should not happen, because
                    // we are in the CORBA application of the DCSA framework.
                    // This is a coding error, and thus an INTERNAL exception
                    // should be thrown.
                    // XXX needs minor code
                    throw new INTERNAL() ;
                }
            }

            // obj is implemented by a foreign ORB, because the Delegate is not a
            // ClientDelegate.
            // XXX this case could be handled by marshalling and
            // unmarshalling.  However, object_to_string cannot be used
            // here, as it is implemented with getIOR.  Note that this
            // will require access to an ORB, so that we can create streams
            // as needed.  The ORB is available simply as io._orb().
            throw wrapper.objrefFromForeignOrb() ;
        } else
            throw wrapper.localObjectNotAllowed() ;
    }

    /** Obtains an IOR for the object reference obj, first connecting it to
    * the ORB if necessary.
    * @return IOR the IOR that represents this objref.  This will
    * never be null.
    * @exception BAD_OPERATION if the object could not be connected,
    * if a connection attempt was needed.
    * @exception BAD_PARAM if obj is a local object, or else was
    * created by a foreign ORB.
    */
    public static IOR connectAndGetIOR( ORB orb, org.omg.CORBA.Object obj )
    {
        IOR result ;
        try {
            result = getIOR( obj ) ;
        } catch (BAD_OPERATION bop) {
            if (StubAdapter.isStub(obj)) {
                try {
                    StubAdapter.connect( obj, orb ) ;
                } catch (java.rmi.RemoteException exc) {
                    throw wrapper.connectingServant( exc ) ;
                }
            } else {
                orb.connect( obj ) ;
            }

            result = getIOR( obj ) ;
        }

        return result ;
    }

    public static String operationNameAndRequestId(CorbaMessageMediator m)
    {
        return "op/" + m.getOperationName() + " id/" + m.getRequestId();
    }

    public static boolean isPrintable(char c)
    {
        if (Character.isJavaIdentifierStart(c)) {
            // Letters and $ _
            return true;
        }
        if (Character.isDigit(c)) {
            return true;
        }
        switch (Character.getType(c)) {
        case Character.MODIFIER_SYMBOL : return true; // ` ^
        case Character.DASH_PUNCTUATION : return true; // -
        case Character.MATH_SYMBOL : return true; // = ~ + | < >
        case Character.OTHER_PUNCTUATION : return true; // !@#%&*;':",./?
        case Character.START_PUNCTUATION : return true; // ( [ {
        case Character.END_PUNCTUATION : return true; // ) ] }
        }
        return false;
    }

    public static String getClassSecurityInfo(final Class cl)
    {
        // Returns a String which looks similar to:
        // PermissionCollection java.security.Permissions@1053693 ...
        // (java.io.FilePermission <<ALL FILES>> ....)
        // (java.io.FilePermission /export0/sunwappserv/lib/- ...)
        // ... other permissions ...
        // Domain ProtectionDomain  (file:/export0/sunwappserv/lib-)
        // java.security.Permissions@141fedb (
        // (java.io.FilePermission <<ALL FILES>> ...)
        // (java.io.FilePermission /var/tmp//- ...)

        String result =
            (String)AccessController.doPrivileged(new PrivilegedAction() {
                public java.lang.Object run() {
                    StringBuffer sb = new StringBuffer(500);
                    ProtectionDomain pd = cl.getProtectionDomain();
                    Policy policy = Policy.getPolicy();
                    PermissionCollection pc = policy.getPermissions(pd);
                    sb.append("\nPermissionCollection ");
                    sb.append(pc.toString());
                    // Don't need to add 'Protection Domain' string, it's
                    // in ProtectionDomain.toString() already.
                    sb.append(pd.toString());
                    return sb.toString();
                }
            });
        return result;
    }
}

// End of file.
