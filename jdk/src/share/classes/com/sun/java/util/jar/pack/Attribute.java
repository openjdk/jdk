/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.util.jar.pack;

import java.io.*;
import java.util.*;
import com.sun.java.util.jar.pack.ConstantPool.*;

/**
 * Represents an attribute in a class-file.
 * Takes care to remember where constant pool indexes occur.
 * Implements the "little language" of Pack200 for describing
 * attribute layouts.
 * @author John Rose
 */
class Attribute implements Comparable, Constants {
    // Attribute instance fields.

    Layout def;     // the name and format of this attr
    byte[] bytes;   // the actual bytes
    Object fixups;  // reference relocations, if any are required

    public String name() { return def.name(); }
    public Layout layout() { return def; }
    public byte[] bytes() { return bytes; }
    public int size() { return bytes.length; }
    public Entry getNameRef() { return def.getNameRef(); }

    private Attribute(Attribute old) {
        this.def = old.def;
        this.bytes = old.bytes;
        this.fixups = old.fixups;
    }

    public Attribute(Layout def, byte[] bytes, Object fixups) {
        this.def = def;
        this.bytes = bytes;
        this.fixups = fixups;
        Fixups.setBytes(fixups, bytes);
    }
    public Attribute(Layout def, byte[] bytes) {
        this(def, bytes, null);
    }

    public Attribute addContent(byte[] bytes, Object fixups) {
        assert(isCanonical());
        if (bytes.length == 0 && fixups == null)
            return this;
        Attribute res = new Attribute(this);
        res.bytes = bytes;
        res.fixups = fixups;
        Fixups.setBytes(fixups, bytes);
        return res;
    }
    public Attribute addContent(byte[] bytes) {
        return addContent(bytes, null);
    }

    public void finishRefs(Index ix) {
        if (fixups != null) {
            Fixups.finishRefs(fixups, bytes, ix);
            fixups = null;
        }
    }

    public boolean isCanonical() {
        return this == def.canon;
    }

    public int compareTo(Object o) {
        Attribute that = (Attribute) o;
        return this.def.compareTo(that.def);
    }

    private static final byte[] noBytes = {};
    private static final Map<List<Attribute>, List<Attribute>> canonLists = new HashMap<>();
    private static final Map<Layout, Attribute> attributes = new HashMap<>();
    private static final Map<Layout, Attribute> standardDefs = new HashMap<>();

    // Canonicalized lists of trivial attrs (Deprecated, etc.)
    // are used by trimToSize, in order to reduce footprint
    // of some common cases.  (Note that Code attributes are
    // always zero size.)
    public static List getCanonList(List<Attribute> al) {
        synchronized (canonLists) {
            List<Attribute> cl = canonLists.get(al);
            if (cl == null) {
                cl = new ArrayList<>(al.size());
                cl.addAll(al);
                cl = Collections.unmodifiableList(cl);
                canonLists.put(al, cl);
            }
            return cl;
        }
    }

    // Find the canonical empty attribute with the given ctype, name, layout.
    public static Attribute find(int ctype, String name, String layout) {
        Layout key = Layout.makeKey(ctype, name, layout);
        synchronized (attributes) {
            Attribute a = attributes.get(key);
            if (a == null) {
                a = new Layout(ctype, name, layout).canonicalInstance();
                attributes.put(key, a);
            }
            return a;
        }
    }

    public static Layout keyForLookup(int ctype, String name) {
        return Layout.makeKey(ctype, name);
    }

    // Find canonical empty attribute with given ctype and name,
    // and with the standard layout.
    public static Attribute lookup(Map<Layout, Attribute> defs, int ctype,
            String name) {
        if (defs == null) {
            defs = standardDefs;
        }
        return defs.get(Layout.makeKey(ctype, name));
    }

    public static Attribute define(Map<Layout, Attribute> defs, int ctype,
            String name, String layout) {
        Attribute a = find(ctype, name, layout);
        defs.put(Layout.makeKey(ctype, name), a);
        return a;
    }

    static {
        Map<Layout, Attribute> sd = standardDefs;
        define(sd, ATTR_CONTEXT_CLASS, "Signature", "RSH");
        define(sd, ATTR_CONTEXT_CLASS, "Synthetic", "");
        define(sd, ATTR_CONTEXT_CLASS, "Deprecated", "");
        define(sd, ATTR_CONTEXT_CLASS, "SourceFile", "RUH");
        define(sd, ATTR_CONTEXT_CLASS, "EnclosingMethod", "RCHRDNH");
        define(sd, ATTR_CONTEXT_CLASS, "InnerClasses", "NH[RCHRCNHRUNHFH]");

        define(sd, ATTR_CONTEXT_FIELD, "Signature", "RSH");
        define(sd, ATTR_CONTEXT_FIELD, "Synthetic", "");
        define(sd, ATTR_CONTEXT_FIELD, "Deprecated", "");
        define(sd, ATTR_CONTEXT_FIELD, "ConstantValue", "KQH");

        define(sd, ATTR_CONTEXT_METHOD, "Signature", "RSH");
        define(sd, ATTR_CONTEXT_METHOD, "Synthetic", "");
        define(sd, ATTR_CONTEXT_METHOD, "Deprecated", "");
        define(sd, ATTR_CONTEXT_METHOD, "Exceptions", "NH[RCH]");
        //define(sd, ATTR_CONTEXT_METHOD, "Code", "HHNI[B]NH[PHPOHPOHRCNH]NH[RUHNI[B]]");

        define(sd, ATTR_CONTEXT_CODE, "StackMapTable",
               ("[NH[(1)]]" +
                "[TB" +
                "(64-127)[(2)]" +
                "(247)[(1)(2)]" +
                "(248-251)[(1)]" +
                "(252)[(1)(2)]" +
                "(253)[(1)(2)(2)]" +
                "(254)[(1)(2)(2)(2)]" +
                "(255)[(1)NH[(2)]NH[(2)]]" +
                "()[]" +
                "]" +
                "[H]" +
                "[TB(7)[RCH](8)[PH]()[]]"));

        define(sd, ATTR_CONTEXT_CODE, "LineNumberTable", "NH[PHH]");
        define(sd, ATTR_CONTEXT_CODE, "LocalVariableTable", "NH[PHOHRUHRSHH]");
        define(sd, ATTR_CONTEXT_CODE, "LocalVariableTypeTable", "NH[PHOHRUHRSHH]");
        //define(sd, ATTR_CONTEXT_CODE, "CharacterRangeTable", "NH[PHPOHIIH]");
        //define(sd, ATTR_CONTEXT_CODE, "CoverageTable", "NH[PHHII]");

        // Note:  Code and InnerClasses are special-cased elsewhere.
        // Their layout specs. are given here for completeness.
        // The Code spec is incomplete, in that it does not distinguish
        // bytecode bytes or locate CP references.
    }

