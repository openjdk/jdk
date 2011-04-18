/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * This class provides streaming mode reading of manifest files.
 * Used by {@link SignatureFileVerifier}.
 */
class SignatureFileManifest extends Manifest {

    /*
     * Reading a manifest into this object by calling update(byte[]) on chunks.
     * During the reading, the bytes are saved in (@code current} until a line
     * is complete and the key-value pair is saved in {@code currentAttr}. When
     * a section is complete, {@code consumeAttr} is called to merge
     * {@code currentAttr} into main attributes or a named entry.
     */

    // Internal state during update() style reading
    // 0. not in update mode
    // 1, in update mode but main attributes not completed yet
    // 2. main attributes completed, still reading the entries
    private int state = 0;

    // The partial line read
    private byte[] current;

    // Number of bytes in current
    private int currentPos = 0;

    // The current Attribute
    private Attributes currentAttr;

    /**
     * Reads a manifest in chunks.
     * <p>
     * This method must be called in a row, reading chunks from a single
     * manifest file by order. After all chunks are read, caller must call
     * {@code update(null)} to fully consume the manifest.
     * <p>
     * The entry names and attributes read will be merged in with the current
     * manifest entries. The {@link #read} method cannot be called inside a
     * row of update calls.
     * <p>
     * Along with the calls, caller can call {@link #getMainAttributes()},
     * {@link #getAttributes(java.lang.String)} or {@link #getEntries()}
     * to get already available contents. However, in order not to return
     * partial result, when the main attributes in the new manifest is not
     * consumed completely, {@link #getMainAttributes()} throws an
     * {@code IllegalStateException}. When a certain named entry is not
     * consumed completely, {@link #getAttributes(java.lang.String)}
     * returns the old {@code Attributes} for the name (if it exists).
     *
     * @param data null for last call, otherwise, feeding chunks
     * @param offset offset into data to begin read
     * @param length length of data after offset to read
     * @exception IOException if an I/O error has occurred
     * @exception IllegalStateException if {@code update(null)} is called
     * without any previous {@code update(non-null)} call
     */
    public void update(byte[] data, int offset, int length) throws IOException {

        // The last call
        if (data == null) {
            if (state == 0) {
                throw new IllegalStateException("No data to update");
            }
            // We accept manifest not ended with \n or \n\n
            if (hasLastByte()) {
                consumeCurrent();
            }
            // We accept empty lines at the end
            if (!currentAttr.isEmpty()) {
                consumeAttr();
            }
            state = 0;  // back to non-update state
            current = null;
            currentAttr = null;
            return;
        }

        // The first call
        if (state == 0) {
            current = new byte[1024];
            currentAttr = super.getMainAttributes(); // the main attribute
            state = 1;
        }

        int end = offset + length;

        while (offset < end) {
            switch (data[offset]) {
                case '\r':
                    break;  // always skip
                case '\n':
                    if (hasLastByte() && lastByte() == '\n') {  // new section
                        consumeCurrent();
                        consumeAttr();
                        if (state == 1) {
                            state = 2;
                        }
                        currentAttr = new Attributes(2);
                    } else {
                        if (hasLastByte()) {
                            // save \n into current but do not parse,
                            // there might be a continuation later
                            ensureCapacity();
                            current[currentPos++] = data[offset];
                        } else if (state == 1) {
                            // there can be multiple empty lines between
                            // sections, but cannot be at the beginning
                            throw new IOException("invalid manifest format");
                        }
                    }
                    break;
                case ' ':
                    if (!hasLastByte()) {
                        throw new IOException("invalid manifest format");
                    } else if (lastByte() == '\n') {
                        currentPos--;   // continuation, remove last \n
                    } else {    // a very normal ' '
                        ensureCapacity();
                        current[currentPos++] = data[offset];
                    }
                    break;
                default:
                    if (hasLastByte() && lastByte() == '\n') {
                        // The start of a new pair, not continuation
                        consumeCurrent();   // the last line read
                    }
                    ensureCapacity();
                    current[currentPos++] = data[offset];
                    break;
            }
            offset++;
        }
    }

    /**
     * Returns the main Attributes for the Manifest.
     * @exception IllegalStateException the main attributes is being read
     * @return the main Attributes for the Manifest
     */
    public Attributes getMainAttributes() {
        if (state == 1) {
            throw new IllegalStateException();
        }
        return super.getMainAttributes();
    }

    /**
     * Reads the Manifest from the specified InputStream. The entry
     * names and attributes read will be merged in with the current
     * manifest entries.
     *
     * @param is the input stream
     * @exception IOException if an I/O error has occurred
     * @exception IllegalStateException if called between two {@link #update}
     * calls
     */
    public void read(InputStream is) throws IOException {
        if (state != 0) {
            throw new IllegalStateException("Cannot call read between updates");
        }
        super.read(is);
    }

    /*
     * ----------  Helper methods  -----------------
     */

    private void ensureCapacity() {
        if (currentPos >= current.length-1) {
            current = Arrays.copyOf(current, current.length*2);
        }
    }

    private boolean hasLastByte() {
        return currentPos > 0;
    }

    private byte lastByte() {
        return current[currentPos-1];
    }

    // Parse current as key:value and save into currentAttr.
    // There MUST be something inside current.
    private void consumeCurrent() throws IOException {
        // current normally has a \n end, except for the last line
        if (current[currentPos-1] == '\n') currentPos--;
        for (int i=0; i<currentPos; i++) {
            if (current[i] == ':') {
                String key = new String(current, 0, 0, i);
                i++;
                while (i < currentPos && current[i] == ' ') { i++; }
                String value = new String(current, i, currentPos-i, "UTF-8");
                currentAttr.putValue(key, value);
                currentPos = 0;
                return;
            }
        }
        throw new IOException("invalid header field");
    }

    // Merge currentAttr into Manifest
    private void consumeAttr() throws IOException {
        // Only needed for named entries. For the main attribute, key/value
        // is added into attr directly, but since getMainAttributes() throws
        // an exception, the partial data is not leaked.
        if (state != 1) {
            String name = currentAttr.getValue("Name");
            if (name != null) {
                currentAttr.remove(new Attributes.Name("Name"));
                Attributes old = getAttributes(name);
                if (old != null) old.putAll(currentAttr);
                else getEntries().put(name, currentAttr);
            } else {
                throw new IOException("invalid manifest format");
            }
        }
    }
}
