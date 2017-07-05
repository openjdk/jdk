/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.jar.Pack200;
import com.sun.java.util.jar.pack.Attribute.Layout;
import com.sun.java.util.jar.pack.ConstantPool.ClassEntry;
import com.sun.java.util.jar.pack.ConstantPool.DescriptorEntry;
import com.sun.java.util.jar.pack.ConstantPool.BootstrapMethodEntry;
import com.sun.java.util.jar.pack.ConstantPool.Index;
import com.sun.java.util.jar.pack.ConstantPool.LiteralEntry;
import com.sun.java.util.jar.pack.ConstantPool.Utf8Entry;
import com.sun.java.util.jar.pack.ConstantPool.Entry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import static com.sun.java.util.jar.pack.Constants.*;

/**
 * Define the main data structure transmitted by pack/unpack.
 * @author John Rose
 */
class Package {
    int verbose;
    {
        PropMap pmap = Utils.currentPropMap();
        if (pmap != null)
            verbose = pmap.getInteger(Utils.DEBUG_VERBOSE);
    }

    final int magic = JAVA_PACKAGE_MAGIC;

    int default_modtime = NO_MODTIME;
    int default_options = 0;  // FO_DEFLATE_HINT

    Version defaultClassVersion = null;

    // These fields can be adjusted by driver properties.
    final Version minClassVersion;
    final Version maxClassVersion;
    // null, indicates that consensus rules during package write
    final Version packageVersion;

    Version observedHighestClassVersion = null;


    // What constants are used in this unit?
    ConstantPool.IndexGroup cp = new ConstantPool.IndexGroup();

    /*
     * typically used by the PackageReader to set the defaults, in which
     * case we take the defaults.
     */
    public Package() {
        minClassVersion = JAVA_MIN_CLASS_VERSION;
        maxClassVersion = JAVA_MAX_CLASS_VERSION;
        packageVersion = null;
    }


    /*
     * Typically used by the PackerImpl during before packing, the defaults are
     * overridden by the users preferences.
     */
    public Package(Version minClassVersion, Version maxClassVersion, Version packageVersion) {
        // Fill in permitted range of major/minor version numbers.
        this.minClassVersion = minClassVersion == null
                ? JAVA_MIN_CLASS_VERSION
                : minClassVersion;
        this.maxClassVersion = maxClassVersion == null
                ? JAVA_MAX_CLASS_VERSION
                : maxClassVersion;
        this.packageVersion  = packageVersion;
    }


    public void reset() {
        cp = new ConstantPool.IndexGroup();
        classes.clear();
        files.clear();
        BandStructure.nextSeqForDebug = 0;
        observedHighestClassVersion = null;
    }

    // Special empty versions of Code and InnerClasses, used for markers.
    public static final Attribute.Layout attrCodeEmpty;
    public static final Attribute.Layout attrBootstrapMethodsEmpty;
    public static final Attribute.Layout attrInnerClassesEmpty;
    public static final Attribute.Layout attrSourceFileSpecial;
    public static final Map<Attribute.Layout, Attribute> attrDefs;
    static {
        Map<Layout, Attribute> ad = new HashMap<>(3);
        attrCodeEmpty = Attribute.define(ad, ATTR_CONTEXT_METHOD,
                                         "Code", "").layout();
        attrBootstrapMethodsEmpty = Attribute.define(ad, ATTR_CONTEXT_CLASS,
                                                     "BootstrapMethods", "").layout();
        attrInnerClassesEmpty = Attribute.define(ad, ATTR_CONTEXT_CLASS,
                                                 "InnerClasses", "").layout();
        attrSourceFileSpecial = Attribute.define(ad, ATTR_CONTEXT_CLASS,
                                                 "SourceFile", "RUNH").layout();
        attrDefs = Collections.unmodifiableMap(ad);
    }

    Version getDefaultClassVersion() {
        return defaultClassVersion;
    }

    /** Return the highest version number of all classes,
     *  or 0 if there are no classes.
     */
    private void setHighestClassVersion() {
        if (observedHighestClassVersion != null)
            return;
        Version res = JAVA_MIN_CLASS_VERSION;  // initial low value
        for (Class cls : classes) {
            Version ver = cls.getVersion();
            if (res.lessThan(ver))  res = ver;
        }
        observedHighestClassVersion = res;
    }

    Version getHighestClassVersion() {
        setHighestClassVersion();
        return observedHighestClassVersion;
    }

    // What Java classes are in this unit?

    ArrayList<Package.Class> classes = new ArrayList<>();

    public List<Package.Class> getClasses() {
        return classes;
    }

    public final
    class Class extends Attribute.Holder implements Comparable<Class> {
        public Package getPackage() { return Package.this; }

        // Optional file characteristics and data source (a "class stub")
        File file;

        // File header
        int magic;
        Version version;

        // Local constant pool (one-way mapping of index => package cp).
        Entry[] cpMap;

        // Class header
        //int flags;  // in Attribute.Holder.this.flags
        ClassEntry thisClass;
        ClassEntry superClass;
        ClassEntry[] interfaces;

        // Class parts
        ArrayList<Field> fields;
        ArrayList<Method> methods;
        //ArrayList attributes;  // in Attribute.Holder.this.attributes
        // Note that InnerClasses may be collected at the package level.
        ArrayList<InnerClass> innerClasses;
        ArrayList<BootstrapMethodEntry> bootstrapMethods;

