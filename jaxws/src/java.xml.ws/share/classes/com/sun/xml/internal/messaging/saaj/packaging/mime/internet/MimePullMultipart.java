/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.messaging.saaj.packaging.mime.internet;

import com.sun.xml.internal.messaging.saaj.packaging.mime.MessagingException;
import com.sun.xml.internal.messaging.saaj.soap.AttachmentPartImpl;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.activation.DataSource;
import com.sun.xml.internal.org.jvnet.mimepull.MIMEConfig;
import com.sun.xml.internal.org.jvnet.mimepull.MIMEMessage;
import com.sun.xml.internal.org.jvnet.mimepull.MIMEPart;

/**
 *
 * @author Kumar
 */
public class MimePullMultipart  extends MimeMultipart {

    private InputStream in = null;
    private String boundary = null;
    private MIMEMessage mm = null;
    private DataSource dataSource = null;
    private ContentType contType = null;
    private String startParam = null;
    private MIMEPart soapPart = null;

    public MimePullMultipart(DataSource ds, ContentType ct)
        throws MessagingException {
        parsed = false;
        if (ct==null)
            contType = new ContentType(ds.getContentType());
        else
            contType = ct;

        dataSource = ds;
        boundary = contType.getParameter("boundary");
    }

    public  MIMEPart readAndReturnSOAPPart() throws  MessagingException {
         if (soapPart != null) {
            throw new MessagingException("Inputstream from datasource was already consumed");
         }
         readSOAPPart();
         return soapPart;

    }

    protected  void readSOAPPart() throws  MessagingException {
        try {
            if (soapPart != null) {
                return;
            }
            in = dataSource.getInputStream();
            MIMEConfig config = new MIMEConfig(); //use defaults
            mm = new MIMEMessage(in, boundary, config);
            String st = contType.getParameter("start");
            if(startParam == null) {
                soapPart = mm.getPart(0);
            } else {
                  // Strip <...> from root part's Content-I
                if (st != null && st.length() > 2 && st.charAt(0) == '<' && st.charAt(st.length()-1) == '>') {
                    st = st.substring(1, st.length()-1);
                }
                startParam = st;
                soapPart = mm.getPart(startParam);

            }
        } catch (IOException ex) {
            throw new MessagingException("No inputstream from datasource", ex);
        }
    }

    public void parseAll() throws MessagingException {
        if (parsed) {
            return;
        }
        if (soapPart == null) {
            readSOAPPart();
        }

        List<MIMEPart> prts = mm.getAttachments();
        for(MIMEPart part : prts) {
            if (part != soapPart) {
                new AttachmentPartImpl(part);
                this.addBodyPart(new MimeBodyPart(part));
            }
       }
       parsed = true;
    }

    protected  void parse() throws MessagingException {
        parseAll();
    }

}
