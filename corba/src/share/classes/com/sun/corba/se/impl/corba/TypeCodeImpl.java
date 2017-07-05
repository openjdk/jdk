/*
 * Copyright (c) 1996, 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.corba;

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

import org.omg.CORBA.TypeCode ;
import org.omg.CORBA.StructMember ;
import org.omg.CORBA.UnionMember ;
import org.omg.CORBA.ValueMember ;
import org.omg.CORBA.TCKind ;
import org.omg.CORBA.Any ;
import org.omg.CORBA.Principal ;
import org.omg.CORBA.BAD_TYPECODE ;
import org.omg.CORBA.BAD_PARAM ;
import org.omg.CORBA.BAD_OPERATION ;
import org.omg.CORBA.INTERNAL ;
import org.omg.CORBA.MARSHAL ;
import org.omg.CORBA.TypeCodePackage.BadKind ;
import org.omg.CORBA_2_3.portable.InputStream;
import org.omg.CORBA_2_3.portable.OutputStream;

import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.logging.CORBALogDomains;

import com.sun.corba.se.impl.encoding.OSFCodeSetRegistry;
import com.sun.corba.se.impl.encoding.MarshalInputStream;
import com.sun.corba.se.impl.encoding.CodeSetConversion;
import com.sun.corba.se.impl.encoding.CDRInputStream;
import com.sun.corba.se.impl.encoding.CDROutputStream;
import com.sun.corba.se.impl.encoding.TypeCodeInputStream;
import com.sun.corba.se.impl.encoding.TypeCodeOutputStream;
import com.sun.corba.se.impl.encoding.TypeCodeReader;
import com.sun.corba.se.impl.encoding.WrapperInputStream;

import com.sun.corba.se.impl.logging.ORBUtilSystemException;

// no chance of subclasses, so no problems with runtime helper lookup
public final class TypeCodeImpl extends TypeCode
{
    //static final boolean debug = false;

    // the indirection TCKind, needed for recursive typecodes.
    protected static final int tk_indirect = 0xFFFFFFFF;

    // typecode encodings have three different categories that determine
    // how the encoding should be done.

    private static final int EMPTY = 0; // no parameters
    private static final int SIMPLE = 1;        // simple parameters.
    private static final int COMPLEX = 2; // complex parameters. need to
    // use CDR encapsulation for
    // parameters

    // a table storing the encoding category for the various typecodes.

    private static final int typeTable[] = {
        EMPTY,  // tk_null
        EMPTY,  // tk_void
        EMPTY,  // tk_short
        EMPTY,  // tk_long
        EMPTY,  // tk_ushort
        EMPTY,  // tk_ulong
        EMPTY,  // tk_float
        EMPTY,  // tk_double
        EMPTY,  // tk_boolean
        EMPTY,  // tk_char
        EMPTY,  // tk_octet
        EMPTY,  // tk_any
        EMPTY,  // tk_typecode
        EMPTY,  // tk_principal
        COMPLEX,        // tk_objref
        COMPLEX,        // tk_struct
        COMPLEX,        // tk_union
        COMPLEX,        // tk_enum
        SIMPLE, // tk_string
        COMPLEX,        // tk_sequence
        COMPLEX,        // tk_array
        COMPLEX,        // tk_alias
        COMPLEX,        // tk_except
        EMPTY,  // tk_longlong
        EMPTY,  // tk_ulonglong
        EMPTY,  // tk_longdouble
        EMPTY,  // tk_wchar
        SIMPLE, // tk_wstring
        SIMPLE, // tk_fixed
        COMPLEX,        // tk_value
        COMPLEX,        // tk_value_box
        COMPLEX,        // tk_native
        COMPLEX // tk_abstract_interface
    };

    // Maps TCKind values to names
    // This is also used in AnyImpl.
    static final String[] kindNames = {
        "null",
        "void",
        "short",
        "long",
        "ushort",
        "ulong",
        "float",
        "double",
        "boolean",
        "char",
        "octet",
        "any",
        "typecode",
        "principal",
        "objref",
        "struct",
        "union",
        "enum",
        "string",
        "sequence",
        "array",
        "alias",
        "exception",
        "longlong",
        "ulonglong",
        "longdouble",
        "wchar",
        "wstring",
        "fixed",
        "value",
        "valueBox",
        "native",
        "abstractInterface"
    };

    private int                 _kind           = 0;    // the typecode kind

    // data members for representing the various kinds of typecodes.
    private String          _id             = "";   // the typecode repository id
    private String          _name           = "";   // the typecode name
    private int             _memberCount    = 0;    // member count
    private String          _memberNames[]  = null; // names of members
    private TypeCodeImpl    _memberTypes[]  = null; // types of members
    private AnyImpl         _unionLabels[]  = null; // values of union labels
    private TypeCodeImpl    _discriminator  = null; // union discriminator type
    private int             _defaultIndex   = -1;   // union default index
    private int             _length         = 0;    // string/seq/array length
    private TypeCodeImpl    _contentType    = null; // seq/array/alias type
    // fixed
    private short           _digits         = 0;
    private short           _scale          = 0;
    // value type
    // _REVISIT_ We might want to keep references to the ValueMember classes
    // passed in at initialization instead of copying the relevant data.
    // Is the data immutable? What about StructMember, UnionMember etc.?
    private short           _type_modifier  = -1;   // VM_NONE, VM_CUSTOM,
    // VM_ABSTRACT, VM_TRUNCATABLE
    private TypeCodeImpl    _concrete_base  = null; // concrete base type
    private short           _memberAccess[] = null; // visibility of ValueMember
    // recursive sequence support
    private TypeCodeImpl    _parent         = null; // the enclosing type code
    private int             _parentOffset   = 0;    // the level of enclosure
    // recursive type code support
    private TypeCodeImpl    _indirectType   = null;

    // caches the byte buffer written in write_value for quick remarshaling...
    private byte[] outBuffer                = null;
    // ... but only if caching is enabled
    private boolean cachingEnabled          = false;

    // the ORB instance: may be instanceof ORBSingleton or ORB
    private ORB _orb;
    private ORBUtilSystemException wrapper ;

    ///////////////////////////////////////////////////////////////////////////
    // Constructors...

    public TypeCodeImpl(ORB orb)
    {
        // initialized to tk_null
        _orb = orb;
        wrapper = ORBUtilSystemException.get(
            (com.sun.corba.se.spi.orb.ORB)orb, CORBALogDomains.RPC_PRESENTATION ) ;
    }

    public TypeCodeImpl(ORB orb, TypeCode tc)
    // to handle conversion of "remote" typecodes into "native" style.
    // also see the 'convertToNative(ORB orb, TypeCode tc)' function
    {
        this(orb) ;

        // This is a protection against misuse of this constructor.
        // Should only be used if tc is not an instance of this class!
        // Otherwise we run into problems with recursive/indirect type codes.
        // _REVISIT_ We should make this constructor private
        if (tc instanceof TypeCodeImpl) {
            TypeCodeImpl tci = (TypeCodeImpl)tc;
            if (tci._kind == tk_indirect)
                throw wrapper.badRemoteTypecode() ;
            if (tci._kind == TCKind._tk_sequence && tci._contentType == null)
                throw wrapper.badRemoteTypecode() ;
        }

        // set up kind
        _kind   = tc.kind().value();

        try {
            // set up parameters
            switch (_kind) {
            case TCKind._tk_value:
                _type_modifier = tc.type_modifier();
                // concrete base may be null
                TypeCode tccb = tc.concrete_base_type();
                if (tccb != null) {
                    _concrete_base = convertToNative(_orb, tccb);
                } else {
                    _concrete_base = null;
                }
                //_memberAccess = tc._memberAccess;
                // Need to reconstruct _memberAccess using member_count() and member_visibility()
                _memberAccess = new short[tc.member_count()];
                for (int i=0; i < tc.member_count(); i++) {
                    _memberAccess[i] = tc.member_visibility(i);
                }
            case TCKind._tk_except:
            case TCKind._tk_struct:
            case TCKind._tk_union:
                // set up member types
                _memberTypes = new TypeCodeImpl[tc.member_count()];
                for (int i=0; i < tc.member_count(); i++) {
                    _memberTypes[i] = convertToNative(_orb, tc.member_type(i));
                    _memberTypes[i].setParent(this);
                }
            case TCKind._tk_enum:
                // set up member names
                _memberNames = new String[tc.member_count()];
                for (int i=0; i < tc.member_count(); i++) {
                    _memberNames[i] = tc.member_name(i);
                }
                // set up member count
                _memberCount = tc.member_count();
            case TCKind._tk_objref:
            case TCKind._tk_alias:
            case TCKind._tk_value_box:
            case TCKind._tk_native:
            case TCKind._tk_abstract_interface:
                setId(tc.id());
                _name = tc.name();
                break;
            }

            // set up stuff for unions
            switch (_kind) {
            case TCKind._tk_union:
                _discriminator = convertToNative(_orb, tc.discriminator_type());
                _defaultIndex  = tc.default_index();
                _unionLabels = new AnyImpl[_memberCount];
                for (int i=0; i < _memberCount; i++)
                    _unionLabels[i] = new AnyImpl(_orb, tc.member_label(i));
                break;
            }

            // set up length
            switch (_kind) {
            case TCKind._tk_string:
            case TCKind._tk_wstring:
            case TCKind._tk_sequence:
            case TCKind._tk_array:
                _length = tc.length();
            }

            // set up content type
            switch (_kind) {
            case TCKind._tk_sequence:
            case TCKind._tk_array:
            case TCKind._tk_alias:
            case TCKind._tk_value_box:
                _contentType = convertToNative(_orb, tc.content_type());
            }
        } catch (org.omg.CORBA.TypeCodePackage.Bounds e) {} catch (BadKind e) {}
        // dont have to worry about these since code ensures we dont step
        // out of bounds.
    }

    public TypeCodeImpl(ORB orb, int creationKind)
    // for primitive types
    {
        this(orb);

        // private API. dont bother checking that
        //     (creationKind < 0 || creationKind > typeTable.length)

        _kind = creationKind;

        // do initialization for special cases
        switch (_kind) {
        case TCKind._tk_objref:
            {
                // this is being used to create typecode for CORBA::Object
                setId("IDL:omg.org/CORBA/Object:1.0");
                _name = "Object";
                break;
            }

        case TCKind._tk_string:
        case TCKind._tk_wstring:
            {
                _length =0;
                break;
            }

        case TCKind._tk_value:
            {
                _concrete_base = null;
                break;
            }
        }
    }

    public TypeCodeImpl(ORB orb,
                        int creationKind,
                        String id,
                        String name,
                        StructMember[] members)
                        // for structs and exceptions
    {
        this(orb);

        if ((creationKind == TCKind._tk_struct) || (creationKind == TCKind._tk_except)) {
            _kind               = creationKind;
            setId(id);
            _name               = name;
            _memberCount        = members.length;

            _memberNames = new String[_memberCount];
            _memberTypes = new TypeCodeImpl[_memberCount];

            for (int i = 0 ; i < _memberCount ; i++) {
                _memberNames[i] = members[i].name;
                _memberTypes[i] = convertToNative(_orb, members[i].type);
                _memberTypes[i].setParent(this);
            }
        } // else initializes to null
    }

    public TypeCodeImpl(ORB orb,
                        int creationKind,
                        String id,
                        String name,
                        TypeCode discriminator_type,
                        UnionMember[] members)
                        // for unions
    {
        this(orb) ;

        if (creationKind == TCKind._tk_union) {
            _kind               = creationKind;
            setId(id);
            _name               = name;
            _memberCount        = members.length;
            _discriminator      = convertToNative(_orb, discriminator_type);

            _memberNames = new String[_memberCount];
            _memberTypes = new TypeCodeImpl[_memberCount];
            _unionLabels = new AnyImpl[_memberCount];

            for (int i = 0 ; i < _memberCount ; i++) {
                _memberNames[i] = members[i].name;
                _memberTypes[i] = convertToNative(_orb, members[i].type);
                _memberTypes[i].setParent(this);
                _unionLabels[i] = new AnyImpl(_orb, members[i].label);
                // check whether this is the default branch.
                if (_unionLabels[i].type().kind() == TCKind.tk_octet) {
                    if (_unionLabels[i].extract_octet() == (byte)0) {
                        _defaultIndex = i;
                    }
                }
            }
        } // else initializes to null
    }

    public TypeCodeImpl(ORB orb,
                        int creationKind,
                        String id,
                        String name,
                        short type_modifier,
                        TypeCode concrete_base,
                        ValueMember[] members)
                        // for value types
    {
        this(orb) ;

        if (creationKind == TCKind._tk_value) {
            _kind               = creationKind;
            setId(id);
            _name               = name;
            _type_modifier      = type_modifier;
            if (concrete_base != null) {
                _concrete_base = convertToNative(_orb, concrete_base);
            }
            _memberCount        = members.length;

            _memberNames = new String[_memberCount];
            _memberTypes = new TypeCodeImpl[_memberCount];
            _memberAccess = new short[_memberCount];

            for (int i = 0 ; i < _memberCount ; i++) {
                _memberNames[i] = members[i].name;
                _memberTypes[i] = convertToNative(_orb, members[i].type);
                _memberTypes[i].setParent(this);
                _memberAccess[i] = members[i].access;
            }
        } // else initializes to null
    }


    public TypeCodeImpl(ORB orb,
                        int creationKind,
                        String id,
                        String name,
                        String[] members)
                        // for enums
    {
        this(orb) ;

        if (creationKind == TCKind._tk_enum)
            {
                _kind           = creationKind;
                setId(id);
                _name           = name;
                _memberCount    = members.length;

                _memberNames = new String[_memberCount];

                for (int i = 0 ; i < _memberCount ; i++)
                    _memberNames[i] = members[i];
            } // else initializes to null
    }

    public TypeCodeImpl(ORB orb,
                        int creationKind,
                        String id,
                        String name,
                        TypeCode original_type)
                        // for aliases and value boxes
    {
        this(orb) ;

        if ( creationKind == TCKind._tk_alias || creationKind == TCKind._tk_value_box )
            {
                _kind           = creationKind;
                setId(id);
                _name           = name;
                _contentType    = convertToNative(_orb, original_type);
            }
        // else initializes to null

    }

    public TypeCodeImpl(ORB orb,
                        int creationKind,
                        String id,
                        String name)
    {
        this(orb) ;

        if (creationKind == TCKind._tk_objref ||
            creationKind == TCKind._tk_native ||
            creationKind == TCKind._tk_abstract_interface)
            {
                _kind           = creationKind;
                setId(id);
                _name           = name;
            } // else initializes to null
    }


    public TypeCodeImpl(ORB orb,
                        int creationKind,
                        int bound)
                        // for strings
    {
        this(orb) ;

        if (bound < 0)
            throw wrapper.negativeBounds() ;

        if ((creationKind == TCKind._tk_string) || (creationKind == TCKind._tk_wstring)) {
            _kind               = creationKind;
            _length             = bound;
        } // else initializes to null
    }

    public TypeCodeImpl(ORB orb,
                        int creationKind,
                        int bound,
                        TypeCode element_type)
                        // for sequences and arrays
    {
        this(orb) ;

        if ( creationKind == TCKind._tk_sequence || creationKind == TCKind._tk_array ) {
            _kind               = creationKind;
            _length             = bound;
            _contentType        = convertToNative(_orb, element_type);
        } // else initializes to null
    }

    public TypeCodeImpl(ORB orb,
                        int creationKind,
                        int bound,
                        int offset)
                        // for recursive sequences
    {
        this(orb) ;

        if (creationKind == TCKind._tk_sequence) {
            _kind               = creationKind;
            _length             = bound;
            _parentOffset       = offset;
        } // else initializes to null
    }

    public TypeCodeImpl(ORB orb,
                        String id)
                        // for recursive type codes
    {
        this(orb) ;

        _kind   = tk_indirect;
        // This is the type code of the type we stand in for, not our own.
        _id             = id;
        // Try to resolve it now. May return null in which case
        // we try again later (see indirectType()).
        tryIndirectType();
    }

    public TypeCodeImpl(ORB orb,
                        int creationKind,
                        short digits,
                        short scale)
                        // for fixed
    {
        this(orb) ;

        //if (digits < 1 || digits > 31)
        //throw new BAD_TYPECODE();

        if (creationKind == TCKind._tk_fixed) {
            _kind               = creationKind;
            _digits             = digits;
            _scale              = scale;
        } // else initializes to null
    }

    ///////////////////////////////////////////////////////////////////////////
    // Other creation functions...

    // Optimization:
    // If we checked for and returned constant primitive typecodes
    // here we could reduce object creation and also enable more
    // efficient typecode comparisons for primitive typecodes.
    //
    protected static TypeCodeImpl convertToNative(ORB orb,
                                                  TypeCode tc)
    {
        if (tc instanceof TypeCodeImpl)
            return (TypeCodeImpl) tc;
        else
            return new TypeCodeImpl(orb, tc);
    }

    public static CDROutputStream newOutputStream(ORB orb) {
        TypeCodeOutputStream tcos = new TypeCodeOutputStream((ORB)orb);
        //if (debug) System.out.println("Created TypeCodeOutputStream " + tcos +
        // " with no parent");
        return tcos;
    }

    // Support for indirect/recursive type codes

    private TypeCodeImpl indirectType() {
        _indirectType = tryIndirectType();
        if (_indirectType == null) {
            // Nothing we can do about that.
            throw wrapper.unresolvedRecursiveTypecode() ;
        }
        return _indirectType;
    }

    private TypeCodeImpl tryIndirectType() {
        // Assert that _kind == tk_indirect
        if (_indirectType != null)
            return _indirectType;

        setIndirectType(_orb.getTypeCode(_id));

        return _indirectType;
    }

    private void setIndirectType(TypeCodeImpl newType) {
        _indirectType = newType;
        if (_indirectType != null) {
            try {
                _id = _indirectType.id();
            } catch (BadKind e) {
                // can't happen
                throw wrapper.badkindCannotOccur() ;
            }
        }
    }

    private void setId(String newID) {
        _id = newID;
        if (_orb instanceof TypeCodeFactory) {
            ((TypeCodeFactory)_orb).setTypeCode(_id, this);
        }
        // check whether return value != this which would indicate that the
        // repository id isn't unique.
    }

    private void setParent(TypeCodeImpl parent) {
        _parent = parent;
    }

    private TypeCodeImpl getParentAtLevel(int level) {
        if (level == 0)
            return this;

        if (_parent == null)
            throw wrapper.unresolvedRecursiveTypecode() ;

        return _parent.getParentAtLevel(level - 1);
    }

    private TypeCodeImpl lazy_content_type() {
        if (_contentType == null) {
            if (_kind == TCKind._tk_sequence && _parentOffset > 0 && _parent != null) {
                // This is an unresolved recursive sequence tc.
                // Try to resolve it now if the hierarchy is complete.
                TypeCodeImpl realParent = getParentAtLevel(_parentOffset);
                if (realParent != null && realParent._id != null) {
                    // Create a recursive type code object as the content type.
                    // This is when the recursive sequence typecode morphes
                    // into a sequence typecode containing a recursive typecode.
                    _contentType = new TypeCodeImpl((ORB)_orb, realParent._id);
                }
            }
        }
        return _contentType;
    }

    // Other private functions

    private TypeCode realType(TypeCode aType) {
        TypeCode realType = aType;
        try {
            // Note: Indirect types are handled in kind() method
            while (realType.kind().value() == TCKind._tk_alias) {
                realType = realType.content_type();
            }
        } catch (BadKind bad) {
            // impossible
            throw wrapper.badkindCannotOccur() ;
        }
        return realType;
    }

    ///////////////////////////////////////////////////////////////////////////
    // TypeCode operations

    public final boolean equal(TypeCode tc)
    // _REVISIT_ for all optional names/ids, we might want to check that
    // they are equal in case both are non-nil.
    {
        if (tc == this)
            return true;

        try {

            if (_kind == tk_indirect) {
                //return indirectType().equal(tc);
                if (_id != null && tc.id() != null)
                    return _id.equals(tc.id());
                return (_id == null && tc.id() == null);
            }

            // make sure kinds are identical.
            if (_kind != tc.kind().value()) {
                return false;
            }

            switch (typeTable[_kind]) {
            case EMPTY:
                // no parameters to check.
                return true;

            case SIMPLE:
                switch (_kind) {
                case TCKind._tk_string:
                case TCKind._tk_wstring:
                    // check for bound.
                    return (_length == tc.length());

                case TCKind._tk_fixed:
                    return (_digits == tc.fixed_digits() && _scale == tc.fixed_scale());
                default:
                    return false;
                }

            case COMPLEX:

                switch(_kind) {

                case TCKind._tk_objref:
                    {
                        // check for logical id.
                        if (_id.compareTo(tc.id()) == 0) {
                            return true;
                        }

                        if (_id.compareTo(
                            (_orb.get_primitive_tc(_kind)).id()) == 0)
                        {
                            return true;
                        }

                        if (tc.id().compareTo(
                            (_orb.get_primitive_tc(_kind)).id()) == 0)
                        {
                            return true;
                        }

                        return false;
                    }

                case TCKind._tk_native:
                case TCKind._tk_abstract_interface:
                    {
                        // check for logical id.
                        if (_id.compareTo(tc.id()) != 0) {
                            return false;

                        }
                        // ignore name since its optional.
                        return true;
                    }

                case TCKind._tk_struct:
                case TCKind._tk_except:
                    {
                        // check for member count
                        if (_memberCount != tc.member_count())
                            return false;
                        // check for repository id
                        if (_id.compareTo(tc.id()) != 0)
                            return false;
                        // check for member types.
                        for (int i = 0 ; i < _memberCount ; i++)
                            if (! _memberTypes[i].equal(tc.member_type(i)))
                                return false;
                        // ignore id and names since those are optional.
                        return true;
                    }

                case TCKind._tk_union:
                    {
                        // check for member count
                        if (_memberCount != tc.member_count())
                            return false;
                        // check for repository id
                        if (_id.compareTo(tc.id()) != 0)
                            return false;
                        // check for default index
                        if (_defaultIndex != tc.default_index())
                            return false;
                        // check for discriminator type
                        if (!_discriminator.equal(tc.discriminator_type()))
                            return false;
                        // check for label types and values
                        for (int i = 0 ; i < _memberCount ; i++)
                            if (! _unionLabels[i].equal(tc.member_label(i)))
                                return false;
                        // check for branch types
                        for (int i = 0 ; i < _memberCount ; i++)
                            if (! _memberTypes[i].equal(tc.member_type(i)))
                                return false;
                        // ignore id and names since those are optional.
                        return true;
                    }

                case TCKind._tk_enum:
                    {
                        // check for repository id
                        if (_id.compareTo(tc.id()) != 0)
                            return false;
                        // check member count
                        if (_memberCount != tc.member_count())
                            return false;
                        // ignore names since those are optional.
                        return true;
                    }

                case TCKind._tk_sequence:
                case TCKind._tk_array:
                    {
                        // check bound/length
                        if (_length != tc.length()) {
                            return false;
                        }
                        // check content type
                        if (! lazy_content_type().equal(tc.content_type())) {
                            return false;
                        }
                        // ignore id and name since those are optional.
                        return true;
                    }

                case TCKind._tk_value:
                    {
                        // check for member count
                        if (_memberCount != tc.member_count())
                            return false;
                        // check for repository id
                        if (_id.compareTo(tc.id()) != 0)
                            return false;
                        // check for member types.
                        for (int i = 0 ; i < _memberCount ; i++)
                            if (_memberAccess[i] != tc.member_visibility(i) ||
                                ! _memberTypes[i].equal(tc.member_type(i)))
                                return false;
                        if (_type_modifier == tc.type_modifier())
                            return false;
                        // concrete_base may be null
                        TypeCode tccb = tc.concrete_base_type();
                        if ((_concrete_base == null && tccb != null) ||
                            (_concrete_base != null && tccb == null) ||
                            ! _concrete_base.equal(tccb))
                        {
                            return false;
                        }
                        // ignore id and names since those are optional.
                        return true;
                    }

                case TCKind._tk_alias:
                case TCKind._tk_value_box:
                    {
                        // check for repository id
                        if (_id.compareTo(tc.id()) != 0) {
                            return false;
                        }
                        // check for equality with the true type
                        return _contentType.equal(tc.content_type());
                    }
                }
            }
        } catch (org.omg.CORBA.TypeCodePackage.Bounds e) {} catch (BadKind e) {}
        // dont have to worry about these since the code ensures these dont
        // arise.
        return false;
    }

    /**
    * The equivalent operation is used by the ORB when determining type equivalence
    * for values stored in an IDL any.
    */
    public boolean equivalent(TypeCode tc) {
        if (tc == this) {
            return true;
        }

        // If the result of the kind operation on either TypeCode is tk_alias, recursively
        // replace the TypeCode with the result of calling content_type, until the kind
        // is no longer tk_alias.
        // Note: Always resolve indirect types first!
        TypeCode myRealType = (_kind == tk_indirect ? indirectType() : this);
        myRealType = realType(myRealType);
        TypeCode otherRealType = realType(tc);

        // If results of the kind operation on each typecode differ,
        // equivalent returns false.
        if (myRealType.kind().value() != otherRealType.kind().value()) {
            return false;
        }

        String myID = null;
        String otherID = null;
        try {
            myID = this.id();
            otherID = tc.id();
            // At this point the id operation is valid for both TypeCodes.

            // Return true if the results of id for both TypeCodes are non-empty strings
            // and both strings are equal.
            // If both ids are non-empty but are not equal, then equivalent returns FALSE.
            if (myID != null && otherID != null) {
                return (myID.equals(otherID));
            }
        } catch (BadKind e) {
            // id operation is not valid for either or both TypeCodes
        }

        // If either or both id is an empty string, or the TypeCode kind does not support
        // the id operation, perform a structural comparison of the TypeCodes.

        int myKind = myRealType.kind().value();
        try {
            if (myKind == TCKind._tk_struct ||
                myKind == TCKind._tk_union ||
                myKind == TCKind._tk_enum ||
                myKind == TCKind._tk_except ||
                myKind == TCKind._tk_value)
            {
                if (myRealType.member_count() != otherRealType.member_count())
                    return false;
            }
            if (myKind == TCKind._tk_union)
            {
                if (myRealType.default_index() != otherRealType.default_index())
                    return false;
            }
            if (myKind == TCKind._tk_string ||
                myKind == TCKind._tk_wstring ||
                myKind == TCKind._tk_sequence ||
                myKind == TCKind._tk_array)
            {
                if (myRealType.length() != otherRealType.length())
                    return false;
            }
            if (myKind == TCKind._tk_fixed)
            {
                if (myRealType.fixed_digits() != otherRealType.fixed_digits() ||
                    myRealType.fixed_scale() != otherRealType.fixed_scale())
                    return false;
            }
            if (myKind == TCKind._tk_union)
            {
                for (int i=0; i<myRealType.member_count(); i++) {
                    if (myRealType.member_label(i) != otherRealType.member_label(i))
                        return false;
                }
                if ( ! myRealType.discriminator_type().equivalent(
                    otherRealType.discriminator_type()))
                    return false;
            }
            if (myKind == TCKind._tk_alias ||
                myKind == TCKind._tk_value_box ||
                myKind == TCKind._tk_sequence ||
                myKind == TCKind._tk_array)
            {
                if ( ! myRealType.content_type().equivalent(otherRealType.content_type()))
                    return false;
            }
            if (myKind == TCKind._tk_struct ||
                myKind == TCKind._tk_union ||
                myKind == TCKind._tk_except ||
                myKind == TCKind._tk_value)
            {
                for (int i=0; i<myRealType.member_count(); i++) {
                    if ( ! myRealType.member_type(i).equivalent(
                        otherRealType.member_type(i)))
                        return false;
                }
            }
        } catch (BadKind e) {
            // impossible if we checked correctly above
            throw wrapper.badkindCannotOccur() ;
        } catch (org.omg.CORBA.TypeCodePackage.Bounds e) {
            // impossible if we checked correctly above
            throw wrapper.boundsCannotOccur() ;
        }

        // Structural comparison succeeded!
        return true;
    }

    public TypeCode get_compact_typecode() {
        // _REVISIT_ It isn't clear whether this method should operate on this or a copy.
        // For now just return this unmodified because the name and member_name fields
        // aren't used for comparison anyways.
        return this;
    }

    public TCKind kind()
    {
        if (_kind == tk_indirect)
            return indirectType().kind();
        return TCKind.from_int(_kind);
    }

    public boolean is_recursive()
    {
        // Recursive is the only form of indirect type codes right now.
        // Indirection can also be used for repeated type codes.
        return (_kind == tk_indirect);
    }

    public String id()
        throws BadKind
    {
        switch (_kind) {
        case tk_indirect:
            //return indirectType().id(); // same as _id
        case TCKind._tk_except:
        case TCKind._tk_objref:
        case TCKind._tk_struct:
        case TCKind._tk_union:
        case TCKind._tk_enum:
        case TCKind._tk_alias:
        case TCKind._tk_value:
        case TCKind._tk_value_box:
        case TCKind._tk_native:
        case TCKind._tk_abstract_interface:
            // exception and objref typecodes must have a repository id.
            // structs, unions, enums, and aliases may or may not.
            return _id;
        default:
            // all other typecodes throw the BadKind exception.
            throw new BadKind();
        }
    }

    public String name()
        throws BadKind
    {
        switch (_kind) {
        case tk_indirect:
            return indirectType().name();
        case TCKind._tk_except:
        case TCKind._tk_objref:
        case TCKind._tk_struct:
        case TCKind._tk_union:
        case TCKind._tk_enum:
        case TCKind._tk_alias:
        case TCKind._tk_value:
        case TCKind._tk_value_box:
        case TCKind._tk_native:
        case TCKind._tk_abstract_interface:
            return _name;
        default:
            throw new BadKind();
        }
    }

    public int member_count()
        throws BadKind
    {
        switch (_kind) {
        case tk_indirect:
            return indirectType().member_count();
        case TCKind._tk_except:
        case TCKind._tk_struct:
        case TCKind._tk_union:
        case TCKind._tk_enum:
        case TCKind._tk_value:
            return _memberCount;
        default:
            throw new BadKind();
        }
    }

    public String member_name(int index)
        throws BadKind, org.omg.CORBA.TypeCodePackage.Bounds
    {
        switch (_kind) {
        case tk_indirect:
            return indirectType().member_name(index);
        case TCKind._tk_except:
        case TCKind._tk_struct:
        case TCKind._tk_union:
        case TCKind._tk_enum:
        case TCKind._tk_value:
            try {
                return _memberNames[index];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new org.omg.CORBA.TypeCodePackage.Bounds();
            }
        default:
            throw new BadKind();
        }
    }

    public TypeCode member_type(int index)
        throws BadKind, org.omg.CORBA.TypeCodePackage.Bounds
    {
        switch (_kind) {
        case tk_indirect:
            return indirectType().member_type(index);
        case TCKind._tk_except:
        case TCKind._tk_struct:
        case TCKind._tk_union:
        case TCKind._tk_value:
            try {
                return _memberTypes[index];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new org.omg.CORBA.TypeCodePackage.Bounds();
            }
        default:
            throw new BadKind();
        }
    }

    public Any member_label(int index)
        throws BadKind, org.omg.CORBA.TypeCodePackage.Bounds
    {
        switch (_kind) {
        case tk_indirect:
            return indirectType().member_label(index);
        case TCKind._tk_union:
            try {
                // _REVISIT_ Why create a new Any for this?
                return new AnyImpl(_orb, _unionLabels[index]);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new org.omg.CORBA.TypeCodePackage.Bounds();
            }
        default:
            throw new BadKind();
        }
    }

    public TypeCode discriminator_type()
        throws BadKind
    {
        switch (_kind) {
        case tk_indirect:
            return indirectType().discriminator_type();
        case TCKind._tk_union:
            return _discriminator;
        default:
            throw new BadKind();
        }
    }

    public int default_index()
        throws BadKind
    {
        switch (_kind) {
        case tk_indirect:
            return indirectType().default_index();
        case TCKind._tk_union:
            return _defaultIndex;
        default:
            throw new BadKind();
        }
    }

    public int length()
        throws BadKind
    {
        switch (_kind) {
        case tk_indirect:
            return indirectType().length();
        case TCKind._tk_string:
        case TCKind._tk_wstring:
        case TCKind._tk_sequence:
        case TCKind._tk_array:
            return _length;
        default:
            throw new BadKind();
        }
    }

    public TypeCode content_type()
        throws BadKind
    {
        switch (_kind) {
        case tk_indirect:
            return indirectType().content_type();
        case TCKind._tk_sequence:
            return lazy_content_type();
        case TCKind._tk_array:
        case TCKind._tk_alias:
        case TCKind._tk_value_box:
            return _contentType;
        default:
            throw new BadKind();
        }
    }

    public short fixed_digits() throws BadKind {
        switch (_kind) {
        case TCKind._tk_fixed:
            return _digits;
        default:
            throw new BadKind();
        }
    }

    public short fixed_scale() throws BadKind {
        switch (_kind) {
        case TCKind._tk_fixed:
            return _scale;
        default:
            throw new BadKind();
        }
    }

    public short member_visibility(int index) throws BadKind,
        org.omg.CORBA.TypeCodePackage.Bounds {
        switch (_kind) {
        case tk_indirect:
            return indirectType().member_visibility(index);
        case TCKind._tk_value:
            try {
                return _memberAccess[index];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new org.omg.CORBA.TypeCodePackage.Bounds();
            }
        default:
            throw new BadKind();
        }
    }

    public short type_modifier() throws BadKind {
        switch (_kind) {
        case tk_indirect:
            return indirectType().type_modifier();
        case TCKind._tk_value:
            return _type_modifier;
        default:
            throw new BadKind();
        }
    }

    public TypeCode concrete_base_type() throws BadKind {
        switch (_kind) {
        case tk_indirect:
            return indirectType().concrete_base_type();
        case TCKind._tk_value:
            return _concrete_base;
        default:
            throw new BadKind();
        }
    }

    public void read_value(InputStream is) {
        if (is instanceof TypeCodeReader) {
            // hardly possible unless caller knows our "private" stream classes.
            if (read_value_kind((TypeCodeReader)is))
                read_value_body(is);
        } else if (is instanceof CDRInputStream) {
            WrapperInputStream wrapper = new WrapperInputStream((CDRInputStream)is);
            //if (debug) System.out.println("Created WrapperInputStream " + wrapper +
            // " with no parent");
            if (read_value_kind((TypeCodeReader)wrapper))
                read_value_body(wrapper);
        } else {
            read_value_kind(is);
            read_value_body(is);
        }
    }

    private void read_value_recursive(TypeCodeInputStream is) {
        // don't wrap a CDRInputStream reading "inner" TypeCodes.
        if (is instanceof TypeCodeReader) {
            if (read_value_kind((TypeCodeReader)is))
                read_value_body(is);
        } else {
            read_value_kind((InputStream)is);
            read_value_body(is);
        }
    }

    boolean read_value_kind(TypeCodeReader tcis)
    {
        _kind = tcis.read_long();

        // Bug fix 5034649: allow for padding that precedes the typecode kind.
        int myPosition = tcis.getTopLevelPosition()-4;

        // check validity of kind
        if ((_kind < 0 || _kind > typeTable.length) && _kind != tk_indirect) {
            throw wrapper.cannotMarshalBadTckind() ;
        }

        // Don't do any work if this is native
        if (_kind == TCKind._tk_native)
            throw wrapper.cannotMarshalNative() ;

        // We have to remember the stream and position for EVERY type code
        // in case some recursive or indirect type code references it.
        TypeCodeReader topStream = tcis.getTopLevelStream();

        if (_kind == tk_indirect) {
            int streamOffset = tcis.read_long();
            if (streamOffset > -4)
                throw wrapper.invalidIndirection( new Integer(streamOffset) ) ;

            // The encoding used for indirection is the same as that used for recursive ,
            // TypeCodes i.e., a 0xffffffff indirection marker followed by a long offset
            // (in units of octets) from the beginning of the long offset.
            int topPos = tcis.getTopLevelPosition();
            // substract 4 to get back to the beginning of the long offset.
            int indirectTypePosition = topPos - 4 + streamOffset;

            // Now we have to find the referenced type
            // by its indirectTypePosition within topStream.
            //if (debug) System.out.println(
            // "TypeCodeImpl looking up indirection at position topPos " +
            //topPos + " - 4 + offset " + streamOffset + " = " + indirectTypePosition);
            TypeCodeImpl type = topStream.getTypeCodeAtPosition(indirectTypePosition);
            if (type == null)
                throw wrapper.indirectionNotFound( new Integer(indirectTypePosition) ) ;
            setIndirectType(type);
            return false;
        }

        topStream.addTypeCodeAtPosition(this, myPosition);
        return true;
    }

    void read_value_kind(InputStream is) {
        // unmarshal the kind
        _kind = is.read_long();

        // check validity of kind
        if ((_kind < 0 || _kind > typeTable.length) && _kind != tk_indirect) {
            throw wrapper.cannotMarshalBadTckind() ;
        }
        // Don't do any work if this is native
        if (_kind == TCKind._tk_native)
            throw wrapper.cannotMarshalNative() ;

        if (_kind == tk_indirect) {
            throw wrapper.recursiveTypecodeError() ;
        }
    }

    void read_value_body(InputStream is) {
        // start unmarshaling the rest of the typecode, based on the
        // encoding (empty, simple or complex).

        switch (typeTable[_kind]) {
        case EMPTY:
            // nothing to unmarshal
            break;

        case SIMPLE:
            switch (_kind) {
            case TCKind._tk_string:
            case TCKind._tk_wstring:
                _length = is.read_long();
                break;
            case TCKind._tk_fixed:
                _digits = is.read_ushort();
                _scale = is.read_short();
                break;
            default:
                throw wrapper.invalidSimpleTypecode() ;
            }
            break;

        case COMPLEX:
            {
                TypeCodeInputStream _encap = TypeCodeInputStream.readEncapsulation(is,
                    is.orb());

                switch(_kind) {

                case TCKind._tk_objref:
                case TCKind._tk_abstract_interface:
                    {
                        // get the repository id
                        setId(_encap.read_string());
                        // get the name
                        _name = _encap.read_string();
                    }
                    break;

                case TCKind._tk_union:
                    {
                        // get the repository id
                        setId(_encap.read_string());

                        // get the name
                        _name = _encap.read_string();

                        // discriminant typecode
                        _discriminator = new TypeCodeImpl((ORB)is.orb());
                        _discriminator.read_value_recursive(_encap);

                        // default index
                        _defaultIndex = _encap.read_long();

                        // get the number of members
                        _memberCount = _encap.read_long();

                        // create arrays for the label values, names and types of members
                        _unionLabels = new AnyImpl[_memberCount];
                        _memberNames = new String[_memberCount];
                        _memberTypes = new TypeCodeImpl[_memberCount];

                        // read off label values, names and types
                        for (int i=0; i < _memberCount; i++) {
                            _unionLabels[i] = new AnyImpl((ORB)is.orb());
                            if (i == _defaultIndex)
                                // for the default case, read off the zero octet
                                _unionLabels[i].insert_octet(_encap.read_octet());
                            else {
                                switch (realType(_discriminator).kind().value()) {
                                case TCKind._tk_short:
                                    _unionLabels[i].insert_short(_encap.read_short());
                                    break;
                                case TCKind._tk_long:
                                    _unionLabels[i].insert_long(_encap.read_long());
                                    break;
                                case TCKind._tk_ushort:
                                    _unionLabels[i].insert_ushort(_encap.read_short());
                                    break;
                                case TCKind._tk_ulong:
                                    _unionLabels[i].insert_ulong(_encap.read_long());
                                    break;
                                case TCKind._tk_float:
                                    _unionLabels[i].insert_float(_encap.read_float());
                                    break;
                                case TCKind._tk_double:
                                    _unionLabels[i].insert_double(_encap.read_double());
                                    break;
                                case TCKind._tk_boolean:
                                    _unionLabels[i].insert_boolean(_encap.read_boolean());
                                    break;
                                case TCKind._tk_char:
                                    _unionLabels[i].insert_char(_encap.read_char());
                                    break;
                                case TCKind._tk_enum:
                                    _unionLabels[i].type(_discriminator);
                                    _unionLabels[i].insert_long(_encap.read_long());
                                    break;
                                case TCKind._tk_longlong:
                                    _unionLabels[i].insert_longlong(_encap.read_longlong());
                                    break;
                                case TCKind._tk_ulonglong:
                                    _unionLabels[i].insert_ulonglong(_encap.read_longlong());
                                    break;
                                    // _REVISIT_ figure out long double mapping
                                    // case TCKind.tk_longdouble:
                                    // _unionLabels[i].insert_longdouble(_encap.getDouble());
                                    // break;
                                case TCKind._tk_wchar:
                                    _unionLabels[i].insert_wchar(_encap.read_wchar());
                                    break;
                                default:
                                    throw wrapper.invalidComplexTypecode() ;
                                }
                            }
                            _memberNames[i] = _encap.read_string();
                            _memberTypes[i] = new TypeCodeImpl((ORB)is.orb());
                            _memberTypes[i].read_value_recursive(_encap);
                            _memberTypes[i].setParent(this);
                        }
                    }
                    break;

                case TCKind._tk_enum:
                    {
                        // get the repository id
                        setId(_encap.read_string());

                        // get the name
                        _name = _encap.read_string();

                        // get the number of members
                        _memberCount = _encap.read_long();

                        // create arrays for the identifier names
                        _memberNames = new String[_memberCount];

                        // read off identifier names
                        for (int i=0; i < _memberCount; i++)
                            _memberNames[i] = _encap.read_string();
                    }
                    break;

                case TCKind._tk_sequence:
                    {
                        // get the type of the sequence
                        _contentType = new TypeCodeImpl((ORB)is.orb());
                        _contentType.read_value_recursive(_encap);

                        // get the bound on the length of the sequence
                        _length = _encap.read_long();
                    }
                    break;

                case TCKind._tk_array:
                    {
                        // get the type of the array
                        _contentType = new TypeCodeImpl((ORB)is.orb());
                        _contentType.read_value_recursive(_encap);

                        // get the length of the array
                        _length = _encap.read_long();
                    }
                    break;

                case TCKind._tk_alias:
                case TCKind._tk_value_box:
                    {
                        // get the repository id
                        setId(_encap.read_string());

                        // get the name
                        _name = _encap.read_string();

                        // get the type aliased
                        _contentType = new TypeCodeImpl((ORB)is.orb());
                        _contentType.read_value_recursive(_encap);
                    }
                    break;

                case TCKind._tk_except:
                case TCKind._tk_struct:
                    {
                        // get the repository id
                        setId(_encap.read_string());

                        // get the name
                        _name = _encap.read_string();

                        // get the number of members
                        _memberCount = _encap.read_long();

                        // create arrays for the names and types of members
                        _memberNames = new String[_memberCount];
                        _memberTypes = new TypeCodeImpl[_memberCount];

                        // read off member names and types
                        for (int i=0; i < _memberCount; i++) {
                            _memberNames[i] = _encap.read_string();
                            _memberTypes[i] = new TypeCodeImpl((ORB)is.orb());
                            //if (debug) System.out.println("TypeCode " + _name +
                            // " reading member " + _memberNames[i]);
                            _memberTypes[i].read_value_recursive(_encap);
                            _memberTypes[i].setParent(this);
                        }
                    }
                    break;

                case TCKind._tk_value:
                    {
                        // get the repository id
                        setId(_encap.read_string());

                        // get the name
                        _name = _encap.read_string();

                        // get the type modifier
                        _type_modifier = _encap.read_short();

                        // get the type aliased
                        _concrete_base = new TypeCodeImpl((ORB)is.orb());
                        _concrete_base.read_value_recursive(_encap);
                        if (_concrete_base.kind().value() == TCKind._tk_null) {
                            _concrete_base = null;
                        }

                        // get the number of members
                        _memberCount = _encap.read_long();

                        // create arrays for the names, types and visibility of members
                        _memberNames = new String[_memberCount];
                        _memberTypes = new TypeCodeImpl[_memberCount];
                        _memberAccess = new short[_memberCount];

                        // read off value member visibilities
                        for (int i=0; i < _memberCount; i++) {
                            _memberNames[i] = _encap.read_string();
                            _memberTypes[i] = new TypeCodeImpl((ORB)is.orb());
                            //if (debug) System.out.println("TypeCode " + _name +
                            // " reading member " + _memberNames[i]);
                            _memberTypes[i].read_value_recursive(_encap);
                            _memberTypes[i].setParent(this);
                            _memberAccess[i] = _encap.read_short();
                        }
                    }
                    break;

                default:
                    throw wrapper.invalidTypecodeKindMarshal() ;
                }
                break;
            }
        }
    }

    public void write_value(OutputStream os) {
        // Wrap OutputStream into TypeCodeOutputStream.
        // This test shouldn't be necessary according to the Java language spec.
        if (os instanceof TypeCodeOutputStream) {
            this.write_value((TypeCodeOutputStream)os);
        } else {
            TypeCodeOutputStream wrapperOutStream = null;

            if (outBuffer == null) {
                wrapperOutStream = TypeCodeOutputStream.wrapOutputStream(os);
                this.write_value(wrapperOutStream);
                if (cachingEnabled) {
                    // Cache the buffer for repeated writes
                    outBuffer = wrapperOutStream.getTypeCodeBuffer();
                    //if (outBuffer != null)
                        //System.out.println("Caching outBuffer with length = " +
                        //outBuffer.length + " for id = " + _id);
                }
            } else {
                //System.out.println("Using cached outBuffer: length = " + outBuffer.length +
                                   //", id = " + _id);
            }
            // Write the first 4 bytes first to trigger alignment.
            // We know that it is the kind.
            if (cachingEnabled && outBuffer != null) {
                os.write_long(_kind);
                os.write_octet_array(outBuffer, 0, outBuffer.length);
            } else {
                //System.out.println("Buffer is empty for " + _id);
                wrapperOutStream.writeRawBuffer(os, _kind);
            }
        }
    }

    public void write_value(TypeCodeOutputStream tcos) {

        // Don't do any work if this is native
        if (_kind == TCKind._tk_native)
            throw wrapper.cannotMarshalNative() ;

        TypeCodeOutputStream topStream = tcos.getTopLevelStream();
        //if (debug) tcos.printBuffer();

        if (_kind == tk_indirect) {
            //if (debug) System.out.println("Writing indirection " + _name + "to " + _id);
            // The encoding used for indirection is the same as that used for recursive ,
            // TypeCodes i.e., a 0xffffffff indirection marker followed by a long offset
            // (in units of octets) from the beginning of the long offset.
            int pos = topStream.getPositionForID(_id);
            int topPos = tcos.getTopLevelPosition();
            //if (debug) System.out.println("TypeCodeImpl " + tcos +
            // " writing indirection " + _id +
                //" to position " + pos + " at position " + topPos);
            tcos.writeIndirection(tk_indirect, pos);
            // All that gets written is _kind and offset.
            return;
        }

        // The original approach changed for 5034649
        // topStream.addIDAtPosition(_id, tcos.getTopLevelPosition());

        // marshal the kind
        tcos.write_long(_kind);

        //if (debug) System.out.println("Writing " + _name + " with id " + _id);
        // We have to remember the stream and position for EVERY type code
        // in case some recursive or indirect type code references it.
        //
        // Bug fix 5034649:
        // Do this AFTER the write of the _kind in case the alignment
        // for the long changes the position.
        topStream.addIDAtPosition(_id, tcos.getTopLevelPosition()-4);

        switch (typeTable[_kind]) {
        case EMPTY:
            // nothing more to marshal
            break;

        case SIMPLE:
            switch (_kind) {
            case TCKind._tk_string:
            case TCKind._tk_wstring:
                // marshal the bound on string length
                tcos.write_long(_length);
                break;
            case TCKind._tk_fixed:
                tcos.write_ushort(_digits);
                tcos.write_short(_scale);
                break;
            default:
                // unknown typecode kind
                throw wrapper.invalidSimpleTypecode() ;
            }
            break;

        case COMPLEX:
            {
                // create an encapsulation
                TypeCodeOutputStream _encap = tcos.createEncapsulation(tcos.orb());

                switch(_kind) {

                case TCKind._tk_objref:
                case TCKind._tk_abstract_interface:
                    {
                        // put the repository id
                        _encap.write_string(_id);

                        // put the name
                        _encap.write_string(_name);
                    }
                    break;

                case TCKind._tk_union:
                    {
                        // put the repository id
                        _encap.write_string(_id);

                        // put the name
                        _encap.write_string(_name);

                        // discriminant typecode
                        _discriminator.write_value(_encap);

                        // default index
                        _encap.write_long(_defaultIndex);

                        // put the number of members
                        _encap.write_long(_memberCount);

                        // marshal label values, names and types
                        for (int i=0; i < _memberCount; i++) {

                            // for the default case, marshal the zero octet
                            if (i == _defaultIndex)
                                _encap.write_octet(_unionLabels[i].extract_octet());

                            else {
                                switch (realType(_discriminator).kind().value()) {
                                case TCKind._tk_short:
                                    _encap.write_short(_unionLabels[i].extract_short());
                                    break;
                                case TCKind._tk_long:
                                    _encap.write_long(_unionLabels[i].extract_long());
                                    break;
                                case TCKind._tk_ushort:
                                    _encap.write_short(_unionLabels[i].extract_ushort());
                                    break;
                                case TCKind._tk_ulong:
                                    _encap.write_long(_unionLabels[i].extract_ulong());
                                    break;
                                case TCKind._tk_float:
                                    _encap.write_float(_unionLabels[i].extract_float());
                                    break;
                                case TCKind._tk_double:
                                    _encap.write_double(_unionLabels[i].extract_double());
                                    break;
                                case TCKind._tk_boolean:
                                    _encap.write_boolean(_unionLabels[i].extract_boolean());
                                    break;
                                case TCKind._tk_char:
                                    _encap.write_char(_unionLabels[i].extract_char());
                                    break;
                                case TCKind._tk_enum:
                                    _encap.write_long(_unionLabels[i].extract_long());
                                    break;
                                case TCKind._tk_longlong:
                                    _encap.write_longlong(_unionLabels[i].extract_longlong());
                                    break;
                                case TCKind._tk_ulonglong:
                                    _encap.write_longlong(_unionLabels[i].extract_ulonglong());
                                    break;
                                    // _REVISIT_ figure out long double mapping
                                    // case TCKind.tk_longdouble:
                                    // _encap.putDouble(_unionLabels[i].extract_longdouble());
                                    // break;
                                case TCKind._tk_wchar:
                                    _encap.write_wchar(_unionLabels[i].extract_wchar());
                                    break;
                                default:
                                    throw wrapper.invalidComplexTypecode() ;
                                }
                            }
                            _encap.write_string(_memberNames[i]);
                            _memberTypes[i].write_value(_encap);
                        }
                    }
                    break;

                case TCKind._tk_enum:
                    {
                        // put the repository id
                        _encap.write_string(_id);

                        // put the name
                        _encap.write_string(_name);

                        // put the number of members
                        _encap.write_long(_memberCount);

                        // marshal identifier names
                        for (int i=0; i < _memberCount; i++)
                            _encap.write_string(_memberNames[i]);
                    }
                    break;

                case TCKind._tk_sequence:
                    {
                        // put the type of the sequence
                        lazy_content_type().write_value(_encap);

                        // put the bound on the length of the sequence
                        _encap.write_long(_length);
                    }
                    break;

                case TCKind._tk_array:
                    {
                        // put the type of the array
                        _contentType.write_value(_encap);

                        // put the length of the array
                        _encap.write_long(_length);
                    }
                    break;

                case TCKind._tk_alias:
                case TCKind._tk_value_box:
                    {
                        // put the repository id
                        _encap.write_string(_id);

                        // put the name
                        _encap.write_string(_name);

                        // put the type aliased
                        _contentType.write_value(_encap);
                    }
                    break;

                case TCKind._tk_struct:
                case TCKind._tk_except:
                    {
                        // put the repository id
                        _encap.write_string(_id);

                        // put the name
                        _encap.write_string(_name);

                        // put the number of members
                        _encap.write_long(_memberCount);

                        // marshal member names and types
                        for (int i=0; i < _memberCount; i++) {
                            _encap.write_string(_memberNames[i]);
                            //if (debug) System.out.println("TypeCode " + _name +
                            // " writing member " + _memberNames[i]);
                            _memberTypes[i].write_value(_encap);
                        }
                    }
                    break;

                case TCKind._tk_value:
                    {
                        // put the repository id
                        _encap.write_string(_id);

                        // put the name
                        _encap.write_string(_name);

                        // put the type modifier
                        _encap.write_short(_type_modifier);

                        // put the type aliased
                        if (_concrete_base == null) {
                            _orb.get_primitive_tc(TCKind._tk_null).write_value(_encap);
                        } else {
                            _concrete_base.write_value(_encap);
                        }

                        // put the number of members
                        _encap.write_long(_memberCount);

                        // marshal member names and types
                        for (int i=0; i < _memberCount; i++) {
                            _encap.write_string(_memberNames[i]);
                            //if (debug) System.out.println("TypeCode " + _name +
                            // " writing member " + _memberNames[i]);
                            _memberTypes[i].write_value(_encap);
                            _encap.write_short(_memberAccess[i]);
                        }
                    }
                    break;

                default:
                    throw wrapper.invalidTypecodeKindMarshal() ;
                }

                // marshal the encapsulation
                _encap.writeOctetSequenceTo(tcos);
                break;
            }
        }
    }

    /**
     * This is not a copy of the TypeCodeImpl objects, but instead it
     * copies the value this type code is representing.
     * See AnyImpl read_value and write_value for usage.
     * The state of this TypeCodeImpl instance isn't changed, only used
     * by the Any to do the correct copy.
     */
    protected void copy(org.omg.CORBA.portable.InputStream src,
        org.omg.CORBA.portable.OutputStream dst)
    {
        switch (_kind) {

        case TCKind._tk_null:
        case TCKind._tk_void:
        case TCKind._tk_native:
        case TCKind._tk_abstract_interface:
            break;

        case TCKind._tk_short:
        case TCKind._tk_ushort:
            dst.write_short(src.read_short());
            break;

        case TCKind._tk_long:
        case TCKind._tk_ulong:
            dst.write_long(src.read_long());
            break;

        case TCKind._tk_float:
            dst.write_float(src.read_float());
            break;

        case TCKind._tk_double:
            dst.write_double(src.read_double());
            break;

        case TCKind._tk_longlong:
        case TCKind._tk_ulonglong:
            dst.write_longlong(src.read_longlong());
            break;

        case TCKind._tk_longdouble:
            throw wrapper.tkLongDoubleNotSupported() ;

        case TCKind._tk_boolean:
            dst.write_boolean(src.read_boolean());
            break;

        case TCKind._tk_char:
            dst.write_char(src.read_char());
            break;

        case TCKind._tk_wchar:
            dst.write_wchar(src.read_wchar());
            break;

        case TCKind._tk_octet:
            dst.write_octet(src.read_octet());
            break;

        case TCKind._tk_string:
            {
                String s;
                s = src.read_string();
                // make sure length bound in typecode is not violated
                if ((_length != 0) && (s.length() > _length))
                    throw wrapper.badStringBounds( new Integer(s.length()),
                        new Integer(_length) ) ;
                dst.write_string(s);
            }
            break;

        case TCKind._tk_wstring:
            {
                String s;
                s = src.read_wstring();
                // make sure length bound in typecode is not violated
                if ((_length != 0) && (s.length() > _length))
                    throw wrapper.badStringBounds( new Integer(s.length()),
                        new Integer(_length) ) ;
                dst.write_wstring(s);
            }
            break;

        case TCKind._tk_fixed:
            {
                dst.write_ushort(src.read_ushort());
                dst.write_short(src.read_short());
            }
            break;

        case TCKind._tk_any:
            {
                //Any tmp = new AnyImpl(_orb);
                Any tmp =  ((CDRInputStream)src).orb().create_any();
                TypeCodeImpl t = new TypeCodeImpl((ORB)dst.orb());
                t.read_value((org.omg.CORBA_2_3.portable.InputStream)src);
                t.write_value((org.omg.CORBA_2_3.portable.OutputStream)dst);
                tmp.read_value(src, t);
                tmp.write_value(dst);
                break;
            }

        case TCKind._tk_TypeCode:
            {
                dst.write_TypeCode(src.read_TypeCode());
                break;
            }

        case TCKind._tk_Principal:
            {
                dst.write_Principal(src.read_Principal());
                break;
            }

        case TCKind._tk_objref:
            {
                dst.write_Object(src.read_Object());
                break;
            }

        case TCKind._tk_except:
            // Copy repositoryId
            dst.write_string(src.read_string());

            // Fall into ...
        // _REVISIT_ what about the inherited members of this values concrete base type?
        case TCKind._tk_value:
        case TCKind._tk_struct:
            {
                // copy each element, using the corresponding member type
                for (int i=0; i < _memberTypes.length; i++) {
                    _memberTypes[i].copy(src, dst);
                }
                break;
            }
        case TCKind._tk_union:
    /* _REVISIT_ More generic code?
            {
                Any discriminator = new AnyImpl(_orb);
                discriminator.read_value(src, _discriminator);
                discriminator.write_value(dst);
                int labelIndex = currentUnionMemberIndex(discriminator);
                if (labelIndex == -1) {
                    // check if label has not been found
                    if (_defaultIndex == -1)
                        // throw exception if default was not expected
                        throw new MARSHAL();
                    else
                        // must be of the default branch type
                        _memberTypes[_defaultIndex].copy(src, dst);
                } else {
                    _memberTypes[labelIndex].copy(src, dst);
                }
            }
    */
            {
                Any tagValue = new AnyImpl( (ORB)src.orb());

                switch  (realType(_discriminator).kind().value()) {
                case TCKind._tk_short:
                    {
                        short value = src.read_short();
                        tagValue.insert_short(value);
                        dst.write_short(value);
                        break;
                    }
                case TCKind._tk_long:
                    {
                        int value = src.read_long();
                        tagValue.insert_long(value);
                        dst.write_long(value);
                        break;
                    }
                case TCKind._tk_ushort:
                    {
                        short value = src.read_short();
                        tagValue.insert_ushort(value);
                        dst.write_short(value);
                        break;
                    }
                case TCKind._tk_ulong:
                    {
                        int value = src.read_long();
                        tagValue.insert_ulong(value);
                        dst.write_long(value);
                        break;
                    }
                case TCKind._tk_float:
                    {
                        float value = src.read_float();
                        tagValue.insert_float(value);
                        dst.write_float(value);
                        break;
                    }
                case TCKind._tk_double:
                    {
                        double value = src.read_double();
                        tagValue.insert_double(value);
                        dst.write_double(value);
                        break;
                    }
                case TCKind._tk_boolean:
                    {
                        boolean value = src.read_boolean();
                        tagValue.insert_boolean(value);
                        dst.write_boolean(value);
                        break;
                    }
                case TCKind._tk_char:
                    {
                        char value = src.read_char();
                        tagValue.insert_char(value);
                        dst.write_char(value);
                        break;
                    }
                case TCKind._tk_enum:
                    {
                        int value = src.read_long();
                        tagValue.type(_discriminator);
                        tagValue.insert_long(value);
                        dst.write_long(value);
                        break;
                    }
                case TCKind._tk_longlong:
                    {
                        long value = src.read_longlong();
                        tagValue.insert_longlong(value);
                        dst.write_longlong(value);
                        break;
                    }
                case TCKind._tk_ulonglong:
                    {
                        long value = src.read_longlong();
                        tagValue.insert_ulonglong(value);
                        dst.write_longlong(value);
                        break;
                    }
                    // _REVISIT_ figure out long double mapping
                    // case TCKind.tk_longdouble:
                    // {
                    // double value = src.read_double();
                    //  tagValue.insert_longdouble(value);
                    //  dst.putDouble(value);
                    //  break;
                    //}
                case TCKind._tk_wchar:
                    {
                        char value = src.read_wchar();
                        tagValue.insert_wchar(value);
                        dst.write_wchar(value);
                        break;
                    }
                default:
                    throw wrapper.illegalUnionDiscriminatorType() ;
                }

                // using the value of the tag, find out the type of the value
                // following.

                int labelIndex;
                for (labelIndex = 0; labelIndex < _unionLabels.length; labelIndex++) {
                    // use equality over anys
                    if (tagValue.equal(_unionLabels[labelIndex])) {
                        _memberTypes[labelIndex].copy(src, dst);
                        break;
                    }
                }

                if (labelIndex == _unionLabels.length) {
                    // check if label has not been found
                    if (_defaultIndex == -1)
                        // throw exception if default was not expected
                        throw wrapper.unexpectedUnionDefault() ;
                    else
                        // must be of the default branch type
                        _memberTypes[_defaultIndex].copy(src, dst);
                }
                break;
            }

        case TCKind._tk_enum:
            dst.write_long(src.read_long());
            break;

        case TCKind._tk_sequence:
            // get the length of the sequence
            int seqLength = src.read_long();

            // check for sequence bound violated
            if ((_length != 0) && (seqLength > _length))
                throw wrapper.badSequenceBounds( new Integer(seqLength),
                    new Integer(_length) ) ;

            // write the length of the sequence
            dst.write_long(seqLength);

            // copy each element of the seq using content type
            lazy_content_type(); // make sure it's resolved
            for (int i=0; i < seqLength; i++)
                _contentType.copy(src, dst);
            break;

        case TCKind._tk_array:
            // copy each element of the array using content type
            for (int i=0; i < _length; i++)
                _contentType.copy(src, dst);
            break;

        case TCKind._tk_alias:
        case TCKind._tk_value_box:
            // follow the alias
            _contentType.copy(src, dst);
            break;

        case tk_indirect:
            // need to follow offset, get unmarshal typecode from that
            // offset, and use that to do the copy
            // Don't need to read type code before using it to do the copy.
            // It should be fully usable.
            indirectType().copy(src, dst);
            break;

        default:
            throw wrapper.invalidTypecodeKindMarshal() ;
        }
    }


    static protected short digits(java.math.BigDecimal value) {
        if (value == null)
            return 0;
        short length = (short)value.unscaledValue().toString().length();
        if (value.signum() == -1)
            length--;
        return length;
    }

    static protected short scale(java.math.BigDecimal value) {
        if (value == null)
            return 0;
        return (short)value.scale();
    }

    // Utility methods

    // Only for union type. Returns the index of the union member
    // corresponding to the discriminator. If not found returns the
    // default index or -1 if there is no default index.
    int currentUnionMemberIndex(Any discriminatorValue) throws BadKind {
        if (_kind != TCKind._tk_union)
            throw new BadKind();

        try {
            for (int i=0; i<member_count(); i++) {
                if (member_label(i).equal(discriminatorValue)) {
                    return i;
                }
            }
            if (_defaultIndex != -1) {
                return _defaultIndex;
            }
        } catch (BadKind bad) {
        } catch (org.omg.CORBA.TypeCodePackage.Bounds bounds) {
        }
        return -1;
    }

    public String description() {
        return "TypeCodeImpl with kind " + _kind + " and id " + _id;
    }

    public String toString() {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream(1024);
        PrintStream printOut = new PrintStream(byteOut, true);
        printStream(printOut);
        return super.toString() + " =\n" + byteOut.toString();
    }

    public void printStream(PrintStream s) {
        printStream(s, 0);
    }

    private void printStream(PrintStream s, int level) {
        if (_kind == tk_indirect) {
            s.print("indirect " + _id);
            return;
        }

        switch (_kind) {
            case TCKind._tk_null:
            case TCKind._tk_void:
            case TCKind._tk_short:
            case TCKind._tk_long:
            case TCKind._tk_ushort:
            case TCKind._tk_ulong:
            case TCKind._tk_float:
            case TCKind._tk_double:
            case TCKind._tk_boolean:
            case TCKind._tk_char:
            case TCKind._tk_octet:
            case TCKind._tk_any:
            case TCKind._tk_TypeCode:
            case TCKind._tk_Principal:
            case TCKind._tk_objref:
            case TCKind._tk_longlong:
            case TCKind._tk_ulonglong:
            case TCKind._tk_longdouble:
            case TCKind._tk_wchar:
            case TCKind._tk_native:
                s.print(kindNames[_kind] + " " + _name);
                break;

            case TCKind._tk_struct:
            case TCKind._tk_except:
            case TCKind._tk_value:
                s.println(kindNames[_kind] + " " + _name + " = {");
                for(int i=0; i<_memberCount; i++) {
                    // memberName might differ from the name of the member.
                    s.print(indent(level + 1));
                    if (_memberTypes[i] != null)
                        _memberTypes[i].printStream(s, level + 1);
                    else
                        s.print("<unknown type>");
                    s.println(" " + _memberNames[i] + ";");
                }
                s.print(indent(level) + "}");
                break;

            case TCKind._tk_union:
                s.print("union " + _name + "...");
                break;

            case TCKind._tk_enum:
                s.print("enum " + _name + "...");
                break;

            case TCKind._tk_string:
                if (_length == 0)
                    s.print("unbounded string " + _name);
                else
                    s.print("bounded string(" + _length + ") " + _name);
                break;

            case TCKind._tk_sequence:
            case TCKind._tk_array:
                s.println(kindNames[_kind] + "[" + _length + "] " + _name + " = {");
                s.print(indent(level + 1));
                if (lazy_content_type() != null) {
                    lazy_content_type().printStream(s, level + 1);
                }
                s.println(indent(level) + "}");
                break;

            case TCKind._tk_alias:
                s.print("alias " + _name + " = " +
                    (_contentType != null ? _contentType._name : "<unresolved>"));
                break;

            case TCKind._tk_wstring:
                s.print("wstring[" + _length + "] " + _name);
                break;

            case TCKind._tk_fixed:
                s.print("fixed(" + _digits + ", " + _scale + ") " + _name);
                break;

            case TCKind._tk_value_box:
                s.print("valueBox " + _name + "...");
                break;

            case TCKind._tk_abstract_interface:
                s.print("abstractInterface " + _name + "...");
                break;

            default:
                s.print("<unknown type>");
                break;
        }
    }

    private String indent(int level) {
        String indent = "";
        for(int i=0; i<level; i++) {
            indent += "  ";
        }
        return indent;
    }

    protected void setCaching(boolean enableCaching) {
        cachingEnabled = enableCaching;
        if (enableCaching == false)
            outBuffer = null;
    }
}
