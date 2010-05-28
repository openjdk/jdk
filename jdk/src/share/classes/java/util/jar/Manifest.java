/*
 * Copyright (c) 1997, 2006, Oracle and/or its affiliates. All rights reserved.
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

package java.util.jar;

import java.io.FilterInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

/**
 * The Manifest class is used to maintain Manifest entry names and their
 * associated Attributes. There are main Manifest Attributes as well as
 * per-entry Attributes. For information on the Manifest format, please
 * see the
 * <a href="../../../../technotes/guides/jar/jar.html">
 * Manifest format specification</a>.
 *
 * @author  David Connelly
 * @see     Attributes
 * @since   1.2
 */
public class Manifest implements Cloneable {
    // manifest main attributes
    private Attributes attr = new Attributes();

    // manifest entries
    private Map entries = new HashMap();

    /**
     * Constructs a new, empty Manifest.
     */
    public Manifest() {
    }

    /**
     * Constructs a new Manifest from the specified input stream.
     *
     * @param is the input stream containing manifest data
     * @throws IOException if an I/O error has occured
     */
    public Manifest(InputStream is) throws IOException {
        read(is);
    }

    /**
     * Constructs a new Manifest that is a copy of the specified Manifest.
     *
     * @param man the Manifest to copy
     */
    public Manifest(Manifest man) {
        attr.putAll(man.getMainAttributes());
        entries.putAll(man.getEntries());
    }

    /**
     * Returns the main Attributes for the Manifest.
     * @return the main Attributes for the Manifest
     */
    public Attributes getMainAttributes() {
        return attr;
    }

    /**
     * Returns a Map of the entries contained in this Manifest. Each entry
     * is represented by a String name (key) and associated Attributes (value).
     * The Map permits the {@code null} key, but no entry with a null key is
     * created by {@link #read}, nor is such an entry written by using {@link
     * #write}.
     *
     * @return a Map of the entries contained in this Manifest
     */
    public Map<String,Attributes> getEntries() {
        return entries;
    }

    /**
     * Returns the Attributes for the specified entry name.
     * This method is defined as:
     * <pre>
     *      return (Attributes)getEntries().get(name)
     * </pre>
     * Though {@code null} is a valid {@code name}, when
     * {@code getAttributes(null)} is invoked on a {@code Manifest}
     * obtained from a jar file, {@code null} will be returned.  While jar
     * files themselves do not allow {@code null}-named attributes, it is
     * possible to invoke {@link #getEntries} on a {@code Manifest}, and
     * on that result, invoke {@code put} with a null key and an
     * arbitrary value.  Subsequent invocations of
     * {@code getAttributes(null)} will return the just-{@code put}
     * value.
     * <p>
     * Note that this method does not return the manifest's main attributes;
     * see {@link #getMainAttributes}.
     *
     * @param name entry name
     * @return the Attributes for the specified entry name
     */
    public Attributes getAttributes(String name) {
        return getEntries().get(name);
    }

    /**
     * Clears the main Attributes as well as the entries in this Manifest.
     */
    public void clear() {
        attr.clear();
        entries.clear();
    }

