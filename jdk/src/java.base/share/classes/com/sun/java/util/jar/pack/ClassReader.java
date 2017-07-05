/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.util.jar.pack;

import com.sun.java.util.jar.pack.ConstantPool.ClassEntry;
import com.sun.java.util.jar.pack.ConstantPool.DescriptorEntry;
import com.sun.java.util.jar.pack.ConstantPool.Entry;
import com.sun.java.util.jar.pack.ConstantPool.SignatureEntry;
import com.sun.java.util.jar.pack.ConstantPool.MemberEntry;
import com.sun.java.util.jar.pack.ConstantPool.MethodHandleEntry;
import com.sun.java.util.jar.pack.ConstantPool.BootstrapMethodEntry;
import com.sun.java.util.jar.pack.ConstantPool.Utf8Entry;
import com.sun.java.util.jar.pack.Package.Class;
import com.sun.java.util.jar.pack.Package.InnerClass;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import static com.sun.java.util.jar.pack.Constants.*;

/**
 * Reader for a class file that is being incorporated into a package.
 * @author John Rose
 */
class ClassReader {
    int verbose;

    Package pkg;
    Class cls;
    long inPos;
    long constantPoolLimit = -1;
    DataInputStream in;
    Map<Attribute.Layout, Attribute> attrDefs;
    Map<Attribute.Layout, String> attrCommands;
    String unknownAttrCommand = "error";;

    ClassReader(Class cls, InputStream in) throws IOException {
        this.pkg = cls.getPackage();
        this.cls = cls;
        this.verbose = pkg.verbose;
        this.in = new DataInputStream(new FilterInputStream(in) {
            public int read(byte b[], int off, int len) throws IOException {
                int nr = super.read(b, off, len);
                if (nr >= 0)  inPos += nr;
                return nr;
            }
            public int read() throws IOException {
                int ch = super.read();
                if (ch >= 0)  inPos += 1;
                return ch;
            }
            public long skip(long n) throws IOException {
                long ns = super.skip(n);
                if (ns >= 0)  inPos += ns;
                return ns;
            }
        });
    }

    public void setAttrDefs(Map<Attribute.Layout, Attribute> attrDefs) {
        this.attrDefs = attrDefs;
    }

    public void setAttrCommands(Map<Attribute.Layout, String> attrCommands) {
        this.attrCommands = attrCommands;
    }

    private void skip(int n, String what) throws IOException {
        Utils.log.warning("skipping "+n+" bytes of "+what);
        long skipped = 0;
        while (skipped < n) {
            long j = in.skip(n - skipped);
            assert(j > 0);
            skipped += j;
        }
        assert(skipped == n);
    }

    private int readUnsignedShort() throws IOException {
        return in.readUnsignedShort();
    }

    private int readInt() throws IOException {
        return in.readInt();
    }

    /** Read a 2-byte int, and return the <em>global</em> CP entry for it. */
    private Entry readRef() throws IOException {
        int i = in.readUnsignedShort();
        return i == 0 ? null : cls.cpMap[i];
    }

    private Entry readRef(byte tag) throws IOException {
        Entry e = readRef();
        assert(!(e instanceof UnresolvedEntry));
        checkTag(e, tag);
        return e;
    }

    /** Throw a ClassFormatException if the entry does not match the expected tag type. */
    private Entry checkTag(Entry e, byte tag) throws ClassFormatException {
        if (e == null || !e.tagMatches(tag)) {
            String where = (inPos == constantPoolLimit
                                ? " in constant pool"
                                : " at pos: " + inPos);
            String got = (e == null
                            ? "null CP index"
                            : "type=" + ConstantPool.tagName(e.tag));
            throw new ClassFormatException("Bad constant, expected type=" +
                    ConstantPool.tagName(tag) +
                    " got "+ got + ", in File: " + cls.file.nameString + where);
        }
        return e;
    }
    private Entry checkTag(Entry e, byte tag, boolean nullOK) throws ClassFormatException {
        return nullOK && e == null ? null : checkTag(e, tag);
    }

    private Entry readRefOrNull(byte tag) throws IOException {
        Entry e = readRef();
        checkTag(e, tag, true);
        return e;
    }

    private Utf8Entry readUtf8Ref() throws IOException {
        return (Utf8Entry) readRef(CONSTANT_Utf8);
    }

    private ClassEntry readClassRef() throws IOException {
        return (ClassEntry) readRef(CONSTANT_Class);
    }

    private ClassEntry readClassRefOrNull() throws IOException {
        return (ClassEntry) readRefOrNull(CONSTANT_Class);
    }

