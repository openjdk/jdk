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
package jaxp.library;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import static org.testng.Assert.fail;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * This is an interface provide basic support for JAXP functional test.
 */
public class JAXPTestUtilities {
    /**
     * Prefix for error message.
     */
    public static final String ERROR_MSG_HEADER = "Unexcepted exception thrown:";

    /**
     * Prefix for error message on clean up block.
     */
    public static final String ERROR_MSG_CLEANUP = "Clean up failed on %s";

    /**
     * Force using slash as File separator as we always use cygwin to test in
     * Windows platform.
     */
    public static final String FILE_SEP = "/";

    /**
     * User home.
     */
    public static final String USER_DIR = System.getProperty("user.dir", ".");

    /**
     * TEMP file directory.
     */
    public static final String TEMP_DIR = System.getProperty("java.io.tmpdir", ".");

    /**
     * BOM table for storing BOM header.
     */
    private final static Map<String, byte[]> bom = new HashMap();

    /**
     * Initialize all BOM headers.
     */
    static {
        bom.put("UTF-8", new byte[]{(byte)0xEF, (byte) 0xBB, (byte) 0xBF});
        bom.put("UTF-16BE", new byte[]{(byte)0xFE, (byte)0xFF});
        bom.put("UTF-16LE", new byte[]{(byte)0xFF, (byte)0xFE});
        bom.put("UTF-32BE", new byte[]{(byte)0x00, (byte)0x00, (byte)0xFE, (byte)0xFF});
        bom.put("UTF-32LE", new byte[]{(byte)0xFF, (byte)0xFE, (byte)0x00, (byte)0x00});
    }

    /**
     * Compare contents of golden file with test output file line by line.
     * return true if they're identical.
     * @param goldfile Golden output file name
     * @param outputfile Test output file name
     * @return true if two files are identical.
     *         false if two files are not identical.
     * @throws IOException if an I/O error occurs reading from the file or a
     *         malformed or unmappable byte sequence is read
     */
    public static boolean compareWithGold(String goldfile, String outputfile)
            throws IOException {
        return Files.readAllLines(Paths.get(goldfile)).
                equals(Files.readAllLines(Paths.get(outputfile)));
    }

    /**
     * Compare contents of golden file with test output file by their document
     * representation.
     * Here we ignore the white space and comments. return true if they're
     * lexical identical.
     * @param goldfile Golden output file name.
     * @param resultFile Test output file name.
     * @return true if two file's document representation are identical.
     *         false if two file's document representation are not identical.
     * @throws javax.xml.parsers.ParserConfigurationException if the
     *         implementation is not available or cannot be instantiated.
     * @throws SAXException If any parse errors occur.
     * @throws IOException if an I/O error occurs reading from the file or a
     *         malformed or unmappable byte sequence is read .
     */
    public static boolean compareDocumentWithGold(String goldfile, String resultFile)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setCoalescing(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        DocumentBuilder db = factory.newDocumentBuilder();

        Document goldD = db.parse(Paths.get(goldfile).toFile());
        goldD.normalizeDocument();
        Document resultD = db.parse(Paths.get(resultFile).toFile());
        resultD.normalizeDocument();
        return goldD.isEqualNode(resultD);
    }
    /**
     * Convert stream to ByteArrayInputStream by given character set.
     * @param charset target character set.
     * @param file a file that contains no BOM head content.
     * @return a ByteArrayInputStream contains BOM heads and bytes in original
     *         stream
     * @throws IOException I/O operation failed or unsupported character set.
     */
    public static InputStream bomStream(String charset, String file)
            throws IOException {
        String localCharset = charset;
        if (charset.equals("UTF-16") || charset.equals("UTF-32")) {
            localCharset
                += ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN ? "BE" : "LE";
        }
        if (!bom.containsKey(localCharset))
            throw new UnsupportedCharsetException("Charset:" + localCharset);

        byte[] content = Files.readAllLines(Paths.get(file)).stream().
                collect(Collectors.joining()).getBytes(localCharset);
        byte[] head = bom.get(localCharset);
        ByteBuffer bb = ByteBuffer.allocate(content.length + head.length);
        bb.put(head);
        bb.put(content);
        return new ByteArrayInputStream(bb.array());
    }

    /**
     * Prints error message if an exception is thrown
     * @param ex The exception is thrown by test.
     */
    public static void failUnexpected(Throwable ex) {
        fail(ERROR_MSG_HEADER, ex);
    }

    /**
     * Prints error message if an exception is thrown when clean up a file.
     * @param ex The exception is thrown in cleaning up a file.
     * @param name Cleaning up file name.
     */
    public static void failCleanup(IOException ex, String name) {
        fail(String.format(ERROR_MSG_CLEANUP, name), ex);
    }
}
