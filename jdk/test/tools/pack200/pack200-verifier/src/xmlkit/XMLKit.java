/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
package xmlkit; // -*- mode: java; indent-tabs-mode: nil -*-

// XML Implementation packages:
import java.util.*;

import java.io.Reader;
import java.io.Writer;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.StringReader;

import java.io.IOException;

import org.xml.sax.XMLReader;
import org.xml.sax.InputSource;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.Attributes;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 * A kit of methods and classes useful for manipulating XML trees in
 * memory. They are very compact and easy to use. An XML element
 * occupies six pointers of overhead (like two arrays) plus a pointer
 * for its name, each attribute name and value, and each sub-element.
 * Many useful XML operations (or Lisp-like calls) can be accomplished
 * with a single method call on an element itself.
 * <p>
 * There is strong integration with the Java collection classes.
 * There are viewing and conversion operators to and from various
 * collection types. Elements directly support list iterators.
 * Most <tt>List</tt> methods work analogously on elements.
 * <p>
 * Because of implementation compromises, these XML trees are less
 * functional than many standard XML classes.
 * <ul>
 * <li>There are no parent or sibling pointers in the tree.</li>
 * <li>Attribute names are simple strings, with no namespaces.</li>
 * <li>There is no internal support for schemas or validation.</li>
 * </ul>
 * <p>
 * Here is a summary of functionality in <tt>XMLKit</tt>.
 * (Overloaded groups of methods are summarized by marking some
 * arguments optional with their default values. Some overloaded
 * arguments are marked with their alternative types separated by
 * a bar "|". Arguments or return values for which a null is
 * specially significant are marked by an alternative "|null".
 * Accessors which have corresponding setters are marked
 * by "/set". Removers which have corresponding retainers are marked
 * by "/retain".)
 * <pre>
 * --- element construction
 * new Element(int elemCapacity=4), String name=""
 * new Element(String name, String[] attrs={}, Element[] elems={}, int elemCapacity=4)
 * new Element(String name, String[] attrs, Object[] elems, int elemCapacity=4)
 * new Element(Element original) // shallow copy
 * new Element(String name="", Collection elems) // coercion
 *
 * Element shallowCopy()
 * Element shallowFreeze() // side-effecting
 * Element deepCopy()
 * Element deepFreeze() // not side-effecting
 *
 * EMPTY // frozen empty anonymous element
 * void ensureExtraCapacity(int)
 * void trimToSize()
 * void sortAttrs() // sort by key
 *
 * --- field accessors
 * String getName()/set
 * int size()
 * boolean isEmpty()
 * boolean isFrozen()
 * boolean isAnonymous()
 * int getExtraCapacity()/set
 * int attrSize()
 *
 * --- attribute accessors
 * String getAttr(int i)/set
 * String getAttrName(int i)
 *
 * String getAttr(String key)/set
 * List getAttrList(String key)/set
 * Number getAttrNumber(String key)/set
 * long getAttrLong(String key)/set
 * double getAttrDouble(String key)/set
 *
 * String getAttr(String key, String dflt=null)
 * long getAttrLong(String key, long dflt=0)
 * double getAttrDouble(String key, double dflt=0)
 *
 * Element copyAttrsOnly()
 * Element getAttrs()/set =&gt; <em>&lt;&gt;&lt;key&gt;value&lt;/key&gt;...&lt;/&gt;</em>
 * void addAttrs(Element attrs)
 *
 * void removeAttr(int i)
 * void clearAttrs()
 *
 * --- element accessors
 * Object get(int i)/set
 * Object getLast() | null
 * Object[] toArray()
 * Element copyContentOnly()
 *
 * void add(int i=0, Object subElem)
 * int addAll(int i=0, Collection | Element elems)
 * int addContent(int i=0, TokenList|Element|Object|null)
 * void XMLKit.addContent(TokenList|Element|Object|null, Collection sink|null)
 *
 * void clear(int beg=0, int end=size)
 * void sort(Comparator=contentOrder())
 * void reverse()
 * void shuffle(Random rnd=(anonymous))
 * void rotate(int distance)
 * Object min/max(Comparator=contentOrder())
 *
 * --- text accessors
 * CharSequence getText()/set
 * CharSequence getUnmarkedText()
 * int addText(int i=size, CharSequence)
 * void trimText();
 *
 * --- views
 * List asList() // element view
 * ListIterator iterator()
 * PrintWriter asWriter()
 * Map asAttrMap()
 * Iterable<CharSequence> texts()
 * Iterable<Element> elements()
 * Iterable<T> partsOnly(Class<T>)
 * String[] toStrings()
 *
 * --- queries
 * boolean equals(Element | Object)
 * int compareTo(Element | Object)
 * boolean equalAttrs(Element)
 * int hashCode()
 * boolean isText() // every sub-elem is CharSequence
 * boolean hasText() // some sub-elem is CharSequence
 *
 * boolean contains(Object)
 * boolean containsAttr(String)
 *
 * int indexOf(Object)
 * int indexOf(Filter, int fromIndex=0)
 * int lastIndexOf(Object)
 * int lastIndexOf(Filter, int fromIndex=size-1)
 *
 * int indexOfAttr(String)
 *
 * // finders, removers, and replacers do addContent of each filtered value
 * // (i.e., TokenLists and anonymous Elements are broken out into their parts)
 * boolean matches(Filter)
 *
 * Object find(Filter, int fromIndex=0)
 * Object findLast(Filter, int fromIndex=size-1)
 * Element findAll(Filter, int fromIndex=0 &amp; int toIndex=size)
 * int findAll(Filter, Collection sink | null, int fromIndex=0 &amp; int toIndex=size)
 *
 * Element removeAllInTree(Filter)/retain
 * int findAllInTree(Filter, Collection sink | null)
 * int countAllInTree(Filter)
 * Element removeAllInTree(Filter)/retain
 * int removeAllInTree(Filter, Collection sink | null)/retain
 * void replaceAllInTree(Filter)
 *
 * Element findElement(String name=any)
 * Element findAllElements(String name=any)
 *
 * Element findWithAttr(String key, String value=any)
 * Element findAllWithAttr(String key, String value=any)
 *
 * Element removeElement(String name=any)
 * Element removeAllElements(String name=any)/retain
 *
 * Element removeWithAttr(String key, String value=any)
 * Element removeAllWithAttr(String key, String value=any)/retain
 *
 * //countAll is the same as findAll but with null sink
 * int countAll(Filter)
 * int countAllElements(String name=any)
 * int countAllWithAttr(String key, String value=any)
 *
 * void replaceAll(Filter, int fromIndex=0 &amp; int toIndex=size)
 * void replaceAllInTree(Filter)
 * void XMLKit.replaceAll(Filter, List target) //if(fx){remove x;addContent fx}
 *
 * --- element mutators
 * boolean remove(Object)
 * Object remove(int)
 * Object removeLast() | null
 *
 * Object remove(Filter, int fromIndex=0)
 * Object removeLast(Filter, int fromIndex=size-1)
 * Element sink = removeAll(Filter, int fromIndex=0 &amp; int toIndex=size)/retain
 * int count = removeAll(Filter, int fromIndex=0 &amp; int toIndex=size, Collection sink | null)/retain
 *
 * Element removeAllElements(String name=any)
 *
 * --- attribute mutators
 * ??int addAllAttrsFrom(Element attrSource)
 *
 * --- parsing and printing
 * void tokenize(String delims=whitespace, returnDelims=false)
 * void writeTo(Writer)
 * void writePrettyTo(Writer)
 * String prettyString()
 * String toString()
 *
 * ContentHandler XMLKit.makeBuilder(Collection sink, tokenizing=false, makeFrozen=false) // for standard XML parser
 * Element XMLKit.readFrom(Reader, tokenizing=false, makeFrozen=false)
 * void XMLKit.prettyPrintTo(Writer | OutputStream, Element)
 * class XMLKit.Printer(Writer) { void print/Recursive(Element) }
 * void XMLKit.output(Object elem, ContentHandler, LexicalHandler=null)
 * void XMLKit.writeToken(String, char quote, Writer)
 * void XMLKit.writeCData(String, Writer)
 * Number XMLKit.convertToNumber(String, Number dflt=null)
 * long XMLKit.convertToLong(String, long dflt=0)
 * double XMLKit.convertToDouble(String, double dflt=0)
 *
 * --- filters
 * XMLKit.ElementFilter { Element filter(Element) }
 * XMLKit.elementFilter(String name=any | Collection nameSet)
 * XMLKit.AttrFilter(String key) { boolean test(String value) }
 * XMLKit.attrFilter(String key, String value=any)
 * XMLKit.attrFilter(Element matchThis, String key)
 * XMLKit.classFilter(Class)
 * XMLKit.textFilter() // matches any CharSequence
 * XMLKit.specialFilter() // matches any Special element
 * XMLKit.methodFilter(Method m, Object[] args=null, falseResult=null)
 * XMLKit.testMethodFilter(Method m, Object[] args=null)
 * XMLKit.not(Filter) // inverts sense of Filter
 * XMLKit.and(Filter&amp;Filter | Filter[])
 * XMLKit.or(Filter&amp;Filter | Filter[])
 * XMLKit.stack(Filter&amp;Filter | Filter[]) // result is (fx && g(fx))
 * XMLKit.content(Filter, Collection sink) // copies content to sink
 * XMLKit.replaceInTree(Filter pre, Filter post=null) // pre-replace else recur
 * XMLKit.findInTree(Filter pre, Collection sink=null) // pre-find else recur
 * XMLKit.nullFilter() // ignores input, always returns null (i.e., false)
 * XMLKit.selfFilter( ) // always returns input (i.e., true)
 * XMLKit.emptyFilter() // ignores input, always returns EMPTY
 * XMLKit.constantFilter(Object) // ignores input, always returns constant
 *
 * --- misc
 * Comparator XMLKit.contentOrder() // for comparing/sorting mixed content
 * Method XMLKit.Element.method(String name) // returns Element method
 * </pre>
 *
 * @author jrose
 */
public abstract class XMLKit {

    private XMLKit() {
    }
    // We need at least this much slop if the element is to stay unfrozen.
    static final int NEED_SLOP = 1;
    static final Object[] noPartsFrozen = {};
    static final Object[] noPartsNotFrozen = new Object[NEED_SLOP];
    static final String WHITESPACE_CHARS = " \t\n\r\f";
    static final String ANON_NAME = new String("*");  // unique copy of "*"

    public static final class Element implements Comparable<Element>, Iterable<Object> {
        // Note:  Does not implement List, because it has more
        // significant parts besides its sub-elements.  Therefore,
        // hashCode and equals must be more distinctive than Lists.

        // <name> of element
        String name;
        // number of child elements, in parts[0..size-1]
        int size;
        // The parts start with child elements::  {e0, e1, e2, ...}.
        // Following that are optional filler elements, all null.
        // Following that are attributes as key/value pairs.
        // They are in reverse: {...key2, val2, key1, val1, key0, val0}.
        // Child elements and attr keys and values are never null.
        Object[] parts;

        // Build a partially-constructed node.
        // Caller is responsible for initializing promised attributes.
        Element(String name, int size, int capacity) {
            this.name = name.toString();
            this.size = size;
            assert (size <= capacity);
            this.parts = capacity > 0 ? new Object[capacity] : noPartsFrozen;
        }

        /** An anonymous, empty element.
         *  Optional elemCapacity argument is expected number of sub-elements.
         */
        public Element() {
            this(ANON_NAME, 0, NEED_SLOP + 4);
        }

        public Element(int extraCapacity) {
            this(ANON_NAME, 0, NEED_SLOP + Math.max(0, extraCapacity));
        }

        /** An empty element with the given name.
         *  Optional extraCapacity argument is expected number of sub-elements.
         */
        public Element(String name) {
            this(name, 0, NEED_SLOP + 4);
        }

        public Element(String name, int extraCapacity) {
            this(name, 0, NEED_SLOP + Math.max(0, extraCapacity));
        }

        /** An empty element with the given name and attributes.
         *  Optional extraCapacity argument is expected number of sub-elements.
         */
        public Element(String name, String... attrs) {
            this(name, attrs, (Element[]) null, 0);
        }

        public Element(String name, String[] attrs, int extraCapacity) {
            this(name, attrs, (Element[]) null, extraCapacity);
        }

        /** An empty element with the given name and sub-elements.
         *  Optional extraCapacity argument is expected extra sub-elements.
         */
        public Element(String name, Element... elems) {
            this(name, (String[]) null, elems, 0);
        }

        public Element(String name, Element[] elems, int extraCapacity) {
            this(name, (String[]) null, elems, extraCapacity);
        }

        /** An empty element with the given name, attributes, and sub-elements.
         *  Optional extraCapacity argument is expected extra sub-elements.
         */
        public Element(String name, String[] attrs, Object... elems) {
            this(name, attrs, elems, 0);
        }

        public Element(String name, String[] attrs, Object[] elems, int extraCapacity) {
            this(name, 0,
                    ((elems == null) ? 0 : elems.length)
                    + Math.max(0, extraCapacity)
                    + NEED_SLOP
                    + ((attrs == null) ? 0 : attrs.length));
            int ne = ((elems == null) ? 0 : elems.length);
            int na = ((attrs == null) ? 0 : attrs.length);
            int fillp = 0;
            for (int i = 0; i < ne; i++) {
                if (elems[i] != null) {
                    parts[fillp++] = elems[i];
                }
            }
            size = fillp;
            for (int i = 0; i < na; i += 2) {
                setAttr(attrs[i + 0], attrs[i + 1]);
            }
        }

        public Element(Collection c) {
            this(c.size());
            addAll(c);
        }

        public Element(String name, Collection c) {
            this(name, c.size());
            addAll(c);
        }

        /** Shallow copy.  Same as old.shallowCopy().
         *  Optional extraCapacity argument is expected extra sub-elements.
         */
        public Element(Element old) {
            this(old, 0);
        }

        public Element(Element old, int extraCapacity) {
            this(old.name, old.size,
                    old.size
                    + Math.max(0, extraCapacity) + NEED_SLOP
                    + old.attrLength());
            // copy sub-elements
            System.arraycopy(old.parts, 0, parts, 0, size);
            int alen = parts.length
                    - (size + Math.max(0, extraCapacity) + NEED_SLOP);
            // copy attributes
            System.arraycopy(old.parts, old.parts.length - alen,
                    parts, parts.length - alen,
                    alen);
            assert (!isFrozen());
        }

        /** Shallow copy.  Same as new Element(this). */
        public Element shallowCopy() {
            return new Element(this);
        }
        static public final Element EMPTY = new Element(ANON_NAME, 0, 0);