    private SignatureEntry readSignatureRef() throws IOException {
        // The class file stores a Utf8, but we want a Signature.
        Entry e = readRef(CONSTANT_Signature);
        return (e != null && e.getTag() == CONSTANT_Utf8)
                ? ConstantPool.getSignatureEntry(e.stringValue())
                : (SignatureEntry) e;
    }

    void read() throws IOException {
        boolean ok = false;
        try {
            readMagicNumbers();
            readConstantPool();
            readHeader();
            readMembers(false);  // fields
            readMembers(true);   // methods
            readAttributes(ATTR_CONTEXT_CLASS, cls);
            fixUnresolvedEntries();
            cls.finishReading();
            assert(0 >= in.read(new byte[1]));
            ok = true;
        } finally {
            if (!ok) {
                if (verbose > 0) Utils.log.warning("Erroneous data at input offset "+inPos+" of "+cls.file);
            }
        }
    }

    void readMagicNumbers() throws IOException {
        cls.magic = in.readInt();
        if (cls.magic != JAVA_MAGIC)
            throw new Attribute.FormatException
                ("Bad magic number in class file "
                 +Integer.toHexString(cls.magic),
                 ATTR_CONTEXT_CLASS, "magic-number", "pass");
        int minver = (short) readUnsignedShort();
        int majver = (short) readUnsignedShort();
        cls.version = Package.Version.of(majver, minver);

        //System.out.println("ClassFile.version="+cls.majver+"."+cls.minver);
        String bad = checkVersion(cls.version);
        if (bad != null) {
            throw new Attribute.FormatException
                ("classfile version too "+bad+": "
                 +cls.version+" in "+cls.file,
                 ATTR_CONTEXT_CLASS, "version", "pass");
        }
    }

    private String checkVersion(Package.Version ver) {
        int majver = ver.major;
        int minver = ver.minor;
        if (majver < pkg.minClassVersion.major ||
            (majver == pkg.minClassVersion.major &&
             minver < pkg.minClassVersion.minor)) {
            return "small";
        }
        if (majver > pkg.maxClassVersion.major ||
            (majver == pkg.maxClassVersion.major &&
             minver > pkg.maxClassVersion.minor)) {
            return "large";
        }
        return null;  // OK
    }

