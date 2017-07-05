/*
 * Copyright 1996-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.tools.jar;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.jar.*;
import java.util.jar.Manifest;
import java.text.MessageFormat;
import sun.misc.JarIndex;

/**
 * This class implements a simple utility for creating files in the JAR
 * (Java Archive) file format. The JAR format is based on the ZIP file
 * format, with optional meta-information stored in a MANIFEST entry.
 */
public
class Main {
    String program;
    PrintStream out, err;
    String fname, mname, ename;
    String zname = "";
    String[] files;
    String rootjar = null;

    // An entryName(path)->File map generated during "expand", it helps to
    // decide whether or not an existing entry in a jar file needs to be
    // replaced, during the "update" operation.
    Map<String, File> entryMap = new HashMap<String, File>();

    // All files need to be added/updated.
    Set<File> entries = new LinkedHashSet<File>();

    // Directories specified by "-C" operation.
    Set<String> paths = new HashSet<String>();

    CRC32 crc32 = new CRC32();
    /*
     * cflag: create
     * uflag: update
     * xflag: xtract
     * tflag: table
     * vflag: verbose
     * flag0: no zip compression (store only)
     * Mflag: DO NOT generate a manifest file (just ZIP)
     * iflag: generate jar index
     */
    boolean cflag, uflag, xflag, tflag, vflag, flag0, Mflag, iflag;

    static final String MANIFEST = JarFile.MANIFEST_NAME;
    static final String MANIFEST_DIR = "META-INF/";
    static final String VERSION = "1.0";
    static final String INDEX = JarIndex.INDEX_NAME;

    private static ResourceBundle rsrc;

    /**
     * If true, maintain compatibility with JDK releases prior to 6.0 by
     * timestamping extracted files with the time at which they are extracted.
     * Default is to use the time given in the archive.
     */
    private static final boolean useExtractionTime =
        Boolean.getBoolean("sun.tools.jar.useExtractionTime");

    /**
     * Initialize ResourceBundle
     */
    static {
        try {
            rsrc = ResourceBundle.getBundle("sun.tools.jar.resources.jar");
        } catch (MissingResourceException e) {
            throw new Error("Fatal: Resource for jar is missing");
        }
    }

    private String getMsg(String key) {
        try {
            return (rsrc.getString(key));
        } catch (MissingResourceException e) {
            throw new Error("Error in message file");
        }
    }

    private String formatMsg(String key, String arg) {
        String msg = getMsg(key);
        String[] args = new String[1];
        args[0] = arg;
        return MessageFormat.format(msg, (Object[]) args);
    }

    private String formatMsg2(String key, String arg, String arg1) {
        String msg = getMsg(key);
        String[] args = new String[2];
        args[0] = arg;
        args[1] = arg1;
        return MessageFormat.format(msg, (Object[]) args);
    }

    public Main(PrintStream out, PrintStream err, String program) {
        this.out = out;
        this.err = err;
        this.program = program;
    }

    private boolean ok;