    // Metadata.
    //
    // We define metadata using similar layouts
    // for all five kinds of metadata attributes.
    //
    // Regular annotations are a counted list of [RSHNH[RUH(1)]][...]
    //   pack.method.attribute.RuntimeVisibleAnnotations=[NH[(1)]][RSHNH[RUH(1)]][TB...]
    //
    // Parameter annotations are a counted list of regular annotations.
    //   pack.method.attribute.RuntimeVisibleParameterAnnotations=[NH[(1)]][NH[(1)]][RSHNH[RUH(1)]][TB...]
    //
    // RuntimeInvisible annotations are defined similarly...
    // Non-method annotations are defined similarly...
    //
    // Annotation are a simple tagged value [TB...]
    //   pack.attribute.method.AnnotationDefault=[TB...]
    //
    static {
        String mdLayouts[] = {
            Attribute.normalizeLayoutString
            (""
             +"\n  # parameter_annotations :="
             +"\n  [ NB[(1)] ]     # forward call to annotations"
             ),
            Attribute.normalizeLayoutString
            (""
             +"\n  # annotations :="
             +"\n  [ NH[(1)] ]     # forward call to annotation"
             +"\n  "
             +"\n  # annotation :="
             +"\n  [RSH"
             +"\n    NH[RUH (1)]   # forward call to value"
             +"\n    ]"
             ),
            Attribute.normalizeLayoutString
            (""
             +"\n  # value :="
             +"\n  [TB # Callable 2 encodes one tagged value."
             +"\n    (\\B,\\C,\\I,\\S,\\Z)[KIH]"
             +"\n    (\\D)[KDH]"
             +"\n    (\\F)[KFH]"
             +"\n    (\\J)[KJH]"
             +"\n    (\\c)[RSH]"
             +"\n    (\\e)[RSH RUH]"
             +"\n    (\\s)[RUH]"
             +"\n    (\\[)[NH[(0)]] # backward self-call to value"
             +"\n    (\\@)[RSH NH[RUH (0)]] # backward self-call to value"
             +"\n    ()[] ]"
             )
        };
        Map<Layout, Attribute> sd = standardDefs;
        String defaultLayout     = mdLayouts[2];
        String annotationsLayout = mdLayouts[1] + mdLayouts[2];
        String paramsLayout      = mdLayouts[0] + annotationsLayout;
        for (int ctype = 0; ctype < ATTR_CONTEXT_LIMIT; ctype++) {
            if (ctype == ATTR_CONTEXT_CODE)  continue;
            define(sd, ctype,
                   "RuntimeVisibleAnnotations",   annotationsLayout);
            define(sd, ctype,
                   "RuntimeInvisibleAnnotations", annotationsLayout);
            if (ctype == ATTR_CONTEXT_METHOD) {
                define(sd, ctype,
                       "RuntimeVisibleParameterAnnotations",   paramsLayout);
                define(sd, ctype,
                       "RuntimeInvisibleParameterAnnotations", paramsLayout);
                define(sd, ctype,
                       "AnnotationDefault", defaultLayout);
            }
        }
    }

    public static String contextName(int ctype) {
        switch (ctype) {
        case ATTR_CONTEXT_CLASS: return "class";
        case ATTR_CONTEXT_FIELD: return "field";
        case ATTR_CONTEXT_METHOD: return "method";
        case ATTR_CONTEXT_CODE: return "code";
        }
        return null;
    }

    /** Base class for any attributed object (Class, Field, Method, Code).
     *  Flags are included because they are used to help transmit the
     *  presence of attributes.  That is, flags are a mix of modifier
     *  bits and attribute indicators.
     */
    public static abstract
    class Holder {

        // We need this abstract method to interpret embedded CP refs.
        protected abstract Entry[] getCPMap();

        protected int flags;             // defined here for convenience
        protected List<Attribute> attributes;

        public int attributeSize() {
            return (attributes == null) ? 0 : attributes.size();
        }

        public void trimToSize() {
            if (attributes == null) {
                return;
            }
            if (attributes.isEmpty()) {
                attributes = null;
                return;
            }
            if (attributes instanceof ArrayList) {
                ArrayList<Attribute> al = (ArrayList<Attribute>)attributes;
                al.trimToSize();
                boolean allCanon = true;
                for (Attribute a : al) {
                    if (!a.isCanonical()) {
                        allCanon = false;
                    }
                    if (a.fixups != null) {
                        assert(!a.isCanonical());
                        a.fixups = Fixups.trimToSize(a.fixups);
                    }
                }
                if (allCanon) {
                    // Replace private writable attribute list
                    // with only trivial entries by public unique
                    // immutable attribute list with the same entries.
                    attributes = getCanonList(al);
                }
            }
        }

        public void addAttribute(Attribute a) {
            if (attributes == null)
                attributes = new ArrayList<>(3);
            else if (!(attributes instanceof ArrayList))
                attributes = new ArrayList<>(attributes);  // unfreeze it
            attributes.add(a);
        }

        public Attribute removeAttribute(Attribute a) {
            if (attributes == null)       return null;
            if (!attributes.contains(a))  return null;
            if (!(attributes instanceof ArrayList))
                attributes = new ArrayList<>(attributes);  // unfreeze it
            attributes.remove(a);
            return a;
        }

        public Attribute getAttribute(int n) {
            return attributes.get(n);
        }

        protected void visitRefs(int mode, Collection<Entry> refs) {
            if (attributes == null)  return;
            for (Attribute a : attributes) {
                a.visitRefs(this, mode, refs);
            }
        }

        static final List<Attribute> noAttributes = Arrays.asList(new Attribute[0]);

        public List<Attribute> getAttributes() {
            if (attributes == null)
                return noAttributes;
            return attributes;
        }

        public void setAttributes(List<Attribute> attrList) {
            if (attrList.isEmpty())
                attributes = null;
            else
                attributes = attrList;
        }

        public Attribute getAttribute(String attrName) {
            if (attributes == null)  return null;
            for (Attribute a : attributes) {
                if (a.name().equals(attrName))
                    return a;
            }
            return null;
        }

        public Attribute getAttribute(Layout attrDef) {
            if (attributes == null)  return null;
            for (Attribute a : attributes) {
                if (a.layout() == attrDef)
                    return a;
            }
            return null;
        }

        public Attribute removeAttribute(String attrName) {
            return removeAttribute(getAttribute(attrName));
        }

        public Attribute removeAttribute(Layout attrDef) {
            return removeAttribute(getAttribute(attrDef));
        }

        public void strip(String attrName) {
            removeAttribute(getAttribute(attrName));
        }
    }

    // Lightweight interface to hide details of band structure.
    // Also used for testing.
    public static abstract
    class ValueStream {
        public int getInt(int bandIndex) { throw undef(); }
        public void putInt(int bandIndex, int value) { throw undef(); }
        public Entry getRef(int bandIndex) { throw undef(); }
        public void putRef(int bandIndex, Entry ref) { throw undef(); }
        // Note:  decodeBCI goes w/ getInt/Ref; encodeBCI goes w/ putInt/Ref
        public int decodeBCI(int bciCode) { throw undef(); }
        public int encodeBCI(int bci) { throw undef(); }
        public void noteBackCall(int whichCallable) { /* ignore by default */ }
        private RuntimeException undef() {
            return new UnsupportedOperationException("ValueStream method");
        }
    }

