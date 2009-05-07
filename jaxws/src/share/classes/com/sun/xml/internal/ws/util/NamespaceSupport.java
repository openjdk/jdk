/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.xml.internal.ws.util;

import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;

/**
 * Encapsulate Namespace logic for use by SAX drivers.
 *
 * <blockquote>
 * <em>This module, both source code and documentation, is in the
 * Public Domain, and comes with <strong>NO WARRANTY</strong>.</em>
 * </blockquote>
 *
 * <p>This class encapsulates the logic of Namespace processing:
 * it tracks the declarations currently in force for each context
 * and automatically processes qualified XML 1.0 names into their
 * Namespace parts; it can also be used in reverse for generating
 * XML 1.0 from Namespaces.</p>
 *
 * <p>Namespace support objects are reusable, but the reset method
 * must be invoked between each session.</p>
 *
 * <p>Here is a simple session:</p>
 *
 * <pre>
 * String parts[] = new String[3];
 * NamespaceSupport support = new NamespaceSupport();
 *
 * support.pushContext();
 * support.declarePrefix("", "http://www.w3.org/1999/xhtml");
 * support.declarePrefix("dc", "http://www.purl.org/dc#");
 *
 * String parts[] = support.processName("p", parts, false);
 * System.out.println("Namespace URI: " + parts[0]);
 * System.out.println("Local name: " + parts[1]);
 * System.out.println("Raw name: " + parts[2]);

 * String parts[] = support.processName("dc:title", parts, false);
 * System.out.println("Namespace URI: " + parts[0]);
 * System.out.println("Local name: " + parts[1]);
 * System.out.println("Raw name: " + parts[2]);

 * support.popContext();
 * </pre>
 *
 * <p>Note that this class is optimized for the use case where most
 * elements do not contain Namespace declarations: if the same
 * prefix/URI mapping is repeated for each context (for example), this
 * class will be somewhat less efficient.</p>
 *
 * @author David Megginson
 * @author WS Development Team
 */
public class NamespaceSupport {

    /* added two new methods, slideContextUp() and slideContextDown()
     * needed to implement the revised streaming parser class (Parser2)
     */

    ////////////////////////////////////////////////////////////////////
    // Constants.
    ////////////////////////////////////////////////////////////////////

    /**
     * The XML Namespace as a constant.
     *
     * <p>This is the Namespace URI that is automatically mapped
     * to the "xml" prefix.</p>
     */
    public final static String XMLNS = "http://www.w3.org/XML/1998/namespace";

    /**
     * An empty enumeration.
     */
    private final static Iterable<String> EMPTY_ENUMERATION =
        new ArrayList<String>();

    ////////////////////////////////////////////////////////////////////
    // Constructor.
    ////////////////////////////////////////////////////////////////////

    /**
     * Create a new Namespace support object.
     */
    public NamespaceSupport() {
        reset();
    }

    // PBG May 6 2002 added a copy constructor to support recording
    public NamespaceSupport(NamespaceSupport that) {
        contexts = new Context[that.contexts.length];
        currentContext = null;
        contextPos = that.contextPos;

        Context currentParent = null;

        for (int i = 0; i < that.contexts.length; i++) {
            Context thatContext = that.contexts[i];

            if (thatContext == null) {
                contexts[i] = null;
                continue;
            }

            Context thisContext = new Context(thatContext, currentParent);
            contexts[i] = thisContext;
            if (that.currentContext == thatContext) {
                currentContext = thisContext;
            }

            currentParent = thisContext;
        }
    }

    ////////////////////////////////////////////////////////////////////
    // Context management.
    ////////////////////////////////////////////////////////////////////

    /**
     * Reset this Namespace support object for reuse.
     *
     * <p>It is necessary to invoke this method before reusing the
     * Namespace support object for a new session.</p>
     */
    public void reset() {
        contexts = new Context[32];
        contextPos = 0;
        contexts[contextPos] = currentContext = new Context();
        currentContext.declarePrefix("xml", XMLNS);
    }

