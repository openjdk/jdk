/*
 * Copyright 1999-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javac.jvm;

import java.io.*;
import java.util.Set;
import java.util.HashSet;

import javax.tools.JavaFileManager;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.util.*;

import static com.sun.tools.javac.code.BoundKind.*;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTags.*;
import static com.sun.tools.javac.jvm.UninitializedType.*;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

/** This class provides operations to map an internal symbol table graph
 *  rooted in a ClassSymbol into a classfile.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ClassWriter extends ClassFile {
    protected static final Context.Key<ClassWriter> classWriterKey =
        new Context.Key<ClassWriter>();

    private final Symtab syms;

    private final Options options;

    /** Switch: debugging output for JSR 308-related operations.
     */
    private boolean debugJSR308;

    /** Switch: verbose output.
     */
    private boolean verbose;

    /** Switch: scrable private names.
     */
    private boolean scramble;

    /** Switch: scrable private names.
     */
    private boolean scrambleAll;

    /** Switch: retrofit mode.
     */
    private boolean retrofit;

    /** Switch: emit source file attribute.
     */
    private boolean emitSourceFile;

    /** Switch: generate CharacterRangeTable attribute.
     */
    private boolean genCrt;

    /** Switch: describe the generated stackmap
     */
    boolean debugstackmap;

    /**
     * Target class version.
     */
    private Target target;

    /**
     * Source language version.
     */
    private Source source;

    /** Type utilities. */
    private Types types;

    /** The initial sizes of the data and constant pool buffers.
     *  sizes are increased when buffers get full.
     */
    static final int DATA_BUF_SIZE = 0x0fff0;
    static final int POOL_BUF_SIZE = 0x1fff0;

    /** An output buffer for member info.
     */
    ByteBuffer databuf = new ByteBuffer(DATA_BUF_SIZE);

    /** An output buffer for the constant pool.
     */
    ByteBuffer poolbuf = new ByteBuffer(POOL_BUF_SIZE);

    /** An output buffer for type signatures.
     */
    ByteBuffer sigbuf = new ByteBuffer();

    /** The constant pool.
     */
    Pool pool;

    /** The inner classes to be written, as a set.
     */
    Set<ClassSymbol> innerClasses;

    /** The inner classes to be written, as a queue where
     *  enclosing classes come first.
     */
    ListBuffer<ClassSymbol> innerClassesQueue;

    /** The log to use for verbose output.
     */
    private final Log log;

    /** The name table. */
    private final Names names;

    /** Access to files. */
    private final JavaFileManager fileManager;

    /** The tags and constants used in compressed stackmap. */
    static final int SAME_FRAME_SIZE = 64;
    static final int SAME_LOCALS_1_STACK_ITEM_EXTENDED = 247;
    static final int SAME_FRAME_EXTENDED = 251;
    static final int FULL_FRAME = 255;
    static final int MAX_LOCAL_LENGTH_DIFF = 4;

    /** Get the ClassWriter instance for this context. */
    public static ClassWriter instance(Context context) {
        ClassWriter instance = context.get(classWriterKey);
        if (instance == null)
            instance = new ClassWriter(context);
        return instance;
    }

    /** Construct a class writer, given an options table.
     */
    private ClassWriter(Context context) {
        context.put(classWriterKey, this);

        log = Log.instance(context);
        names = Names.instance(context);
        syms = Symtab.instance(context);
        options = Options.instance(context);
        target = Target.instance(context);
        source = Source.instance(context);
        types = Types.instance(context);
        fileManager = context.get(JavaFileManager.class);

        debugJSR308    = options.get("TA:writer") != null;
        verbose        = options.get("-verbose")     != null;
        scramble       = options.get("-scramble")    != null;
        scrambleAll    = options.get("-scrambleAll") != null;
        retrofit       = options.get("-retrofit") != null;
        genCrt         = options.get("-Xjcov") != null;
        debugstackmap  = options.get("debugstackmap") != null;

        emitSourceFile = options.get("-g:")==null || options.get("-g:source")!=null;

        String dumpModFlags = options.get("dumpmodifiers");
        dumpClassModifiers =
            (dumpModFlags != null && dumpModFlags.indexOf('c') != -1);
        dumpFieldModifiers =
            (dumpModFlags != null && dumpModFlags.indexOf('f') != -1);
        dumpInnerClassModifiers =
            (dumpModFlags != null && dumpModFlags.indexOf('i') != -1);
        dumpMethodModifiers =
            (dumpModFlags != null && dumpModFlags.indexOf('m') != -1);
    }

/******************************************************************
 * Diagnostics: dump generated class names and modifiers
 ******************************************************************/

    /** Value of option 'dumpmodifiers' is a string
     *  indicating which modifiers should be dumped for debugging:
     *    'c' -- classes
     *    'f' -- fields
     *    'i' -- innerclass attributes
     *    'm' -- methods
     *  For example, to dump everything:
     *    javac -XDdumpmodifiers=cifm MyProg.java
     */
    private final boolean dumpClassModifiers; // -XDdumpmodifiers=c
    private final boolean dumpFieldModifiers; // -XDdumpmodifiers=f
    private final boolean dumpInnerClassModifiers; // -XDdumpmodifiers=i
    private final boolean dumpMethodModifiers; // -XDdumpmodifiers=m


    /** Return flags as a string, separated by " ".
     */
    public static String flagNames(long flags) {
        StringBuffer sbuf = new StringBuffer();
        int i = 0;
        long f = flags & StandardFlags;
        while (f != 0) {
            if ((f & 1) != 0) sbuf.append(" " + flagName[i]);
            f = f >> 1;
            i++;
        }
        return sbuf.toString();
    }
    //where
        private final static String[] flagName = {
            "PUBLIC", "PRIVATE", "PROTECTED", "STATIC", "FINAL",
            "SUPER", "VOLATILE", "TRANSIENT", "NATIVE", "INTERFACE",
            "ABSTRACT", "STRICTFP"};

/******************************************************************
 * Output routines
 ******************************************************************/

    /** Write a character into given byte buffer;
     *  byte buffer will not be grown.
     */
    void putChar(ByteBuffer buf, int op, int x) {
        buf.elems[op  ] = (byte)((x >>  8) & 0xFF);
        buf.elems[op+1] = (byte)((x      ) & 0xFF);
    }

    /** Write an integer into given byte buffer;
     *  byte buffer will not be grown.
     */
    void putInt(ByteBuffer buf, int adr, int x) {
        buf.elems[adr  ] = (byte)((x >> 24) & 0xFF);
        buf.elems[adr+1] = (byte)((x >> 16) & 0xFF);
        buf.elems[adr+2] = (byte)((x >>  8) & 0xFF);
        buf.elems[adr+3] = (byte)((x      ) & 0xFF);
    }

