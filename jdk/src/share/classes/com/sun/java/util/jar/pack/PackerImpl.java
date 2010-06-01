/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import java.io.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;


/*
 * Implementation of the Pack provider.
 * </pre></blockquote>
 * @author John Rose
 * @author Kumar Srinivasan
 */


public class PackerImpl implements Pack200.Packer {

    /**
     * Constructs a Packer object and sets the initial state of
     * the packer engines.
     */
    public PackerImpl() {
        _props = new PropMap();
        //_props.getProperty() consults defaultProps invisibly.
        //_props.putAll(defaultProps);
    }


    // Private stuff.
    final PropMap _props;

    /**
     * Get the set of options for the pack and unpack engines.
     * @return A sorted association of option key strings to option values.
     */
    public SortedMap properties() {
        return _props;
    }


    //Driver routines

    /**
     * Takes a JarFile and converts into a pack-stream.
     * <p>
     * Closes its input but not its output.  (Pack200 archives are appendable.)
     * @param in a JarFile
     * @param out an OutputStream
     * @exception IOException if an error is encountered.
     */
    public void pack(JarFile in, OutputStream out) throws IOException {
        assert(Utils.currentInstance.get() == null);
        TimeZone tz = (_props.getBoolean(Utils.PACK_DEFAULT_TIMEZONE)) ? null :
            TimeZone.getDefault();
        try {
            Utils.currentInstance.set(this);
            if (tz != null) TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            if ("0".equals(_props.getProperty(Pack200.Packer.EFFORT))) {
                Utils.copyJarFile(in, out);
            } else {
                (new DoPack()).run(in, out);
                in.close();
            }
        } finally {
            Utils.currentInstance.set(null);
            if (tz != null) TimeZone.setDefault(tz);
        }
    }

