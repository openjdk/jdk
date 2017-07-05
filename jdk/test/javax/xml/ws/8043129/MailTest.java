/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8043129
 * @summary JAF initialisation in SAAJ clashing with the one in javax.mail
 * @author mkos
 * @library javax.mail.jar
 * @build MailTest
 * @run main MailTest
 */

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;

public class MailTest {

    String host = null;
    String user = "";
    String password = null;
    String from = null;
    String to = null;

    public static void main(String[] args) {
        MailTest t = new MailTest();

        t.user = "somebody@somewhere.com";
        t.from = "somebody@somewhere.com";
        t.to = "somebody@somewhere.com";

        t.user = "somebody@somewhere.com";
        t.password = "somepassword";
        t.host = "somehost";

        t.sendMail();    //this works

        t.addSoapAttachement();
        t.sendMail();    //after addAttachmentPart to soapmessage it do not work

        // workaroundJAFSetup();
        // t.sendMail();    //after workaround works again
    }

    void addSoapAttachement() {
        try {
            MessageFactory messageFactory = MessageFactory.newInstance();
            SOAPMessage message = messageFactory.createMessage();
            AttachmentPart a = message.createAttachmentPart();
            a.setContentType("binary/octet-stream");
            message.addAttachmentPart(a);
        } catch (SOAPException e) {
            e.printStackTrace();
        }
    }

    void sendMail() {

        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.auth", "true");

            Session session = Session.getInstance(props);
            session.setDebug(true);

            // Define message
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.addRecipients(Message.RecipientType.TO, to);
            message.setSubject("this is a multipart test");

            Multipart multipart = new MimeMultipart();

            BodyPart messageBodyPart1 = new MimeBodyPart();
            messageBodyPart1.setText("please send also this Content\n ciao!");
            multipart.addBodyPart(messageBodyPart1);

            BodyPart messageBodyPart2 = new MimeBodyPart();
            messageBodyPart2.setContent("<b>please</b> send also this Content <br>ciao!", "text/html; charset=UTF-8");
            multipart.addBodyPart(messageBodyPart2);

            message.setContent(multipart);

            /*
                Transport tr = session.getTransport("smtp");
                tr.connect(host,user, password);
                tr.sendMessage(message,InternetAddress.parse(to));
                tr.close();
            */

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            message.writeTo(baos);
            String output = baos.toString();
            System.out.println("output = " + output);
            if (output.contains("also this Content")) {
                System.out.println("Test PASSED.");
            } else {
                System.out.println("Test FAILED, missing content.");
                throw new IllegalStateException("Test FAILED, missing content.");
            }
        } catch (MessagingException ignored) {
        } catch (IOException ignored) {
        }
    }

    // this is how the error can be worked around ...
    static void workaroundJAFSetup() {
        MailcapCommandMap mailMap = (MailcapCommandMap) CommandMap.getDefaultCommandMap();
        mailMap.addMailcap("multipart/mixed;;x-java-content-handler=com.sun.mail.handlers.multipart_mixed");
    }
}