/******************************************************************
 * Signature Generation
 ******************************************************************/

    /** Assemble signature of given type in string buffer.
     */
    void assembleSig(Type type) {
        switch (type.tag) {
        case BYTE:
            sigbuf.appendByte('B');
            break;
        case SHORT:
            sigbuf.appendByte('S');
            break;
        case CHAR:
            sigbuf.appendByte('C');
            break;
        case INT:
            sigbuf.appendByte('I');
            break;
        case LONG:
            sigbuf.appendByte('J');
            break;
        case FLOAT:
            sigbuf.appendByte('F');
            break;
        case DOUBLE:
            sigbuf.appendByte('D');
            break;
        case BOOLEAN:
            sigbuf.appendByte('Z');
            break;
        case VOID:
            sigbuf.appendByte('V');
            break;
        case CLASS:
            sigbuf.appendByte('L');
            assembleClassSig(type);
            sigbuf.appendByte(';');
            break;
        case ARRAY:
            ArrayType at = (ArrayType)type;
            sigbuf.appendByte('[');
            assembleSig(at.elemtype);
            break;
        case METHOD:
            MethodType mt = (MethodType)type;
            sigbuf.appendByte('(');
            assembleSig(mt.argtypes);
            sigbuf.appendByte(')');
            assembleSig(mt.restype);
            if (hasTypeVar(mt.thrown)) {
                for (List<Type> l = mt.thrown; l.nonEmpty(); l = l.tail) {
                    sigbuf.appendByte('^');
                    assembleSig(l.head);
                }
            }
            break;
        case WILDCARD: {
            WildcardType ta = (WildcardType) type;
            switch (ta.kind) {
            case SUPER:
                sigbuf.appendByte('-');
                assembleSig(ta.type);
                break;
            case EXTENDS:
                sigbuf.appendByte('+');
                assembleSig(ta.type);
                break;
            case UNBOUND:
                sigbuf.appendByte('*');
                break;
            default:
                throw new AssertionError(ta.kind);
            }
            break;
        }
        case TYPEVAR:
            sigbuf.appendByte('T');
            sigbuf.appendName(type.tsym.name);
            sigbuf.appendByte(';');
            break;
        case FORALL:
            ForAll ft = (ForAll)type;
            assembleParamsSig(ft.tvars);
            assembleSig(ft.qtype);
            break;
        case UNINITIALIZED_THIS:
        case UNINITIALIZED_OBJECT:
            // we don't yet have a spec for uninitialized types in the
            // local variable table
            assembleSig(types.erasure(((UninitializedType)type).qtype));
            break;
        default:
            throw new AssertionError("typeSig " + type.tag);
        }
    }

    boolean hasTypeVar(List<Type> l) {
        while (l.nonEmpty()) {
            if (l.head.tag == TypeTags.TYPEVAR) return true;
            l = l.tail;
        }
        return false;
    }

    void assembleClassSig(Type type) {
        ClassType ct = (ClassType)type;
        ClassSymbol c = (ClassSymbol)ct.tsym;
        enterInner(c);
        Type outer = ct.getEnclosingType();
        if (outer.allparams().nonEmpty()) {
            boolean rawOuter =
                c.owner.kind == MTH || // either a local class
                c.name == names.empty; // or anonymous
            assembleClassSig(rawOuter
                             ? types.erasure(outer)
                             : outer);
            sigbuf.appendByte('.');
            assert c.flatname.startsWith(c.owner.enclClass().flatname);
            sigbuf.appendName(rawOuter
                              ? c.flatname.subName(c.owner.enclClass().flatname.getByteLength()+1,c.flatname.getByteLength())
                              : c.name);
        } else {
            sigbuf.appendBytes(externalize(c.flatname));
        }
        if (ct.getTypeArguments().nonEmpty()) {
            sigbuf.appendByte('<');
            assembleSig(ct.getTypeArguments());
            sigbuf.appendByte('>');
        }
    }


    void assembleSig(List<Type> types) {
        for (List<Type> ts = types; ts.nonEmpty(); ts = ts.tail)
            assembleSig(ts.head);
    }

    void assembleParamsSig(List<Type> typarams) {
        sigbuf.appendByte('<');
        for (List<Type> ts = typarams; ts.nonEmpty(); ts = ts.tail) {
            TypeVar tvar = (TypeVar)ts.head;
            sigbuf.appendName(tvar.tsym.name);
            List<Type> bounds = types.getBounds(tvar);
            if ((bounds.head.tsym.flags() & INTERFACE) != 0) {
                sigbuf.appendByte(':');
            }
            for (List<Type> l = bounds; l.nonEmpty(); l = l.tail) {
                sigbuf.appendByte(':');
                assembleSig(l.head);
            }
        }
        sigbuf.appendByte('>');
    }

    /** Return signature of given type
     */
    Name typeSig(Type type) {
        assert sigbuf.length == 0;
        //- System.out.println(" ? " + type);
        assembleSig(type);
        Name n = sigbuf.toName(names);
        sigbuf.reset();
        //- System.out.println("   " + n);
        return n;
    }

    /** Given a type t, return the extended class name of its erasure in
     *  external representation.
     */
    public Name xClassName(Type t) {
        if (t.tag == CLASS) {
            return names.fromUtf(externalize(t.tsym.flatName()));
        } else if (t.tag == ARRAY) {
            return typeSig(types.erasure(t));
        } else {
            throw new AssertionError("xClassName");
        }
    }

/******************************************************************
 * Writing the Constant Pool
 ******************************************************************/

    /** Thrown when the constant pool is over full.
     */
    public static class PoolOverflow extends Exception {
        private static final long serialVersionUID = 0;
        public PoolOverflow() {}
    }
    public static class StringOverflow extends Exception {
        private static final long serialVersionUID = 0;
        public final String value;
        public StringOverflow(String s) {
            value = s;
        }
    }

    /** Write constant pool to pool buffer.
     *  Note: during writing, constant pool
     *  might grow since some parts of constants still need to be entered.
     */
    void writePool(Pool pool) throws PoolOverflow, StringOverflow {
        int poolCountIdx = poolbuf.length;
        poolbuf.appendChar(0);
        int i = 1;
        while (i < pool.pp) {
            Object value = pool.pool[i];
            assert value != null;
            if (value instanceof Pool.Method)
                value = ((Pool.Method)value).m;
            else if (value instanceof Pool.Variable)
                value = ((Pool.Variable)value).v;

            if (value instanceof MethodSymbol) {
                MethodSymbol m = (MethodSymbol)value;
                poolbuf.appendByte((m.owner.flags() & INTERFACE) != 0
                          ? CONSTANT_InterfaceMethodref
                          : CONSTANT_Methodref);
                poolbuf.appendChar(pool.put(m.owner));
                poolbuf.appendChar(pool.put(nameType(m)));
            } else if (value instanceof VarSymbol) {
                VarSymbol v = (VarSymbol)value;
                poolbuf.appendByte(CONSTANT_Fieldref);
                poolbuf.appendChar(pool.put(v.owner));
                poolbuf.appendChar(pool.put(nameType(v)));
            } else if (value instanceof Name) {
                poolbuf.appendByte(CONSTANT_Utf8);
                byte[] bs = ((Name)value).toUtf();
                poolbuf.appendChar(bs.length);
                poolbuf.appendBytes(bs, 0, bs.length);
                if (bs.length > Pool.MAX_STRING_LENGTH)
                    throw new StringOverflow(value.toString());
            } else if (value instanceof ClassSymbol) {
                ClassSymbol c = (ClassSymbol)value;
                if (c.owner.kind == TYP) pool.put(c.owner);
                poolbuf.appendByte(CONSTANT_Class);
                if (c.type.tag == ARRAY) {
                    poolbuf.appendChar(pool.put(typeSig(c.type)));
                } else {
                    poolbuf.appendChar(pool.put(names.fromUtf(externalize(c.flatname))));
                    enterInner(c);
                }
            } else if (value instanceof NameAndType) {
                NameAndType nt = (NameAndType)value;
                poolbuf.appendByte(CONSTANT_NameandType);
                poolbuf.appendChar(pool.put(nt.name));
                poolbuf.appendChar(pool.put(typeSig(nt.type)));
            } else if (value instanceof Integer) {
                poolbuf.appendByte(CONSTANT_Integer);
                poolbuf.appendInt(((Integer)value).intValue());
            } else if (value instanceof Long) {
                poolbuf.appendByte(CONSTANT_Long);
                poolbuf.appendLong(((Long)value).longValue());
                i++;
            } else if (value instanceof Float) {
                poolbuf.appendByte(CONSTANT_Float);
                poolbuf.appendFloat(((Float)value).floatValue());
            } else if (value instanceof Double) {
                poolbuf.appendByte(CONSTANT_Double);
                poolbuf.appendDouble(((Double)value).doubleValue());
                i++;
            } else if (value instanceof String) {
                poolbuf.appendByte(CONSTANT_String);
                poolbuf.appendChar(pool.put(names.fromString((String)value)));
            } else if (value instanceof Type) {
                Type type = (Type)value;
                if (type.tag == CLASS) enterInner((ClassSymbol)type.tsym);
                poolbuf.appendByte(CONSTANT_Class);
                poolbuf.appendChar(pool.put(xClassName(type)));
            } else {
                assert false : "writePool " + value;
            }
            i++;
        }
        if (pool.pp > Pool.MAX_ENTRIES)
            throw new PoolOverflow();
        putChar(poolbuf, poolCountIdx, pool.pp);
    }

    /** Given a field, return its name.
     */
    Name fieldName(Symbol sym) {
        if (scramble && (sym.flags() & PRIVATE) != 0 ||
            scrambleAll && (sym.flags() & (PROTECTED | PUBLIC)) == 0)
            return names.fromString("_$" + sym.name.getIndex());
        else
            return sym.name;
    }

    /** Given a symbol, return its name-and-type.
     */
    NameAndType nameType(Symbol sym) {
        return new NameAndType(fieldName(sym),
                               retrofit
                               ? sym.erasure(types)
                               : sym.externalType(types));
        // if we retrofit, then the NameAndType has been read in as is
        // and no change is necessary. If we compile normally, the
        // NameAndType is generated from a symbol reference, and the
        // adjustment of adding an additional this$n parameter needs to be made.
    }

