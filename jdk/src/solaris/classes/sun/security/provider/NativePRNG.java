/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

import java.io.*;

import java.security.*;
import java.security.SecureRandom;

/**
 * Native PRNG implementation for Solaris/Linux. It interacts with
 * /dev/random and /dev/urandom, so it is only available if those
 * files are present. Otherwise, SHA1PRNG is used instead of this class.
 *
 * getSeed() and setSeed() directly read/write /dev/random. However,
 * /dev/random is only writable by root in many configurations. Because
 * we cannot just ignore bytes specified via setSeed(), we keep a
 * SHA1PRNG around in parallel.
 *
 * nextBytes() reads the bytes directly from /dev/urandom (and then
 * mixes them with bytes from the SHA1PRNG for the reasons explained
 * above). Reading bytes from /dev/urandom means that constantly get
 * new entropy the operating system has collected. This is a notable
 * advantage over the SHA1PRNG model, which acquires entropy only
 * initially during startup although the VM may be running for months.
 *
 * Also note that we do not need any initial pure random seed from
 * /dev/random. This is an advantage because on some versions of Linux
 * it can be exhausted very quickly and could thus impact startup time.
 *
 * Finally, note that we use a singleton for the actual work (RandomIO)
 * to avoid having to open and close /dev/[u]random constantly. However,
 * there may me many NativePRNG instances created by the JCA framework.
 *
 * @since   1.5
 * @author  Andreas Sterbenz
 */
public final class NativePRNG extends SecureRandomSpi {

    private static final long serialVersionUID = -6599091113397072932L;

    // name of the pure random file (also used for setSeed())
    private static final String NAME_RANDOM = "/dev/random";
    // name of the pseudo random file
    private static final String NAME_URANDOM = "/dev/urandom";

    // singleton instance or null if not available
    private static final RandomIO INSTANCE = initIO();

    private static RandomIO initIO() {
        return AccessController.doPrivileged(
            new PrivilegedAction<RandomIO>() {
                public RandomIO run() {
                File randomFile = new File(NAME_RANDOM);
                if (randomFile.exists() == false) {
                    return null;
                }
                File urandomFile = new File(NAME_URANDOM);
                if (urandomFile.exists() == false) {
                    return null;
                }
                try {
                    return new RandomIO(randomFile, urandomFile);
                } catch (Exception e) {
                    return null;
                }
            }
        });
    }

    // return whether the NativePRNG is available
    static boolean isAvailable() {
        return INSTANCE != null;
    }

    // constructor, called by the JCA framework
    public NativePRNG() {
        super();
        if (INSTANCE == null) {
            throw new AssertionError("NativePRNG not available");
        }
    }

    // set the seed
    protected void engineSetSeed(byte[] seed) {
        INSTANCE.implSetSeed(seed);
    }

    // get pseudo random bytes
    protected void engineNextBytes(byte[] bytes) {
        INSTANCE.implNextBytes(bytes);
    }

    // get true random bytes
    protected byte[] engineGenerateSeed(int numBytes) {
        return INSTANCE.implGenerateSeed(numBytes);
    }

    /**
     * Nested class doing the actual work. Singleton, see INSTANCE above.
     */
    private static class RandomIO {

        // we buffer data we read from /dev/urandom for efficiency,
        // but we limit the lifetime to avoid using stale bits
        // lifetime in ms, currently 100 ms (0.1 s)
        private final static long MAX_BUFFER_TIME = 100;

        // size of the /dev/urandom buffer
        private final static int BUFFER_SIZE = 32;

        // In/OutputStream for /dev/random and /dev/urandom
        private final InputStream randomIn, urandomIn;
        private OutputStream randomOut;

        // flag indicating if we have tried to open randomOut yet
        private boolean randomOutInitialized;

        // SHA1PRNG instance for mixing
        // initialized lazily on demand to avoid problems during startup
        private volatile sun.security.provider.SecureRandom mixRandom;

        // buffer for /dev/urandom bits
        private final byte[] urandomBuffer;

        // number of bytes left in urandomBuffer
        private int buffered;

        // time we read the data into the urandomBuffer
        private long lastRead;

