/*
 * Copyright (c) 1998, 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.security.auth;

import java.io.*;
import java.lang.RuntimePermission;
import java.net.MalformedURLException;
import java.net.SocketPermission;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Vector;
import java.util.StringTokenizer;
import java.security.GeneralSecurityException;
import sun.security.util.PropertyExpander;

/**
 * The policy for a Java runtime (specifying
 * which permissions are available for code from various principals)
 * is represented as a separate
 * persistent configuration.  The configuration may be stored as a
 * flat ASCII file, as a serialized binary file of
 * the Policy class, or as a database. <p>
 *
 * <p>The Java runtime creates one global Policy object, which is used to
 * represent the static policy configuration file.  It is consulted by
 * a ProtectionDomain when the protection domain initializes its set of
 * permissions. <p>
 *
 * <p>The Policy <code>init</code> method parses the policy
 * configuration file, and then
 * populates the Policy object.  The Policy object is agnostic in that
 * it is not involved in making policy decisions.  It is merely the
 * Java runtime representation of the persistent policy configuration
 * file. <p>
 *
 * <p>When a protection domain needs to initialize its set of
 * permissions, it executes code such as the following
 * to ask the global Policy object to populate a
 * Permissions object with the appropriate permissions:
 * <pre>
 *  policy = Policy.getPolicy();
 *  Permissions perms = policy.getPermissions(MyCodeSource)
 * </pre>
 *
 * <p>The protection domain passes in a CodeSource
 * object, which encapsulates its codebase (URL) and public key attributes.
 * The Policy object evaluates the global policy in light of who the
 * principal is and returns an appropriate Permissions object.
 *
 * @deprecated As of JDK&nbsp;1.4, replaced by
 *             {@link sun.security.provider.PolicyParser}.
 *             This class is entirely deprecated.
 *
 * @author Roland Schemers
 *
 * @since 1.2
 */
@Deprecated
class PolicyParser {

    private static final java.util.ResourceBundle rb =
        java.security.AccessController.doPrivileged
        (new java.security.PrivilegedAction<java.util.ResourceBundle>() {
            public java.util.ResourceBundle run() {
                    return (java.util.ResourceBundle.getBundle
                                ("sun.security.util.AuthResources"));
           }
        });

    private Vector<GrantEntry> grantEntries;

    // Convenience variables for parsing
    private static final sun.security.util.Debug debug =
        sun.security.util.Debug.getInstance("parser", "\t[Auth Policy Parser]");
    private StreamTokenizer st;
    private int lookahead;
    private int linenum;
    private boolean expandProp = false;
    private String keyStoreUrlString = null; // unexpanded
    private String keyStoreType = null;

    private String expand(String value)
        throws PropertyExpander.ExpandException
    {
        if (expandProp)
            return PropertyExpander.expand(value);
        else
            return value;
    }
    /**
     * Creates a PolicyParser object.
     */

    public PolicyParser() {
        grantEntries = new Vector<GrantEntry>();
    }


    public PolicyParser(boolean expandProp) {
        this();
        this.expandProp = expandProp;
    }

    /**
     * Reads a policy configuration into the Policy object using a
     * Reader object. <p>
     *
     * @param policy the policy Reader object.
     *
     * @exception ParsingException if the policy configuration contains
     *          a syntax error.
     *
     * @exception IOException if an error occurs while reading the policy
     *          configuration.
     */