        Class(int flags, ClassEntry thisClass, ClassEntry superClass, ClassEntry[] interfaces) {
            this.magic      = JAVA_MAGIC;
            this.version    = defaultClassVersion;
            this.flags      = flags;
            this.thisClass  = thisClass;
            this.superClass = superClass;
            this.interfaces = interfaces;

            boolean added = classes.add(this);
            assert(added);
        }

        Class(String classFile) {
            // A blank class; must be read with a ClassReader, etc.
            initFile(newStub(classFile));
        }

        List<Field> getFields() { return fields == null ? noFields : fields; }
        List<Method> getMethods() { return methods == null ? noMethods : methods; }

        public String getName() {
            return thisClass.stringValue();
        }

        Version getVersion() {
            return this.version;
        }

        // Note:  equals and hashCode are identity-based.
        public int compareTo(Class that) {
            String n0 = this.getName();
            String n1 = that.getName();
            return n0.compareTo(n1);
        }

        String getObviousSourceFile() {
            return Package.getObviousSourceFile(getName());
        }

        private void transformSourceFile(boolean minimize) {
            // Replace "obvious" SourceFile by null.
            Attribute olda = getAttribute(attrSourceFileSpecial);
            if (olda == null)
                return;  // no SourceFile attr.
            String obvious = getObviousSourceFile();
            List<Entry> ref = new ArrayList<>(1);
            olda.visitRefs(this, VRM_PACKAGE, ref);
            Utf8Entry sfName = (Utf8Entry) ref.get(0);
            Attribute a = olda;
            if (sfName == null) {
                if (minimize) {
                    // A pair of zero bytes.  Cannot use predef. layout.
                    a = Attribute.find(ATTR_CONTEXT_CLASS, "SourceFile", "H");
                    a = a.addContent(new byte[2]);
                } else {
                    // Expand null attribute to the obvious string.
                    byte[] bytes = new byte[2];
                    sfName = getRefString(obvious);
                    Object f = null;
                    f = Fixups.addRefWithBytes(f, bytes, sfName);
                    a = attrSourceFileSpecial.addContent(bytes, f);
                }
            } else if (obvious.equals(sfName.stringValue())) {
                if (minimize) {
                    // Replace by an all-zero attribute.
                    a = attrSourceFileSpecial.addContent(new byte[2]);
                } else {
                    assert(false);
                }
            }
            if (a != olda) {
                if (verbose > 2)
                    Utils.log.fine("recoding obvious SourceFile="+obvious);
                List<Attribute> newAttrs = new ArrayList<>(getAttributes());
                int where = newAttrs.indexOf(olda);
                newAttrs.set(where, a);
                setAttributes(newAttrs);
            }
        }

        void minimizeSourceFile() {
            transformSourceFile(true);
        }
        void expandSourceFile() {
            transformSourceFile(false);
        }

        protected Entry[] getCPMap() {
            return cpMap;
        }

        protected void setCPMap(Entry[] cpMap) {
            this.cpMap = cpMap;
        }

        boolean hasBootstrapMethods() {
            return bootstrapMethods != null && !bootstrapMethods.isEmpty();
        }

        List<BootstrapMethodEntry> getBootstrapMethods() {
            return bootstrapMethods;
        }

        BootstrapMethodEntry[] getBootstrapMethodMap() {
            return (hasBootstrapMethods())
                    ? bootstrapMethods.toArray(new BootstrapMethodEntry[bootstrapMethods.size()])
                    : null;
        }

        void setBootstrapMethods(Collection<BootstrapMethodEntry> bsms) {
            assert(bootstrapMethods == null);  // do not do this twice
            bootstrapMethods = new ArrayList<>(bsms);
        }

        boolean hasInnerClasses() {
            return innerClasses != null;
        }
        List<InnerClass> getInnerClasses() {
            return innerClasses;
        }

        public void setInnerClasses(Collection<InnerClass> ics) {
            innerClasses = (ics == null) ? null : new ArrayList<>(ics);
            // Edit the attribute list, if necessary.
            Attribute a = getAttribute(attrInnerClassesEmpty);
            if (innerClasses != null && a == null)
                addAttribute(attrInnerClassesEmpty.canonicalInstance());
            else if (innerClasses == null && a != null)
                removeAttribute(a);
        }

        /** Given a global map of ICs (keyed by thisClass),
         *  compute the subset of its Map.values which are
         *  required to be present in the local InnerClasses
         *  attribute.  Perform this calculation without
         *  reference to any actual InnerClasses attribute.
         *  <p>
         *  The order of the resulting list is consistent
         *  with that of Package.this.allInnerClasses.
         */
        public List<InnerClass> computeGloballyImpliedICs() {
            Set<Entry> cpRefs = new HashSet<>();
            {   // This block temporarily displaces this.innerClasses.
                ArrayList<InnerClass> innerClassesSaved = innerClasses;
                innerClasses = null;  // ignore for the moment
                visitRefs(VRM_CLASSIC, cpRefs);
                innerClasses = innerClassesSaved;
            }
            ConstantPool.completeReferencesIn(cpRefs, true);

            Set<Entry> icRefs = new HashSet<>();
            for (Entry e : cpRefs) {
                // Restrict cpRefs to InnerClasses entries only.
                if (!(e instanceof ClassEntry))  continue;
                // For every IC reference, add its outers also.
                while (e != null) {
                    InnerClass ic = getGlobalInnerClass(e);
                    if (ic == null)  break;
                    if (!icRefs.add(e))  break;
                    e = ic.outerClass;
                    // If we add A$B$C to the mix, we must also add A$B.
                }
            }
            // This loop is structured this way so as to accumulate
            // entries into impliedICs in an order which reflects
            // the order of allInnerClasses.
            ArrayList<InnerClass> impliedICs = new ArrayList<>();
            for (InnerClass ic : allInnerClasses) {
                // This one is locally relevant if it describes
                // a member of the current class, or if the current
                // class uses it somehow.  In the particular case
                // where thisClass is an inner class, it will already
                // be a member of icRefs.
                if (icRefs.contains(ic.thisClass)
                    || ic.outerClass == this.thisClass) {
                    // Add every relevant class to the IC attribute:
                    if (verbose > 1)
                        Utils.log.fine("Relevant IC: "+ic);
                    impliedICs.add(ic);
                }
            }
            return impliedICs;
        }

