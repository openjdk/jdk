/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.rmic.iiop;

import sun.tools.java.Identifier;

public interface Constants extends sun.rmi.rmic.Constants {

    // Identifiers for referenced classes:

    public static final Identifier idReplyHandler =
        Identifier.lookup("org.omg.CORBA.portable.ResponseHandler");
    public static final Identifier idStubBase =
        Identifier.lookup("javax.rmi.CORBA.Stub");
    public static final Identifier idTieBase =
        Identifier.lookup("org.omg.CORBA.portable.ObjectImpl");
    public static final Identifier idTieInterface =
        Identifier.lookup("javax.rmi.CORBA.Tie");
    public static final Identifier idPOAServantType =
        Identifier.lookup( "org.omg.PortableServer.Servant" ) ;
    public static final Identifier idDelegate =
        Identifier.lookup("org.omg.CORBA.portable.Delegate");
    public static final Identifier idOutputStream =
        Identifier.lookup("org.omg.CORBA.portable.OutputStream");
    public static final Identifier idExtOutputStream =
        Identifier.lookup("org.omg.CORBA_2_3.portable.OutputStream");
    public static final Identifier idInputStream =
        Identifier.lookup("org.omg.CORBA.portable.InputStream");
    public static final Identifier idExtInputStream =
        Identifier.lookup("org.omg.CORBA_2_3.portable.InputStream");
    public static final Identifier idSystemException =
        Identifier.lookup("org.omg.CORBA.SystemException");
    public static final Identifier idBadMethodException =
        Identifier.lookup("org.omg.CORBA.BAD_OPERATION");
    public static final Identifier idPortableUnknownException =
        Identifier.lookup("org.omg.CORBA.portable.UnknownException");
    public static final Identifier idApplicationException =
        Identifier.lookup("org.omg.CORBA.portable.ApplicationException");
    public static final Identifier idRemarshalException =
        Identifier.lookup("org.omg.CORBA.portable.RemarshalException");
    public static final Identifier idJavaIoExternalizable =
        Identifier.lookup("java.io.Externalizable");
    public static final Identifier idCorbaObject =
        Identifier.lookup("org.omg.CORBA.Object");
    public static final Identifier idCorbaORB =
        Identifier.lookup("org.omg.CORBA.ORB");
    public static final Identifier idClassDesc =
        Identifier.lookup("javax.rmi.CORBA.ClassDesc");
    public static final Identifier idJavaIoIOException =
        Identifier.lookup("java.io.IOException");
    public static final Identifier idIDLEntity =
        Identifier.lookup("org.omg.CORBA.portable.IDLEntity");
    public static final Identifier idValueBase =
        Identifier.lookup("org.omg.CORBA.portable.ValueBase");
    public static final Identifier idBoxedRMI =
        Identifier.lookup("org.omg.boxedRMI");
    public static final Identifier idBoxedIDL =
        Identifier.lookup("org.omg.boxedIDL");
    public static final Identifier idCorbaUserException =
        Identifier.lookup("org.omg.CORBA.UserException");


    // Identifiers for primitive types:

    public static final Identifier idBoolean =
        Identifier.lookup("boolean");
    public static final Identifier idByte =
        Identifier.lookup("byte");
    public static final Identifier idChar =
        Identifier.lookup("char");
    public static final Identifier idShort =
        Identifier.lookup("short");
    public static final Identifier idInt =
        Identifier.lookup("int");
    public static final Identifier idLong =
        Identifier.lookup("long");
    public static final Identifier idFloat =
        Identifier.lookup("float");
    public static final Identifier idDouble =
        Identifier.lookup("double");
    public static final Identifier idVoid =
        Identifier.lookup("void");

    // IndentingWriter constructor args:

    public static final int INDENT_STEP = 4;
    public static final int TAB_SIZE = Integer.MAX_VALUE; // No tabs.

    // Type status codes:

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_VALID = 1;
    public static final int STATUS_INVALID = 2;

    // Java Names:

    public static final String NAME_SEPARATOR = ".";
    public static final String SERIAL_VERSION_UID = "serialVersionUID";

    // IDL Names:

    public static final String[] IDL_KEYWORDS = {
        "abstract",
        "any",
        "attribute",
        "boolean",
        "case",
        "char",
        "const",
        "context",
        "custom",
        "default",
        "double",
        "enum",
        "exception",
        "factory",
        "FALSE",
        "fixed",
        "float",
        "in",
        "inout",
        "interface",
        "long",
        "module",
        "native",
        "Object",
        "octet",
        "oneway",
        "out",
        "private",
        "public",
        "raises",
        "readonly",
        "sequence",
        "short",
        "string",
        "struct",
        "supports",
        "switch",
        "TRUE",
        "truncatable",
        "typedef",
        "unsigned",
        "union",
        "ValueBase",
        "valuetype",
        "void",
        "wchar",
        "wstring",
    };


    public static final String EXCEPTION_SUFFIX = "Exception";
    public static final String ERROR_SUFFIX = "Error";
    public static final String EX_SUFFIX = "Ex";

    public static final String IDL_REPOSITORY_ID_PREFIX = "IDL:";
    public static final String IDL_REPOSITORY_ID_VERSION = ":1.0";

    public static final String[]  IDL_CORBA_MODULE = {"CORBA"};
    public static final String[]  IDL_SEQUENCE_MODULE = {"org","omg","boxedRMI"};
    public static final String[]  IDL_BOXEDIDL_MODULE = {"org","omg","boxedIDL"};