        Element deepFreezeOrCopy(boolean makeFrozen) {
            if (makeFrozen && isFrozen()) {
                return this;  // no need to copy it
            }
            int alen = attrLength();
            int plen = size + (makeFrozen ? 0 : NEED_SLOP) + alen;
            Element copy = new Element(name, size, plen);
            // copy attributes
            System.arraycopy(parts, parts.length - alen, copy.parts, plen - alen, alen);
            // copy sub-elements
            for (int i = 0; i < size; i++) {
                Object e = parts[i];
                String str;
                if (e instanceof Element) {  // recursion is common case
                    e = ((Element) e).deepFreezeOrCopy(makeFrozen);
                } else if (makeFrozen) {
                    // Freeze StringBuffers, etc.
                    e = fixupString(e);
                }
                copy.setRaw(i, e);
            }
            return copy;
        }

        /** Returns new Element(this), and also recursively copies sub-elements. */
        public Element deepCopy() {
            return deepFreezeOrCopy(false);
        }

        /** Returns frozen version of deepCopy. */
        public Element deepFreeze() {
            return deepFreezeOrCopy(true);
        }

        /** Freeze this element.
         *  Throw an IllegalArgumentException if any sub-element is not already frozen.
         *  (Use deepFreeze() to make a frozen copy of an entire element tree.)
         */
        public void shallowFreeze() {
            if (isFrozen()) {
                return;
            }
            int alen = attrLength();
            Object[] nparts = new Object[size + alen];
            // copy attributes
            System.arraycopy(parts, parts.length - alen, nparts, size, alen);
            // copy sub-elements
            for (int i = 0; i < size; i++) {
                Object e = parts[i];
                String str;
                if (e instanceof Element) {  // recursion is common case
                    if (!((Element) e).isFrozen()) {
                        throw new IllegalArgumentException("Sub-element must be frozen.");
                    }
                } else {
                    // Freeze StringBuffers, etc.
                    e = fixupString(e);
                }
                nparts[i] = e;
            }
            parts = nparts;
            assert (isFrozen());
        }

        /** Return the name of this element. */
        public String getName() {
            return name;
        }

        /** Change the name of this element. */
        public void setName(String name) {
            checkNotFrozen();
            this.name = name.toString();
        }

        /** Reports if the element's name is a particular string (spelled "*").
         *  Such elements are created by the nullary Element constructor,
         *  and by query functions which return multiple values,
         *  such as <tt>findAll</tt>.
         */
        public boolean isAnonymous() {
            return name == ANON_NAME;
        }

        /** Return number of elements.  (Does not include attributes.) */
        public int size() {
            return size;
        }

        /** True if no elements.  (Does not consider attributes.) */
        public boolean isEmpty() {
            return size == 0;
        }

        /** True if this element does not allow modification. */
        public boolean isFrozen() {
            // It is frozen iff there is no slop space.
            return !hasNulls(NEED_SLOP);
        }

        void checkNotFrozen() {
            if (isFrozen()) {
                throw new UnsupportedOperationException("cannot modify frozen element");
            }
        }

        /** Remove specified elements.  (Does not affect attributes.) */
        public void clear() {
            clear(0, size);
        }

        public void clear(int beg) {
            clear(beg, size);
        }

        public void clear(int beg, int end) {
            if (end > size) {
                badIndex(end);
            }
            if (beg < 0 || beg > end) {
                badIndex(beg);
            }
            if (beg == end) {
                return;
            }
            checkNotFrozen();
            if (end == size) {
                if (beg == 0
                        && parts.length > 0 && parts[parts.length - 1] == null) {
                    // If no attributes, free the parts array.
                    parts = noPartsNotFrozen;
                    size = 0;
                } else {
                    clearParts(beg, size);
                    size = beg;
                }
            } else {
                close(beg, end - beg);
            }
        }

        void clearParts(int beg, int end) {
            for (int i = beg; i < end; i++) {
                parts[i] = null;
            }
        }

        /** True if name, attributes, and elements are the same. */
        public boolean equals(Element that) {
            if (!this.name.equals(that.name)) {
                return false;
            }
            if (this.size != that.size) {
                return false;
            }
            // elements must be equal and ordered
            Object[] thisParts = this.parts;
            Object[] thatParts = that.parts;
            for (int i = 0; i < size; i++) {
                Object thisPart = thisParts[i];
                Object thatPart = thatParts[i];

                if (thisPart instanceof Element) { // recursion is common case
                    if (!thisPart.equals(thatPart)) {
                        return false;
                    }
                } else {
                    // If either is a non-string char sequence, normalize it.
                    thisPart = fixupString(thisPart);
                    thatPart = fixupString(thatPart);
                    if (!thisPart.equals(thatPart)) {
                        return false;
                    }
                }
            }
            // finally, attributes must be equal (unordered)
            return this.equalAttrs(that);
        }
        // bridge method

        public boolean equals(Object o) {
            if (!(o instanceof Element)) {
                return false;
            }
            return equals((Element) o);
        }

        public int hashCode() {
            int hc = 0;
            int alen = attrLength();
            for (int i = parts.length - alen; i < parts.length; i += 2) {
                hc += (parts[i + 0].hashCode() ^ parts[i + 1].hashCode());
            }
            hc ^= hc << 11;
            hc += name.hashCode();
            for (int i = 0; i < size; i++) {
                hc ^= hc << 7;
                Object p = parts[i];
                if (p instanceof Element) {
                    hc += p.hashCode();  // recursion is common case
                } else {
                    hc += fixupString(p).hashCode();
                }
            }
            hc ^= hc >>> 19;
            return hc;
        }

        /** Compare lexicographically.  Earlier-spelled attrs are more sigificant. */
        public int compareTo(Element that) {
            int r;
            // Primary key is element name.
            r = this.name.compareTo(that.name);
            if (r != 0) {
                return r;
            }

            // Secondary key is attributes, as if in normal key order.
            // The key/value pairs are sorted as a token sequence.
            int thisAlen = this.attrLength();
            int thatAlen = that.attrLength();
            if (thisAlen != 0 || thatAlen != 0) {
                r = compareAttrs(thisAlen, that, thatAlen, true);
                assert (assertAttrCompareOK(r, that));
                if (r != 0) {
                    return r;
                }
            }

            // Finally, elements should be equal and ordered,
            // and the first difference rules.
            Object[] thisParts = this.parts;
            Object[] thatParts = that.parts;
            int minSize = this.size;
            if (minSize > that.size) {
                minSize = that.size;
            }
            Comparator<Object> cc = contentOrder();
            for (int i = 0; i < minSize; i++) {
                r = cc.compare(thisParts[i], thatParts[i]);
                if (r != 0) {
                    return r;
                }
            }
            //if (this.size < that.size)  return -1;
            return this.size - that.size;
        }

        private boolean assertAttrCompareOK(int r, Element that) {
            Element e0 = this.copyAttrsOnly();
            Element e1 = that.copyAttrsOnly();
            e0.sortAttrs();
            e1.sortAttrs();
            int r2;
            for (int k = 0;; k++) {
                boolean con0 = e0.containsAttr(k);
                boolean con1 = e1.containsAttr(k);
                if (con0 != con1) {
                    if (!con0) {
                        r2 = 0 - 1;
                        break;
                    }
                    if (!con1) {
                        r2 = 1 - 0;
                        break;
                    }
                }
                if (!con0) {
                    r2 = 0;
                    break;
                }
                String k0 = e0.getAttrName(k);
                String k1 = e1.getAttrName(k);
                r2 = k0.compareTo(k1);
                if (r2 != 0) {
                    break;
                }
                String v0 = e0.getAttr(k);
                String v1 = e1.getAttr(k);
                r2 = v0.compareTo(v1);
                if (r2 != 0) {
                    break;
                }
            }
            if (r != 0) {
                r = (r > 0) ? 1 : -1;
            }
            if (r2 != 0) {
                r2 = (r2 > 0) ? 1 : -1;
            }
            if (r != r2) {
                System.out.println("*** wrong attr compare, " + r + " != " + r2);
                System.out.println(" this = " + this);
                System.out.println("  attr->" + e0);
                System.out.println(" that = " + that);
                System.out.println("  attr->" + e1);
            }
            return r == r2;
        }

        private void badIndex(int i) {
            Object badRef = (new Object[0])[i];
        }

        public Object get(int i) {
            if (i >= size) {
                badIndex(i);
            }
            return parts[i];
        }

        public Object set(int i, Object e) {
            if (i >= size) {
                badIndex(i);
            }
            e.getClass();  // null check
            checkNotFrozen();
            Object old = parts[i];
            setRaw(i, e);
            return old;
        }

        void setRaw(int i, Object e) {
            parts[i] = e;
        }

        public boolean remove(Object e) {
            int i = indexOf(e);
            if (i < 0) {
                return false;
            }
            close(i, 1);
            return true;
        }

        public Object remove(int i) {
            if (i >= size) {
                badIndex(i);
            }
            Object e = parts[i];
            close(i, 1);
            return e;
        }

        public Object removeLast() {
            if (size == 0) {
                return null;
            }
            return remove(size - 1);
        }

        /** Remove the first element matching the given filter.
         *  Return the filtered value.
         */
        public Object remove(Filter f) {
            return findOrRemove(f, 0, true);
        }

        public Object remove(Filter f, int fromIndex) {
            if (fromIndex < 0) {
                fromIndex = 0;
            }
            return findOrRemove(f, fromIndex, true);
        }

        /** Remove the last element matching the given filter.
         *  Return the filtered value.
         */
        public Object removeLast(Filter f) {
            return findOrRemoveLast(f, size - 1, true);
        }

        public Object removeLast(Filter f, int fromIndex) {
            if (fromIndex >= size) {
                fromIndex = size - 1;
            }
            return findOrRemoveLast(f, fromIndex, true);
        }

        /** Remove all elements matching the given filter.
         *  If there is a non-null collection given as a sink,
         *  transfer removed elements to the given collection.
         *  The int result is the number of removed elements.
         *  If there is a null sink given, the removed elements
         *  are discarded.  If there is no sink given, the removed
         *  elements are returned in an anonymous container element.
         */
        public Element removeAll(Filter f) {
            Element result = new Element();
            findOrRemoveAll(f, false, 0, size, result.asList(), true);
            return result;
        }

        public Element removeAll(Filter f, int fromIndex, int toIndex) {
            Element result = new Element();
            findOrRemoveAll(f, true, fromIndex, toIndex, result.asList(), true);
            return result;
        }

        public int removeAll(Filter f, Collection<Object> sink) {
            return findOrRemoveAll(f, false, 0, size, sink, true);
        }

        public int removeAll(Filter f, int fromIndex, int toIndex, Collection<Object> sink) {
            return findOrRemoveAll(f, false, fromIndex, toIndex, sink, true);
        }

        /** Remove all elements not matching the given filter.
         *  If there is a non-null collection given as a sink,
         *  transfer removed elements to the given collection.
         *  The int result is the number of removed elements.
         *  If there is a null sink given, the removed elements
         *  are discarded.  If there is no sink given, the removed
         *  elements are returned in an anonymous container element.
         */
        public Element retainAll(Filter f) {
            Element result = new Element();
            findOrRemoveAll(f, true, 0, size, result.asList(), true);
            return result;
        }

        public Element retainAll(Filter f, int fromIndex, int toIndex) {
            Element result = new Element();
            findOrRemoveAll(f, true, fromIndex, toIndex, result.asList(), true);
            return result;
        }

        public int retainAll(Filter f, Collection<Object> sink) {
            return findOrRemoveAll(f, true, 0, size, sink, true);
        }

        public int retainAll(Filter f, int fromIndex, int toIndex, Collection<Object> sink) {
            return findOrRemoveAll(f, true, fromIndex, toIndex, sink, true);
        }

        public void add(int i, Object e) {
            // (The shape of this method is tweaked for common cases.)
            e.getClass();  // force a null check on e
            if (hasNulls(1 + NEED_SLOP)) {
                // Common case:  Have some slop space.
                if (i == size) {
                    // Most common case:  Append.
                    setRaw(i, e);
                    size++;
                    return;
                }
                if (i > size) {
                    badIndex(i);
                }
                // Second most common case:  Shift right by one.
                open(i, 1);
                setRaw(i, e);
                return;
            }
            // Ran out of space.  Do something complicated.
            size = expand(i, 1);
            setRaw(i, e);
        }

        public boolean add(Object e) {
            add(size, e);
            return true;
        }

        public Object getLast() {
            return size == 0 ? null : parts[size - 1];
        }

        /** Returns the text of this Element.
         *  All sub-elements of this Element must be of type CharSequence.
         *  A ClassCastException is raised if there are non-character sub-elements.
         *  If there is one sub-element, return it.
         *  Otherwise, returns a TokenList of all sub-elements.
         *  This results in a space being placed between each adjacent pair of sub-elements.
         */
        public CharSequence getText() {
            checkTextOnly();
            if (size == 1) {
                return parts[0].toString();
            } else {
                return new TokenList(parts, 0, size);
            }
        }

        /** Provides an iterable view of this object as a series of texts.
         *  All sub-elements of this Element must be of type CharSequence.
         *  A ClassCastException is raised if there are non-character sub-elements.
         */
        public Iterable<CharSequence> texts() {
            checkTextOnly();
            return (Iterable<CharSequence>) (Iterable) this;
        }

        /** Returns an array of strings derived from the sub-elements of this object.
         *  All sub-elements of this Element must be of type CharSequence.
         *  A ClassCastException is raised if there are non-character sub-elements.
         */
        public String[] toStrings() {
            //checkTextOnly();
            String[] result = new String[size];
            for (int i = 0; i < size; i++) {
                result[i] = ((CharSequence) parts[i]).toString();
            }
            return result;
        }

        /** Like getText, except that it disregards non-text elements.
         *  Non-text elements are replaced by their textual contents, if any.
         *  Text elements which were separated only by non-text element
         *  boundaries are merged into single tokens.
         *  <p>
         *  There is no corresponding setter, since this accessor does
         *  not report the full state of the element.
         */
        public CharSequence getFlatText() {
            if (size == 1) {
                // Simple cases.
                if (parts[0] instanceof CharSequence) {
                    return parts[0].toString();
                } else {
                    return new TokenList();
                }
            }
            if (isText()) {
                return getText();
            }
            // Filter and merge.
            Element result = new Element(size);
            boolean merge = false;
            for (int i = 0; i < size; i++) {
                Object text = parts[i];
                if (!(text instanceof CharSequence)) {
                    // Skip, but erase this boundary.
                    if (text instanceof Element) {
                        Element te = (Element) text;
                        if (!te.isEmpty()) {
                            result.addText(te.getFlatText());
                        }
                    }
                    merge = true;
                    continue;
                }
                if (merge) {
                    // Merge w/ previous token.
                    result.addText((CharSequence) text);
                    merge = false;
                } else {
                    result.add(text);
                }
            }
            if (result.size() == 1) {
                return (CharSequence) result.parts[0];
            } else {
                return result.getText();
            }
        }

        /** Return true if all sub-elements are of type CharSequence. */
        public boolean isText() {
            for (int i = 0; i < size; i++) {
                if (!(parts[i] instanceof CharSequence)) {
                    return false;
                }
            }
            return true;
        }

        /** Return true if at least one sub-element is of type CharSequence. */
        public boolean hasText() {
            for (int i = 0; i < size; i++) {
                if (parts[i] instanceof CharSequence) {
                    return true;
                }
            }
            return false;
        }