        // mutex lock for nextBytes()
        private final Object LOCK_GET_BYTES = new Object();

        // mutex lock for getSeed()
        private final Object LOCK_GET_SEED = new Object();

        // mutex lock for setSeed()
        private final Object LOCK_SET_SEED = new Object();

        // constructor, called only once from initIO()
        private RandomIO(File randomFile, File urandomFile) throws IOException {
            randomIn = new FileInputStream(randomFile);
            urandomIn = new FileInputStream(urandomFile);
            urandomBuffer = new byte[BUFFER_SIZE];
        }

        // get the SHA1PRNG for mixing
        // initialize if not yet created
        private sun.security.provider.SecureRandom getMixRandom() {
            sun.security.provider.SecureRandom r = mixRandom;
            if (r == null) {
                synchronized (LOCK_GET_BYTES) {
                    r = mixRandom;
                    if (r == null) {
                        r = new sun.security.provider.SecureRandom();
                        try {
                            byte[] b = new byte[20];
                            readFully(urandomIn, b);
                            r.engineSetSeed(b);
                        } catch (IOException e) {
                            throw new ProviderException("init failed", e);
                        }
                        mixRandom = r;
                    }
                }
            }
            return r;
        }

        // read data.length bytes from in
        // /dev/[u]random are not normal files, so we need to loop the read.
        // just keep trying as long as we are making progress
        private static void readFully(InputStream in, byte[] data)
                throws IOException {
            int len = data.length;
            int ofs = 0;
            while (len > 0) {
                int k = in.read(data, ofs, len);
                if (k <= 0) {
                    throw new EOFException("/dev/[u]random closed?");
                }
                ofs += k;
                len -= k;
            }
            if (len > 0) {
                throw new IOException("Could not read from /dev/[u]random");
            }
        }

        // get true random bytes, just read from /dev/random
        private byte[] implGenerateSeed(int numBytes) {
            synchronized (LOCK_GET_SEED) {
                try {
                    byte[] b = new byte[numBytes];
                    readFully(randomIn, b);
                    return b;
                } catch (IOException e) {
                    throw new ProviderException("generateSeed() failed", e);
                }
            }
        }

        // supply random bytes to the OS
        // write to /dev/random if possible
        // always add the seed to our mixing random
        private void implSetSeed(byte[] seed) {
            synchronized (LOCK_SET_SEED) {
                if (randomOutInitialized == false) {
                    randomOutInitialized = true;
                    randomOut = AccessController.doPrivileged(
                            new PrivilegedAction<OutputStream>() {
                        public OutputStream run() {
                            try {
                                return new FileOutputStream(NAME_RANDOM, true);
                            } catch (Exception e) {
                                return null;
                            }
                        }
                    });
                }
                if (randomOut != null) {
                    try {
                        randomOut.write(seed);
                    } catch (IOException e) {
                        throw new ProviderException("setSeed() failed", e);
                    }
                }
                getMixRandom().engineSetSeed(seed);
            }
        }

        // ensure that there is at least one valid byte in the buffer
        // if not, read new bytes
        private void ensureBufferValid() throws IOException {
            long time = System.currentTimeMillis();
            if ((buffered > 0) && (time - lastRead < MAX_BUFFER_TIME)) {
                return;
            }
            lastRead = time;
            readFully(urandomIn, urandomBuffer);
            buffered = urandomBuffer.length;
        }

        // get pseudo random bytes
        // read from /dev/urandom and XOR with bytes generated by the
        // mixing SHA1PRNG
        private void implNextBytes(byte[] data) {
            synchronized (LOCK_GET_BYTES) {
                try {
                    getMixRandom().engineNextBytes(data);
                    int len = data.length;
                    int ofs = 0;
                    while (len > 0) {
                        ensureBufferValid();
                        int bufferOfs = urandomBuffer.length - buffered;
                        while ((len > 0) && (buffered > 0)) {
                            data[ofs++] ^= urandomBuffer[bufferOfs++];
                            len--;
                            buffered--;
                        }
                    }
                } catch (IOException e) {
                    throw new ProviderException("nextBytes() failed", e);
                }
            }
        }

    }

}
