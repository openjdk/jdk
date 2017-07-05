/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A collection of different, general-purpose methods for JAVA-specific things
 * @author Christian Geuer-Pollmann
 */
public class JavaUtils {

    /** {@link org.apache.commons.logging} logging facility */
    private static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(JavaUtils.class.getName());

    private JavaUtils() {
        // we don't allow instantiation
    }

    /**
     * Method getBytesFromFile
     *
     * @param fileName
     * @return the bytes read from the file
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static byte[] getBytesFromFile(String fileName)
        throws FileNotFoundException, IOException {

        byte refBytes[] = null;

        FileInputStream fisRef = null;
        UnsyncByteArrayOutputStream baos = null;
        try {
            fisRef = new FileInputStream(fileName);
            baos = new UnsyncByteArrayOutputStream();
            byte buf[] = new byte[1024];
            int len;

            while ((len = fisRef.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }

            refBytes = baos.toByteArray();
        } finally {
            if (baos != null) {
                baos.close();
            }
            if (fisRef != null) {
                fisRef.close();
            }
        }

        return refBytes;
    }

    /**
     * Method writeBytesToFilename
     *
     * @param filename
     * @param bytes
     */
    public static void writeBytesToFilename(String filename, byte[] bytes) {
        FileOutputStream fos = null;
        try {
            if (filename != null && bytes != null) {
                File f = new File(filename);

                fos = new FileOutputStream(f);

                fos.write(bytes);
                fos.close();
            } else {
                if (log.isLoggable(java.util.logging.Level.FINE)) {
                    log.log(java.util.logging.Level.FINE, "writeBytesToFilename got null byte[] pointed");
                }
            }
        } catch (IOException ex) {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ioe) {
                    if (log.isLoggable(java.util.logging.Level.FINE)) {
                        log.log(java.util.logging.Level.FINE, ioe.getMessage(), ioe);
                    }
                }
            }
        }
    }

    /**
     * This method reads all bytes from the given InputStream till EOF and
     * returns them as a byte array.
     *
     * @param inputStream
     * @return the bytes read from the stream
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static byte[] getBytesFromStream(InputStream inputStream) throws IOException {
        UnsyncByteArrayOutputStream baos = null;

        byte[] retBytes = null;
        try {
            baos = new UnsyncByteArrayOutputStream();
            byte buf[] = new byte[4 * 1024];
            int len;

            while ((len = inputStream.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }
            retBytes = baos.toByteArray();
        } finally {
            baos.close();
        }

        return retBytes;
    }
}
