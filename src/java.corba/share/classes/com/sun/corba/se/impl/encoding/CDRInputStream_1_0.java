/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.encoding;

import java.io.IOException;
import java.io.Serializable;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.io.OptionalDataException;
import java.io.IOException;

import java.util.Stack;

import java.net.URL;
import java.net.MalformedURLException;

import java.nio.ByteBuffer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.math.BigDecimal;

import java.rmi.Remote;
import java.rmi.StubNotFoundException;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;

import org.omg.CORBA.SystemException;
import org.omg.CORBA.Object;
import org.omg.CORBA.Principal;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.Any;
import org.omg.CORBA.portable.Delegate;
import org.omg.CORBA.portable.ValueBase;
import org.omg.CORBA.portable.IndirectionException;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.TCKind;
import org.omg.CORBA.TypeCodePackage.BadKind;
import org.omg.CORBA.CustomMarshal;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.Principal;
import org.omg.CORBA.Any;
import org.omg.CORBA.portable.BoxedValueHelper;
import org.omg.CORBA.portable.ValueFactory;
import org.omg.CORBA.portable.CustomValue;
import org.omg.CORBA.portable.StreamableValue;
import org.omg.CORBA.MARSHAL;
import org.omg.CORBA.portable.IDLEntity;

import javax.rmi.PortableRemoteObject;
import javax.rmi.CORBA.Tie;
import javax.rmi.CORBA.Util;
import javax.rmi.CORBA.ValueHandler;

import com.sun.corba.se.pept.protocol.MessageMediator;
import com.sun.corba.se.pept.transport.ByteBufferPool;

import com.sun.corba.se.spi.protocol.RequestDispatcherRegistry;
import com.sun.corba.se.spi.protocol.CorbaClientDelegate;

import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.IORFactories;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.orb.ORBVersionFactory;
import com.sun.corba.se.spi.orb.ORBVersion;

import com.sun.corba.se.spi.protocol.CorbaMessageMediator;

import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.presentation.rmi.PresentationManager;
import com.sun.corba.se.spi.presentation.rmi.StubAdapter;
import com.sun.corba.se.spi.presentation.rmi.PresentationDefaults;

import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.logging.OMGSystemException;

import com.sun.corba.se.impl.corba.PrincipalImpl;
import com.sun.corba.se.impl.corba.TypeCodeImpl;
import com.sun.corba.se.impl.corba.CORBAObjectImpl;

import com.sun.corba.se.impl.encoding.CDROutputObject;
import com.sun.corba.se.impl.encoding.CodeSetConversion;

import com.sun.corba.se.impl.util.Utility;
import com.sun.corba.se.impl.util.RepositoryId;

import com.sun.corba.se.impl.orbutil.RepositoryIdStrings;
import com.sun.corba.se.impl.orbutil.RepositoryIdInterface;
import com.sun.corba.se.impl.orbutil.RepositoryIdUtility;
import com.sun.corba.se.impl.orbutil.RepositoryIdFactory;

import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.orbutil.CacheTable;


import com.sun.org.omg.CORBA.portable.ValueHelper;

import com.sun.org.omg.SendingContext.CodeBase;

