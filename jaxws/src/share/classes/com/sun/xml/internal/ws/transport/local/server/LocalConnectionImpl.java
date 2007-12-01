/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.transport.local.server;
import java.util.List;
import java.util.Map;
import com.sun.xml.internal.ws.transport.WSConnectionImpl;
import com.sun.xml.internal.ws.transport.local.LocalMessage;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;

import java.io.InputStream;
import java.io.OutputStream;


/**
 * @author WS Development Team
 *
 * Server-side Local transport implementation
 */
public class LocalConnectionImpl extends WSConnectionImpl {
    private int status;
    private LocalMessage lm;

    public LocalConnectionImpl (LocalMessage localMessage) {
        this.lm = localMessage;
    }

    public Map<String,List<String>> getHeaders () {
        return lm.getHeaders ();
    }

    /**
     * sets response headers.
     */
    public void setHeaders (Map<String,List<String>> headers) {
        lm.setHeaders (headers);
    }

    public void setStatus (int status) {
        this.status = status;
    }

    public InputStream getInput () {
        return lm.getOutput().newInputStream();
    }

    public OutputStream getOutput () {
        ByteArrayBuffer bab = new ByteArrayBuffer();
        lm.setOutput(bab);
        return bab;
    }
}
