/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class NativeUnpack {
    // Pointer to the native unpacker obj
    private long unpackerPtr;

    // Input stream.
    private BufferedInputStream in;

    private static synchronized native void initIDs();

    // Starts processing at the indicated position in the buffer.
    // If the buffer is null, the readInputFn callback is used to get bytes.
    // Returns (s<<32|f), the number of following segments and files.
    private synchronized native long start(ByteBuffer buf, long offset);

    // Returns true if there's another, and fills in the parts.
    private synchronized native boolean getNextFile(Object[] parts);

    private synchronized native ByteBuffer getUnusedInput();

    // Resets the engine and frees all resources.
    // Returns total number of bytes consumed by the engine.
    private synchronized native long finish();

    // Setting state in the unpacker.
    protected  synchronized native boolean setOption(String opt, String value);
    protected  synchronized native String getOption(String opt);

    private  int _verbose;

    // State for progress bar:
    private  long _byteCount;      // bytes read in current segment
    private  int  _segCount;       // number of segs scanned
    private  int  _fileCount;      // number of files written
    private  long _estByteLimit;   // estimate of eventual total
    private  int  _estSegLimit;    // ditto
    private  int  _estFileLimit;   // ditto
    private  int  _prevPercent = -1; // for monotonicity

    private final CRC32   _crc32 = new CRC32();
    private       byte[]  _buf   = new byte[1<<14];

    private  UnpackerImpl _p200;
    private  PropMap _props;

    static {
        // If loading from stand alone build uncomment this.
        // System.loadLibrary("unpack");
        java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Void>() {
                public Void run() {
                    System.loadLibrary("unpack");
                    return null;
                }
            });
        initIDs();
    }

    NativeUnpack(UnpackerImpl p200) {
        super();
        _p200  = p200;
        _props = p200.props;
        p200._nunp = this;
    }

    // for JNI callbacks
    static private Object currentInstance() {
        UnpackerImpl p200 = (UnpackerImpl) Utils.getTLGlobals();
        return (p200 == null)? null: p200._nunp;
    }

    private synchronized long getUnpackerPtr() {
        return unpackerPtr;
    }

    // Callback from the unpacker engine to get more data.
    private long readInputFn(ByteBuffer pbuf, long minlen) throws IOException {
        if (in == null)  return 0;  // nothing is readable
        long maxlen = pbuf.capacity() - pbuf.position();
        assert(minlen <= maxlen);  // don't talk nonsense
        long numread = 0;
        int steps = 0;
        while (numread < minlen) {
            steps++;
            // read available input, up to buf.length or maxlen
            int readlen = _buf.length;
            if (readlen > (maxlen - numread))
                readlen = (int)(maxlen - numread);
            int nr = in.read(_buf, 0, readlen);
            if (nr <= 0)  break;
            numread += nr;
            assert(numread <= maxlen);
            // %%% get rid of this extra copy by using nio?
            pbuf.put(_buf, 0, nr);
        }
        if (_verbose > 1)
            Utils.log.fine("readInputFn("+minlen+","+maxlen+") => "+numread+" steps="+steps);
        if (maxlen > 100) {
            _estByteLimit = _byteCount + maxlen;
        } else {
            _estByteLimit = (_byteCount + numread) * 20;
        }
        _byteCount += numread;
        updateProgress();
        return numread;
    }

    private void updateProgress() {
        // Progress is a combination of segment reading and file writing.
        final double READ_WT  = 0.33;
        final double WRITE_WT = 0.67;
        double readProgress = _segCount;
        if (_estByteLimit > 0 && _byteCount > 0)
            readProgress += (double)_byteCount / _estByteLimit;
        double writeProgress = _fileCount;
        double scaledProgress
            = READ_WT  * readProgress  / Math.max(_estSegLimit,1)
            + WRITE_WT * writeProgress / Math.max(_estFileLimit,1);
        int percent = (int) Math.round(100*scaledProgress);
        if (percent > 100)  percent = 100;
        if (percent > _prevPercent) {
            _prevPercent = percent;
            _props.setInteger(Pack200.Unpacker.PROGRESS, percent);
            if (_verbose > 0)
                Utils.log.info("progress = "+percent);
        }
    }

    private void copyInOption(String opt) {
        String val = _props.getProperty(opt);
        if (_verbose > 0)
            Utils.log.info("set "+opt+"="+val);
        if (val != null) {
            boolean set = setOption(opt, val);
            if (!set)
                Utils.log.warning("Invalid option "+opt+"="+val);
        }
    }

    void run(InputStream inRaw, JarOutputStream jstream,
             ByteBuffer presetInput) throws IOException {
        BufferedInputStream in0 = new BufferedInputStream(inRaw);
        this.in = in0;    // for readInputFn to see
        _verbose = _props.getInteger(Utils.DEBUG_VERBOSE);
        // Fix for BugId: 4902477, -unpack.modification.time = 1059010598000
        // TODO eliminate and fix in unpack.cpp

        final int modtime = Pack200.Packer.KEEP.equals(_props.getProperty(Utils.UNPACK_MODIFICATION_TIME, "0")) ?
                Constants.NO_MODTIME : _props.getTime(Utils.UNPACK_MODIFICATION_TIME);

        copyInOption(Utils.DEBUG_VERBOSE);
        copyInOption(Pack200.Unpacker.DEFLATE_HINT);
        if (modtime == Constants.NO_MODTIME)  // Don't pass KEEP && NOW
            copyInOption(Utils.UNPACK_MODIFICATION_TIME);
        updateProgress();  // reset progress bar
        for (;;) {
            // Read the packed bits.
            long counts = start(presetInput, 0);
            _byteCount = _estByteLimit = 0;  // reset partial scan counts
            ++_segCount;  // just finished scanning a whole segment...
            int nextSeg  = (int)( counts >>> 32 );
            int nextFile = (int)( counts >>>  0 );

            // Estimate eventual total number of segments and files.
            _estSegLimit = _segCount + nextSeg;
            double filesAfterThisSeg = _fileCount + nextFile;
            _estFileLimit = (int)( (filesAfterThisSeg *
                                    _estSegLimit) / _segCount );

            // Write the files.
            int[] intParts = { 0,0, 0, 0 };
            //    intParts = {size.hi/lo, mod, defl}
            Object[] parts = { intParts, null, null, null };
            //       parts = { {intParts}, name, data0/1 }
            while (getNextFile(parts)) {
                //BandStructure.printArrayTo(System.out, intParts, 0, parts.length);
                String name = (String) parts[1];
                long   size = ( (long)intParts[0] << 32)
                            + (((long)intParts[1] << 32) >>> 32);

                long   mtime = (modtime != Constants.NO_MODTIME ) ?
                                modtime : intParts[2] ;
                boolean deflateHint = (intParts[3] != 0);
                ByteBuffer data0 = (ByteBuffer) parts[2];
                ByteBuffer data1 = (ByteBuffer) parts[3];
                writeEntry(jstream, name, mtime, size, deflateHint,
                           data0, data1);
                ++_fileCount;
                updateProgress();
            }
            presetInput = getUnusedInput();
            long consumed = finish();
            if (_verbose > 0)
                Utils.log.info("bytes consumed = "+consumed);
            if (presetInput == null &&
                !Utils.isPackMagic(Utils.readMagic(in0))) {
                break;
            }
            if (_verbose > 0 ) {
                if (presetInput != null)
                    Utils.log.info("unused input = "+presetInput);
            }
        }
    }

    void run(InputStream in, JarOutputStream jstream) throws IOException {
        run(in, jstream, null);
    }

    void run(File inFile, JarOutputStream jstream) throws IOException {
        // %%% maybe memory-map the file, and pass it straight into unpacker
        ByteBuffer mappedFile = null;
        try (FileInputStream fis = new FileInputStream(inFile)) {
            run(fis, jstream, mappedFile);
        }
        // Note:  caller is responsible to finish with jstream.
    }

    private void writeEntry(JarOutputStream j, String name,
                            long mtime, long lsize, boolean deflateHint,
                            ByteBuffer data0, ByteBuffer data1) throws IOException {
        int size = (int)lsize;
        if (size != lsize)
            throw new IOException("file too large: "+lsize);

        CRC32 crc32 = _crc32;

        if (_verbose > 1)
            Utils.log.fine("Writing entry: "+name+" size="+size
                             +(deflateHint?" deflated":""));

        if (_buf.length < size) {
            int newSize = size;
            while (newSize < _buf.length) {
                newSize <<= 1;
                if (newSize <= 0) {
                    newSize = size;
                    break;
                }
            }
            _buf = new byte[newSize];
        }
        assert(_buf.length >= size);

        int fillp = 0;
        if (data0 != null) {
            int size0 = data0.capacity();
            data0.get(_buf, fillp, size0);
            fillp += size0;
        }
        if (data1 != null) {
            int size1 = data1.capacity();
            data1.get(_buf, fillp, size1);
            fillp += size1;
        }
        while (fillp < size) {
            // Fill in rest of data from the stream itself.
            int nr = in.read(_buf, fillp, size - fillp);
            if (nr <= 0)  throw new IOException("EOF at end of archive");
            fillp += nr;
        }

        ZipEntry z = new ZipEntry(name);
        z.setTime(mtime * 1000);

        if (size == 0) {
            z.setMethod(ZipOutputStream.STORED);
            z.setSize(0);
            z.setCrc(0);
            z.setCompressedSize(0);
        } else if (!deflateHint) {
            z.setMethod(ZipOutputStream.STORED);
            z.setSize(size);
            z.setCompressedSize(size);
            crc32.reset();
            crc32.update(_buf, 0, size);
            z.setCrc(crc32.getValue());
        } else {
            z.setMethod(Deflater.DEFLATED);
            z.setSize(size);
        }

        j.putNextEntry(z);

        if (size > 0)
            j.write(_buf, 0, size);

        j.closeEntry();
        if (_verbose > 0) Utils.log.info("Writing " + Utils.zeString(z));
    }
}
