/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import javax.xml.soap.AttachmentPart;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

/*
 * @test
 * @summary tests JAF DataHandler can be instantiated; test serialize and
 *   deserialize SOAP message containing xml attachment
 */
public class XmlTest {

    public void test() throws Exception {

        File file = new File("message.xml");
        file.deleteOnExit();

        MessageFactory mf = MessageFactory.newInstance();
        SOAPMessage msg = createMessage(mf);

        // Save the soap message to file
        try (FileOutputStream sentFile = new FileOutputStream(file)) {
            msg.writeTo(sentFile);
        }

        // See if we get the image object back
        try (FileInputStream fin = new FileInputStream(file)) {
            SOAPMessage newMsg = mf.createMessage(msg.getMimeHeaders(), fin);

            newMsg.writeTo(new ByteArrayOutputStream());

            Iterator<?> i = newMsg.getAttachments();
            while (i.hasNext()) {
                AttachmentPart att = (AttachmentPart) i.next();
                Object obj = att.getContent();
                if (!(obj instanceof StreamSource)) {
                    fail("Got incorrect attachment type [" + obj.getClass() + "], " +
                         "expected [javax.xml.transform.stream.StreamSource]");
                }
            }
        }

    }

    private SOAPMessage createMessage(MessageFactory mf) throws SOAPException, IOException {
        SOAPMessage msg = mf.createMessage();
        SOAPEnvelope envelope = msg.getSOAPPart().getEnvelope();
        Name name = envelope.createName("hello", "ex", "http://example.com");
        envelope.getBody().addChildElement(name).addTextNode("THERE!");

        String s = "<root><hello>THERE!</hello></root>";

        AttachmentPart ap = msg.createAttachmentPart(
                new StreamSource(new ByteArrayInputStream(s.getBytes())),
                "text/xml"
        );
        msg.addAttachmentPart(ap);
        msg.saveChanges();

        return msg;
    }

    private void fail(String s) {
        throw new RuntimeException(s);
    }

    public static void main(String[] args) throws Exception {
        new XmlTest().test();
    }

}