    // Element kinds:
    static final byte EK_INT  = 1;     // B H I SH etc.
    static final byte EK_BCI  = 2;     // PH POH etc.
    static final byte EK_BCO  = 3;     // OH etc.
    static final byte EK_FLAG = 4;     // FH etc.
    static final byte EK_REPL = 5;     // NH[...] etc.
    static final byte EK_REF  = 6;     // RUH, RUNH, KQH, etc.
    static final byte EK_UN   = 7;     // TB(...)[...] etc.
    static final byte EK_CASE = 8;     // (...)[...] etc.
    static final byte EK_CALL = 9;     // (0), (1), etc.
    static final byte EK_CBLE = 10;    // [...][...] etc.
    static final byte EF_SIGN  = 1<<0;   // INT is signed
    static final byte EF_DELTA = 1<<1;   // BCI/BCI value is diff'ed w/ previous
    static final byte EF_NULL  = 1<<2;   // null REF is expected/allowed
    static final byte EF_BACK  = 1<<3;   // call, callable, case is backward
    static final int NO_BAND_INDEX = -1;

    /** A "class" of attributes, characterized by a context-type, name
     *  and format.  The formats are specified in a "little language".
     */
    public static
    class Layout implements Comparable {
        int ctype;       // attribute context type, e.g., ATTR_CONTEXT_CODE
        String name;     // name of attribute
        boolean hasRefs; // this kind of attr contains CP refs?
        String layout;   // layout specification
        int bandCount;   // total number of elems
        Element[] elems; // tokenization of layout
        Attribute canon; // canonical instance of this layout

        public int ctype() { return ctype; }
        public String name() { return name; }
        public String layout() { return layout; }
        public Attribute canonicalInstance() { return canon; }

        public Entry getNameRef() {
            return ConstantPool.getUtf8Entry(name());
        }

        public boolean isEmpty() { return layout == ""; }

        public Layout(int ctype, String name, String layout) {
            this.ctype = ctype;
            this.name = name.intern();
            this.layout = layout.intern();
            assert(ctype < ATTR_CONTEXT_LIMIT);
            boolean hasCallables = layout.startsWith("[");
            try {
                if (!hasCallables) {
                    this.elems = tokenizeLayout(this, -1, layout);
                } else {
                    String[] bodies = splitBodies(layout);
                    // Make the callables now, so they can be linked immediately.
                    Element[] elems = new Element[bodies.length];
                    this.elems = elems;
                    for (int i = 0; i < elems.length; i++) {
                        Element ce = this.new Element();
                        ce.kind = EK_CBLE;
                        ce.removeBand();
                        ce.bandIndex = NO_BAND_INDEX;
                        ce.layout = bodies[i];
                        elems[i] = ce;
                    }
                    // Next fill them in.
                    for (int i = 0; i < elems.length; i++) {
                        Element ce = elems[i];
                        ce.body = tokenizeLayout(this, i, bodies[i]);
                    }
                    //System.out.println(Arrays.asList(elems));
                }
            } catch (StringIndexOutOfBoundsException ee) {
                // simplest way to catch syntax errors...
                throw new RuntimeException("Bad attribute layout: "+layout, ee);
            }
            // Some uses do not make a fresh one for each occurrence.
            // For example, if layout == "", we only need one attr to share.
            canon = new Attribute(this, noBytes);
        }
        private Layout() {}
        static Layout makeKey(int ctype, String name, String layout) {
            Layout def = new Layout();
            def.ctype = ctype;
            def.name = name.intern();
            def.layout = layout.intern();
            assert(ctype < ATTR_CONTEXT_LIMIT);
            return def;
        }
        static Layout makeKey(int ctype, String name) {
            return makeKey(ctype, name, "");
        }

        public Attribute addContent(byte[] bytes, Object fixups) {
            return canon.addContent(bytes, fixups);
        }
        public Attribute addContent(byte[] bytes) {
            return canon.addContent(bytes, null);
        }

        public boolean equals(Object x) {
            return x instanceof Layout && equals((Layout)x);
        }
        public boolean equals(Layout that) {
            return this.name == that.name
                && this.layout == that.layout
                && this.ctype == that.ctype;
        }
        public int hashCode() {
            return (((17 + name.hashCode())
                    * 37 + layout.hashCode())
                    * 37 + ctype);
        }
        public int compareTo(Object o) {
            Layout that = (Layout) o;
            int r;
            r = this.name.compareTo(that.name);
            if (r != 0)  return r;
            r = this.layout.compareTo(that.layout);
            if (r != 0)  return r;
            return this.ctype - that.ctype;
        }
        public String toString() {
            String str = contextName(ctype)+"."+name+"["+layout+"]";
            // If -ea, print out more informative strings!
            assert((str = stringForDebug()) != null);
            return str;
        }
        private String stringForDebug() {
            return contextName(ctype)+"."+name+Arrays.asList(elems);
        }

        public
        class Element {
            String layout;   // spelling in the little language
            byte flags;      // EF_SIGN, etc.
            byte kind;       // EK_UINT, etc.
            byte len;        // scalar length of element
            byte refKind;    // CONSTANT_String, etc.
            int bandIndex;   // which band does this element govern?
            int value;       // extra parameter
            Element[] body;  // extra data (for replications, unions, calls)

            boolean flagTest(byte mask) { return (flags & mask) != 0; }

            Element() {
                bandIndex = bandCount++;
            }

            void removeBand() {
                --bandCount;
                assert(bandIndex == bandCount);
                bandIndex = NO_BAND_INDEX;
            }

            public boolean hasBand() {
                return bandIndex >= 0;
            }
            public String toString() {
                String str = layout;
                // If -ea, print out more informative strings!
                assert((str = stringForDebug()) != null);
                return str;
            }
            private String stringForDebug() {
                Element[] body = this.body;
                switch (kind) {
                case EK_CALL:
                    body = null;
                    break;
                case EK_CASE:
                    if (flagTest(EF_BACK))
                        body = null;
                    break;
                }
                return layout
                    + (!hasBand()?"":"#"+bandIndex)
                    + "<"+ (flags==0?"":""+flags)+kind+len
                    + (refKind==0?"":""+refKind) + ">"
                    + (value==0?"":"("+value+")")
                    + (body==null?"": ""+Arrays.asList(body));
            }
        }

        public boolean hasCallables() {
            return (elems.length > 0 && elems[0].kind == EK_CBLE);
        }
        static private final Element[] noElems = {};
        public Element[] getCallables() {
            if (hasCallables())
                return elems;
            else
                return noElems;  // no callables at all
        }
        public Element[] getEntryPoint() {
            if (hasCallables())
                return elems[0].body;  // body of first callable
            else
                return elems;  // no callables; whole body
        }