    /**
     * Start a new Namespace context.
     *
     * <p>Normally, you should push a new context at the beginning
     * of each XML element: the new context will automatically inherit
     * the declarations of its parent context, but it will also keep
     * track of which declarations were made within this context.</p>
     *
     * <p>The Namespace support object always starts with a base context
     * already in force: in this context, only the "xml" prefix is
     * declared.</p>
     *
     * @see #popContext
     */
    public void pushContext() {
        int max = contexts.length;
        contextPos++;

        // Extend the array if necessary
        if (contextPos >= max) {
            Context newContexts[] = new Context[max * 2];
            System.arraycopy(contexts, 0, newContexts, 0, max);
            contexts = newContexts;
        }

        // Allocate the context if necessary.
        currentContext = contexts[contextPos];
        if (currentContext == null) {
            contexts[contextPos] = currentContext = new Context();
        }

        // Set the parent, if any.
        if (contextPos > 0) {
            currentContext.setParent(contexts[contextPos - 1]);
        }
    }

    /**
     * Revert to the previous Namespace context.
     *
     * <p>Normally, you should pop the context at the end of each
     * XML element.  After popping the context, all Namespace prefix
     * mappings that were previously in force are restored.</p>
     *
     * <p>You must not attempt to declare additional Namespace
     * prefixes after popping a context, unless you push another
     * context first.</p>
     *
     * @see #pushContext
     */
    public void popContext() {
        contextPos--;
        if (contextPos < 0) {
            throw new EmptyStackException();
        }
        currentContext = contexts[contextPos];
    }

    /*
     * added for the revised streaming parser class (Parser2)
     * Move the context artificially up one level (i.e. contracting it).
     */
    public void slideContextUp() {
        contextPos--;
        currentContext = contexts[contextPos];
    }

    /*
     * added for the revised streaming parser class (Parser2)
     * Move the context artificially down one level (i.e. expanding it).
     */
    public void slideContextDown() {
        contextPos++;

        if (contexts[contextPos] == null) {
            // trying to slide to a context that was never created
            contexts[contextPos] = contexts[contextPos - 1];
        }

        currentContext = contexts[contextPos];
    }

    ////////////////////////////////////////////////////////////////////
    // Operations within a context.
    ////////////////////////////////////////////////////////////////////

    /**
     * Declare a Namespace prefix.
     *
     * <p>This method declares a prefix in the current Namespace
     * context; the prefix will remain in force until this context
     * is popped, unless it is shadowed in a descendant context.</p>
     *
     * <p>To declare a default Namespace, use the empty string.  The
     * prefix must not be "xml" or "xmlns".</p>
     *
     * <p>Note that you must <em>not</em> declare a prefix after
     * you've pushed and popped another Namespace.</p>
     *
     * <p>Note that there is an asymmetry in this library: while {@link
     * #getPrefix getPrefix} will not return the default "" prefix,
     * even if you have declared one; to check for a default prefix,
     * you have to look it up explicitly using {@link #getURI getURI}.
     * This asymmetry exists to make it easier to look up prefixes
     * for attribute names, where the default prefix is not allowed.</p>
     *
     * @param prefix The prefix to declare, or null for the empty
     *        string.
     * @param uri The Namespace URI to associate with the prefix.
     * @return true if the prefix was legal, false otherwise
     * @see #processName
     * @see #getURI
     * @see #getPrefix
     */
    public boolean declarePrefix(String prefix, String uri) {
        // bugfix#: 4989753
        if ((prefix.equals("xml") && !uri.equals(SOAPNamespaceConstants.XMLNS))
            || prefix.equals("xmlns")) {
            return false;
        } else {
            currentContext.declarePrefix(prefix, uri);
            return true;
        }
    }