        // Helper for both minimizing and expanding.
        // Computes a symmetric difference.
        private List<InnerClass> computeICdiff() {
            List<InnerClass> impliedICs = computeGloballyImpliedICs();
            List<InnerClass> actualICs  = getInnerClasses();
            if (actualICs == null)
                actualICs = Collections.emptyList();

            // Symmetric difference is calculated from I, A like this:
            //  diff = (I+A) - (I*A)
            // Note that the center C is unordered, but the result
            // preserves the original ordering of I and A.
            //
            // Class file rules require that outers precede inners.
            // So, add I before A, in case A$B$Z is local, but A$B
            // is implicit.  The reverse is never the case.
            if (actualICs.isEmpty()) {
                return impliedICs;
                // Diff is I since A is empty.
            }
            if (impliedICs.isEmpty()) {
                return actualICs;
                // Diff is A since I is empty.
            }
            // (I*A) is non-trivial
            Set<InnerClass> center = new HashSet<>(actualICs);
            center.retainAll(new HashSet<>(impliedICs));
            impliedICs.addAll(actualICs);
            impliedICs.removeAll(center);
            // Diff is now I^A = (I+A)-(I*A).
            return impliedICs;
        }

        /** When packing, anticipate the effect of expandLocalICs.
         *  Replace the local ICs by their symmetric difference
         *  with the globally implied ICs for this class; if this
         *  difference is empty, remove the local ICs altogether.
         *  <p>
         *  An empty local IC attribute is reserved to signal
         *  the unpacker to delete the attribute altogether,
         *  so a missing local IC attribute signals the unpacker
         *  to use the globally implied ICs changed.
         */
        void minimizeLocalICs() {
            List<InnerClass> diff = computeICdiff();
            List<InnerClass> actualICs = innerClasses;
            List<InnerClass> localICs;  // will be the diff, modulo edge cases
            if (diff.isEmpty()) {
                // No diff, so transmit no attribute.
                localICs = null;
                if (actualICs != null && actualICs.isEmpty()) {
                    // Odd case:  No implied ICs, and a zero length attr.
                    // Do not support it directly.
                    if (verbose > 0)
                        Utils.log.info("Warning: Dropping empty InnerClasses attribute from "+this);
                }
            } else if (actualICs == null) {
                // No local IC attribute, even though some are implied.
                // Signal with trivial attribute.
                localICs = Collections.emptyList();
            } else {
                // Transmit a non-empty diff, which will create
                // a local ICs attribute.
                localICs = diff;
            }
            // Reduce the set to the symmetric difference.
            setInnerClasses(localICs);
            if (verbose > 1 && localICs != null)
                Utils.log.fine("keeping local ICs in "+this+": "+localICs);
        }

        /** When unpacking, undo the effect of minimizeLocalICs.
         *  Must return negative if any IC tuples may have been deleted.
         *  Otherwise, return positive if any IC tuples were added.
         */
        int expandLocalICs() {
            List<InnerClass> localICs = innerClasses;
            List<InnerClass> actualICs;
            int changed;
            if (localICs == null) {
                // Diff was empty.  (Common case.)
                List<InnerClass> impliedICs = computeGloballyImpliedICs();
                if (impliedICs.isEmpty()) {
                    actualICs = null;
                    changed = 0;
                } else {
                    actualICs = impliedICs;
                    changed = 1;  // added more tuples
                }
            } else if (localICs.isEmpty()) {
                // It was a non-empty diff, but the local ICs were absent.
                actualICs = null;
                changed = 0;  // [] => null, no tuple change
            } else {
                // Non-trivial diff was transmitted.
                actualICs = computeICdiff();
                // If we only added more ICs, return +1.
                changed = actualICs.containsAll(localICs)? +1: -1;
            }
            setInnerClasses(actualICs);
            return changed;
        }

        public abstract
        class Member extends Attribute.Holder implements Comparable<Member> {
            DescriptorEntry descriptor;

            protected Member(int flags, DescriptorEntry descriptor) {
                this.flags = flags;
                this.descriptor = descriptor;
            }

            public Class thisClass() { return Class.this; }

            public DescriptorEntry getDescriptor() {
                return descriptor;
            }
            public String getName() {
                return descriptor.nameRef.stringValue();
            }
            public String getType() {
                return descriptor.typeRef.stringValue();
            }

