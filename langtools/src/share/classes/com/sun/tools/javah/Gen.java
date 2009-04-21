/*
 * Copyright 2002-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javah;

import java.io.UnsupportedEncodingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import com.sun.javadoc.*;
import java.io.*;
import java.util.Stack;
import java.util.Vector;
import java.util.Arrays;


/**
 * An abstraction for generating support files required by native methods.
 * Subclasses are for specific native interfaces. At the time of its
 * original writing, this interface is rich enough to support JNI and the
 * old 1.0-style native method interface.
 *
 * @author  Sucheta Dambalkar(Revised)
 */


public abstract class Gen {
    protected String lineSep = System.getProperty("line.separator");

    RootDoc root;
    /*
     * List of classes for which we must generate output.
     */
    protected ClassDoc[] classes;
    static private final boolean isWindows =
        System.getProperty("os.name").startsWith("Windows");

    public Gen(RootDoc root){
        this.root = root;
    }

    /**
     * Override this abstract method, generating content for the named
     * class into the outputstream.
     */
    protected abstract void write(OutputStream o, ClassDoc clazz)
        throws ClassNotFoundException;

    /**
     * Override this method to provide a list of #include statements
     * required by the native interface.
     */
    protected abstract String getIncludes();

    /*
     * Output location.
     */
    protected String outDir;
    protected String outFile;

    public void setOutDir(String outDir) {
        /* Check important, otherwise concatenation of two null strings
         * produces the "nullnull" String.
         */
        if (outDir != null) {
            this.outDir = outDir + System.getProperty("file.separator");
            File d = new File(outDir);
            if (!d.exists())
                if (!d.mkdirs())
                    Util.error("cant.create.dir", d.toString());
        }
    }

    public void setOutFile(String outFile) {
        this.outFile = outFile;
    }


    public void setClasses(ClassDoc[] classes) {
        this.classes = classes;
    }

    /*
     * Smartness with generated files.
     */
    protected boolean force = false;

    public void setForce(boolean state) {
        force = state;
    }

    /**
     * We explicitly need to write ASCII files because that is what C
     * compilers understand.
     */
    protected PrintWriter wrapWriter(OutputStream o) {
        try {
            return new
            PrintWriter(new OutputStreamWriter(o, "ISO8859_1"), true);
        } catch (UnsupportedEncodingException use) {
            Util.bug("encoding.iso8859_1.not.found");
            return null; /* dead code */
        }
    }

    /**
     * After initializing state of an instance, use this method to start
     * processing.
     *
     * Buffer size chosen as an approximation from a single sampling of:
     *         expr `du -sk` / `ls *.h | wc -l`
     */
    public void run() throws IOException, ClassNotFoundException {
        int i = 0;
        if (outFile != null) {
            /* Everything goes to one big file... */
            ByteArrayOutputStream bout = new ByteArrayOutputStream(8192);
            writeFileTop(bout); /* only once */

            for (i = 0; i < classes.length; i++) {
                write(bout, classes[i]);
            }

            writeIfChanged(bout.toByteArray(), outFile);
        } else {
            /* Each class goes to its own file... */
            for (i = 0; i < classes.length; i++) {
                ByteArrayOutputStream bout = new ByteArrayOutputStream(8192);
                writeFileTop(bout);
                ClassDoc clazz = classes[i];
                write(bout, clazz);
                writeIfChanged(bout.toByteArray(), getFileName(clazz.qualifiedName()));
            }
        }
    }

    /*
     * Write the contents of byte[] b to a file named file.  Writing
     * is done if either the file doesn't exist or if the contents are
     * different.
     */
    private void writeIfChanged(byte[] b, String file) throws IOException {
        File f = new File(file);
        boolean mustWrite = false;
        String event = "[No need to update file ";

        if (force) {
            mustWrite = true;
            event = "[Forcefully writing file ";
        } else {
            if (!f.exists()) {
                mustWrite = true;
                event = "[Creating file ";
            } else {
                int l = (int)f.length();
                if (b.length != l) {
                    mustWrite = true;
                    event = "[Overwriting file ";
                } else {
                    /* Lengths are equal, so read it. */
                    byte[] a = new byte[l];
                    FileInputStream in = new FileInputStream(f);
                    if (in.read(a) != l) {
                        in.close();
                        /* This can't happen, we already checked the length. */
                        Util.error("not.enough.bytes", Integer.toString(l),
                                   f.toString());
                    }
                    in.close();
                    while (--l >= 0) {
                        if (a[l] != b[l]) {
                            mustWrite = true;
                            event = "[Overwriting file ";
                        }
                    }
                }
            }
        }
        if (Util.verbose)
            Util.log(event + file + "]");
        if (mustWrite) {
            OutputStream out = new FileOutputStream(file);
            out.write(b); /* No buffering, just one big write! */
            out.close();
        }
    }