/******************************************************************
 * Writing Attributes
 ******************************************************************/

    /** Write header for an attribute to data buffer and return
     *  position past attribute length index.
     */
    int writeAttr(Name attrName) {
        databuf.appendChar(pool.put(attrName));
        databuf.appendInt(0);
        return databuf.length;
    }

    /** Fill in attribute length.
     */
    void endAttr(int index) {
        putInt(databuf, index - 4, databuf.length - index);
    }

    /** Leave space for attribute count and return index for
     *  number of attributes field.
     */
    int beginAttrs() {
        databuf.appendChar(0);
        return databuf.length;
    }

    /** Fill in number of attributes.
     */
    void endAttrs(int index, int count) {
        putChar(databuf, index - 2, count);
    }

    /** Write the EnclosingMethod attribute if needed.
     *  Returns the number of attributes written (0 or 1).
     */
    int writeEnclosingMethodAttribute(ClassSymbol c) {
        if (!target.hasEnclosingMethodAttribute() ||
            c.owner.kind != MTH && // neither a local class
            c.name != names.empty) // nor anonymous
            return 0;

        int alenIdx = writeAttr(names.EnclosingMethod);
        ClassSymbol enclClass = c.owner.enclClass();
        MethodSymbol enclMethod =
            (c.owner.type == null // local to init block
             || c.owner.kind != MTH) // or member init
            ? null
            : (MethodSymbol)c.owner;
        databuf.appendChar(pool.put(enclClass));
        databuf.appendChar(enclMethod == null ? 0 : pool.put(nameType(c.owner)));
        endAttr(alenIdx);
        return 1;
    }

    /** Write flag attributes; return number of attributes written.
     */
    int writeFlagAttrs(long flags) {
        int acount = 0;
        if ((flags & DEPRECATED) != 0) {
            int alenIdx = writeAttr(names.Deprecated);
            endAttr(alenIdx);
            acount++;
        }
        if ((flags & ENUM) != 0 && !target.useEnumFlag()) {
            int alenIdx = writeAttr(names.Enum);
            endAttr(alenIdx);
            acount++;
        }
        if ((flags & SYNTHETIC) != 0 && !target.useSyntheticFlag()) {
            int alenIdx = writeAttr(names.Synthetic);
            endAttr(alenIdx);
            acount++;
        }
        if ((flags & BRIDGE) != 0 && !target.useBridgeFlag()) {
            int alenIdx = writeAttr(names.Bridge);
            endAttr(alenIdx);
            acount++;
        }
        if ((flags & VARARGS) != 0 && !target.useVarargsFlag()) {
            int alenIdx = writeAttr(names.Varargs);
            endAttr(alenIdx);
            acount++;
        }
        if ((flags & ANNOTATION) != 0 && !target.useAnnotationFlag()) {
            int alenIdx = writeAttr(names.Annotation);
            endAttr(alenIdx);
            acount++;
        }
        return acount;
    }

    /** Write member (field or method) attributes;
     *  return number of attributes written.
     */
    int writeMemberAttrs(Symbol sym) {
        int acount = writeFlagAttrs(sym.flags());
        long flags = sym.flags();
        if (source.allowGenerics() &&
            (flags & (SYNTHETIC|BRIDGE)) != SYNTHETIC &&
            (flags & ANONCONSTR) == 0 &&
            (!types.isSameType(sym.type, sym.erasure(types)) ||
             hasTypeVar(sym.type.getThrownTypes()))) {
            // note that a local class with captured variables
            // will get a signature attribute
            int alenIdx = writeAttr(names.Signature);
            databuf.appendChar(pool.put(typeSig(sym.type)));
            endAttr(alenIdx);
            acount++;
        }
        acount += writeJavaAnnotations(sym.getAnnotationMirrors());
        acount += writeTypeAnnotations(sym.typeAnnotations);
        return acount;
    }

    /** Write method parameter annotations;
     *  return number of attributes written.
     */
    int writeParameterAttrs(MethodSymbol m) {
        boolean hasVisible = false;
        boolean hasInvisible = false;
        if (m.params != null) for (VarSymbol s : m.params) {
            for (Attribute.Compound a : s.getAnnotationMirrors()) {
                switch (getRetention(a.type.tsym)) {
                case SOURCE: break;
                case CLASS: hasInvisible = true; break;
                case RUNTIME: hasVisible = true; break;
                default: ;// /* fail soft */ throw new AssertionError(vis);
                }
            }
        }

        int attrCount = 0;
        if (hasVisible) {
            int attrIndex = writeAttr(names.RuntimeVisibleParameterAnnotations);
            databuf.appendByte(m.params.length());
            for (VarSymbol s : m.params) {
                ListBuffer<Attribute.Compound> buf = new ListBuffer<Attribute.Compound>();
                for (Attribute.Compound a : s.getAnnotationMirrors())
                    if (getRetention(a.type.tsym) == RetentionPolicy.RUNTIME)
                        buf.append(a);
                databuf.appendChar(buf.length());
                for (Attribute.Compound a : buf)
                    writeCompoundAttribute(a);
            }
            endAttr(attrIndex);
            attrCount++;
        }
        if (hasInvisible) {
            int attrIndex = writeAttr(names.RuntimeInvisibleParameterAnnotations);
            databuf.appendByte(m.params.length());
            for (VarSymbol s : m.params) {
                ListBuffer<Attribute.Compound> buf = new ListBuffer<Attribute.Compound>();
                for (Attribute.Compound a : s.getAnnotationMirrors())
                    if (getRetention(a.type.tsym) == RetentionPolicy.CLASS)
                        buf.append(a);
                databuf.appendChar(buf.length());
                for (Attribute.Compound a : buf)
                    writeCompoundAttribute(a);
            }
            endAttr(attrIndex);
            attrCount++;
        }
        return attrCount;
    }