            protected Entry[] getCPMap() {
                return cpMap;
            }
            protected void visitRefs(int mode, Collection<Entry> refs) {
                if (verbose > 2)  Utils.log.fine("visitRefs "+this);
                // Careful:  The descriptor is used by the package,
                // but the classfile breaks it into component refs.
                if (mode == VRM_CLASSIC) {
                    refs.add(descriptor.nameRef);
                    refs.add(descriptor.typeRef);
                } else {
                    refs.add(descriptor);
                }
                // Handle attribute list:
                super.visitRefs(mode, refs);
            }

            public String toString() {
                return Class.this + "." + descriptor.prettyString();
            }
        }

        public
        class Field extends Member {
            // Order is significant for fields:  It is visible to reflection.
            int order;

            public Field(int flags, DescriptorEntry descriptor) {
                super(flags, descriptor);
                assert(!descriptor.isMethod());
                if (fields == null)
                    fields = new ArrayList<>();
                boolean added = fields.add(this);
                assert(added);
                order = fields.size();
            }

            public byte getLiteralTag() {
                return descriptor.getLiteralTag();
            }

            public int compareTo(Member o) {
                Field that = (Field)o;
                return this.order - that.order;
            }
        }

        public
        class Method extends Member {
            // Code attribute is specially hardwired.
            Code code;

            public Method(int flags, DescriptorEntry descriptor) {
                super(flags, descriptor);
                assert(descriptor.isMethod());
                if (methods == null)
                    methods = new ArrayList<>();
                boolean added = methods.add(this);
                assert(added);
            }

            public void trimToSize() {
                super.trimToSize();
                if (code != null)
                    code.trimToSize();
            }

            public int getArgumentSize() {
                int argSize  = descriptor.typeRef.computeSize(true);
                int thisSize = Modifier.isStatic(flags) ? 0 : 1;
                return thisSize + argSize;
            }

            // Sort methods in a canonical order (by type, then by name).
            public int compareTo(Member o) {
                Method that = (Method)o;
                return this.getDescriptor().compareTo(that.getDescriptor());
            }

            public void strip(String attrName) {
                if ("Code".equals(attrName))
                    code = null;
                if (code != null)
                    code.strip(attrName);
                super.strip(attrName);
            }
            protected void visitRefs(int mode, Collection<Entry> refs) {
                super.visitRefs(mode, refs);
                if (code != null) {
                    if (mode == VRM_CLASSIC) {
                        refs.add(getRefString("Code"));
                    }
                    code.visitRefs(mode, refs);
                }
            }
        }

        public void trimToSize() {
            super.trimToSize();
            for (int isM = 0; isM <= 1; isM++) {
                ArrayList<? extends Member> members = (isM == 0) ? fields : methods;
                if (members == null)  continue;
                members.trimToSize();
                for (Member m : members) {
                    m.trimToSize();
                }
            }
            if (innerClasses != null) {
                innerClasses.trimToSize();
            }
        }

        public void strip(String attrName) {
            if ("InnerClass".equals(attrName))
                innerClasses = null;
            for (int isM = 0; isM <= 1; isM++) {
                ArrayList<? extends Member> members = (isM == 0) ? fields : methods;
                if (members == null)  continue;
                for (Member m : members) {
                    m.strip(attrName);
                }
            }
            super.strip(attrName);
        }

        protected void visitRefs(int mode, Collection<Entry> refs) {
            if (verbose > 2)  Utils.log.fine("visitRefs "+this);
            refs.add(thisClass);
            refs.add(superClass);
            refs.addAll(Arrays.asList(interfaces));
            for (int isM = 0; isM <= 1; isM++) {
                ArrayList<? extends Member> members = (isM == 0) ? fields : methods;
                if (members == null)  continue;
                for (Member m : members) {
                    boolean ok = false;
                    try {
                        m.visitRefs(mode, refs);
                        ok = true;
                    } finally {
                        if (!ok)
                            Utils.log.warning("Error scanning "+m);
                    }
                }
            }
            visitInnerClassRefs(mode, refs);
            // Handle attribute list:
            super.visitRefs(mode, refs);
        }

        protected void visitInnerClassRefs(int mode, Collection<Entry> refs) {
            Package.visitInnerClassRefs(innerClasses, mode, refs);
        }

        // Hook called by ClassReader when it's done.
        void finishReading() {
            trimToSize();
            maybeChooseFileName();
        }

        public void initFile(File file) {
            assert(this.file == null);  // set-once
            if (file == null) {
                // Build a trivial stub.
                file = newStub(canonicalFileName());
            }
            this.file = file;
            assert(file.isClassStub());
            file.stubClass = this;
            maybeChooseFileName();
        }

        public void maybeChooseFileName() {
            if (thisClass == null) {
                return;  // do not choose yet
            }
            String canonName = canonicalFileName();
            if (file.nameString.equals("")) {
                file.nameString = canonName;
            }
            if (file.nameString.equals(canonName)) {
                // The file name is predictable.  Transmit "".
                file.name = getRefString("");
                return;
            }
            // If name has not yet been looked up, find it now.
            if (file.name == null) {
                file.name = getRefString(file.nameString);
            }
        }

        public String canonicalFileName() {
            if (thisClass == null)  return null;
            return thisClass.stringValue() + ".class";
        }

        public java.io.File getFileName(java.io.File parent) {
            String name = file.name.stringValue();
            if (name.equals(""))
                name = canonicalFileName();
            String fname = name.replace('/', java.io.File.separatorChar);
            return new java.io.File(parent, fname);
        }
        public java.io.File getFileName() {
            return getFileName(null);
        }

        public String toString() {
            return thisClass.stringValue();
        }
    }