    /**
     * Writes the Manifest to the specified OutputStream.
     * Attributes.Name.MANIFEST_VERSION must be set in
     * MainAttributes prior to invoking this method.
     *
     * @param out the output stream
     * @exception IOException if an I/O error has occurred
     * @see #getMainAttributes
     */
    public void write(OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        // Write out the main attributes for the manifest
        attr.writeMain(dos);
        // Now write out the pre-entry attributes
        Iterator it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry)it.next();
            StringBuffer buffer = new StringBuffer("Name: ");
            String value = (String)e.getKey();
            if (value != null) {
                byte[] vb = value.getBytes("UTF8");
                value = new String(vb, 0, 0, vb.length);
            }
            buffer.append(value);
            buffer.append("\r\n");
            make72Safe(buffer);
            dos.writeBytes(buffer.toString());
            ((Attributes)e.getValue()).write(dos);
        }
        dos.flush();
    }

    /**
     * Adds line breaks to enforce a maximum 72 bytes per line.
     */
    static void make72Safe(StringBuffer line) {
        int length = line.length();
        if (length > 72) {
            int index = 70;
            while (index < length - 2) {
                line.insert(index, "\r\n ");
                index += 72;
                length += 3;
            }
        }
        return;
    }

    /**
     * Reads the Manifest from the specified InputStream. The entry
     * names and attributes read will be merged in with the current
     * manifest entries.
     *
     * @param is the input stream
     * @exception IOException if an I/O error has occurred
     */
    public void read(InputStream is) throws IOException {
        // Buffered input stream for reading manifest data
        FastInputStream fis = new FastInputStream(is);
        // Line buffer
        byte[] lbuf = new byte[512];
        // Read the main attributes for the manifest
        attr.read(fis, lbuf);
        // Total number of entries, attributes read
        int ecount = 0, acount = 0;
        // Average size of entry attributes
        int asize = 2;
        // Now parse the manifest entries
        int len;
        String name = null;
        boolean skipEmptyLines = true;
        byte[] lastline = null;

        while ((len = fis.readLine(lbuf)) != -1) {
            if (lbuf[--len] != '\n') {
                throw new IOException("manifest line too long");
            }
            if (len > 0 && lbuf[len-1] == '\r') {
                --len;
            }
            if (len == 0 && skipEmptyLines) {
                continue;
            }
            skipEmptyLines = false;

            if (name == null) {
                name = parseName(lbuf, len);
                if (name == null) {
                    throw new IOException("invalid manifest format");
                }
                if (fis.peek() == ' ') {
                    // name is wrapped
                    lastline = new byte[len - 6];
                    System.arraycopy(lbuf, 6, lastline, 0, len - 6);
                    continue;
                }
            } else {
                // continuation line
                byte[] buf = new byte[lastline.length + len - 1];
                System.arraycopy(lastline, 0, buf, 0, lastline.length);
                System.arraycopy(lbuf, 1, buf, lastline.length, len - 1);
                if (fis.peek() == ' ') {
                    // name is wrapped
                    lastline = buf;
                    continue;
                }
                name = new String(buf, 0, buf.length, "UTF8");
                lastline = null;
            }
            Attributes attr = getAttributes(name);
            if (attr == null) {
                attr = new Attributes(asize);
                entries.put(name, attr);
            }
            attr.read(fis, lbuf);
            ecount++;
            acount += attr.size();
            //XXX: Fix for when the average is 0. When it is 0,
            // you get an Attributes object with an initial
            // capacity of 0, which tickles a bug in HashMap.
            asize = Math.max(2, acount / ecount);

            name = null;
            skipEmptyLines = true;
        }
    }

    private String parseName(byte[] lbuf, int len) {
        if (toLower(lbuf[0]) == 'n' && toLower(lbuf[1]) == 'a' &&
            toLower(lbuf[2]) == 'm' && toLower(lbuf[3]) == 'e' &&
            lbuf[4] == ':' && lbuf[5] == ' ') {
            try {
                return new String(lbuf, 6, len - 6, "UTF8");
            }
            catch (Exception e) {
            }
        }
        return null;
    }

    private int toLower(int c) {
        return (c >= 'A' && c <= 'Z') ? 'a' + (c - 'A') : c;
    }

    /**
     * Returns true if the specified Object is also a Manifest and has
     * the same main Attributes and entries.
     *
     * @param o the object to be compared
     * @return true if the specified Object is also a Manifest and has
     * the same main Attributes and entries
     */
    public boolean equals(Object o) {
        if (o instanceof Manifest) {
            Manifest m = (Manifest)o;
            return attr.equals(m.getMainAttributes()) &&
                   entries.equals(m.getEntries());
        } else {
            return false;
        }
    }

    /**
     * Returns the hash code for this Manifest.
     */
    public int hashCode() {
        return attr.hashCode() + entries.hashCode();
    }

    /**
     * Returns a shallow copy of this Manifest.  The shallow copy is
     * implemented as follows:
     * <pre>
     *     public Object clone() { return new Manifest(this); }
     * </pre>
     * @return a shallow copy of this Manifest
     */
    public Object clone() {
        return new Manifest(this);
    }

    /*
     * A fast buffered input stream for parsing manifest files.
     */
    static class FastInputStream extends FilterInputStream {
        private byte buf[];
        private int count = 0;
        private int pos = 0;

        FastInputStream(InputStream in) {
            this(in, 8192);
        }

        FastInputStream(InputStream in, int size) {
            super(in);
            buf = new byte[size];
        }

        public int read() throws IOException {
            if (pos >= count) {
                fill();
                if (pos >= count) {
                    return -1;
                }
            }
            return buf[pos++] & 0xff;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            int avail = count - pos;
            if (avail <= 0) {
                if (len >= buf.length) {
                    return in.read(b, off, len);
                }
                fill();
                avail = count - pos;
                if (avail <= 0) {
                    return -1;
                }
            }
            if (len > avail) {
                len = avail;
            }
            System.arraycopy(buf, pos, b, off, len);
            pos += len;
            return len;
        }

        /*
         * Reads 'len' bytes from the input stream, or until an end-of-line
         * is reached. Returns the number of bytes read.
         */
        public int readLine(byte[] b, int off, int len) throws IOException {
            byte[] tbuf = this.buf;
            int total = 0;
            while (total < len) {
                int avail = count - pos;
                if (avail <= 0) {
                    fill();
                    avail = count - pos;
                    if (avail <= 0) {
                        return -1;
                    }
                }
                int n = len - total;
                if (n > avail) {
                    n = avail;
                }
                int tpos = pos;
                int maxpos = tpos + n;
                while (tpos < maxpos && tbuf[tpos++] != '\n') ;
                n = tpos - pos;
                System.arraycopy(tbuf, pos, b, off, n);
                off += n;
                total += n;
                pos = tpos;
                if (tbuf[tpos-1] == '\n') {
                    break;
                }
            }
            return total;
        }

        public byte peek() throws IOException {
            if (pos == count)
                fill();
            return buf[pos];
        }

        public int readLine(byte[] b) throws IOException {
            return readLine(b, 0, b.length);
        }

        public long skip(long n) throws IOException {
            if (n <= 0) {
                return 0;
            }
            long avail = count - pos;
            if (avail <= 0) {
                return in.skip(n);
            }
            if (n > avail) {
                n = avail;
            }
            pos += n;
            return n;
        }

        public int available() throws IOException {
            return (count - pos) + in.available();
        }

        public void close() throws IOException {
            if (in != null) {
                in.close();
                in = null;
                buf = null;
            }
        }

        private void fill() throws IOException {
            count = pos = 0;
            int n = in.read(buf, 0, buf.length);
            if (n > 0) {
                count = n;
            }
        }
    }
}
