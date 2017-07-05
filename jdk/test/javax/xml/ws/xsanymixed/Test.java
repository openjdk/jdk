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
 * @bug 8036981 8038966 8051441
 * @summary the content of xs:any content:mixed should remain as is,
 *          no white space changes and no changes to namespace prefixes
 * @run shell compile-wsdl.sh
 * @run main/othervm Test
 */

import com.sun.net.httpserver.HttpServer;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.FileVisitResult.CONTINUE;

public class Test {

    private static HttpServer httpServer;
    private static Endpoint endpoint;
    private static final String NL = System.getProperty("line.separator");

    private static final String XS_ANY_MIXED_PART =
            "<AppHdr xmlns=\"urn:head.001\">" + NL +
            "      <Fr>" + NL + NL +
            "<FIId xmlns=\"urn:head.009\">" + NL + NL +
            "        any" + NL +
            "    white" + NL +
            "      space" + NL + NL +
            "        <FinInstnId>... and" + NL + NL +
            "            NO namespace prefixes!!!" + NL + NL +
            "        </FinInstnId>" + NL + NL +
            "  </FIId>" + NL +
            "</Fr>" + NL +
            "</AppHdr>";

    private static final String XML_REQUEST = "<soap:Envelope " +
            "xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "xmlns:ws=\"http://ws.somewhere.org/\">" +
            "<soap:Header/><soap:Body>" +
            "<ws:echoRequest>" + NL +
                XS_ANY_MIXED_PART + NL +
            "</ws:echoRequest>" +
            "</soap:Body></soap:Envelope>";

    private static String deployWebservice() throws IOException {
        // Manually create HttpServer here using ephemeral address for port
        // so as to not end up with attempt to bind to an in-use port
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.start();
        endpoint = Endpoint.create(new ServiceImpl());
        endpoint.publish(httpServer.createContext("/wservice"));

        String wsdlAddress = "http://localhost:" + httpServer.getAddress().getPort() + "/wservice?wsdl";
        log("address = " + wsdlAddress);
        return wsdlAddress;
    }

    private static void stopWebservice() {
        if (endpoint != null && endpoint.isPublished()) {
            endpoint.stop();
        }
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    public static void main(String[] args) throws IOException, TransformerException {

        try {
            String address = deployWebservice();
            Service service = Service.create(new URL(address), ServiceImpl.SERVICE_NAME);

            Dispatch<Source> d = service.createDispatch(ServiceImpl.PORT_NAME, Source.class, Service.Mode.MESSAGE);
            Source response = d.invoke(new StreamSource(new StringReader(XML_REQUEST)));

            String resultXml = toString(response);

            log("= request ======== \n");
            log(XML_REQUEST);
            log("= result ========= \n");
            log(resultXml);
            log("\n==================");

            boolean xsAnyMixedPartSame = resultXml.contains(XS_ANY_MIXED_PART);
            log("resultXml.contains(XS_ANY_PART) = " + xsAnyMixedPartSame);
            if (!xsAnyMixedPartSame) {
                fail("The xs:any content=mixed part is supposed to be same in request and response.");
                throw new RuntimeException();
            }

            log("TEST PASSED");
        } finally {
            stopWebservice();

            // if you need to debug or explore wsdl generation result
            // comment this line out:
            deleteGeneratedFiles();
        }
    }

    private static String toString(Source response) throws TransformerException, IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.transform(response, new StreamResult(bos));
        bos.close();
        return new String(bos.toByteArray());
    }

    private static void fail(String message) {
        log("TEST FAILED.");
        throw new RuntimeException(message);
    }

    private static void log(String msg) {
        System.out.println(msg);
    }

    private static void deleteGeneratedFiles() {
        Path p = Paths.get("..", "classes", "javax", "xml", "ws", "xsanymixed", "org");
        System.out.println("performing cleanup, deleting wsdl compilation result: " + p.toFile().getAbsolutePath());
        if (Files.exists(p)) {
            try {
                Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(
                            Path file,
                            BasicFileAttributes attrs) throws IOException {

                        System.out.println("deleting file [" + file.toFile().getAbsoluteFile() + "]");
                        Files.delete(file);
                        return CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(
                            Path dir,
                            IOException exc) throws IOException {

                        System.out.println("deleting dir [" + dir.toFile().getAbsoluteFile() + "]");
                        if (exc == null) {
                            Files.delete(dir);
                            return CONTINUE;
                        } else {
                            throw exc;
                        }
                    }
                });
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

}