    /**
     * Takes a JarInputStream and converts into a pack-stream.
     * <p>
     * Closes its input but not its output.  (Pack200 archives are appendable.)
     * <p>
     * The modification time and deflation hint attributes are not available,
     * for the jar-manifest file and the directory containing the file.
     *
     * @see #MODIFICATION_TIME
     * @see #DEFLATION_HINT
     * @param in a JarInputStream
     * @param out an OutputStream
     * @exception IOException if an error is encountered.
     */
    public void pack(JarInputStream in, OutputStream out) throws IOException {
        assert(Utils.currentInstance.get() == null);
        TimeZone tz = (_props.getBoolean(Utils.PACK_DEFAULT_TIMEZONE)) ? null :
            TimeZone.getDefault();
        try {
            Utils.currentInstance.set(this);
            if (tz != null) TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            if ("0".equals(_props.getProperty(Pack200.Packer.EFFORT))) {
                Utils.copyJarFile(in, out);
            } else {
                (new DoPack()).run(in, out);
                in.close();
            }
        } finally {
            Utils.currentInstance.set(null);
            if (tz != null) TimeZone.setDefault(tz);

        }
    }
    /**
     * Register a listener for changes to options.
     * @param listener  An object to be invoked when a property is changed.
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        _props.addListener(listener);
    }

    /**
     * Remove a listener for the PropertyChange event.
     * @param listener  The PropertyChange listener to be removed.
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        _props.removeListener(listener);
    }



    // All the worker bees.....

    // The packer worker.
    private class DoPack {
        final int verbose = _props.getInteger(Utils.DEBUG_VERBOSE);

        {
            _props.setInteger(Pack200.Packer.PROGRESS, 0);
            if (verbose > 0) Utils.log.info(_props.toString());
        }

        // Here's where the bits are collected before getting packed:
        final Package pkg = new Package();

        final String unknownAttrCommand;
        {
            String uaMode = _props.getProperty(Pack200.Packer.UNKNOWN_ATTRIBUTE, Pack200.Packer.PASS);
            if (!(Pack200.Packer.STRIP.equals(uaMode) ||
                  Pack200.Packer.PASS.equals(uaMode) ||
                  Pack200.Packer.ERROR.equals(uaMode))) {
                throw new RuntimeException("Bad option: " + Pack200.Packer.UNKNOWN_ATTRIBUTE + " = " + uaMode);
            }
            unknownAttrCommand = uaMode.intern();
        }

        final HashMap attrDefs;
        final HashMap attrCommands;
        {
            HashMap attrDefs     = new HashMap();
            HashMap attrCommands = new HashMap();
            String[] keys = {
                Pack200.Packer.CLASS_ATTRIBUTE_PFX,
                Pack200.Packer.FIELD_ATTRIBUTE_PFX,
                Pack200.Packer.METHOD_ATTRIBUTE_PFX,
                Pack200.Packer.CODE_ATTRIBUTE_PFX
            };
            int[] ctypes = {
                Constants.ATTR_CONTEXT_CLASS,
                Constants.ATTR_CONTEXT_FIELD,
                Constants.ATTR_CONTEXT_METHOD,
                Constants.ATTR_CONTEXT_CODE
            };
            for (int i = 0; i < ctypes.length; i++) {
                String pfx = keys[i];
                Map map = _props.prefixMap(pfx);
                for (Iterator j = map.keySet().iterator(); j.hasNext(); ) {
                    String key = (String) j.next();
                    assert(key.startsWith(pfx));
                    String name = key.substring(pfx.length());
                    String layout = _props.getProperty(key);
                    Object lkey = Attribute.keyForLookup(ctypes[i], name);
                    if (Pack200.Packer.STRIP.equals(layout) ||
                        Pack200.Packer.PASS.equals(layout) ||
                        Pack200.Packer.ERROR.equals(layout)) {
                        attrCommands.put(lkey, layout.intern());
                    } else {
                        Attribute.define(attrDefs, ctypes[i], name, layout);
                        if (verbose > 1) {
                            Utils.log.fine("Added layout for "+Constants.ATTR_CONTEXT_NAME[i]+" attribute "+name+" = "+layout);
                        }
                        assert(attrDefs.containsKey(lkey));
                    }
                }
            }
            if (attrDefs.size() > 0)
                this.attrDefs = attrDefs;
            else
                this.attrDefs = null;
            if (attrCommands.size() > 0)
                this.attrCommands = attrCommands;
            else
                this.attrCommands = null;
        }

        final boolean keepFileOrder
            = _props.getBoolean(Pack200.Packer.KEEP_FILE_ORDER);
        final boolean keepClassOrder
            = _props.getBoolean(Utils.PACK_KEEP_CLASS_ORDER);

        final boolean keepModtime
            = Pack200.Packer.KEEP.equals(_props.getProperty(Pack200.Packer.MODIFICATION_TIME));
        final boolean latestModtime
            = Pack200.Packer.LATEST.equals(_props.getProperty(Pack200.Packer.MODIFICATION_TIME));
        final boolean keepDeflateHint
            = Pack200.Packer.KEEP.equals(_props.getProperty(Pack200.Packer.DEFLATE_HINT));
        {
            if (!keepModtime && !latestModtime) {
                int modtime = _props.getTime(Pack200.Packer.MODIFICATION_TIME);
                if (modtime != Constants.NO_MODTIME) {
                    pkg.default_modtime = modtime;
                }
            }
            if (!keepDeflateHint) {
                boolean deflate_hint = _props.getBoolean(Pack200.Packer.DEFLATE_HINT);
                if (deflate_hint) {
                    pkg.default_options |= Constants.AO_DEFLATE_HINT;
                }
            }
        }

        long totalOutputSize = 0;
        int  segmentCount = 0;
        long segmentTotalSize = 0;
        long segmentSize = 0;  // running counter
        final long segmentLimit;
        {
            long limit;
            if (_props.getProperty(Pack200.Packer.SEGMENT_LIMIT, "").equals(""))
                limit = -1;
            else
                limit = _props.getLong(Pack200.Packer.SEGMENT_LIMIT);
            limit = Math.min(Integer.MAX_VALUE, limit);
            limit = Math.max(-1, limit);
            if (limit == -1)
                limit = Long.MAX_VALUE;
            segmentLimit = limit;
        }

        final List passFiles;  // parsed pack.pass.file options
        {
            // Which class files will be passed through?
            passFiles = _props.getProperties(Pack200.Packer.PASS_FILE_PFX);
            for (ListIterator i = passFiles.listIterator(); i.hasNext(); ) {
                String file = (String) i.next();
                if (file == null) { i.remove(); continue; }
                file = Utils.getJarEntryName(file);  // normalize '\\' to '/'
                if (file.endsWith("/"))
                    file = file.substring(0, file.length()-1);
                i.set(file);
            }
            if (verbose > 0) Utils.log.info("passFiles = " + passFiles);
        }

        {
            // Fill in permitted range of major/minor version numbers.
            int ver;
            if ((ver = _props.getInteger(Utils.COM_PREFIX+"min.class.majver")) != 0)
                pkg.min_class_majver = (short) ver;
            if ((ver = _props.getInteger(Utils.COM_PREFIX+"min.class.minver")) != 0)
                pkg.min_class_minver = (short) ver;
            if ((ver = _props.getInteger(Utils.COM_PREFIX+"max.class.majver")) != 0)
                pkg.max_class_majver = (short) ver;
            if ((ver = _props.getInteger(Utils.COM_PREFIX+"max.class.minver")) != 0)
                pkg.max_class_minver = (short) ver;
            if ((ver = _props.getInteger(Utils.COM_PREFIX+"package.minver")) != 0)
                pkg.package_minver = (short) ver;
            if ((ver = _props.getInteger(Utils.COM_PREFIX+"package.majver")) != 0)
                pkg.package_majver = (short) ver;
        }

        {
            // Hook for testing:  Forces use of special archive modes.
            int opt = _props.getInteger(Utils.COM_PREFIX+"archive.options");
            if (opt != 0)
                pkg.default_options |= opt;
        }

        // (Done collecting options from _props.)

        boolean isClassFile(String name) {
            if (!name.endsWith(".class"))  return false;
            for (String prefix = name; ; ) {
                if (passFiles.contains(prefix))  return false;
                int chop = prefix.lastIndexOf('/');
                if (chop < 0)  break;
                prefix = prefix.substring(0, chop);
            }
            return true;
        }

        boolean isMetaInfFile(String name) {
            return name.startsWith("/" + Utils.METAINF) ||
                        name.startsWith(Utils.METAINF);
        }

        // Get a new package, based on the old one.
        private void makeNextPackage() {
            pkg.reset();
        }

        class InFile {
            final String name;
            final JarFile jf;
            final JarEntry je;
            final File f;
            int modtime = Constants.NO_MODTIME;
            int options;
            InFile(String name) {
                this.name = Utils.getJarEntryName(name);
                this.f = new File(name);
                this.jf = null;
                this.je = null;
                int timeSecs = getModtime(f.lastModified());
                if (keepModtime && timeSecs != Constants.NO_MODTIME) {
                    this.modtime = timeSecs;
                } else if (latestModtime && timeSecs > pkg.default_modtime) {
                    pkg.default_modtime = timeSecs;
                }
            }
            InFile(JarFile jf, JarEntry je) {
                this.name = Utils.getJarEntryName(je.getName());
                this.f = null;
                this.jf = jf;
                this.je = je;
                int timeSecs = getModtime(je.getTime());
                if (keepModtime && timeSecs != Constants.NO_MODTIME) {
                     this.modtime = timeSecs;
                } else if (latestModtime && timeSecs > pkg.default_modtime) {
                    pkg.default_modtime = timeSecs;
                }
                if (keepDeflateHint && je.getMethod() == JarEntry.DEFLATED) {
                    options |= Constants.FO_DEFLATE_HINT;
                }
            }
            InFile(JarEntry je) {
                this(null, je);
            }
            long getInputLength() {
                long len = (je != null)? je.getSize(): f.length();
                assert(len >= 0) : this+".len="+len;
                // Bump size by pathname length and modtime/def-hint bytes.
                return Math.max(0, len) + name.length() + 5;
            }
            int getModtime(long timeMillis) {
                // Convert milliseconds to seconds.
                long seconds = (timeMillis+500) / 1000;
                if ((int)seconds == seconds) {
                    return (int)seconds;
                } else {
                    Utils.log.warning("overflow in modtime for "+f);
                    return Constants.NO_MODTIME;
                }
            }
            void copyTo(Package.File file) {
                if (modtime != Constants.NO_MODTIME)
                    file.modtime = modtime;
                file.options |= options;
            }
            InputStream getInputStream() throws IOException {
                if (jf != null)
                    return jf.getInputStream(je);
                else
                    return new FileInputStream(f);
            }

            public String toString() {
                return name;
            }
        }

        private int nread = 0;  // used only if (verbose > 0)
        private void noteRead(InFile f) {
            nread++;
            if (verbose > 2)
                Utils.log.fine("...read "+f.name);
            if (verbose > 0 && (nread % 1000) == 0)
                Utils.log.info("Have read "+nread+" files...");
        }

        void run(JarInputStream in, OutputStream out) throws IOException {
            // First thing we do is get the manifest, as JIS does
            // not provide the Manifest as an entry.
            if (in.getManifest() != null) {
                ByteArrayOutputStream tmp = new ByteArrayOutputStream();
                in.getManifest().write(tmp);
                InputStream tmpIn = new ByteArrayInputStream(tmp.toByteArray());
                pkg.addFile(readFile(JarFile.MANIFEST_NAME, tmpIn));
            }
            for (JarEntry je; (je = in.getNextJarEntry()) != null; ) {
                InFile inFile = new InFile(je);

                String name = inFile.name;
                Package.File bits = readFile(name, in);
                Package.File file = null;
                // (5078608) : discount the resource files in META-INF
                // from segment computation.
                long inflen = (isMetaInfFile(name)) ?  0L :
                                inFile.getInputLength();

                if ((segmentSize += inflen) > segmentLimit) {
                    segmentSize -= inflen;
                    int nextCount = -1;  // don't know; it's a stream
                    flushPartial(out, nextCount);
                }
                if (verbose > 1)
                    Utils.log.fine("Reading " + name);

                assert(je.isDirectory() == name.endsWith("/"));

                if (isClassFile(name)) {
                    file = readClass(name, bits.getInputStream());
                }
                if (file == null) {
                    file = bits;
                    pkg.addFile(file);
                }
                inFile.copyTo(file);
                noteRead(inFile);
            }
            flushAll(out);
        }

        void run(JarFile in, OutputStream out) throws IOException {
            List inFiles = scanJar(in);

            if (verbose > 0)
                Utils.log.info("Reading " + inFiles.size() + " files...");

            int numDone = 0;
            for (Iterator i = inFiles.iterator(); i.hasNext(); ) {
                InFile inFile = (InFile) i.next();
                String name      = inFile.name;
                // (5078608) : discount the resource files completely from segmenting
                long inflen = (isMetaInfFile(name)) ? 0L :
                                inFile.getInputLength() ;
                if ((segmentSize += inflen) > segmentLimit) {
                    segmentSize -= inflen;
                    // Estimate number of remaining segments:
                    float filesDone = numDone+1;
                    float segsDone  = segmentCount+1;
                    float filesToDo = inFiles.size() - filesDone;
                    float segsToDo  = filesToDo * (segsDone/filesDone);
                    if (verbose > 1)
                        Utils.log.fine("Estimated segments to do: "+segsToDo);
                    flushPartial(out, (int) Math.ceil(segsToDo));
                }
                InputStream strm = inFile.getInputStream();
                if (verbose > 1)
                    Utils.log.fine("Reading " + name);
                Package.File file = null;
                if (isClassFile(name)) {
                    file = readClass(name, strm);
                    if (file == null) {
                        strm.close();
                        strm = inFile.getInputStream();
                    }
                }
                if (file == null) {
                    file = readFile(name, strm);
                    pkg.addFile(file);
                }
                inFile.copyTo(file);
                strm.close();  // tidy up
                noteRead(inFile);
                numDone += 1;
            }
            flushAll(out);
        }

        Package.File readClass(String fname, InputStream in) throws IOException {
            Package.Class cls = pkg.new Class(fname);
            in = new BufferedInputStream(in);
            ClassReader reader = new ClassReader(cls, in);
            reader.setAttrDefs(attrDefs);
            reader.setAttrCommands(attrCommands);
            reader.unknownAttrCommand = unknownAttrCommand;
            try {
                reader.read();
            } catch (Attribute.FormatException ee) {
                // He passed up the category to us in layout.
                if (ee.layout.equals(Pack200.Packer.PASS)) {
                    Utils.log.warning("Passing class file uncompressed due to unrecognized attribute: "+fname);
                    Utils.log.info(ee.toString());
                    return null;
                }
                // Otherwise, it must be an error.
                throw ee;
            }
            pkg.addClass(cls);
            return cls.file;
        }

        // Read raw data.
        Package.File readFile(String fname, InputStream in) throws IOException {

            Package.File file = pkg.new File(fname);
            file.readFrom(in);
            if (file.isDirectory() && file.getFileLength() != 0)
                throw new IllegalArgumentException("Non-empty directory: "+file.getFileName());
            return file;
        }

        void flushPartial(OutputStream out, int nextCount) throws IOException {
            if (pkg.files.size() == 0 && pkg.classes.size() == 0) {
                return;  // do not flush an empty segment
            }
            flushPackage(out, Math.max(1, nextCount));
            _props.setInteger(Pack200.Packer.PROGRESS, 25);
            // In case there will be another segment:
            makeNextPackage();
            segmentCount += 1;
            segmentTotalSize += segmentSize;
            segmentSize = 0;
        }

        void flushAll(OutputStream out) throws IOException {
            _props.setInteger(Pack200.Packer.PROGRESS, 50);
            flushPackage(out, 0);
            out.flush();
            _props.setInteger(Pack200.Packer.PROGRESS, 100);
            segmentCount += 1;
            segmentTotalSize += segmentSize;
            segmentSize = 0;
            if (verbose > 0 && segmentCount > 1) {
                Utils.log.info("Transmitted "
                                 +segmentTotalSize+" input bytes in "
                                 +segmentCount+" segments totaling "
                                 +totalOutputSize+" bytes");
            }
        }


        /** Write all information in the current package segment
         *  to the output stream.
         */
        void flushPackage(OutputStream out, int nextCount) throws IOException {
            int nfiles = pkg.files.size();
            if (!keepFileOrder) {
                // Keeping the order of classes costs about 1%
                // Keeping the order of all files costs something more.
                if (verbose > 1)  Utils.log.fine("Reordering files.");
                boolean stripDirectories = true;
                pkg.reorderFiles(keepClassOrder, stripDirectories);
            } else {
                // Package builder must have created a stub for each class.
                assert(pkg.files.containsAll(pkg.getClassStubs()));
                // Order of stubs in file list must agree with classes.
                List res = pkg.files;
                assert((res = new ArrayList(pkg.files))
                       .retainAll(pkg.getClassStubs()) || true);
                assert(res.equals(pkg.getClassStubs()));
            }
            pkg.trimStubs();

            // Do some stripping, maybe.
            if (_props.getBoolean(Utils.COM_PREFIX+"strip.debug"))        pkg.stripAttributeKind("Debug");
            if (_props.getBoolean(Utils.COM_PREFIX+"strip.compile"))      pkg.stripAttributeKind("Compile");
            if (_props.getBoolean(Utils.COM_PREFIX+"strip.constants"))    pkg.stripAttributeKind("Constant");
            if (_props.getBoolean(Utils.COM_PREFIX+"strip.exceptions"))   pkg.stripAttributeKind("Exceptions");
            if (_props.getBoolean(Utils.COM_PREFIX+"strip.innerclasses")) pkg.stripAttributeKind("InnerClasses");

            // Must choose an archive version; PackageWriter does not.
            if (pkg.package_majver <= 0)  pkg.choosePackageVersion();

            PackageWriter pw = new PackageWriter(pkg, out);
            pw.archiveNextCount = nextCount;
            pw.write();
            out.flush();
            if (verbose > 0) {
                long outSize = pw.archiveSize0+pw.archiveSize1;
                totalOutputSize += outSize;
                long inSize = segmentSize;
                Utils.log.info("Transmitted "
                                 +nfiles+" files of "
                                 +inSize+" input bytes in a segment of "
                                 +outSize+" bytes");
            }
        }

        List scanJar(JarFile jf) throws IOException {
            // Collect jar entries, preserving order.
            List inFiles = new ArrayList();
            for (Enumeration e = jf.entries(); e.hasMoreElements(); ) {
                JarEntry je = (JarEntry) e.nextElement();
                InFile inFile = new InFile(jf, je);
                assert(je.isDirectory() == inFile.name.endsWith("/"));
                inFiles.add(inFile);
            }
            return inFiles;
        }
    }
}