/**********************************************************************
 * Writing Java-language annotations (aka metadata, attributes)
 **********************************************************************/

    /** Write Java-language annotations; return number of JVM
     *  attributes written (zero or one).
     */
    int writeJavaAnnotations(List<Attribute.Compound> attrs) {
        if (attrs.isEmpty()) return 0;
        ListBuffer<Attribute.Compound> visibles = new ListBuffer<Attribute.Compound>();
        ListBuffer<Attribute.Compound> invisibles = new ListBuffer<Attribute.Compound>();
        for (Attribute.Compound a : attrs) {
            switch (getRetention(a.type.tsym)) {
            case SOURCE: break;
            case CLASS: invisibles.append(a); break;
            case RUNTIME: visibles.append(a); break;
            default: ;// /* fail soft */ throw new AssertionError(vis);
            }
        }

        int attrCount = 0;
        if (visibles.length() != 0) {
            int attrIndex = writeAttr(names.RuntimeVisibleAnnotations);
            databuf.appendChar(visibles.length());
            for (Attribute.Compound a : visibles)
                writeCompoundAttribute(a);
            endAttr(attrIndex);
            attrCount++;
        }
        if (invisibles.length() != 0) {
            int attrIndex = writeAttr(names.RuntimeInvisibleAnnotations);
            databuf.appendChar(invisibles.length());
            for (Attribute.Compound a : invisibles)
                writeCompoundAttribute(a);
            endAttr(attrIndex);
            attrCount++;
        }
        return attrCount;
    }

    int writeTypeAnnotations(List<Attribute.TypeCompound> typeAnnos) {
        if (typeAnnos.isEmpty()) return 0;

        ListBuffer<Attribute.TypeCompound> visibles = ListBuffer.lb();
        ListBuffer<Attribute.TypeCompound> invisibles = ListBuffer.lb();

        for (Attribute.TypeCompound tc : typeAnnos) {
            if (tc.position.type == TargetType.UNKNOWN
                || !tc.position.emitToClassfile())
                continue;
            switch (getRetention(tc.type.tsym)) {
            case SOURCE: break;
            case CLASS: invisibles.append(tc); break;
            case RUNTIME: visibles.append(tc); break;
            default: ;// /* fail soft */ throw new AssertionError(vis);
            }
        }

        int attrCount = 0;
        if (visibles.length() != 0) {
            int attrIndex = writeAttr(names.RuntimeVisibleTypeAnnotations);
            databuf.appendChar(visibles.length());
            for (Attribute.TypeCompound p : visibles)
                writeTypeAnnotation(p);
            endAttr(attrIndex);
            attrCount++;
        }

        if (invisibles.length() != 0) {
            int attrIndex = writeAttr(names.RuntimeInvisibleTypeAnnotations);
            databuf.appendChar(invisibles.length());
            for (Attribute.TypeCompound p : invisibles)
                writeTypeAnnotation(p);
            endAttr(attrIndex);
            attrCount++;
        }

        return attrCount;
    }

    /** A mirror of java.lang.annotation.RetentionPolicy. */
    enum RetentionPolicy {
        SOURCE,
        CLASS,
        RUNTIME
    }

    RetentionPolicy getRetention(TypeSymbol annotationType) {
        RetentionPolicy vis = RetentionPolicy.CLASS; // the default
        Attribute.Compound c = annotationType.attribute(syms.retentionType.tsym);
        if (c != null) {
            Attribute value = c.member(names.value);
            if (value != null && value instanceof Attribute.Enum) {
                Name levelName = ((Attribute.Enum)value).value.name;
                if (levelName == names.SOURCE) vis = RetentionPolicy.SOURCE;
                else if (levelName == names.CLASS) vis = RetentionPolicy.CLASS;
                else if (levelName == names.RUNTIME) vis = RetentionPolicy.RUNTIME;
                else ;// /* fail soft */ throw new AssertionError(levelName);
            }
        }
        return vis;
    }

    /** A visitor to write an attribute including its leading
     *  single-character marker.
     */
    class AttributeWriter implements Attribute.Visitor {
        public void visitConstant(Attribute.Constant _value) {
            Object value = _value.value;
            switch (_value.type.tag) {
            case BYTE:
                databuf.appendByte('B');
                break;
            case CHAR:
                databuf.appendByte('C');
                break;
            case SHORT:
                databuf.appendByte('S');
                break;
            case INT:
                databuf.appendByte('I');
                break;
            case LONG:
                databuf.appendByte('J');
                break;
            case FLOAT:
                databuf.appendByte('F');
                break;
            case DOUBLE:
                databuf.appendByte('D');
                break;
            case BOOLEAN:
                databuf.appendByte('Z');
                break;
            case CLASS:
                assert value instanceof String;
                databuf.appendByte('s');
                value = names.fromString(value.toString()); // CONSTANT_Utf8
                break;
            default:
                throw new AssertionError(_value.type);
            }
            databuf.appendChar(pool.put(value));
        }
        public void visitEnum(Attribute.Enum e) {
            databuf.appendByte('e');
            databuf.appendChar(pool.put(typeSig(e.value.type)));
            databuf.appendChar(pool.put(e.value.name));
        }
        public void visitClass(Attribute.Class clazz) {
            databuf.appendByte('c');
            databuf.appendChar(pool.put(typeSig(clazz.type)));
        }
        public void visitCompound(Attribute.Compound compound) {
            databuf.appendByte('@');
            writeCompoundAttribute(compound);
        }
        public void visitError(Attribute.Error x) {
            throw new AssertionError(x);
        }
        public void visitArray(Attribute.Array array) {
            databuf.appendByte('[');
            databuf.appendChar(array.values.length);
            for (Attribute a : array.values) {
                a.accept(this);
            }
        }
    }
    AttributeWriter awriter = new AttributeWriter();

    /** Write a compound attribute excluding the '@' marker. */
    void writeCompoundAttribute(Attribute.Compound c) {
        databuf.appendChar(pool.put(typeSig(c.type)));
        databuf.appendChar(c.values.length());
        for (Pair<Symbol.MethodSymbol,Attribute> p : c.values) {
            databuf.appendChar(pool.put(p.fst.name));
            p.snd.accept(awriter);
        }
    }

    void writeTypeAnnotation(Attribute.TypeCompound c) {
        if (debugJSR308)
            System.out.println("TA: writing " + c + " at " + c.position
                    + " in " + log.currentSourceFile());
        writeCompoundAttribute(c);
        writePosition(c.position);
    }

    void writePosition(TypeAnnotationPosition p) {
        databuf.appendByte(p.type.targetTypeValue());
        switch (p.type) {
        // type case
        case TYPECAST:
        case TYPECAST_GENERIC_OR_ARRAY:
        // object creation
        case INSTANCEOF:
        case INSTANCEOF_GENERIC_OR_ARRAY:
        // new expression
        case NEW:
        case NEW_GENERIC_OR_ARRAY:
            databuf.appendChar(p.offset);
            break;
         // local variable
        case LOCAL_VARIABLE:
        case LOCAL_VARIABLE_GENERIC_OR_ARRAY:
            databuf.appendChar(p.lvarOffset.length);  // for table length
            for (int i = 0; i < p.lvarOffset.length; ++i) {
                databuf.appendChar(p.lvarOffset[i]);
                databuf.appendChar(p.lvarLength[i]);
                databuf.appendChar(p.lvarIndex[i]);
            }
            break;
         // method receiver
        case METHOD_RECEIVER:
            // Do nothing
            break;
        // type parameters
        case CLASS_TYPE_PARAMETER:
        case METHOD_TYPE_PARAMETER:
            databuf.appendByte(p.parameter_index);
            break;
        // type parameters bounds
        case CLASS_TYPE_PARAMETER_BOUND:
        case CLASS_TYPE_PARAMETER_BOUND_GENERIC_OR_ARRAY:
        case METHOD_TYPE_PARAMETER_BOUND:
        case METHOD_TYPE_PARAMETER_BOUND_GENERIC_OR_ARRAY:
            databuf.appendByte(p.parameter_index);
            databuf.appendByte(p.bound_index);
            break;
         // wildcards
        case WILDCARD_BOUND:
        case WILDCARD_BOUND_GENERIC_OR_ARRAY:
            writePosition(p.wildcard_position);
            break;
         // Class extends and implements clauses
        case CLASS_EXTENDS:
        case CLASS_EXTENDS_GENERIC_OR_ARRAY:
            databuf.appendByte(p.type_index);
            break;
        // throws
        case THROWS:
            databuf.appendByte(p.type_index);
            break;
        case CLASS_LITERAL:
        case CLASS_LITERAL_GENERIC_OR_ARRAY:
            databuf.appendChar(p.offset);
            break;
        // method parameter: not specified
        case METHOD_PARAMETER_GENERIC_OR_ARRAY:
            databuf.appendByte(p.parameter_index);
            break;
        // method type argument: wasn't specified
        case NEW_TYPE_ARGUMENT:
        case NEW_TYPE_ARGUMENT_GENERIC_OR_ARRAY:
        case METHOD_TYPE_ARGUMENT:
        case METHOD_TYPE_ARGUMENT_GENERIC_OR_ARRAY:
            databuf.appendChar(p.offset);
            databuf.appendByte(p.type_index);
            break;
        // We don't need to worry abut these
        case METHOD_RETURN_GENERIC_OR_ARRAY:
        case FIELD_GENERIC_OR_ARRAY:
            break;
        case UNKNOWN:
            break;
        default:
            throw new AssertionError("unknown position: " + p);
        }

        // Append location data for generics/arrays.
        if (p.type.hasLocation()) {
            databuf.appendChar(p.location.size());
            for (int i : p.location)
                databuf.appendByte((byte)i);
        }
    }