        /** Raise a ClassCastException if !isText. */
        public void checkTextOnly() {
            for (int i = 0; i < size; i++) {
                ((CharSequence) parts[i]).getClass();
            }
        }

        /** Clears out all sub-elements, and replaces them by the given text.
         *  A ClassCastException is raised if there are non-character sub-elements,
         *  either before or after the change.
         */
        public void setText(CharSequence text) {
            checkTextOnly();
            clear();
            if (text instanceof TokenList) {
                // TL's contain only strings
                addAll(0, (TokenList) text);
            } else {
                add(text);
            }
        }

        /** Add text at the given position, merging with any previous
         *  text element, but preserving token boundaries where possible.
         *  <p>
         *  In all cases, the new value of getText() is the string
         *  concatenation of the old value of getText() plus the new text.
         *  <p>
         *  The total effect is to concatenate the given text to any
         *  pre-existing text, and to do so efficiently even if there
         *  are many such concatenations.  Also, getText calls which
         *  return multiple tokens (in a TokenList) are respected.
         *  For example, if x is empty, x.addText(y.getText()) puts
         *  an exact structural copy of y's text into x.
         *  <p>
         *  Internal token boundaries in the original text, and in the new
         *  text (i.e., if it is a TokenList), are preserved.  However,
         *  at the point where new text joins old text, a StringBuffer
         *  or new String may be created to join the last old and first
         *  new token.
         *  <p>
         *  If the given text is a TokenList, add the tokens as
         *  separate sub-elements, possibly merging the first token to
         *  a previous text item (to avoid making a new token boundary).
         *  <p>
         *  If the element preceding position i is a StringBuffer,
         *  append the first new token to it.
         *  <p>
         *  If the preceding element is a CharSequence, replace it by a
         *  StringBuffer containing both its and the first new token.
         *  <p>
         *  If tokens are added after a StringBuffer, freeze it into a String.
         *  <p>
         *  Every token not merged into a previous CharSequence is added
         *  as a new sub-element, starting at position i.
         *  <p>
         *  Returns the number of elements added, which is useful
         *  for further calls to addText.  This number is zero
         *  if the input string was null, or was successfully
         *  merged into a StringBuffer at position i-1.
         *  <p>
         *  By contrast, calling add(text) always adds a new sub-element.
         *  In that case, if there is a previous string, a separating
         *  space is virtually present also, and will be observed if
         *  getText() is used to return all the text together.
         */
        public int addText(int i, CharSequence text) {
            if (text instanceof String) {
                return addText(i, (String) text);
            } else if (text instanceof TokenList) {
                // Text is a list of tokens.
                TokenList tl = (TokenList) text;
                int tlsize = tl.size();
                if (tlsize == 0) {
                    return 0;
                }
                String token0 = tl.get(0).toString();
                if (tlsize == 1) {
                    return addText(i, token0);
                }
                if (mergeWithPrev(i, token0, false)) {
                    // Add the n-1 remaining tokens.
                    addAll(i, tl.subList(1, tlsize));
                    return tlsize - 1;
                } else {
                    addAll(i, (Collection) tl);
                    return tlsize;
                }
            } else {
                return addText(i, text.toString());
            }
        }

        public int addText(CharSequence text) {
            return addText(size, text);
        }

        private // no reason to make this helper public
                int addText(int i, String text) {
            if (text.length() == 0) {
                return 0;  // Trivial success.
            }
            if (mergeWithPrev(i, text, true)) {
                return 0;  // Merged with previous token.
            }
            // No previous token.
            add(i, text);
            return 1;
        }

        // Tries to merge token with previous contents.
        // Returns true if token is successfully disposed of.
        // If keepSB is false, any previous StringBuffer is frozen.
        // If keepSB is true, a StringBuffer may be created to hold
        // the merged token.
        private boolean mergeWithPrev(int i, String token, boolean keepSB) {
            if (i == 0) // Trivial success if the token is length zero.
            {
                return (token.length() == 0);
            }
            Object prev = parts[i - 1];
            if (prev instanceof StringBuffer) {
                StringBuffer psb = (StringBuffer) prev;
                psb.append(token);
                if (!keepSB) {
                    parts[i - 1] = psb.toString();
                }
                return true;
            }
            if (token.length() == 0) {
                return true;  // Trivial success.
            }
            if (prev instanceof CharSequence) {
                // Must concatenate.
                StringBuffer psb = new StringBuffer(prev.toString());
                psb.append(token);
                if (keepSB) {
                    parts[i - 1] = psb;
                } else {
                    parts[i - 1] = psb.toString();
                }
                return true;
            }
            return false;
        }

        /** Trim all strings, using String.trim().
         *  Remove empty strings.
         *  Normalize CharSequences to Strings.
         */
        public void trimText() {
            checkNotFrozen();
            int fillp = 0;
            int size = this.size;
            Object[] parts = this.parts;
            for (int i = 0; i < size; i++) {
                Object e = parts[i];
                if (e instanceof CharSequence) {
                    String tt = e.toString().trim();
                    if (tt.length() == 0) {
                        continue;
                    }
                    e = tt;
                }
                parts[fillp++] = e;
            }
            while (size > fillp) {
                parts[--size] = null;
            }
            this.size = fillp;
        }

        /** Add one or more subelements at the given position.
         *  If the object reference is null, nothing happens.
         *  If the object is an anonymous Element, addAll is called.
         *  If the object is a TokenList, addAll is called (to add the tokens).
         *  Otherwise, add is called, adding a single subelement or string.
         *  The net effect is to add zero or more tokens.
         *  The returned value is the number of added elements.
         *  <p>
         *  Note that getText() can return a TokenList which preserves
         *  token boundaries in the text source.  Such a text will be
         *  added as multiple text sub-elements.
         *  <p>
         *  If a text string is added adjacent to an immediately
         *  preceding string, there will be a token boundary between
         *  the strings, which will print as an extra space.
         */
        public int addContent(int i, Object e) {
            if (e == null) {
                return 0;
            } else if (e instanceof TokenList) {
                return addAll(i, (Collection) e);
            } else if (e instanceof Element
                    && ((Element) e).isAnonymous()) {
                return addAll(i, (Element) e);
            } else {
                add(i, e);
                return 1;
            }
        }

        public int addContent(Object e) {
            return addContent(size, e);
        }

        public Object[] toArray() {
            Object[] result = new Object[size];
            System.arraycopy(parts, 0, result, 0, size);
            return result;
        }

        public Element copyContentOnly() {
            Element content = new Element(size);
            System.arraycopy(parts, 0, content.parts, 0, size);
            content.size = size;
            return content;
        }

        public void sort(Comparator<Object> c) {
            Arrays.sort(parts, 0, size, c);
        }

        public void sort() {
            sort(CONTENT_ORDER);
        }

        /** Equivalent to Collections.reverse(this.asList()). */
        public void reverse() {
            for (int i = 0, mid = size >> 1, j = size - 1; i < mid; i++, j--) {
                Object p = parts[i];
                parts[i] = parts[j];
                parts[j] = p;
            }
        }

        /** Equivalent to Collections.shuffle(this.asList() [, rnd]). */
        public void shuffle() {
            Collections.shuffle(this.asList());
        }

        public void shuffle(Random rnd) {
            Collections.shuffle(this.asList(), rnd);
        }

        /** Equivalent to Collections.rotate(this.asList(), dist). */
        public void rotate(int dist) {
            Collections.rotate(this.asList(), dist);
        }

        /** Equivalent to Collections.min(this.asList(), c). */
        public Object min(Comparator<Object> c) {
            return Collections.min(this.asList(), c);
        }

        public Object min() {
            return min(CONTENT_ORDER);
        }

        /** Equivalent to Collections.max(this.asList(), c). */
        public Object max(Comparator<Object> c) {
            return Collections.max(this.asList(), c);
        }

        public Object max() {
            return max(CONTENT_ORDER);
        }

        public int addAll(int i, Collection c) {
            if (c instanceof LView) {
                return addAll(i, ((LView) c).asElement());
            } else {
                int csize = c.size();
                if (csize == 0) {
                    return 0;
                }
                openOrExpand(i, csize);
                int fill = i;
                for (Object part : c) {
                    parts[fill++] = part;
                }
                return csize;
            }
        }

        public int addAll(int i, Element e) {
            int esize = e.size;
            if (esize == 0) {
                return 0;
            }
            openOrExpand(i, esize);
            System.arraycopy(e.parts, 0, parts, i, esize);
            return esize;
        }

        public int addAll(Collection c) {
            return addAll(size, c);
        }

        public int addAll(Element e) {
            return addAll(size, e);
        }

        public int addAllAttrsFrom(Element e) {
            int added = 0;
            for (int k = 0; e.containsAttr(k); k++) {
                String old = setAttr(e.getAttrName(k), e.getAttr(k));
                if (old == null) {
                    added += 1;
                }
            }
            // Return number of added (not merely changed) attrs.
            return added;
        }

        // Search.
        public boolean matches(Filter f) {
            return f.filter(this) != null;
        }

        public Object find(Filter f) {
            return findOrRemove(f, 0, false);
        }

        public Object find(Filter f, int fromIndex) {
            if (fromIndex < 0) {
                fromIndex = 0;
            }
            return findOrRemove(f, fromIndex, false);
        }

        /** Find the last element matching the given filter.
         *  Return the filtered value.
         */
        public Object findLast(Filter f) {
            return findOrRemoveLast(f, size - 1, false);
        }

        public Object findLast(Filter f, int fromIndex) {
            if (fromIndex >= size) {
                fromIndex = size - 1;
            }
            return findOrRemoveLast(f, fromIndex, false);
        }

        /** Find all elements matching the given filter.
         *  If there is a non-null collection given as a sink,
         *  transfer matching elements to the given collection.
         *  The int result is the number of matching elements.
         *  If there is a null sink given, the matching elements are
         *  not collected.  If there is no sink given, the matching
         *  elements are returned in an anonymous container element.
         *  In no case is the receiver element changed.
         *  <p>
         *  Note that a simple count of matching elements can be
         *  obtained by passing a null collection argument.
         */
        public Element findAll(Filter f) {
            Element result = new Element();
            findOrRemoveAll(f, false, 0, size, result.asList(), false);
            return result;
        }

        public Element findAll(Filter f, int fromIndex, int toIndex) {
            Element result = new Element(name);
            findOrRemoveAll(f, false, fromIndex, toIndex, result.asList(), false);
            return result;
        }

        public int findAll(Filter f, Collection<Object> sink) {
            return findOrRemoveAll(f, false, 0, size, sink, false);
        }

        public int findAll(Filter f, int fromIndex, int toIndex, Collection<Object> sink) {
            return findOrRemoveAll(f, false, fromIndex, toIndex, sink, false);
        }

        /// Driver routines.
        private Object findOrRemove(Filter f, int fromIndex, boolean remove) {
            for (int i = fromIndex; i < size; i++) {
                Object x = f.filter(parts[i]);
                if (x != null) {
                    if (remove) {
                        close(i, 1);
                    }
                    return x;
                }
            }
            return null;
        }

        private Object findOrRemoveLast(Filter f, int fromIndex, boolean remove) {
            for (int i = fromIndex; i >= 0; i--) {
                Object x = f.filter(parts[i]);
                if (x != null) {
                    if (remove) {
                        close(i, 1);
                    }
                    return x;
                }
            }
            return null;
        }

        private int findOrRemoveAll(Filter f, boolean fInvert,
                int fromIndex, int toIndex,
                Collection<Object> sink, boolean remove) {
            if (fromIndex < 0) {
                badIndex(fromIndex);
            }
            if (toIndex > size) {
                badIndex(toIndex);
            }
            int found = 0;
            for (int i = fromIndex; i < toIndex; i++) {
                Object p = parts[i];
                Object x = f.filter(p);
                if (fInvert ? (x == null) : (x != null)) {
                    if (remove) {
                        close(i--, 1);
                        toIndex--;
                    }
                    found += XMLKit.addContent(fInvert ? p : x, sink);
                }
            }
            return found;
        }

        public void replaceAll(Filter f) {
            XMLKit.replaceAll(f, this.asList());
        }

        public void replaceAll(Filter f, int fromIndex, int toIndex) {
            XMLKit.replaceAll(f, this.asList().subList(fromIndex, toIndex));
        }

        /// Recursive walks.
        // findAllInTree(f)     == findAll(findInTree(f,S)), S.toElement
        // findAllInTree(f,S)   == findAll(findInTree(content(f,S)))
        // removeAllInTree(f)   == replaceAll(replaceInTree(and(f,emptyF)))
        // removeAllInTree(f,S) == replaceAll(replaceInTree(and(content(f,S),emptyF)))
        // retainAllInTree(f)   == removeAllInTree(not(f))
        // replaceAllInTree(f)  == replaceAll(replaceInTree(f))
        public Element findAllInTree(Filter f) {
            Element result = new Element();
            findAllInTree(f, result.asList());
            return result;
        }

        public int findAllInTree(Filter f, Collection<Object> sink) {
            int found = 0;
            int size = this.size;  // cache copy
            for (int i = 0; i < size; i++) {
                Object p = parts[i];
                Object x = f.filter(p);
                if (x != null) {
                    found += XMLKit.addContent(x, sink);
                } else if (p instanceof Element) {
                    found += ((Element) p).findAllInTree(f, sink);
                }
            }
            return found;
        }

        public int countAllInTree(Filter f) {
            return findAllInTree(f, null);
        }

        public int removeAllInTree(Filter f, Collection<Object> sink) {
            if (sink == null) {
                sink = newCounterColl();
            }
            replaceAll(replaceInTree(and(content(f, sink), emptyFilter())));
            return sink.size();
        }

        public Element removeAllInTree(Filter f) {
            Element result = new Element();
            removeAllInTree(f, result.asList());
            return result;
        }

        public int retainAllInTree(Filter f, Collection<Object> sink) {
            return removeAllInTree(not(f), sink);
        }

        public Element retainAllInTree(Filter f) {
            Element result = new Element();
            retainAllInTree(f, result.asList());
            return result;
        }

        public void replaceAllInTree(Filter f) {
            replaceAll(replaceInTree(f));
        }

        /** Raise a ClassCastException if any subelements are the wrong type. */
        public void checkPartsOnly(Class<?> elementClass) {
            for (int i = 0; i < size; i++) {
                elementClass.cast(parts[i]).getClass();
            }
        }

        /** Return true if all sub-elements are of the given type. */
        public boolean isPartsOnly(Class<?> elementClass) {
            for (int i = 0; i < size; i++) {
                if (!elementClass.isInstance(parts[i])) {
                    return false;
                }
            }
            return true;
        }

        /** Provides an iterable view of this object as a series of elements.
         *  All sub-elements of this Element must be of type Element.
         *  A ClassCastException is raised if there are non-Element sub-elements.
         */
        public <T> Iterable<T> partsOnly(Class<T> elementClass) {
            checkPartsOnly(elementClass);
            return (Iterable<T>) (Iterable) this;
        }

