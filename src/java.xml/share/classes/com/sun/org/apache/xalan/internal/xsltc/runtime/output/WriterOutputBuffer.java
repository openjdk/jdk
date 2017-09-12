/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xalan.internal.xsltc.runtime.output;

import com.sun.org.apache.xalan.internal.utils.SecuritySupport;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * @author Santiago Pericas-Geertsen
 */
class WriterOutputBuffer implements OutputBuffer {
    private static final int KB = 1024;
    private static int BUFFER_SIZE = 4 * KB;

    static {
        // Set a larger buffer size for Solaris
        final String osName = SecuritySupport.getSystemProperty("os.name");
        if (osName.equalsIgnoreCase("solaris")) {
            BUFFER_SIZE = 32 * KB;
        }
    }

    private Writer _writer;

    /**
     * Initializes a WriterOutputBuffer by creating an instance of a
     * BufferedWriter. The size of the buffer in this writer may have
     * a significant impact on throughput. Solaris prefers a larger
     * buffer, while Linux works better with a smaller one.
     */
    public WriterOutputBuffer(Writer writer) {
        _writer = new BufferedWriter(writer, BUFFER_SIZE);
    }

    public String close() {
        try {
            _writer.flush();
        }
        catch (IOException e) {
            throw new RuntimeException(e.toString());
        }
        return "";
    }

    public OutputBuffer append(String s) {
        try {
            _writer.write(s);
        }
        catch (IOException e) {
            throw new RuntimeException(e.toString());
        }
        return this;
    }

    public OutputBuffer append(char[] s, int from, int to) {
        try {
            _writer.write(s, from, to);
        }
        catch (IOException e) {
            throw new RuntimeException(e.toString());
        }
        return this;
    }

    public OutputBuffer append(char ch) {
        try {
            _writer.write(ch);
        }
        catch (IOException e) {
            throw new RuntimeException(e.toString());
        }
        return this;
    }
}