        /** Return a sequence of tokens from the given attribute bytes.
         *  Sequence elements will be 1-1 correspondent with my layout tokens.
         */
        public void parse(Holder holder,
                          byte[] bytes, int pos, int len, ValueStream out) {
            int end = parseUsing(getEntryPoint(),
                                 holder, bytes, pos, len, out);
            if (end != pos + len)
                throw new InternalError("layout parsed "+(end-pos)+" out of "+len+" bytes");
        }
        /** Given a sequence of tokens, return the attribute bytes.
         *  Sequence elements must be 1-1 correspondent with my layout tokens.
         *  The returned object is a cookie for Fixups.finishRefs, which
         *  must be used to harden any references into integer indexes.
         */
        public Object unparse(ValueStream in, ByteArrayOutputStream out) {
            Object[] fixups = { null };
            unparseUsing(getEntryPoint(), fixups, in, out);
            return fixups[0]; // return ref-bearing cookie, if any
        }

        public String layoutForPackageMajver(int majver) {
            if (majver <= JAVA5_PACKAGE_MAJOR_VERSION) {
                // Disallow layout syntax in the oldest protocol version.
                return expandCaseDashNotation(layout);
            }
            return layout;
        }
    }

    public static
    class FormatException extends IOException {
        private int ctype;
        private String name;
        String layout;
        public FormatException(String message,
                               int ctype, String name, String layout) {
            super(ATTR_CONTEXT_NAME[ctype]+"."+name
                  +(message == null? "": (": "+message)));
            this.ctype = ctype;
            this.name = name;
            this.layout = layout;
        }
        public FormatException(String message,
                               int ctype, String name) {
            this(message, ctype, name, null);
        }
    }

    void visitRefs(Holder holder, int mode, final Collection refs) {
        if (mode == VRM_CLASSIC) {
            refs.add(getNameRef());
        }
        // else the name is owned by the layout, and is processed elsewhere
        if (bytes.length == 0)  return;  // quick exit
        if (!def.hasRefs)       return;  // quick exit
        if (fixups != null) {
            Fixups.visitRefs(fixups, refs);
            return;
        }
        // References (to a local cpMap) are embedded in the bytes.
        def.parse(holder, bytes, 0, bytes.length,
            new ValueStream() {
                public void putInt(int bandIndex, int value) {
                }
                public void putRef(int bandIndex, Entry ref) {
                    refs.add(ref);
                }
                public int encodeBCI(int bci) {
                    return bci;
                }
            });
    }

    public void parse(Holder holder, byte[] bytes, int pos, int len, ValueStream out) {
        def.parse(holder, bytes, pos, len, out);
    }
    public Object unparse(ValueStream in, ByteArrayOutputStream out) {
        return def.unparse(in, out);
    }

    public String toString() {
        return def
            +"{"+(bytes == null ? -1 : size())+"}"
            +(fixups == null? "": fixups.toString());
    }