/**********************************************************************
 * Writing Objects
 **********************************************************************/

    /** Enter an inner class into the `innerClasses' set/queue.
     */
    void enterInner(ClassSymbol c) {
        assert !c.type.isCompound();
        try {
            c.complete();
        } catch (CompletionFailure ex) {
            System.err.println("error: " + c + ": " + ex.getMessage());
            throw ex;
        }
        if (c.type.tag != CLASS) return; // arrays
        if (pool != null && // pool might be null if called from xClassName
            c.owner.kind != PCK &&
            (innerClasses == null || !innerClasses.contains(c))) {
//          log.errWriter.println("enter inner " + c);//DEBUG
            if (c.owner.kind == TYP) enterInner((ClassSymbol)c.owner);
            pool.put(c);
            pool.put(c.name);
            if (innerClasses == null) {
                innerClasses = new HashSet<ClassSymbol>();
                innerClassesQueue = new ListBuffer<ClassSymbol>();
                pool.put(names.InnerClasses);
            }
            innerClasses.add(c);
            innerClassesQueue.append(c);
        }
    }

    /** Write "inner classes" attribute.
     */
    void writeInnerClasses() {
        int alenIdx = writeAttr(names.InnerClasses);
        databuf.appendChar(innerClassesQueue.length());
        for (List<ClassSymbol> l = innerClassesQueue.toList();
             l.nonEmpty();
             l = l.tail) {
            ClassSymbol inner = l.head;
            char flags = (char) adjustFlags(inner.flags_field);
            if ((flags & INTERFACE) != 0) flags |= ABSTRACT; // Interfaces are always ABSTRACT
            if (inner.name.isEmpty()) flags &= ~FINAL; // Anonymous class: unset FINAL flag
            if (dumpInnerClassModifiers) {
                log.errWriter.println("INNERCLASS  " + inner.name);
                log.errWriter.println("---" + flagNames(flags));
            }
            databuf.appendChar(pool.get(inner));
            databuf.appendChar(
                inner.owner.kind == TYP ? pool.get(inner.owner) : 0);
            databuf.appendChar(
                !inner.name.isEmpty() ? pool.get(inner.name) : 0);
            databuf.appendChar(flags);
        }
        endAttr(alenIdx);
    }

    /** Write field symbol, entering all references into constant pool.
     */
    void writeField(VarSymbol v) {
        int flags = adjustFlags(v.flags());
        databuf.appendChar(flags);
        if (dumpFieldModifiers) {
            log.errWriter.println("FIELD  " + fieldName(v));
            log.errWriter.println("---" + flagNames(v.flags()));
        }
        databuf.appendChar(pool.put(fieldName(v)));
        databuf.appendChar(pool.put(typeSig(v.erasure(types))));
        int acountIdx = beginAttrs();
        int acount = 0;
        if (v.getConstValue() != null) {
            int alenIdx = writeAttr(names.ConstantValue);
            databuf.appendChar(pool.put(v.getConstValue()));
            endAttr(alenIdx);
            acount++;
        }
        acount += writeMemberAttrs(v);
        endAttrs(acountIdx, acount);
    }

    /** Write method symbol, entering all references into constant pool.
     */
    void writeMethod(MethodSymbol m) {
        int flags = adjustFlags(m.flags());
        databuf.appendChar(flags);
        if (dumpMethodModifiers) {
            log.errWriter.println("METHOD  " + fieldName(m));
            log.errWriter.println("---" + flagNames(m.flags()));
        }
        databuf.appendChar(pool.put(fieldName(m)));
        databuf.appendChar(pool.put(typeSig(m.externalType(types))));
        int acountIdx = beginAttrs();
        int acount = 0;
        if (m.code != null) {
            int alenIdx = writeAttr(names.Code);
            writeCode(m.code);
            m.code = null; // to conserve space
            endAttr(alenIdx);
            acount++;
        }
        List<Type> thrown = m.erasure(types).getThrownTypes();
        if (thrown.nonEmpty()) {
            int alenIdx = writeAttr(names.Exceptions);
            databuf.appendChar(thrown.length());
            for (List<Type> l = thrown; l.nonEmpty(); l = l.tail)
                databuf.appendChar(pool.put(l.head.tsym));
            endAttr(alenIdx);
            acount++;
        }
        if (m.defaultValue != null) {
            int alenIdx = writeAttr(names.AnnotationDefault);
            m.defaultValue.accept(awriter);
            endAttr(alenIdx);
            acount++;
        }
        acount += writeMemberAttrs(m);
        acount += writeParameterAttrs(m);
        endAttrs(acountIdx, acount);
    }

    /** Write code attribute of method.
     */
    void writeCode(Code code) {
        databuf.appendChar(code.max_stack);
        databuf.appendChar(code.max_locals);
        databuf.appendInt(code.cp);
        databuf.appendBytes(code.code, 0, code.cp);
        databuf.appendChar(code.catchInfo.length());
        for (List<char[]> l = code.catchInfo.toList();
             l.nonEmpty();
             l = l.tail) {
            for (int i = 0; i < l.head.length; i++)
                databuf.appendChar(l.head[i]);
        }
        int acountIdx = beginAttrs();
        int acount = 0;

        if (code.lineInfo.nonEmpty()) {
            int alenIdx = writeAttr(names.LineNumberTable);
            databuf.appendChar(code.lineInfo.length());
            for (List<char[]> l = code.lineInfo.reverse();
                 l.nonEmpty();
                 l = l.tail)
                for (int i = 0; i < l.head.length; i++)
                    databuf.appendChar(l.head[i]);
            endAttr(alenIdx);
            acount++;
        }

        if (genCrt && (code.crt != null)) {
            CRTable crt = code.crt;
            int alenIdx = writeAttr(names.CharacterRangeTable);
            int crtIdx = beginAttrs();
            int crtEntries = crt.writeCRT(databuf, code.lineMap, log);
            endAttrs(crtIdx, crtEntries);
            endAttr(alenIdx);
            acount++;
        }

        // counter for number of generic local variables
        int nGenericVars = 0;

        if (code.varBufferSize > 0) {
            int alenIdx = writeAttr(names.LocalVariableTable);
            databuf.appendChar(code.varBufferSize);

            for (int i=0; i<code.varBufferSize; i++) {
                Code.LocalVar var = code.varBuffer[i];

                // write variable info
                assert var.start_pc >= 0;
                assert var.start_pc <= code.cp;
                databuf.appendChar(var.start_pc);
                assert var.length >= 0;
                assert (var.start_pc + var.length) <= code.cp;
                databuf.appendChar(var.length);
                VarSymbol sym = var.sym;
                databuf.appendChar(pool.put(sym.name));
                Type vartype = sym.erasure(types);
                if (!types.isSameType(sym.type, vartype))
                    nGenericVars++;
                databuf.appendChar(pool.put(typeSig(vartype)));
                databuf.appendChar(var.reg);
            }
            endAttr(alenIdx);
            acount++;
        }

        if (nGenericVars > 0) {
            int alenIdx = writeAttr(names.LocalVariableTypeTable);
            databuf.appendChar(nGenericVars);
            int count = 0;

            for (int i=0; i<code.varBufferSize; i++) {
                Code.LocalVar var = code.varBuffer[i];
                VarSymbol sym = var.sym;
                if (types.isSameType(sym.type, sym.erasure(types)))
                    continue;
                count++;
                // write variable info
                databuf.appendChar(var.start_pc);
                databuf.appendChar(var.length);
                databuf.appendChar(pool.put(sym.name));
                databuf.appendChar(pool.put(typeSig(sym.type)));
                databuf.appendChar(var.reg);
            }
            assert count == nGenericVars;
            endAttr(alenIdx);
            acount++;
        }

        if (code.stackMapBufferSize > 0) {
            if (debugstackmap) System.out.println("Stack map for " + code.meth);
            int alenIdx = writeAttr(code.stackMap.getAttributeName(names));
            writeStackMap(code);
            endAttr(alenIdx);
            acount++;
        }
        endAttrs(acountIdx, acount);
    }

    void writeStackMap(Code code) {
        int nframes = code.stackMapBufferSize;
        if (debugstackmap) System.out.println(" nframes = " + nframes);
        databuf.appendChar(nframes);

        switch (code.stackMap) {
        case CLDC:
            for (int i=0; i<nframes; i++) {
                if (debugstackmap) System.out.print("  " + i + ":");
                Code.StackMapFrame frame = code.stackMapBuffer[i];

                // output PC
                if (debugstackmap) System.out.print(" pc=" + frame.pc);
                databuf.appendChar(frame.pc);

                // output locals
                int localCount = 0;
                for (int j=0; j<frame.locals.length;
                     j += (target.generateEmptyAfterBig() ? 1 : Code.width(frame.locals[j]))) {
                    localCount++;
                }
                if (debugstackmap) System.out.print(" nlocals=" +
                                                    localCount);
                databuf.appendChar(localCount);
                for (int j=0; j<frame.locals.length;
                     j += (target.generateEmptyAfterBig() ? 1 : Code.width(frame.locals[j]))) {
                    if (debugstackmap) System.out.print(" local[" + j + "]=");
                    writeStackMapType(frame.locals[j]);
                }

                // output stack
                int stackCount = 0;
                for (int j=0; j<frame.stack.length;
                     j += (target.generateEmptyAfterBig() ? 1 : Code.width(frame.stack[j]))) {
                    stackCount++;
                }
                if (debugstackmap) System.out.print(" nstack=" +
                                                    stackCount);
                databuf.appendChar(stackCount);
                for (int j=0; j<frame.stack.length;
                     j += (target.generateEmptyAfterBig() ? 1 : Code.width(frame.stack[j]))) {
                    if (debugstackmap) System.out.print(" stack[" + j + "]=");
                    writeStackMapType(frame.stack[j]);
                }
                if (debugstackmap) System.out.println();
            }
            break;
        case JSR202: {
            assert code.stackMapBuffer == null;
            for (int i=0; i<nframes; i++) {
                if (debugstackmap) System.out.print("  " + i + ":");
                StackMapTableFrame frame = code.stackMapTableBuffer[i];
                frame.write(this);
                if (debugstackmap) System.out.println();
            }
            break;
        }
        default:
            throw new AssertionError("Unexpected stackmap format value");
        }
    }

        //where
        void writeStackMapType(Type t) {
            if (t == null) {
                if (debugstackmap) System.out.print("empty");
                databuf.appendByte(0);
            }
            else switch(t.tag) {
            case BYTE:
            case CHAR:
            case SHORT:
            case INT:
            case BOOLEAN:
                if (debugstackmap) System.out.print("int");
                databuf.appendByte(1);
                break;
            case FLOAT:
                if (debugstackmap) System.out.print("float");
                databuf.appendByte(2);
                break;
            case DOUBLE:
                if (debugstackmap) System.out.print("double");
                databuf.appendByte(3);
                break;
            case LONG:
                if (debugstackmap) System.out.print("long");
                databuf.appendByte(4);
                break;
            case BOT: // null
                if (debugstackmap) System.out.print("null");
                databuf.appendByte(5);
                break;
            case CLASS:
            case ARRAY:
                if (debugstackmap) System.out.print("object(" + t + ")");
                databuf.appendByte(7);
                databuf.appendChar(pool.put(t));
                break;
            case TYPEVAR:
                if (debugstackmap) System.out.print("object(" + types.erasure(t).tsym + ")");
                databuf.appendByte(7);
                databuf.appendChar(pool.put(types.erasure(t).tsym));
                break;
            case UNINITIALIZED_THIS:
                if (debugstackmap) System.out.print("uninit_this");
                databuf.appendByte(6);
                break;
            case UNINITIALIZED_OBJECT:
                { UninitializedType uninitType = (UninitializedType)t;
                databuf.appendByte(8);
                if (debugstackmap) System.out.print("uninit_object@" + uninitType.offset);
                databuf.appendChar(uninitType.offset);
                }
                break;
            default:
                throw new AssertionError();
            }
        }

    /** An entry in the JSR202 StackMapTable */
    abstract static class StackMapTableFrame {
        abstract int getFrameType();

        void write(ClassWriter writer) {
            int frameType = getFrameType();
            writer.databuf.appendByte(frameType);
            if (writer.debugstackmap) System.out.print(" frame_type=" + frameType);
        }

        static class SameFrame extends StackMapTableFrame {
            final int offsetDelta;
            SameFrame(int offsetDelta) {
                this.offsetDelta = offsetDelta;
            }
            int getFrameType() {
                return (offsetDelta < SAME_FRAME_SIZE) ? offsetDelta : SAME_FRAME_EXTENDED;
            }
            @Override
            void write(ClassWriter writer) {
                super.write(writer);
                if (getFrameType() == SAME_FRAME_EXTENDED) {
                    writer.databuf.appendChar(offsetDelta);
                    if (writer.debugstackmap){
                        System.out.print(" offset_delta=" + offsetDelta);
                    }
                }
            }
        }

        static class SameLocals1StackItemFrame extends StackMapTableFrame {
            final int offsetDelta;
            final Type stack;
            SameLocals1StackItemFrame(int offsetDelta, Type stack) {
                this.offsetDelta = offsetDelta;
                this.stack = stack;
            }
            int getFrameType() {
                return (offsetDelta < SAME_FRAME_SIZE) ?
                       (SAME_FRAME_SIZE + offsetDelta) :
                       SAME_LOCALS_1_STACK_ITEM_EXTENDED;
            }
            @Override
            void write(ClassWriter writer) {
                super.write(writer);
                if (getFrameType() == SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
                    writer.databuf.appendChar(offsetDelta);
                    if (writer.debugstackmap) {
                        System.out.print(" offset_delta=" + offsetDelta);
                    }
                }
                if (writer.debugstackmap) {
                    System.out.print(" stack[" + 0 + "]=");
                }
                writer.writeStackMapType(stack);
            }
        }

        static class ChopFrame extends StackMapTableFrame {
            final int frameType;
            final int offsetDelta;
            ChopFrame(int frameType, int offsetDelta) {
                this.frameType = frameType;
                this.offsetDelta = offsetDelta;
            }
            int getFrameType() { return frameType; }
            @Override
            void write(ClassWriter writer) {
                super.write(writer);
                writer.databuf.appendChar(offsetDelta);
                if (writer.debugstackmap) {
                    System.out.print(" offset_delta=" + offsetDelta);
                }
            }
        }

        static class AppendFrame extends StackMapTableFrame {
            final int frameType;
            final int offsetDelta;
            final Type[] locals;
            AppendFrame(int frameType, int offsetDelta, Type[] locals) {
                this.frameType = frameType;
                this.offsetDelta = offsetDelta;
                this.locals = locals;
            }
            int getFrameType() { return frameType; }
            @Override
            void write(ClassWriter writer) {
                super.write(writer);
                writer.databuf.appendChar(offsetDelta);
                if (writer.debugstackmap) {
                    System.out.print(" offset_delta=" + offsetDelta);
                }
                for (int i=0; i<locals.length; i++) {
                     if (writer.debugstackmap) System.out.print(" locals[" + i + "]=");
                     writer.writeStackMapType(locals[i]);
                }
            }
        }

        static class FullFrame extends StackMapTableFrame {
            final int offsetDelta;
            final Type[] locals;
            final Type[] stack;
            FullFrame(int offsetDelta, Type[] locals, Type[] stack) {
                this.offsetDelta = offsetDelta;
                this.locals = locals;
                this.stack = stack;
            }
            int getFrameType() { return FULL_FRAME; }
            @Override
            void write(ClassWriter writer) {
                super.write(writer);
                writer.databuf.appendChar(offsetDelta);
                writer.databuf.appendChar(locals.length);
                if (writer.debugstackmap) {
                    System.out.print(" offset_delta=" + offsetDelta);
                    System.out.print(" nlocals=" + locals.length);
                }
                for (int i=0; i<locals.length; i++) {
                    if (writer.debugstackmap) System.out.print(" locals[" + i + "]=");
                    writer.writeStackMapType(locals[i]);
                }

                writer.databuf.appendChar(stack.length);
                if (writer.debugstackmap) { System.out.print(" nstack=" + stack.length); }
                for (int i=0; i<stack.length; i++) {
                    if (writer.debugstackmap) System.out.print(" stack[" + i + "]=");
                    writer.writeStackMapType(stack[i]);
                }
            }
        }

       /** Compare this frame with the previous frame and produce
        *  an entry of compressed stack map frame. */
        static StackMapTableFrame getInstance(Code.StackMapFrame this_frame,
                                              int prev_pc,
                                              Type[] prev_locals,
                                              Types types) {
            Type[] locals = this_frame.locals;
            Type[] stack = this_frame.stack;
            int offset_delta = this_frame.pc - prev_pc - 1;
            if (stack.length == 1) {
                if (locals.length == prev_locals.length
                    && compare(prev_locals, locals, types) == 0) {
                    return new SameLocals1StackItemFrame(offset_delta, stack[0]);
                }
            } else if (stack.length == 0) {
                int diff_length = compare(prev_locals, locals, types);
                if (diff_length == 0) {
                    return new SameFrame(offset_delta);
                } else if (-MAX_LOCAL_LENGTH_DIFF < diff_length && diff_length < 0) {
                    // APPEND
                    Type[] local_diff = new Type[-diff_length];
                    for (int i=prev_locals.length, j=0; i<locals.length; i++,j++) {
                        local_diff[j] = locals[i];
                    }
                    return new AppendFrame(SAME_FRAME_EXTENDED - diff_length,
                                           offset_delta,
                                           local_diff);
                } else if (0 < diff_length && diff_length < MAX_LOCAL_LENGTH_DIFF) {
                    // CHOP
                    return new ChopFrame(SAME_FRAME_EXTENDED - diff_length,
                                         offset_delta);
                }
            }
            // FULL_FRAME
            return new FullFrame(offset_delta, locals, stack);
        }

        static boolean isInt(Type t) {
            return (t.tag < TypeTags.INT || t.tag == TypeTags.BOOLEAN);
        }

        static boolean isSameType(Type t1, Type t2, Types types) {
            if (t1 == null) { return t2 == null; }
            if (t2 == null) { return false; }

            if (isInt(t1) && isInt(t2)) { return true; }

            if (t1.tag == UNINITIALIZED_THIS) {
                return t2.tag == UNINITIALIZED_THIS;
            } else if (t1.tag == UNINITIALIZED_OBJECT) {
                if (t2.tag == UNINITIALIZED_OBJECT) {
                    return ((UninitializedType)t1).offset == ((UninitializedType)t2).offset;
                } else {
                    return false;
                }
            } else if (t2.tag == UNINITIALIZED_THIS || t2.tag == UNINITIALIZED_OBJECT) {
                return false;
            }

            return types.isSameType(t1, t2);
        }

        static int compare(Type[] arr1, Type[] arr2, Types types) {
            int diff_length = arr1.length - arr2.length;
            if (diff_length > MAX_LOCAL_LENGTH_DIFF || diff_length < -MAX_LOCAL_LENGTH_DIFF) {
                return Integer.MAX_VALUE;
            }
            int len = (diff_length > 0) ? arr2.length : arr1.length;
            for (int i=0; i<len; i++) {
                if (!isSameType(arr1[i], arr2[i], types)) {
                    return Integer.MAX_VALUE;
                }
            }
            return diff_length;
        }
    }

    void writeFields(Scope.Entry e) {
        // process them in reverse sibling order;
        // i.e., process them in declaration order.
        List<VarSymbol> vars = List.nil();
        for (Scope.Entry i = e; i != null; i = i.sibling) {
            if (i.sym.kind == VAR) vars = vars.prepend((VarSymbol)i.sym);
        }
        while (vars.nonEmpty()) {
            writeField(vars.head);
            vars = vars.tail;
        }
    }

    void writeMethods(Scope.Entry e) {
        List<MethodSymbol> methods = List.nil();
        for (Scope.Entry i = e; i != null; i = i.sibling) {
            if (i.sym.kind == MTH && (i.sym.flags() & HYPOTHETICAL) == 0)
                methods = methods.prepend((MethodSymbol)i.sym);
        }
        while (methods.nonEmpty()) {
            writeMethod(methods.head);
            methods = methods.tail;
        }
    }

    /** Emit a class file for a given class.
     *  @param c      The class from which a class file is generated.
     */
    public JavaFileObject writeClass(ClassSymbol c)
        throws IOException, PoolOverflow, StringOverflow
    {
        JavaFileObject outFile
            = fileManager.getJavaFileForOutput(CLASS_OUTPUT,
                                               c.flatname.toString(),
                                               JavaFileObject.Kind.CLASS,
                                               c.sourcefile);
        OutputStream out = outFile.openOutputStream();
        try {
            writeClassFile(out, c);
            if (verbose)
                log.errWriter.println(Log.getLocalizedString("verbose.wrote.file", outFile));
            out.close();
            out = null;
        } finally {
            if (out != null) {
                // if we are propogating an exception, delete the file
                out.close();
                outFile.delete();
                outFile = null;
            }
        }
        return outFile; // may be null if write failed
    }

    /** Write class `c' to outstream `out'.
     */
    public void writeClassFile(OutputStream out, ClassSymbol c)
        throws IOException, PoolOverflow, StringOverflow {
        assert (c.flags() & COMPOUND) == 0;
        databuf.reset();
        poolbuf.reset();
        sigbuf.reset();
        pool = c.pool;
        innerClasses = null;
        innerClassesQueue = null;

        Type supertype = types.supertype(c.type);
        List<Type> interfaces = types.interfaces(c.type);
        List<Type> typarams = c.type.getTypeArguments();

        int flags = adjustFlags(c.flags());
        if ((flags & PROTECTED) != 0) flags |= PUBLIC;
        flags = flags & ClassFlags & ~STRICTFP;
        if ((flags & INTERFACE) == 0) flags |= ACC_SUPER;
        if (c.isInner() && c.name.isEmpty()) flags &= ~FINAL;
        if (dumpClassModifiers) {
            log.errWriter.println();
            log.errWriter.println("CLASSFILE  " + c.getQualifiedName());
            log.errWriter.println("---" + flagNames(flags));
        }
        databuf.appendChar(flags);

        databuf.appendChar(pool.put(c));
        databuf.appendChar(supertype.tag == CLASS ? pool.put(supertype.tsym) : 0);
        databuf.appendChar(interfaces.length());
        for (List<Type> l = interfaces; l.nonEmpty(); l = l.tail)
            databuf.appendChar(pool.put(l.head.tsym));
        int fieldsCount = 0;
        int methodsCount = 0;
        for (Scope.Entry e = c.members().elems; e != null; e = e.sibling) {
            switch (e.sym.kind) {
            case VAR: fieldsCount++; break;
            case MTH: if ((e.sym.flags() & HYPOTHETICAL) == 0) methodsCount++;
                      break;
            case TYP: enterInner((ClassSymbol)e.sym); break;
            default : assert false;
            }
        }
        databuf.appendChar(fieldsCount);
        writeFields(c.members().elems);
        databuf.appendChar(methodsCount);
        writeMethods(c.members().elems);

        int acountIdx = beginAttrs();
        int acount = 0;

        boolean sigReq =
            typarams.length() != 0 || supertype.allparams().length() != 0;
        for (List<Type> l = interfaces; !sigReq && l.nonEmpty(); l = l.tail)
            sigReq = l.head.allparams().length() != 0;
        if (sigReq) {
            assert source.allowGenerics();
            int alenIdx = writeAttr(names.Signature);
            if (typarams.length() != 0) assembleParamsSig(typarams);
            assembleSig(supertype);
            for (List<Type> l = interfaces; l.nonEmpty(); l = l.tail)
                assembleSig(l.head);
            databuf.appendChar(pool.put(sigbuf.toName(names)));
            sigbuf.reset();
            endAttr(alenIdx);
            acount++;
        }

        if (c.sourcefile != null && emitSourceFile) {
            int alenIdx = writeAttr(names.SourceFile);
            // WHM 6/29/1999: Strip file path prefix.  We do it here at
            // the last possible moment because the sourcefile may be used
            // elsewhere in error diagnostics. Fixes 4241573.
            //databuf.appendChar(c.pool.put(c.sourcefile));
            String filename = c.sourcefile.toString();
            int sepIdx = filename.lastIndexOf(File.separatorChar);
            // Allow '/' as separator on all platforms, e.g., on Win32.
            int slashIdx = filename.lastIndexOf('/');
            if (slashIdx > sepIdx) sepIdx = slashIdx;
            if (sepIdx >= 0) filename = filename.substring(sepIdx + 1);
            databuf.appendChar(c.pool.put(names.fromString(filename)));
            endAttr(alenIdx);
            acount++;
        }

        if (genCrt) {
            // Append SourceID attribute
            int alenIdx = writeAttr(names.SourceID);
            databuf.appendChar(c.pool.put(names.fromString(Long.toString(getLastModified(c.sourcefile)))));
            endAttr(alenIdx);
            acount++;
            // Append CompilationID attribute
            alenIdx = writeAttr(names.CompilationID);
            databuf.appendChar(c.pool.put(names.fromString(Long.toString(System.currentTimeMillis()))));
            endAttr(alenIdx);
            acount++;
        }

        acount += writeFlagAttrs(c.flags());
        acount += writeJavaAnnotations(c.getAnnotationMirrors());
        acount += writeTypeAnnotations(c.typeAnnotations);
        acount += writeEnclosingMethodAttribute(c);

        poolbuf.appendInt(JAVA_MAGIC);
        poolbuf.appendChar(target.minorVersion);
        poolbuf.appendChar(target.majorVersion);

        writePool(c.pool);

        if (innerClasses != null) {
            writeInnerClasses();
            acount++;
        }
        endAttrs(acountIdx, acount);

        poolbuf.appendBytes(databuf.elems, 0, databuf.length);
        out.write(poolbuf.elems, 0, poolbuf.length);

        pool = c.pool = null; // to conserve space
     }

    int adjustFlags(final long flags) {
        int result = (int)flags;
        if ((flags & SYNTHETIC) != 0  && !target.useSyntheticFlag())
            result &= ~SYNTHETIC;
        if ((flags & ENUM) != 0  && !target.useEnumFlag())
            result &= ~ENUM;
        if ((flags & ANNOTATION) != 0  && !target.useAnnotationFlag())
            result &= ~ANNOTATION;

        if ((flags & BRIDGE) != 0  && target.useBridgeFlag())
            result |= ACC_BRIDGE;
        if ((flags & VARARGS) != 0  && target.useVarargsFlag())
            result |= ACC_VARARGS;
        return result;
    }

    long getLastModified(FileObject filename) {
        long mod = 0;
        try {
            mod = filename.getLastModified();
        } catch (SecurityException e) {
            throw new AssertionError("CRT: couldn't get source file modification date: " + e.getMessage());
        }
        return mod;
    }
}