    public static final String    IDL_CLASS = "ClassDesc";
    public static final String[]  IDL_CLASS_MODULE = {"javax","rmi","CORBA"};

    public static final String    IDL_IDLENTITY = "IDLEntity";
    public static final String    IDL_SERIALIZABLE = "Serializable";
    public static final String    IDL_EXTERNALIZABLE = "Externalizable";
    public static final String[]  IDL_JAVA_IO_MODULE = {"java","io"};
    public static final String[]  IDL_ORG_OMG_CORBA_MODULE = {"org","omg","CORBA"};
    public static final String[]  IDL_ORG_OMG_CORBA_PORTABLE_MODULE = {"org","omg","CORBA","portable"};

    public static final String    IDL_JAVA_LANG_OBJECT = "_Object";
    public static final String[]  IDL_JAVA_LANG_MODULE = {"java","lang"};

    public static final String    IDL_JAVA_RMI_REMOTE = "Remote";
    public static final String[]  IDL_JAVA_RMI_MODULE = {"java","rmi"};

    public static final String  IDL_SEQUENCE = "seq";

    public static final String  IDL_CONSTRUCTOR = "create";

    public static final String  IDL_NAME_SEPARATOR = "::";
    public static final String  IDL_BOOLEAN = "boolean";
    public static final String  IDL_BYTE = "octet";
    public static final String  IDL_CHAR = "wchar";
    public static final String  IDL_SHORT = "short";
    public static final String  IDL_INT = "long";
    public static final String  IDL_LONG = "long long";
    public static final String  IDL_FLOAT = "float";
    public static final String  IDL_DOUBLE = "double";
    public static final String  IDL_VOID = "void";

    public static final String  IDL_STRING = "WStringValue";
    public static final String  IDL_CONSTANT_STRING = "wstring";
    public static final String  IDL_CORBA_OBJECT = "Object";
    public static final String  IDL_ANY = "any";

    // File names:

    public static final String SOURCE_FILE_EXTENSION = ".java";
    public static final String IDL_FILE_EXTENSION = ".idl";

    // Type Codes:

    public static final int TYPE_VOID                   = 0x00000001;   // In PrimitiveType
    public static final int TYPE_BOOLEAN                = 0x00000002;   // In PrimitiveType
    public static final int TYPE_BYTE                   = 0x00000004;   // In PrimitiveType
    public static final int TYPE_CHAR                   = 0x00000008;   // In PrimitiveType
    public static final int TYPE_SHORT                  = 0x00000010;   // In PrimitiveType
    public static final int TYPE_INT                    = 0x00000020;   // In PrimitiveType
    public static final int TYPE_LONG                   = 0x00000040;   // In PrimitiveType
    public static final int TYPE_FLOAT                  = 0x00000080;   // In PrimitiveType
    public static final int TYPE_DOUBLE                 = 0x00000100;   // In PrimitiveType

    public static final int TYPE_STRING                 = 0x00000200;   // In SpecialClassType (String)
    public static final int TYPE_ANY                    = 0x00000400;   // In SpecialInterfaceType (Serializable,Externalizable)
    public static final int TYPE_CORBA_OBJECT   = 0x00000800;   // In SpecialInterfaceType (CORBA.Object,Remote)

    public static final int TYPE_REMOTE                 = 0x00001000;   // In RemoteType
    public static final int TYPE_ABSTRACT               = 0x00002000;   // In AbstractType
    public static final int TYPE_NC_INTERFACE   = 0x00004000;   // In NCInterfaceType

    public static final int TYPE_VALUE                  = 0x00008000;   // In ValueType
    public static final int TYPE_IMPLEMENTATION = 0x00010000;   // In ImplementationType
    public static final int TYPE_NC_CLASS               = 0x00020000;   // In NCClassType

    public static final int TYPE_ARRAY                  = 0x00040000;   // In ArrayType
    public static final int TYPE_JAVA_RMI_REMOTE = 0x00080000;  // In SpecialInterfaceType

    // Type code masks:

    public static final int TYPE_NONE                   = 0x00000000;
    public static final int TYPE_ALL                    = 0xFFFFFFFF;
    public static final int TYPE_MASK                   = 0x00FFFFFF;
    public static final int TM_MASK                             = 0xFF000000;

    // Type code modifiers:

    public static final int TM_PRIMITIVE                = 0x01000000;
    public static final int TM_COMPOUND                 = 0x02000000;
    public static final int TM_CLASS                    = 0x04000000;
    public static final int TM_INTERFACE                = 0x08000000;
    public static final int TM_SPECIAL_CLASS    = 0x10000000;
    public static final int TM_SPECIAL_INTERFACE= 0x20000000;
    public static final int TM_NON_CONFORMING   = 0x40000000;
    public static final int TM_INNER            = 0x80000000;

    // Attribute kinds...

    public static final int ATTRIBUTE_NONE = 0;     // Not an attribute.
    public static final int ATTRIBUTE_IS = 1;       // read-only, had "is" prefix.
    public static final int ATTRIBUTE_GET = 2;      // read-only, had "get" prefix.
    public static final int ATTRIBUTE_IS_RW = 3;    // read-write, had "is" prefix.
    public static final int ATTRIBUTE_GET_RW = 4;   // read-write, had "get" prefix.
    public static final int ATTRIBUTE_SET = 5;      // had "set" prefix.

    public static final String[] ATTRIBUTE_WIRE_PREFIX = {
        "",
        "_get_",
        "_get_",
        "_get_",
        "_get_",
        "_set_",
    };
}