        public Iterable<Element> elements() {
            return partsOnly(Element.class);
        }

        /// Useful shorthands.
        // Finding or removing elements w/o regard to their type or content.
        public Element findElement() {
            return (Element) find(elementFilter());
        }

        public Element findAllElements() {
            return findAll(elementFilter());
        }

        public Element removeElement() {
            return (Element) remove(elementFilter());
        }

        public Element removeAllElements() {
            return (Element) removeAll(elementFilter());
        }

        // Finding or removing by element tag or selected attribute,
        // as if by elementFilter(name) or attrFilter(name, value).
        // Roughly akin to Common Lisp ASSOC.
        public Element findElement(String name) {
            return (Element) find(elementFilter(name));
        }

        public Element removeElement(String name) {
            return (Element) remove(elementFilter(name));
        }

        public Element findWithAttr(String key) {
            return (Element) find(attrFilter(name));
        }

        public Element findWithAttr(String key, String value) {
            return (Element) find(attrFilter(name, value));
        }

        public Element removeWithAttr(String key) {
            return (Element) remove(attrFilter(name));
        }

        public Element removeWithAttr(String key, String value) {
            return (Element) remove(attrFilter(name, value));
        }

        public Element findAllElements(String name) {
            return findAll(elementFilter(name));
        }

        public Element removeAllElements(String name) {
            return removeAll(elementFilter(name));
        }

        public Element retainAllElements(String name) {
            return retainAll(elementFilter(name));
        }

        public Element findAllWithAttr(String key) {
            return findAll(attrFilter(key));
        }

        public Element removeAllWithAttr(String key) {
            return removeAll(attrFilter(key));
        }

        public Element retainAllWithAttr(String key) {
            return retainAll(attrFilter(key));
        }

        public Element findAllWithAttr(String key, String value) {
            return findAll(attrFilter(key, value));
        }

        public Element removeAllWithAttr(String key, String value) {
            return removeAll(attrFilter(key, value));
        }

        public Element retainAllWithAttr(String key, String value) {
            return retainAll(attrFilter(key, value));
        }

        public int countAll(Filter f) {
            return findAll(f, null);
        }

        public int countAllElements() {
            return countAll(elementFilter());
        }

        public int countAllElements(String name) {
            return countAll(elementFilter(name));
        }

        public int countAllWithAttr(String key) {
            return countAll(attrFilter(name));
        }

        public int countAllWithAttr(String key, String value) {
            return countAll(attrFilter(key, value));
        }

        public int indexOf(Object e) {
            for (int i = 0; i < size; i++) {
                if (e.equals(parts[i])) {
                    return i;
                }
            }
            return -1;
        }

        public int lastIndexOf(Object e) {
            for (int i = size - 1; i >= 0; i--) {
                if (e.equals(parts[i])) {
                    return i;
                }
            }
            return -1;
        }

        /** Remove the first element matching the given filter.
         *  Return the filtered value.
         */
        public int indexOf(Filter f) {
            return indexOf(f, 0);
        }

        public int indexOf(Filter f, int fromIndex) {
            if (fromIndex < 0) {
                fromIndex = 0;
            }
            for (int i = fromIndex; i < size; i++) {
                Object x = f.filter(parts[i]);
                if (x != null) {
                    return i;
                }
            }
            return -1;
        }

        /** Remove the last element matching the given filter.
         *  Return the filtered value.
         */
        public int lastIndexOf(Filter f) {
            return lastIndexOf(f, size - 1);
        }

        public int lastIndexOf(Filter f, int fromIndex) {
            if (fromIndex >= size) {
                fromIndex = size - 1;
            }
            for (int i = fromIndex; i >= 0; i--) {
                Object x = f.filter(parts[i]);
                if (x != null) {
                    return i;
                }
            }
            return -1;
        }

        public boolean contains(Object e) {
            return indexOf(e) >= 0;
        }

        // attributes
        private int findOrCreateAttr(String key, boolean create) {
            key.toString();  // null check
            int attrBase = parts.length;
            for (int i = parts.length - 2; i >= size; i -= 2) {
                String akey = (String) parts[i + 0];
                if (akey == null) {
                    if (!create) {
                        return -1;
                    }
                    if (i == size) {
                        break;  // NEED_SLOP
                    }
                    parts[i + 0] = key;
                    //parts[i+1] = ""; //caller responsibility
                    return i;
                }
                attrBase = i;
                if (akey.equals(key)) {
                    return i;
                }
            }
            // If we fell through, we ran into an element part.
            // Therefore we have run out of empty slots.
            if (!create) {
                return -1;
            }
            assert (!isFrozen());
            int alen = parts.length - attrBase;
            expand(size, 2); // generally expands by more than 2
            // since there was a reallocation, the garbage slots are really null
            assert (parts[size + 0] == null && parts[size + 1] == null);
            alen += 2;
            int i = parts.length - alen;
            parts[i + 0] = key;
            //parts[i+1] = ""; //caller responsibility
            return i;
        }

        public int attrSize() {
            return attrLength() >>> 1;
        }

        public int indexOfAttr(String key) {
            return findOrCreateAttr(key, false);
        }

        public boolean containsAttr(String key) {
            return indexOfAttr(key) >= 0;
        }

        public String getAttr(String key) {
            return getAttr(key, null);
        }

        public String getAttr(String key, String dflt) {
            int i = findOrCreateAttr(key, false);
            return (i < 0) ? dflt : (String) parts[i + 1];
        }

        public TokenList getAttrList(String key) {
            return convertToList(getAttr(key));
        }

        public Number getAttrNumber(String key) {
            return convertToNumber(getAttr(key));
        }

        public long getAttrLong(String key) {
            return getAttrLong(key, 0);
        }

        public double getAttrDouble(String key) {
            return getAttrDouble(key, 0.0);
        }

        public long getAttrLong(String key, long dflt) {
            return convertToLong(getAttr(key), dflt);
        }

        public double getAttrDouble(String key, double dflt) {
            return convertToDouble(getAttr(key), dflt);
        }

        int indexAttr(int k) {
            int i = parts.length - (k * 2) - 2;
            if (i < size || parts[i] == null) {
                return -2;  // always oob
            }
            return i;
        }

        public boolean containsAttr(int k) {
            return indexAttr(k) >= 0;
        }

        public String getAttr(int k) {
            return (String) parts[indexAttr(k) + 1];
        }

        public String getAttrName(int k) {
            return (String) parts[indexAttr(k) + 0];
        }

        public Iterable<String> attrNames() {
            //return asAttrMap().keySet();
            return new Iterable<String>() {

                public Iterator<String> iterator() {
                    return new ANItr();
                }
            };
        }

        // Hand-inlined replacement for asAttrMap().keySet().iterator():
        class ANItr implements Iterator<String> {

            boolean lastRet;
            int cursor = -2;  // pointer from end of parts

            public boolean hasNext() {
                int i = cursor + parts.length;
                return i >= size && parts[i] == null;
            }

            public String next() {
                int i = cursor + parts.length;
                Object x;
                if (i < size || (x = parts[i]) == null) {
                    nsee();
                    return null;
                }
                cursor -= 2;
                lastRet = true;
                return (String) x;
            }

            public void remove() {
                if (!lastRet) {
                    throw new IllegalStateException();
                }
                Element.this.removeAttr((-4 - cursor) / 2);
                cursor += 2;
                lastRet = false;
            }

            Exception nsee() {
                throw new NoSuchElementException("attribute " + (-2 - cursor) / 2);
            }
        }

        /** Return an anonymous copy of self, but only with attributes.
         */
        public Element copyAttrsOnly() {
            int alen = attrLength();
            Element attrs = new Element(alen);
            Object[] attrParts = attrs.parts;
            assert (attrParts.length == NEED_SLOP + alen);
            System.arraycopy(parts, parts.length - alen,
                    attrParts, NEED_SLOP,
                    alen);
            return attrs;
        }

        /** Get all attributes, represented as an element with sub-elements.
         *  The name of each sub-element is the attribute key, and the text
         *  This is a fresh copy, and can be updated with affecting the original.
         *  of each sub-element is the corresponding attribute value.
         *  See also asAttrMap() for a "live" view of all the attributes as a Map.
         */
        public Element getAttrs() {
            int asize = attrSize();
            Element attrs = new Element(ANON_NAME, asize, NEED_SLOP + asize);
            for (int i = 0; i < asize; i++) {
                Element attr = new Element(getAttrName(i), 1, NEED_SLOP + 1);
                // %%% normalize attrs to token lists?
                attr.setRaw(0, getAttr(i));
                attrs.setRaw(i, attr);
            }
            return attrs;
        }

        public void setAttrs(Element attrs) {
            int alen = attrLength();
            clearParts(parts.length - alen, alen);
            if (!hasNulls(NEED_SLOP + attrs.size * 2)) {
                expand(size, attrs.size * 2);
            }
            addAttrs(attrs);
        }

        public void addAttrs(Element attrs) {
            for (int i = 0; i < attrs.size; i++) {
                Element attr = (Element) attrs.get(i);
                setAttr(attr.name, attr.getText().toString());
            }
        }

        public void removeAttr(int i) {
            checkNotFrozen();
            while ((i -= 2) >= size) {
                Object k = parts[i + 0];
                Object v = parts[i + 1];
                if (k == null) {
                    break;
                }
                parts[i + 2] = k;
                parts[i + 3] = v;
            }
            parts[i + 2] = null;
            parts[i + 3] = null;
        }

        public void clearAttrs() {
            if (parts.length == 0 || parts[parts.length - 1] == null) {
                return;  // no attrs to clear
            }
            checkNotFrozen();
            if (size == 0) {
                // If no elements, free the parts array.
                parts = noPartsNotFrozen;
                return;
            }
            for (int i = parts.length - 1; parts[i] != null; i--) {
                assert (i >= size);
                parts[i] = null;
            }
        }

        public String setAttr(String key, String value) {
            String old;
            if (value == null) {
                int i = findOrCreateAttr(key, false);
                if (i >= 0) {
                    old = (String) parts[i + 1];
                    removeAttr(i);
                } else {
                    old = null;
                }
            } else {
                checkNotFrozen();
                int i = findOrCreateAttr(key, true);
                old = (String) parts[i + 1];
                parts[i + 1] = value;
            }
            return old;
        }

        public String setAttrList(String key, List<String> l) {
            if (l == null) {
                return setAttr(key, null);
            }
            if (!(l instanceof TokenList)) {
                l = new TokenList(l);
            }
            return setAttr(key, l.toString());
        }

        public String setAttrNumber(String key, Number n) {
            return setAttr(key, (n == null) ? null : n.toString());
        }

        public String setAttrLong(String key, long n) {
            return setAttr(key, (n == 0) ? null : String.valueOf(n));
        }

        public String setAttrDouble(String key, double n) {
            return setAttr(key, (n == 0) ? null : String.valueOf(n));
        }

        public String setAttr(int k, String value) {
            int i = indexAttr(k);
            String old = (String) parts[i + 1];
            if (value == null) {
                removeAttr(i);
            } else {
                checkNotFrozen();
                parts[i + 1] = value;
            }
            return old;
        }

        int attrLength() {
            return parts.length - attrBase();
        }

        /** Are the attributes of the two two elements equal?
         *  Disregards name, sub-elements, and ordering of attributes.
         */
        public boolean equalAttrs(Element that) {
            int alen = this.attrLength();
            if (alen != that.attrLength()) {
                return false;
            }
            if (alen == 0) {
                return true;
            }
            return compareAttrs(alen, that, alen, false) == 0;
        }

        private int compareAttrs(int thisAlen,
                Element that, int thatAlen,
                boolean fullCompare) {
            Object[] thisParts = this.parts;
            Object[] thatParts = that.parts;
            int thisBase = thisParts.length - thisAlen;
            int thatBase = thatParts.length - thatAlen;
            // search indexes into unmatched parts of this.attrs:
            int firstI = 0;
            // search indexes into unmatched parts of that.attrs:
            int firstJ = 0;
            int lastJ = thatAlen - 2;
            // try to find the mismatch with the first key:
            String firstKey = null;
            int firstKeyValCmp = 0;
            int foundKeys = 0;
            for (int i = 0; i < thisAlen; i += 2) {
                String key = (String) thisParts[thisBase + i + 0];
                String val = (String) thisParts[thisBase + i + 1];
                String otherVal = null;
                for (int j = firstJ; j <= lastJ; j += 2) {
                    if (key.equals(thatParts[thatBase + j + 0])) {
                        foundKeys += 1;
                        otherVal = (String) thatParts[thatBase + j + 1];
                        // Optimization:  Narrow subsequent searches when easy.
                        if (j == lastJ) {
                            lastJ -= 2;
                        } else if (j == firstJ) {
                            firstJ += 2;
                        }
                        if (i == firstI) {
                            firstI += 2;
                        }
                        break;
                    }
                }
                int valCmp;
                if (otherVal != null) {
                    // The key was found.
                    if (!fullCompare) {
                        if (!val.equals(otherVal)) {
                            return 1 - 0; //arb.
                        }
                        continue;
                    }
                    valCmp = val.compareTo(otherVal);
                } else {
                    // Found the key in this but not that.
                    // Such a mismatch puts the guy missing the key last.
                    valCmp = 0 - 1;
                }
                if (valCmp != 0) {
                    // found a mismatch, key present in both elems
                    if (firstKey == null
                            || firstKey.compareTo(key) > 0) {
                        // found a better key
                        firstKey = key;
                        firstKeyValCmp = valCmp;
                    }
                }
            }
            // We have located the first mismatch of all keys in this.attrs.
            // In general we must also look for keys in that.attrs but missing
            // from this.attrs; such missing keys, if earlier than firstKey,
            // rule the comparison.

            // We can sometimes prove quickly there is no missing key.
            if (foundKeys == thatAlen / 2) {
                // Exhausted all keys in that.attrs.
                return firstKeyValCmp;
            }

            // Search for a missing key in that.attrs earlier than firstKey.
            findMissingKey:
            for (int j = firstJ; j <= lastJ; j += 2) {
                String otherKey = (String) thatParts[thatBase + j + 0];
                if (firstKey == null
                        || firstKey.compareTo(otherKey) > 0) {
                    // Found a better key; is it missing?
                    for (int i = firstI; i < thisAlen; i += 2) {
                        if (otherKey.equals(thisParts[thisBase + i + 0])) {
                            continue findMissingKey;
                        }
                    }
                    // If we get here, there was no match in this.attrs.
                    return 1 - 0;
                }
            }

            // No missing key.  Previous comparison value rules.
            return firstKeyValCmp;
        }

        // Binary search looking for first non-null after size.
        int attrBase() {
            // Smallest & largest possible attribute indexes:
            int kmin = 0;
            int kmax = (parts.length - size) >>> 1;
            // earlist possible attribute position:
            int abase = parts.length - (kmax * 2);
            // binary search using scaled indexes:
            while (kmin != kmax) {
                int kmid = kmin + ((kmax - kmin) >>> 1);
                if (parts[abase + (kmid * 2)] == null) {
                    kmin = kmid + 1;
                } else {
                    kmax = kmid;
                }
                assert (kmin <= kmax);
            }
            return abase + (kmax * 2);
        }