    /**
     * Process a raw XML 1.0 name.
     *
     * <p>This method processes a raw XML 1.0 name in the current
     * context by removing the prefix and looking it up among the
     * prefixes currently declared.  The return value will be the
     * array supplied by the caller, filled in as follows:</p>
     *
     * <dl>
     * <dt>parts[0]</dt>
     * <dd>The Namespace URI, or an empty string if none is
     *  in use.</dd>
     * <dt>parts[1]</dt>
     * <dd>The local name (without prefix).</dd>
     * <dt>parts[2]</dt>
     * <dd>The original raw name.</dd>
     * </dl>
     *
     * <p>All of the strings in the array will be internalized.  If
     * the raw name has a prefix that has not been declared, then
     * the return value will be null.</p>
     *
     * <p>Note that attribute names are processed differently than
     * element names: an unprefixed element name will received the
     * default Namespace (if any), while an unprefixed element name
     * will not.</p>
     *
     * @param qName The raw XML 1.0 name to be processed.
     * @param parts An array supplied by the caller, capable of
     *        holding at least three members.
     * @param isAttribute A flag indicating whether this is an
     *        attribute name (true) or an element name (false).
     * @return The supplied array holding three internalized strings
     *        representing the Namespace URI (or empty string), the
     *        local name, and the raw XML 1.0 name; or null if there
     *        is an undeclared prefix.
     * @see #declarePrefix
     * @see java.lang.String#intern */
    public String[] processName(
        String qName,
        String parts[],
        boolean isAttribute) {
        String myParts[] = currentContext.processName(qName, isAttribute);
        if (myParts == null) {
            return null;
        } else {
            parts[0] = myParts[0];
            parts[1] = myParts[1];
            parts[2] = myParts[2];
            return parts;
        }
    }

    /**
     * Look up a prefix and get the currently-mapped Namespace URI.
     *
     * <p>This method looks up the prefix in the current context.
     * Use the empty string ("") for the default Namespace.</p>
     *
     * @param prefix The prefix to look up.
     * @return The associated Namespace URI, or null if the prefix
     *         is undeclared in this context.
     * @see #getPrefix
     * @see #getPrefixes
     */
    public String getURI(String prefix) {
        return currentContext.getURI(prefix);
    }

    /**
     * Return an enumeration of all prefixes currently declared.
     *
     * <p><strong>Note:</strong> if there is a default prefix, it will not be
     * returned in this enumeration; check for the default prefix
     * using the {@link #getURI getURI} with an argument of "".</p>
     *
     * @return An enumeration of all prefixes declared in the
     *         current context except for the empty (default)
     *         prefix.
     * @see #getDeclaredPrefixes
     * @see #getURI
     */
    public Iterable<String> getPrefixes() {
        return currentContext.getPrefixes();
    }

    /**
     * Return one of the prefixes mapped to a Namespace URI.
     *
     * <p>If more than one prefix is currently mapped to the same
     * URI, this method will make an arbitrary selection; if you
     * want all of the prefixes, use the {@link #getPrefixes}
     * method instead.</p>
     *
     * <p><strong>Note:</strong> this will never return the empty (default) prefix;
     * to check for a default prefix, use the {@link #getURI getURI}
     * method with an argument of "".</p>
     *
     * @param uri The Namespace URI.
     * @return One of the prefixes currently mapped to the URI supplied,
     *         or null if none is mapped or if the URI is assigned to
     *         the default Namespace.
     * @see #getPrefixes(java.lang.String)
     * @see #getURI
     */
    public String getPrefix(String uri) {
        return currentContext.getPrefix(uri);
    }

    /**
     * Return an enumeration of all prefixes currently declared for a URI.
     *
     * <p>This method returns prefixes mapped to a specific Namespace
     * URI.  The xml: prefix will be included.  If you want only one
     * prefix that's mapped to the Namespace URI, and you don't care
     * which one you get, use the {@link #getPrefix getPrefix}
     *  method instead.</p>
     *
     * <p><strong>Note:</strong> the empty (default) prefix is <em>never</em> included
     * in this enumeration; to check for the presence of a default
     * Namespace, use the {@link #getURI getURI} method with an
     * argument of "".</p>
     *
     * @param uri The Namespace URI.
     * @return An enumeration of all prefixes declared in the
     *         current context.
     * @see #getPrefix
     * @see #getDeclaredPrefixes
     * @see #getURI
     */
    public Iterator getPrefixes(String uri) {
        List prefixes = new ArrayList();
        for (String prefix: getPrefixes()) {
            if (uri.equals(getURI(prefix))) {
                prefixes.add(prefix);
            }
        }
        return prefixes.iterator();
    }

    /**
     * Return an enumeration of all prefixes declared in this context.
     *
     * <p>The empty (default) prefix will be included in this
     * enumeration; note that this behaviour differs from that of
     * {@link #getPrefix} and {@link #getPrefixes}.</p>
     *
     * @return An enumeration of all prefixes declared in this
     *         context.
     * @see #getPrefixes
     * @see #getURI
     */
    public Iterable<String> getDeclaredPrefixes() {
        return currentContext.getDeclaredPrefixes();
    }