    /** Remove any informal "pretty printing" from the layout string.
     *  Removes blanks and control chars.
     *  Removes '#' comments (to end of line).
     *  Replaces '\c' by the decimal code of the character c.
     *  Replaces '0xNNN' by the decimal code of the hex number NNN.
     */
    static public
    String normalizeLayoutString(String layout) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0, len = layout.length(); i < len; ) {
            char ch = layout.charAt(i++);
            if (ch <= ' ') {
                // Skip whitespace and control chars
                continue;
            } else if (ch == '#') {
                // Skip to end of line.
                int end1 = layout.indexOf('\n', i);
                int end2 = layout.indexOf('\r', i);
                if (end1 < 0)  end1 = len;
                if (end2 < 0)  end2 = len;
                i = Math.min(end1, end2);
            } else if (ch == '\\') {
                // Map a character reference to its decimal code.
                buf.append((int) layout.charAt(i++));
            } else if (ch == '0' && layout.startsWith("0x", i-1)) {
                // Map a hex numeral to its decimal code.
                int start = i-1;
                int end = start+2;
                while (end < len) {
                    int dig = layout.charAt(end);
                    if ((dig >= '0' && dig <= '9') ||
                        (dig >= 'a' && dig <= 'f'))
                        ++end;
                    else
                        break;
                }
                if (end > start) {
                    String num = layout.substring(start, end);
                    buf.append(Integer.decode(num));
                    i = end;
                } else {
                    buf.append(ch);
                }
            } else {
                buf.append(ch);
            }
        }
        String result = buf.toString();
        if (false && !result.equals(layout)) {
            Utils.log.info("Normalizing layout string");
            Utils.log.info("    From: "+layout);
            Utils.log.info("    To:   "+result);
        }
        return result;
    }

    /// Subroutines for parsing and unparsing:

    /** Parse the attribute layout language.
<pre>
  attribute_layout:
        ( layout_element )* | ( callable )+
  layout_element:
        ( integral | replication | union | call | reference )

  callable:
        '[' body ']'
  body:
        ( layout_element )+

  integral:
        ( unsigned_int | signed_int | bc_index | bc_offset | flag )
  unsigned_int:
        uint_type
  signed_int:
        'S' uint_type
  any_int:
        ( unsigned_int | signed_int )
  bc_index:
        ( 'P' uint_type | 'PO' uint_type )
  bc_offset:
        'O' any_int
  flag:
        'F' uint_type
  uint_type:
        ( 'B' | 'H' | 'I' | 'V' )

  replication:
        'N' uint_type '[' body ']'

  union:
        'T' any_int (union_case)* '(' ')' '[' (body)? ']'
  union_case:
        '(' union_case_tag (',' union_case_tag)* ')' '[' (body)? ']'
  union_case_tag:
        ( numeral | numeral '-' numeral )
  call:
        '(' numeral ')'

  reference:
        reference_type ( 'N' )? uint_type
  reference_type:
        ( constant_ref | schema_ref | utf8_ref | untyped_ref )
  constant_ref:
        ( 'KI' | 'KJ' | 'KF' | 'KD' | 'KS' | 'KQ' )
  schema_ref:
        ( 'RC' | 'RS' | 'RD' | 'RF' | 'RM' | 'RI' )
  utf8_ref:
        'RU'
  untyped_ref:
        'RQ'

  numeral:
        '(' ('-')? (digit)+ ')'
  digit:
        ( '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' )
 </pre>
    */
    static //private
    Layout.Element[] tokenizeLayout(Layout self, int curCble, String layout) {
        ArrayList<Layout.Element> col = new ArrayList<>(layout.length());
        tokenizeLayout(self, curCble, layout, col);
        Layout.Element[] res = new Layout.Element[col.size()];
        col.toArray(res);
        return res;
    }
    static //private
    void tokenizeLayout(Layout self, int curCble, String layout, ArrayList<Layout.Element> col) {
        boolean prevBCI = false;
        for (int len = layout.length(), i = 0; i < len; ) {
            int start = i;
            int body;
            Layout.Element e = self.new Element();
            byte kind;
            //System.out.println("at "+i+": ..."+layout.substring(i));
            // strip a prefix
            switch (layout.charAt(i++)) {
            /// layout_element: integral
            case 'B': case 'H': case 'I': case 'V': // unsigned_int
                kind = EK_INT;
                --i; // reparse
                i = tokenizeUInt(e, layout, i);
                break;
            case 'S': // signed_int
                kind = EK_INT;
                --i; // reparse
                i = tokenizeSInt(e, layout, i);
                break;
            case 'P': // bc_index
                kind = EK_BCI;
                if (layout.charAt(i++) == 'O') {
                    // bc_index: 'PO' tokenizeUInt
                    e.flags |= EF_DELTA;
                    // must follow P or PO:
                    if (!prevBCI)
                        { i = -i; continue; } // fail
                    i++; // move forward
                }
                --i; // reparse
                i = tokenizeUInt(e, layout, i);
                break;
            case 'O': // bc_offset
                kind = EK_BCO;
                e.flags |= EF_DELTA;
                // must follow P or PO:
                if (!prevBCI)
                    { i = -i; continue; } // fail
                i = tokenizeSInt(e, layout, i);
                break;
            case 'F': // flag
                kind = EK_FLAG;
                i = tokenizeUInt(e, layout, i);
                break;
            case 'N': // replication: 'N' uint '[' elem ... ']'
                kind = EK_REPL;
                i = tokenizeUInt(e, layout, i);
                if (layout.charAt(i++) != '[')
                    { i = -i; continue; } // fail
                i = skipBody(layout, body = i);
                e.body = tokenizeLayout(self, curCble,
                                        layout.substring(body, i++));
                break;
            case 'T': // union: 'T' any_int union_case* '(' ')' '[' body ']'
                kind = EK_UN;
                i = tokenizeSInt(e, layout, i);
                ArrayList<Layout.Element> cases = new ArrayList<>();
                for (;;) {
                    // Keep parsing cases until we hit the default case.
                    if (layout.charAt(i++) != '(')
                        { i = -i; break; } // fail
                    int beg = i;
                    i = layout.indexOf(')', i);
                    String cstr = layout.substring(beg, i++);
                    int cstrlen = cstr.length();
                    if (layout.charAt(i++) != '[')
                        { i = -i; break; } // fail
                    // Check for duplication.
                    if (layout.charAt(i) == ']')
                        body = i;  // missing body, which is legal here
                    else
                        i = skipBody(layout, body = i);
                    Layout.Element[] cbody
                        = tokenizeLayout(self, curCble,
                                         layout.substring(body, i++));
                    if (cstrlen == 0) {
                        Layout.Element ce = self.new Element();
                        ce.body = cbody;
                        ce.kind = EK_CASE;
                        ce.removeBand();
                        cases.add(ce);
                        break;  // done with the whole union
                    } else {
                        // Parse a case string.
                        boolean firstCaseNum = true;
                        for (int cp = 0, endp;; cp = endp+1) {
                            // Look for multiple case tags:
                            endp = cstr.indexOf(',', cp);
                            if (endp < 0)  endp = cstrlen;
                            String cstr1 = cstr.substring(cp, endp);
                            if (cstr1.length() == 0)
                                cstr1 = "empty";  // will fail parse
                            int value0, value1;
                            // Check for a case range (new in 1.6).
                            int dash = findCaseDash(cstr1, 0);
                            if (dash >= 0) {
                                value0 = parseIntBefore(cstr1, dash);
                                value1 = parseIntAfter(cstr1, dash);
                                if (value0 >= value1)
                                    { i = -i; break; } // fail
                            } else {
                                value0 = value1 = Integer.parseInt(cstr1);
                            }
                            // Add a case for each value in value0..value1
                            for (;; value0++) {
                                Layout.Element ce = self.new Element();
                                ce.body = cbody;  // all cases share one body
                                ce.kind = EK_CASE;
                                ce.removeBand();
                                if (!firstCaseNum)
                                    // "backward case" repeats a body
                                    ce.flags |= EF_BACK;
                                firstCaseNum = false;
                                ce.value = value0;
                                cases.add(ce);
                                if (value0 == value1)  break;
                            }
                            if (endp == cstrlen) {
                                break;  // done with this case
                            }
                        }
                    }
                }
                e.body = new Layout.Element[cases.size()];
                cases.toArray(e.body);
                e.kind = kind;
                for (int j = 0; j < e.body.length-1; j++) {
                    Layout.Element ce = e.body[j];
                    if (matchCase(e, ce.value) != ce) {
                        // Duplicate tag.
                        { i = -i; break; } // fail
                    }
                }
                break;
            case '(': // call: '(' '-'? digit+ ')'
                kind = EK_CALL;
                e.removeBand();
                i = layout.indexOf(')', i);
                String cstr = layout.substring(start+1, i++);
                int offset = Integer.parseInt(cstr);
                int target = curCble + offset;
                if (!(offset+"").equals(cstr) ||
                    self.elems == null ||
                    target < 0 ||
                    target >= self.elems.length)
                    { i = -i; continue; } // fail
                Layout.Element ce = self.elems[target];
                assert(ce.kind == EK_CBLE);
                e.value = target;
                e.body = new Layout.Element[]{ ce };
                // Is it a (recursive) backward call?
                if (offset <= 0) {
                    // Yes.  Mark both caller and callee backward.
                    e.flags  |= EF_BACK;
                    ce.flags |= EF_BACK;
                }
                break;
            case 'K':  // reference_type: constant_ref
                kind = EK_REF;
                switch (layout.charAt(i++)) {
                case 'I': e.refKind = CONSTANT_Integer; break;
                case 'J': e.refKind = CONSTANT_Long; break;
                case 'F': e.refKind = CONSTANT_Float; break;
                case 'D': e.refKind = CONSTANT_Double; break;
                case 'S': e.refKind = CONSTANT_String; break;
                case 'Q': e.refKind = CONSTANT_Literal; break;
                default: { i = -i; continue; } // fail
                }
                break;
            case 'R': // schema_ref
                kind = EK_REF;
                switch (layout.charAt(i++)) {
                case 'C': e.refKind = CONSTANT_Class; break;
                case 'S': e.refKind = CONSTANT_Signature; break;
                case 'D': e.refKind = CONSTANT_NameandType; break;
                case 'F': e.refKind = CONSTANT_Fieldref; break;
                case 'M': e.refKind = CONSTANT_Methodref; break;
                case 'I': e.refKind = CONSTANT_InterfaceMethodref; break;

                case 'U': e.refKind = CONSTANT_Utf8; break; //utf8_ref
                case 'Q': e.refKind = CONSTANT_All; break; //untyped_ref

                default: { i = -i; continue; } // fail
                }
                break;
            default: { i = -i; continue; } // fail
            }

            // further parsing of refs
            if (kind == EK_REF) {
                // reference: reference_type -><- ( 'N' )? tokenizeUInt
                if (layout.charAt(i++) == 'N') {
                    e.flags |= EF_NULL;
                    i++; // move forward
                }
                --i; // reparse
                i = tokenizeUInt(e, layout, i);
                self.hasRefs = true;
            }

            prevBCI = (kind == EK_BCI);

            // store the new element
            e.kind = kind;
            e.layout = layout.substring(start, i);
            col.add(e);
        }
    }
    static //private
    String[] splitBodies(String layout) {
        ArrayList<String> bodies = new ArrayList<>();
        // Parse several independent layout bodies:  "[foo][bar]...[baz]"
        for (int i = 0; i < layout.length(); i++) {
            if (layout.charAt(i++) != '[')
                layout.charAt(-i);  // throw error
            int body;
            i = skipBody(layout, body = i);
            bodies.add(layout.substring(body, i));
        }
        String[] res = new String[bodies.size()];
        bodies.toArray(res);
        return res;
    }
    static private
    int skipBody(String layout, int i) {
        assert(layout.charAt(i-1) == '[');
        if (layout.charAt(i) == ']')
            // No empty bodies, please.
            return -i;
        // skip balanced [...[...]...]
        for (int depth = 1; depth > 0; ) {
            switch (layout.charAt(i++)) {
            case '[': depth++; break;
            case ']': depth--; break;
            }
        }
        --i;  // get before bracket
        assert(layout.charAt(i) == ']');
        return i;  // return closing bracket
    }
    static private
    int tokenizeUInt(Layout.Element e, String layout, int i) {
        switch (layout.charAt(i++)) {
        case 'V': e.len = 0; break;
        case 'B': e.len = 1; break;
        case 'H': e.len = 2; break;
        case 'I': e.len = 4; break;
        default: return -i;
        }
        return i;
    }
    static private
    int tokenizeSInt(Layout.Element e, String layout, int i) {
        if (layout.charAt(i) == 'S') {
            e.flags |= EF_SIGN;
            ++i;
        }
        return tokenizeUInt(e, layout, i);
    }

    static private
    boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** Find an occurrence of hyphen '-' between two numerals. */
    static //private
    int findCaseDash(String layout, int fromIndex) {
        if (fromIndex <= 0)  fromIndex = 1;  // minimum dash pos
        int lastDash = layout.length() - 2;  // maximum dash pos
        for (;;) {
            int dash = layout.indexOf('-', fromIndex);
            if (dash < 0 || dash > lastDash)  return -1;
            if (isDigit(layout.charAt(dash-1))) {
                char afterDash = layout.charAt(dash+1);
                if (afterDash == '-' && dash+2 < layout.length())
                    afterDash = layout.charAt(dash+2);
                if (isDigit(afterDash)) {
                    // matched /[0-9]--?[0-9]/; return position of dash
                    return dash;
                }
            }
            fromIndex = dash+1;
        }
    }
    static
    int parseIntBefore(String layout, int dash) {
        int end = dash;
        int beg = end;
        while (beg > 0 && isDigit(layout.charAt(beg-1))) {
            --beg;
        }
        if (beg == end)  return Integer.parseInt("empty");
        // skip backward over a sign
        if (beg >= 1 && layout.charAt(beg-1) == '-')  --beg;
        assert(beg == 0 || !isDigit(layout.charAt(beg-1)));
        return Integer.parseInt(layout.substring(beg, end));
    }
    static
    int parseIntAfter(String layout, int dash) {
        int beg = dash+1;
        int end = beg;
        int limit = layout.length();
        if (end < limit && layout.charAt(end) == '-')  ++end;
        while (end < limit && isDigit(layout.charAt(end))) {
            ++end;
        }
        if (beg == end)  return Integer.parseInt("empty");
        return Integer.parseInt(layout.substring(beg, end));
    }
    /** For compatibility with 1.5 pack, expand 1-5 into 1,2,3,4,5. */
    static
    String expandCaseDashNotation(String layout) {
        int dash = findCaseDash(layout, 0);
        if (dash < 0)  return layout;  // no dashes (the common case)
        StringBuffer result = new StringBuffer(layout.length() * 3);
        int sofar = 0;  // how far have we processed the layout?
        for (;;) {
            // for each dash, collect everything up to the dash
            result.append(layout.substring(sofar, dash));
            sofar = dash+1;  // skip the dash
            // then collect intermediate values
            int value0 = parseIntBefore(layout, dash);
            int value1 = parseIntAfter(layout, dash);
            assert(value0 < value1);
            result.append(",");  // close off value0 numeral
            for (int i = value0+1; i < value1; i++) {
                result.append(i);
                result.append(",");  // close off i numeral
            }
            dash = findCaseDash(layout, sofar);
            if (dash < 0)  break;
        }
        result.append(layout.substring(sofar));  // collect the rest
        return result.toString();
    }
    static {
        assert(expandCaseDashNotation("1-5").equals("1,2,3,4,5"));
        assert(expandCaseDashNotation("-2--1").equals("-2,-1"));
        assert(expandCaseDashNotation("-2-1").equals("-2,-1,0,1"));
        assert(expandCaseDashNotation("-1-0").equals("-1,0"));
    }

    // Parse attribute bytes, putting values into bands.  Returns new pos.
    // Used when reading a class file (local refs resolved with local cpMap).
    // Also used for ad hoc scanning.
    static
    int parseUsing(Layout.Element[] elems, Holder holder,
                   byte[] bytes, int pos, int len, ValueStream out) {
        int prevBCI = 0;
        int prevRBCI = 0;
        int end = pos + len;
        int[] buf = { 0 };  // for calls to parseInt, holds 2nd result
        for (int i = 0; i < elems.length; i++) {
            Layout.Element e = elems[i];
            int bandIndex = e.bandIndex;
            int value;
            int BCI, RBCI;
            switch (e.kind) {
            case EK_INT:
                pos = parseInt(e, bytes, pos, buf);
                value = buf[0];
                out.putInt(bandIndex, value);
                break;
            case EK_BCI:  // PH, POH
                pos = parseInt(e, bytes, pos, buf);
                BCI = buf[0];
                RBCI = out.encodeBCI(BCI);
                if (!e.flagTest(EF_DELTA)) {
                    // PH:  transmit R(bci), store bci
                    value = RBCI;
                } else {
                    // POH:  transmit D(R(bci)), store bci
                    value = RBCI - prevRBCI;
                }
                prevBCI = BCI;
                prevRBCI = RBCI;
                out.putInt(bandIndex, value);
                break;
            case EK_BCO:  // OH
                assert(e.flagTest(EF_DELTA));
                // OH:  transmit D(R(bci)), store D(bci)
                pos = parseInt(e, bytes, pos, buf);
                BCI = prevBCI + buf[0];
                RBCI = out.encodeBCI(BCI);
                value = RBCI - prevRBCI;
                prevBCI = BCI;
                prevRBCI = RBCI;
                out.putInt(bandIndex, value);
                break;
            case EK_FLAG:
                pos = parseInt(e, bytes, pos, buf);
                value = buf[0];
                out.putInt(bandIndex, value);
                break;
            case EK_REPL:
                pos = parseInt(e, bytes, pos, buf);
                value = buf[0];
                out.putInt(bandIndex, value);
                for (int j = 0; j < value; j++) {
                    pos = parseUsing(e.body, holder, bytes, pos, end-pos, out);
                }
                break;  // already transmitted the scalar value
            case EK_UN:
                pos = parseInt(e, bytes, pos, buf);
                value = buf[0];
                out.putInt(bandIndex, value);
                Layout.Element ce = matchCase(e, value);
                pos = parseUsing(ce.body, holder, bytes, pos, end-pos, out);

                break;  // already transmitted the scalar value
            case EK_CALL:
                // Adjust band offset if it is a backward call.
                assert(e.body.length == 1);
                assert(e.body[0].kind == EK_CBLE);
                if (e.flagTest(EF_BACK))
                    out.noteBackCall(e.value);
                pos = parseUsing(e.body[0].body, holder, bytes, pos, end-pos, out);
                break;  // no additional scalar value to transmit
            case EK_REF:
                pos = parseInt(e, bytes, pos, buf);
                int localRef = buf[0];
                Entry globalRef;
                if (localRef == 0) {
                    globalRef = null;  // N.B. global null reference is -1
                } else {
                    globalRef = holder.getCPMap()[localRef];
                    if (e.refKind == CONSTANT_Signature
                        && globalRef.getTag() == CONSTANT_Utf8) {
                        // Cf. ClassReader.readSignatureRef.
                        String typeName = globalRef.stringValue();
                        globalRef = ConstantPool.getSignatureEntry(typeName);
                    } else if (e.refKind == CONSTANT_Literal) {
                        assert(globalRef.getTag() >= CONSTANT_Integer);
                        assert(globalRef.getTag() <= CONSTANT_String);
                    } else if (e.refKind != CONSTANT_All) {
                        assert(e.refKind == globalRef.getTag());
                    }
                }
                out.putRef(bandIndex, globalRef);
                break;
            default: assert(false); continue;
            }
        }
        return pos;
    }

    static
    Layout.Element matchCase(Layout.Element e, int value) {
        assert(e.kind == EK_UN);
        int lastj = e.body.length-1;
        for (int j = 0; j < lastj; j++) {
            Layout.Element ce = e.body[j];
            assert(ce.kind == EK_CASE);
            if (value == ce.value)
                return ce;
        }
        return e.body[lastj];
    }

    static private
    int parseInt(Layout.Element e, byte[] bytes, int pos, int[] buf) {
        int value = 0;
        int loBits = e.len * 8;
        // Read in big-endian order:
        for (int bitPos = loBits; (bitPos -= 8) >= 0; ) {
            value += (bytes[pos++] & 0xFF) << bitPos;
        }
        if (loBits < 32 && e.flagTest(EF_SIGN)) {
            // sign-extend subword value
            int hiBits = 32 - loBits;
            value = (value << hiBits) >> hiBits;
        }
        buf[0] = value;
        return pos;
    }

    // Format attribute bytes, drawing values from bands.
    // Used when emptying attribute bands into a package model.
    // (At that point CP refs. are not yet assigned indexes.)
    static
    void unparseUsing(Layout.Element[] elems, Object[] fixups,
                      ValueStream in, ByteArrayOutputStream out) {
        int prevBCI = 0;
        int prevRBCI = 0;
        for (int i = 0; i < elems.length; i++) {
            Layout.Element e = elems[i];
            int bandIndex = e.bandIndex;
            int value;
            int BCI, RBCI;  // "RBCI" is R(BCI), BCI's coded representation
            switch (e.kind) {
            case EK_INT:
                value = in.getInt(bandIndex);
                unparseInt(e, value, out);
                break;
            case EK_BCI:  // PH, POH
                value = in.getInt(bandIndex);
                if (!e.flagTest(EF_DELTA)) {
                    // PH:  transmit R(bci), store bci
                    RBCI = value;
                } else {
                    // POH:  transmit D(R(bci)), store bci
                    RBCI = prevRBCI + value;
                }
                assert(prevBCI == in.decodeBCI(prevRBCI));
                BCI = in.decodeBCI(RBCI);
                unparseInt(e, BCI, out);
                prevBCI = BCI;
                prevRBCI = RBCI;
                break;
            case EK_BCO:  // OH
                value = in.getInt(bandIndex);
                assert(e.flagTest(EF_DELTA));
                // OH:  transmit D(R(bci)), store D(bci)
                assert(prevBCI == in.decodeBCI(prevRBCI));
                RBCI = prevRBCI + value;
                BCI = in.decodeBCI(RBCI);
                unparseInt(e, BCI - prevBCI, out);
                prevBCI = BCI;
                prevRBCI = RBCI;
                break;
            case EK_FLAG:
                value = in.getInt(bandIndex);
                unparseInt(e, value, out);
                break;
            case EK_REPL:
                value = in.getInt(bandIndex);
                unparseInt(e, value, out);
                for (int j = 0; j < value; j++) {
                    unparseUsing(e.body, fixups, in, out);
                }
                break;
            case EK_UN:
                value = in.getInt(bandIndex);
                unparseInt(e, value, out);
                Layout.Element ce = matchCase(e, value);
                unparseUsing(ce.body, fixups, in, out);
                break;
            case EK_CALL:
                assert(e.body.length == 1);
                assert(e.body[0].kind == EK_CBLE);
                unparseUsing(e.body[0].body, fixups, in, out);
                break;
            case EK_REF:
                Entry globalRef = in.getRef(bandIndex);
                int localRef;
                if (globalRef != null) {
                    // It's a one-element array, really an lvalue.
                    fixups[0] = Fixups.add(fixups[0], null, out.size(),
                                           Fixups.U2_FORMAT, globalRef);
                    localRef = 0; // placeholder for fixups
                } else {
                    localRef = 0; // fixed null value
                }
                unparseInt(e, localRef, out);
                break;
            default: assert(false); continue;
            }
        }
    }

    static private
    void unparseInt(Layout.Element e, int value, ByteArrayOutputStream out) {
        int loBits = e.len * 8;
        if (loBits == 0) {
            // It is not stored at all ('V' layout).
            return;
        }
        if (loBits < 32) {
            int hiBits = 32 - loBits;
            int codedValue;
            if (e.flagTest(EF_SIGN))
                codedValue = (value << hiBits) >> hiBits;
            else
                codedValue = (value << hiBits) >>> hiBits;
            if (codedValue != value)
                throw new InternalError("cannot code in "+e.len+" bytes: "+value);
        }
        // Write in big-endian order:
        for (int bitPos = loBits; (bitPos -= 8) >= 0; ) {
            out.write((byte)(value >>> bitPos));
        }
    }