    /*
     * Starts main program with the specified arguments.
     */
    public synchronized boolean run(String args[]) {
        ok = true;
        if (!parseArgs(args)) {
            return false;
        }
        try {
            if (cflag || uflag) {
                if (fname != null) {
                    // The name of the zip file as it would appear as its own
                    // zip file entry. We use this to make sure that we don't
                    // add the zip file to itself.
                    zname = fname.replace(File.separatorChar, '/');
                    if (zname.startsWith("./")) {
                        zname = zname.substring(2);
                    }
                }
            }
            if (cflag) {
                Manifest manifest = null;
                InputStream in = null;

                if (!Mflag) {
                    if (mname != null) {
                        in = new FileInputStream(mname);
                        manifest = new Manifest(new BufferedInputStream(in));
                    } else {
                        manifest = new Manifest();
                    }
                    addVersion(manifest);
                    addCreatedBy(manifest);
                    if (isAmbigousMainClass(manifest)) {
                        if (in != null) {
                            in.close();
                        }
                        return false;
                    }
                    if (ename != null) {
                        addMainClass(manifest, ename);
                    }
                }
                OutputStream out;
                if (fname != null) {
                    out = new FileOutputStream(fname);
                } else {
                    out = new FileOutputStream(FileDescriptor.out);
                    if (vflag) {
                        // Disable verbose output so that it does not appear
                        // on stdout along with file data
                        // error("Warning: -v option ignored");
                        vflag = false;
                    }
                }
                expand(null, files, false);
                create(new BufferedOutputStream(out, 4096), manifest);
                if (in != null) {
                    in.close();
                }
                out.close();
            } else if (uflag) {
                File inputFile = null, tmpFile = null;
                FileInputStream in;
                FileOutputStream out;
                if (fname != null) {
                    inputFile = new File(fname);
                    String path = inputFile.getParent();
                    tmpFile = File.createTempFile("tmp", null,
                              new File((path == null) ? "." : path));
                    in = new FileInputStream(inputFile);
                    out = new FileOutputStream(tmpFile);
                } else {
                    in = new FileInputStream(FileDescriptor.in);
                    out = new FileOutputStream(FileDescriptor.out);
                    vflag = false;
                }
                InputStream manifest = (!Mflag && (mname != null)) ?
                    (new FileInputStream(mname)) : null;
                expand(null, files, true);
                boolean updateOk = update(in, new BufferedOutputStream(out), manifest, null);
                if (ok) {
                    ok = updateOk;
                }
                in.close();
                out.close();
                if (manifest != null) {
                    manifest.close();
                }
                if (fname != null) {
                    // on Win32, we need this delete
                    inputFile.delete();
                    if (!tmpFile.renameTo(inputFile)) {
                        tmpFile.delete();
                        throw new IOException(getMsg("error.write.file"));
                    }
                    tmpFile.delete();
                }
            } else if (tflag) {
                replaceFSC(files);
                if (fname != null) {
                    list(fname, files);
                } else {
                    InputStream in = new FileInputStream(FileDescriptor.in);
                    try{
                        list(new BufferedInputStream(in), files);
                    } finally {
                        in.close();
                    }
                }
            } else if (xflag) {
                replaceFSC(files);
                if (fname != null && files != null) {
                    extract(fname, files);
                } else {
                    InputStream in = (fname == null)
                        ? new FileInputStream(FileDescriptor.in)
                        : new FileInputStream(fname);
                    try {
                        extract(new BufferedInputStream(in), files);
                    } finally {
                        in.close();
                    }
                }
            } else if (iflag) {
                genIndex(rootjar, files);
            }
        } catch (IOException e) {
            fatalError(e);
            ok = false;
        } catch (Error ee) {
            ee.printStackTrace();
            ok = false;
        } catch (Throwable t) {
            t.printStackTrace();
            ok = false;
        }
        out.flush();
        err.flush();
        return ok;
    }