        /** Sort attributes by name. */
        public void sortAttrs() {
            checkNotFrozen();
            int abase = attrBase();
            int alen = parts.length - abase;
            String[] buf = new String[alen];
            // collect keys
            for (int k = 0; k < alen / 2; k++) {
                String akey = (String) parts[abase + (k * 2) + 0];
                buf[k] = akey;
            }
            Arrays.sort(buf, 0, alen / 2);
            // collect values
            for (int k = 0; k < alen / 2; k++) {
                String akey = buf[k];
                buf[k + alen / 2] = getAttr(akey);
            }
            // reorder keys and values
            int fillp = parts.length;
            for (int k = 0; k < alen / 2; k++) {
                String akey = buf[k];
                String aval = buf[k + alen / 2];
                fillp -= 2;
                parts[fillp + 0] = akey;
                parts[fillp + 1] = aval;
            }
            assert (fillp == abase);
        }

        /*
        Notes on whitespace and tokenization.
        On input, never split CDATA blocks.  They remain single tokens.
        ?Try to treat encoded characters as CDATA-quoted, also?

        Internally, each String sub-element is logically a token.
        However, if there was no token-splitting on input,
        consecutive strings are merged by the parser.

        Internally, we need addToken (intervening blank) and addText
        (hard concatenation).

        Optionally on input, tokenize unquoted text into words.
        Between each adjacent word pair, elide either one space
        or all space.

        On output, we always add spaces between tokens.
        The Element("a", {"b", "c", Element("d"), "e    f"})
        outputs as "<a>b c<d/>e    f</a>"
         */
        /** Split strings into tokens, using a StringTokenizer. */
        public void tokenize(String delims, boolean returnDelims) {
            checkNotFrozen();
            if (delims == null) {
                delims = WHITESPACE_CHARS;  // StringTokenizer default
            }
            for (int i = 0; i < size; i++) {
                if (!(parts[i] instanceof CharSequence)) {
                    continue;
                }
                int osize = size;
                String str = parts[i].toString();
                StringTokenizer st = new StringTokenizer(str, delims, returnDelims);
                int nstrs = st.countTokens();
                switch (nstrs) {
                    case 0:
                        close(i--, 1);
                        break;
                    case 1:
                        parts[i] = st.nextToken();
                        break;
                    default:
                        openOrExpand(i + 1, nstrs - 1);
                        for (int j = 0; j < nstrs; j++) {
                            parts[i + j] = st.nextToken();
                        }
                        i += nstrs - 1;
                        break;
                }
            }
        }

        public void tokenize(String delims) {
            tokenize(delims, false);
        }

        public void tokenize() {
            tokenize(null, false);
        }

        // views
        class LView extends AbstractList<Object> {

            Element asElement() {
                return Element.this;
            }

            public int size() {
                return Element.this.size();
            }

            public Object get(int i) {
                return Element.this.get(i);
            }

            @Override
            public boolean contains(Object e) {
                return Element.this.contains(e);
            }

            @Override
            public Object[] toArray() {
                return Element.this.toArray();
            }

            @Override
            public int indexOf(Object e) {
                return Element.this.indexOf(e);
            }

            @Override
            public int lastIndexOf(Object e) {
                return Element.this.lastIndexOf(e);
            }

            @Override
            public void add(int i, Object e) {
                ++modCount;
                Element.this.add(i, e);
            }

            @Override
            public boolean addAll(int i, Collection<? extends Object> c) {
                ++modCount;
                return Element.this.addAll(i, c) > 0;
            }

            @Override
            public boolean addAll(Collection<? extends Object> c) {
                ++modCount;
                return Element.this.addAll(c) > 0;
            }

            @Override
            public Object remove(int i) {
                ++modCount;
                return Element.this.remove(i);
            }

            @Override
            public Object set(int i, Object e) {
                ++modCount;
                return Element.this.set(i, e);
            }

            @Override
            public void clear() {
                ++modCount;
                Element.this.clear();
            }
            // Others: toArray(Object[]), containsAll, removeAll, retainAll
        }

        /** Produce a list view of sub-elements.
         *  (The list view does not provide access to the element's
         *  name or attributes.)
         *  Changes to this view are immediately reflected in the
         *  element itself.
         */
        public List<Object> asList() {
            return new LView();
        }

        /** Produce a list iterator on all sub-elements. */
        public ListIterator<Object> iterator() {
            //return asList().listIterator();
            return new Itr();
        }

        // Hand-inlined replacement for LView.listIterator():
        class Itr implements ListIterator<Object> {

            int lastRet = -1;
            int cursor = 0;

            public boolean hasNext() {
                return cursor < size;
            }

            public boolean hasPrevious() {
                return cursor > 0 && cursor <= size;
            }

            public Object next() {
                if (!hasNext()) {
                    nsee();
                }
                return parts[lastRet = cursor++];
            }

            public Object previous() {
                if (!hasPrevious()) {
                    nsee();
                }
                return parts[--cursor];
            }

            public int nextIndex() {
                return cursor;
            }

            public int previousIndex() {
                return cursor - 1;
            }

            public void set(Object x) {
                parts[lastRet] = x;
            }

            public void add(Object x) {
                lastRet = -1;
                Element.this.add(cursor++, x);
            }

            public void remove() {
                if (lastRet < 0) {
                    throw new IllegalStateException();
                }
                Element.this.remove(lastRet);
                if (lastRet < cursor) {
                    --cursor;
                }
                lastRet = -1;
            }

            void nsee() {
                throw new NoSuchElementException("element " + cursor);
            }
        }

        /** A PrintWriter which always appends as if by addText.
         *  Use of this stream may insert a StringBuffer at the end
         *  of the Element.  The user must not directly modify this
         *  StringBuffer, or use it in other data structures.
         *  From time to time, the StringBuffer may be replaced by a
         *  constant string as a result of using the PrintWriter.
         */
        public PrintWriter asWriter() {
            return new ElemW();
        }

        class ElemW extends PrintWriter {

            ElemW() {
                super(new StringWriter());
            }
            final StringBuffer buf = ((StringWriter) out).getBuffer();

            {
                lock = buf;
            }  // synchronize on this buffer

            @Override
            public void println() {
                synchronized (buf) {
                    ensureCursor();
                    super.println();
                }
            }

            @Override
            public void write(int ch) {
                synchronized (buf) {
                    ensureCursor();
                    //buf.append(ch);
                    super.write(ch);
                }
            }

            @Override
            public void write(char buf[], int off, int len) {
                synchronized (buf) {
                    ensureCursor();
                    super.write(buf, off, len);
                }
            }

            @Override
            public void write(String s, int off, int len) {
                synchronized (buf) {
                    ensureCursor();
                    //buf.append(s.substring(off, off+len));
                    super.write(s, off, len);
                }
            }

            @Override
            public void write(String s) {
                synchronized (buf) {
                    ensureCursor();
                    //buf.append(s);
                    super.write(s);
                }
            }

            private void ensureCursor() {
                checkNotFrozen();
                if (getLast() != buf) {
                    int pos = indexOf(buf);
                    if (pos >= 0) {
                        // Freeze the pre-existing use of buf.
                        setRaw(pos, buf.toString());
                    }
                    add(buf);
                }
            }
        }

        /** Produce a map view of attributes, in which the attribute
         *  name strings are the keys.
         *  (The map view does not provide access to the element's
         *  name or sub-elements.)
         *  Changes to this view are immediately reflected in the
         *  element itself.
         */
        public Map<String, String> asAttrMap() {
            class Entry implements Map.Entry<String, String> {

                final int k;

                Entry(int k) {
                    this.k = k;
                    assert (((String) getKey()).toString() != null);  // check, fail-fast
                }

                public String getKey() {
                    return Element.this.getAttrName(k);
                }

                public String getValue() {
                    return Element.this.getAttr(k);
                }

                public String setValue(String v) {
                    return Element.this.setAttr(k, v.toString());
                }

                @Override
                public boolean equals(Object o) {
                    if (!(o instanceof Map.Entry)) {
                        return false;
                    }
                    Map.Entry that = (Map.Entry) o;
                    return (this.getKey().equals(that.getKey())
                            && this.getValue().equals(that.getValue()));
                }

                @Override
                public int hashCode() {
                    return getKey().hashCode() ^ getValue().hashCode();
                }
            }
            class EIter implements Iterator<Map.Entry<String, String>> {

                int k = 0;  // index of pending next() attribute

                public boolean hasNext() {
                    return Element.this.containsAttr(k);
                }

                public Map.Entry<String, String> next() {
                    return new Entry(k++);
                }

                public void remove() {
                    Element.this.removeAttr(--k);
                }
            }
            class ESet extends AbstractSet<Map.Entry<String, String>> {

                public int size() {
                    return Element.this.attrSize();
                }

                public Iterator<Map.Entry<String, String>> iterator() {
                    return new EIter();
                }

                @Override
                public void clear() {
                    Element.this.clearAttrs();
                }
            }
            class AView extends AbstractMap<String, String> {

                private transient Set<Map.Entry<String, String>> eSet;

                public Set<Map.Entry<String, String>> entrySet() {
                    if (eSet == null) {
                        eSet = new ESet();
                    }
                    return eSet;
                }

                @Override
                public int size() {
                    return Element.this.attrSize();
                }

                public boolean containsKey(String k) {
                    return Element.this.containsAttr(k);
                }

                public String get(String k) {
                    return Element.this.getAttr(k);
                }

                @Override
                public String put(String k, String v) {
                    return Element.this.setAttr(k, v.toString());
                }

                public String remove(String k) {
                    return Element.this.setAttr(k, null);
                }
            }
            return new AView();
        }

        /** Reports number of additional elements this object can accommodate
         *  without reallocation.
         */
        public int getExtraCapacity() {
            int abase = attrBase();
            return Math.max(0, abase - size - NEED_SLOP);
        }

        /** Ensures that at least the given number of additional elements
         *  can be added to this object without reallocation.
         */
        public void ensureExtraCapacity(int cap) {
            if (cap == 0 || hasNulls(cap + NEED_SLOP)) {
                return;
            }
            setExtraCapacity(cap);
        }

        /**
         * Trim excess capacity to zero, or do nothing if frozen.
         * This minimizes the space occupied by this Element,
         * at the expense of a reallocation if sub-elements or attributes
         * are added later.
         */
        public void trimToSize() {
            if (isFrozen()) {
                return;
            }
            setExtraCapacity(0);
        }

        /** Changes the number of additional elements this object can accommodate
         *  without reallocation.
         */
        public void setExtraCapacity(int cap) {
            checkNotFrozen();
            int abase = attrBase();
            int alen = parts.length - abase;  // slots allocated for attrs
            int nlen = size + cap + NEED_SLOP + alen;
            if (nlen != parts.length) {
                Object[] nparts = new Object[nlen];
                // copy attributes
                System.arraycopy(parts, abase, nparts, nlen - alen, alen);
                // copy sub-elements
                System.arraycopy(parts, 0, nparts, 0, size);
                parts = nparts;
            }
            assert (cap == getExtraCapacity());
        }

        // Return true if there are at least len nulls of slop available.
        boolean hasNulls(int len) {
            if (len == 0) {
                return true;
            }
            int lastNull = size + len - 1;
            if (lastNull >= parts.length) {
                return false;
            }
            return (parts[lastNull] == null);
        }

        // Opens up parts array at pos by len spaces.
        void open(int pos, int len) {
            assert (pos < size);
            assert (hasNulls(len + NEED_SLOP));
            checkNotFrozen();
            int nsize = size + len;
            int tlen = size - pos;
            System.arraycopy(parts, pos, parts, pos + len, tlen);
            size = nsize;
        }

        // Reallocate and open up at parts[pos] to at least len empty places.
        // Shift anything after pos right by len.  Reallocate if necessary.
        // If pos < size, caller must fill it in with non-null values.
        // Returns incremented size; caller is responsible for storing it
        // down, if desired.
        int expand(int pos, int len) {
            assert (pos <= size);
            // There must be at least len nulls between elems and attrs.
            assert (!hasNulls(NEED_SLOP + len));  // caller responsibility
            checkNotFrozen();
            int nsize = size + len;  // length of all elements
            int tlen = size - pos;   // length of elements in post-pos tail
            int abase = attrBase();
            int alen = parts.length - abase;  // slots allocated for attrs
            int nlen = nsize + alen + NEED_SLOP;
            nlen += (nlen >>> 1);  // add new slop!
            Object[] nparts = new Object[nlen];
            // copy head of sub-elements
            System.arraycopy(parts, 0, nparts, 0, pos);
            // copy tail of sub-elements
            System.arraycopy(parts, pos, nparts, pos + len, tlen);
            // copy attributes
            System.arraycopy(parts, abase, nparts, nlen - alen, alen);
            // update self
            parts = nparts;
            //assert(hasNulls(len));  <- not yet true, since size != nsize
            return nsize;
        }

        // Open or expand at the given position, as appropriate.
        boolean openOrExpand(int pos, int len) {
            if (pos < 0 || pos > size) {
                badIndex(pos);
            }
            if (hasNulls(len + NEED_SLOP)) {
                if (pos == size) {
                    size += len;
                } else {
                    open(pos, len);
                }
                return false;
            } else {
                size = expand(pos, len);
                return true;
            }
        }

        // Close up at parts[pos] len old places.
        // Shift anything after pos left by len.
        // Fill unused end of parts with null.
        void close(int pos, int len) {
            assert (len > 0);
            assert ((size - pos) >= len);
            checkNotFrozen();
            int tlen = (size - pos) - len;   // length of elements in post-pos tail
            int nsize = size - len;
            System.arraycopy(parts, pos + len, parts, pos, tlen);
            // reinitialize the unoccupied slots to null
            clearParts(nsize, nsize + len);
            // update self
            size = nsize;
            assert (hasNulls(len));
        }

        public void writeTo(Writer w) throws IOException {
            new Printer(w).print(this);
        }

        public void writePrettyTo(Writer w) throws IOException {
            prettyPrintTo(w, this);
        }

        public String prettyString() {
            StringWriter sw = new StringWriter();
            try {
                writePrettyTo(sw);
            } catch (IOException ee) {
                throw new Error(ee);  // should not happen
            }
            return sw.toString();
        }

        @Override
        public String toString() {
            StringWriter sw = new StringWriter();
            try {
                writeTo(sw);
            } catch (IOException ee) {
                throw new Error(ee);  // should not happen
            }
            return sw.toString();
        }

