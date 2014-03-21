/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.jar;

import java.io.*;
import java.util.*;
import java.security.*;

import sun.net.www.MessageHeader;
import java.util.Base64;

/**
 * This is OBSOLETE. DO NOT USE THIS. Use java.util.jar.Manifest
 * instead. It has to stay here because some apps (namely HJ and HJV)
 * call directly into it.
 *
 * @author David Brown
 * @author Benjamin Renaud
 */

public class Manifest {

    /* list of headers that all pertain to a particular
     * file in the archive
     */
    private Vector<MessageHeader> entries = new Vector<>();
    private byte[] tmpbuf = new byte[512];
    /* a hashtable of entries, for fast lookup */
    private Hashtable<String, MessageHeader> tableEntries = new Hashtable<>();

    static final String[] hashes = {"SHA"};
    static final byte[] EOL = {(byte)'\r', (byte)'\n'};

    static final boolean debug = false;
    static final String VERSION = "1.0";
    static final void debug(String s) {
        if (debug)
            System.out.println("man> " + s);
    }

    public Manifest() {}

    public Manifest(byte[] bytes) throws IOException {
        this(new ByteArrayInputStream(bytes), false);
    }

    public Manifest(InputStream is) throws IOException {
        this(is, true);
    }

    /**
     * Parse a manifest from a stream, optionally computing hashes
     * for the files.
     */
    public Manifest(InputStream is, boolean compute) throws IOException {
        if (!is.markSupported()) {
            is = new BufferedInputStream(is);
        }
        /* do not rely on available() here! */
        while (true) {
            is.mark(1);
            if (is.read() == -1) { // EOF
                break;
            }
            is.reset();
            MessageHeader m = new MessageHeader(is);
            if (compute) {
                doHashes(m);
            }
            addEntry(m);
        }
    }

    /* recursively generate manifests from directory tree */
    public Manifest(String[] files) throws IOException {
        MessageHeader globals = new MessageHeader();
        globals.add("Manifest-Version", VERSION);
        String jdkVersion = System.getProperty("java.version");
        globals.add("Created-By", "Manifest JDK "+jdkVersion);
        addEntry(globals);
        addFiles(null, files);
    }

    public void addEntry(MessageHeader entry) {
        entries.addElement(entry);
        String name = entry.findValue("Name");
        debug("addEntry for name: "+name);
        if (name != null) {
            tableEntries.put(name, entry);
        }
    }

    public MessageHeader getEntry(String name) {
        return tableEntries.get(name);
    }

    public MessageHeader entryAt(int i) {
        return entries.elementAt(i);
    }

    public Enumeration<MessageHeader> entries() {
        return entries.elements();
    }

    public void addFiles(File dir, String[] files) throws IOException {
        if (files == null)
            return;
        for (int i = 0; i < files.length; i++) {
            File file;
            if (dir == null) {
                file = new File(files[i]);
            } else {
                file = new File(dir, files[i]);
            }
            if (file.isDirectory()) {
                addFiles(file, file.list());
            } else {
                addFile(file);
            }
        }
    }

    /**
     * File names are represented internally using "/";
     * they are converted to the local format for anything else
     */

    private final String stdToLocal(String name) {
        return name.replace('/', java.io.File.separatorChar);
    }

    private final String localToStd(String name) {
        name = name.replace(java.io.File.separatorChar, '/');
        if (name.startsWith("./"))
            name = name.substring(2);
        else if (name.startsWith("/"))
            name = name.substring(1);
        return name;
    }

    public void addFile(File f) throws IOException {
        String stdName = localToStd(f.getPath());
        if (tableEntries.get(stdName) == null) {
            MessageHeader mh = new MessageHeader();
            mh.add("Name", stdName);
            addEntry(mh);
        }
    }

    public void doHashes(MessageHeader mh) throws IOException {
        // If unnamed or is a directory return immediately
        String name = mh.findValue("Name");
        if (name == null || name.endsWith("/")) {
            return;
        }


        /* compute hashes, write over any other "Hash-Algorithms" (?) */
        for (int j = 0; j < hashes.length; ++j) {
            InputStream is = new FileInputStream(stdToLocal(name));
            try {
                MessageDigest dig = MessageDigest.getInstance(hashes[j]);

                int len;
                while ((len = is.read(tmpbuf, 0, tmpbuf.length)) != -1) {
                    dig.update(tmpbuf, 0, len);
                }
                mh.set(hashes[j] + "-Digest", Base64.getMimeEncoder().encodeToString(dig.digest()));
            } catch (NoSuchAlgorithmException e) {
                throw new JarException("Digest algorithm " + hashes[j] +
                                       " not available.");
            } finally {
                is.close();
            }
        }
    }

    /* Add a manifest file at current position in a stream
     */
    public void stream(OutputStream os) throws IOException {

        PrintStream ps;
        if (os instanceof PrintStream) {
            ps = (PrintStream) os;
        } else {
            ps = new PrintStream(os);
        }

        /* the first header in the file should be the global one.
         * It should say "Manifest-Version: x.x"; if not add it
         */
        MessageHeader globals = entries.elementAt(0);

        if (globals.findValue("Manifest-Version") == null) {
            /* Assume this is a user-defined manifest.  If it has a Name: <..>
             * field, then it is not global, in which case we just add our own
             * global Manifest-version: <version>
             * If the first MessageHeader has no Name: <..>, we assume it
             * is a global header and so prepend Manifest to it.
             */
            String jdkVersion = System.getProperty("java.version");

            if (globals.findValue("Name") == null) {
                globals.prepend("Manifest-Version", VERSION);
                globals.add("Created-By", "Manifest JDK "+jdkVersion);
            } else {
                ps.print("Manifest-Version: "+VERSION+"\r\n"+
                         "Created-By: "+jdkVersion+"\r\n\r\n");
            }
            ps.flush();
        }

        globals.print(ps);

        for (int i = 1; i < entries.size(); ++i) {
            MessageHeader mh = entries.elementAt(i);
            mh.print(ps);
        }
    }

    public static boolean isManifestName(String name) {

        // remove leading /
        if (name.charAt(0) == '/') {
            name = name.substring(1, name.length());
        }
        // case insensitive
        name = name.toUpperCase();

        if (name.equals("META-INF/MANIFEST.MF")) {
            return true;
        }
        return false;
    }
}
