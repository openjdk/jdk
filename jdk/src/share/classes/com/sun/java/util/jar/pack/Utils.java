/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.java.util.jar.pack.ConstantPool.LiteralEntry;
import com.sun.java.util.jar.pack.ConstantPool.MemberEntry;
import com.sun.java.util.jar.pack.ConstantPool.SignatureEntry;
import com.sun.java.util.jar.pack.ConstantPool.Utf8Entry;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import sun.util.logging.PlatformLogger;

class Utils {
    static final String COM_PREFIX = "com.sun.java.util.jar.pack.";
    static final String METAINF    = "META-INF";

    /*
     * Outputs various diagnostic support information.
     * If >0, print summary comments (e.g., constant pool info).
     * If >1, print unit comments (e.g., processing of classes).
     * If >2, print many comments (e.g., processing of members).
     * If >3, print tons of comments (e.g., processing of references).
     * (installer only)
     */
    static final String DEBUG_VERBOSE = Utils.COM_PREFIX+"verbose";

    /*
     * Disables use of native code, prefers the Java-coded implementation.
     * (installer only)
     */
    static final String DEBUG_DISABLE_NATIVE = COM_PREFIX+"disable.native";

    /*
     * Use the default working TimeZone instead of UTC.
     * Note: This has installer unpacker implications.
     * see: zip.cpp which uses gmtime vs. localtime.
     */
    static final String PACK_DEFAULT_TIMEZONE = COM_PREFIX+"default.timezone";

    /*
     * Property indicating that the unpacker should
     * ignore the transmitted PACK_MODIFICATION_TIME,
     * replacing it by the given value. The value can
     * be a numeric string, representing the number of
     * mSecs since the epoch (UTC), or the special string
     * {@link #NOW}, meaning the current time (UTC).
     * The default value is the special string {@link #KEEP},
     * which asks the unpacker to preserve all transmitted
     * modification time information.
     * (installer only)
     */
    static final String UNPACK_MODIFICATION_TIME = COM_PREFIX+"unpack.modification.time";

    /*
     * Property indicating that the unpacker strip the
     * Debug Attributes, if they are present, in the pack stream.
     * The default value is false.
     * (installer only)
     */
    static final String UNPACK_STRIP_DEBUG = COM_PREFIX+"unpack.strip.debug";

    /*
     * Remove the input file after unpacking.
     * (installer only)
     */
    static final String UNPACK_REMOVE_PACKFILE = COM_PREFIX+"unpack.remove.packfile";

    /*
     * A possible value for MODIFICATION_TIME
     */
    static final String NOW                             = "now";
    // Other debug options:
    //   com...debug.bands=false      add band IDs to pack file, to verify sync
    //   com...dump.bands=false       dump band contents to local disk
    //   com...no.vary.codings=false  turn off coding variation heuristics
    //   com...no.big.strings=false   turn off "big string" feature

    /*
     * If this property is set to {@link #TRUE}, the packer will preserve
     * the ordering of class files of the original jar in the output archive.
     * The ordering is preserved only for class-files; resource files
     * may be reordered.
     * <p>
     * If the packer is allowed to reorder class files, it can marginally
     * decrease the transmitted size of the archive.
     */
    static final String PACK_KEEP_CLASS_ORDER = COM_PREFIX+"keep.class.order";
    /*
     * This string PACK200 is given as a zip comment on all JAR files
     * produced by this utility.
     */
    static final String PACK_ZIP_ARCHIVE_MARKER_COMMENT = "PACK200";

    // Keep a TLS point to the global data and environment.
    // This makes it simpler to supply environmental options
    // to the engine code, especially the native code.
    static final ThreadLocal<TLGlobals> currentInstance = new ThreadLocal<>();

    // convenience methods to access the TL globals
    static TLGlobals getTLGlobals() {
        return currentInstance.get();
    }

    static Map<String, Utf8Entry> getUtf8Entries() {
        return getTLGlobals().getUtf8Entries();
    }

    static Map<String, ClassEntry> getClassEntries() {
        return getTLGlobals().getClassEntries();
    }

    static Map<Object, LiteralEntry> getLiteralEntries() {
        return getTLGlobals().getLiteralEntries();
    }

    static Map<String, DescriptorEntry> getDescriptorEntries() {
         return getTLGlobals().getDescriptorEntries();
    }

    static Map<String, SignatureEntry> getSignatureEntries() {
        return getTLGlobals().getSignatureEntries();
    }

    static Map<String, MemberEntry> getMemberEntries() {
        return getTLGlobals().getMemberEntries();
    }

    static PropMap currentPropMap() {
        Object obj = currentInstance.get();
        if (obj instanceof PackerImpl)
            return ((PackerImpl)obj).props;
        if (obj instanceof UnpackerImpl)
            return ((UnpackerImpl)obj).props;
        return null;
    }

    static final boolean nolog
        = Boolean.getBoolean(Utils.COM_PREFIX+"nolog");


    static class Pack200Logger {
        private final String name;
        private PlatformLogger log;
        Pack200Logger(String name) {
            this.name = name;
        }

        private synchronized PlatformLogger getLogger() {
            if (log == null) {
                log = PlatformLogger.getLogger(name);
            }
            return log;
        }