    ////////////////////////////////////////////////////////////////////
    // Internal state.
    ////////////////////////////////////////////////////////////////////

    private Context contexts[];
    private Context currentContext;
    private int contextPos;

    ////////////////////////////////////////////////////////////////////
    // Internal classes.
    ////////////////////////////////////////////////////////////////////

    /**
     * Internal class for a single Namespace context.
     *
     * <p>This module caches and reuses Namespace contexts, so the number
     * allocated will be equal to the element depth of the document, not to the
     * total number of elements (i.e. 5-10 rather than tens of thousands).</p>
     */
    final static class Context {

        /**
         * Create the root-level Namespace context.
         */
        Context() {
            copyTables();
        }
        // PGB May 6 2002 added copy constructor
        Context(Context that, Context newParent) {
            if (that == null) {
                copyTables();
                return;
            }

            if (newParent != null && !that.tablesDirty) {
                prefixTable =
                    that.prefixTable == that.parent.prefixTable
                        ? newParent.prefixTable
                        : (HashMap) that.prefixTable.clone();

                uriTable =
                    that.uriTable == that.parent.uriTable
                        ? newParent.uriTable
                        : (HashMap) that.uriTable.clone();
                elementNameTable =
                    that.elementNameTable == that.parent.elementNameTable
                        ? newParent.elementNameTable
                        : (HashMap) that.elementNameTable.clone();
                attributeNameTable =
                    that.attributeNameTable == that.parent.attributeNameTable
                        ? newParent.attributeNameTable
                        : (HashMap) that.attributeNameTable.clone();
                defaultNS =
                    that.defaultNS == that.parent.defaultNS
                        ? newParent.defaultNS
                        : that.defaultNS;
            } else {
                prefixTable = (HashMap) that.prefixTable.clone();
                uriTable = (HashMap) that.uriTable.clone();
                elementNameTable = (HashMap) that.elementNameTable.clone();
                attributeNameTable = (HashMap) that.attributeNameTable.clone();
                defaultNS = that.defaultNS;
            }

            tablesDirty = that.tablesDirty;
            parent = newParent;
            declarations =
                that.declarations == null
                    ? null
                    : (ArrayList) that.declarations.clone();
        }

        /**
         * (Re)set the parent of this Namespace context.
         *
         * @param parent The parent Namespace context object.
         */
        void setParent(Context parent) {
            this.parent = parent;
            declarations = null;
            prefixTable = parent.prefixTable;
            uriTable = parent.uriTable;
            elementNameTable = parent.elementNameTable;
            attributeNameTable = parent.attributeNameTable;
            defaultNS = parent.defaultNS;
            tablesDirty = false;
        }

        /**
         * Declare a Namespace prefix for this context.
         *
         * @param prefix The prefix to declare.
         * @param uri The associated Namespace URI.
         * @see org.xml.sax.helpers.NamespaceSupport#declarePrefix
         */
        void declarePrefix(String prefix, String uri) {
            // Lazy processing...
            if (!tablesDirty) {
                copyTables();
            }
            if (declarations == null) {
                declarations = new ArrayList();
            }

            prefix = prefix.intern();
            uri = uri.intern();
            if ("".equals(prefix)) {
                if ("".equals(uri)) {
                    defaultNS = null;
                } else {
                    defaultNS = uri;
                }
            } else {
                prefixTable.put(prefix, uri);
                uriTable.put(uri, prefix); // may wipe out another prefix
            }
            declarations.add(prefix);
        }