public class CDRInputStream_1_0 extends CDRInputStreamBase
    implements RestorableInputStream
{
    private static final String kReadMethod = "read";
    private static final int maxBlockLength = 0x7fffff00;

    protected BufferManagerRead bufferManagerRead;
    protected ByteBufferWithInfo bbwi;

    // Set to the ORB's transportDebugFlag value.  This value is
    // used if the ORB is null.
    private boolean debug = false;

    protected boolean littleEndian;
    protected ORB orb;
    protected ORBUtilSystemException wrapper ;
    protected OMGSystemException omgWrapper ;
    protected ValueHandler valueHandler = null;

    // Value cache
    private CacheTable valueCache = null;

    // Repository ID cache
    private CacheTable repositoryIdCache = null;

    // codebase cache
    private CacheTable codebaseCache = null;

    // Current Class Stack (repository Ids of current class being read)
    // private Stack currentStack = null;

    // Length of current chunk, or a large positive number if not in a chunk
    protected int blockLength = maxBlockLength;

    // Read end flag (value nesting depth)
    protected int end_flag = 0;

    // Beginning with the resolution to interop issue 3526 (4328?),
    // only enclosing chunked valuetypes are taken into account
    // when computing the nesting level.  However, we still need
    // the old computation around for interoperability with our
    // older ORBs.
    private int chunkedValueNestingLevel = 0;

    // Flag used to determine whether blocksize was zero
    // private int checkForNullBlock = -1;

    // In block flag
    // private boolean inBlock = false;

    // Indicates whether we are inside a value
    // private boolean outerValueDone = true;

    // Int used by read_value(Serializable) that is set by this class
    // before calling ValueFactory.read_value
    protected int valueIndirection = 0;

    // Int set by readStringOrIndirection to communicate the actual
    // offset of the string length field back to the caller
    protected int stringIndirection = 0;

    // Flag indicating whether we are unmarshalling a chunked value
    protected boolean isChunked = false;

    // Repository ID handlers
    private RepositoryIdUtility repIdUtil;
    private RepositoryIdStrings repIdStrs;

    // Code set converters (created when first needed)
    private CodeSetConversion.BTCConverter charConverter;
    private CodeSetConversion.BTCConverter wcharConverter;

    // RMI-IIOP stream format version 2 case in which we know
    // that there is no more optional data available.  If the
    // Serializable's readObject method tries to read anything,
    // we must throw a MARSHAL with the special minor code
    // so that the ValueHandler can give the correct exception
    // to readObject.  The state is cleared when the ValueHandler
    // calls end_value after the readObject method exits.
    private boolean specialNoOptionalDataState = false;

    // Template method
    public CDRInputStreamBase dup()
    {
        CDRInputStreamBase result = null ;

        try {
            result = (CDRInputStreamBase)this.getClass().newInstance();
        } catch (Exception e) {
            throw wrapper.couldNotDuplicateCdrInputStream( e ) ;
        }
        result.init(this.orb,
                    this.bbwi.byteBuffer,
                    this.bbwi.buflen,
                    this.littleEndian,
                    this.bufferManagerRead);

        ((CDRInputStream_1_0)result).bbwi.position(this.bbwi.position());
        // To ensure we keep bbwi.byteBuffer.limit in sync with bbwi.buflen.
        ((CDRInputStream_1_0)result).bbwi.byteBuffer.limit(this.bbwi.buflen);

        return result;
    }

    /**
     * NOTE:  size passed to init means buffer size
     */
    public void init(org.omg.CORBA.ORB orb,
                     ByteBuffer byteBuffer,
                     int size,
                     boolean littleEndian,
                     BufferManagerRead bufferManager)
    {
        this.orb = (ORB)orb;
        this.wrapper = ORBUtilSystemException.get( (ORB)orb,
            CORBALogDomains.RPC_ENCODING ) ;
        this.omgWrapper = OMGSystemException.get( (ORB)orb,
            CORBALogDomains.RPC_ENCODING ) ;
        this.littleEndian = littleEndian;
        this.bufferManagerRead = bufferManager;
        this.bbwi = new ByteBufferWithInfo(orb,byteBuffer,0);
        this.bbwi.buflen = size;
        this.bbwi.byteBuffer.limit(bbwi.buflen);
        this.markAndResetHandler = bufferManagerRead.getMarkAndResetHandler();

        debug = ((ORB)orb).transportDebugFlag;
    }

    // See description in CDRInputStream
    void performORBVersionSpecificInit() {
        createRepositoryIdHandlers();
    }

    private final void createRepositoryIdHandlers()
    {
        repIdUtil = RepositoryIdFactory.getRepIdUtility();
        repIdStrs = RepositoryIdFactory.getRepIdStringsFactory();
    }

    public GIOPVersion getGIOPVersion() {
        return GIOPVersion.V1_0;
    }

    // Called by Request and Reply message. Valid for GIOP versions >= 1.2 only.
    // Illegal for GIOP versions < 1.2.
    void setHeaderPadding(boolean headerPadding) {
        throw wrapper.giopVersionError();
    }

    protected final int computeAlignment(int index, int align) {
        if (align > 1) {
            int incr = index & (align - 1);
            if (incr != 0)
                return align - incr;
        }

        return 0;
    }

    public int getSize()
    {
        return bbwi.position();
    }

    protected void checkBlockLength(int align, int dataSize) {
        // Since chunks can end at arbitrary points (though not within
        // primitive CDR types, arrays of primitives, strings, wstrings,
        // or indirections),
        // we must check here for termination of the current chunk.
        if (!isChunked)
            return;

        // RMI-IIOP stream format version 2 case in which we know
        // that there is no more optional data available.  If the
        // Serializable's readObject method tries to read anything,
        // we must throw a MARSHAL exception with the special minor code
        // so that the ValueHandler can give the correct exception
        // to readObject.  The state is cleared when the ValueHandler
        // calls end_value after the readObject method exits.
        if (specialNoOptionalDataState) {
            throw omgWrapper.rmiiiopOptionalDataIncompatible1() ;
        }

        boolean checkForEndTag = false;

        // Are we at the end of the current chunk?  If so,
        // try to interpret the next long as a chunk length.
        // (It has to be either a chunk length, end tag,
        // or valuetag.)
        //
        // If it isn't a chunk length, blockLength will
        // remain set to maxBlockLength.
        if (blockLength == get_offset()) {

            blockLength = maxBlockLength;
            start_block();

            // What's next is either a valuetag or
            // an end tag.  If it's a valuetag, we're
            // probably being called as part of the process
            // to read the valuetag.  If it's an end tag,
            // then there isn't enough data left in
            // this valuetype to read!
            if (blockLength == maxBlockLength)
                checkForEndTag = true;

        } else
        if (blockLength < get_offset()) {
            // Are we already past the end of the current chunk?
            // This is always an error.
            throw wrapper.chunkOverflow() ;
        }

        // If what's next on the wire isn't a chunk length or
        // what we want to read (which can't be split across chunks)
        // won't fit in the current chunk, throw this exception.
        // This probably means that we're in an RMI-IIOP
        // Serializable's readObject method or a custom marshaled
        // IDL type is reading too much/in an incorrect order
        int requiredNumBytes =
                            computeAlignment(bbwi.position(), align) + dataSize;

        if (blockLength != maxBlockLength &&
            blockLength < get_offset() + requiredNumBytes) {
            throw omgWrapper.rmiiiopOptionalDataIncompatible2() ;
        }

        // REVISIT - We should look at using the built in advancement
        //           of using ByteBuffer.get() rather than explicitly
        //           advancing the ByteBuffer's position.
        //           This is true for anywhere we are incrementing
        //           the ByteBuffer's position.
        if (checkForEndTag) {
            int nextLong = read_long();
            bbwi.position(bbwi.position() - 4);

            // It was an end tag, so there wasn't enough data
            // left in the valuetype's encoding on the wire
            // to read what we wanted
            if (nextLong < 0)
                throw omgWrapper.rmiiiopOptionalDataIncompatible3() ;
        }
    }

    protected void alignAndCheck(int align, int n) {

        checkBlockLength(align, n);

        // WARNING: Must compute real alignment after calling
        // checkBlockLength since it may move the position
        int alignResult = computeAlignment(bbwi.position(), align);
        bbwi.position(bbwi.position() + alignResult);

        if (bbwi.position() + n > bbwi.buflen)
            grow(align, n);
    }

    //
    // This can be overridden....
    //
    protected void grow(int align, int n) {

        bbwi.needed = n;

        bbwi = bufferManagerRead.underflow(bbwi);

    }

    //
    // Marshal primitives.
    //

    public final void consumeEndian() {
        littleEndian = read_boolean();
    }

    // No such type in java
    public final double read_longdouble() {
        throw wrapper.longDoubleNotImplemented( CompletionStatus.COMPLETED_MAYBE);
    }

    public final boolean read_boolean() {
        return (read_octet() != 0);
    }

    public final char read_char() {
        alignAndCheck(1, 1);

        return getConvertedChars(1, getCharConverter())[0];
    }

    public char read_wchar() {

        // Don't allow transmission of wchar/wstring data with
        // foreign ORBs since it's against the spec.
        if (ORBUtility.isForeignORB((ORB)orb)) {
            throw wrapper.wcharDataInGiop10( CompletionStatus.COMPLETED_MAYBE);
        }

        // If we're talking to one of our legacy ORBs, do what
        // they did:
        int b1, b2;

        alignAndCheck(2, 2);

        if (littleEndian) {
            b2 = bbwi.byteBuffer.get(bbwi.position()) & 0x00FF;
            bbwi.position(bbwi.position() + 1);
            b1 = bbwi.byteBuffer.get(bbwi.position()) & 0x00FF;
            bbwi.position(bbwi.position() + 1);
        } else {
            b1 = bbwi.byteBuffer.get(bbwi.position()) & 0x00FF;
            bbwi.position(bbwi.position() + 1);
            b2 = bbwi.byteBuffer.get(bbwi.position()) & 0x00FF;
            bbwi.position(bbwi.position() + 1);
        }

        return (char)((b1 << 8) + (b2 << 0));
    }

    public final byte read_octet() {

        alignAndCheck(1, 1);

        byte b = bbwi.byteBuffer.get(bbwi.position());
        bbwi.position(bbwi.position() + 1);

        return b;
    }

    public final short read_short() {
        int b1, b2;

        alignAndCheck(2, 2);

        if (littleEndian) {
            b2 = (bbwi.byteBuffer.get(bbwi.position()) << 0) & 0x000000FF;
            bbwi.position(bbwi.position() + 1);
            b1 = (bbwi.byteBuffer.get(bbwi.position()) << 8) & 0x0000FF00;
            bbwi.position(bbwi.position() + 1);
        } else {
            b1 = (bbwi.byteBuffer.get(bbwi.position()) << 8) & 0x0000FF00;
            bbwi.position(bbwi.position() + 1);
            b2 = (bbwi.byteBuffer.get(bbwi.position()) << 0) & 0x000000FF;
            bbwi.position(bbwi.position() + 1);
        }

        return (short)(b1 | b2);
    }

    public final short read_ushort() {
        return read_short();
    }

    public final int read_long() {
        int b1, b2, b3, b4;

        alignAndCheck(4, 4);

        int bufPos = bbwi.position();
        if (littleEndian) {
            b4 = bbwi.byteBuffer.get(bufPos++) & 0xFF;
            b3 = bbwi.byteBuffer.get(bufPos++) & 0xFF;
            b2 = bbwi.byteBuffer.get(bufPos++) & 0xFF;
            b1 = bbwi.byteBuffer.get(bufPos++) & 0xFF;
        } else {
            b1 = bbwi.byteBuffer.get(bufPos++) & 0xFF;
            b2 = bbwi.byteBuffer.get(bufPos++) & 0xFF;
            b3 = bbwi.byteBuffer.get(bufPos++) & 0xFF;
            b4 = bbwi.byteBuffer.get(bufPos++) & 0xFF;
        }
        bbwi.position(bufPos);

        return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }

    public final int read_ulong() {
        return read_long();
    }

    public final long read_longlong() {
        long i1, i2;

        alignAndCheck(8, 8);

        if (littleEndian) {
            i2 = read_long() & 0xFFFFFFFFL;
            i1 = (long)read_long() << 32;
        } else {
            i1 = (long)read_long() << 32;
            i2 = read_long() & 0xFFFFFFFFL;
        }

        return (i1 | i2);
    }

    public final long read_ulonglong() {
        return read_longlong();
    }

    public final float read_float() {
        return Float.intBitsToFloat(read_long());
    }

    public final double read_double() {
        return Double.longBitsToDouble(read_longlong());
    }

    protected final void checkForNegativeLength(int length) {
        if (length < 0)
            throw wrapper.negativeStringLength( CompletionStatus.COMPLETED_MAYBE,
                new Integer(length) ) ;
    }

    protected final String readStringOrIndirection(boolean allowIndirection) {

        int len = read_long();

        //
        // Check for indirection
        //
        if (allowIndirection) {
            if (len == 0xffffffff)
                return null;
            else
                stringIndirection = get_offset() - 4;
        }

        checkForNegativeLength(len);

        return internalReadString(len);
    }

    private final String internalReadString(int len) {
        // Workaround for ORBs which send string lengths of
        // zero to mean empty string.
        //
        // IMPORTANT: Do not replace 'new String("")' with "", it may result
        // in a Serialization bug (See serialization.zerolengthstring) and
        // bug id: 4728756 for details
        if (len == 0)
            return new String("");

        char[] result = getConvertedChars(len - 1, getCharConverter());

        // Skip over the 1 byte null
        read_octet();

        return new String(result, 0, getCharConverter().getNumChars());
    }

    public final String read_string() {
        return readStringOrIndirection(false);
    }

    public String read_wstring() {
        // Don't allow transmission of wchar/wstring data with
        // foreign ORBs since it's against the spec.
        if (ORBUtility.isForeignORB((ORB)orb)) {
            throw wrapper.wcharDataInGiop10( CompletionStatus.COMPLETED_MAYBE);
        }

        int len = read_long();

        //
        // Workaround for ORBs which send string lengths of
        // zero to mean empty string.
        //
        //
        // IMPORTANT: Do not replace 'new String("")' with "", it may result
        // in a Serialization bug (See serialization.zerolengthstring) and
        // bug id: 4728756 for details
        if (len == 0)
            return new String("");

        checkForNegativeLength(len);

        len--;
        char[] c = new char[len];

        for (int i = 0; i < len; i++)
            c[i] = read_wchar();

        // skip the two null terminator bytes
        read_wchar();
        // bbwi.position(bbwi.position() + 2);

        return new String(c);
    }

    public final void read_octet_array(byte[] b, int offset, int length) {
        if ( b == null )
            throw wrapper.nullParam() ;

        // Must call alignAndCheck at least once to ensure
        // we aren't at the end of a chunk.  Of course, we
        // should only call it if we actually need to read
        // something, otherwise we might end up with an
        // exception at the end of the stream.
        if (length == 0)
            return;

        alignAndCheck(1, 1);

        int n = offset;
        while (n < length+offset) {
            int avail;
            int bytes;
            int wanted;

            avail = bbwi.buflen - bbwi.position();
            if (avail <= 0) {
                grow(1, 1);
                avail = bbwi.buflen - bbwi.position();
            }
            wanted = (length + offset) - n;
            bytes = (wanted < avail) ? wanted : avail;
            // Microbenchmarks are showing a loop of ByteBuffer.get(int) being
            // faster than ByteBuffer.get(byte[], int, int).
            for (int i = 0; i < bytes; i++) {
                b[n+i] = bbwi.byteBuffer.get(bbwi.position() + i);
            }

            bbwi.position(bbwi.position() + bytes);

            n += bytes;
        }
    }

    public Principal read_Principal() {
        int len = read_long();
        byte[] pvalue = new byte[len];
        read_octet_array(pvalue,0,len);

        Principal p = new PrincipalImpl();
        p.name(pvalue);
        return p;
    }

    public TypeCode read_TypeCode() {
        TypeCodeImpl tc = new TypeCodeImpl(orb);
        tc.read_value(parent);
        return tc;
    }

    public Any read_any() {
        Any any = orb.create_any();
        TypeCodeImpl tc = new TypeCodeImpl(orb);

        // read off the typecode

        // REVISIT We could avoid this try-catch if we could peek the typecode
        // kind off this stream and see if it is a tk_value.  Looking at the
        // code we know that for tk_value the Any.read_value() below
        // ignores the tc argument anyway (except for the kind field).
        // But still we would need to make sure that the whole typecode,
        // including encapsulations, is read off.
        try {
            tc.read_value(parent);
        } catch (MARSHAL ex) {
            if (tc.kind().value() != TCKind._tk_value)
                throw ex;
            // We can be sure that the whole typecode encapsulation has been
            // read off.
            dprintThrowable(ex);
        }
        // read off the value of the any
        any.read_value(parent, tc);

        return any;
    }

    public org.omg.CORBA.Object read_Object() {
        return read_Object(null);
    }

    // ------------ RMI related methods --------------------------

    // IDL to Java ptc-00-01-08 1.21.4.1
    //
    // The clz argument to read_Object can be either a stub
    // Class or the "Class object for the RMI/IDL interface type
    // that is statically expected."
    // This functions as follows:
    // 1. If clz==null, just use the repository ID from the stub
    // 2. If clz is a stub class, just use it as a static factory.
    //    clz is a stub class iff StubAdapter.isStubClass( clz ).
    //    In addition, clz is a IDL stub class iff
    //    IDLEntity.class.isAssignableFrom( clz ).
    // 3. If clz is an interface, use it to create the appropriate
    //    stub factory.

    public org.omg.CORBA.Object read_Object(Class clz)
    {
        // In any case, we must first read the IOR.
        IOR ior = IORFactories.makeIOR(parent) ;
        if (ior.isNil()) {
            return null ;
        }

        PresentationManager.StubFactoryFactory sff = ORB.getStubFactoryFactory() ;
        String codeBase = ior.getProfile().getCodebase() ;
        PresentationManager.StubFactory stubFactory = null ;

        if (clz == null) {
            RepositoryId rid = RepositoryId.cache.getId( ior.getTypeId() ) ;
            String className = rid.getClassName() ;
            orb.validateIORClass(className);
            boolean isIDLInterface = rid.isIDLType() ;

            if (className == null || className.equals( "" ))
                stubFactory = null ;
            else
                try {
                    stubFactory = sff.createStubFactory( className,
                        isIDLInterface, codeBase, (Class)null,
                        (ClassLoader)null );
                } catch (Exception exc) {
                    // Could not create stubFactory, so use null.
                    // XXX stubFactory handling is still too complex:
                    // Can we resolve the stubFactory question once in
                    // a single place?
                    stubFactory = null ;
                }
        } else if (StubAdapter.isStubClass( clz )) {
            stubFactory = PresentationDefaults.makeStaticStubFactory(
                clz ) ;
        } else {
            // clz is an interface class
            boolean isIDL = IDLEntity.class.isAssignableFrom( clz ) ;
            stubFactory = sff.createStubFactory( clz.getName(),
                isIDL, codeBase, clz, clz.getClassLoader() ) ;
        }
        return internalIORToObject( ior, stubFactory, orb ) ;
    }

    /*
     * This is used as a general utility (e.g., the PortableInterceptor
     * implementation uses it.   If stubFactory is null, the ior's
     * IIOPProfile must support getServant.
     */
    public static org.omg.CORBA.Object internalIORToObject(
        IOR ior, PresentationManager.StubFactory stubFactory, ORB orb)
    {
        ORBUtilSystemException wrapper = ORBUtilSystemException.get(
            (ORB)orb, CORBALogDomains.RPC_ENCODING ) ;

        java.lang.Object servant = ior.getProfile().getServant() ;
        if (servant != null ) {
            if (servant instanceof Tie) {
                String codebase = ior.getProfile().getCodebase();
                org.omg.CORBA.Object objref = (org.omg.CORBA.Object)
                    Utility.loadStub( (Tie)servant, stubFactory, codebase,
                        false);

                // If we managed to load a stub, return it, otherwise we
                // must fail...
                if (objref != null) {
                    return objref;
                } else {
                    throw wrapper.readObjectException() ;
                }
            } else if (servant instanceof org.omg.CORBA.Object) {
                if (!(servant instanceof
                        org.omg.CORBA.portable.InvokeHandler)) {
                    return (org.omg.CORBA.Object) servant;
                }
            } else
                throw wrapper.badServantReadObject() ;
        }

        CorbaClientDelegate del = ORBUtility.makeClientDelegate( ior ) ;
        org.omg.CORBA.Object objref = null ;
        try {
            objref = stubFactory.makeStub() ;
        } catch (Throwable e) {
            wrapper.stubCreateError( e ) ;

            if (e instanceof ThreadDeath) {
                throw (ThreadDeath) e;
            }

            // Return the "default" stub...
            objref = new CORBAObjectImpl() ;
        }

        StubAdapter.setDelegate( objref, del ) ;
        return objref;
    }

    public java.lang.Object read_abstract_interface()
    {
        return read_abstract_interface(null);
    }

    public java.lang.Object read_abstract_interface(java.lang.Class clz)
    {
        boolean object = read_boolean();

        if (object) {
            return read_Object(clz);
        } else {
            return read_value();
        }
    }

    public Serializable read_value()
    {
        return read_value((Class)null);
    }

    private Serializable handleIndirection() {
        int indirection = read_long() + get_offset() - 4;
        if (valueCache != null && valueCache.containsVal(indirection)) {

            java.io.Serializable cachedValue
                = (java.io.Serializable)valueCache.getKey(indirection);
            return cachedValue;
        } else {
            // In RMI-IIOP the ValueHandler will recognize this
            // exception and use the provided indirection value
            // to lookup a possible indirection to an object
            // currently on the deserialization stack.
            throw new IndirectionException(indirection);
        }
    }

    private String readRepositoryIds(int valueTag,
                                     Class expectedType,
                                     String expectedTypeRepId) {
        return readRepositoryIds(valueTag, expectedType,
                                 expectedTypeRepId, null);
    }

    /**
     * Examines the valuetag to see how many (if any) repository IDs
     * are present on the wire.  If no repository ID information
     * is on the wire but the expectedType or expectedTypeRepId
     * is known, it will return one of those (favoring the
     * expectedType's repId). Failing that, it uses the supplied
     * BoxedValueHelper to obtain the repository ID, as a last resort.
     */
    private String readRepositoryIds(int valueTag,
                                     Class expectedType,
                                     String expectedTypeRepId,
                                     BoxedValueHelper factory) {
        switch(repIdUtil.getTypeInfo(valueTag)) {
            case RepositoryIdUtility.NO_TYPE_INFO :
                // Throw an exception if we have no repository ID info and
                // no expectedType to work with.  Otherwise, how would we
                // know what to unmarshal?
                if (expectedType == null) {
                    if (expectedTypeRepId != null) {
                        return expectedTypeRepId;
                    } else if (factory != null) {
                        return factory.get_id();
                    } else {
                        throw wrapper.expectedTypeNullAndNoRepId(
                            CompletionStatus.COMPLETED_MAYBE);
                    }
                }
                return repIdStrs.createForAnyType(expectedType);
            case RepositoryIdUtility.SINGLE_REP_TYPE_INFO :
                return read_repositoryId();
            case RepositoryIdUtility.PARTIAL_LIST_TYPE_INFO :
                return read_repositoryIds();
            default:
                throw wrapper.badValueTag( CompletionStatus.COMPLETED_MAYBE,
                    Integer.toHexString(valueTag) ) ;
        }
    }

    public Serializable read_value(Class expectedType) {

        // Read value tag
        int vType = readValueTag();

        // Is value null?
        if (vType == 0)
            return null;

        // Is this an indirection to a previously
        // read valuetype?
        if (vType == 0xffffffff)
            return handleIndirection();

        // Save where this valuetype started so we
        // can put it in the indirection valueCache
        // later
        int indirection = get_offset() - 4;

        // Need to save this special marker variable
        // to restore its value during recursion
        boolean saveIsChunked = isChunked;

        isChunked = repIdUtil.isChunkedEncoding(vType);

        java.lang.Object value = null;

        String codebase_URL = null;
        if (repIdUtil.isCodeBasePresent(vType)) {
            codebase_URL = read_codebase_URL();
        }

        // Read repository id(s)
        String repositoryIDString
            = readRepositoryIds(vType, expectedType, null);

        // If isChunked was determined to be true based
        // on the valuetag, this will read a chunk length
        start_block();

        // Remember that end_flag keeps track of all nested
        // valuetypes and is used for older ORBs
        end_flag--;
        if (isChunked)
            chunkedValueNestingLevel--;

        if (repositoryIDString.equals(repIdStrs.getWStringValueRepId())) {
            value = read_wstring();
        } else
        if (repositoryIDString.equals(repIdStrs.getClassDescValueRepId())) {
            // read in the class whether with the old ClassDesc or the
            // new one
            value = readClass();
        } else {

            Class valueClass = expectedType;

            // By this point, either the expectedType or repositoryIDString
            // is guaranteed to be non-null.
            if (expectedType == null ||
                !repositoryIDString.equals(repIdStrs.createForAnyType(expectedType))) {

                valueClass = getClassFromString(repositoryIDString,
                                                codebase_URL,
                                                expectedType);
            }

            if (valueClass == null) {
                // No point attempting to use value handler below, since the
                // class information is not available.
                throw wrapper.couldNotFindClass(
                    CompletionStatus.COMPLETED_MAYBE,
                    new ClassNotFoundException());
            }

            if (valueClass != null &&
                org.omg.CORBA.portable.IDLEntity.class.isAssignableFrom(valueClass)) {

                value =  readIDLValue(indirection,
                                      repositoryIDString,
                                      valueClass,
                                      codebase_URL);

            } else {

                // Must be some form of RMI-IIOP valuetype

                try {
                    if (valueHandler == null)
                        valueHandler = ORBUtility.createValueHandler();

                    value = valueHandler.readValue(parent,
                                                   indirection,
                                                   valueClass,
                                                   repositoryIDString,
                                                   getCodeBase());

                } catch(SystemException sysEx) {
                    // Just rethrow any CORBA system exceptions
                    // that come out of the ValueHandler
                    throw sysEx;
                } catch(Exception ex) {
                    throw wrapper.valuehandlerReadException(
                        CompletionStatus.COMPLETED_MAYBE, ex ) ;
                } catch(Error e) {
                    throw wrapper.valuehandlerReadError(
                        CompletionStatus.COMPLETED_MAYBE, e ) ;
                }
            }
        }

        // Skip any remaining chunks until we get to
        // an end tag or a valuetag.  If we see a valuetag,
        // that means there was another valuetype in the sender's
        // version of this class that we need to skip over.
        handleEndOfValue();

        // Read and process the end tag if we're chunking.
        // Assumes that we're at the position of the end tag
        // (handleEndOfValue should assure this)
        readEndTag();

        // Cache the valuetype that we read
        if (valueCache == null)
            valueCache = new CacheTable(orb,false);
        valueCache.put(value, indirection);

        // Allow for possible continuation chunk.
        // If we're a nested valuetype inside of a chunked
        // valuetype, and that enclosing valuetype has
        // more data to write, it will need to have this
        // new chunk begin after we wrote our end tag.
        isChunked = saveIsChunked;
        start_block();

        return (java.io.Serializable)value;
    }

    public Serializable read_value(BoxedValueHelper factory) {

        // Read value tag
        int vType = readValueTag();

        if (vType == 0)
            return null; // value is null
        else if (vType == 0xffffffff) { // Indirection tag
            int indirection = read_long() + get_offset() - 4;
            if (valueCache != null && valueCache.containsVal(indirection))
                {
                    java.io.Serializable cachedValue =
                           (java.io.Serializable)valueCache.getKey(indirection);
                    return cachedValue;
                }
            else {
                throw new IndirectionException(indirection);
            }
        }
        else {
            int indirection = get_offset() - 4;

            // end_block();

            boolean saveIsChunked = isChunked;
            isChunked = repIdUtil.isChunkedEncoding(vType);

            java.lang.Object value = null;

            String codebase_URL = null;
            if (repIdUtil.isCodeBasePresent(vType)){
                codebase_URL = read_codebase_URL();
            }

            // Read repository id
            String repositoryIDString
                = readRepositoryIds(vType, null, null, factory);

            // Compare rep. ids to see if we should use passed helper
            if (!repositoryIDString.equals(factory.get_id()))
                factory = Utility.getHelper(null, codebase_URL, repositoryIDString);

            start_block();
            end_flag--;
            if (isChunked)
                chunkedValueNestingLevel--;

            if (factory instanceof ValueHelper) {
                value = readIDLValueWithHelper((ValueHelper)factory, indirection);
            } else {
                valueIndirection = indirection;  // for callback
                value = factory.read_value(parent);
            }

            handleEndOfValue();
            readEndTag();

            // Put into valueCache
            if (valueCache == null)
                valueCache = new CacheTable(orb,false);
            valueCache.put(value, indirection);

            // allow for possible continuation chunk
            isChunked = saveIsChunked;
            start_block();

            return (java.io.Serializable)value;
        }
    }

    private boolean isCustomType(ValueHelper helper) {
        try{
            TypeCode tc = helper.get_type();
            int kind = tc.kind().value();
            if (kind == TCKind._tk_value) {
                return (tc.type_modifier() == org.omg.CORBA.VM_CUSTOM.value);
            }
        } catch(BadKind ex) {
            throw wrapper.badKind(ex) ;
        }

        return false;
    }

    // This method is actually called indirectly by
    // read_value(String repositoryId).
    // Therefore, it is not a truly independent read call that handles
    // header information itself.
    public java.io.Serializable read_value(java.io.Serializable value) {

        // Put into valueCache using valueIndirection
        if (valueCache == null)
            valueCache = new CacheTable(orb,false);
        valueCache.put(value, valueIndirection);

        if (value instanceof StreamableValue)
            ((StreamableValue)value)._read(parent);
        else if (value instanceof CustomValue)
            ((CustomValue)value).unmarshal(parent);

        return value;
    }

    public java.io.Serializable read_value(java.lang.String repositoryId) {

        // if (inBlock)
        //    end_block();

        // Read value tag
        int vType = readValueTag();

        if (vType == 0)
            return null; // value is null
        else if (vType == 0xffffffff) { // Indirection tag
            int indirection = read_long() + get_offset() - 4;
            if (valueCache != null && valueCache.containsVal(indirection))
                {
                    java.io.Serializable cachedValue =
                          (java.io.Serializable)valueCache.getKey(indirection);
                    return cachedValue;
                }
            else {
                throw new IndirectionException(indirection);
            }
        }
        else {
            int indirection = get_offset() - 4;

            // end_block();

            boolean saveIsChunked = isChunked;
            isChunked = repIdUtil.isChunkedEncoding(vType);

            java.lang.Object value = null;

            String codebase_URL = null;
            if (repIdUtil.isCodeBasePresent(vType)){
                codebase_URL = read_codebase_URL();
            }

            // Read repository id
            String repositoryIDString
                = readRepositoryIds(vType, null, repositoryId);

            ValueFactory factory =
               Utility.getFactory(null, codebase_URL, orb, repositoryIDString);

            start_block();
            end_flag--;
            if (isChunked)
                chunkedValueNestingLevel--;

            valueIndirection = indirection;  // for callback
            value = factory.read_value(parent);

            handleEndOfValue();
            readEndTag();

            // Put into valueCache
            if (valueCache == null)
                valueCache = new CacheTable(orb,false);
            valueCache.put(value, indirection);

            // allow for possible continuation chunk
            isChunked = saveIsChunked;
            start_block();

            return (java.io.Serializable)value;
        }
    }

    private Class readClass() {

        String codebases = null, classRepId = null;

        if (orb == null ||
            ORBVersionFactory.getFOREIGN().equals(orb.getORBVersion()) ||
            ORBVersionFactory.getNEWER().compareTo(orb.getORBVersion()) <= 0) {

            codebases = (String)read_value(java.lang.String.class);
            classRepId = (String)read_value(java.lang.String.class);
        } else {
            // Pre-Merlin/J2EE 1.3 ORBs wrote the repository ID
            // and codebase strings in the wrong order.
            classRepId = (String)read_value(java.lang.String.class);
            codebases = (String)read_value(java.lang.String.class);
        }

        if (debug) {
            dprint("readClass codebases: "
                   + codebases
                   + " rep Id: "
                   + classRepId);
        }

        Class cl = null;

        RepositoryIdInterface repositoryID
            = repIdStrs.getFromString(classRepId);

        try {
            cl = repositoryID.getClassFromType(codebases);
        } catch(ClassNotFoundException cnfe) {
            throw wrapper.cnfeReadClass( CompletionStatus.COMPLETED_MAYBE,
                cnfe, repositoryID.getClassName() ) ;
        } catch(MalformedURLException me) {
            throw wrapper.malformedUrl( CompletionStatus.COMPLETED_MAYBE,
                me, repositoryID.getClassName(), codebases ) ;
        }

        return cl;
    }

    private java.lang.Object readIDLValueWithHelper(ValueHelper helper, int indirection)
    {
        // look for two-argument static read method
        Method readMethod;
        try {
            Class argTypes[] = {org.omg.CORBA.portable.InputStream.class, helper.get_class()};
            readMethod = helper.getClass().getDeclaredMethod(kReadMethod, argTypes);
        }
        catch(NoSuchMethodException nsme) { // must be boxed value helper
            java.lang.Object result = helper.read_value(parent);
            return result;
        }

        // found two-argument read method, so must be non-boxed value...
        // ...create a blank instance
        java.lang.Object val = null;
        try {
            val = helper.get_class().newInstance();
        } catch(java.lang.InstantiationException ie) {
            throw wrapper.couldNotInstantiateHelper( ie,
                helper.get_class() ) ;
        } catch(IllegalAccessException iae){
            // Value's constructor is protected or private
            //
            // So, use the helper to read the value.
            //
            // NOTE : This means that in this particular case a recursive ref.
            // would fail.
            return helper.read_value(parent);
        }

        // add blank instance to cache table
        if (valueCache == null)
            valueCache = new CacheTable(orb,false);
        valueCache.put(val, indirection);

        // if custom type, call unmarshal method
        if (val instanceof CustomMarshal && isCustomType(helper)) {
            ((CustomMarshal)val).unmarshal(parent);
            return val;
        }

        // call two-argument read method using reflection
        try {
            java.lang.Object args[] = {parent, val};
            readMethod.invoke(helper, args);
            return val;
        } catch(IllegalAccessException iae2) {
            throw wrapper.couldNotInvokeHelperReadMethod( iae2, helper.get_class() ) ;
        } catch(InvocationTargetException ite){
            throw wrapper.couldNotInvokeHelperReadMethod( ite, helper.get_class() ) ;
        }
    }

    private java.lang.Object readBoxedIDLEntity(Class clazz, String codebase)
    {
        Class cls = null ;

        try {
            ClassLoader clazzLoader = (clazz == null ? null : clazz.getClassLoader());

            cls = Utility.loadClassForClass(clazz.getName()+"Helper", codebase,
                                                   clazzLoader, clazz, clazzLoader);
            final Class helperClass = cls ;

            final Class argTypes[] = {org.omg.CORBA.portable.InputStream.class};

            // getDeclaredMethod requires RuntimePermission accessDeclaredMembers
            // if a different class loader is used (even though the javadoc says otherwise)
            Method readMethod = null;
            try {
                readMethod = (Method)AccessController.doPrivileged(
                    new PrivilegedExceptionAction() {
                        public java.lang.Object run() throws NoSuchMethodException {
                            return helperClass.getDeclaredMethod(kReadMethod, argTypes);
                        }
                    }
                );
            } catch (PrivilegedActionException pae) {
                // this gets caught below
                throw (NoSuchMethodException)pae.getException();
            }

            java.lang.Object args[] = {parent};
            return readMethod.invoke(null, args);

        } catch (ClassNotFoundException cnfe) {
            throw wrapper.couldNotInvokeHelperReadMethod( cnfe, cls ) ;
        } catch(NoSuchMethodException nsme) {
            throw wrapper.couldNotInvokeHelperReadMethod( nsme, cls ) ;
        } catch(IllegalAccessException iae) {
            throw wrapper.couldNotInvokeHelperReadMethod( iae, cls ) ;
        } catch(InvocationTargetException ite) {
            throw wrapper.couldNotInvokeHelperReadMethod( ite, cls ) ;
        }
    }

    private java.lang.Object readIDLValue(int indirection, String repId,
                                          Class clazz, String codebase)
    {
        ValueFactory factory ;

        // Always try to find a ValueFactory first, as required by the spec.
        // There are some complications here in the IDL 3.0 mapping (see 1.13.8),
        // but basically we must always be able to override the DefaultFactory
        // or Helper mappings that are also used.  This appears to be the case
        // even in the boxed value cases.  The original code only did the lookup
        // in the case of class implementing either StreamableValue or CustomValue,
        // but abstract valuetypes only implement ValueBase, and really require
        // the use of the repId to find a factory (including the DefaultFactory).
        try {
            // use new-style OBV support (factory object)
            factory = Utility.getFactory(clazz, codebase, orb, repId);
        } catch (MARSHAL marshal) {
            // XXX log marshal at one of the INFO levels

            // Could not get a factory, so try alternatives
            if (!StreamableValue.class.isAssignableFrom(clazz) &&
                !CustomValue.class.isAssignableFrom(clazz) &&
                ValueBase.class.isAssignableFrom(clazz)) {
                // use old-style OBV support (helper object)
                BoxedValueHelper helper = Utility.getHelper(clazz, codebase, repId);
                if (helper instanceof ValueHelper)
                    return readIDLValueWithHelper((ValueHelper)helper, indirection);
                else
                    return helper.read_value(parent);
            } else {
                // must be a boxed IDLEntity, so make a reflective call to the
                // helper's static read method...
                return readBoxedIDLEntity(clazz, codebase);
            }
        }

        // If there was no error in getting the factory, use it.
        valueIndirection = indirection;  // for callback
        return factory.read_value(parent);
    }

    /**
     * End tags are only written for chunked valuetypes.
     *
     * Before Merlin, our ORBs wrote end tags which took into account
     * all enclosing valuetypes.  This was changed by an interop resolution
     * (see details around chunkedValueNestingLevel) to only include
     * enclosing chunked types.
     *
     * ORB versioning and end tag compaction are handled here.
     */
    private void readEndTag() {
        if (isChunked) {

            // Read the end tag
            int anEndTag = read_long();

            // End tags should always be negative, and the outermost
            // enclosing chunked valuetype should have a -1 end tag.
            //
            // handleEndOfValue should have assured that we were
            // at the end tag position!
            if (anEndTag >= 0) {
                throw wrapper.positiveEndTag( CompletionStatus.COMPLETED_MAYBE,
                    new Integer(anEndTag), new Integer( get_offset() - 4 ) ) ;
            }

            // If the ORB is null, or if we're sure we're talking to
            // a foreign ORB, Merlin, or something more recent, we
            // use the updated end tag computation, and are more strenuous
            // about the values.
            if (orb == null ||
                ORBVersionFactory.getFOREIGN().equals(orb.getORBVersion()) ||
                ORBVersionFactory.getNEWER().compareTo(orb.getORBVersion()) <= 0) {

                // If the end tag we read was less than what we were expecting,
                // then the sender must think it's sent more enclosing
                // chunked valuetypes than we have.  Throw an exception.
                if (anEndTag < chunkedValueNestingLevel)
                    throw wrapper.unexpectedEnclosingValuetype(
                        CompletionStatus.COMPLETED_MAYBE, new Integer( anEndTag ),
                        new Integer( chunkedValueNestingLevel ) ) ;

                // If the end tag is bigger than what we expected, but
                // still negative, then the sender has done some end tag
                // compaction.  We back up the stream 4 bytes so that the
                // next time readEndTag is called, it will get down here
                // again.  Even with fragmentation, we'll always be able
                // to do this.
                if (anEndTag != chunkedValueNestingLevel) {
                    bbwi.position(bbwi.position() - 4);
                 }

            } else {

                // When talking to Kestrel or Ladybird, we use our old
                // end tag rules and are less strict.  If the end tag
                // isn't what we expected, we back up, assuming
                // compaction.
                if (anEndTag != end_flag) {
                    bbwi.position(bbwi.position() - 4);
                }
            }

            // This only keeps track of the enclosing chunked
            // valuetypes
            chunkedValueNestingLevel++;
        }

        // This keeps track of all enclosing valuetypes
        end_flag++;
    }

    protected int get_offset() {
        return bbwi.position();
    }

    private void start_block() {

        // if (outerValueDone)
        if (!isChunked)
            return;

        // if called from alignAndCheck, need to reset blockLength
        // to avoid an infinite recursion loop on read_long() call
        blockLength = maxBlockLength;

        blockLength = read_long();

        // Must remember where we began the chunk to calculate how far
        // along we are.  See notes above about chunkBeginPos.

        if (blockLength > 0 && blockLength < maxBlockLength) {
            blockLength += get_offset();  // _REVISIT_ unsafe, should use a Java long

            // inBlock = true;
        } else {

            // System.out.println("start_block snooped a " + Integer.toHexString(blockLength));

            // not a chunk length field
            blockLength = maxBlockLength;

            bbwi.position(bbwi.position() - 4);
        }
    }

    // Makes sure that if we were reading a chunked value, we end up
    // at the right place in the stream, no matter how little the
    // unmarshalling code read.
    //
    // After calling this method, if we are chunking, we should be
    // in position to read the end tag.
    private void handleEndOfValue() {

        // If we're not chunking, we don't have to worry about
        // skipping remaining chunks or finding end tags
        if (!isChunked)
            return;

        // Skip any remaining chunks
        while (blockLength != maxBlockLength) {
            end_block();
            start_block();
        }

        // Now look for the end tag

        // This is a little wasteful since we're reading
        // this long up to 3 times in the worst cases (once
        // in start_block, once here, and once in readEndTag
        //
        // Peek next long
        int nextLong = read_long();
        bbwi.position(bbwi.position() - 4);

        // We did find an end tag, so we're done.  readEndTag
        // should take care of making sure it's the correct
        // end tag, etc.  Remember that since end tags,
        // chunk lengths, and valuetags have non overlapping
        // ranges, we can tell by the value what the longs are.
        if (nextLong < 0)
            return;

        if (nextLong == 0 || nextLong >= maxBlockLength) {

            // A custom marshaled valuetype left extra data
            // on the wire, and that data had another
            // nested value inside of it.  We've just
            // read the value tag or null of that nested value.
            //
            // In an attempt to get by it, we'll try to call
            // read_value() to get the nested value off of
            // the wire.  Afterwards, we must call handleEndOfValue
            // recursively to read any further chunks that the containing
            // valuetype might still have after the nested
            // value.
            read_value();
            handleEndOfValue();
        } else {
            // This probably means that the code to skip chunks has
            // an error, and ended up setting blockLength to something
            // other than maxBlockLength even though we weren't
            // starting a new chunk.
            throw wrapper.couldNotSkipBytes( CompletionStatus.COMPLETED_MAYBE,
                new Integer( nextLong ), new Integer( get_offset() ) ) ;
        }
    }

    private void end_block() {

        // if in a chunk, check for underflow or overflow
        if (blockLength != maxBlockLength) {
            if (blockLength == get_offset()) {
                // Chunk ended correctly
                blockLength = maxBlockLength;
            } else {
                // Skip over anything left by bad unmarshaling code (ex:
                // a buggy custom unmarshaler).  See handleEndOfValue.
                if (blockLength > get_offset()) {
                    skipToOffset(blockLength);
                } else {
                    throw wrapper.badChunkLength( new Integer( blockLength ),
                        new Integer( get_offset() ) ) ;
                }
            }
        }
    }

    private int readValueTag(){
        // outerValueDone = false;
        return read_long();
    }

    public org.omg.CORBA.ORB orb() {
        return orb;
    }

    // ------------ End RMI related methods --------------------------

    public final void read_boolean_array(boolean[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_boolean();
        }
    }

    public final void read_char_array(char[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_char();
        }
    }

    public final void read_wchar_array(char[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_wchar();
        }
    }

    public final void read_short_array(short[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_short();
        }
    }

    public final void read_ushort_array(short[] value, int offset, int length) {
        read_short_array(value, offset, length);
    }

    public final void read_long_array(int[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_long();
        }
    }

    public final void read_ulong_array(int[] value, int offset, int length) {
        read_long_array(value, offset, length);
    }

    public final void read_longlong_array(long[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_longlong();
        }
    }

    public final void read_ulonglong_array(long[] value, int offset, int length) {
        read_longlong_array(value, offset, length);
    }

    public final void read_float_array(float[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_float();
        }
    }

    public final void read_double_array(double[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_double();
        }
    }

    public final void read_any_array(org.omg.CORBA.Any[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_any();
        }
    }

    //--------------------------------------------------------------------//
    // CDRInputStream state management.
    //

    /**
     * Are we at the end of the input stream?
     */
//     public final boolean isAtEnd() {
//      return bbwi.position() == bbwi.buflen;
//     }

//     public int available() throws IOException {
//         return bbwi.buflen - bbwi.position();
//     }

    private String read_repositoryIds() {

        // Read # of repository ids
        int numRepIds = read_long();
        if (numRepIds == 0xffffffff) {
            int indirection = read_long() + get_offset() - 4;
            if (repositoryIdCache != null && repositoryIdCache.containsOrderedVal(indirection))
                return (String)repositoryIdCache.getKey(indirection);
            else
                throw wrapper.unableToLocateRepIdArray( new Integer( indirection ) ) ;
        } else {

            // read first array element and store it as an indirection to the whole array
            int indirection = get_offset();
            String repID = read_repositoryId();
            if (repositoryIdCache == null)
                repositoryIdCache = new CacheTable(orb,false);
            repositoryIdCache.put(repID, indirection);

            // read and ignore the subsequent array elements, but put them in the
            // indirection table in case there are later indirections back to them
            for (int i = 1; i < numRepIds; i++) {
                read_repositoryId();
            }

            return repID;
        }
    }

    private final String read_repositoryId()
    {
        String result = readStringOrIndirection(true);

        if (result == null) { // Indirection
            int indirection = read_long() + get_offset() - 4;

            if (repositoryIdCache != null && repositoryIdCache.containsOrderedVal(indirection))
                return (String)repositoryIdCache.getKey(indirection);
            else
                throw wrapper.badRepIdIndirection( CompletionStatus.COMPLETED_MAYBE,
                    new Integer(bbwi.position()) ) ;
        } else {
            if (repositoryIdCache == null)
                repositoryIdCache = new CacheTable(orb,false);
            repositoryIdCache.put(result, stringIndirection);
        }

        return result ;
    }

    private final String read_codebase_URL()
    {
        String result = readStringOrIndirection(true);

        if (result == null) { // Indirection
            int indirection = read_long() + get_offset() - 4;

            if (codebaseCache != null && codebaseCache.containsVal(indirection))
                return (String)codebaseCache.getKey(indirection);
            else
                throw wrapper.badCodebaseIndirection(
                    CompletionStatus.COMPLETED_MAYBE,
                    new Integer(bbwi.position()) ) ;
        } else {
            if (codebaseCache == null)
                codebaseCache = new CacheTable(orb,false);
            codebaseCache.put(result, stringIndirection);
        }

        return result;
    }

    /* DataInputStream methods */

    public java.lang.Object read_Abstract () {
        return read_abstract_interface();
    }

    public java.io.Serializable read_Value () {
        return read_value();
    }

    public void read_any_array (org.omg.CORBA.AnySeqHolder seq, int offset, int length) {
        read_any_array(seq.value, offset, length);
    }

    public void read_boolean_array (org.omg.CORBA.BooleanSeqHolder seq, int offset, int length) {
        read_boolean_array(seq.value, offset, length);
    }

    public void read_char_array (org.omg.CORBA.CharSeqHolder seq, int offset, int length) {
        read_char_array(seq.value, offset, length);
    }

    public void read_wchar_array (org.omg.CORBA.WCharSeqHolder seq, int offset, int length) {
        read_wchar_array(seq.value, offset, length);
    }

    public void read_octet_array (org.omg.CORBA.OctetSeqHolder seq, int offset, int length) {
        read_octet_array(seq.value, offset, length);
    }

    public void read_short_array (org.omg.CORBA.ShortSeqHolder seq, int offset, int length) {
        read_short_array(seq.value, offset, length);
    }

    public void read_ushort_array (org.omg.CORBA.UShortSeqHolder seq, int offset, int length) {
        read_ushort_array(seq.value, offset, length);
    }

    public void read_long_array (org.omg.CORBA.LongSeqHolder seq, int offset, int length) {
        read_long_array(seq.value, offset, length);
    }

    public void read_ulong_array (org.omg.CORBA.ULongSeqHolder seq, int offset, int length) {
        read_ulong_array(seq.value, offset, length);
    }

    public void read_ulonglong_array (org.omg.CORBA.ULongLongSeqHolder seq, int offset, int length) {
        read_ulonglong_array(seq.value, offset, length);
    }

    public void read_longlong_array (org.omg.CORBA.LongLongSeqHolder seq, int offset, int length) {
        read_longlong_array(seq.value, offset, length);
    }

    public void read_float_array (org.omg.CORBA.FloatSeqHolder seq, int offset, int length) {
        read_float_array(seq.value, offset, length);
    }

    public void read_double_array (org.omg.CORBA.DoubleSeqHolder seq, int offset, int length) {
        read_double_array(seq.value, offset, length);
    }

    public java.math.BigDecimal read_fixed(short digits, short scale) {
        // digits isn't really needed here
        StringBuffer buffer = read_fixed_buffer();
        if (digits != buffer.length())
            throw wrapper.badFixed( new Integer(digits),
                new Integer(buffer.length()) ) ;
        buffer.insert(digits - scale, '.');
        return new BigDecimal(buffer.toString());
    }

    // This method is unable to yield the correct scale.
    public java.math.BigDecimal read_fixed() {
        return new BigDecimal(read_fixed_buffer().toString());
    }

    // Each octet contains (up to) two decimal digits.
    // If the fixed type has an odd number of decimal digits, then the representation
    // begins with the first (most significant) digit.
    // Otherwise, this first half-octet is all zero, and the first digit
    // is in the second half-octet.
    // The sign configuration, in the last half-octet of the representation,
    // is 0xD for negative numbers and 0xC for positive and zero values.
    private StringBuffer read_fixed_buffer() {
        StringBuffer buffer = new StringBuffer(64);
        byte doubleDigit;
        int firstDigit;
        int secondDigit;
        boolean wroteFirstDigit = false;
        boolean more = true;
        while (more) {
            doubleDigit = this.read_octet();
            firstDigit = (int)((doubleDigit & 0xf0) >> 4);
            secondDigit = (int)(doubleDigit & 0x0f);
            if (wroteFirstDigit || firstDigit != 0) {
                buffer.append(Character.forDigit(firstDigit, 10));
                wroteFirstDigit = true;
            }
            if (secondDigit == 12) {
                // positive number or zero
                if ( ! wroteFirstDigit) {
                    // zero
                    return new StringBuffer("0.0");
                } else {
                    // positive number
                    // done
                }
                more = false;
            } else if (secondDigit == 13) {
                // negative number
                buffer.insert(0, '-');
                more = false;
            } else {
                buffer.append(Character.forDigit(secondDigit, 10));
                wroteFirstDigit = true;
            }
        }
        return buffer;
    }

    private final static String _id = "IDL:omg.org/CORBA/DataInputStream:1.0";
    private final static String[] _ids = { _id };

    public String[] _truncatable_ids() {
        if (_ids == null)
            return null;

        return (String[])_ids.clone();
    }

    /* for debugging */

    public void printBuffer() {
        CDRInputStream_1_0.printBuffer(this.bbwi);
    }

    public static void printBuffer(ByteBufferWithInfo bbwi) {

        System.out.println("----- Input Buffer -----");
        System.out.println();
        System.out.println("Current position: " + bbwi.position());
        System.out.println("Total length : " + bbwi.buflen);
        System.out.println();

        try {

            char[] charBuf = new char[16];

            for (int i = 0; i < bbwi.buflen; i += 16) {

                int j = 0;

                // For every 16 bytes, there is one line
                // of output.  First, the hex output of
                // the 16 bytes with each byte separated
                // by a space.
                while (j < 16 && j + i < bbwi.buflen) {
                    int k = bbwi.byteBuffer.get(i + j);
                    if (k < 0)
                        k = 256 + k;
                    String hex = Integer.toHexString(k);
                    if (hex.length() == 1)
                        hex = "0" + hex;
                    System.out.print(hex + " ");
                    j++;
                }

                // Add any extra spaces to align the
                // text column in case we didn't end
                // at 16
                while (j < 16) {
                    System.out.print("   ");
                    j++;
                }

                // Now output the ASCII equivalents.  Non-ASCII
                // characters are shown as periods.
                int x = 0;
                while (x < 16 && x + i < bbwi.buflen) {
                    if (ORBUtility.isPrintable((char)bbwi.byteBuffer.get(i + x)))
                        charBuf[x] = (char)bbwi.byteBuffer.get(i + x);
                    else
                        charBuf[x] = '.';
                    x++;
                }
                System.out.println(new String(charBuf, 0, x));
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }

        System.out.println("------------------------");
    }

    public ByteBuffer getByteBuffer() {
        ByteBuffer result = null;
        if (bbwi != null) {
            result = bbwi.byteBuffer;
        }
        return result;
    }

    public int getBufferLength() {
        return bbwi.buflen;
    }

    public void setBufferLength(int value) {
        bbwi.buflen = value;
        bbwi.byteBuffer.limit(bbwi.buflen);
    }

    public void setByteBufferWithInfo(ByteBufferWithInfo bbwi) {
        this.bbwi = bbwi;
    }

    public void setByteBuffer(ByteBuffer byteBuffer) {
        bbwi.byteBuffer = byteBuffer;
    }

    public int getIndex() {
        return bbwi.position();
    }

    public void setIndex(int value) {
        bbwi.position(value);
    }

    public boolean isLittleEndian() {
        return littleEndian;
    }

    public void orb(org.omg.CORBA.ORB orb) {
        this.orb = (ORB)orb;
    }

    public BufferManagerRead getBufferManager() {
        return bufferManagerRead;
    }

    private void skipToOffset(int offset) {

        // Number of bytes to skip
        int len = offset - get_offset();

        int n = 0;

        while (n < len) {
            int avail;
            int bytes;
            int wanted;

            avail = bbwi.buflen - bbwi.position();
            if (avail <= 0) {
                grow(1, 1);
                avail = bbwi.buflen - bbwi.position();
            }

            wanted = len - n;
            bytes = (wanted < avail) ? wanted : avail;
            bbwi.position(bbwi.position() + bytes);
            n += bytes;
        }
    }


    // Mark and reset -------------------------------------------------

    protected MarkAndResetHandler markAndResetHandler = null;

    protected class StreamMemento
    {
        // These are the fields that may change after marking
        // the stream position, so we need to save them.
        private int blockLength_;
        private int end_flag_;
        private int chunkedValueNestingLevel_;
        private int valueIndirection_;
        private int stringIndirection_;
        private boolean isChunked_;
        private javax.rmi.CORBA.ValueHandler valueHandler_;
        private ByteBufferWithInfo bbwi_;
        private boolean specialNoOptionalDataState_;

        public StreamMemento()
        {
            blockLength_ = blockLength;
            end_flag_ = end_flag;
            chunkedValueNestingLevel_ = chunkedValueNestingLevel;
            valueIndirection_ = valueIndirection;
            stringIndirection_ = stringIndirection;
            isChunked_ = isChunked;
            valueHandler_ = valueHandler;
            specialNoOptionalDataState_ = specialNoOptionalDataState;
            bbwi_ = new ByteBufferWithInfo(bbwi);
        }
    }

    public java.lang.Object createStreamMemento() {
        return new StreamMemento();
    }

    public void restoreInternalState(java.lang.Object streamMemento) {

        StreamMemento mem = (StreamMemento)streamMemento;

        blockLength = mem.blockLength_;
        end_flag = mem.end_flag_;
        chunkedValueNestingLevel = mem.chunkedValueNestingLevel_;
        valueIndirection = mem.valueIndirection_;
        stringIndirection = mem.stringIndirection_;
        isChunked = mem.isChunked_;
        valueHandler = mem.valueHandler_;
        specialNoOptionalDataState = mem.specialNoOptionalDataState_;
        bbwi = mem.bbwi_;
    }

    public int getPosition() {
        return get_offset();
    }

    public void mark(int readlimit) {
        markAndResetHandler.mark(this);
    }

    public void reset() {
        markAndResetHandler.reset();
    }

    // ---------------------------------- end Mark and Reset

    // Provides a hook so subclasses of CDRInputStream can provide
    // a CodeBase.  This ultimately allows us to grab a Connection
    // instance in IIOPInputStream, the only subclass where this
    // is actually used.
    CodeBase getCodeBase() {
        return parent.getCodeBase();
    }

    /**
     * Attempts to find the class described by the given
     * repository ID string and expected type.  The first
     * attempt is to find the class locally, falling back
     * on the URL that came with the value.  The second
     * attempt is to use a URL from the remote CodeBase.
     */
    private Class getClassFromString(String repositoryIDString,
                                     String codebaseURL,
                                     Class expectedType)
    {
        RepositoryIdInterface repositoryID
            = repIdStrs.getFromString(repositoryIDString);

        try {
            try {
                // First try to load the class locally, then use
                // the provided URL (if it isn't null)
                return repositoryID.getClassFromType(expectedType,
                                                     codebaseURL);
            } catch (ClassNotFoundException cnfeOuter) {

                try {

                    if (getCodeBase() == null) {
                        return null; // class cannot be loaded remotely.
                    }

                    // Get a URL from the remote CodeBase and retry
                    codebaseURL = getCodeBase().implementation(repositoryIDString);

                    // Don't bother trying to find it locally again if
                    // we got a null URL
                    if (codebaseURL == null)
                        return null;

                    return repositoryID.getClassFromType(expectedType,
                                                         codebaseURL);
                } catch (ClassNotFoundException cnfeInner) {
                    dprintThrowable(cnfeInner);
                    // Failed to load the class
                    return null;
                }
            }
        } catch (MalformedURLException mue) {
            // Always report a bad URL
            throw wrapper.malformedUrl( CompletionStatus.COMPLETED_MAYBE,
                mue, repositoryIDString, codebaseURL ) ;
        }
    }

    /**
     * Attempts to find the class described by the given
     * repository ID string.  At most, three attempts are made:
     * Try to find it locally, through the provided URL, and
     * finally, via a URL from the remote CodeBase.
     */
    private Class getClassFromString(String repositoryIDString,
                                     String codebaseURL)
    {
        RepositoryIdInterface repositoryID
            = repIdStrs.getFromString(repositoryIDString);

        for (int i = 0; i < 3; i++) {

            try {

                switch (i)
                {
                    case 0:
                        // First try to load the class locally
                        return repositoryID.getClassFromType();
                    case 1:
                        // Try to load the class using the provided
                        // codebase URL (falls out below)
                        break;
                    case 2:
                        // Try to load the class using a URL from the
                        // remote CodeBase
                        codebaseURL = getCodeBase().implementation(repositoryIDString);
                        break;
                }

                // Don't bother if the codebaseURL is null
                if (codebaseURL == null)
                    continue;

                return repositoryID.getClassFromType(codebaseURL);

            } catch(ClassNotFoundException cnfe) {
                // Will ultimately return null if all three
                // attempts fail, but don't do anything here.
            } catch (MalformedURLException mue) {
                throw wrapper.malformedUrl( CompletionStatus.COMPLETED_MAYBE,
                    mue, repositoryIDString, codebaseURL ) ;
            }
        }

        // If we get here, we have failed to load the class
        dprint("getClassFromString failed with rep id "
               + repositoryIDString
               + " and codebase "
               + codebaseURL);

        return null;
    }

    // Utility method used to get chars from bytes
    char[] getConvertedChars(int numBytes,
                             CodeSetConversion.BTCConverter converter) {

        // REVISIT - Look at CodeSetConversion.BTCConverter to see
        //           if it can work with an NIO ByteBuffer. We should
        //           avoid getting the bytes into an array if possible.

        // To be honest, I doubt this saves much real time
        if (bbwi.buflen - bbwi.position() >= numBytes) {
            // If the entire string is in this buffer,
            // just convert directly from the bbwi rather than
            // allocating and copying.
            byte[] tmpBuf;
            if (bbwi.byteBuffer.hasArray())
            {
                tmpBuf = bbwi.byteBuffer.array();
            }
            else
            {
                 tmpBuf = new byte[bbwi.buflen];
                 // Microbenchmarks are showing a loop of ByteBuffer.get(int)
                 // being faster than ByteBuffer.get(byte[], int, int).
                 for (int i = 0; i < bbwi.buflen; i++)
                     tmpBuf[i] = bbwi.byteBuffer.get(i);
            }
            char[] result = converter.getChars(tmpBuf,bbwi.position(),numBytes);

            bbwi.position(bbwi.position() + numBytes);
            return result;
        } else {
            // Stretches across buffers.  Unless we provide an
            // incremental conversion interface, allocate and
            // copy the bytes.
            byte[] bytes = new byte[numBytes];
            read_octet_array(bytes, 0, bytes.length);

            return converter.getChars(bytes, 0, numBytes);
        }
    }

    protected CodeSetConversion.BTCConverter getCharConverter() {
        if (charConverter == null)
            charConverter = parent.createCharBTCConverter();

        return charConverter;
    }

    protected CodeSetConversion.BTCConverter getWCharConverter() {
        if (wcharConverter == null)
            wcharConverter = parent.createWCharBTCConverter();

        return wcharConverter;
    }

    protected void dprintThrowable(Throwable t) {
        if (debug && t != null)
            t.printStackTrace();
    }

    protected void dprint(String msg) {
        if (debug) {
            ORBUtility.dprint(this, msg);
        }
    }

    /**
     * Aligns the current position on the given octet boundary
     * if there are enough bytes available to do so.  Otherwise,
     * it just returns.  This is used for some (but not all)
     * GIOP 1.2 message headers.
     */

    void alignOnBoundary(int octetBoundary) {
        int needed = computeAlignment(bbwi.position(), octetBoundary);

        if (bbwi.position() + needed <= bbwi.buflen)
        {
            bbwi.position(bbwi.position() + needed);
        }
    }

    public void resetCodeSetConverters() {
        charConverter = null;
        wcharConverter = null;
    }

    public void start_value() {
        // Read value tag
        int vType = readValueTag();

        if (vType == 0) {
            // Stream needs to go into a state where it
            // throws standard exception until end_value
            // is called.  This means the sender didn't
            // send any custom data.  If the reader here
            // tries to read more, we need to throw an
            // exception before reading beyond where
            // we're supposed to
            specialNoOptionalDataState = true;

            return;
        }

        if (vType == 0xffffffff) {
            // One should never indirect to a custom wrapper
            throw wrapper.customWrapperIndirection(
                CompletionStatus.COMPLETED_MAYBE);
        }

        if (repIdUtil.isCodeBasePresent(vType)) {
            throw wrapper.customWrapperWithCodebase(
                CompletionStatus.COMPLETED_MAYBE);
        }

        if (repIdUtil.getTypeInfo(vType)
            != RepositoryIdUtility.SINGLE_REP_TYPE_INFO) {
            throw wrapper.customWrapperNotSingleRepid(
                CompletionStatus.COMPLETED_MAYBE);
        }


        // REVISIT - Could verify repository ID even though
        // it isn't used elsewhere
        read_repositoryId();

        // Note: isChunked should be true here.  Should have
        // been set to true in the containing value's read_value
        // method.

        start_block();
        end_flag--;
        chunkedValueNestingLevel--;
    }

    public void end_value() {

        if (specialNoOptionalDataState) {
            specialNoOptionalDataState = false;
            return;
        }

        handleEndOfValue();
        readEndTag();

        // Note that isChunked should still be true here.
        // If the containing valuetype is the highest
        // chunked value, it will get set to false
        // at the end of read_value.

        // allow for possible continuation chunk
        start_block();
    }

    public void close() throws IOException
    {

        // tell BufferManagerRead to release any ByteBuffers
        getBufferManager().close(bbwi);

        // It's possible bbwi.byteBuffer is shared between
        // this InputStream and an OutputStream. Thus, we check
        // if the Input/Output streams are using the same ByteBuffer.
        // If they sharing the same ByteBuffer we need to ensure only
        // one of those ByteBuffers are released to the ByteBufferPool.

        if (bbwi != null && getByteBuffer() != null)
        {
            MessageMediator messageMediator = parent.getMessageMediator();
            if (messageMediator != null)
            {
                CDROutputObject outputObj =
                             (CDROutputObject)messageMediator.getOutputObject();
                if (outputObj != null)
                {
                    if (outputObj.isSharing(getByteBuffer()))
                    {
                        // Set OutputStream's ByteBuffer and bbwi to null
                        // so its ByteBuffer cannot be released to the pool
                        outputObj.setByteBuffer(null);
                        outputObj.setByteBufferWithInfo(null);
                    }
                }
            }

            // release this stream's ByteBuffer to the pool
            ByteBufferPool byteBufferPool = orb.getByteBufferPool();
            if (debug)
            {
                // print address of ByteBuffer being released
                int bbAddress = System.identityHashCode(bbwi.byteBuffer);
                StringBuffer sb = new StringBuffer(80);
                sb.append(".close - releasing ByteBuffer id (");
                sb.append(bbAddress).append(") to ByteBufferPool.");
                String msg = sb.toString();
                dprint(msg);
            }
            byteBufferPool.releaseByteBuffer(bbwi.byteBuffer);
            bbwi.byteBuffer = null;
            bbwi = null;
        }
    }
}