    void readConstantPool() throws IOException {
        int length = in.readUnsignedShort();
        //System.err.println("reading CP, length="+length);

        int[] fixups = new int[length*4];
        int fptr = 0;

        Entry[] cpMap = new Entry[length];
        cpMap[0] = null;
        for (int i = 1; i < length; i++) {
            //System.err.println("reading CP elt, i="+i);
            int tag = in.readByte();
            switch (tag) {
                case CONSTANT_Utf8:
                    cpMap[i] = ConstantPool.getUtf8Entry(in.readUTF());
                    break;
                case CONSTANT_Integer:
                    {
                        cpMap[i] = ConstantPool.getLiteralEntry(in.readInt());
                    }
                    break;
                case CONSTANT_Float:
                    {
                        cpMap[i] = ConstantPool.getLiteralEntry(in.readFloat());
                    }
                    break;
                case CONSTANT_Long:
                    {
                        cpMap[i] = ConstantPool.getLiteralEntry(in.readLong());
                        cpMap[++i] = null;
                    }
                    break;
                case CONSTANT_Double:
                    {
                        cpMap[i] = ConstantPool.getLiteralEntry(in.readDouble());
                        cpMap[++i] = null;
                    }
                    break;

                // just read the refs; do not attempt to resolve while reading
                case CONSTANT_Class:
                case CONSTANT_String:
                case CONSTANT_MethodType:
                    fixups[fptr++] = i;
                    fixups[fptr++] = tag;
                    fixups[fptr++] = in.readUnsignedShort();
                    fixups[fptr++] = -1;  // empty ref2
                    break;
                case CONSTANT_Fieldref:
                case CONSTANT_Methodref:
                case CONSTANT_InterfaceMethodref:
                case CONSTANT_NameandType:
                    fixups[fptr++] = i;
                    fixups[fptr++] = tag;
                    fixups[fptr++] = in.readUnsignedShort();
                    fixups[fptr++] = in.readUnsignedShort();
                    break;
                case CONSTANT_InvokeDynamic:
                    fixups[fptr++] = i;
                    fixups[fptr++] = tag;
                    fixups[fptr++] = -1 ^ in.readUnsignedShort();  // not a ref
                    fixups[fptr++] = in.readUnsignedShort();
                    break;
                case CONSTANT_MethodHandle:
                    fixups[fptr++] = i;
                    fixups[fptr++] = tag;
                    fixups[fptr++] = -1 ^ in.readUnsignedByte();
                    fixups[fptr++] = in.readUnsignedShort();
                    break;
                default:
                    throw new ClassFormatException("Bad constant pool tag " +
                            tag + " in File: " + cls.file.nameString +
                            " at pos: " + inPos);
            }
        }
        constantPoolLimit = inPos;

        // Fix up refs, which might be out of order.
        while (fptr > 0) {
            if (verbose > 3)
                Utils.log.fine("CP fixups ["+fptr/4+"]");
            int flimit = fptr;
            fptr = 0;
            for (int fi = 0; fi < flimit; ) {
                int cpi = fixups[fi++];
                int tag = fixups[fi++];
                int ref = fixups[fi++];
                int ref2 = fixups[fi++];
                if (verbose > 3)
                    Utils.log.fine("  cp["+cpi+"] = "+ConstantPool.tagName(tag)+"{"+ref+","+ref2+"}");
                if (ref >= 0 && cpMap[ref] == null || ref2 >= 0 && cpMap[ref2] == null) {
                    // Defer.
                    fixups[fptr++] = cpi;
                    fixups[fptr++] = tag;
                    fixups[fptr++] = ref;
                    fixups[fptr++] = ref2;
                    continue;
                }
                switch (tag) {
                case CONSTANT_Class:
                    cpMap[cpi] = ConstantPool.getClassEntry(cpMap[ref].stringValue());
                    break;
                case CONSTANT_String:
                    cpMap[cpi] = ConstantPool.getStringEntry(cpMap[ref].stringValue());
                    break;
                case CONSTANT_Fieldref:
                case CONSTANT_Methodref:
                case CONSTANT_InterfaceMethodref:
                    ClassEntry      mclass = (ClassEntry)      checkTag(cpMap[ref],  CONSTANT_Class);
                    DescriptorEntry mdescr = (DescriptorEntry) checkTag(cpMap[ref2], CONSTANT_NameandType);
                    cpMap[cpi] = ConstantPool.getMemberEntry((byte)tag, mclass, mdescr);
                    break;
                case CONSTANT_NameandType:
                    Utf8Entry mname = (Utf8Entry) checkTag(cpMap[ref],  CONSTANT_Utf8);
                    Utf8Entry mtype = (Utf8Entry) checkTag(cpMap[ref2], CONSTANT_Signature);
                    cpMap[cpi] = ConstantPool.getDescriptorEntry(mname, mtype);
                    break;
                case CONSTANT_MethodType:
                    cpMap[cpi] = ConstantPool.getMethodTypeEntry((Utf8Entry) checkTag(cpMap[ref], CONSTANT_Signature));
                    break;
                case CONSTANT_MethodHandle:
                    byte refKind = (byte)(-1 ^ ref);
                    MemberEntry memRef = (MemberEntry) checkTag(cpMap[ref2], CONSTANT_AnyMember);
                    cpMap[cpi] = ConstantPool.getMethodHandleEntry(refKind, memRef);
                    break;
                case CONSTANT_InvokeDynamic:
                    DescriptorEntry idescr = (DescriptorEntry) checkTag(cpMap[ref2], CONSTANT_NameandType);
                    cpMap[cpi] = new UnresolvedEntry((byte)tag, (-1 ^ ref), idescr);
                    // Note that ref must be resolved later, using the BootstrapMethods attribute.
                    break;
                default:
                    assert(false);
                }
            }
            assert(fptr < flimit);  // Must make progress.
        }

        cls.cpMap = cpMap;
    }

    private /*non-static*/
    class UnresolvedEntry extends Entry {
        final Object[] refsOrIndexes;
        UnresolvedEntry(byte tag, Object... refsOrIndexes) {
            super(tag);
            this.refsOrIndexes = refsOrIndexes;
            ClassReader.this.haveUnresolvedEntry = true;
        }
        Entry resolve() {
            Class cls = ClassReader.this.cls;
            Entry res;
            switch (tag) {
            case CONSTANT_InvokeDynamic:
                BootstrapMethodEntry iboots = cls.bootstrapMethods.get((Integer) refsOrIndexes[0]);
                DescriptorEntry         idescr = (DescriptorEntry) refsOrIndexes[1];
                res = ConstantPool.getInvokeDynamicEntry(iboots, idescr);
                break;
            default:
                throw new AssertionError();
            }
            return res;
        }
        private void unresolved() { throw new RuntimeException("unresolved entry has no string"); }
        public int compareTo(Object x) { unresolved(); return 0; }
        public boolean equals(Object x) { unresolved(); return false; }
        protected int computeValueHash() { unresolved(); return 0; }
        public String stringValue() { unresolved(); return toString(); }
        public String toString() { return "(unresolved "+ConstantPool.tagName(tag)+")"; }
    }

