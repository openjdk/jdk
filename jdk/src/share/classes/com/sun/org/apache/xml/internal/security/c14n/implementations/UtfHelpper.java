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
package com.sun.org.apache.xml.internal.security.c14n.implementations;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class UtfHelpper {

    static final void writeByte(
        final String str,
        final OutputStream out,
        Map<String, byte[]> cache
    ) throws IOException {
        byte[] result = cache.get(str);
        if (result == null) {
            result = getStringInUtf8(str);
            cache.put(str, result);
        }

        out.write(result);
    }

    static final void writeCharToUtf8(final char c, final OutputStream out) throws IOException {
        if (c < 0x80) {
            out.write(c);
            return;
        }
        if ((c >= 0xD800 && c <= 0xDBFF) || (c >= 0xDC00 && c <= 0xDFFF)) {
            //No Surrogates in sun java
            out.write(0x3f);
            return;
        }
        int bias;
        int write;
        char ch;
        if (c > 0x07FF) {
            ch = (char)(c>>>12);
            write = 0xE0;
            if (ch > 0) {
                write |= (ch & 0x0F);
            }
            out.write(write);
            write = 0x80;
            bias = 0x3F;
        } else {
            write = 0xC0;
            bias = 0x1F;
        }
        ch = (char)(c>>>6);
        if (ch > 0) {
            write |= (ch & bias);
        }
        out.write(write);
        out.write(0x80 | ((c) & 0x3F));

    }

    static final void writeStringToUtf8(
        final String str,
        final OutputStream out
    ) throws IOException{
        final int length = str.length();
        int i = 0;
        char c;
        while (i < length) {
            c = str.charAt(i++);
            if (c < 0x80)  {
                out.write(c);
                continue;
            }
            if ((c >= 0xD800 && c <= 0xDBFF) || (c >= 0xDC00 && c <= 0xDFFF)) {
                //No Surrogates in sun java
                out.write(0x3f);
                continue;
            }
            char ch;
            int bias;
            int write;
            if (c > 0x07FF) {
                ch = (char)(c>>>12);
                write = 0xE0;
                if (ch > 0) {
                    write |= (ch & 0x0F);
                }
                out.write(write);
                write = 0x80;
                bias = 0x3F;
            } else {
                write = 0xC0;
                bias = 0x1F;
            }
            ch = (char)(c>>>6);
            if (ch > 0) {
                write |= (ch & bias);
            }
            out.write(write);
            out.write(0x80 | ((c) & 0x3F));

        }

    }

    public static final byte[] getStringInUtf8(final String str) {
        final int length = str.length();
        boolean expanded = false;
        byte[] result = new byte[length];
        int i = 0;
        int out = 0;
        char c;
        while (i < length) {
            c = str.charAt(i++);
            if (c < 0x80) {
                result[out++] = (byte)c;
                continue;
            }
            if ((c >= 0xD800 && c <= 0xDBFF) || (c >= 0xDC00 && c <= 0xDFFF)) {
                //No Surrogates in sun java
                result[out++] = 0x3f;
                continue;
            }
            if (!expanded) {
                byte newResult[] = new byte[3*length];
                System.arraycopy(result, 0, newResult, 0, out);
                result = newResult;
                expanded = true;
            }
            char ch;
            int bias;
            byte write;
            if (c > 0x07FF) {
                ch = (char)(c>>>12);
                write = (byte)0xE0;
                if (ch > 0) {
                    write |= (ch & 0x0F);
                }
                result[out++] = write;
                write = (byte)0x80;
                bias = 0x3F;
            } else {
                write = (byte)0xC0;
                bias = 0x1F;
            }
            ch = (char)(c>>>6);
            if (ch > 0) {
                write |= (ch & bias);
            }
            result[out++] = write;
            result[out++] = (byte)(0x80 | ((c) & 0x3F));
        }
        if (expanded) {
            byte newResult[] = new byte[out];
            System.arraycopy(result, 0, newResult, 0, out);
            result = newResult;
        }
        return result;
    }

}