        public void warning(String msg, Object param) {
                getLogger().warning(msg, param);
            }

        public void warning(String msg) {
            warning(msg, null);
        }

        public void info(String msg) {
            int verbose = currentPropMap().getInteger(DEBUG_VERBOSE);
            if (verbose > 0) {
                if (nolog) {
                    System.out.println(msg);
                } else {
                    getLogger().info(msg);
                }
            }
        }

        public void fine(String msg) {
            int verbose = currentPropMap().getInteger(DEBUG_VERBOSE);
            if (verbose > 0) {
                    System.out.println(msg);
            }
        }
    }

    static final Pack200Logger log
        = new Pack200Logger("java.util.jar.Pack200");

    // Returns the Max Version String of this implementation
    static String getVersionString() {
        return "Pack200, Vendor: " +
            System.getProperty("java.vendor") +
            ", Version: " +
            Constants.JAVA6_PACKAGE_MAJOR_VERSION + "." +
            Constants.JAVA6_PACKAGE_MINOR_VERSION;
    }

    static void markJarFile(JarOutputStream out) throws IOException {
        out.setComment(PACK_ZIP_ARCHIVE_MARKER_COMMENT);
    }

    // -0 mode helper
    static void copyJarFile(JarInputStream in, JarOutputStream out) throws IOException {
        if (in.getManifest() != null) {
            ZipEntry me = new ZipEntry(JarFile.MANIFEST_NAME);
            out.putNextEntry(me);
            in.getManifest().write(out);
            out.closeEntry();
        }
        byte[] buffer = new byte[1 << 14];
        for (JarEntry je; (je = in.getNextJarEntry()) != null; ) {
            out.putNextEntry(je);
            for (int nr; 0 < (nr = in.read(buffer)); ) {
                out.write(buffer, 0, nr);
            }
        }
        in.close();
        markJarFile(out);  // add PACK200 comment
    }
    static void copyJarFile(JarFile in, JarOutputStream out) throws IOException {
        byte[] buffer = new byte[1 << 14];
        for (Enumeration e = in.entries(); e.hasMoreElements(); ) {
            JarEntry je = (JarEntry) e.nextElement();
            out.putNextEntry(je);
            InputStream ein = in.getInputStream(je);
            for (int nr; 0 < (nr = ein.read(buffer)); ) {
                out.write(buffer, 0, nr);
            }
        }
        in.close();
        markJarFile(out);  // add PACK200 comment
    }
    static void copyJarFile(JarInputStream in, OutputStream out) throws IOException {
        // 4947205 : Peformance is slow when using pack-effort=0
        out = new BufferedOutputStream(out);
        out = new NonCloser(out); // protect from JarOutputStream.close()
        JarOutputStream jout = new JarOutputStream(out);
        copyJarFile(in, jout);
        jout.close();
    }
    static void copyJarFile(JarFile in, OutputStream out) throws IOException {

        // 4947205 : Peformance is slow when using pack-effort=0
        out = new BufferedOutputStream(out);
        out = new NonCloser(out); // protect from JarOutputStream.close()
        JarOutputStream jout = new JarOutputStream(out);
        copyJarFile(in, jout);
        jout.close();
    }
        // Wrapper to prevent closing of client-supplied stream.
    static private
    class NonCloser extends FilterOutputStream {
        NonCloser(OutputStream out) { super(out); }
        public void close() throws IOException { flush(); }
    }
   static String getJarEntryName(String name) {
        if (name == null)  return null;
        return name.replace(File.separatorChar, '/');
    }

    static String zeString(ZipEntry ze) {
        int store = (ze.getCompressedSize() > 0) ?
            (int)( (1.0 - ((double)ze.getCompressedSize()/(double)ze.getSize()))*100 )
            : 0 ;
        // Follow unzip -lv output
        return (long)ze.getSize() + "\t" + ze.getMethod()
            + "\t" + ze.getCompressedSize() + "\t"
            + store + "%\t"
            + new Date(ze.getTime()) + "\t"
            + Long.toHexString(ze.getCrc()) + "\t"
            + ze.getName() ;
    }



    static byte[] readMagic(BufferedInputStream in) throws IOException {
        in.mark(4);
        byte[] magic = new byte[4];
        for (int i = 0; i < magic.length; i++) {
            // read 1 byte at a time, so we always get 4
            if (1 != in.read(magic, i, 1))
                break;
        }
        in.reset();
        return magic;
    }

    // magic number recognizers
    static boolean isJarMagic(byte[] magic) {
        return (magic[0] == (byte)'P' &&
                magic[1] == (byte)'K' &&
                magic[2] >= 1 &&
                magic[2] <  8 &&
                magic[3] == magic[2] + 1);
    }
    static boolean isPackMagic(byte[] magic) {
        return (magic[0] == (byte)0xCA &&
                magic[1] == (byte)0xFE &&
                magic[2] == (byte)0xD0 &&
                magic[3] == (byte)0x0D);
    }
    static boolean isGZIPMagic(byte[] magic) {
        return (magic[0] == (byte)0x1F &&
                magic[1] == (byte)0x8B &&
                magic[2] == (byte)0x08);
        // fourth byte is variable "flg" field
    }

    private Utils() { } // do not instantiate
}