        public String dump() {
            // For debugging only.  Reveals internal layout.
            StringBuilder buf = new StringBuilder();
            buf.append("<").append(name).append("[").append(size).append("]");
            for (int i = 0; i < parts.length; i++) {
                Object p = parts[i];
                if (p == null) {
                    buf.append(" null");
                } else {
                    buf.append(" {");
                    String cname = p.getClass().getName();
                    cname = cname.substring(1 + cname.indexOf('/'));
                    cname = cname.substring(1 + cname.indexOf('$'));
                    cname = cname.substring(1 + cname.indexOf('#'));
                    if (!cname.equals("String")) {
                        buf.append(cname).append(":");
                    }
                    buf.append(p);
                    buf.append("}");
                }
            }
            return buf.append(">").toString();
        }

        public static java.lang.reflect.Method method(String name) {
            HashMap allM = allMethods;
            if (allM == null) {
                allM = makeAllMethods();
            }
            java.lang.reflect.Method res = (java.lang.reflect.Method) allMethods.get(name);
            if (res == null) {
                throw new IllegalArgumentException(name);
            }
            return res;
        }
        private static HashMap allMethods;

        private static synchronized HashMap makeAllMethods() {
            if (allMethods != null) {
                return allMethods;
            }
            java.lang.reflect.Method[] methods = Element.class.getMethods();
            HashMap<String, java.lang.reflect.Method> allM = new HashMap<String, java.lang.reflect.Method>(),
                    ambig = new HashMap<String, java.lang.reflect.Method>();
            for (int i = 0; i < methods.length; i++) {
                java.lang.reflect.Method m = methods[i];
                Class[] args = m.getParameterTypes();
                String name = m.getName();
                assert (java.lang.reflect.Modifier.isPublic(m.getModifiers()));
                if (name.startsWith("notify")) {
                    continue;
                }
                if (name.endsWith("Attr")
                        && args.length > 0 && args[0] == int.class) // ignore getAttr(int), etc.
                {
                    continue;
                }
                if (name.endsWith("All")
                        && args.length > 1 && args[0] == Filter.class) // ignore findAll(Filter, int...), etc.
                {
                    continue;
                }
                java.lang.reflect.Method pm = allM.put(name, m);
                if (pm != null) {
                    Class[] pargs = pm.getParameterTypes();
                    if (pargs.length > args.length) {
                        allM.put(name, pm);   // put it back
                    } else if (pargs.length == args.length) {
                        ambig.put(name, pm);  // make a note of it
                    }
                }
            }
            // Delete ambiguous methods.
            for (Map.Entry<String, java.lang.reflect.Method> e : ambig.entrySet()) {
                String name = e.getKey();
                java.lang.reflect.Method pm = e.getValue();
                java.lang.reflect.Method m = allM.get(name);
                Class[] args = m.getParameterTypes();
                Class[] pargs = pm.getParameterTypes();
                if (pargs.length == args.length) {
                    //System.out.println("ambig: "+pm);
                    //System.out.println(" with: "+m);
                    //ambig: int addAll(int,Element)
                    // with: int addAll(int,Collection)
                    allM.put(name, null);  // get rid of
                }
            }
            //System.out.println("allM: "+allM);
            return allMethods = allM;
        }
    }

    static Object fixupString(Object part) {
        if (part instanceof CharSequence && !(part instanceof String)) {
            return part.toString();
        } else {
            return part;
        }
    }

    public static final class Special implements Comparable<Special> {

        String kind;
        Object value;

        public Special(String kind, Object value) {
            this.kind = kind;
            this.value = value;
        }

        public String getKind() {
            return kind;
        }

