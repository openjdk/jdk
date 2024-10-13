/*
 * Copyright (c) 1994, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www;

import jdk.internal.util.StaticProperty;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.FileNameMap;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Hashtable;
import java.util.Properties;
import java.util.StringTokenizer;

public class MimeTable implements FileNameMap {
    /** Hash mark introducing a URI fragment */
    private static final int HASH_MARK = '#';

    /** Keyed by content type, returns MimeEntries */
    private final Hashtable<String, MimeEntry> entries = new Hashtable<>();

    /** Keyed by file extension (with the .), returns MimeEntries */
    private final Hashtable<String, MimeEntry> extensionMap = new Hashtable<>();

    // Will be reset if in the platform-specific data file
    @SuppressWarnings("removal")
    private static String tempFileTemplate =
        java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<String>() {
                    public String run() {
                        return System.getProperty("content.types.temp.file.template",
                                "/tmp/%s");
                    }
                });

    private static final String filePreamble = "sun.net.www MIME content-types table";

    MimeTable() {
        load();
    }

    private static class DefaultInstanceHolder {
        static final MimeTable defaultInstance = getDefaultInstance();

        @SuppressWarnings("removal")
        static MimeTable getDefaultInstance() {
            return java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<>() {
                public MimeTable run() {
                    MimeTable instance = new MimeTable();
                    URLConnection.setFileNameMap(instance);
                    return instance;
                }
            });
        }
    }

    /**
     * Get the single instance of this class.  First use will load the
     * table from a data file.
     */
    public static MimeTable getDefaultTable() {
        return DefaultInstanceHolder.defaultInstance;
    }

    public static FileNameMap loadTable() {
        MimeTable mt = getDefaultTable();
        return mt;
    }

    public synchronized int getSize() {
        return entries.size();
    }

    public synchronized String getContentTypeFor(String fileName) {
        MimeEntry entry = findByFileName(fileName);
        if (entry != null) {
            return entry.getType();
        } else {
            return null;
        }
    }

    public synchronized void add(MimeEntry m) {
        entries.put(m.getType(), m);

        String exts[] = m.getExtensions();
        if (exts == null) {
            return;
        }

        for (int i = 0; i < exts.length; i++) {
            extensionMap.put(exts[i], m);
        }
    }

    public synchronized MimeEntry remove(String type) {
        MimeEntry entry = entries.get(type);
        return remove(entry);
    }

    public synchronized MimeEntry remove(MimeEntry entry) {
        String[] extensionKeys = entry.getExtensions();
        if (extensionKeys != null) {
            for (int i = 0; i < extensionKeys.length; i++) {
                extensionMap.remove(extensionKeys[i]);
            }
        }

        return entries.remove(entry.getType());
    }

    public synchronized MimeEntry find(String type) {
        MimeEntry entry = entries.get(type);
        if (entry == null) {
            // try a wildcard lookup
            Enumeration<MimeEntry> e = entries.elements();
            while (e.hasMoreElements()) {
                MimeEntry wild = e.nextElement();
                if (wild.matches(type)) {
                    return wild;
                }
            }
        }

        return entry;
    }

    /**
     * Extracts the file extension and uses it to look up the entry.
     */
    private MimeEntry findViaFileExtension(String fname) {
        int i = fname.lastIndexOf('.');
        // REMIND: OS specific delimiters appear here
        i = Math.max(i, fname.lastIndexOf('/'));
        i = Math.max(i, fname.lastIndexOf('?'));

        String ext = "";
        if (i != -1 && fname.charAt(i) == '.') {
            ext = fname.substring(i).toLowerCase(Locale.ROOT);
        }

        return findByExt(ext);
    }

    /**
     * Locate a MimeEntry by its associated file extension.
     * Parses general file names, and URLs.
     *
     * @param fname the file name
     *
     * @return the MIME entry associated with the file name or {@code null}
     */
    public MimeEntry findByFileName(String fname) {
        MimeEntry entry = null;

        // If an optional fragment introduced by a hash mark is
        // present, then strip it and use the prefix
        int hashIndex = fname.lastIndexOf(HASH_MARK);
        if (hashIndex > 0) {
            entry = findViaFileExtension(fname.substring(0, hashIndex));
            if (entry != null) {
                return entry;
            }
        }

        assert entry == null;

        // If either no optional fragment was present, or the entry was not
        // found with the fragment stripped, then try again with the full name
        if (entry == null) {
            entry = findViaFileExtension(fname);
        }

        return entry;
    }

    /**
     * Locate a MimeEntry by the file extension that has been associated
     * with it.
     */
    public synchronized MimeEntry findByExt(String fileExtension) {
        return extensionMap.get(fileExtension);
    }

    public synchronized MimeEntry findByDescription(String description) {
        Enumeration<MimeEntry> e = elements();
        while (e.hasMoreElements()) {
            MimeEntry entry = e.nextElement();
            if (description.equals(entry.getDescription())) {
                return entry;
            }
        }

        // We failed, now try treating description as type
        return find(description);
    }

    String getTempFileTemplate() {
        return tempFileTemplate;
    }

    public synchronized Enumeration<MimeEntry> elements() {
        return entries.elements();
    }

    // For backward compatibility -- mailcap format files
    // This is not currently used, but may in the future when we add ability
    // to read BOTH the properties format and the mailcap format.
    @SuppressWarnings("removal")
    protected static String[] mailcapLocations =
        java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<String[]>() {
                    public String[] run() {
                        return new String[]{
                                System.getProperty("user.mailcap"),
                                StaticProperty.userHome() + "/.mailcap",
                                "/etc/mailcap",
                                "/usr/etc/mailcap",
                                "/usr/local/etc/mailcap",
                        };
                    }
                });

    public synchronized void load() {
        Properties entries = new Properties();
        File file = null;
        InputStream in;

        // First try to load the user-specific table, if it exists
        String userTablePath = System.getProperty("content.types.user.table");
        if (userTablePath != null && (file = new File(userTablePath)).exists()) {
            try {
                in = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                System.err.println("Warning: " + file.getPath()
                                   + " mime table not found.");
                return;
            }
        } else {
            in = MimeTable.class.getResourceAsStream("content-types.properties");
            if (in == null)
                throw new InternalError("default mime table not found");
        }

        try (in) {
            entries.load(in);
        } catch (IOException e) {
            System.err.println("Warning: " + e.getMessage());
        }
        parse(entries);
    }

    void parse(Properties entries) {
        // first, strip out the platform-specific temp file template
        String tempFileTemplate = (String)entries.get("temp.file.template");
        if (tempFileTemplate != null) {
            entries.remove("temp.file.template");
            MimeTable.tempFileTemplate = tempFileTemplate;
        }

        // now, parse the mime-type spec's
        Enumeration<?> types = entries.propertyNames();
        while (types.hasMoreElements()) {
            String type = (String)types.nextElement();
            String attrs = entries.getProperty(type);
            parse(type, attrs);
        }
    }

    //
    // Table format:
    //
    // <entry> ::= <table_tag> | <type_entry>
    //
    // <table_tag> ::= <table_format_version> | <temp_file_template>
    //
    // <type_entry> ::= <type_subtype_pair> '=' <type_attrs_list>
    //
    // <type_subtype_pair> ::= <type> '/' <subtype>
    //
    // <type_attrs_list> ::= <attr_value_pair> [ ';' <attr_value_pair> ]*
    //                       | [ <attr_value_pair> ]+
    //
    // <attr_value_pair> ::= <attr_name> '=' <attr_value>
    //
    // <attr_name> ::= 'description' | 'action' | 'application'
    //                 | 'file_extensions' | 'icon'
    //
    // <attr_value> ::= <legal_char>*
    //
    // Embedded ';' in an <attr_value> are quoted with leading '\' .
    //
    // Interpretation of <attr_value> depends on the <attr_name> it is
    // associated with.
    //

    void parse(String type, String attrs) {
        MimeEntry newEntry = new MimeEntry(type);

        // REMIND handle embedded ';' and '|' and literal '"'
        StringTokenizer tokenizer = new StringTokenizer(attrs, ";");
        while (tokenizer.hasMoreTokens()) {
            String pair = tokenizer.nextToken();
            parse(pair, newEntry);
        }

        add(newEntry);
    }

    void parse(String pair, MimeEntry entry) {
        // REMIND add exception handling...
        String name = null;
        String value = null;

        boolean gotName = false;
        StringTokenizer tokenizer = new StringTokenizer(pair, "=");
        while (tokenizer.hasMoreTokens()) {
            if (gotName) {
                value = tokenizer.nextToken().trim();
            }
            else {
                name = tokenizer.nextToken().trim();
                gotName = true;
            }
        }

        fill(entry, name, value);
    }

    void fill(MimeEntry entry, String name, String value) {
        if ("description".equalsIgnoreCase(name)) {
            entry.setDescription(value);
        }
        else if ("action".equalsIgnoreCase(name)) {
            entry.setAction(getActionCode(value));
        }
        else if ("application".equalsIgnoreCase(name)) {
            entry.setCommand(value);
        }
        else if ("icon".equalsIgnoreCase(name)) {
            entry.setImageFileName(value);
        }
        else if ("file_extensions".equalsIgnoreCase(name)) {
            entry.setExtensions(value);
        }

        // else illegal name exception
    }

    int getActionCode(String action) {
        for (int i = 0; i < MimeEntry.actionKeywords.length; i++) {
            if (action.equalsIgnoreCase(MimeEntry.actionKeywords[i])) {
                return i;
            }
        }

        return MimeEntry.UNKNOWN;
    }

    public Properties getAsProperties() {
        Properties properties = new Properties();
        Enumeration<MimeEntry> e = elements();
        while (e.hasMoreElements()) {
            MimeEntry entry = e.nextElement();
            properties.put(entry.getType(), entry.toProperty());
        }

        return properties;
    }

    protected boolean saveAsProperties(File file) {
        try (FileOutputStream os = new FileOutputStream(file)) {
            Properties properties = getAsProperties();
            properties.put("temp.file.template", tempFileTemplate);
            String tag;
            // Perform the property security check for user.name
            @SuppressWarnings("removal")
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPropertyAccess("user.name");
            }
            String user = StaticProperty.userName();
            if (user != null) {
                tag = "; customized for " + user;
                properties.store(os, filePreamble + tag);
            }
            else {
                properties.store(os, filePreamble);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
    /*
     * Debugging utilities
     *
    public void list(PrintStream out) {
        Enumeration keys = entries.keys();
        while (keys.hasMoreElements()) {
            String key = (String)keys.nextElement();
            MimeEntry entry = (MimeEntry)entries.get(key);
            out.println(key + ": " + entry);
        }
    }

    public static void main(String[] args) {
        MimeTable testTable = MimeTable.getDefaultTable();

        Enumeration e = testTable.elements();
        while (e.hasMoreElements()) {
            MimeEntry entry = (MimeEntry)e.nextElement();
            System.out.println(entry);
        }

        testTable.save(File.separator + "tmp" +
                       File.separator + "mime_table.save");
    }
    */
}