    /*
     * Parse command line arguments.
     */
    boolean parseArgs(String args[]) {
        /* Preprocess and expand @file arguments */
        try {
            args = CommandLine.parse(args);
        } catch (FileNotFoundException e) {
            fatalError(formatMsg("error.cant.open", e.getMessage()));
            return false;
        } catch (IOException e) {
            fatalError(e);
            return false;
        }
        /* parse flags */
        int count = 1;
        try {
            String flags = args[0];
            if (flags.startsWith("-")) {
                flags = flags.substring(1);
            }
            for (int i = 0; i < flags.length(); i++) {
                switch (flags.charAt(i)) {
                case 'c':
                    if (xflag || tflag || uflag) {
                        usageError();
                        return false;
                    }
                    cflag = true;
                    break;
                case 'u':
                    if (cflag || xflag || tflag) {
                        usageError();
                        return false;
                    }
                    uflag = true;
                    break;
                case 'x':
                    if (cflag || uflag || tflag) {
                        usageError();
                        return false;
                    }
                    xflag = true;
                    break;
                case 't':
                    if (cflag || uflag || xflag) {
                        usageError();
                        return false;
                    }
                    tflag = true;
                    break;
                case 'M':
                    Mflag = true;
                    break;
                case 'v':
                    vflag = true;
                    break;
                case 'f':
                    fname = args[count++];
                    break;
                case 'm':
                    mname = args[count++];
                    break;
                case '0':
                    flag0 = true;
                    break;
                case 'i':
                    // do not increase the counter, files will contain rootjar
                    rootjar = args[count++];
                    iflag = true;
                    break;
                case 'e':
                     ename = args[count++];
                     break;
                default:
                    error(formatMsg("error.illegal.option",
                                String.valueOf(flags.charAt(i))));
                    usageError();
                    return false;
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            usageError();
            return false;
        }
        if (!cflag && !tflag && !xflag && !uflag && !iflag) {
            error(getMsg("error.bad.option"));
            usageError();
            return false;
        }
        /* parse file arguments */
        int n = args.length - count;
        if (n > 0) {
            int k = 0;
            String[] nameBuf = new String[n];
            try {
                for (int i = count; i < args.length; i++) {
                    if (args[i].equals("-C")) {
                        /* change the directory */
                        String dir = args[++i];
                        dir = (dir.endsWith(File.separator) ?
                               dir : (dir + File.separator));
                        dir = dir.replace(File.separatorChar, '/');
                        while (dir.indexOf("//") > -1) {
                            dir = dir.replace("//", "/");
                        }
                        paths.add(dir.replace(File.separatorChar, '/'));
                        nameBuf[k++] = dir + args[++i];
                    } else {
                        nameBuf[k++] = args[i];
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                usageError();
                return false;
            }
            files = new String[k];
            System.arraycopy(nameBuf, 0, files, 0, k);
        } else if (cflag && (mname == null)) {
            error(getMsg("error.bad.cflag"));
            usageError();
            return false;
        } else if (uflag) {
            if ((mname != null) || (ename != null)) {
                /* just want to update the manifest */
                return true;
            } else {
                error(getMsg("error.bad.uflag"));
                usageError();
                return false;
            }
        }
        return true;
    }

    /*
     * Expands list of files to process into full list of all files that
     * can be found by recursively descending directories.
     */
    void expand(File dir, String[] files, boolean isUpdate) {
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File f;
            if (dir == null) {
                f = new File(files[i]);
            } else {
                f = new File(dir, files[i]);
            }
            if (f.isFile()) {
                if (entries.add(f)) {
                    if (isUpdate)
                        entryMap.put(entryName(f.getPath()), f);
                }
            } else if (f.isDirectory()) {
                if (entries.add(f)) {
                    if (isUpdate) {
                        String dirPath = f.getPath();
                        dirPath = (dirPath.endsWith(File.separator)) ? dirPath :
                            (dirPath + File.separator);
                        entryMap.put(entryName(dirPath), f);
                    }
                    expand(f, f.list(), isUpdate);
                }
            } else {
                error(formatMsg("error.nosuch.fileordir", String.valueOf(f)));
                ok = false;
            }
        }
    }

    /*
     * Creates a new JAR file.
     */
    void create(OutputStream out, Manifest manifest)
        throws IOException
    {
        ZipOutputStream zos = new JarOutputStream(out);
        if (flag0) {
            zos.setMethod(ZipOutputStream.STORED);
        }
        if (manifest != null) {
            if (vflag) {
                output(getMsg("out.added.manifest"));
            }
            ZipEntry e = new ZipEntry(MANIFEST_DIR);
            e.setTime(System.currentTimeMillis());
            e.setSize(0);
            e.setCrc(0);
            zos.putNextEntry(e);
            e = new ZipEntry(MANIFEST);
            e.setTime(System.currentTimeMillis());
            if (flag0) {
                crc32Manifest(e, manifest);
            }
            zos.putNextEntry(e);
            manifest.write(zos);
            zos.closeEntry();
        }
        for (File file: entries) {
            addFile(zos, file);
        }
        zos.close();
    }

    /*
     * update an existing jar file.
     */
    boolean update(InputStream in, OutputStream out,
                   InputStream newManifest,
                   JarIndex jarIndex) throws IOException
    {
        ZipInputStream zis = new ZipInputStream(in);
        ZipOutputStream zos = new JarOutputStream(out);
        ZipEntry e = null;
        boolean foundManifest = false;
        byte[] buf = new byte[1024];
        int n = 0;
        boolean updateOk = true;

        if (jarIndex != null) {
            addIndex(jarIndex, zos);
        }

        // put the old entries first, replace if necessary
        while ((e = zis.getNextEntry()) != null) {
            String name = e.getName();

            boolean isManifestEntry = name.toUpperCase(
                                            java.util.Locale.ENGLISH).
                                        equals(MANIFEST);
            if ((name.toUpperCase().equals(INDEX) && jarIndex != null)
                || (Mflag && isManifestEntry)) {
                continue;
            } else if (isManifestEntry && ((newManifest != null) ||
                        (ename != null))) {
                foundManifest = true;
                if (newManifest != null) {
                    // Don't read from the newManifest InputStream, as we
                    // might need it below, and we can't re-read the same data
                    // twice.
                    FileInputStream fis = new FileInputStream(mname);
                    boolean ambigous = isAmbigousMainClass(new Manifest(fis));
                    fis.close();
                    if (ambigous) {
                        return false;
                    }
                }

                // Update the manifest.
                Manifest old = new Manifest(zis);
                if (newManifest != null) {
                    old.read(newManifest);
                }
                updateManifest(old, zos);
            } else {
                if (!entryMap.containsKey(name)) { // copy the old stuff
                    // do our own compression
                    ZipEntry e2 = new ZipEntry(name);
                    e2.setMethod(e.getMethod());
                    e2.setTime(e.getTime());
                    e2.setComment(e.getComment());
                    e2.setExtra(e.getExtra());
                    if (e.getMethod() == ZipEntry.STORED) {
                        e2.setSize(e.getSize());
                        e2.setCrc(e.getCrc());
                    }
                    zos.putNextEntry(e2);
                    while ((n = zis.read(buf, 0, buf.length)) != -1) {
                        zos.write(buf, 0, n);
                    }
                } else { // replace with the new files
                    File f = entryMap.get(name);
                    addFile(zos, f);
                    entryMap.remove(name);
                    entries.remove(f);
                }
            }
        }

        // add the remaining new files
        for (File f: entries) {
            addFile(zos, f);
        }
        if (!foundManifest) {
            if (newManifest != null) {
                Manifest m = new Manifest(newManifest);
                updateOk = !isAmbigousMainClass(m);
                if (updateOk) {
                    updateManifest(m, zos);
                }
            } else if (ename != null) {
                updateManifest(new Manifest(), zos);
            }
        }
        zis.close();
        zos.close();
        return updateOk;
    }


    private void addIndex(JarIndex index, ZipOutputStream zos)
        throws IOException
    {
        ZipEntry e = new ZipEntry(INDEX);
        e.setTime(System.currentTimeMillis());
        if (flag0) {
            e.setMethod(ZipEntry.STORED);
            File ifile = File.createTempFile("index", null, new File("."));
            BufferedOutputStream bos = new BufferedOutputStream
                (new FileOutputStream(ifile));
            index.write(bos);
            crc32File(e, ifile);
            bos.close();
            ifile.delete();
        }
        zos.putNextEntry(e);
        index.write(zos);
        if (vflag) {
            // output(getMsg("out.update.manifest"));
        }
    }

    private void updateManifest(Manifest m, ZipOutputStream zos)
        throws IOException
    {
        addVersion(m);
        addCreatedBy(m);
        if (ename != null) {
            addMainClass(m, ename);
        }
        ZipEntry e = new ZipEntry(MANIFEST);
        e.setTime(System.currentTimeMillis());
        if (flag0) {
            e.setMethod(ZipEntry.STORED);
            crc32Manifest(e, m);
        }
        zos.putNextEntry(e);
        m.write(zos);
        if (vflag) {
            output(getMsg("out.update.manifest"));
        }
    }


    private String entryName(String name) {
        name = name.replace(File.separatorChar, '/');
        String matchPath = "";
        for (String path : paths) {
            if (name.startsWith(path) && (path.length() > matchPath.length())) {
                matchPath = path;
            }
        }
        name = name.substring(matchPath.length());

        if (name.startsWith("/")) {
            name = name.substring(1);
        } else if (name.startsWith("./")) {
            name = name.substring(2);
        }
        return name;
    }

    private void addVersion(Manifest m) {
        Attributes global = m.getMainAttributes();
        if (global.getValue(Attributes.Name.MANIFEST_VERSION) == null) {
            global.put(Attributes.Name.MANIFEST_VERSION, VERSION);
        }
    }

    private void addCreatedBy(Manifest m) {
        Attributes global = m.getMainAttributes();
        if (global.getValue(new Attributes.Name("Created-By")) == null) {
            String javaVendor = System.getProperty("java.vendor");
            String jdkVersion = System.getProperty("java.version");
            global.put(new Attributes.Name("Created-By"), jdkVersion + " (" +
                        javaVendor + ")");
        }
    }

    private void addMainClass(Manifest m, String mainApp) {
        Attributes global = m.getMainAttributes();

        // overrides any existing Main-Class attribute
        global.put(Attributes.Name.MAIN_CLASS, mainApp);
    }

    private boolean isAmbigousMainClass(Manifest m) {
        if (ename != null) {
            Attributes global = m.getMainAttributes();
            if ((global.get(Attributes.Name.MAIN_CLASS) != null)) {
                error(getMsg("error.bad.eflag"));
                usageError();
                return true;
            }
        }
        return false;
    }

    /*
     * Adds a new file entry to the ZIP output stream.
     */
    void addFile(ZipOutputStream zos, File file) throws IOException {
        String name = file.getPath();
        boolean isDir = file.isDirectory();
        if (isDir) {
            name = name.endsWith(File.separator) ? name :
                (name + File.separator);
        }
        name = entryName(name);

        if (name.equals("") || name.equals(".") || name.equals(zname)) {
            return;
        } else if ((name.equals(MANIFEST_DIR) || name.equals(MANIFEST))
                   && !Mflag) {
            if (vflag) {
                output(formatMsg("out.ignore.entry", name));
            }
            return;
        }

        long size = isDir ? 0 : file.length();

        if (vflag) {
            out.print(formatMsg("out.adding", name));
        }
        ZipEntry e = new ZipEntry(name);
        e.setTime(file.lastModified());
        if (size == 0) {
            e.setMethod(ZipEntry.STORED);
            e.setSize(0);
            e.setCrc(0);
        } else if (flag0) {
            e.setSize(size);
            e.setMethod(ZipEntry.STORED);
            crc32File(e, file);
        }
        zos.putNextEntry(e);
        if (!isDir) {
            byte[] buf = new byte[8192];
            int len;
            InputStream is = new BufferedInputStream(new FileInputStream(file));
            while ((len = is.read(buf, 0, buf.length)) != -1) {
                zos.write(buf, 0, len);
            }
            is.close();
        }
        zos.closeEntry();
        /* report how much compression occurred. */
        if (vflag) {
            size = e.getSize();
            long csize = e.getCompressedSize();
            out.print(formatMsg2("out.size", String.valueOf(size),
                        String.valueOf(csize)));
            if (e.getMethod() == ZipEntry.DEFLATED) {
                long ratio = 0;
                if (size != 0) {
                    ratio = ((size - csize) * 100) / size;
                }
                output(formatMsg("out.deflated", String.valueOf(ratio)));
            } else {
                output(getMsg("out.stored"));
            }
        }
    }

    /*
     * compute the crc32 of a file.  This is necessary when the ZipOutputStream
     * is in STORED mode.
     */
    private void crc32Manifest(ZipEntry e, Manifest m) throws IOException {
        crc32.reset();
        CRC32OutputStream os = new CRC32OutputStream(crc32);
        m.write(os);
        e.setSize((long) os.n);
        e.setCrc(crc32.getValue());
    }

    /*
     * compute the crc32 of a file.  This is necessary when the ZipOutputStream
     * is in STORED mode.
     */
    private void crc32File(ZipEntry e, File f) throws IOException {
        InputStream is = new BufferedInputStream(new FileInputStream(f));
        byte[] buf = new byte[8192];
        crc32.reset();
        int r = 0;
        int nread = 0;
        long len = f.length();
        while ((r = is.read(buf)) != -1) {
            nread += r;
            crc32.update(buf, 0, r);
        }
        is.close();
        if (nread != (int) len) {
            throw new JarException(formatMsg(
                        "error.incorrect.length", f.getPath()));
        }
        e.setCrc(crc32.getValue());
    }

    void replaceFSC(String files[]) {
        if (files != null) {
            for (String file : files) {
                file = file.replace(File.separatorChar, '/');
            }
        }
    }

    Set<ZipEntry> newDirSet() {
        return new HashSet<ZipEntry>() {
            public boolean add(ZipEntry e) {
                return ((e == null || useExtractionTime) ? false : super.add(e));
            }};
    }

    void updateLastModifiedTime(Set<ZipEntry> zes) throws IOException {
        for (ZipEntry ze : zes) {
            long lastModified = ze.getTime();
            if (lastModified != -1) {
                File f = new File(ze.getName().replace('/', File.separatorChar));
                f.setLastModified(lastModified);
            }
        }
    }

    /*
     * Extracts specified entries from JAR file.
     */
    void extract(InputStream in, String files[]) throws IOException {
        ZipInputStream zis = new ZipInputStream(in);
        ZipEntry e;
        // Set of all directory entries specified in archive.  Disallows
        // null entries.  Disallows all entries if using pre-6.0 behavior.
        Set<ZipEntry> dirs = newDirSet();
        while ((e = zis.getNextEntry()) != null) {
            if (files == null) {
                dirs.add(extractFile(zis, e));
            } else {
                String name = e.getName();
                for (String file : files) {
                    if (name.startsWith(file)) {
                        dirs.add(extractFile(zis, e));
                        break;
                    }
                }
            }
        }

        // Update timestamps of directories specified in archive with their
        // timestamps as given in the archive.  We do this after extraction,
        // instead of during, because creating a file in a directory changes
        // that directory's timestamp.
        updateLastModifiedTime(dirs);
    }

    /*
     * Extracts specified entries from JAR file, via ZipFile.
     */
    void extract(String fname, String files[]) throws IOException {
        ZipFile zf = new ZipFile(fname);
        Set<ZipEntry> dirs = newDirSet();
        Enumeration<? extends ZipEntry> zes = zf.entries();
        while (zes.hasMoreElements()) {
            ZipEntry e = zes.nextElement();
            InputStream is;
            if (files == null) {
                dirs.add(extractFile(zf.getInputStream(e), e));
            } else {
                String name = e.getName();
                for (String file : files) {
                    if (name.startsWith(file)) {
                        dirs.add(extractFile(zf.getInputStream(e), e));
                        break;
                    }
                }
            }
        }
        zf.close();
        updateLastModifiedTime(dirs);
    }

    /*
     * Extracts next entry from JAR file, creating directories as needed.  If
     * the entry is for a directory which doesn't exist prior to this
     * invocation, returns that entry, otherwise returns null.
     */
    ZipEntry extractFile(InputStream is, ZipEntry e) throws IOException {
        ZipEntry rc = null;
        String name = e.getName();
        File f = new File(e.getName().replace('/', File.separatorChar));
        if (e.isDirectory()) {
            if (f.exists()) {
                if (!f.isDirectory()) {
                    throw new IOException(formatMsg("error.create.dir",
                        f.getPath()));
                }
            } else {
                if (!f.mkdirs()) {
                    throw new IOException(formatMsg("error.create.dir",
                        f.getPath()));
                } else {
                    rc = e;
                }
            }

            if (vflag) {
                output(formatMsg("out.create", name));
            }
        } else {
            if (f.getParent() != null) {
                File d = new File(f.getParent());
                if (!d.exists() && !d.mkdirs() || !d.isDirectory()) {
                    throw new IOException(formatMsg(
                        "error.create.dir", d.getPath()));
                }
            }
            OutputStream os = new FileOutputStream(f);
            byte[] b = new byte[8192];
            int len;
            try {
                while ((len = is.read(b, 0, b.length)) != -1) {
                    os.write(b, 0, len);
                }
            } finally {
                if (is instanceof ZipInputStream)
                    ((ZipInputStream)is).closeEntry();
                else
                    is.close();
                os.close();
            }
            if (vflag) {
                if (e.getMethod() == ZipEntry.DEFLATED) {
                    output(formatMsg("out.inflated", name));
                } else {
                    output(formatMsg("out.extracted", name));
                }
            }
        }
        if (!useExtractionTime) {
            long lastModified = e.getTime();
            if (lastModified != -1) {
                f.setLastModified(lastModified);
            }
        }
        return rc;
    }

    /*
     * Lists contents of JAR file.
     */
    void list(InputStream in, String files[]) throws IOException {
        ZipInputStream zis = new ZipInputStream(in);
        ZipEntry e;
        while ((e = zis.getNextEntry()) != null) {
            /*
             * In the case of a compressed (deflated) entry, the entry size
             * is stored immediately following the entry data and cannot be
             * determined until the entry is fully read. Therefore, we close
             * the entry first before printing out its attributes.
             */
            zis.closeEntry();
            printEntry(e, files);
        }
    }

    /*
     * Lists contents of JAR file, via ZipFile.
     */
    void list(String fname, String files[]) throws IOException {
        ZipFile zf = new ZipFile(fname);
        Enumeration<? extends ZipEntry> zes = zf.entries();
        while (zes.hasMoreElements()) {
            printEntry(zes.nextElement(), files);
        }
        zf.close();
    }

    /**
     * Output the class index table to the INDEX.LIST file of the
     * root jar file.
     */
    void dumpIndex(String rootjar, JarIndex index) throws IOException {
        File scratchFile = File.createTempFile("scratch", null, new File("."));
        File jarFile = new File(rootjar);
        boolean updateOk = update(new FileInputStream(jarFile),
                                  new FileOutputStream(scratchFile),
                                  null, index);
        jarFile.delete();
        if (!scratchFile.renameTo(jarFile)) {
            scratchFile.delete();
            throw new IOException(getMsg("error.write.file"));
        }
        scratchFile.delete();
    }

    private Hashtable jarTable = new Hashtable();
    /*
     * Generate the transitive closure of the Class-Path attribute for
     * the specified jar file.
     */
    Vector getJarPath(String jar) throws IOException {
        Vector files = new Vector();
        files.add(jar);
        jarTable.put(jar, jar);

        // take out the current path
        String path = jar.substring(0, Math.max(0, jar.lastIndexOf('/') + 1));

        // class path attribute will give us jar file name with
        // '/' as separators, so we need to change them to the
        // appropriate one before we open the jar file.
        JarFile rf = new JarFile(jar.replace('/', File.separatorChar));

        if (rf != null) {
            Manifest man = rf.getManifest();
            if (man != null) {
                Attributes attr = man.getMainAttributes();
                if (attr != null) {
                    String value = attr.getValue(Attributes.Name.CLASS_PATH);
                    if (value != null) {
                        StringTokenizer st = new StringTokenizer(value);
                        while (st.hasMoreTokens()) {
                            String ajar = st.nextToken();
                            if (!ajar.endsWith("/")) {  // it is a jar file
                                ajar = path.concat(ajar);
                                /* check on cyclic dependency */
                                if (jarTable.get(ajar) == null) {
                                    files.addAll(getJarPath(ajar));
                                }
                            }
                        }
                    }
                }
            }
        }
        rf.close();
        return files;
    }

    /**
     * Generate class index file for the specified root jar file.
     */
    void genIndex(String rootjar, String[] files) throws IOException {
        Vector jars = getJarPath(rootjar);
        int njars = jars.size();
        String[] jarfiles;

        if (njars == 1 && files != null) {
            // no class-path attribute defined in rootjar, will
            // use command line specified list of jars
            for (int i = 0; i < files.length; i++) {
                jars.addAll(getJarPath(files[i]));
            }
            njars = jars.size();
        }
        jarfiles = (String[])jars.toArray(new String[njars]);
        JarIndex index = new JarIndex(jarfiles);
        dumpIndex(rootjar, index);
    }

    /*
     * Prints entry information, if requested.
     */
    void printEntry(ZipEntry e, String[] files) throws IOException {
        if (files == null) {
            printEntry(e);
        } else {
            String name = e.getName();
            for (String file : files) {
                if (name.startsWith(file)) {
                    printEntry(e);
                    return;
                }
            }
        }
    }

    /*
     * Prints entry information.
     */
    void printEntry(ZipEntry e) throws IOException {
        if (vflag) {
            StringBuilder sb = new StringBuilder();
            String s = Long.toString(e.getSize());
            for (int i = 6 - s.length(); i > 0; --i) {
                sb.append(' ');
            }
            sb.append(s).append(' ').append(new Date(e.getTime()).toString());
            sb.append(' ').append(e.getName());
            output(sb.toString());
        } else {
            output(e.getName());
        }
    }

    /*
     * Print usage message and die.
     */
    void usageError() {
        error(getMsg("usage"));
    }

    /*
     * A fatal exception has been caught.  No recovery possible
     */
    void fatalError(Exception e) {
        e.printStackTrace();
    }

    /*
     * A fatal condition has been detected; message is "s".
     * No recovery possible
     */
    void fatalError(String s) {
        error(program + ": " + s);
    }

    /**
     * Print an output message; like verbose output and the like
     */
    protected void output(String s) {
        out.println(s);
    }

    /**
     * Print an error mesage; like something is broken
     */
    protected void error(String s) {
        err.println(s);
    }

    /*
     * Main routine to start program.
     */
    public static void main(String args[]) {
        Main jartool = new Main(System.out, System.err, "jar");
        System.exit(jartool.run(args) ? 0 : 1);
    }
}

/*
 * an OutputStream that doesn't send its output anywhere, (but could).
 * It's here to find the CRC32 of a manifest, necessary for STORED only
 * mode in ZIP.
 */
final class CRC32OutputStream extends java.io.OutputStream {
    CRC32 crc;
    int n = 0;
    CRC32OutputStream(CRC32 crc) {
        this.crc = crc;
    }

    public void write(int r) throws IOException {
        crc.update(r);
        n++;
    }

    public void write(byte[] b) throws IOException {
        crc.update(b, 0, b.length);
        n += b.length;
    }

    public void write(byte[] b, int off, int len) throws IOException {
        crc.update(b, off, len);
        n += len - off;
    }
}