    public void read(Reader policy)
        throws ParsingException, IOException
    {
        if (!(policy instanceof BufferedReader)) {
            policy = new BufferedReader(policy);
        }

        /**
         * Configure the stream tokenizer:
         *      Recognize strings between "..."
         *      Don't convert words to lowercase
         *      Recognize both C-style and C++-style comments
         *      Treat end-of-line as white space, not as a token
         */
        st   = new StreamTokenizer(policy);

        st.resetSyntax();
        st.wordChars('a', 'z');
        st.wordChars('A', 'Z');
        st.wordChars('.', '.');
        st.wordChars('0', '9');
        st.wordChars('_', '_');
        st.wordChars('$', '$');
        st.wordChars(128 + 32, 255);
        st.whitespaceChars(0, ' ');
        st.commentChar('/');
        st.quoteChar('\'');
        st.quoteChar('"');
        st.lowerCaseMode(false);
        st.ordinaryChar('/');
        st.slashSlashComments(true);
        st.slashStarComments(true);

        /**
         * The main parsing loop.  The loop is executed once
         * for each entry in the config file.      The entries
         * are delimited by semicolons.   Once we've read in
         * the information for an entry, go ahead and try to
         * add it to the policy vector.
         *
         */

        lookahead = st.nextToken();
        while (lookahead != StreamTokenizer.TT_EOF) {
            if (peek("grant")) {
                GrantEntry ge = parseGrantEntry();
                // could be null if we couldn't expand a property
                if (ge != null)
                    add(ge);
            } else if (peek("keystore") && keyStoreUrlString==null) {
                // only one keystore entry per policy file, others will be
                // ignored
                parseKeyStoreEntry();
            } else {
                // error?
            }
            match(";");
        }
    }

    public void add(GrantEntry ge)
    {
        grantEntries.addElement(ge);
    }

    public void replace(GrantEntry origGe, GrantEntry newGe)
    {
        grantEntries.setElementAt(newGe, grantEntries.indexOf(origGe));
    }

    public boolean remove(GrantEntry ge)
    {
        return grantEntries.removeElement(ge);
    }

    /**
     * Returns the (possibly expanded) keystore location, or null if the
     * expansion fails.
     */
    public String getKeyStoreUrl() {
        try {
            if (keyStoreUrlString!=null && keyStoreUrlString.length()!=0) {
                return expand(keyStoreUrlString).replace(File.separatorChar,
                                                         '/');
            }
        } catch (PropertyExpander.ExpandException peee) {
            return null;
        }
        return null;
    }

