/*
 * Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.jvm;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.code.Types.UniqueType;
import com.sun.tools.javac.util.Name;


/** A JVM class file.
 *
 *  <p>Generic Java classfiles have one additional attribute for classes,
 *  methods and fields:
 *  <pre>
 *   "Signature" (u4 attr-length, u2 signature-index)
 *  </pre>
 *
 *  <p>A signature gives the full Java type of a method or field. When
 *  used as a class attribute, it indicates type parameters, followed
 *  by supertype, followed by all interfaces.
 *  <pre>
 *     methodOrFieldSignature ::= type
 *     classSignature         ::= [ typeparams ] supertype { interfacetype }
 *  </pre>
 *  <p>The type syntax in signatures is extended as follows:
 *  <pre>{@literal
 *     type       ::= ... | classtype | methodtype | typevar
 *     classtype  ::= classsig { '.' classsig }
 *     classig    ::= 'L' name [typeargs] ';'
 *     methodtype ::= [ typeparams ] '(' { type } ')' type
 *     typevar    ::= 'T' name ';'
 *     typeargs   ::= '<' type { type } '>'
 *     typeparams ::= '<' typeparam { typeparam } '>'
 *     typeparam  ::= name ':' type
 *  }</pre>
 *  <p>This class defines constants used in class files as well
 *  as routines to convert between internal ``.'' and external ``/''
 *  separators in class names.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b> */
public class ClassFile {

    public final static int JAVA_MAGIC = 0xCAFEBABE;

    // see Target
    public final static int CONSTANT_Utf8 = 1;
    public final static int CONSTANT_Unicode = 2;
    public final static int CONSTANT_Integer = 3;
    public final static int CONSTANT_Float = 4;
    public final static int CONSTANT_Long = 5;
    public final static int CONSTANT_Double = 6;
    public final static int CONSTANT_Class = 7;
    public final static int CONSTANT_String = 8;
    public final static int CONSTANT_Fieldref = 9;
    public final static int CONSTANT_Methodref = 10;
    public final static int CONSTANT_InterfaceMethodref = 11;
    public final static int CONSTANT_NameandType = 12;
    public final static int CONSTANT_MethodHandle = 15;
    public final static int CONSTANT_MethodType = 16;
    public final static int CONSTANT_Dynamic = 17;
    public final static int CONSTANT_InvokeDynamic = 18;
    public final static int CONSTANT_Module = 19;
    public final static int CONSTANT_Package = 20;

    public final static int REF_getField = 1;
    public final static int REF_getStatic = 2;
    public final static int REF_putField = 3;
    public final static int REF_putStatic = 4;
    public final static int REF_invokeVirtual = 5;
    public final static int REF_invokeStatic = 6;
    public final static int REF_invokeSpecial = 7;
    public final static int REF_newInvokeSpecial = 8;
    public final static int REF_invokeInterface = 9;

    public final static int MAX_PARAMETERS = 0xff;
    public final static int MAX_DIMENSIONS = 0xff;
    public final static int MAX_CODE = 0xffff;
    public final static int MAX_LOCALS = 0xffff;
    public final static int MAX_STACK = 0xffff;

    public final static int PREVIEW_MINOR_VERSION = 0xffff;

    public enum Version {
        V45_3(45, 3), // base level for all attributes
        V49(49, 0),   // JDK 1.5: enum, generics, annotations
        V50(50, 0),   // JDK 1.6: stackmaps
        V51(51, 0),   // JDK 1.7
        V52(52, 0),   // JDK 1.8: lambda, type annos, param names
        V53(53, 0),   // JDK 1.9: modules, indy string concat
        V54(54, 0),   // JDK 10
        V55(55, 0);   // JDK 11: constant dynamic
        Version(int major, int minor) {
            this.major = major;
            this.minor = minor;
        }
        public final int major, minor;

        private static final Version MIN = values()[0];
        /** Return the least version supported, MIN */
        public static Version MIN() { return MIN; }

        private static final Version MAX = values()[values().length-1];
        /** Return the largest version supported, MAX */
        public static Version MAX() { return MAX; }
    }


/************************************************************************
 * String Translation Routines
 ***********************************************************************/

    /**
     * Return internal representation of buf[offset..offset+len-1], converting '/' to '.'.
     *
     * Note: the naming is the inverse of that used by JVMS 4.2 The Internal Form Of Names,
     * which defines "internal name" to be the form using "/" instead of "."
     */
    public static byte[] internalize(byte[] buf, int offset, int len) {
        byte[] translated = new byte[len];
        for (int j = 0; j < len; j++) {
            byte b = buf[offset + j];
            if (b == '/') translated[j] = (byte) '.';
            else translated[j] = b;
        }
        return translated;
    }

    /**
     * Return internal representation of given name, converting '/' to '.'.
     *
     * Note: the naming is the inverse of that used by JVMS 4.2 The Internal Form Of Names,
     * which defines "internal name" to be the form using "/" instead of "."
     */
    public static byte[] internalize(Name name) {
        return internalize(name.getByteArray(), name.getByteOffset(), name.getByteLength());
    }

    /**
     * Return external representation of buf[offset..offset+len-1], converting '.' to '/'.
     *
     * Note: the naming is the inverse of that used by JVMS 4.2 The Internal Form Of Names,
     * which defines "internal name" to be the form using "/" instead of "."
     */
    public static byte[] externalize(byte[] buf, int offset, int len) {
        byte[] translated = new byte[len];
        for (int j = 0; j < len; j++) {
            byte b = buf[offset + j];
            if (b == '.') translated[j] = (byte) '/';
            else translated[j] = b;
        }
        return translated;
    }

    /**
     * Return external representation of given name, converting '/' to '.'.
     *
     * Note: the naming is the inverse of that used by JVMS 4.2 The Internal Form Of Names,
     * which defines "internal name" to be the form using "/" instead of "."
     */
    public static byte[] externalize(Name name) {
        return externalize(name.getByteArray(), name.getByteOffset(), name.getByteLength());
    }

/************************************************************************
 * Name-and-type
 ***********************************************************************/

    /** A class for the name-and-type signature of a method or field.
     */
    public static class NameAndType {
        Name name;
        UniqueType uniqueType;
        Types types;

        NameAndType(Name name, Type type, Types types) {
            this.name = name;
            this.uniqueType = new UniqueType(type, types);
            this.types = types;
        }

        void setType(Type type) {
            this.uniqueType = new UniqueType(type, types);
        }

        @Override
        public boolean equals(Object other) {
            return (other instanceof NameAndType &&
                    name == ((NameAndType) other).name &&
                        uniqueType.equals(((NameAndType) other).uniqueType));
        }

        @Override
        public int hashCode() {
            return name.hashCode() * uniqueType.hashCode();
        }
    }
}
