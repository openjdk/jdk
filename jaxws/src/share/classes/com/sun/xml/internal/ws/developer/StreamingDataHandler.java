/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.ws.developer;

import com.sun.xml.internal.org.jvnet.mimepull.MIMEPart;

import javax.activation.DataSource;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.File;
import java.net.URL;

/**
 * Implementation of {@link com.sun.xml.internal.org.jvnet.staxex.StreamingDataHandler} to access MIME
 * attachments efficiently. Applications can use the additional methods and decide
 * on how to access the attachment data in JAX-WS applications.
 *
 * <p>
 * for e.g.:
 *
 * DataHandler dh = proxy.getData();
 * StreamingDataHandler sdh = (StreamingDataHandler)dh;
 * // readOnce() doesn't store attachment on the disk in some cases
 * // for e.g when only one huge attachment after soap envelope part in MIME message
 * InputStream in = sdh.readOnce();
 * ...
 * in.close();
 * sdh.close();
 *
 * @author Jitendra Kotamraju
 */
public abstract class StreamingDataHandler extends com.sun.xml.internal.org.jvnet.staxex.StreamingDataHandler {

    public StreamingDataHandler(Object o, String s) {
        super(o, s);
    }

    public StreamingDataHandler(URL url) {
        super(url);
    }

    public StreamingDataHandler(DataSource dataSource) {
        super(dataSource);
    }

}