    boolean haveUnresolvedEntry;
    private void fixUnresolvedEntries() {
        if (!haveUnresolvedEntry)  return;
        Entry[] cpMap = cls.getCPMap();
        for (int i = 0; i < cpMap.length; i++) {
            Entry e = cpMap[i];
            if (e instanceof UnresolvedEntry) {
                cpMap[i] = e = ((UnresolvedEntry)e).resolve();
                assert(!(e instanceof UnresolvedEntry));
            }
        }
        haveUnresolvedEntry = false;
    }

    void readHeader() throws IOException {
        cls.flags = readUnsignedShort();
        cls.thisClass = readClassRef();
        cls.superClass = readClassRefOrNull();
        int ni = readUnsignedShort();
        cls.interfaces = new ClassEntry[ni];
        for (int i = 0; i < ni; i++) {
            cls.interfaces[i] = readClassRef();
        }
    }

    void readMembers(boolean doMethods) throws IOException {
        int nm = readUnsignedShort();
        for (int i = 0; i < nm; i++) {
            readMember(doMethods);
        }
    }

    void readMember(boolean doMethod) throws IOException {
        int    mflags = readUnsignedShort();
        Utf8Entry       mname = readUtf8Ref();
        SignatureEntry  mtype = readSignatureRef();
        DescriptorEntry descr = ConstantPool.getDescriptorEntry(mname, mtype);
        Class.Member m;
        if (!doMethod)
            m = cls.new Field(mflags, descr);
        else
            m = cls.new Method(mflags, descr);
        readAttributes(!doMethod ? ATTR_CONTEXT_FIELD : ATTR_CONTEXT_METHOD,
                       m);
    }
    void readAttributes(int ctype, Attribute.Holder h) throws IOException {
        int na = readUnsignedShort();
        if (na == 0)  return;  // nothing to do here
        if (verbose > 3)
            Utils.log.fine("readAttributes "+h+" ["+na+"]");
        for (int i = 0; i < na; i++) {
            String name = readUtf8Ref().stringValue();
            int length = readInt();
            // See if there is a special command that applies.
            if (attrCommands != null) {
                Attribute.Layout lkey = Attribute.keyForLookup(ctype, name);
                String cmd = attrCommands.get(lkey);
                if (cmd != null) {
                    switch (cmd) {
                        case "pass":
                            String message1 = "passing attribute bitwise in " + h;
                            throw new Attribute.FormatException(message1, ctype, name, cmd);
                        case "error":
                            String message2 = "attribute not allowed in " + h;
                            throw new Attribute.FormatException(message2, ctype, name, cmd);
                        case "strip":
                            skip(length, name + " attribute in " + h);
                            continue;
                    }
                }
            }
            // Find canonical instance of the requested attribute.
            Attribute a = Attribute.lookup(Package.attrDefs, ctype, name);
            if (verbose > 4 && a != null)
                Utils.log.fine("pkg_attribute_lookup "+name+" = "+a);
            if (a == null) {
                a = Attribute.lookup(this.attrDefs, ctype, name);
                if (verbose > 4 && a != null)
                    Utils.log.fine("this "+name+" = "+a);
            }
            if (a == null) {
                a = Attribute.lookup(null, ctype, name);
                if (verbose > 4 && a != null)
                    Utils.log.fine("null_attribute_lookup "+name+" = "+a);
            }
            if (a == null && length == 0) {
                // Any zero-length attr is "known"...
                // We can assume an empty attr. has an empty layout.
                // Handles markers like Enum, Bridge, Synthetic, Deprecated.
                a = Attribute.find(ctype, name, "");
            }
            boolean isStackMap = (ctype == ATTR_CONTEXT_CODE
                                  && (name.equals("StackMap") ||
                                      name.equals("StackMapX")));
            if (isStackMap) {
                // Known attribute but with a corner case format, "pass" it.
                Code code = (Code) h;
                final int TOO_BIG = 0x10000;
                if (code.max_stack   >= TOO_BIG ||
                    code.max_locals  >= TOO_BIG ||
                    code.getLength() >= TOO_BIG ||
                    name.endsWith("X")) {
                    // No, we don't really know what to do with this one.
                    // Do not compress the rare and strange "u4" and "X" cases.
                    a = null;
                }
            }
            if (a == null) {
                if (isStackMap) {
                    // Known attribute but w/o a format; pass it.
                    String message = "unsupported StackMap variant in "+h;
                    throw new Attribute.FormatException(message, ctype, name,
                                                        "pass");
                } else if ("strip".equals(unknownAttrCommand)) {
                    // Skip the unknown attribute.
                    skip(length, "unknown "+name+" attribute in "+h);
                    continue;
                } else {
                    String message = " is unknown attribute in class " + h;
                    throw new Attribute.FormatException(message, ctype, name,
                                                        unknownAttrCommand);
                }
            }
            long pos0 = inPos;  // in case we want to check it
            if (a.layout() == Package.attrCodeEmpty) {
                // These are hardwired.
                Class.Method m = (Class.Method) h;
                m.code = new Code(m);
                try {
                    readCode(m.code);
                } catch (Instruction.FormatException iie) {
                    String message = iie.getMessage() + " in " + h;
                    throw new ClassReader.ClassFormatException(message, iie);
                }
                assert(length == inPos - pos0);
                // Keep empty attribute a...
            } else if (a.layout() == Package.attrBootstrapMethodsEmpty) {
                assert(h == cls);
                readBootstrapMethods(cls);
                assert(length == inPos - pos0);
                // Delete the attribute; it is logically part of the constant pool.
                continue;
            } else if (a.layout() == Package.attrInnerClassesEmpty) {
                // These are hardwired also.
                assert(h == cls);
                readInnerClasses(cls);
                assert(length == inPos - pos0);
                // Keep empty attribute a...
            } else if (length > 0) {
                byte[] bytes = new byte[length];
                in.readFully(bytes);
                a = a.addContent(bytes);
            }
            if (a.size() == 0 && !a.layout().isEmpty()) {
                throw new ClassFormatException(name +
                        ": attribute length cannot be zero, in " + h);
            }
            h.addAttribute(a);
            if (verbose > 2)
                Utils.log.fine("read "+a);
        }
    }