        /**
         * Process a raw XML 1.0 name in this context.
         *
         * @param qName The raw XML 1.0 name.
         * @param isAttribute true if this is an attribute name.
         * @return An array of three strings containing the
         *         URI part (or empty string), the local part,
         *         and the raw name, all internalized, or null
         *         if there is an undeclared prefix.
         * @see org.xml.sax.helpers.NamespaceSupport#processName
         */
        String[] processName(String qName, boolean isAttribute) {
            String name[];
            Map table;

            // Select the appropriate table.
            if (isAttribute) {
                table = elementNameTable;
            } else {
                table = attributeNameTable;
            }

            // Start by looking in the cache, and
            // return immediately if the name
            // is already known in this content
            name = (String[]) table.get(qName);
            if (name != null) {
                return name;
            }

            // We haven't seen this name in this
            // context before.
            name = new String[3];
            int index = qName.indexOf(':');

            // No prefix.
            if (index == -1) {
                if (isAttribute || defaultNS == null) {
                    name[0] = "";
                } else {
                    name[0] = defaultNS;
                }
                name[1] = qName.intern();
                name[2] = name[1];
            }

            // Prefix
            else {
                String prefix = qName.substring(0, index);
                String local = qName.substring(index + 1);
                String uri;
                if ("".equals(prefix)) {
                    uri = defaultNS;
                } else {
                    uri = (String) prefixTable.get(prefix);
                }
                if (uri == null) {
                    return null;
                }
                name[0] = uri;
                name[1] = local.intern();
                name[2] = qName.intern();
            }

            // Save in the cache for future use.
            table.put(name[2], name);
            tablesDirty = true;
            return name;
        }

        /**
         * Look up the URI associated with a prefix in this context.
         *
         * @param prefix The prefix to look up.
         * @return The associated Namespace URI, or null if none is
         *         declared.
         * @see org.xml.sax.helpers.NamespaceSupport#getURI
         */
        String getURI(String prefix) {
            if ("".equals(prefix)) {
                return defaultNS;
            } else if (prefixTable == null) {
                return null;
            } else {
                return (String) prefixTable.get(prefix);
            }
        }

        /**
         * Look up one of the prefixes associated with a URI in this context.
         *
         * <p>Since many prefixes may be mapped to the same URI,
         * the return value may be unreliable.</p>
         *
         * @param uri The URI to look up.
         * @return The associated prefix, or null if none is declared.
         * @see org.xml.sax.helpers.NamespaceSupport#getPrefix
         */
        String getPrefix(String uri) {
            if (uriTable == null) {
                return null;
            } else {
                return (String) uriTable.get(uri);
            }
        }

        /**
         * Return an enumeration of prefixes declared in this context.
         *
         * @return An enumeration of prefixes (possibly empty).
         * @see org.xml.sax.helpers.NamespaceSupport#getDeclaredPrefixes
         */
        Iterable<String> getDeclaredPrefixes() {
            if (declarations == null) {
                return EMPTY_ENUMERATION;
            } else {
                return declarations;
            }
        }

        /**
         * Return an enumeration of all prefixes currently in force.
         *
         * <p>The default prefix, if in force, is <em>not</em>
         * returned, and will have to be checked for separately.</p>
         *
         * @return An enumeration of prefixes (never empty).
         * @see org.xml.sax.helpers.NamespaceSupport#getPrefixes
         */
        Iterable<String> getPrefixes() {
            if (prefixTable == null) {
                return EMPTY_ENUMERATION;
            } else {
                return prefixTable.keySet();
            }
        }

        ////////////////////////////////////////////////////////////////
        // Internal methods.
        ////////////////////////////////////////////////////////////////

        /**
         * Copy on write for the internal tables in this context.
         *
         * <p>This class is optimized for the normal case where most
         * elements do not contain Namespace declarations.</p>
         */
        private void copyTables() {
            if (prefixTable != null) {
                prefixTable = (HashMap) prefixTable.clone();
            } else {
                prefixTable = new HashMap();
            }
            if (uriTable != null) {
                uriTable = (HashMap) uriTable.clone();
            } else {
                uriTable = new HashMap();
            }
            elementNameTable = new HashMap();
            attributeNameTable = new HashMap();
            tablesDirty = true;
        }

        ////////////////////////////////////////////////////////////////
        // Protected state.
        ////////////////////////////////////////////////////////////////

        HashMap prefixTable;
        HashMap uriTable;
        // PBG May 6 2002 changed these two from Map to HashMap
        HashMap elementNameTable;
        HashMap attributeNameTable;
        String defaultNS = null;

        ////////////////////////////////////////////////////////////////
        // Internal state.
        ////////////////////////////////////////////////////////////////

        // PBG May 6 2002 changed this from List to ArrayList
        private ArrayList declarations = null;
        private boolean tablesDirty = false;
        private Context parent = null;
    }
}
