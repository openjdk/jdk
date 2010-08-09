/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.util;

/**
 * Access to the compiler's name table.  STandard names are defined,
 * as well as methods to create new names.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Names {

    public static final Context.Key<Names> namesKey = new Context.Key<Names>();

    public static Names instance(Context context) {
        Names instance = context.get(namesKey);
        if (instance == null) {
            instance = new Names(context);
            context.put(namesKey, instance);
        }
        return instance;
    }

    public final Name slash;
    public final Name hyphen;
    public final Name T;
    public final Name slashequals;
    public final Name deprecated;
    public final Name init;
    public final Name clinit;
    public final Name error;
    public final Name any;
    public final Name empty;
    public final Name one;
    public final Name period;
    public final Name comma;
    public final Name semicolon;
    public final Name asterisk;
    public final Name _this;
    public final Name _super;
    public final Name _default;
    public final Name _class;
    public final Name java_lang;
    public final Name java_lang_Object;
    public final Name java_lang_Class;
    public final Name java_lang_Cloneable;
    public final Name java_io_Serializable;
    public final Name serialVersionUID;
    public final Name java_lang_Enum;
    public final Name java_dyn_MethodHandle;
    public final Name java_dyn_InvokeDynamic;
    public final Name package_info;
    public final Name ConstantValue;
    public final Name LineNumberTable;
    public final Name LocalVariableTable;
    public final Name LocalVariableTypeTable;
    public final Name CharacterRangeTable;
    public final Name StackMap;
    public final Name StackMapTable;
    public final Name SourceID;
    public final Name CompilationID;
    public final Name Code;
    public final Name Exceptions;
    public final Name SourceFile;
    public final Name InnerClasses;
    public final Name Synthetic;
    public final Name Bridge;
    public final Name Deprecated;
    public final Name Enum;
    public final Name _name;
    public final Name Signature;
    public final Name Varargs;
    public final Name Annotation;
    public final Name RuntimeVisibleAnnotations;
    public final Name RuntimeInvisibleAnnotations;
    public final Name RuntimeVisibleTypeAnnotations;
    public final Name RuntimeInvisibleTypeAnnotations;
    public final Name RuntimeVisibleParameterAnnotations;
    public final Name RuntimeInvisibleParameterAnnotations;
    public final Name PolymorphicSignature;
    public final Name Value;
    public final Name EnclosingMethod;
    public final Name desiredAssertionStatus;
    public final Name append;
    public final Name family;
    public final Name forName;
    public final Name toString;
    public final Name length;
    public final Name valueOf;
    public final Name value;
    public final Name getMessage;
    public final Name getClass;
    public final Name TYPE;
    public final Name TYPE_USE;
    public final Name TYPE_PARAMETER;
    public final Name FIELD;
    public final Name METHOD;
    public final Name PARAMETER;
    public final Name CONSTRUCTOR;
    public final Name LOCAL_VARIABLE;
    public final Name ANNOTATION_TYPE;
    public final Name PACKAGE;
    public final Name SOURCE;
    public final Name CLASS;
    public final Name RUNTIME;
    public final Name Array;
    public final Name Method;
    public final Name Bound;
    public final Name clone;
    public final Name getComponentType;
    public final Name getClassLoader;
    public final Name initCause;
    public final Name values;
    public final Name iterator;
    public final Name hasNext;
    public final Name next;
    public final Name AnnotationDefault;
    public final Name ordinal;
    public final Name equals;
    public final Name hashCode;
    public final Name compareTo;
    public final Name getDeclaringClass;
    public final Name ex;
    public final Name finalize;
    public final Name java_lang_AutoCloseable;
    public final Name close;

    public final Name.Table table;

    public Names(Context context) {
        Options options = Options.instance(context);
        table = createTable(options);

        slash = fromString("/");
        hyphen = fromString("-");
        T = fromString("T");
        slashequals = fromString("/=");
        deprecated = fromString("deprecated");

        init = fromString("<init>");
        clinit = fromString("<clinit>");
        error = fromString("<error>");
        any = fromString("<any>");
        empty = fromString("");
        one = fromString("1");
        period = fromString(".");
        comma = fromString(",");
        semicolon = fromString(";");
        asterisk = fromString("*");
        _this = fromString("this");
        _super = fromString("super");
        _default = fromString("default");

        _class = fromString("class");
        java_lang = fromString("java.lang");
        java_lang_Object = fromString("java.lang.Object");
        java_lang_Class = fromString("java.lang.Class");
        java_lang_Cloneable = fromString("java.lang.Cloneable");
        java_io_Serializable = fromString("java.io.Serializable");
        java_lang_Enum = fromString("java.lang.Enum");
        java_dyn_MethodHandle = fromString("java.dyn.MethodHandle");
        java_dyn_InvokeDynamic = fromString("java.dyn.InvokeDynamic");
        package_info = fromString("package-info");
        serialVersionUID = fromString("serialVersionUID");
        ConstantValue = fromString("ConstantValue");
        LineNumberTable = fromString("LineNumberTable");
        LocalVariableTable = fromString("LocalVariableTable");
        LocalVariableTypeTable = fromString("LocalVariableTypeTable");
        CharacterRangeTable = fromString("CharacterRangeTable");
        StackMap = fromString("StackMap");
        StackMapTable = fromString("StackMapTable");
        SourceID = fromString("SourceID");
        CompilationID = fromString("CompilationID");
        Code = fromString("Code");
        Exceptions = fromString("Exceptions");
        SourceFile = fromString("SourceFile");
        InnerClasses = fromString("InnerClasses");
        Synthetic = fromString("Synthetic");
        Bridge = fromString("Bridge");
        Deprecated = fromString("Deprecated");
        Enum = fromString("Enum");
        _name = fromString("name");
        Signature = fromString("Signature");
        Varargs = fromString("Varargs");
        Annotation = fromString("Annotation");
        RuntimeVisibleAnnotations = fromString("RuntimeVisibleAnnotations");
        RuntimeInvisibleAnnotations = fromString("RuntimeInvisibleAnnotations");
        RuntimeVisibleTypeAnnotations = fromString("RuntimeVisibleTypeAnnotations");
        RuntimeInvisibleTypeAnnotations = fromString("RuntimeInvisibleTypeAnnotations");
        RuntimeVisibleParameterAnnotations = fromString("RuntimeVisibleParameterAnnotations");
        RuntimeInvisibleParameterAnnotations = fromString("RuntimeInvisibleParameterAnnotations");
        PolymorphicSignature = fromString("PolymorphicSignature");
        Value = fromString("Value");
        EnclosingMethod = fromString("EnclosingMethod");

        desiredAssertionStatus = fromString("desiredAssertionStatus");

        append = fromString("append");
        family = fromString("family");
        forName = fromString("forName");
        toString = fromString("toString");
        length = fromString("length");
        valueOf = fromString("valueOf");
        value = fromString("value");
        getMessage = fromString("getMessage");
        getClass = fromString("getClass");

        TYPE = fromString("TYPE");
        TYPE_USE = fromString("TYPE_USE");
        TYPE_PARAMETER = fromString("TYPE_PARAMETER");
        FIELD = fromString("FIELD");
        METHOD = fromString("METHOD");
        PARAMETER = fromString("PARAMETER");
        CONSTRUCTOR = fromString("CONSTRUCTOR");
        LOCAL_VARIABLE = fromString("LOCAL_VARIABLE");
        ANNOTATION_TYPE = fromString("ANNOTATION_TYPE");
        PACKAGE = fromString("PACKAGE");

        SOURCE = fromString("SOURCE");
        CLASS = fromString("CLASS");
        RUNTIME = fromString("RUNTIME");

        Array = fromString("Array");
        Method = fromString("Method");
        Bound = fromString("Bound");
        clone = fromString("clone");
        getComponentType = fromString("getComponentType");
        getClassLoader = fromString("getClassLoader");
        initCause = fromString("initCause");
        values = fromString("values");
        iterator = fromString("iterator");
        hasNext = fromString("hasNext");
        next = fromString("next");
        AnnotationDefault = fromString("AnnotationDefault");
        ordinal = fromString("ordinal");
        equals = fromString("equals");
        hashCode = fromString("hashCode");
        compareTo = fromString("compareTo");
        getDeclaringClass = fromString("getDeclaringClass");
        ex = fromString("ex");
        finalize = fromString("finalize");

        java_lang_AutoCloseable = fromString("java.lang.AutoCloseable");
        close = fromString("close");
    }

    protected Name.Table createTable(Options options) {
        boolean useUnsharedTable = options.get("useUnsharedTable") != null;
        if (useUnsharedTable)
            return new UnsharedNameTable(this);
        else
            return new SharedNameTable(this);
    }

    public void dispose() {
        table.dispose();
    }

    public Name fromChars(char[] cs, int start, int len) {
        return table.fromChars(cs, start, len);
    }

    public Name fromString(String s) {
        return table.fromString(s);
    }

    public Name fromUtf(byte[] cs) {
        return table.fromUtf(cs);
    }

    public Name fromUtf(byte[] cs, int start, int len) {
        return table.fromUtf(cs, start, len);
    }
}