    void addClass(Class c) {
        assert(c.getPackage() == this);
        boolean added = classes.add(c);
        assert(added);
        // Make sure the class is represented in the total file order:
        if (c.file == null)  c.initFile(null);
        addFile(c.file);
    }

    // What non-class files are in this unit?
    ArrayList<File> files = new ArrayList<>();

    public List<File> getFiles() {
        return files;
    }

    public List<File> getClassStubs() {
        List<File> classStubs = new ArrayList<>(classes.size());
        for (Class cls : classes) {
            assert(cls.file.isClassStub());
            classStubs.add(cls.file);
        }
        return classStubs;
    }

    public final class File implements Comparable<File> {
        String nameString;  // true name of this file
        Utf8Entry name;
        int modtime = NO_MODTIME;
        int options = 0;  // random flag bits, such as deflate_hint
        Class stubClass;  // if this is a stub, here's the class
        ArrayList<byte[]> prepend = new ArrayList<>();  // list of byte[]
        java.io.ByteArrayOutputStream append = new ByteArrayOutputStream();

        File(Utf8Entry name) {
            this.name = name;
            this.nameString = name.stringValue();
            // caller must fill in contents
        }
        File(String nameString) {
            nameString = fixupFileName(nameString);
            this.name = getRefString(nameString);
            this.nameString = name.stringValue();
        }

        public boolean isDirectory() {
            // JAR directory.  Useless.
            return nameString.endsWith("/");
        }
        public boolean isClassStub() {
            return (options & FO_IS_CLASS_STUB) != 0;
        }
        public Class getStubClass() {
            assert(isClassStub());
            assert(stubClass != null);
            return stubClass;
        }
        public boolean isTrivialClassStub() {
            return isClassStub()
                && name.stringValue().equals("")
                && (modtime == NO_MODTIME || modtime == default_modtime)
                && (options &~ FO_IS_CLASS_STUB) == 0;
        }

        // The nameString is the key.  Ignore other things.
        // (Note:  The name might be "", in the case of a trivial class stub.)
        public boolean equals(Object o) {
            if (o == null || (o.getClass() != File.class))
                return false;
            File that = (File)o;
            return that.nameString.equals(this.nameString);
        }
        public int hashCode() {
            return nameString.hashCode();
        }
        // Simple alphabetic sort.  PackageWriter uses a better comparator.
        public int compareTo(File that) {
            return this.nameString.compareTo(that.nameString);
        }
        public String toString() {
            return nameString+"{"
                +(isClassStub()?"*":"")
                +(BandStructure.testBit(options,FO_DEFLATE_HINT)?"@":"")
                +(modtime==NO_MODTIME?"":"M"+modtime)
                +(getFileLength()==0?"":"["+getFileLength()+"]")
                +"}";
        }

        public java.io.File getFileName() {
            return getFileName(null);
        }
        public java.io.File getFileName(java.io.File parent) {
            String lname = this.nameString;
            //if (name.startsWith("./"))  name = name.substring(2);
            String fname = lname.replace('/', java.io.File.separatorChar);
            return new java.io.File(parent, fname);
        }

        public void addBytes(byte[] bytes) {
            addBytes(bytes, 0, bytes.length);
        }
        public void addBytes(byte[] bytes, int off, int len) {
            if (((append.size() | len) << 2) < 0) {
                prepend.add(append.toByteArray());
                append.reset();
            }
            append.write(bytes, off, len);
        }
        public long getFileLength() {
            long len = 0;
            if (prepend == null || append == null)  return 0;
            for (byte[] block : prepend) {
                len += block.length;
            }
            len += append.size();
            return len;
        }
        public void writeTo(OutputStream out) throws IOException {
            if (prepend == null || append == null)  return;
            for (byte[] block : prepend) {
                out.write(block);
            }
            append.writeTo(out);
        }
        public void readFrom(InputStream in) throws IOException {
            byte[] buf = new byte[1 << 16];
            int nr;
            while ((nr = in.read(buf)) > 0) {
                addBytes(buf, 0, nr);
            }
        }
        public InputStream getInputStream() {
            InputStream in = new ByteArrayInputStream(append.toByteArray());
            if (prepend.isEmpty())  return in;
            List<InputStream> isa = new ArrayList<>(prepend.size()+1);
            for (byte[] bytes : prepend) {
                isa.add(new ByteArrayInputStream(bytes));
            }
            isa.add(in);
            return new SequenceInputStream(Collections.enumeration(isa));
        }

        protected void visitRefs(int mode, Collection<Entry> refs) {
            assert(name != null);
            refs.add(name);
        }
    }

    File newStub(String classFileNameString) {
        File stub = new File(classFileNameString);
        stub.options |= FO_IS_CLASS_STUB;
        stub.prepend = null;
        stub.append = null;  // do not collect data
        return stub;
    }

    private static String fixupFileName(String name) {
        String fname = name.replace(java.io.File.separatorChar, '/');
        if (fname.startsWith("/")) {
            throw new IllegalArgumentException("absolute file name "+fname);
        }
        return fname;
    }

    void addFile(File file) {
        boolean added = files.add(file);
        assert(added);
    }

    // Is there a globally declared table of inner classes?
    List<InnerClass> allInnerClasses = new ArrayList<>();
    Map<ClassEntry, InnerClass>   allInnerClassesByThis;

    public
    List<InnerClass> getAllInnerClasses() {
        return allInnerClasses;
    }