    public void setKeyStoreUrl(String url) {
        keyStoreUrlString = url;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(String type) {
        keyStoreType = type;
    }

    /**
     * Enumerate all the entries in the global policy object.
     * This method is used by policy admin tools.   The tools
     * should use the Enumeration methods on the returned object
     * to fetch the elements sequentially.
     */
    public Enumeration<GrantEntry> grantElements(){
        return grantEntries.elements();
    }

    /**
     * write out the policy
     */

    public void write(Writer policy)
    {
        PrintWriter out = new PrintWriter(new BufferedWriter(policy));

        Enumeration<GrantEntry> enum_ = grantElements();

        out.println("/* AUTOMATICALLY GENERATED ON "+
                    (new java.util.Date()) + "*/");
        out.println("/* DO NOT EDIT */");
        out.println();

        // write the (unexpanded) keystore entry as the first entry of the
        // policy file
        if (keyStoreUrlString != null) {
            writeKeyStoreEntry(out);
        }

        // write "grant" entries
        while (enum_.hasMoreElements()) {
            GrantEntry ge = enum_.nextElement();
            ge.write(out);
            out.println();
        }
        out.flush();
    }

    /**
     * parses a keystore entry
     */
    private void parseKeyStoreEntry() throws ParsingException, IOException {
        match("keystore");
        keyStoreUrlString = match("quoted string");

        // parse keystore type
        if (!peek(",")) {
            return; // default type
        }
        match(",");

        if (peek("\"")) {
            keyStoreType = match("quoted string");
        } else {
            throw new ParsingException(st.lineno(),
                        rb.getString("expected keystore type"));
        }
    }

    /**
     * writes the (unexpanded) keystore entry
     */
    private void writeKeyStoreEntry(PrintWriter out) {
        out.print("keystore \"");
        out.print(keyStoreUrlString);
        out.print('"');
        if (keyStoreType != null && keyStoreType.length() > 0)
            out.print(", \"" + keyStoreType + "\"");
        out.println(";");
        out.println();
    }

    /**
     * parse a Grant entry
     */
    private GrantEntry parseGrantEntry()
        throws ParsingException, IOException
    {
        GrantEntry e = new GrantEntry();
        LinkedList<PrincipalEntry> principals = null;
        boolean ignoreEntry = false;

        match("grant");

        while(!peek("{")) {

            if (peekAndMatch("Codebase")) {
                e.codeBase = match("quoted string");
                peekAndMatch(",");
            } else if (peekAndMatch("SignedBy")) {
                e.signedBy = match("quoted string");
                peekAndMatch(",");
            } else if (peekAndMatch("Principal")) {
                if (principals == null) {
                    principals = new LinkedList<PrincipalEntry>();
                }

                // check for principalClass wildcard
                String principalClass;
                if (peek("*")) {
                    match("*");
                    principalClass = PrincipalEntry.WILDCARD_CLASS;
                } else {
                    principalClass = match("principal type");
                }

                // check for principalName wildcard
                String principalName;
                if (peek("*")) {
                    match("*");
                    principalName = PrincipalEntry.WILDCARD_NAME;
                } else {
                    principalName = match("quoted string");
                }

                // disallow WILDCARD_CLASS && actual name
                if (principalClass.equals(PrincipalEntry.WILDCARD_CLASS) &&
                    !principalName.equals(PrincipalEntry.WILDCARD_NAME)) {
                    if (debug != null)
                        debug.println("disallowing principal that has " +
                                "WILDCARD class but no WILDCARD name");
                    throw new ParsingException
                        (st.lineno(),
                        rb.getString("can not specify Principal with a ") +
                        rb.getString("wildcard class without a wildcard name"));
                }

                try {
                    principalName = expand(principalName);
                    principals.add
                        (new PrincipalEntry(principalClass, principalName));
                } catch (PropertyExpander.ExpandException peee) {
                    // ignore the entire policy entry
                    // but continue parsing all the info
                    // so we can get to the next entry
                    if (debug != null)
                        debug.println("principal name expansion failed: " +
                                        principalName);
                    ignoreEntry = true;
                }
                peekAndMatch(",");
            } else {
                throw new
                 ParsingException(st.lineno(),
                        rb.getString("expected codeBase or SignedBy"));
            }
        }

        // disallow non principal-based grant entries
        if (principals == null) {
            throw new ParsingException
                (st.lineno(),
                rb.getString("only Principal-based grant entries permitted"));
        }

        e.principals = principals;
        match("{");

        while(!peek("}")) {
            if (peek("Permission")) {
                try {
                    PermissionEntry pe = parsePermissionEntry();
                    e.add(pe);
                } catch (PropertyExpander.ExpandException peee) {
                    // ignore. The add never happened
                    skipEntry();  // BugId 4219343
                }
                match(";");
            } else {
                throw new
                    ParsingException(st.lineno(),
                    rb.getString("expected permission entry"));
            }
        }
        match("}");

        try {
            if (e.codeBase != null)
              e.codeBase = expand(e.codeBase).replace(File.separatorChar, '/');
            e.signedBy = expand(e.signedBy);
        } catch (PropertyExpander.ExpandException peee) {
            return null;
        }

        return (ignoreEntry == true) ? null : e;
    }

    /**
     * parse a Permission entry
     */
    private PermissionEntry parsePermissionEntry()
        throws ParsingException, IOException, PropertyExpander.ExpandException
    {
        PermissionEntry e = new PermissionEntry();

        // Permission
        match("Permission");
        e.permission = match("permission type");

        if (peek("\"")) {
            // Permission name
            e.name = expand(match("quoted string"));
        }

        if (!peek(",")) {
            return e;
        }
        match(",");

        if (peek("\"")) {
                e.action = expand(match("quoted string"));
                if (!peek(",")) {
                    return e;
                }
                match(",");
        }

        if (peekAndMatch("SignedBy")) {
            e.signedBy = expand(match("quoted string"));
        }
        return e;
    }

    private boolean peekAndMatch(String expect)
        throws ParsingException, IOException
    {
        if (peek(expect)) {
            match(expect);
            return true;
        } else {
            return false;
        }
    }

    private boolean peek(String expect) {
        boolean found = false;

        switch (lookahead) {

        case StreamTokenizer.TT_WORD:
            if (expect.equalsIgnoreCase(st.sval))
                found = true;
            break;
        case ',':
            if (expect.equalsIgnoreCase(","))
                found = true;
            break;
        case '{':
            if (expect.equalsIgnoreCase("{"))
                found = true;
            break;
        case '}':
            if (expect.equalsIgnoreCase("}"))
                found = true;
            break;
        case '"':
            if (expect.equalsIgnoreCase("\""))
                found = true;
            break;
        case '*':
            if (expect.equalsIgnoreCase("*"))
                found = true;
            break;
        default:

        }
        return found;
    }

    private String match(String expect)
        throws ParsingException, IOException
    {
        String value = null;

        switch (lookahead) {
        case StreamTokenizer.TT_NUMBER:
            throw new ParsingException(st.lineno(), expect,
                                        rb.getString("number ") +
                                        String.valueOf(st.nval));
        case StreamTokenizer.TT_EOF:
           throw new ParsingException
                (rb.getString("expected ") + expect +
                rb.getString(", read end of file"));
        case StreamTokenizer.TT_WORD:
            if (expect.equalsIgnoreCase(st.sval)) {
                lookahead = st.nextToken();
            } else if (expect.equalsIgnoreCase("permission type")) {
                value = st.sval;
                lookahead = st.nextToken();
            } else if (expect.equalsIgnoreCase("principal type")) {
                value = st.sval;
                lookahead = st.nextToken();
            } else {
                throw new ParsingException(st.lineno(), expect, st.sval);
            }
            break;
        case '"':
            if (expect.equalsIgnoreCase("quoted string")) {
                value = st.sval;
                lookahead = st.nextToken();
            } else if (expect.equalsIgnoreCase("permission type")) {
                value = st.sval;
                lookahead = st.nextToken();
            } else if (expect.equalsIgnoreCase("principal type")) {
                value = st.sval;
                lookahead = st.nextToken();
            } else {
                throw new ParsingException(st.lineno(), expect, st.sval);
            }
            break;
        case ',':
            if (expect.equalsIgnoreCase(","))
                lookahead = st.nextToken();
            else
                throw new ParsingException(st.lineno(), expect, ",");
            break;
        case '{':
            if (expect.equalsIgnoreCase("{"))
                lookahead = st.nextToken();
            else
                throw new ParsingException(st.lineno(), expect, "{");
            break;
        case '}':
            if (expect.equalsIgnoreCase("}"))
                lookahead = st.nextToken();
            else
                throw new ParsingException(st.lineno(), expect, "}");
            break;
        case ';':
            if (expect.equalsIgnoreCase(";"))
                lookahead = st.nextToken();
            else
                throw new ParsingException(st.lineno(), expect, ";");
            break;
        case '*':
            if (expect.equalsIgnoreCase("*"))
                lookahead = st.nextToken();
            else
                throw new ParsingException(st.lineno(), expect, "*");
            break;
        default:
            throw new ParsingException(st.lineno(), expect,
                               new String(new char[] {(char)lookahead}));
        }
        return value;
    }

    /**
     * skip all tokens for this entry leaving the delimiter ";"
     * in the stream.
     */
    private void skipEntry()
        throws ParsingException, IOException
    {
      while(lookahead != ';') {
        switch (lookahead) {
        case StreamTokenizer.TT_NUMBER:
            throw new ParsingException(st.lineno(), ";",
                                       rb.getString("number ") +
                                        String.valueOf(st.nval));
        case StreamTokenizer.TT_EOF:
          throw new ParsingException
                (rb.getString("expected ';', read end of file"));
        default:
          lookahead = st.nextToken();
        }
      }
    }

    /**
     * Each grant entry in the policy configuration file is
     * represented by a
     * GrantEntry object.  <p>
     *
     * <p>
     * For example, the entry
     * <pre>
     *      grant signedBy "Duke" {
     *          permission java.io.FilePermission "/tmp", "read,write";
     *      };
     *
     * </pre>
     * is represented internally
     * <pre>
     *
     * pe = new PermissionEntry("java.io.FilePermission",
     *                           "/tmp", "read,write");
     *
     * ge = new GrantEntry("Duke", null);
     *
     * ge.add(pe);
     *
     * </pre>
     *
     * @author Roland Schemers
     *
     * version 1.19, 05/21/98
     */

    static class GrantEntry {

        public String signedBy;
        public String codeBase;
        public LinkedList<PrincipalEntry> principals;
        public Vector<PermissionEntry> permissionEntries;

        public GrantEntry() {
            permissionEntries = new Vector<PermissionEntry>();
        }

        public GrantEntry(String signedBy, String codeBase) {
            this.codeBase = codeBase;
            this.signedBy = signedBy;
            permissionEntries = new Vector<PermissionEntry>();
        }

        public void add(PermissionEntry pe)
        {
            permissionEntries.addElement(pe);
        }

        public boolean remove(PermissionEntry pe)
        {
            return permissionEntries.removeElement(pe);
        }

        public boolean contains(PermissionEntry pe)
        {
            return permissionEntries.contains(pe);
        }

        /**
         * Enumerate all the permission entries in this GrantEntry.
         */
        public Enumeration<PermissionEntry> permissionElements(){
            return permissionEntries.elements();
        }


        public void write(PrintWriter out) {
            out.print("grant");
            if (signedBy != null) {
                out.print(" signedBy \"");
                out.print(signedBy);
                out.print('"');
                if (codeBase != null)
                    out.print(", ");
            }
            if (codeBase != null) {
                out.print(" codeBase \"");
                out.print(codeBase);
                out.print('"');
                if (principals != null && principals.size() > 0)
                    out.print(",\n");
            }
            if (principals != null && principals.size() > 0) {
                ListIterator<PrincipalEntry> pli = principals.listIterator();
                while (pli.hasNext()) {
                    out.print("\tPrincipal ");
                    PrincipalEntry pe = pli.next();
                    out.print(pe.principalClass +
                                " \"" + pe.principalName + "\"");
                    if (pli.hasNext())
                        out.print(",\n");
                }
            }
            out.println(" {");
            Enumeration<PermissionEntry> enum_ = permissionEntries.elements();
            while (enum_.hasMoreElements()) {
                PermissionEntry pe = enum_.nextElement();
                out.write("  ");
                pe.write(out);
            }
            out.println("};");
        }

    }

    /**
     * Principal info (class and name) in a grant entry
     */
    static class PrincipalEntry {

        static final String WILDCARD_CLASS = "WILDCARD_PRINCIPAL_CLASS";
        static final String WILDCARD_NAME = "WILDCARD_PRINCIPAL_NAME";

        String principalClass;
        String principalName;

        /**
         * A PrincipalEntry consists of the <code>Principal</code>
         * class and <code>Principal</code> name.
         *
         * <p>
         *
         * @param principalClass the <code>Principal</code> class. <p>
         *
         * @param principalName the <code>Principal</code> name. <p>
         */
        public PrincipalEntry(String principalClass, String principalName) {
            if (principalClass == null || principalName == null)
                throw new NullPointerException
                        ("null principalClass or principalName");
            this.principalClass = principalClass;
            this.principalName = principalName;
        }

        /**
         * Test for equality between the specified object and this object.
         * Two PrincipalEntries are equal if their PrincipalClass and
         * PrincipalName values are equal.
         *
         * <p>
         *
         * @param obj the object to test for equality with this object.
         *
         * @return true if the objects are equal, false otherwise.
         */
        public boolean equals(Object obj) {
            if (this == obj)
                return true;

            if (!(obj instanceof PrincipalEntry))
                return false;

            PrincipalEntry that = (PrincipalEntry)obj;
            if (this.principalClass.equals(that.principalClass) &&
                this.principalName.equals(that.principalName)) {
                return true;
            }

            return false;
        }

        /**
         * Return a hashcode for this <code>PrincipalEntry</code>.
         *
         * <p>
         *
         * @return a hashcode for this <code>PrincipalEntry</code>.
         */
        public int hashCode() {
            return principalClass.hashCode();
        }
    }

    /**
     * Each permission entry in the policy configuration file is
     * represented by a
     * PermissionEntry object.  <p>
     *
     * <p>
     * For example, the entry
     * <pre>
     *          permission java.io.FilePermission "/tmp", "read,write";
     * </pre>
     * is represented internally
     * <pre>
     *
     * pe = new PermissionEntry("java.io.FilePermission",
     *                           "/tmp", "read,write");
     * </pre>
     *
     * @author Roland Schemers
     *
     * version 1.19, 05/21/98
     */

    static class PermissionEntry {

        public String permission;
        public String name;
        public String action;
        public String signedBy;

        public PermissionEntry() {
        }

        public PermissionEntry(String permission,
                        String name,
                        String action) {
            this.permission = permission;
            this.name = name;
            this.action = action;
        }

        /**
         * Calculates a hash code value for the object.  Objects
         * which are equal will also have the same hashcode.
         */
        public int hashCode() {
            int retval = permission.hashCode();
            if (name != null) retval ^= name.hashCode();
            if (action != null) retval ^= action.hashCode();
            return retval;
        }

        public boolean equals(Object obj) {
            if (obj == this)
                return true;

            if (! (obj instanceof PermissionEntry))
                return false;

            PermissionEntry that = (PermissionEntry) obj;

            if (this.permission == null) {
                if (that.permission != null) return false;
            } else {
                if (!this.permission.equals(that.permission)) return false;
            }

            if (this.name == null) {
                if (that.name != null) return false;
            } else {
                if (!this.name.equals(that.name)) return false;
            }

            if (this.action == null) {
                if (that.action != null) return false;
            } else {
                if (!this.action.equals(that.action)) return false;
            }

            if (this.signedBy == null) {
                if (that.signedBy != null) return false;
            } else {
                if (!this.signedBy.equals(that.signedBy)) return false;
            }

            // everything matched -- the 2 objects are equal
            return true;
        }

        public void write(PrintWriter out) {
            out.print("permission ");
            out.print(permission);
            if (name != null) {
                out.print(" \"");

                // have to add escape chars for quotes
                if (name.indexOf("\"") != -1) {
                    int numQuotes = 0;
                    char[] chars = name.toCharArray();

                    // count the number of quote chars
                    for (int i = 0; i < chars.length; i++) {
                        if (chars[i] == '"')
                            numQuotes++;
                    }

                    // now, add an escape char before each quote
                    char[] newChars = new char[chars.length + numQuotes];
                    for (int i = 0, j = 0; i < chars.length; i++) {
                        if (chars[i] != '"') {
                            newChars[j++] = chars[i];
                        } else {
                            newChars[j++] = '\\';
                            newChars[j++] = chars[i];
                        }
                    }
                    name = new String(newChars);
                }
                out.print(name);
                out.print('"');
            }
            if (action != null) {
                out.print(", \"");
                out.print(action);
                out.print('"');
            }
            if (signedBy != null) {
                out.print(", signedBy \"");
                out.print(signedBy);
                out.print('"');
            }
            out.println(";");
        }
    }

    static class ParsingException extends GeneralSecurityException {

        private static final long serialVersionUID = 8240970523155877400L;

        /**
         * Constructs a ParsingException with the specified
         * detail message. A detail message is a String that describes
         * this particular exception, which may, for example, specify which
         * algorithm is not available.
         *
         * @param msg the detail message.
         */
        public ParsingException(String msg) {
            super(msg);
        }

        public ParsingException(int line, String msg) {
            super(rb.getString("line ") + line + rb.getString(": ") + msg);
        }

        public ParsingException(int line, String expect, String actual) {
            super(rb.getString("line ") + line + rb.getString(": expected '") +
                expect + rb.getString("', found '") + actual +
                rb.getString("'"));
        }
    }

    public static void main(String arg[]) throws Exception {
        PolicyParser pp = new PolicyParser(true);
        pp.read(new FileReader(arg[0]));
        FileWriter fr = new FileWriter(arg[1]);
        pp.write(fr);
        fr.close();
    }
}
