/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.*;

/**
 * A Content type that provides for transferring files.
 *
 * @author Mark Reinhold
 * @author Brad R. Wetmore
 */
class FileContent implements Content {

    private static File ROOT = new File("root");

    private File fn;

    FileContent(URI uri) {
        fn = new File(ROOT,
                      uri.getPath()
                      .replace('/',
                               File.separatorChar));
    }

    private String type = null;

    public String type() {
        if (type != null)
            return type;
        String nm = fn.getName();
        if (nm.endsWith(".html"))
            type = "text/html; charset=iso-8859-1";
        else if ((nm.indexOf('.') < 0) || nm.endsWith(".txt"))
            type = "text/plain; charset=iso-8859-1";
        else
            type = "application/octet-stream";
        return type;
    }

    private FileChannel fc = null;
    private long length = -1;
    private long position = -1;         // NB only; >= 0 if transferring

    public long length() {
        return length;
    }

    public void prepare() throws IOException {
        if (fc == null)
            fc = new RandomAccessFile(fn, "r").getChannel();
        length = fc.size();
        position = 0;                   // NB only
    }

    public boolean send(ChannelIO cio) throws IOException {
        if (fc == null)
            throw new IllegalStateException();
        if (position < 0)               // NB only
            throw new IllegalStateException();

        /*
         * Short-circuit if we're already done.
         */
        if (position >= length) {
            return false;
        }

        position += cio.transferTo(fc, position, length - position);
        return (position < length);
    }

    public void release() throws IOException {
        if (fc != null) {
            fc.close();
            fc = null;
        }
    }
}
