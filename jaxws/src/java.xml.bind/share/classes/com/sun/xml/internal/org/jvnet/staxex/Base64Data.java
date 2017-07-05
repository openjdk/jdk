/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.org.jvnet.staxex;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

// for testing method
//import com.sun.xml.internal.stream.writers.XMLStreamWriterImpl;
//import java.io.FileNotFoundException;
//import java.io.FileWriter;
//import javax.activation.FileDataSource;

/**
 * Binary data represented as base64-encoded string
 * in XML.
 *
 * <p>
 * Used in conjunction with {@link XMLStreamReaderEx}
 * and {@link XMLStreamWriterEx}.
 *
 * @author Kohsuke Kawaguchi, Martin Grebac
 */
public class Base64Data implements CharSequence, Cloneable {

    // either dataHandler or (data,dataLen,mimeType?) must be present
    // (note that having both is allowed)

    private DataHandler dataHandler;
    private byte[] data;
    private String hrefCid;

    /**
     * Length of the valid data in {@link #data}.
     */
    private int dataLen;
    /**
     * True if {@link #data} can be cloned by reference
     * if Base64Data instance is cloned.
     */
    private boolean dataCloneByRef;
    /**
     * Optional MIME type of {@link #data}.
     *
     * Unused when {@link #dataHandler} is set.
     * Use {@link DataHandler#getContentType()} in that case.
     */
    private String mimeType;

    /**
     * Default constructor
     */
    public Base64Data() {
    }

    private static final Logger logger = Logger.getLogger(Base64Data.class.getName());

    /**
     * Clone constructor
     * @param that needs to be cloned
     */
    public Base64Data(Base64Data that) {
        that.get();
        if (that.dataCloneByRef) {
            this.data = that.data;
        } else {
            this.data = new byte[that.dataLen];
            System.arraycopy(that.data, 0, this.data, 0, that.dataLen);
        }

        this.dataCloneByRef = true;
        this.dataLen = that.dataLen;
        this.dataHandler = null;
        this.mimeType = that.mimeType;
    }

    /**
     * Fills in the data object by a portion of the byte[].
     *
     * @param data actual data
     * @param len
     *      data[0] to data[len-1] are treated as the data.
     * @param mimeType MIME type
     * @param cloneByRef
     *      true if data[] can be cloned by reference
     */
    public void set(byte[] data, int len, String mimeType, boolean cloneByRef) {
        this.data = data;
        this.dataLen = len;
        this.dataCloneByRef = cloneByRef;
        this.dataHandler = null;
        this.mimeType = mimeType;
    }

    /**
     * Fills in the data object by a portion of the byte[].
     *
     * @param data actual data bytes
     * @param len
     *      data[0] to data[len-1] are treated as the data.
     * @param mimeType MIME type
     */
    public void set(byte[] data, int len, String mimeType) {
        set(data,len,mimeType,false);
    }

    /**
     * Fills in the data object by the byte[] of the exact length.
     *
     * @param data
     *      this buffer may be owned directly by the unmarshaleld JAXB object.
     * @param mimeType MIME type
     */
    public void set(byte[] data,String mimeType) {
        set(data,data.length,mimeType,false);
    }

    /**
     * Fills in the data object by a {@link DataHandler}.
     *
     * @param data DataHandler for the data
     */
    public void set(DataHandler data) {
        assert data!=null;
        this.dataHandler = data;
        this.data = null;
    }

    /**
     * Gets the raw data. If the returned DataHandler is {@link StreamingDataHandler},
     * callees may need to downcast to take advantage of its capabilities.
     *
     * @see StreamingDataHandler
     * @return DataHandler for the data
     */
    public DataHandler getDataHandler() {
        if(dataHandler==null){
            dataHandler = new Base64StreamingDataHandler(new Base64DataSource());
        } else if (!(dataHandler instanceof StreamingDataHandler)) {
            dataHandler = new FilterDataHandler(dataHandler);
        }
        return dataHandler;
    }

    private final class Base64DataSource implements DataSource {
        public String getContentType() {
            return getMimeType();
        }

        public InputStream getInputStream() {
            return new ByteArrayInputStream(data,0,dataLen);
        }

        public String getName() {
            return null;
        }