    public
    void setAllInnerClasses(Collection<InnerClass> ics) {
        assert(ics != allInnerClasses);
        allInnerClasses.clear();
        allInnerClasses.addAll(ics);

        // Make an index:
        allInnerClassesByThis = new HashMap<>(allInnerClasses.size());
        for (InnerClass ic : allInnerClasses) {
            Object pic = allInnerClassesByThis.put(ic.thisClass, ic);
            assert(pic == null);  // caller must ensure key uniqueness!
        }
    }

    /** Return a global inner class record for the given thisClass. */
    public
    InnerClass getGlobalInnerClass(Entry thisClass) {
        assert(thisClass instanceof ClassEntry);
        return allInnerClassesByThis.get(thisClass);
    }

    static
    class InnerClass implements Comparable<InnerClass> {
        final ClassEntry thisClass;
        final ClassEntry outerClass;
        final Utf8Entry name;
        final int flags;

        // Can name and outerClass be derived from thisClass?
        final boolean predictable;

        // About 30% of inner classes are anonymous (in rt.jar).
        // About 60% are class members; the rest are named locals.
        // Nearly all have predictable outers and names.

        InnerClass(ClassEntry thisClass, ClassEntry outerClass,
                   Utf8Entry name, int flags) {
            this.thisClass = thisClass;
            this.outerClass = outerClass;
            this.name = name;
            this.flags = flags;
            this.predictable = computePredictable();
        }

        private boolean computePredictable() {
            //System.out.println("computePredictable "+outerClass+" "+this.name);
            String[] parse = parseInnerClassName(thisClass.stringValue());
            if (parse == null)  return false;
            String pkgOuter = parse[0];
            //String number = parse[1];
            String lname     = parse[2];
            String haveName  = (this.name == null)  ? null : this.name.stringValue();
            String haveOuter = (outerClass == null) ? null : outerClass.stringValue();
            boolean lpredictable = (lname == haveName && pkgOuter == haveOuter);
            //System.out.println("computePredictable => "+predictable);
            return lpredictable;
        }

        public boolean equals(Object o) {
            if (o == null || o.getClass() != InnerClass.class)
                return false;
            InnerClass that = (InnerClass)o;
            return eq(this.thisClass, that.thisClass)
                && eq(this.outerClass, that.outerClass)
                && eq(this.name, that.name)
                && this.flags == that.flags;
        }
        private static boolean eq(Object x, Object y) {
            return (x == null)? y == null: x.equals(y);
        }
        public int hashCode() {
            return thisClass.hashCode();
        }
        public int compareTo(InnerClass that) {
            return this.thisClass.compareTo(that.thisClass);
        }

        protected void visitRefs(int mode, Collection<Entry> refs) {
            refs.add(thisClass);
            if (mode == VRM_CLASSIC || !predictable) {
                // If the name can be demangled, the package omits
                // the products of demangling.  Otherwise, include them.
                refs.add(outerClass);
                refs.add(name);
            }
        }

        public String toString() {
            return thisClass.stringValue();
        }
    }

    // Helper for building InnerClasses attributes.
    private static
    void visitInnerClassRefs(Collection<InnerClass> innerClasses, int mode, Collection<Entry> refs) {
        if (innerClasses == null) {
            return;  // no attribute; nothing to do
        }
        if (mode == VRM_CLASSIC) {
            refs.add(getRefString("InnerClasses"));
        }
        if (innerClasses.size() > 0) {
            // Count the entries themselves:
            for (InnerClass c : innerClasses) {
                c.visitRefs(mode, refs);
            }
        }
    }

    static String[] parseInnerClassName(String n) {
        //System.out.println("parseInnerClassName "+n);
        String pkgOuter, number, name;
        int dollar1, dollar2;  // pointers to $ in the pattern
        // parse n = (<pkg>/)*<outer>($<number>)?($<name>)?
        int nlen = n.length();
        int pkglen = lastIndexOf(SLASH_MIN,  SLASH_MAX,  n, n.length()) + 1;
        dollar2    = lastIndexOf(DOLLAR_MIN, DOLLAR_MAX, n, n.length());
        if (dollar2 < pkglen)  return null;
        if (isDigitString(n, dollar2+1, nlen)) {
            // n = (<pkg>/)*<outer>$<number>
            number = n.substring(dollar2+1, nlen);
            name = null;
            dollar1 = dollar2;
        } else if ((dollar1
                    = lastIndexOf(DOLLAR_MIN, DOLLAR_MAX, n, dollar2-1))
                   > pkglen
                   && isDigitString(n, dollar1+1, dollar2)) {
            // n = (<pkg>/)*<outer>$<number>$<name>
            number = n.substring(dollar1+1, dollar2);
            name = n.substring(dollar2+1, nlen).intern();
        } else {
            // n = (<pkg>/)*<outer>$<name>
            dollar1 = dollar2;
            number = null;
            name = n.substring(dollar2+1, nlen).intern();
        }
        if (number == null)
            pkgOuter = n.substring(0, dollar1).intern();
        else
            pkgOuter = null;
        //System.out.println("parseInnerClassName parses "+pkgOuter+" "+number+" "+name);
        return new String[] { pkgOuter, number, name };
    }

    private static final int SLASH_MIN = '.';
    private static final int SLASH_MAX = '/';
    private static final int DOLLAR_MIN = 0;
    private static final int DOLLAR_MAX = '-';
    static {
        assert(lastIndexOf(DOLLAR_MIN, DOLLAR_MAX, "x$$y$", 4) == 2);
        assert(lastIndexOf(SLASH_MIN,  SLASH_MAX,  "x//y/", 4) == 2);
    }

