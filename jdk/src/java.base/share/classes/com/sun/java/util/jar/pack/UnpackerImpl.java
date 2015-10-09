/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;

/*
 * Implementation of the Pack provider.
 * </pre></blockquote>
 * @author John Rose
 * @author Kumar Srinivasan
 */


public class UnpackerImpl extends TLGlobals implements Pack200.Unpacker {

    public UnpackerImpl() {}



    /**
     * Get the set of options for the pack and unpack engines.
     * @return A sorted association of option key strings to option values.
     */
    public SortedMap<String, String> properties() {
        return props;
    }

    // Back-pointer to NativeUnpacker, when active.
    Object _nunp;


    public String toString() {
        return Utils.getVersionString();
    }

    //Driver routines

    // The unpack worker...
    /**
     * Takes a packed-stream InputStream, and writes to a JarOutputStream. Internally
     * the entire buffer must be read, it may be more efficient to read the packed-stream
     * to a file and pass the File object, in the alternate method described below.
     * <p>
     * Closes its input but not its output.  (The output can accumulate more elements.)
     * @param in an InputStream.
     * @param out a JarOutputStream.
     * @exception IOException if an error is encountered.
     */
    public synchronized void unpack(InputStream in, JarOutputStream out) throws IOException {
        if (in == null) {
            throw new NullPointerException("null input");
        }
        if (out == null) {
            throw new NullPointerException("null output");
        }
        assert(Utils.currentInstance.get() == null);

        try {
            Utils.currentInstance.set(this);
            final int verbose = props.getInteger(Utils.DEBUG_VERBOSE);
            BufferedInputStream in0 = new BufferedInputStream(in);
            if (Utils.isJarMagic(Utils.readMagic(in0))) {
                if (verbose > 0)
                    Utils.log.info("Copying unpacked JAR file...");
                Utils.copyJarFile(new JarInputStream(in0), out);
            } else if (props.getBoolean(Utils.DEBUG_DISABLE_NATIVE)) {
                (new DoUnpack()).run(in0, out);
                in0.close();
                Utils.markJarFile(out);
            } else {
                try {
                    (new NativeUnpack(this)).run(in0, out);
                } catch (UnsatisfiedLinkError | NoClassDefFoundError ex) {
                    // failover to java implementation
                    (new DoUnpack()).run(in0, out);
                }
                in0.close();
                Utils.markJarFile(out);
            }
        } finally {
            _nunp = null;
            Utils.currentInstance.set(null);
        }
    }

    /**
     * Takes an input File containing the pack file, and generates a JarOutputStream.
     * <p>
     * Does not close its output.  (The output can accumulate more elements.)
     * @param in a File.
     * @param out a JarOutputStream.
     * @exception IOException if an error is encountered.
     */
    public synchronized void unpack(File in, JarOutputStream out) throws IOException {
        if (in == null) {
            throw new NullPointerException("null input");
        }
        if (out == null) {
            throw new NullPointerException("null output");
        }
        // Use the stream-based implementation.
        // %%% Reconsider if native unpacker learns to memory-map the file.
        try (FileInputStream instr = new FileInputStream(in)) {
            unpack(instr, out);
        }
        if (props.getBoolean(Utils.UNPACK_REMOVE_PACKFILE)) {
            in.delete();
        }
    }

    private class DoUnpack {
        final int verbose = props.getInteger(Utils.DEBUG_VERBOSE);

        {
            props.setInteger(Pack200.Unpacker.PROGRESS, 0);
        }

        // Here's where the bits are read from disk:
        final Package pkg = new Package();

        final boolean keepModtime
            = Pack200.Packer.KEEP.equals(
              props.getProperty(Utils.UNPACK_MODIFICATION_TIME, Pack200.Packer.KEEP));
        final boolean keepDeflateHint
            = Pack200.Packer.KEEP.equals(
              props.getProperty(Pack200.Unpacker.DEFLATE_HINT, Pack200.Packer.KEEP));
        final int modtime;
        final boolean deflateHint;
        {
            if (!keepModtime) {
                modtime = props.getTime(Utils.UNPACK_MODIFICATION_TIME);
            } else {
                modtime = pkg.default_modtime;
            }

            deflateHint = (keepDeflateHint) ? false :
                props.getBoolean(java.util.jar.Pack200.Unpacker.DEFLATE_HINT);
        }

        // Checksum apparatus.
        final CRC32 crc = new CRC32();
        final ByteArrayOutputStream bufOut = new ByteArrayOutputStream();
        final OutputStream crcOut = new CheckedOutputStream(bufOut, crc);

        public void run(BufferedInputStream in, JarOutputStream out) throws IOException {
            if (verbose > 0) {
                props.list(System.out);
            }
            for (int seg = 1; ; seg++) {
                unpackSegment(in, out);

                // Try to get another segment.
                if (!Utils.isPackMagic(Utils.readMagic(in)))  break;
                if (verbose > 0)
                    Utils.log.info("Finished segment #"+seg);
            }
        }

        private void unpackSegment(InputStream in, JarOutputStream out) throws IOException {
            props.setProperty(java.util.jar.Pack200.Unpacker.PROGRESS,"0");
            // Process the output directory or jar output.
            new PackageReader(pkg, in).read();

            if (props.getBoolean("unpack.strip.debug"))    pkg.stripAttributeKind("Debug");
            if (props.getBoolean("unpack.strip.compile"))  pkg.stripAttributeKind("Compile");
            props.setProperty(java.util.jar.Pack200.Unpacker.PROGRESS,"50");
            pkg.ensureAllClassFiles();
            // Now write out the files.
            Set<Package.Class> classesToWrite = new HashSet<>(pkg.getClasses());
            for (Package.File file : pkg.getFiles()) {
                String name = file.nameString;
                JarEntry je = new JarEntry(Utils.getJarEntryName(name));
                boolean deflate;

                deflate = (keepDeflateHint)
                          ? (((file.options & Constants.FO_DEFLATE_HINT) != 0) ||
                            ((pkg.default_options & Constants.AO_DEFLATE_HINT) != 0))
                          : deflateHint;

                boolean needCRC = !deflate;  // STORE mode requires CRC

                if (needCRC)  crc.reset();
                bufOut.reset();
                if (file.isClassStub()) {
                    Package.Class cls = file.getStubClass();
                    assert(cls != null);
                    new ClassWriter(cls, needCRC ? crcOut : bufOut).write();
                    classesToWrite.remove(cls);  // for an error check
                } else {
                    // collect data & maybe CRC
                    file.writeTo(needCRC ? crcOut : bufOut);
                }
                je.setMethod(deflate ? JarEntry.DEFLATED : JarEntry.STORED);
                if (needCRC) {
                    if (verbose > 0)
                        Utils.log.info("stored size="+bufOut.size()+" and crc="+crc.getValue());

                    je.setMethod(JarEntry.STORED);
                    je.setSize(bufOut.size());
                    je.setCrc(crc.getValue());
                }
                if (keepModtime) {
                    LocalDateTime ldt = LocalDateTime
                            .ofEpochSecond(file.modtime, 0, ZoneOffset.UTC);
                    je.setTimeLocal(ldt);
                } else {
                    je.setTime((long)modtime * 1000);
                }
                out.putNextEntry(je);
                bufOut.writeTo(out);
                out.closeEntry();
                if (verbose > 0)
                    Utils.log.info("Writing "+Utils.zeString((ZipEntry)je));
            }
            assert(classesToWrite.isEmpty());
            props.setProperty(java.util.jar.Pack200.Unpacker.PROGRESS,"100");
            pkg.reset();  // reset for the next segment, if any
        }
    }
}