        public Object getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Special)) {
                return false;
            }
            Special that = (Special) o;
            return this.kind.equals(that.kind) && this.value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return kind.hashCode() * 65 + value.hashCode();
        }

        public int compareTo(Special that) {
            int r = this.kind.compareTo(that.kind);
            if (r != 0) {
                return r;
            }
            return ((Comparable) this.value).compareTo(that.value);
        }

        @Override
        public String toString() {
            int split = kind.indexOf(' ');
            String pref = kind.substring(0, split < 0 ? 0 : split);
            String post = kind.substring(split + 1);
            return pref + value + post;
        }
    }

    /** Supports sorting of mixed content.  Sorts strings first,
     *  then Elements, then everything else (as Comparable).
     */
    public static Comparator<Object> contentOrder() {
        return CONTENT_ORDER;
    }
    private static Comparator<Object> CONTENT_ORDER = new ContentComparator();

    private static class ContentComparator implements Comparator<Object> {

        public int compare(Object o1, Object o2) {
            boolean cs1 = (o1 instanceof CharSequence);
            boolean cs2 = (o2 instanceof CharSequence);
            if (cs1 && cs2) {
                String s1 = (String) fixupString(o1);
                String s2 = (String) fixupString(o2);
                return s1.compareTo(s2);
            }
            if (cs1) {
                return 0 - 1;
            }
            if (cs2) {
                return 1 - 0;
            }
            boolean el1 = (o1 instanceof Element);
            boolean el2 = (o2 instanceof Element);
            if (el1 && el2) {
                return ((Element) o1).compareTo((Element) o2);
            }
            if (el1) {
                return 0 - 1;
            }
            if (el2) {
                return 1 - 0;
            }
            return ((Comparable) o1).compareTo(o2);
        }
    }

    /** Used to find, filter, or transform sub-elements.
     *  When used as a predicate, the filter returns a null
     *  value for false, and the original object value for true.
     *  When used as a transformer, the filter may return
     *  null, for no values, the original object, a new object,
     *  or an anonymous Element (meaning multiple results).
     */
    public interface Filter {

        Object filter(Object value);
    }

    /** Use this to find an element, perhaps with a given name. */
    public static class ElementFilter implements Filter {

        /** Subclasses may override this to implement better value tests.
         *  By default, it returns the element itself, thus recognizing
         *  all elements, regardless of name.
         */
        public Element filter(Element elem) {
            return elem;  // override this
        }

        public final Object filter(Object value) {
            if (!(value instanceof Element)) {
                return null;
            }
            return filter((Element) value);
        }

        @Override
        public String toString() {
            return "<ElementFilter name='*'/>";
        }
    }
    private static Filter elementFilter;

    public static Filter elementFilter() {
        return (elementFilter != null) ? elementFilter : (elementFilter = new ElementFilter());
    }

    public static Filter elementFilter(final String name) {
        name.toString();  // null check
        return new ElementFilter() {

            @Override
            public Element filter(Element elem) {
                return name.equals(elem.name) ? elem : null;
            }

            @Override
            public String toString() {
                return "<ElementFilter name='" + name + "'/>";
            }
        };
    }

    public static Filter elementFilter(final Collection nameSet) {
        nameSet.getClass();  // null check
        return new ElementFilter() {

            @Override
            public Element filter(Element elem) {
                return nameSet.contains(elem.name) ? elem : null;
            }

            @Override
            public String toString() {
                return "<ElementFilter name='" + nameSet + "'/>";
            }
        };
    }

    public static Filter elementFilter(String... nameSet) {
        Collection<String> ncoll = Arrays.asList(nameSet);
        if (nameSet.length > 10) {
            ncoll = new HashSet<String>(ncoll);
        }
        return elementFilter(ncoll);
    }

    /** Use this to find an element with a named attribute,
     *  possibly with a particular value.
     *  (Note that an attribute is missing if and only if its value is null.)
     */
    public static class AttrFilter extends ElementFilter {

        protected final String attrName;

        public AttrFilter(String attrName) {
            this.attrName = attrName.toString();
        }

        /** Subclasses may override this to implement better value tests.
         *  By default, it returns true for any non-null value, thus
         *  recognizing any attribute of the given name, regardless of value.
         */
        public boolean test(String attrVal) {
            return attrVal != null;  // override this
        }

        @Override
        public final Element filter(Element elem) {
            return test(elem.getAttr(attrName)) ? elem : null;
        }

        @Override
        public String toString() {
            return "<AttrFilter name='" + attrName + "' value='*'/>";
        }
    }

    public static Filter attrFilter(String attrName) {
        return new AttrFilter(attrName);
    }

    public static Filter attrFilter(String attrName, final String attrVal) {
        if (attrVal == null) {
            return not(attrFilter(attrName));
        }
        return new AttrFilter(attrName) {

            @Override
            public boolean test(String attrVal2) {
                return attrVal.equals(attrVal2);
            }

            @Override
            public String toString() {
                return "<AttrFilter name='" + attrName + "' value='" + attrVal + "'/>";
            }
        };
    }

    public static Filter attrFilter(Element matchThis, String attrName) {
        return attrFilter(attrName, matchThis.getAttr(attrName));
    }

    /** Use this to find a sub-element of a given class. */
    public static Filter classFilter(final Class clazz) {
        return new Filter() {

            public Object filter(Object value) {
                return clazz.isInstance(value) ? value : null;
            }

            @Override
            public String toString() {
                return "<ClassFilter class='" + clazz.getName() + "'/>";
            }
        };
    }
    private static Filter textFilter;

    public static Filter textFilter() {
        return (textFilter != null) ? textFilter : (textFilter = classFilter(CharSequence.class));
    }
    private static Filter specialFilter;

    public static Filter specialFilter() {
        return (specialFilter != null) ? specialFilter : (specialFilter = classFilter(Special.class));
    }
    private static Filter selfFilter;

    /** This filter always returns its own argument. */
    public static Filter selfFilter() {
        if (selfFilter != null) {
            return selfFilter;
        }
        return selfFilter = new Filter() {

            public Object filter(Object value) {
                return value;
            }

            @Override
            public String toString() {
                return "<Self/>";
            }
        };
    }

    /** This filter always returns a fixed value, regardless of argument. */
    public static Filter constantFilter(final Object value) {
        return new Filter() {

            public Object filter(Object ignore) {
                return value;
            }

            @Override
            public String toString() {
                return "<Constant>" + value + "</Constant>";
            }
        };
    }
    private static Filter nullFilter;

    public static Filter nullFilter() {
        return (nullFilter != null) ? nullFilter : (nullFilter = constantFilter(null));
    }
    private static Filter emptyFilter;

    public static Filter emptyFilter() {
        return (emptyFilter != null) ? emptyFilter : (emptyFilter = constantFilter(Element.EMPTY));
    }

    /** Use this to invert the logical sense of the given filter. */
    public static Filter not(final Filter f) {
        return new Filter() {

            public Object filter(Object value) {
                return f.filter(value) == null ? value : null;
            }

            @Override
            public String toString() {
                return "<Not>" + f + "</Not>";
            }
        };
    }

    /** Use this to combine several filters with logical AND.
     *  Returns either the first null or the last non-null value.
     */
    public static Filter and(final Filter f0, final Filter f1) {
        return and(new Filter[]{f0, f1});
    }

    public static Filter and(final Filter... fs) {
        switch (fs.length) {
            case 0:
                return selfFilter();  // always true (on non-null inputs)
            case 1:
                return fs[0];
        }
        return new Filter() {

            public Object filter(Object value) {
                Object res = fs[0].filter(value);
                if (res != null) {
                    res = fs[1].filter(value);
                    for (int i = 2; res != null && i < fs.length; i++) {
                        res = fs[i].filter(value);
                    }
                }
                return res;
            }

            @Override
            public String toString() {
                return opToString("<And>", fs, "</And>");
            }
        };
    }

    /** Use this to combine several filters with logical OR.
     *  Returns either the first non-null or the last null value.
     */
    public static Filter or(final Filter f0, final Filter f1) {
        return or(new Filter[]{f0, f1});
    }

    public static Filter or(final Filter... fs) {
        switch (fs.length) {
            case 0:
                return nullFilter();
            case 1:
                return fs[0];
        }
        return new Filter() {

            public Object filter(Object value) {
                Object res = fs[0].filter(value);
                if (res == null) {
                    res = fs[1].filter(value);
                    for (int i = 2; res == null && i < fs.length; i++) {
                        res = fs[i].filter(value);
                    }
                }
                return res;
            }

            @Override
            public String toString() {
                return opToString("<Or>", fs, "</Or>");
            }
        };
    }

    /** Use this to combine several filters with logical AND,
     *  and where each non-null result is passed as the argument
     *  to the next filter.
     *  Returns either the first null or the last non-null value.
     */
    public static Filter stack(final Filter f0, final Filter f1) {
        return stack(new Filter[]{f0, f1});
    }

    public static Filter stack(final Filter... fs) {
        switch (fs.length) {
            case 0:
                return nullFilter();
            case 1:
                return fs[0];
        }
        return new Filter() {

            public Object filter(Object value) {
                Object res = fs[0].filter(value);
                if (res != null) {
                    res = fs[1].filter(res);
                    for (int i = 2; res != null && i < fs.length; i++) {
                        res = fs[i].filter(res);
                    }
                }
                return res;
            }

            @Override
            public String toString() {
                return opToString("<Stack>", fs, "</Stack>");
            }
        };
    }

    /** Copy everything produced by f to sink, using addContent. */
    public static Filter content(final Filter f, final Collection<Object> sink) {
        return new Filter() {

            public Object filter(Object value) {
                Object res = f.filter(value);
                addContent(res, sink);
                return res;
            }

            @Override
            public String toString() {
                return opToString("<addContent>", new Object[]{f, sink},
                        "</addContent>");
            }
        };
    }

    /** Look down the tree using f, collecting fx, else recursing into x.
     *  Identities:
     *  <code>
     *     findInTree(f, s) == findInTree(content(f, s))
     *     findInTree(f)    == replaceInTree(and(f, selfFilter())).
     *  </code>
     */
    public static Filter findInTree(Filter f, Collection<Object> sink) {
        if (sink != null) {
            f = content(f, sink);
        }
        return findInTree(f);
    }

    /** Look down the tree using f, recursing into x unless fx. */
    public static Filter findInTree(final Filter f) {
        return new Filter() {

            public Object filter(Object value) {
                Object res = f.filter(value);
                if (res != null) {
                    return res;
                }
                if (value instanceof Element) {
                    // recurse
                    return ((Element) value).find(this);
                }
                return null;
            }

            @Override
            public String toString() {
                return opToString("<FindInTree>", new Object[]{f},
                        "</FindInTree>");
            }
        };
    }

    /** Look down the tree using f.  Replace each x with fx, else recurse.
     *  If post filter g is given, optionally replace with gx after recursion.
     */
    public static Filter replaceInTree(final Filter f, final Filter g) {
        return new Filter() {

            public Object filter(Object value) {
                Object res = (f == null) ? null : f.filter(value);
                if (res != null) {
                    return res;
                }
                if (value instanceof Element) {
                    // recurse
                    ((Element) value).replaceAll(this);
                    // Optional postorder traversal:
                    if (g != null) {
                        res = g.filter(value);
                    }
                }
                return res;  // usually null, meaning no replacement
            }

            @Override
            public String toString() {
                return opToString("<ReplaceInTree>",
                        new Object[]{f, g},
                        "</ReplaceInTree>");
            }
        };
    }

    public static Filter replaceInTree(Filter f) {
        f.getClass(); // null check
        return replaceInTree(f, null);
    }

    /** Make a filter which calls this method on the given element.
     *  If the method is static, the first argument is passed the
     *  the subtree value being filtered.
     *  If the method is non-static, the receiver is the subtree value itself.
     *  <p>
     *  Optionally, additional arguments may be specified.
     *  <p>
     *  If the filtered value does not match the receiver class
     *  (or else the first argument type, if the method is static),
     *  the filter returns null without invoking the method.
     *  <p>
     *  The returned filter value is the result returned from the method.
     *  Optionally, a non-null special false result value may be specified.
     *  If the result returned from the method is equal to that false value,
     *  the filter will return null.
     */
    public static Filter methodFilter(java.lang.reflect.Method m, Object[] extraArgs,
            Object falseResult) {
        return methodFilter(m, false, extraArgs, falseResult);
    }

    public static Filter methodFilter(java.lang.reflect.Method m,
            Object[] args) {
        return methodFilter(m, args, null);
    }

    public static Filter methodFilter(java.lang.reflect.Method m) {
        return methodFilter(m, null, null);
    }

    public static Filter testMethodFilter(java.lang.reflect.Method m, Object[] extraArgs,
            Object falseResult) {
        return methodFilter(m, true, extraArgs, falseResult);
    }

    public static Filter testMethodFilter(java.lang.reflect.Method m, Object[] extraArgs) {
        return methodFilter(m, true, extraArgs, zeroArgs.get(m.getReturnType()));
    }

    public static Filter testMethodFilter(java.lang.reflect.Method m) {
        return methodFilter(m, true, null, zeroArgs.get(m.getReturnType()));
    }

    private static Filter methodFilter(final java.lang.reflect.Method m,
            final boolean isTest,
            Object[] extraArgs, final Object falseResult) {
        Class[] params = m.getParameterTypes();
        final boolean isStatic = java.lang.reflect.Modifier.isStatic(m.getModifiers());
        int insertLen = (isStatic ? 1 : 0);
        if (insertLen + (extraArgs == null ? 0 : extraArgs.length) > params.length) {
            throw new IllegalArgumentException("too many arguments");
        }
        final Object[] args = (params.length == insertLen) ? null
                : new Object[params.length];
        final Class valueType = !isStatic ? m.getDeclaringClass() : params[0];
        if (valueType.isPrimitive()) {
            throw new IllegalArgumentException("filtered value must be reference type");
        }
        int fillp = insertLen;
        if (extraArgs != null) {
            for (int i = 0; i < extraArgs.length; i++) {
                args[fillp++] = extraArgs[i];
            }
        }
        if (args != null) {
            while (fillp < args.length) {
                Class param = params[fillp];
                args[fillp++] = param.isPrimitive() ? zeroArgs.get(param) : null;
            }
        }
        final Thread curt = Thread.currentThread();
        class MFilt implements Filter {

            public Object filter(Object value) {
                if (!valueType.isInstance(value)) {
                    return null;  // filter fails quickly
                }
                Object[] args1 = args;
                if (isStatic) {
                    if (args1 == null) {
                        args1 = new Object[1];
                    } else if (curt != Thread.currentThread()) // Dirty hack to curtail array copying in common case.
                    {
                        args1 = (Object[]) args1.clone();
                    }
                    args1[0] = value;
                }
                Object res;
                try {
                    res = m.invoke(value, args1);
                } catch (java.lang.reflect.InvocationTargetException te) {
                    Throwable ee = te.getCause();
                    if (ee instanceof RuntimeException) {
                        throw (RuntimeException) ee;
                    }
                    if (ee instanceof Error) {
                        throw (Error) ee;
                    }
                    throw new RuntimeException("throw in filter", ee);
                } catch (IllegalAccessException ee) {
                    throw new RuntimeException("access error in filter", ee);
                }
                if (res == null) {
                    if (!isTest && m.getReturnType() == Void.TYPE) {
                        // Void methods return self by convention.
                        // (But void "tests" always return false.)
                        res = value;
                    }
                } else {
                    if (falseResult != null && falseResult.equals(res)) {
                        res = null;
                    } else if (isTest) {
                        // Tests return self by convention.
                        res = value;
                    }
                }
                return res;
            }

            @Override
            public String toString() {
                return "<Method>" + m + "</Method>";
            }
        }
        return new MFilt();
    }
    private static HashMap<Class, Object> zeroArgs = new HashMap<Class, Object>();

    static {
        zeroArgs.put(Boolean.TYPE, Boolean.FALSE);
        zeroArgs.put(Character.TYPE, new Character((char) 0));
        zeroArgs.put(Byte.TYPE, new Byte((byte) 0));
        zeroArgs.put(Short.TYPE, new Short((short) 0));
        zeroArgs.put(Integer.TYPE, new Integer(0));
        zeroArgs.put(Float.TYPE, new Float(0));
        zeroArgs.put(Long.TYPE, new Long(0));
        zeroArgs.put(Double.TYPE, new Double(0));
    }

    private static String opToString(String s1, Object[] s2, String s3) {
        StringBuilder buf = new StringBuilder(s1);
        for (int i = 0; i < s2.length; i++) {
            if (s2[i] != null) {
                buf.append(s2[i]);
            }
        }
        buf.append(s3);
        return buf.toString();
    }

    /** Call the filter on each list element x, and replace x with the
     *  resulting filter value e, or its parts.
     *  If e is null, keep x.  (This eases use of partial-domain filters.)
     *  If e is a TokenList or an anonymous Element, add e's parts
     *  to the list instead of x.
     *  Otherwise, replace x by e.
     *  <p>
     *  The effect at each list position <code>n</code> may be expressed
     *  in terms of XMLKit.addContent as follows:
     *  <pre>
     *     Object e = f.filter(target.get(n));
     *     if (e != null) {
     *         target.remove(n);
     *         addContent(e, target.subList(n,n));
     *     }
     *  </pre>
     *  <p>
     *  Note:  To force deletion of x, simply have the filter return
     *  Element.EMPTY or TokenList.EMPTY.
     *  To force null filter values to have this effect,
     *  use the expression: <code>or(f, emptyFilter())</code>.
     */
    public static void replaceAll(Filter f, List<Object> target) {
        for (ListIterator<Object> i = target.listIterator(); i.hasNext();) {
            Object x = i.next();
            Object fx = f.filter(x);
            if (fx == null) {
                // Unliked addContent, a null is a no-op here.
                // i.remove();
            } else if (fx instanceof TokenList) {
                TokenList tl = (TokenList) fx;
                if (tl.size() == 1) {
                    i.set(tl);
                } else {
                    i.remove();
                    for (String part : tl) {
                        i.add(part);
                    }
                }
            } else if (fx instanceof Element
                    && ((Element) fx).isAnonymous()) {
                Element anon = (Element) fx;
                if (anon.size() == 1) {
                    i.set(anon);
                } else {
                    i.remove();
                    for (Object part : anon) {
                        i.add(part);
                    }
                }
            } else if (x != fx) {
                i.set(fx);
            }
        }
    }

    /** If e is null, return zero.
     *  If e is a TokenList or an anonymous Element, add e's parts
     *  to the collection, and return the number of parts.
     *  Otherwise, add e to the collection, and return one.
     *  If the collection reference is null, the result is as if
     *  a throwaway collection were used.
     */
    public static int addContent(Object e, Collection<Object> sink) {
        if (e == null) {
            return 0;
        } else if (e instanceof TokenList) {
            TokenList tl = (TokenList) e;
            if (sink != null) {
                sink.addAll(tl);
            }
            return tl.size();
        } else if (e instanceof Element
                && ((Element) e).isAnonymous()) {
            Element anon = (Element) e;
            if (sink != null) {
                sink.addAll(anon.asList());
            }
            return anon.size();
        } else {
            if (sink != null) {
                sink.add(e);
            }
            return 1;
        }
    }

    static Collection<Object> newCounterColl() {
        return new AbstractCollection<Object>() {

            int size;

            public int size() {
                return size;
            }

            @Override
            public boolean add(Object o) {
                ++size;
                return true;
            }

            public Iterator<Object> iterator() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /** SAX2 document handler for building Element trees. */
    private static class Builder implements ContentHandler, LexicalHandler {
        /*, EntityResolver, DTDHandler, ErrorHandler*/

        Collection<Object> sink;
        boolean makeFrozen;
        boolean tokenizing;

        Builder(Collection<Object> sink, boolean tokenizing, boolean makeFrozen) {
            this.sink = sink;
            this.tokenizing = tokenizing;
            this.makeFrozen = makeFrozen;
        }
        Object[] parts = new Object[30];
        int nparts = 0;
        int[] attrBases = new int[10];  // index into parts
        int[] elemBases = new int[10];  // index into parts
        int depth = -1;  // index into attrBases, elemBases
        // Parts is organized this way:
        // | name0 | akey aval ... | subelem ... | name1 | ... |
        // The position of the first "akey" after name0 is attrBases[0].
        // The position of the first "subelem" after name0 is elemBases[0].
        // The position after the last part is always nparts.
        int mergeableToken = -1;  // index into parts of recent CharSequence
        boolean inCData = false;

        void addPart(Object x) {
            //System.out.println("addPart "+x);
            if (nparts == parts.length) {
                Object[] newParts = new Object[parts.length * 2];
                System.arraycopy(parts, 0, newParts, 0, parts.length);
                parts = newParts;
            }
            parts[nparts++] = x;
        }

        Object getMergeableToken() {
            if (mergeableToken == nparts - 1) {
                assert (parts[mergeableToken] instanceof CharSequence);
                return parts[nparts - 1];
            } else {
                return null;
            }
        }

        void clearMergeableToken() {
            if (mergeableToken >= 0) {
                // Freeze temporary StringBuffers into strings.
                assert (parts[mergeableToken] instanceof CharSequence);
                parts[mergeableToken] = parts[mergeableToken].toString();
                mergeableToken = -1;
            }
        }

        void setMergeableToken() {
            if (mergeableToken != nparts - 1) {
                clearMergeableToken();
                mergeableToken = nparts - 1;
                assert (parts[mergeableToken] instanceof CharSequence);
            }
        }

        // ContentHandler callbacks
        public void startElement(String ns, String localName, String name, Attributes atts) {
            clearMergeableToken();
            addPart(name.intern());
            ++depth;
            if (depth == attrBases.length) {
                int oldlen = depth;
                int newlen = depth * 2;
                int[] newAB = new int[newlen];
                int[] newEB = new int[newlen];
                System.arraycopy(attrBases, 0, newAB, 0, oldlen);
                System.arraycopy(elemBases, 0, newEB, 0, oldlen);
                attrBases = newAB;
                elemBases = newEB;
            }
            attrBases[depth] = nparts;
            // Collect attributes.
            int na = atts.getLength();
            for (int k = 0; k < na; k++) {
                addPart(atts.getQName(k).intern());
                addPart(atts.getValue(k));
            }
            // Get ready to collect elements.
            elemBases[depth] = nparts;
        }

        public void endElement(String ns, String localName, String name) {
            assert (depth >= 0);
            clearMergeableToken();
            int ebase = elemBases[depth];
            int elen = nparts - ebase;
            int abase = attrBases[depth];
            int alen = ebase - abase;
            int nbase = abase - 1;
            int cap = alen + (makeFrozen ? 0 : NEED_SLOP) + elen;
            Element e = new Element((String) parts[nbase], elen, cap);
            // Set up attributes.
            for (int k = 0; k < alen; k += 2) {
                e.parts[cap - k - 2] = parts[abase + k + 0];
                e.parts[cap - k - 1] = parts[abase + k + 1];
            }
            // Set up sub-elements.
            System.arraycopy(parts, ebase, e.parts, 0, elen);
            // Back out of this level.
            --depth;
            nparts = nbase;
            assert (e.isFrozen() == makeFrozen);
            assert (e.size() == elen);
            assert (e.attrSize() * 2 == alen);
            if (depth >= 0) {
                addPart(e);
            } else {
                sink.add(e);
            }
        }

        public void startCDATA() {
            inCData = true;
        }

        public void endCDATA() {
            inCData = false;
        }

        public void characters(char[] buf, int off, int len) {
            boolean headSpace = false;
            boolean tailSpace = false;
            int firstLen;
            if (tokenizing && !inCData) {
                // Strip unquoted blanks.
                while (len > 0 && isWhitespace(buf[off])) {
                    headSpace = true;
                    ++off;
                    --len;
                }
                if (len == 0) {
                    tailSpace = true;  // it is all space
                }
                while (len > 0 && isWhitespace(buf[off + len - 1])) {
                    tailSpace = true;
                    --len;
                }
                firstLen = 0;
                while (firstLen < len && !isWhitespace(buf[off + firstLen])) {
                    ++firstLen;
                }
            } else {
                firstLen = len;
            }
            if (headSpace) {
                clearMergeableToken();
            }
            boolean mergeAtEnd = !tailSpace;
            // If buffer was empty, or had only ignorable blanks, do nothing.
            if (len == 0) {
                return;
            }
            // Decide whether to merge some of these chars into a previous token.
            Object prev = getMergeableToken();
            if (prev instanceof StringBuffer) {
                ((StringBuffer) prev).append(buf, off, firstLen);
            } else if (prev == null) {
                addPart(new String(buf, off, firstLen));
            } else {
                // Merge two strings.
                String prevStr = prev.toString();
                StringBuffer prevBuf = new StringBuffer(prevStr.length() + firstLen);
                prevBuf.append(prevStr);
                prevBuf.append(buf, off, firstLen);
                if (mergeAtEnd && len == firstLen) {
                    // Replace previous string with new StringBuffer.
                    parts[nparts - 1] = prevBuf;
                } else {
                    // Freeze it now.
                    parts[nparts - 1] = prevBuf.toString();
                }
            }
            off += firstLen;
            len -= firstLen;
            if (len > 0) {
                // Appended only the first token.
                clearMergeableToken();
                // Add the rest as separate parts.
                while (len > 0) {
                    while (len > 0 && isWhitespace(buf[off])) {
                        ++off;
                        --len;
                    }
                    int nextLen = 0;
                    while (nextLen < len && !isWhitespace(buf[off + nextLen])) {
                        ++nextLen;
                    }
                    assert (nextLen > 0);
                    addPart(new String(buf, off, nextLen));
                    off += nextLen;
                    len -= nextLen;
                }
            }
            if (mergeAtEnd) {
                setMergeableToken();
            }
        }

        public void ignorableWhitespace(char[] buf, int off, int len) {
            clearMergeableToken();
            if (false) {
                characters(buf, off, len);
                clearMergeableToken();
            }
        }

        public void comment(char[] buf, int off, int len) {
            addPart(new Special("<!-- -->", new String(buf, off, len)));
        }

        public void processingInstruction(String name, String instruction) {
            Element pi = new Element(name);
            pi.add(instruction);
            addPart(new Special("<? ?>", pi));
        }

        public void skippedEntity(String name) {
        }

        public void startDTD(String name, String publicId, String systemId) {
        }

        public void endDTD() {
        }

        public void startEntity(String name) {
        }

        public void endEntity(String name) {
        }

        public void setDocumentLocator(org.xml.sax.Locator locator) {
        }

        public void startDocument() {
        }

        public void endDocument() {
        }

        public void startPrefixMapping(String prefix, String uri) {
        }

        public void endPrefixMapping(String prefix) {
        }
    }

    /** Produce a ContentHandler for use with an XML parser.
     *  The object is <em>also</em> a LexicalHandler.
     *  Every top-level Element produced will get added to sink.
     *  All elements will be frozen iff makeFrozen is true.
     */
    public static ContentHandler makeBuilder(Collection<Object> sink, boolean tokenizing, boolean makeFrozen) {
        return new Builder(sink, tokenizing, makeFrozen);
    }

    public static ContentHandler makeBuilder(Collection<Object> sink, boolean tokenizing) {
        return new Builder(sink, tokenizing, false);
    }

    public static ContentHandler makeBuilder(Collection<Object> sink) {
        return makeBuilder(sink, false, false);
    }

    public static Element readFrom(Reader in, boolean tokenizing, boolean makeFrozen) throws IOException {
        Element sink = new Element();
        ContentHandler b = makeBuilder(sink.asList(), tokenizing, makeFrozen);
        XMLReader parser;
        try {
            parser = org.xml.sax.helpers.XMLReaderFactory.createXMLReader();
        } catch (SAXException ee) {
            throw new Error(ee);
        }
        //parser.setFastStandalone(true);
        parser.setContentHandler(b);
        try {
            parser.setProperty("http://xml.org/sax/properties/lexical-handler",
                    (LexicalHandler) b);
        } catch (SAXException ee) {
            // Ignore.  We will miss the comments and whitespace.
        }
        try {
            parser.parse(new InputSource(in));
        } catch (SAXParseException ee) {
            throw new RuntimeException("line " + ee.getLineNumber() + " col " + ee.getColumnNumber() + ": ", ee);
        } catch (SAXException ee) {
            throw new RuntimeException(ee);
        }
        switch (sink.size()) {
            case 0:
                return null;
            case 1:
                if (sink.get(0) instanceof Element) {
                    return (Element) sink.get(0);
                }
            // fall through
            default:
                if (makeFrozen) {
                    sink.shallowFreeze();
                }
                return sink;
        }
    }

    public static Element readFrom(Reader in, boolean tokenizing) throws IOException {
        return readFrom(in, tokenizing, false);
    }

    public static Element readFrom(Reader in) throws IOException {
        return readFrom(in, false, false);
    }

    public static void prettyPrintTo(OutputStream out, Element e) throws IOException {
        prettyPrintTo(new OutputStreamWriter(out), e);
    }

    public static void prettyPrintTo(Writer out, Element e) throws IOException {
        Printer pr = new Printer(out);
        pr.pretty = true;
        pr.print(e);
    }

    static class Outputter {

        ContentHandler ch;
        LexicalHandler lh;

        Outputter(ContentHandler ch, LexicalHandler lh) {
            this.ch = ch;
            this.lh = lh;
        }
        AttributesImpl atts = new AttributesImpl();  // handy

        void output(Object x) throws SAXException {
            // Cf. jdom.org/jdom-b8/src/java/org/jdom/output/SAXOutputter.java
            if (x instanceof Element) {
                Element e = (Element) x;
                atts.clear();
                for (int asize = e.attrSize(), k = 0; k < asize; k++) {
                    String key = e.getAttrName(k);
                    String val = e.getAttr(k);
                    atts.addAttribute("", "", key, "CDATA", val);
                }
                ch.startElement("", "", e.getName(), atts);
                for (int i = 0; i < e.size(); i++) {
                    output(e.get(i));
                }
                ch.endElement("", "", e.getName());
            } else if (x instanceof Special) {
                Special sp = (Special) x;
                if (sp.kind.startsWith("<!--")) {
                    char[] chars = sp.value.toString().toCharArray();
                    lh.comment(chars, 0, chars.length);
                } else if (sp.kind.startsWith("<?")) {
                    Element nameInstr = (Element) sp.value;
                    ch.processingInstruction(nameInstr.name,
                            nameInstr.get(0).toString());
                } else {
                    // drop silently
                }
            } else {
                char[] chars = x.toString().toCharArray();
                ch.characters(chars, 0, chars.length);
            }
        }
    }

    public static class Printer {

        public Writer w;
        public boolean tokenizing;
        public boolean pretty;
        public boolean abbreviated;  // nonstandard format cuts down on noise
        int depth = 0;
        boolean prevStr;
        int tabStop = 2;

        public Printer(Writer w) {
            this.w = w;
        }

        public Printer() {
            StringWriter sw = new StringWriter();
            this.w = sw;

        }

        public String nextString() {
            StringBuffer sb = ((StringWriter) w).getBuffer();
            String next = sb.toString();
            sb.setLength(0);  // reset
            return next;
        }

        void indent(int depth) throws IOException {
            if (depth > 0) {
                w.write("\n");
            }
            int nsp = tabStop * depth;
            while (nsp > 0) {
                String s = "                ";
                String t = s.substring(0, nsp < s.length() ? nsp : s.length());
                w.write(t);
                nsp -= t.length();
            }
        }

        public void print(Element e) throws IOException {
            if (e.isAnonymous()) {
                printParts(e);
                return;
            }
            printRecursive(e);
        }

        public void println(Element e) throws IOException {
            print(e);
            w.write("\n");
            w.flush();
        }

        public void printRecursive(Element e) throws IOException {
            boolean indented = false;
            if (pretty && !prevStr && e.size() + e.attrSize() > 0) {
                indent(depth);
                indented = true;
            }
            w.write("<");
            w.write(e.name);
            for (int asize = e.attrSize(), k = 0; k < asize; k++) {
                String key = e.getAttrName(k);
                String val = e.getAttr(k);
                w.write(" ");
                w.write(key);
                w.write("=");
                if (val == null) {
                    w.write("null");  // Should not happen....
                } else if (val.indexOf("\"") < 0) {
                    w.write("\"");
                    writeToken(val, '"', w);
                    w.write("\"");
                } else {
                    w.write("'");
                    writeToken(val, '\'', w);
                    w.write("'");
                }
            }
            if (e.size() == 0) {
                w.write("/>");
            } else {
                ++depth;
                if (abbreviated) {
                    w.write("/");
                } else {
                    w.write(">");
                }
                prevStr = false;
                printParts(e);
                if (abbreviated) {
                    w.write(">");
                } else {
                    if (indented && !prevStr) {
                        indent(depth - 1);
                    }
                    w.write("</");
                    w.write(e.name);
                    w.write(">");
                }
                prevStr = false;
                --depth;
            }
        }

        private void printParts(Element e) throws IOException {
            for (int i = 0; i < e.size(); i++) {
                Object x = e.get(i);
                if (x instanceof Element) {
                    printRecursive((Element) x);
                    prevStr = false;
                } else if (x instanceof Special) {
                    w.write(((Special) x).toString());
                    prevStr = false;
                } else {
                    String s = String.valueOf(x);
                    if (pretty) {
                        s = s.trim();
                        if (s.length() == 0) {
                            continue;
                        }
                    }
                    if (prevStr) {
                        w.write(' ');
                    }
                    writeToken(s, tokenizing ? ' ' : (char) -1, w);
                    prevStr = true;
                }
                if (pretty && depth == 0) {
                    w.write("\n");
                    prevStr = false;
                }
            }
        }
    }

    public static void output(Object e, ContentHandler ch, LexicalHandler lh) throws SAXException {
        new Outputter(ch, lh).output(e);
    }

    public static void output(Object e, ContentHandler ch) throws SAXException {
        if (ch instanceof LexicalHandler) {
            output(e, ch, (LexicalHandler) ch);
        } else {
            output(e, ch, null);
        }
    }

    public static void writeToken(String val, char quote, Writer w) throws IOException {
        int len = val.length();
        boolean canUseCData = (quote != '"' && quote != '\'');
        int vpos = 0;
        for (int i = 0; i < len; i++) {
            char ch = val.charAt(i);
            if ((ch == '<' || ch == '&' || ch == '>' || ch == quote)
                    || (quote == ' ' && isWhitespace(ch))) {
                if (canUseCData) {
                    assert (vpos == 0);
                    writeCData(val, w);
                    return;
                } else {
                    if (vpos < i) {
                        w.write(val, vpos, i - vpos);
                    }
                    String esc;
                    switch (ch) {
                        case '&':
                            esc = "&amp;";
                            break;
                        case '<':
                            esc = "&lt;";
                            break;
                        case '\'':
                            esc = "&apos;";
                            break;
                        case '"':
                            esc = "&quot;";
                            break;
                        case '>':
                            esc = "&gt;";
                            break;
                        default:
                            esc = "&#" + (int) ch + ";";
                            break;
                    }
                    w.write(esc);
                    vpos = i + 1;  // skip escaped char
                }
            }
        }
        // write the unquoted tail
        w.write(val, vpos, val.length() - vpos);
    }

    public static void writeCData(String val, Writer w) throws IOException {
        String begCData = "<![CDATA[";
        String endCData = "]]>";
        w.write(begCData);
        for (int vpos = 0, split;; vpos = split) {
            split = val.indexOf(endCData, vpos);
            if (split < 0) {
                w.write(val, vpos, val.length() - vpos);
                w.write(endCData);
                return;
            }
            split += 2; // bisect the "]]>" goo
            w.write(val, vpos, split - vpos);
            w.write(endCData);
            w.write(begCData);
        }
    }

    public static TokenList convertToList(String str) {
        if (str == null) {
            return null;
        }
        return new TokenList(str);
    }

    /** If str is null, empty, or blank, returns null.
     *  Otherwise, return a Double if str spells a double value and contains '.' or 'e'.
     *  Otherwise, return an Integer if str spells an int value.
     *  Otherwise, return a Long if str spells a long value.
     *  Otherwise, return a BigInteger for the string.
     *  Otherwise, throw NumberFormatException.
     */
    public static Number convertToNumber(String str) {
        if (str == null) {
            return null;
        }
        str = str.trim();
        if (str.length() == 0) {
            return null;
        }
        if (str.indexOf('.') >= 0
                || str.indexOf('e') >= 0
                || str.indexOf('E') >= 0) {
            return Double.valueOf(str);
        }
        try {
            long lval = Long.parseLong(str);
            if (lval == (int) lval) {
                // Narrow to Integer, if possible.
                return new Integer((int) lval);
            }
            return new Long(lval);
        } catch (NumberFormatException ee) {
            // Could not represent it as a long.
            return new java.math.BigInteger(str, 10);
        }
    }

    public static Number convertToNumber(String str, Number dflt) {
        Number n = convertToNumber(str);
        return (n == null) ? dflt : n;
    }

    public static long convertToLong(String str) {
        return convertToLong(str, 0);
    }

    public static long convertToLong(String str, long dflt) {
        Number n = convertToNumber(str);
        return (n == null) ? dflt : n.longValue();
    }

    public static double convertToDouble(String str) {
        return convertToDouble(str, 0);
    }

    public static double convertToDouble(String str, double dflt) {
        Number n = convertToNumber(str);
        return (n == null) ? dflt : n.doubleValue();
    }

    // Testing:
    public static void main(String... av) throws Exception {
        Element.method("getAttr");
        //new org.jdom.input.SAXBuilder().build(file).getRootElement();
        //jdom.org/jdom-b8/src/java/org/jdom/input/SAXBuilder.java
        //Document build(InputSource in) throws JDOMException

        int reps = 0;

        boolean tokenizing = false;
        boolean makeFrozen = false;
        if (av.length > 0) {
            tokenizing = true;
            try {
                reps = Integer.parseInt(av[0]);
            } catch (NumberFormatException ee) {
            }
        }
        Reader inR = new BufferedReader(new InputStreamReader(System.in));
        String inS = null;
        if (reps > 1) {
            StringWriter inBufR = new StringWriter(1 << 14);
            char[] cbuf = new char[1024];
            for (int nr; (nr = inR.read(cbuf)) >= 0;) {
                inBufR.write(cbuf, 0, nr);
            }
            inS = inBufR.toString();
            inR = new StringReader(inS);
        }
        Element e = XMLKit.readFrom(inR, tokenizing, makeFrozen);
        System.out.println("transform = " + e.findAll(methodFilter(Element.method("prettyString"))));
        System.out.println("transform = " + e.findAll(testMethodFilter(Element.method("hasText"))));
        long tm0 = 0;
        int warmup = 10;
        for (int i = 1; i < reps; i++) {
            inR = new StringReader(inS);
            readFrom(inR, tokenizing, makeFrozen);
            if (i == warmup) {
                System.out.println("Start timing...");
                tm0 = System.currentTimeMillis();
            }
        }
        if (tm0 != 0) {
            long tm1 = System.currentTimeMillis();
            System.out.println((reps - warmup) + " in " + (tm1 - tm0) + " ms");
        }
        System.out.println("hashCode = " + e.hashCode());
        String eStr = e.toString();
        System.out.println(eStr);
        Element e2 = readFrom(new StringReader(eStr), tokenizing, !makeFrozen);
        System.out.println("hashCode = " + e2.hashCode());
        if (!e.equals(e2)) {
            System.out.println("**** NOT EQUAL 1\n" + e2);
        }
        e = e.deepCopy();
        System.out.println("hashCode = " + e.hashCode());
        if (!e.equals(e2)) {
            System.out.println("**** NOT EQUAL 2");
        }
        e2.shallowFreeze();
        System.out.println("hashCode = " + e2.hashCode());
        if (!e.equals(e2)) {
            System.out.println("**** NOT EQUAL 3");
        }
        if (false) {
            System.out.println(e);
        } else {
            prettyPrintTo(new OutputStreamWriter(System.out), e);
        }
        System.out.println("Flat text:|" + e.getFlatText() + "|");
        {
            System.out.println("<!--- Sorted: --->");
            Element ce = e.copyContentOnly();
            ce.sort();
            prettyPrintTo(new OutputStreamWriter(System.out), ce);
        }
        {
            System.out.println("<!--- Trimmed: --->");
            Element tr = e.deepCopy();
            findInTree(testMethodFilter(Element.method("trimText"))).filter(tr);
            System.out.println(tr);
        }
        {
            System.out.println("<!--- Unstrung: --->");
            Element us = e.deepCopy();
            int nr = us.retainAllInTree(elementFilter(), null);
            System.out.println("nr=" + nr);
            System.out.println(us);
        }
        {
            System.out.println("<!--- Rollup: --->");
            Element ru = e.deepCopy();
            Filter makeAnonF =
                    methodFilter(Element.method("setName"),
                    new Object[]{ANON_NAME});
            Filter testSizeF =
                    testMethodFilter(Element.method("size"));
            Filter walk =
                    replaceInTree(and(not(elementFilter()), emptyFilter()),
                    and(testSizeF, makeAnonF));
            ru = (Element) walk.filter(ru);
            //System.out.println(ru);
            prettyPrintTo(new OutputStreamWriter(System.out), ru);
        }
    }

    static boolean isWhitespace(char c) {
        switch (c) {
            case 0x20:
            case 0x09:
            case 0x0D:
            case 0x0A:
                return true;
        }
        return false;
    }
}