        public OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }

    }

    private final class Base64StreamingDataHandler extends StreamingDataHandler {

        Base64StreamingDataHandler(DataSource source) {
            super(source);
        }

        public InputStream readOnce() throws IOException {
            return getDataSource().getInputStream();
        }

        public void moveTo(File dst) throws IOException {
            FileOutputStream fout = new FileOutputStream(dst);
            try {
                fout.write(data, 0, dataLen);
            } finally {
                fout.close();
            }
        }

        public void close() throws IOException {
            // nothing to do
        }
    }

    private static final class FilterDataHandler extends StreamingDataHandler {

        FilterDataHandler(DataHandler dh) {
            super(dh.getDataSource());
        }

        public InputStream readOnce() throws IOException {
            return getDataSource().getInputStream();
        }

        public void moveTo(File dst) throws IOException {
            byte[] buf = new byte[8192];
            InputStream in = null;
            OutputStream out = null;
            try {
                in = getDataSource().getInputStream();
                out = new FileOutputStream(dst);
                while (true) {
                    int amountRead = in.read(buf);
                    if (amountRead == -1) {
                        break;
                    }
                    out.write(buf, 0, amountRead);
                }
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch(IOException ioe) {
                        // nothing to do
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch(IOException ioe) {
                        // nothing to do
                    }
                }
            }
        }

        public void close() throws IOException {
            // nothing to do
        }
    }

    /**
     * Gets the byte[] of the exact length.
     *
     * @return byte[] for data
     */
    public byte[] getExact() {
        get();
        if(dataLen!=data.length) {
            byte[] buf = new byte[dataLen];
            System.arraycopy(data,0,buf,0,dataLen);
            data = buf;
        }
        return data;
    }

    /**
     * Gets the data as an {@link InputStream}.
     *
     * @return data as InputStream
     * @throws IOException if i/o error occurs
     */
    public InputStream getInputStream() throws IOException {
        if(dataHandler!=null) {
            return dataHandler.getInputStream();
        } else {
            return new ByteArrayInputStream(data,0,dataLen);
        }
    }

    /**
     * Returns false if this object only has {@link DataHandler} and therefore
     * {@link #get()} operation is likely going to be expensive.
     *
     * @return false if it has only DataHandler
     */
    public boolean hasData() {
        return data!=null;
    }

    /**
     * Gets the raw data. The size of the byte array maybe larger than the actual length.
     *
     * @return data as byte[], the array may be larger
     */
    public byte[] get() {
        if(data==null) {
            try {
                ByteArrayOutputStreamEx baos = new ByteArrayOutputStreamEx(1024);
                InputStream is = dataHandler.getDataSource().getInputStream();
                baos.readFrom(is);
                is.close();
                data = baos.getBuffer();
                dataLen = baos.size();
                dataCloneByRef = true;
            } catch (IOException e) {
                // TODO: report the error to the unmarshaller
                dataLen = 0;    // recover by assuming length-0 data
            }
        }
        return data;
    }

    /**
     * Gets the length of the binary data counted in bytes.
     *
     * Note that if this object encapsulates {@link DataHandler},
     * this method would have to read the whole thing into {@code byte[]}
     * just to count the length, because {@link DataHandler}
     * doesn't easily expose the length.
     *
     * @return no of bytes
     */
    public int getDataLen() {
        get();
        return dataLen;
    }

    public String getMimeType() {
        if (mimeType==null) {
            return "application/octet-stream";
        }
        return mimeType;
    }

    /**
     * Gets the number of characters needed to represent
     * this binary data in the base64 encoding.
     */
    public int length() {
        // for each 3 bytes you use 4 chars
        // if the remainder is 1 or 2 there will be 4 more
        get();  // fill in the buffer if necessary
        return ((dataLen+2)/3)*4;
    }

    /**
     * Encode this binary data in the base64 encoding
     * and returns the character at the specified position.
     */
    public char charAt(int index) {
        // we assume that the length() method is called before this method
        // (otherwise how would the caller know that the index is valid?)
        // so we assume that the byte[] is already populated

        int offset = index%4;
        int base = (index/4)*3;

        byte b1,b2;

        switch(offset) {
        case 0:
            return Base64Encoder.encode(data[base]>>2);
        case 1:
            if (base+1<dataLen) {
                b1 = data[base+1];
            } else {
                b1 = 0;
            }
            return Base64Encoder.encode(
                        ((data[base]&0x3)<<4) |
                        ((b1>>4)&0xF));
        case 2:
            if (base+1<dataLen) {
                b1 = data[base+1];
                if (base+2<dataLen) {
                    b2 = data[base+2];
                } else {
                    b2 = 0;
                }

                return Base64Encoder.encode(
                            ((b1&0xF)<<2)|
                            ((b2>>6)&0x3));
            } else {
                return '=';
            }
        case 3:
            if(base+2<dataLen) {
                return Base64Encoder.encode(data[base+2]&0x3F);
            } else {
                return '=';
            }
        }

        throw new IllegalStateException();
    }

    /**
     * Internally this is only used to split a text to a list,
     * which doesn't happen that much for base64.
     * So this method should be smaller than faster.
     */
    public CharSequence subSequence(int start, int end) {
        StringBuilder buf = new StringBuilder();
        get();  // fill in the buffer if we haven't done so
        for (int i=start; i<end; i++ ) {
            buf.append(charAt(i));
        }
        return buf;
    }

    /**
     * Returns the base64 encoded string of this data.
     */
    @Override
    public String toString() {
        get();  // fill in the buffer
        return Base64Encoder.print(data, 0, dataLen);
    }

    public void writeTo(char[] buf, int start) {
        get();
        Base64Encoder.print(data, 0, dataLen, buf, start);
    }

    private static final int CHUNK_SIZE;
    static {
        int bufSize = 1024;
        try {
            String bufSizeStr = getProperty("com.sun.xml.internal.org.jvnet.staxex.Base64DataStreamWriteBufferSize");
            if (bufSizeStr != null) {
                bufSize = Integer.parseInt(bufSizeStr);
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "Error reading com.sun.xml.internal.org.jvnet.staxex.Base64DataStreamWriteBufferSize property", e);
        }
        CHUNK_SIZE = bufSize;
    }

    public void writeTo(XMLStreamWriter output) throws IOException, XMLStreamException {
        if (data==null) {
            try {
                InputStream is = dataHandler.getDataSource().getInputStream();
                ByteArrayOutputStream outStream = new ByteArrayOutputStream(); // dev-null stream
                Base64EncoderStream encWriter = new Base64EncoderStream(output, outStream);
                int b;
                byte[] buffer = new byte[CHUNK_SIZE];
                while ((b = is.read(buffer)) != -1) {
                    encWriter.write(buffer, 0, b);
                }
                outStream.close();
                encWriter.close();
            } catch (IOException e) {
                dataLen = 0;    // recover by assuming length-0 data
                throw e;
            }
        } else {
            // the data is already in memory and not streamed
            String s = Base64Encoder.print(data, 0, dataLen);
            output.writeCharacters(s);
        }
    }

    @Override
    public Base64Data clone() {
        try {
            Base64Data clone = (Base64Data) super.clone();
            clone.get();
            if (clone.dataCloneByRef) {
                this.data = clone.data;
            } else {
                this.data = new byte[clone.dataLen];
                System.arraycopy(clone.data, 0, this.data, 0, clone.dataLen);
            }

            this.dataCloneByRef = true;
            this.dataLen = clone.dataLen;
            this.dataHandler = null;
            this.mimeType = clone.mimeType;
            return clone;
        } catch (CloneNotSupportedException ex) {
            Logger.getLogger(Base64Data.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    static String getProperty(final String propName) {
        if (System.getSecurityManager() == null) {
            return System.getProperty(propName);
        } else {
            return (String) java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction() {
                        public java.lang.Object run() {
                            return System.getProperty(propName);
                        }
                    });
        }
    }

//    public static void main(String[] args) throws FileNotFoundException, IOException, XMLStreamException {
//        Base64Data data = new Base64Data();
//
//        File f = new File("/Users/snajper/work/builds/weblogic/wls1211_dev.zip");
//        FileDataSource fds = new FileDataSource(f);
//        DataHandler dh = new DataHandler(fds);
//        data.set(dh);
//
//        FileWriter fw = new FileWriter(new File("/Users/snajper/Desktop/b.txt"));
//        XMLStreamWriterImpl wi = new XMLStreamWriterImpl(fw, null);
//
//        data.writeTo(wi);
//        wi.flush();fw.flush();
//        //System.out.println("SW: " + sw.toString());
//
//    }

    public String getHrefCid() {
        if (hrefCid == null && dataHandler != null && dataHandler instanceof StreamingDataHandler) {
            hrefCid = ((StreamingDataHandler)dataHandler).getHrefCid();
        }
        return hrefCid;
    }

    public void setHrefCid(final String cid) {
        this.hrefCid = cid;
        if (dataHandler != null && dataHandler instanceof StreamingDataHandler) ((StreamingDataHandler)dataHandler).setHrefCid(cid);
    }

}