    protected String defineForStatic(ClassDoc c, FieldDoc f){

        String cnamedoc = c.qualifiedName();
        String fnamedoc = f.name();

        String cname = Mangle.mangle(cnamedoc, Mangle.Type.CLASS);
        String fname = Mangle.mangle(fnamedoc, Mangle.Type.FIELDSTUB);

        if (!f.isStatic())
            Util.bug("tried.to.define.non.static");

        if (f.isFinal()) {
            Object value = null;

            value = f.constantValue();

            if (value != null) { /* so it is a ConstantExpression */
                String constString = null;
                if ((value instanceof Integer)
                    || (value instanceof Byte)
                    || (value instanceof Character)
                    || (value instanceof Short)
                    || (value instanceof Boolean)) {
                    /* covers byte, boolean, char, short, int */
                    if(value instanceof Boolean)
                        constString = (value.toString() == "true") ? "1L" : "0L";
                    else
                        constString = value.toString() + "L";
                } else if (value instanceof Long) {
                    // Visual C++ supports the i64 suffix, not LL.
                    if (isWindows)
                        constString = value.toString() + "i64";
                    else
                        constString = value.toString() + "LL";
                } else if (value instanceof Float) {
                    /* bug for bug */
                    float fv = ((Float)value).floatValue();
                    if (Float.isInfinite(fv))
                        constString = ((fv < 0) ? "-" : "") + "Inff";
                    else
                        constString = value.toString() + "f";
                } else if (value instanceof Double) {
                    /* bug for bug */
                    double d = ((Double)value).doubleValue();
                    if (Double.isInfinite(d))
                        constString = ((d < 0) ? "-" : "") + "InfD";
                    else
                        constString = value.toString();
                }
                if (constString != null) {
                    StringBuffer s = new StringBuffer("#undef ");
                    s.append(cname); s.append("_"); s.append(fname); s.append(lineSep);
                    s.append("#define "); s.append(cname); s.append("_");
                    s.append(fname); s.append(" "); s.append(constString);
                    return s.toString();
                }

            }
        }
        return null;
    }

    /*
     * Deal with the C pre-processor.
     */
    protected String cppGuardBegin() {
        return "#ifdef __cplusplus" + lineSep + "extern \"C\" {" + lineSep + "#endif";
    }

    protected String cppGuardEnd() {
        return "#ifdef __cplusplus" + lineSep + "}" + lineSep + "#endif";
    }

    protected String guardBegin(String cname) {
        return "/* Header for class " + cname + " */" + lineSep + lineSep +
            "#ifndef _Included_" + cname + lineSep +
            "#define _Included_" + cname;
    }

    protected String guardEnd(String cname) {
        return "#endif";
    }

    /*
     * File name and file preamble related operations.
     */
    protected void writeFileTop(OutputStream o) {
        PrintWriter pw = wrapWriter(o);
        pw.println("/* DO NOT EDIT THIS FILE - it is machine generated */" + lineSep +
                   getIncludes());
    }

    protected String baseFileName(String clazz) {
        StringBuffer f =
            new StringBuffer(Mangle.mangle(clazz,
                                           Mangle.Type.CLASS));
        if (outDir != null) {
            f.insert(0, outDir);
        }
        return f.toString();
    }

    protected String getFileName(String clazz) {
        return baseFileName(clazz) + getFileSuffix();
    }

    protected String getFileSuffix() {
        return ".h";
    }

    /**
     * Including super classes' fields.
     */

    FieldDoc[] getAllFields(ClassDoc subclazz)
                throws ClassNotFoundException {
        Vector<FieldDoc> fields = new Vector<FieldDoc>();
        ClassDoc cd = null;
        Stack<Object> s = new Stack<Object>();

        cd = subclazz;
        while (true) {
            s.push(cd);
            ClassDoc c = cd.superclass();
            if (c == null)
                break;
            cd = c;
        }

        while (!s.empty()) {
            cd = (ClassDoc)s.pop();
            fields.addAll(Arrays.asList(cd.fields()));
        }

        return fields.toArray(new FieldDoc[fields.size()]);
    }
}