/*
    /// Testing.
    public static void main(String av[]) {
        int maxVal = 12;
        int iters = 0;
        boolean verbose;
        int ap = 0;
        while (ap < av.length) {
            if (!av[ap].startsWith("-"))  break;
            if (av[ap].startsWith("-m"))
                maxVal = Integer.parseInt(av[ap].substring(2));
            else if (av[ap].startsWith("-i"))
                iters = Integer.parseInt(av[ap].substring(2));
            else
                throw new RuntimeException("Bad option: "+av[ap]);
            ap++;
        }
        verbose = (iters == 0);
        if (iters <= 0)  iters = 1;
        if (ap == av.length) {
            av = new String[] {
                "HH",         // ClassFile.version
                "RUH",        // SourceFile
                "RCHRDNH",    // EnclosingMethod
                "KQH",        // ConstantValue
                "NH[RCH]",    // Exceptions
                "NH[PHH]",    // LineNumberTable
                "NH[PHOHRUHRSHH]",      // LocalVariableTable
                "NH[PHPOHIIH]",         // CharacterRangeTable
                "NH[PHHII]",            // CoverageTable
                "NH[RCHRCNHRUNHFH]",    // InnerClasses
                "HHNI[B]NH[PHPOHPOHRCNH]NH[RUHNI[B]]", // Code
                "=AnnotationDefault",
                // Like metadata, but with a compact tag set:
                "[NH[(1)]]"
                +"[NH[(2)]]"
                +"[RSHNH[RUH(3)]]"
                +"[TB(0,1,3)[KIH](2)[KDH](5)[KFH](4)[KJH](7)[RSH](8)[RSHRUH](9)[RUH](10)[(2)](6)[NH[(3)]]()[]]",
                ""
            };
            ap = 0;
        }
        final int[][] counts = new int[2][3];  // int bci ref
        final Entry[] cpMap = new Entry[maxVal+1];
        for (int i = 0; i < cpMap.length; i++) {
            if (i == 0)  continue;  // 0 => null
            cpMap[i] = ConstantPool.getLiteralEntry(new Integer(i));
        }
        Class cls = new Package().new Class("");
        cls.cpMap = cpMap;
        class TestValueStream extends ValueStream {
            Random rand = new Random(0);
            ArrayList history = new ArrayList();
            int ckidx = 0;
            int maxVal;
            boolean verbose;
            void reset() { history.clear(); ckidx = 0; }
            public int getInt(int bandIndex) {
                counts[0][0]++;
                int value = rand.nextInt(maxVal+1);
                history.add(new Integer(bandIndex));
                history.add(new Integer(value));
                return value;
            }
            public void putInt(int bandIndex, int token) {
                counts[1][0]++;
                if (verbose)
                    System.out.print(" "+bandIndex+":"+token);
                // Make sure this put parallels a previous get:
                int check0 = ((Integer)history.get(ckidx+0)).intValue();
                int check1 = ((Integer)history.get(ckidx+1)).intValue();
                if (check0 != bandIndex || check1 != token) {
                    if (!verbose)
                        System.out.println(history.subList(0, ckidx));
                    System.out.println(" *** Should be "+check0+":"+check1);
                    throw new RuntimeException("Failed test!");
                }
                ckidx += 2;
            }
            public Entry getRef(int bandIndex) {
                counts[0][2]++;
                int value = getInt(bandIndex);
                if (value < 0 || value > maxVal) {
                    System.out.println(" *** Unexpected ref code "+value);
                    return ConstantPool.getLiteralEntry(new Integer(value));
                }
                return cpMap[value];
            }
            public void putRef(int bandIndex, Entry ref) {
                counts[1][2]++;
                if (ref == null) {
                    putInt(bandIndex, 0);
                    return;
                }
                Number refValue = null;
                if (ref instanceof ConstantPool.NumberEntry)
                    refValue = ((ConstantPool.NumberEntry)ref).numberValue();
                int value;
                if (!(refValue instanceof Integer)) {
                    System.out.println(" *** Unexpected ref "+ref);
                    value = -1;
                } else {
                    value = ((Integer)refValue).intValue();
                }
                putInt(bandIndex, value);
            }
            public int encodeBCI(int bci) {
                counts[1][1]++;
                // move LSB to MSB of low byte
                int code = (bci >> 8) << 8;  // keep high bits
                code += (bci & 0xFE) >> 1;
                code += (bci & 0x01) << 7;
                return code ^ (8<<8);  // mark it clearly as coded
            }
            public int decodeBCI(int bciCode) {
                counts[0][1]++;
                bciCode ^= (8<<8);  // remove extra mark
                int bci = (bciCode >> 8) << 8;  // keep high bits
                bci += (bciCode & 0x7F) << 1;
                bci += (bciCode & 0x80) >> 7;
                return bci;
            }
        }
        TestValueStream tts = new TestValueStream();
        tts.maxVal = maxVal;
        tts.verbose = verbose;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (int i = 0; i < (1 << 30); i = (i + 1) * 5) {
            int ei = tts.encodeBCI(i);
            int di = tts.decodeBCI(ei);
            if (di != i)  System.out.println("i="+Integer.toHexString(i)+
                                             " ei="+Integer.toHexString(ei)+
                                             " di="+Integer.toHexString(di));
        }
        while (iters-- > 0) {
            for (int i = ap; i < av.length; i++) {
                String layout = av[i];
                if (layout.startsWith("=")) {
                    String name = layout.substring(1);
                    for (Iterator j = standardDefs.values().iterator(); j.hasNext(); ) {
                        Attribute a = (Attribute) j.next();
                        if (a.name().equals(name)) {
                            layout = a.layout().layout();
                            break;
                        }
                    }
                    if (layout.startsWith("=")) {
                        System.out.println("Could not find "+name+" in "+standardDefs.values());
                    }
                }
                Layout self = new Layout(0, "Foo", layout);
                if (verbose) {
                    System.out.print("/"+layout+"/ => ");
                    System.out.println(Arrays.asList(self.elems));
                }
                buf.reset();
                tts.reset();
                Object fixups = self.unparse(tts, buf);
                byte[] bytes = buf.toByteArray();
                // Attach the references to the byte array.
                Fixups.setBytes(fixups, bytes);
                // Patch the references to their frozen values.
                Fixups.finishRefs(fixups, bytes, new Index("test", cpMap));
                if (verbose) {
                    System.out.print("  bytes: {");
                    for (int j = 0; j < bytes.length; j++) {
                        System.out.print(" "+bytes[j]);
                    }
                    System.out.println("}");
                }
                if (verbose) {
                    System.out.print("  parse: {");
                }
                self.parse(0, cls, bytes, 0, bytes.length, tts);
                if (verbose) {
                    System.out.println("}");
                }
            }
        }
        for (int j = 0; j <= 1; j++) {
            System.out.print("values "+(j==0?"read":"written")+": {");
            for (int k = 0; k < counts[j].length; k++) {
                System.out.print(" "+counts[j][k]);
            }
            System.out.println(" }");
        }
    }
//*/
}