    void readCode(Code code) throws IOException {
        code.max_stack = readUnsignedShort();
        code.max_locals = readUnsignedShort();
        code.bytes = new byte[readInt()];
        in.readFully(code.bytes);
        Entry[] cpMap = cls.getCPMap();
        Instruction.opcodeChecker(code.bytes, cpMap, this.cls.version);
        int nh = readUnsignedShort();
        code.setHandlerCount(nh);
        for (int i = 0; i < nh; i++) {
            code.handler_start[i] = readUnsignedShort();
            code.handler_end[i]   = readUnsignedShort();
            code.handler_catch[i] = readUnsignedShort();
            code.handler_class[i] = readClassRefOrNull();
        }
        readAttributes(ATTR_CONTEXT_CODE, code);
    }

    void readBootstrapMethods(Class cls) throws IOException {
        BootstrapMethodEntry[] bsms = new BootstrapMethodEntry[readUnsignedShort()];
        for (int i = 0; i < bsms.length; i++) {
            MethodHandleEntry bsmRef = (MethodHandleEntry) readRef(CONSTANT_MethodHandle);
            Entry[] argRefs = new Entry[readUnsignedShort()];
            for (int j = 0; j < argRefs.length; j++) {
                argRefs[j] = readRef(CONSTANT_LoadableValue);
            }
            bsms[i] = ConstantPool.getBootstrapMethodEntry(bsmRef, argRefs);
        }
        cls.setBootstrapMethods(Arrays.asList(bsms));
    }

    void readInnerClasses(Class cls) throws IOException {
        int nc = readUnsignedShort();
        ArrayList<InnerClass> ics = new ArrayList<>(nc);
        for (int i = 0; i < nc; i++) {
            InnerClass ic =
                new InnerClass(readClassRef(),
                               readClassRefOrNull(),
                               (Utf8Entry)readRefOrNull(CONSTANT_Utf8),
                               readUnsignedShort());
            ics.add(ic);
        }
        cls.innerClasses = ics;  // set directly; do not use setInnerClasses.
        // (Later, ics may be transferred to the pkg.)
    }

    static class ClassFormatException extends IOException {
        private static final long serialVersionUID = -3564121733989501833L;

        public ClassFormatException(String message) {
            super(message);
        }

        public ClassFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