    private static int lastIndexOf(int chMin, int chMax, String str, int pos) {
        for (int i = pos; --i >= 0; ) {
            int ch = str.charAt(i);
            if (ch >= chMin && ch <= chMax) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isDigitString(String x, int beg, int end) {
        if (beg == end)  return false;  // null string
        for (int i = beg; i < end; i++) {
            char ch = x.charAt(i);
            if (!(ch >= '0' && ch <= '9'))  return false;
        }
        return true;
    }

    static String getObviousSourceFile(String className) {
        String n = className;
        int pkglen = lastIndexOf(SLASH_MIN,  SLASH_MAX,  n, n.length()) + 1;
        n = n.substring(pkglen);
        int cutoff = n.length();
        for (;;) {
            // Work backwards, finding all '$', '#', etc.
            int dollar2 = lastIndexOf(DOLLAR_MIN, DOLLAR_MAX, n, cutoff-1);
            if (dollar2 < 0)
                break;
            cutoff = dollar2;
            if (cutoff == 0)
                break;
        }
        String obvious = n.substring(0, cutoff)+".java";
        return obvious;
    }
/*
    static {
        assert(getObviousSourceFile("foo").equals("foo.java"));
        assert(getObviousSourceFile("foo/bar").equals("bar.java"));
        assert(getObviousSourceFile("foo/bar$baz").equals("bar.java"));
        assert(getObviousSourceFile("foo/bar#baz#1").equals("bar.java"));
        assert(getObviousSourceFile("foo.bar.baz#1").equals("baz.java"));
    }
*/

    static Utf8Entry getRefString(String s) {
        return ConstantPool.getUtf8Entry(s);
    }

    static LiteralEntry getRefLiteral(Comparable<?> s) {
        return ConstantPool.getLiteralEntry(s);
    }

    void stripAttributeKind(String what) {
        // what is one of { Debug, Compile, Constant, Exceptions, InnerClasses }
        if (verbose > 0)
            Utils.log.info("Stripping "+what.toLowerCase()+" data and attributes...");
        switch (what) {
            case "Debug":
                strip("SourceFile");
                strip("LineNumberTable");
                strip("LocalVariableTable");
                strip("LocalVariableTypeTable");
                break;
            case "Compile":
                // Keep the inner classes normally.
                // Although they have no effect on execution,
                // the Reflection API exposes them, and JCK checks them.
                // NO: // strip("InnerClasses");
                strip("Deprecated");
                strip("Synthetic");
                break;
            case "Exceptions":
                // Keep the exceptions normally.
                // Although they have no effect on execution,
                // the Reflection API exposes them, and JCK checks them.
                strip("Exceptions");
                break;
            case "Constant":
                stripConstantFields();
                break;
        }
    }

    public void trimToSize() {
        classes.trimToSize();
        for (Class c : classes) {
            c.trimToSize();
        }
        files.trimToSize();
    }

    public void strip(String attrName) {
        for (Class c : classes) {
            c.strip(attrName);
        }
    }

    public void stripConstantFields() {
        for (Class c : classes) {
            for (Iterator<Class.Field> j = c.fields.iterator(); j.hasNext(); ) {
                Class.Field f = j.next();
                if (Modifier.isFinal(f.flags)
                    // do not strip non-static finals:
                    && Modifier.isStatic(f.flags)
                    && f.getAttribute("ConstantValue") != null
                    && !f.getName().startsWith("serial")) {
                    if (verbose > 2) {
                        Utils.log.fine(">> Strip "+this+" ConstantValue");
                        j.remove();
                    }
                }
            }
        }
    }

    protected void visitRefs(int mode, Collection<Entry> refs) {
        for ( Class c : classes) {
            c.visitRefs(mode, refs);
        }
        if (mode != VRM_CLASSIC) {
            for (File f : files) {
                f.visitRefs(mode, refs);
            }
            visitInnerClassRefs(allInnerClasses, mode, refs);
        }
    }

    // Use this before writing the package file.
    // It sorts files into a new order which seems likely to
    // compress better.  It also moves classes to the end of the
    // file order.  It also removes JAR directory entries, which
    // are useless.
    void reorderFiles(boolean keepClassOrder, boolean stripDirectories) {
        // First reorder the classes, if that is allowed.
        if (!keepClassOrder) {
            // In one test with rt.jar, this trick gained 0.7%
            Collections.sort(classes);
        }

        // Remove stubs from resources; maybe we'll add them on at the end,
        // if there are some non-trivial ones.  The best case is that
        // modtimes and options are not transmitted, and the stub files
        // for class files do not need to be transmitted at all.
        // Also
        List<File> stubs = getClassStubs();
        for (Iterator<File> i = files.iterator(); i.hasNext(); ) {
            File file = i.next();
            if (file.isClassStub() ||
                (stripDirectories && file.isDirectory())) {
                i.remove();
            }
        }

        // Sort the remaining non-class files.
        // We sort them by file type.
        // This keeps files of similar format near each other.
        // Put class files at the end, keeping their fixed order.
        // Be sure the JAR file's required manifest stays at the front. (4893051)
        Collections.sort(files, new Comparator<>() {
                public int compare(File r0, File r1) {
                    // Get the file name.
                    String f0 = r0.nameString;
                    String f1 = r1.nameString;
                    if (f0.equals(f1)) return 0;
                    if (JarFile.MANIFEST_NAME.equals(f0))  return 0-1;
                    if (JarFile.MANIFEST_NAME.equals(f1))  return 1-0;
                    // Extract file basename.
                    String n0 = f0.substring(1+f0.lastIndexOf('/'));
                    String n1 = f1.substring(1+f1.lastIndexOf('/'));
                    // Extract basename extension.
                    String x0 = n0.substring(1+n0.lastIndexOf('.'));
                    String x1 = n1.substring(1+n1.lastIndexOf('.'));
                    int r;
                    // Primary sort key is file extension.
                    r = x0.compareTo(x1);
                    if (r != 0)  return r;
                    r = f0.compareTo(f1);
                    return r;
                }
            });

        // Add back the class stubs after sorting, before trimStubs.
        files.addAll(stubs);
    }

    void trimStubs() {
        // Restore enough non-trivial stubs to carry the needed class modtimes.
        for (ListIterator<File> i = files.listIterator(files.size()); i.hasPrevious(); ) {
            File file = i.previous();
            if (!file.isTrivialClassStub()) {
                if (verbose > 1)
                    Utils.log.fine("Keeping last non-trivial "+file);
                break;
            }
            if (verbose > 2)
                Utils.log.fine("Removing trivial "+file);
            i.remove();
        }

        if (verbose > 0) {
            Utils.log.info("Transmitting "+files.size()+" files, including per-file data for "+getClassStubs().size()+" classes out of "+classes.size());
        }
    }

    // Use this before writing the package file.
    void buildGlobalConstantPool(Set<Entry> requiredEntries) {
        if (verbose > 1)
            Utils.log.fine("Checking for unused CP entries");
        requiredEntries.add(getRefString(""));  // uconditionally present
        visitRefs(VRM_PACKAGE, requiredEntries);
        ConstantPool.completeReferencesIn(requiredEntries, false);
        if (verbose > 1)
            Utils.log.fine("Sorting CP entries");
        Index   cpAllU = ConstantPool.makeIndex("unsorted", requiredEntries);
        Index[] byTagU = ConstantPool.partitionByTag(cpAllU);
        for (int i = 0; i < ConstantPool.TAGS_IN_ORDER.length; i++) {
            byte tag = ConstantPool.TAGS_IN_ORDER[i];
            // Work on all entries of a given kind.
            Index ix = byTagU[tag];
            if (ix == null)  continue;
            ConstantPool.sort(ix);
            cp.initIndexByTag(tag, ix);
            byTagU[tag] = null;  // done with it
        }
        for (int i = 0; i < byTagU.length; i++) {
            Index ix = byTagU[i];
            assert(ix == null);  // all consumed
        }
        for (int i = 0; i < ConstantPool.TAGS_IN_ORDER.length; i++) {
            byte tag = ConstantPool.TAGS_IN_ORDER[i];
            Index ix = cp.getIndexByTag(tag);
            assert(ix.assertIsSorted());
            if (verbose > 2)  Utils.log.fine(ix.dumpString());
        }
    }

    // Use this before writing the class files.
    void ensureAllClassFiles() {
        Set<File> fileSet = new HashSet<>(files);
        for (Class cls : classes) {
            // Add to the end of ths list:
            if (!fileSet.contains(cls.file))
                files.add(cls.file);
        }
    }

    static final List<Object> noObjects = Arrays.asList(new Object[0]);
    static final List<Class.Field> noFields = Arrays.asList(new Class.Field[0]);
    static final List<Class.Method> noMethods = Arrays.asList(new Class.Method[0]);
    static final List<InnerClass> noInnerClasses = Arrays.asList(new InnerClass[0]);

    protected static final class Version {

        public final short major;
        public final short minor;

        private Version(short major, short minor) {
            this.major = major;
            this.minor = minor;
        }

        public String toString() {
            return major + "." + minor;
        }

        public boolean equals(Object that) {
            return that instanceof Version
                    && major == ((Version)that).major
                    && minor == ((Version)that).minor;
        }

        public int intValue() {
            return (major << 16) + minor;
        }

        public int hashCode() {
            return (major << 16) + 7 + minor;
        }

        public static Version of(int major, int minor) {
            return new Version((short)major, (short)minor);
        }

        public static Version of(byte[] bytes) {
           int minor = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
           int major = ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
           return new Version((short)major, (short)minor);
        }

        public static Version of(int major_minor) {
            short minor = (short)major_minor;
            short major = (short)(major_minor >>> 16);
            return new Version(major, minor);
        }

        public static Version makeVersion(PropMap props, String partialKey) {
            int min = props.getInteger(Utils.COM_PREFIX
                    + partialKey + ".minver", -1);
            int maj = props.getInteger(Utils.COM_PREFIX
                    + partialKey + ".majver", -1);
            return min >= 0 && maj >= 0 ? Version.of(maj, min) : null;
        }
        public byte[] asBytes() {
            byte[] bytes = {
                (byte) (minor >> 8), (byte) minor,
                (byte) (major >> 8), (byte) major
            };
            return bytes;
        }
        public int compareTo(Version that) {
            return this.intValue() - that.intValue();
        }

        public boolean lessThan(Version that) {
            return compareTo(that) < 0 ;
        }

        public boolean greaterThan(Version that) {
            return compareTo(that) > 0 ;
        }
    }
}
