/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2005 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.sun.org.apache.xml.internal.security.utils;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A class that buffers writte without synchronize its methods
 * @author raul
 *
 */
public class UnsyncBufferedOutputStream extends OutputStream {
        final OutputStream out;

        final byte[] buf;
        static final int size=8*1024;
        private static ThreadLocal bufCahce = new ThreadLocal() {
        protected synchronized Object initialValue() {
            return new byte[size];
        }
    };
        int pointer=0;
        /**
         * Creates a buffered output stream without synchronization
         * @param out the outputstream to buffer
         */
        public UnsyncBufferedOutputStream(OutputStream out) {
                buf=(byte[])bufCahce.get();
                this.out=out;
        }

        /** @inheritDoc */
        public void write(byte[] arg0) throws IOException {
                write(arg0,0,arg0.length);
        }

        /** @inheritDoc */
        public void write(byte[] arg0, int arg1, int len) throws IOException {
                int newLen=pointer+len;
                if (newLen> size) {
                        flushBuffer();
                        if (len>size) {
                                out.write(arg0,arg1,len);
                                return;
                        }
                        newLen=len;
                }
                System.arraycopy(arg0,arg1,buf,pointer,len);
                pointer=newLen;
        }

        private final void flushBuffer() throws IOException {
                if (pointer>0)
                        out.write(buf,0,pointer);
                pointer=0;

        }

        /** @inheritDoc */
        public void write(int arg0) throws IOException {
                if (pointer>= size) {
                        flushBuffer();
                }
                buf[pointer++]=(byte)arg0;

        }

        /** @inheritDoc */
        public void flush() throws IOException {
                flushBuffer();
                out.flush();
        }

        /** @inheritDoc */
        public void close() throws IOException {
                flush();
        }

}
