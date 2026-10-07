/*
 * Copyright (c) 2002, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www.protocol.http.ntlm;

import java.io.IOException;
import java.lang.ref.Cleaner.Cleanable;
import java.util.Base64;
import jdk.internal.ref.CleanerFactory;

/*
 * Hooks into Windows implementation of NTLM.
 * This class will be replaced if a cross-platform version of NTLM
 * is implemented in the future.
 */

public class NTLMAuthSequence implements AutoCloseable {

    private String username;
    private String password;
    private String ntdomain;
    private int state;
    private NativeHandles handles;

    static {
        initFirst(Status.class, NativeHandles.class);
    }

    // Used by native code to indicate when a particular protocol sequence is completed
    // and must not be re-used.

    static class Status {
        boolean sequenceComplete;
    }

    // Holds the native CredHandle* and CtxtHandle* pointers
    static class NativeHandles implements Runnable {
        long crdHandle;
        long ctxHandle;
        private final Cleanable cleanable;

        NativeHandles(NTLMAuthSequence owner, long crdHandle) {
            this.crdHandle = crdHandle;
            this.ctxHandle = 0L;
            this.cleanable = CleanerFactory.cleaner().register(owner, this);
        }

        synchronized long crdHandle() { return crdHandle; }

        synchronized void setCtxHandle(long h) { ctxHandle = h; }

        // Called by endSequence in native code after freeing handles, to prevent double-free
        synchronized void clearHandles() {
            crdHandle = 0L;
            ctxHandle = 0L;
        }

        void clean() { cleanable.clean(); }

        @Override
        public void run() {
            long crd, ctx;
            synchronized (this) {
                crd = crdHandle;
                ctx = ctxHandle;
                crdHandle = 0L;
                ctxHandle = 0L;
            }
            if (crd != 0L || ctx != 0L) {
                freeHandles(crd, ctx);
            }
        }
    }

    Status status;

    @SuppressWarnings("this-escape")
    NTLMAuthSequence (String username, String password, String ntdomain)
    throws IOException
    {
        this.username = username;
        this.password = password;
        this.ntdomain = ntdomain;
        this.status = new Status();
        state = 0;
        long crd = getCredentialsHandle(username, ntdomain, password);
        if (crd == 0) {
            throw new IOException("could not get credentials handle");
        }
        this.handles = new NativeHandles(this, crd);
    }

    public String getAuthHeader (String token) throws IOException {
        byte[] input = null;

        assert !status.sequenceComplete;

        if (token != null)
            input = Base64.getDecoder().decode(token);
        byte[] b = getNextToken(handles.crdHandle(), input, status);
        if (b == null)
            throw new IOException("Internal authentication error");
        return Base64.getEncoder().encodeToString(b);
    }

    public boolean isComplete() {
        return status.sequenceComplete;
    }

    @Override
    public void close() {
        handles.clean();
    }

    private static native void initFirst(Class<NTLMAuthSequence.Status> statusClazz, Class<NTLMAuthSequence.NativeHandles> handlesClazz);

    private native long getCredentialsHandle (String user, String domain, String password);

    private native byte[] getNextToken (long crdHandle, byte[] lastToken, Status returned)
            throws IOException;

    static native void freeHandles(long crdHandle, long ctxHandle);
}
