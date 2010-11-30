/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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


import com.sun.java.util.jar.pack.ConstantPool.Entry;
import com.sun.java.util.jar.pack.ConstantPool.Index;
import com.sun.java.util.jar.pack.ConstantPool.NumberEntry;
import com.sun.java.util.jar.pack.Package.Class;
import com.sun.java.util.jar.pack.Package.InnerClass;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

/**
 * Writer for a class file that is incorporated into a package.
 * @author John Rose
 */
class ClassWriter implements Constants {
    int verbose;

    Package pkg;
    Class cls;
    DataOutputStream out;
    Index cpIndex;

    ClassWriter(Class cls, OutputStream out) throws IOException {
        this.pkg = cls.getPackage();
        this.cls = cls;
        this.verbose = pkg.verbose;
        this.out = new DataOutputStream(new BufferedOutputStream(out));
        this.cpIndex = ConstantPool.makeIndex(cls.toString(), cls.getCPMap());
        this.cpIndex.flattenSigs = true;
        if (verbose > 1)
            Utils.log.fine("local CP="+(verbose > 2 ? cpIndex.dumpString() : cpIndex.toString()));
    }

    private void writeShort(int x) throws IOException {
        out.writeShort(x);
    }

    private void writeInt(int x) throws IOException {
        out.writeInt(x);
    }

    /** Write a 2-byte int representing a CP entry, using the local cpIndex. */
    private void writeRef(Entry e) throws IOException {
        int i = (e == null) ? 0 : cpIndex.indexOf(e);
        writeShort(i);
    }

    void write() throws IOException {
        boolean ok = false;
        try {
            if (verbose > 1)  Utils.log.fine("...writing "+cls);
            writeMagicNumbers();
            writeConstantPool();
            writeHeader();
            writeMembers(false);  // fields
            writeMembers(true);   // methods
            writeAttributes(ATTR_CONTEXT_CLASS, cls);
            /* Closing here will cause all the underlying
               streams to close, Causing the jar stream
               to close prematurely, instead we just flush.
               out.close();
             */
            out.flush();
            ok = true;
        } finally {
            if (!ok) {
                Utils.log.warning("Error on output of "+cls);
            }
        }
    }

    void writeMagicNumbers() throws IOException {
        writeInt(cls.magic);
        writeShort(cls.minver);
        writeShort(cls.majver);
    }

    void writeConstantPool() throws IOException {
        Entry[] cpMap = cls.cpMap;
        writeShort(cpMap.length);
        for (int i = 0; i < cpMap.length; i++) {
            Entry e = cpMap[i];
            assert((e == null) == (i == 0 || cpMap[i-1] != null && cpMap[i-1].isDoubleWord()));
            if (e == null)  continue;
            byte tag = e.getTag();
            if (verbose > 2)  Utils.log.fine("   CP["+i+"] = "+e);
            out.write(tag);
            switch (tag) {
                case CONSTANT_Signature:
                    assert(false);  // should not reach here
                    break;
                case CONSTANT_Utf8:
                    out.writeUTF(e.stringValue());
                    break;
                case CONSTANT_Integer:
                    out.writeInt(((NumberEntry)e).numberValue().intValue());
                    break;
                case CONSTANT_Float:
                    float fval = ((NumberEntry)e).numberValue().floatValue();
                    out.writeInt(Float.floatToRawIntBits(fval));
                    break;
                case CONSTANT_Long:
                    out.writeLong(((NumberEntry)e).numberValue().longValue());
                    break;
                case CONSTANT_Double:
                    double dval = ((NumberEntry)e).numberValue().doubleValue();
                    out.writeLong(Double.doubleToRawLongBits(dval));
                    break;
                case CONSTANT_Class:
                case CONSTANT_String:
                    writeRef(e.getRef(0));
                    break;
                case CONSTANT_Fieldref:
                case CONSTANT_Methodref:
                case CONSTANT_InterfaceMethodref:
                case CONSTANT_NameandType:
                    writeRef(e.getRef(0));
                    writeRef(e.getRef(1));
                    break;
                default:
                    throw new IOException("Bad constant pool tag "+tag);
            }
        }
    }

    void writeHeader() throws IOException {
        writeShort(cls.flags);
        writeRef(cls.thisClass);
        writeRef(cls.superClass);
        writeShort(cls.interfaces.length);
        for (int i = 0; i < cls.interfaces.length; i++) {
            writeRef(cls.interfaces[i]);
        }
    }

    void writeMembers(boolean doMethods) throws IOException {
        List mems;
        if (!doMethods)
            mems = cls.getFields();
        else
            mems = cls.getMethods();
        writeShort(mems.size());
        for (Iterator i = mems.iterator(); i.hasNext(); ) {
            Class.Member m = (Class.Member) i.next();
            writeMember(m, doMethods);
        }
    }

    void writeMember(Class.Member m, boolean doMethod) throws IOException {
        if (verbose > 2)  Utils.log.fine("writeMember "+m);
        writeShort(m.flags);
        writeRef(m.getDescriptor().nameRef);
        writeRef(m.getDescriptor().typeRef);
        writeAttributes(!doMethod ? ATTR_CONTEXT_FIELD : ATTR_CONTEXT_METHOD,
                        m);
    }

    // handy buffer for collecting attrs
    ByteArrayOutputStream buf    = new ByteArrayOutputStream();
    DataOutputStream      bufOut = new DataOutputStream(buf);

    void writeAttributes(int ctype, Attribute.Holder h) throws IOException {
        if (h.attributes == null) {
            writeShort(0);  // attribute size
            return;
        }
        writeShort(h.attributes.size());
        for (Iterator i = h.attributes.iterator(); i.hasNext(); ) {
            Attribute a = (Attribute) i.next();
            a.finishRefs(cpIndex);
            writeRef(a.getNameRef());
            if (a.layout() == Package.attrCodeEmpty ||
                a.layout() == Package.attrInnerClassesEmpty) {
                // These are hardwired.
                DataOutputStream savedOut = out;
                assert(out != bufOut);
                buf.reset();
                out = bufOut;
                if (a.name() == "Code") {
                    Class.Method m = (Class.Method) h;
                    writeCode(m.code);
                } else {
                    assert(h == cls);
                    writeInnerClasses(cls);
                }
                out = savedOut;
                if (verbose > 2)
                    Utils.log.fine("Attribute "+a.name()+" ["+buf.size()+"]");
                writeInt(buf.size());
                buf.writeTo(out);
            } else {
                if (verbose > 2)
                    Utils.log.fine("Attribute "+a.name()+" ["+a.size()+"]");
                writeInt(a.size());
                out.write(a.bytes());
            }
        }
    }

    void writeCode(Code code) throws IOException {
        code.finishRefs(cpIndex);
        writeShort(code.max_stack);
        writeShort(code.max_locals);
        writeInt(code.bytes.length);
        out.write(code.bytes);
        int nh = code.getHandlerCount();
        writeShort(nh);
        for (int i = 0; i < nh; i++) {
             writeShort(code.handler_start[i]);
             writeShort(code.handler_end[i]);
             writeShort(code.handler_catch[i]);
             writeRef(code.handler_class[i]);
        }
        writeAttributes(ATTR_CONTEXT_CODE, code);
    }

    void writeInnerClasses(Class cls) throws IOException {
        List ics = cls.getInnerClasses();
        writeShort(ics.size());
        for (Iterator i = ics.iterator(); i.hasNext(); ) {
            InnerClass ic = (InnerClass) i.next();
            writeRef(ic.thisClass);
            writeRef(ic.outerClass);
            writeRef(ic.name);
            writeShort(ic.flags);
        }
    }
}
