/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
import sun.misc.BASE64Encoder;
import sun.misc.BASE64Decoder;

/*
 * Hooks into Windows implementation of NTLM.
 * This class will be replaced if a cross-platform version of NTLM
 * is implemented in the future.
 */

public class NTLMAuthSequence {

    private String username;
    private String password;
    private String ntdomain;
    private int state;
    private long crdHandle;
    private long ctxHandle;

    static {
        initFirst();
    }

    NTLMAuthSequence (String username, String password, String ntdomain)
    throws IOException
    {
        this.username = username;
        this.password = password;
        this.ntdomain = ntdomain;
        state = 0;
        crdHandle = getCredentialsHandle (username, ntdomain, password);
        if (crdHandle == 0) {
            throw new IOException ("could not get credentials handle");
        }
    }

    public String getAuthHeader (String token) throws IOException {
        byte[] input = null;
        if (token != null)
            input = (new BASE64Decoder()).decodeBuffer(token);
        byte[] b = getNextToken (crdHandle, input);
        if (b == null)
            throw new IOException ("Internal authentication error");
        return (new B64Encoder()).encode (b);
    }

    private native static void initFirst ();

    private native long getCredentialsHandle (String user, String domain, String password);

    private native byte[] getNextToken (long crdHandle, byte[] lastToken);
}

class B64Encoder extends BASE64Encoder {
    protected int bytesPerLine () {
        return 1024;
    }
}
