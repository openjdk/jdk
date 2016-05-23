/*
 * Copyright (c) 1996, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleDescriptor.Version;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolutionException;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.*;
import java.util.jar.*;
import java.util.jar.Pack200.*;
import java.util.jar.Manifest;
import java.text.MessageFormat;

import jdk.internal.misc.JavaLangModuleAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.module.ModuleHashes;
import jdk.internal.module.ModuleInfoExtender;
import jdk.internal.util.jar.JarIndex;

import static jdk.internal.util.jar.JarIndex.INDEX_NAME;
import static java.util.jar.JarFile.MANIFEST_NAME;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

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
    // All packages.
    Set<String> packages = new HashSet<>();
    // All actual entries added, or existing, in the jar file ( excl manifest
    // and module-info.class ). Populated during create or update.
    Set<String> jarEntries = new HashSet<>();

    // Directories specified by "-C" operation.
    Set<String> paths = new HashSet<String>();

    /*
     * cflag: create
     * uflag: update
     * xflag: xtract
     * tflag: table
     * vflag: verbose
     * flag0: no zip compression (store only)
     * Mflag: DO NOT generate a manifest file (just ZIP)
     * iflag: generate jar index
     * nflag: Perform jar normalization at the end
     * pflag: preserve/don't strip leading slash and .. component from file name
     */
    boolean cflag, uflag, xflag, tflag, vflag, flag0, Mflag, iflag, nflag, pflag;

    /* To support additional GNU Style informational options */
    enum Info {
        HELP(GNUStyleOptions::printHelp),
        COMPAT_HELP(GNUStyleOptions::printCompatHelp),
        USAGE_SUMMARY(GNUStyleOptions::printUsageSummary),
        VERSION(GNUStyleOptions::printVersion);

        private Consumer<PrintStream> printFunction;
        Info(Consumer<PrintStream> f) { this.printFunction = f; }
        void print(PrintStream out) { printFunction.accept(out); }
    };
    Info info;

    /* Modular jar related options */
    boolean printModuleDescriptor;
    Version moduleVersion;
    Pattern modulesToHash;
    ModuleFinder moduleFinder = ModuleFinder.of();

    private static final String MODULE_INFO = "module-info.class";

    static final String MANIFEST_DIR = "META-INF/";
    static final String VERSIONS_DIR = MANIFEST_DIR + "versions/";
    static final String VERSION = "1.0";

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

    static String getMsg(String key) {
        try {
            return (rsrc.getString(key));
        } catch (MissingResourceException e) {
            throw new Error("Error in message file");
        }
    }

    static String formatMsg(String key, String arg) {
        String msg = getMsg(key);
        String[] args = new String[1];
        args[0] = arg;
        return MessageFormat.format(msg, (Object[]) args);
    }

    static String formatMsg2(String key, String arg, String arg1) {
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

    /**
     * Creates a new empty temporary file in the same directory as the
     * specified file.  A variant of File.createTempFile.
     */
    private static File createTempFileInSameDirectoryAs(File file)
        throws IOException {
        File dir = file.getParentFile();
        if (dir == null)
            dir = new File(".");
        return File.createTempFile("jartmp", null, dir);
    }

    private boolean ok;

    /**
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
                    if (isAmbiguousMainClass(manifest)) {
                        if (in != null) {
                            in.close();
                        }
                        return false;
                    }
                    if (ename != null) {
                        addMainClass(manifest, ename);
                    }
                }
                Map<String,Path> moduleInfoPaths = new HashMap<>();
                expand(null, files, false, moduleInfoPaths);

                Map<String,byte[]> moduleInfos = new LinkedHashMap<>();
                if (!moduleInfoPaths.isEmpty()) {
                    if (!checkModuleInfos(moduleInfoPaths))
                        return false;

                    // root module-info first
                    byte[] b = readModuleInfo(moduleInfoPaths.get(MODULE_INFO));
                    moduleInfos.put(MODULE_INFO, b);
                    for (Map.Entry<String,Path> e : moduleInfoPaths.entrySet())
                        moduleInfos.putIfAbsent(e.getKey(), readModuleInfo(e.getValue()));

                    if (!addExtendedModuleAttributes(moduleInfos))
                        return false;

                    // Basic consistency checks for modular jars.
                    if (!checkServices(moduleInfos.get(MODULE_INFO)))
                        return false;

                } else if (moduleVersion != null || modulesToHash != null) {
                    error(getMsg("error.module.options.without.info"));
                    return false;
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
                File tmpfile = null;
                final OutputStream finalout = out;
                final String tmpbase = (fname == null)
                        ? "tmpjar"
                        : fname.substring(fname.indexOf(File.separatorChar) + 1);
                if (nflag) {
                    tmpfile = createTemporaryFile(tmpbase, ".jar");
                    out = new FileOutputStream(tmpfile);
                }
                create(new BufferedOutputStream(out, 4096), manifest, moduleInfos);

                if (in != null) {
                    in.close();
                }
                out.close();
                if (nflag) {
                    JarFile jarFile = null;
                    File packFile = null;
                    JarOutputStream jos = null;
                    try {
                        Packer packer = Pack200.newPacker();
                        Map<String, String> p = packer.properties();
                        p.put(Packer.EFFORT, "1"); // Minimal effort to conserve CPU
                        jarFile = new JarFile(tmpfile.getCanonicalPath());
                        packFile = createTemporaryFile(tmpbase, ".pack");
                        out = new FileOutputStream(packFile);
                        packer.pack(jarFile, out);
                        jos = new JarOutputStream(finalout);
                        Unpacker unpacker = Pack200.newUnpacker();
                        unpacker.unpack(packFile, jos);
                    } catch (IOException ioe) {
                        fatalError(ioe);
                    } finally {
                        if (jarFile != null) {
                            jarFile.close();
                        }
                        if (out != null) {
                            out.close();
                        }
                        if (jos != null) {
                            jos.close();
                        }
                        if (tmpfile != null && tmpfile.exists()) {
                            tmpfile.delete();
                        }
                        if (packFile != null && packFile.exists()) {
                            packFile.delete();
                        }
                    }
                }
            } else if (uflag) {
                File inputFile = null, tmpFile = null;
                FileInputStream in;
                FileOutputStream out;
                if (fname != null) {
                    inputFile = new File(fname);
                    tmpFile = createTempFileInSameDirectoryAs(inputFile);
                    in = new FileInputStream(inputFile);
                    out = new FileOutputStream(tmpFile);
                } else {
                    in = new FileInputStream(FileDescriptor.in);
                    out = new FileOutputStream(FileDescriptor.out);
                    vflag = false;
                }
                InputStream manifest = (!Mflag && (mname != null)) ?
                    (new FileInputStream(mname)) : null;

                Map<String,Path> moduleInfoPaths = new HashMap<>();
                expand(null, files, true, moduleInfoPaths);

                Map<String,byte[]> moduleInfos = new HashMap<>();
                for (Map.Entry<String,Path> e : moduleInfoPaths.entrySet())
                    moduleInfos.put(e.getKey(), readModuleInfo(e.getValue()));

                boolean updateOk = update(in, new BufferedOutputStream(out),
                                          manifest, moduleInfos, null);

                // Consistency checks for modular jars.
                if (!moduleInfos.isEmpty()) {
                    if(!checkServices(moduleInfos.get(MODULE_INFO)))
                        return false;
                }

                if (ok) {
                    ok = updateOk;
                }
                in.close();
                out.close();
                if (manifest != null) {
                    manifest.close();
                }
                if (ok && fname != null) {
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
                // For the "list table contents" action, access using the
                // ZipFile class is always most efficient since only a
                // "one-finger" scan through the central directory is required.
                if (fname != null) {
                    list(fname, files);
                } else {
                    InputStream in = new FileInputStream(FileDescriptor.in);
                    try {
                        list(new BufferedInputStream(in), files);
                    } finally {
                        in.close();
                    }
                }
            } else if (xflag) {
                replaceFSC(files);
                // For the extract action, when extracting all the entries,
                // access using the ZipInputStream class is most efficient,
                // since only a single sequential scan through the zip file is
                // required.  When using the ZipFile class, a "two-finger" scan
                // is required, but this is likely to be more efficient when a
                // partial extract is requested.  In case the zip file has
                // "leading garbage", we fall back from the ZipInputStream
                // implementation to the ZipFile implementation, since only the
                // latter can handle it.
                if (fname != null && files != null) {
                    extract(fname, files);
                } else {
                    InputStream in = (fname == null)
                        ? new FileInputStream(FileDescriptor.in)
                        : new FileInputStream(fname);
                    try {
                        if (!extract(new BufferedInputStream(in), files) && fname != null) {
                            extract(fname, files);
                        }
                    } finally {
                        in.close();
                    }
                }
            } else if (iflag) {
                genIndex(rootjar, files);
            } else if (printModuleDescriptor) {
                boolean found;
                if (fname != null) {
                    found = printModuleDescriptor(new ZipFile(fname));
                } else {
                    try (FileInputStream fin = new FileInputStream(FileDescriptor.in)) {
                        found = printModuleDescriptor(fin);
                    }
                }
                if (!found)
                    error(getMsg("error.module.descriptor.not.found"));
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

    /**
     * Parses command line arguments.
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

            // Note: flags.length == 2 can be treated as the short version of
            // the GNU option since the there cannot be any other options,
            // excluding -C, as per the old way.
            if (flags.startsWith("--")
                || (flags.startsWith("-") && flags.length() == 2)) {
                try {
                    count = GNUStyleOptions.parseOptions(this, args);
                } catch (GNUStyleOptions.BadArgs x) {
                    if (info != null) {
                        info.print(out);
                        return true;
                    }
                    error(x.getMessage());
                    if (x.showUsage)
                        Info.USAGE_SUMMARY.print(err);
                    return false;
                }
            } else {
                // Legacy/compatibility options
                if (flags.startsWith("-")) {
                    flags = flags.substring(1);
                }
                for (int i = 0; i < flags.length(); i++) {
                    switch (flags.charAt(i)) {
                        case 'c':
                            if (xflag || tflag || uflag || iflag) {
                                usageError();
                                return false;
                            }
                            cflag = true;
                            break;
                        case 'u':
                            if (cflag || xflag || tflag || iflag) {
                                usageError();
                                return false;
                            }
                            uflag = true;
                            break;
                        case 'x':
                            if (cflag || uflag || tflag || iflag) {
                                usageError();
                                return false;
                            }
                            xflag = true;
                            break;
                        case 't':
                            if (cflag || uflag || xflag || iflag) {
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
                            if (cflag || uflag || xflag || tflag) {
                                usageError();
                                return false;
                            }
                            // do not increase the counter, files will contain rootjar
                            rootjar = args[count++];
                            iflag = true;
                            break;
                        case 'n':
                            nflag = true;
                            break;
                        case 'e':
                            ename = args[count++];
                            break;
                        case 'P':
                            pflag = true;
                            break;
                        default:
                            error(formatMsg("error.illegal.option",
                                    String.valueOf(flags.charAt(i))));
                            usageError();
                            return false;
                    }
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            usageError();
            return false;
        }

        if (info != null) {
            info.print(out);
            return true;
        }

        if (!cflag && !tflag && !xflag && !uflag && !iflag && !printModuleDescriptor) {
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

    private static Set<String> findPackages(ZipFile zf) {
        return zf.stream()
                 .filter(e -> e.getName().endsWith(".class"))
                 .map(e -> toPackageName(e))
                 .filter(pkg -> pkg.length() > 0)
                 .distinct()
                 .collect(Collectors.toSet());
    }

    private static String toPackageName(ZipEntry entry) {
        return toPackageName(entry.getName());
    }

    private static String toPackageName(String path) {
        assert path.endsWith(".class");
        int index = path.lastIndexOf('/');
        if (index != -1) {
            return path.substring(0, index).replace('/', '.');
        } else {
            return "";
        }
    }

    /**
     * Expands list of files to process into full list of all files that
     * can be found by recursively descending directories.
     */
    void expand(File dir,
                String[] files,
                boolean isUpdate,
                Map<String,Path> moduleInfoPaths)
        throws IOException
    {
        if (files == null)
            return;

        for (int i = 0; i < files.length; i++) {
            File f;
            if (dir == null)
                f = new File(files[i]);
            else
                f = new File(dir, files[i]);

            if (f.isFile()) {
                String path = f.getPath();
                String entryName = entryName(path);
                if (entryName.endsWith(MODULE_INFO)) {
                    moduleInfoPaths.put(entryName, f.toPath());
                    if (isUpdate)
                        entryMap.put(entryName, f);
                } else if (entries.add(f)) {
                    jarEntries.add(entryName);
                    if (path.endsWith(".class") && !entryName.startsWith(VERSIONS_DIR))
                        packages.add(toPackageName(entryName));
                    if (isUpdate)
                        entryMap.put(entryName, f);
                }
            } else if (f.isDirectory()) {
                if (entries.add(f)) {
                    if (isUpdate) {
                        String dirPath = f.getPath();
                        dirPath = (dirPath.endsWith(File.separator)) ? dirPath :
                            (dirPath + File.separator);
                        entryMap.put(entryName(dirPath), f);
                    }
                    expand(f, f.list(), isUpdate, moduleInfoPaths);
                }
            } else {
                error(formatMsg("error.nosuch.fileordir", String.valueOf(f)));
                ok = false;
            }
        }
    }

    /**
     * Creates a new JAR file.
     */
    void create(OutputStream out, Manifest manifest, Map<String,byte[]> moduleInfos)
        throws IOException
    {
        ZipOutputStream zos = new JarOutputStream(out);
        if (flag0) {
            zos.setMethod(ZipOutputStream.STORED);
        }
        // TODO: check module-info attributes against manifest ??
        if (manifest != null) {
            if (vflag) {
                output(getMsg("out.added.manifest"));
            }
            ZipEntry e = new ZipEntry(MANIFEST_DIR);
            e.setTime(System.currentTimeMillis());
            e.setSize(0);
            e.setCrc(0);
            zos.putNextEntry(e);
            e = new ZipEntry(MANIFEST_NAME);
            e.setTime(System.currentTimeMillis());
            if (flag0) {
                crc32Manifest(e, manifest);
            }
            zos.putNextEntry(e);
            manifest.write(zos);
            zos.closeEntry();
        }
        for (Map.Entry<String,byte[]> mi : moduleInfos.entrySet()) {
            String entryName = mi.getKey();
            byte[] miBytes = mi.getValue();
            if (vflag) {
                output(formatMsg("out.added.module-info", entryName));
            }
            ZipEntry e = new ZipEntry(mi.getKey());
            e.setTime(System.currentTimeMillis());
            if (flag0) {
                crc32ModuleInfo(e, miBytes);
            }
            zos.putNextEntry(e);
            ByteArrayInputStream in = new ByteArrayInputStream(miBytes);
            in.transferTo(zos);
            zos.closeEntry();
        }
        for (File file: entries) {
            addFile(zos, file);
        }
        zos.close();
    }

    private char toUpperCaseASCII(char c) {
        return (c < 'a' || c > 'z') ? c : (char) (c + 'A' - 'a');
    }

    /**
     * Compares two strings for equality, ignoring case.  The second
     * argument must contain only upper-case ASCII characters.
     * We don't want case comparison to be locale-dependent (else we
     * have the notorious "turkish i bug").
     */
    private boolean equalsIgnoreCase(String s, String upper) {
        assert upper.toUpperCase(java.util.Locale.ENGLISH).equals(upper);
        int len;
        if ((len = s.length()) != upper.length())
            return false;
        for (int i = 0; i < len; i++) {
            char c1 = s.charAt(i);
            char c2 = upper.charAt(i);
            if (c1 != c2 && toUpperCaseASCII(c1) != c2)
                return false;
        }
        return true;
    }

    /**
     * Returns true of the given module-info's are located in acceptable
     * locations.  Otherwise, outputs an appropriate message and returns false.
     */
    private boolean checkModuleInfos(Map<String,?> moduleInfos) {
        // there must always be, at least, a root module-info
        if (!moduleInfos.containsKey(MODULE_INFO)) {
            error(getMsg("error.versioned.info.without.root"));
            return false;
        }

        // module-info can only appear in the root, or a versioned section
        Optional<String> other = moduleInfos.keySet().stream()
                .filter(x -> !x.equals(MODULE_INFO))
                .filter(x -> !x.startsWith(VERSIONS_DIR))
                .findFirst();

        if (other.isPresent()) {
            error(formatMsg("error.unexpected.module-info", other.get()));
            return false;
        }
        return true;
    }

    /**
     * Updates an existing jar file.
     */
    boolean update(InputStream in, OutputStream out,
                   InputStream newManifest,
                   Map<String,byte[]> moduleInfos,
                   JarIndex jarIndex) throws IOException
    {
        ZipInputStream zis = new ZipInputStream(in);
        ZipOutputStream zos = new JarOutputStream(out);
        ZipEntry e = null;
        boolean foundManifest = false;
        boolean updateOk = true;

        if (jarIndex != null) {
            addIndex(jarIndex, zos);
        }

        // put the old entries first, replace if necessary
        while ((e = zis.getNextEntry()) != null) {
            String name = e.getName();

            boolean isManifestEntry = equalsIgnoreCase(name, MANIFEST_NAME);
            boolean isModuleInfoEntry = name.endsWith(MODULE_INFO);

            if ((jarIndex != null && equalsIgnoreCase(name, INDEX_NAME))
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
                    boolean ambiguous = isAmbiguousMainClass(new Manifest(fis));
                    fis.close();
                    if (ambiguous) {
                        return false;
                    }
                }

                // Update the manifest.
                Manifest old = new Manifest(zis);
                if (newManifest != null) {
                    old.read(newManifest);
                }
                if (!updateManifest(old, zos)) {
                    return false;
                }
            } else if (moduleInfos != null && isModuleInfoEntry) {
                moduleInfos.putIfAbsent(name, readModuleInfo(zis));
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
                    copy(zis, zos);
                } else { // replace with the new files
                    File f = entryMap.get(name);
                    addFile(zos, f);
                    entryMap.remove(name);
                    entries.remove(f);
                }

                jarEntries.add(name);
                if (name.endsWith(".class"))
                    packages.add(toPackageName(name));
            }
        }

        // add the remaining new files
        for (File f: entries) {
            addFile(zos, f);
        }
        if (!foundManifest) {
            if (newManifest != null) {
                Manifest m = new Manifest(newManifest);
                updateOk = !isAmbiguousMainClass(m);
                if (updateOk) {
                    if (!updateManifest(m, zos)) {
                        updateOk = false;
                    }
                }
            } else if (ename != null) {
                if (!updateManifest(new Manifest(), zos)) {
                    updateOk = false;
                }
            }
        }

        if (moduleInfos != null && !moduleInfos.isEmpty()) {
            if (!checkModuleInfos(moduleInfos))
                updateOk = false;

            if (updateOk) {
                if (!addExtendedModuleAttributes(moduleInfos))
                    updateOk = false;
            }

            // TODO: check manifest main classes, etc

            if (updateOk) {
                for (Map.Entry<String,byte[]> mi : moduleInfos.entrySet()) {
                    if (!updateModuleInfo(mi.getValue(), zos, mi.getKey()))
                        updateOk = false;
                }
            }
        } else if (moduleVersion != null || modulesToHash != null) {
            error(getMsg("error.module.options.without.info"));
            updateOk = false;
        }

        zis.close();
        zos.close();
        return updateOk;
    }


    private void addIndex(JarIndex index, ZipOutputStream zos)
        throws IOException
    {
        ZipEntry e = new ZipEntry(INDEX_NAME);
        e.setTime(System.currentTimeMillis());
        if (flag0) {
            CRC32OutputStream os = new CRC32OutputStream();
            index.write(os);
            os.updateEntry(e);
        }
        zos.putNextEntry(e);
        index.write(zos);
        zos.closeEntry();
    }

    private boolean updateModuleInfo(byte[] moduleInfoBytes, ZipOutputStream zos, String entryName)
        throws IOException
    {
        ZipEntry e = new ZipEntry(entryName);
        e.setTime(System.currentTimeMillis());
        if (flag0) {
            crc32ModuleInfo(e, moduleInfoBytes);
        }
        zos.putNextEntry(e);
        zos.write(moduleInfoBytes);
        if (vflag) {
            output(formatMsg("out.update.module-info", entryName));
        }
        return true;
    }

    private boolean updateManifest(Manifest m, ZipOutputStream zos)
        throws IOException
    {
        addVersion(m);
        addCreatedBy(m);
        if (ename != null) {
            addMainClass(m, ename);
        }
        ZipEntry e = new ZipEntry(MANIFEST_NAME);
        e.setTime(System.currentTimeMillis());
        if (flag0) {
            crc32Manifest(e, m);
        }
        zos.putNextEntry(e);
        m.write(zos);
        if (vflag) {
            output(getMsg("out.update.manifest"));
        }
        return true;
    }

    private static final boolean isWinDriveLetter(char c) {
        return ((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z'));
    }

    private String safeName(String name) {
        if (!pflag) {
            int len = name.length();
            int i = name.lastIndexOf("../");
            if (i == -1) {
                i = 0;
            } else {
                i += 3; // strip any dot-dot components
            }
            if (File.separatorChar == '\\') {
                // the spec requests no drive letter. skip if
                // the entry name has one.
                while (i < len) {
                    int off = i;
                    if (i + 1 < len &&
                        name.charAt(i + 1) == ':' &&
                        isWinDriveLetter(name.charAt(i))) {
                        i += 2;
                    }
                    while (i < len && name.charAt(i) == '/') {
                        i++;
                    }
                    if (i == off) {
                        break;
                    }
                }
            } else {
                while (i < len && name.charAt(i) == '/') {
                    i++;
                }
            }
            if (i != 0) {
                name = name.substring(i);
            }
        }
        return name;
    }

    private String entryName(String name) {
        name = name.replace(File.separatorChar, '/');
        String matchPath = "";
        for (String path : paths) {
            if (name.startsWith(path)
                && (path.length() > matchPath.length())) {
                matchPath = path;
            }
        }
        name = safeName(name.substring(matchPath.length()));
        // the old implementaton doesn't remove
        // "./" if it was led by "/" (?)
        if (name.startsWith("./")) {
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

    private boolean isAmbiguousMainClass(Manifest m) {
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

    /**
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
        } else if ((name.equals(MANIFEST_DIR) || name.equals(MANIFEST_NAME))
                   && !Mflag) {
            if (vflag) {
                output(formatMsg("out.ignore.entry", name));
            }
            return;
        } else if (name.equals(MODULE_INFO)) {
            throw new Error("Unexpected module info: " + name);
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
            crc32File(e, file);
        }
        zos.putNextEntry(e);
        if (!isDir) {
            copy(file, zos);
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

    /**
     * A buffer for use only by copy(InputStream, OutputStream).
     * Not as clean as allocating a new buffer as needed by copy,
     * but significantly more efficient.
     */
    private byte[] copyBuf = new byte[8192];

    /**
     * Copies all bytes from the input stream to the output stream.
     * Does not close or flush either stream.
     *
     * @param from the input stream to read from
     * @param to the output stream to write to
     * @throws IOException if an I/O error occurs
     */
    private void copy(InputStream from, OutputStream to) throws IOException {
        int n;
        while ((n = from.read(copyBuf)) != -1)
            to.write(copyBuf, 0, n);
    }

    /**
     * Copies all bytes from the input file to the output stream.
     * Does not close or flush the output stream.
     *
     * @param from the input file to read from
     * @param to the output stream to write to
     * @throws IOException if an I/O error occurs
     */
    private void copy(File from, OutputStream to) throws IOException {
        InputStream in = new FileInputStream(from);
        try {
            copy(in, to);
        } finally {
            in.close();
        }
    }

    /**
     * Copies all bytes from the input stream to the output file.
     * Does not close the input stream.
     *
     * @param from the input stream to read from
     * @param to the output file to write to
     * @throws IOException if an I/O error occurs
     */
    private void copy(InputStream from, File to) throws IOException {
        OutputStream out = new FileOutputStream(to);
        try {
            copy(from, out);
        } finally {
            out.close();
        }
    }

    /**
     * Computes the crc32 of a module-info.class.  This is necessary when the
     * ZipOutputStream is in STORED mode.
     */
    private void crc32ModuleInfo(ZipEntry e, byte[] bytes) throws IOException {
        CRC32OutputStream os = new CRC32OutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        in.transferTo(os);
        os.updateEntry(e);
    }

    /**
     * Computes the crc32 of a Manifest.  This is necessary when the
     * ZipOutputStream is in STORED mode.
     */
    private void crc32Manifest(ZipEntry e, Manifest m) throws IOException {
        CRC32OutputStream os = new CRC32OutputStream();
        m.write(os);
        os.updateEntry(e);
    }

    /**
     * Computes the crc32 of a File.  This is necessary when the
     * ZipOutputStream is in STORED mode.
     */
    private void crc32File(ZipEntry e, File f) throws IOException {
        CRC32OutputStream os = new CRC32OutputStream();
        copy(f, os);
        if (os.n != f.length()) {
            throw new JarException(formatMsg(
                        "error.incorrect.length", f.getPath()));
        }
        os.updateEntry(e);
    }

    void replaceFSC(String files[]) {
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                files[i] = files[i].replace(File.separatorChar, '/');
            }
        }
    }

    @SuppressWarnings("serial")
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
                String name = safeName(ze.getName().replace(File.separatorChar, '/'));
                if (name.length() != 0) {
                    File f = new File(name.replace('/', File.separatorChar));
                    f.setLastModified(lastModified);
                }
            }
        }
    }

    /**
     * Extracts specified entries from JAR file.
     *
     * @return whether entries were found and successfully extracted
     * (indicating this was a zip file without "leading garbage")
     */
    boolean extract(InputStream in, String files[]) throws IOException {
        ZipInputStream zis = new ZipInputStream(in);
        ZipEntry e;
        // Set of all directory entries specified in archive.  Disallows
        // null entries.  Disallows all entries if using pre-6.0 behavior.
        boolean entriesFound = false;
        Set<ZipEntry> dirs = newDirSet();
        while ((e = zis.getNextEntry()) != null) {
            entriesFound = true;
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

        return entriesFound;
    }

    /**
     * Extracts specified entries from JAR file, via ZipFile.
     */
    void extract(String fname, String files[]) throws IOException {
        ZipFile zf = new ZipFile(fname);
        Set<ZipEntry> dirs = newDirSet();
        Enumeration<? extends ZipEntry> zes = zf.entries();
        while (zes.hasMoreElements()) {
            ZipEntry e = zes.nextElement();
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

    /**
     * Extracts next entry from JAR file, creating directories as needed.  If
     * the entry is for a directory which doesn't exist prior to this
     * invocation, returns that entry, otherwise returns null.
     */
    ZipEntry extractFile(InputStream is, ZipEntry e) throws IOException {
        ZipEntry rc = null;
        // The spec requres all slashes MUST be forward '/', it is possible
        // an offending zip/jar entry may uses the backwards slash in its
        // name. It might cause problem on Windows platform as it skips
        // our "safe" check for leading slahs and dot-dot. So replace them
        // with '/'.
        String name = safeName(e.getName().replace(File.separatorChar, '/'));
        if (name.length() == 0) {
            return rc;    // leading '/' or 'dot-dot' only path
        }
        File f = new File(name.replace('/', File.separatorChar));
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
            try {
                copy(is, f);
            } finally {
                if (is instanceof ZipInputStream)
                    ((ZipInputStream)is).closeEntry();
                else
                    is.close();
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

    /**
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

    /**
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
     * Outputs the class index table to the INDEX.LIST file of the
     * root jar file.
     */
    void dumpIndex(String rootjar, JarIndex index) throws IOException {
        File jarFile = new File(rootjar);
        Path jarPath = jarFile.toPath();
        Path tmpPath = createTempFileInSameDirectoryAs(jarFile).toPath();
        try {
            if (update(Files.newInputStream(jarPath),
                       Files.newOutputStream(tmpPath),
                       null, null, index)) {
                try {
                    Files.move(tmpPath, jarPath, REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new IOException(getMsg("error.write.file"), e);
                }
            }
        } finally {
            Files.deleteIfExists(tmpPath);
        }
    }

    private HashSet<String> jarPaths = new HashSet<String>();

    /**
     * Generates the transitive closure of the Class-Path attribute for
     * the specified jar file.
     */
    List<String> getJarPath(String jar) throws IOException {
        List<String> files = new ArrayList<String>();
        files.add(jar);
        jarPaths.add(jar);

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
                                if (! jarPaths.contains(ajar)) {
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
     * Generates class index file for the specified root jar file.
     */
    void genIndex(String rootjar, String[] files) throws IOException {
        List<String> jars = getJarPath(rootjar);
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
        jarfiles = jars.toArray(new String[njars]);
        JarIndex index = new JarIndex(jarfiles);
        dumpIndex(rootjar, index);
    }

    /**
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

    /**
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

    /**
     * Prints usage message.
     */
    void usageError() {
        Info.USAGE_SUMMARY.print(err);
    }

    /**
     * A fatal exception has been caught.  No recovery possible
     */
    void fatalError(Exception e) {
        e.printStackTrace();
    }

    /**
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
     * Print an error message; like something is broken
     */
    protected void error(String s) {
        err.println(s);
    }

    /**
     * Main routine to start program.
     */
    public static void main(String args[]) {
        Main jartool = new Main(System.out, System.err, "jar");
        System.exit(jartool.run(args) ? 0 : 1);
    }

    /**
     * An OutputStream that doesn't send its output anywhere, (but could).
     * It's here to find the CRC32 of an input file, necessary for STORED
     * mode in ZIP.
     */
    private static class CRC32OutputStream extends java.io.OutputStream {
        final CRC32 crc = new CRC32();
        long n = 0;

        CRC32OutputStream() {}

        public void write(int r) throws IOException {
            crc.update(r);
            n++;
        }

        public void write(byte[] b, int off, int len) throws IOException {
            crc.update(b, off, len);
            n += len;
        }

        /**
         * Updates a ZipEntry which describes the data read by this
         * output stream, in STORED mode.
         */
        public void updateEntry(ZipEntry e) {
            e.setMethod(ZipEntry.STORED);
            e.setSize(n);
            e.setCrc(crc.getValue());
        }
    }

    /**
     * Attempt to create temporary file in the system-provided temporary folder, if failed attempts
     * to create it in the same folder as the file in parameter (if any)
     */
    private File createTemporaryFile(String tmpbase, String suffix) {
        File tmpfile = null;

        try {
            tmpfile = File.createTempFile(tmpbase, suffix);
        } catch (IOException | SecurityException e) {
            // Unable to create file due to permission violation or security exception
        }
        if (tmpfile == null) {
            // Were unable to create temporary file, fall back to temporary file in the same folder
            if (fname != null) {
                try {
                    File tmpfolder = new File(fname).getAbsoluteFile().getParentFile();
                    tmpfile = File.createTempFile(fname, ".tmp" + suffix, tmpfolder);
                } catch (IOException ioe) {
                    // Last option failed - fall gracefully
                    fatalError(ioe);
                }
            } else {
                // No options left - we can not compress to stdout without access to the temporary folder
                fatalError(new IOException(getMsg("error.create.tempfile")));
            }
        }
        return tmpfile;
    }

    private static byte[] readModuleInfo(InputStream zis) throws IOException {
        return zis.readAllBytes();
    }

    private static byte[] readModuleInfo(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return is.readAllBytes();
        }
    }

    // Modular jar support

    static <T> String toString(Set<T> set,
                               CharSequence prefix,
                               CharSequence suffix ) {
        if (set.isEmpty())
            return "";

        return set.stream().map(e -> e.toString())
                           .collect(joining(", ", prefix, suffix));
    }

    private boolean printModuleDescriptor(ZipFile zipFile)
        throws IOException
    {
        ZipEntry entry = zipFile.getEntry(MODULE_INFO);
        if (entry ==  null)
            return false;

        try (InputStream is = zipFile.getInputStream(entry)) {
            printModuleDescriptor(is);
        }
        return true;
    }

    private boolean printModuleDescriptor(FileInputStream fis)
        throws IOException
    {
        try (BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream zis = new ZipInputStream(bis)) {

            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().equals(MODULE_INFO)) {
                    printModuleDescriptor(zis);
                    return true;
                }
            }
        }
        return false;
    }

    static <T> String toString(Set<T> set) {
        if (set.isEmpty()) { return ""; }
        return set.stream().map(e -> e.toString().toLowerCase(Locale.ROOT))
                  .collect(joining(" "));
    }

    private static final JavaLangModuleAccess JLMA = SharedSecrets.getJavaLangModuleAccess();

    private void printModuleDescriptor(InputStream entryInputStream)
        throws IOException
    {
        ModuleDescriptor md = ModuleDescriptor.read(entryInputStream);
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(md.toNameAndVersion());

        md.requires().stream()
            .sorted(Comparator.comparing(Requires::name))
            .forEach(r -> {
                sb.append("\n  requires ");
                if (!r.modifiers().isEmpty())
                    sb.append(toString(r.modifiers())).append(" ");
                sb.append(r.name());
            });

        md.uses().stream().sorted()
            .forEach(p -> sb.append("\n  uses ").append(p));

        md.exports().stream()
            .sorted(Comparator.comparing(Exports::source))
            .forEach(p -> sb.append("\n  exports ").append(p));

        md.conceals().stream().sorted()
            .forEach(p -> sb.append("\n  conceals ").append(p));

        md.provides().values().stream()
            .sorted(Comparator.comparing(Provides::service))
            .forEach(p -> sb.append("\n  provides ").append(p.service())
                            .append(" with ")
                            .append(toString(p.providers())));

        md.mainClass().ifPresent(v -> sb.append("\n  main-class " + v));

        md.osName().ifPresent(v -> sb.append("\n  operating-system-name " + v));

        md.osArch().ifPresent(v -> sb.append("\n  operating-system-architecture " + v));

        md.osVersion().ifPresent(v -> sb.append("\n  operating-system-version " + v));

        JLMA.hashes(md).ifPresent(hashes ->
                hashes.names().stream().sorted().forEach(
                    mod -> sb.append("\n  hashes ").append(mod).append(" ")
                             .append(hashes.algorithm()).append(" ")
                             .append(hashes.hashFor(mod))));

        output(sb.toString());
    }

    private static String toBinaryName(String classname) {
        return (classname.replace('.', '/')) + ".class";
    }

    /* A module must have the implementation class of the services it 'provides'. */
    private boolean checkServices(byte[] moduleInfoBytes)
        throws IOException
    {
        ModuleDescriptor md = ModuleDescriptor.read(ByteBuffer.wrap(moduleInfoBytes));
        Set<String> missing = md.provides()
                                .values()
                                .stream()
                                .map(Provides::providers)
                                .flatMap(Set::stream)
                                .filter(p -> !jarEntries.contains(toBinaryName(p)))
                                .collect(Collectors.toSet());
        if (missing.size() > 0) {
            missing.stream().forEach(s -> fatalError(formatMsg("error.missing.provider", s)));
            return false;
        }
        return true;
    }

    /**
     * Adds extended modules attributes to the given module-info's.  The given
     * Map values are updated in-place. Returns false if an error occurs.
     */
    private boolean addExtendedModuleAttributes(Map<String,byte[]> moduleInfos)
        throws IOException
    {
        assert !moduleInfos.isEmpty() && moduleInfos.get(MODULE_INFO) != null;

        ByteBuffer bb = ByteBuffer.wrap(moduleInfos.get(MODULE_INFO));
        ModuleDescriptor rd = ModuleDescriptor.read(bb);

        Set<String> exports = rd.exports()
                                .stream()
                                .map(Exports::source)
                                .collect(toSet());

        Set<String> conceals = packages.stream()
                                       .filter(p -> !exports.contains(p))
                                       .collect(toSet());

        for (Map.Entry<String,byte[]> e: moduleInfos.entrySet()) {
            ModuleDescriptor vd = ModuleDescriptor.read(ByteBuffer.wrap(e.getValue()));
            if (!(isValidVersionedDescriptor(vd, rd)))
                return false;
            e.setValue(extendedInfoBytes(rd, vd, e.getValue(), conceals));
        }
        return true;
    }

    private static boolean isPlatformModule(String name) {
        return name.startsWith("java.") || name.startsWith("jdk.");
    }

    /**
     * Tells whether or not the given versioned module descriptor's attributes
     * are valid when compared against the given root module descriptor.
     *
     * A versioned module descriptor must be identical to the root module
     * descriptor, with two exceptions:
     *  - A versioned descriptor can have different non-public `requires`
     *    clauses of platform ( `java.*` and `jdk.*` ) modules, and
     *  - A versioned descriptor can have different `uses` clauses, even of
     *    service types defined outside of the platform modules.
     */
    private boolean isValidVersionedDescriptor(ModuleDescriptor vd,
                                               ModuleDescriptor rd)
        throws IOException
    {
        if (!rd.name().equals(vd.name())) {
            fatalError(getMsg("error.versioned.info.name.notequal"));
            return false;
        }
        if (!rd.requires().equals(vd.requires())) {
            Set<Requires> rootRequires = rd.requires();
            for (Requires r : vd.requires()) {
                if (rootRequires.contains(r)) {
                    continue;
                } else if (r.modifiers().contains(Requires.Modifier.PUBLIC)) {
                    fatalError(getMsg("error.versioned.info.requires.public"));
                    return false;
                } else if (!isPlatformModule(r.name())) {
                    fatalError(getMsg("error.versioned.info.requires.added"));
                    return false;
                }
            }
            for (Requires r : rootRequires) {
                Set<Requires> mdRequires = vd.requires();
                if (mdRequires.contains(r)) {
                    continue;
                } else if (!isPlatformModule(r.name())) {
                    fatalError(getMsg("error.versioned.info.requires.dropped"));
                    return false;
                }
            }
        }
        if (!rd.exports().equals(vd.exports())) {
            fatalError(getMsg("error.versioned.info.exports.notequal"));
            return false;
        }
        if (!rd.provides().equals(vd.provides())) {
            fatalError(getMsg("error.versioned.info.provides.notequal"));
            return false;
        }
        return true;
    }

    /**
     * Returns a byte array containing the given module-info.class plus any
     * extended attributes.
     *
     * If --module-version, --main-class, or other options were provided
     * then the corresponding class file attributes are added to the
     * module-info here.
     */
    private byte[] extendedInfoBytes(ModuleDescriptor rootDescriptor,
                                     ModuleDescriptor md,
                                     byte[] miBytes,
                                     Set<String> conceals)
        throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream is = new ByteArrayInputStream(miBytes);
        ModuleInfoExtender extender = ModuleInfoExtender.newExtender(is);

        // Add (or replace) the ConcealedPackages attribute
        extender.conceals(conceals);

        // --main-class
        if (ename != null)
            extender.mainClass(ename);
        else if (rootDescriptor.mainClass().isPresent())
            extender.mainClass(rootDescriptor.mainClass().get());

        // --module-version
        if (moduleVersion != null)
            extender.version(moduleVersion);
        else if (rootDescriptor.version().isPresent())
            extender.version(rootDescriptor.version().get());

        // --hash-modules
        if (modulesToHash != null) {
            String mn = md.name();
            Hasher hasher = new Hasher(md, fname);
            ModuleHashes moduleHashes = hasher.computeHashes(mn);
            if (moduleHashes != null) {
                extender.hashes(moduleHashes);
            } else {
                // should it issue warning or silent?
                System.out.println("warning: no module is recorded in hash in " + mn);
            }
        }

        extender.write(baos);
        return baos.toByteArray();
    }

    /**
     * Compute and record hashes
     */
    private class Hasher {
        final ModuleFinder finder;
        final Map<String, Path> moduleNameToPath;
        final Set<String> modules;
        final Configuration configuration;
        Hasher(ModuleDescriptor descriptor, String fname) throws IOException {
            // Create a module finder that finds the modular JAR
            // being created/updated
            URI uri = Paths.get(fname).toUri();
            ModuleReference mref = new ModuleReference(descriptor, uri,
                new Supplier<>() {
                    @Override
                    public ModuleReader get() {
                        throw new UnsupportedOperationException("should not reach here");
                    }
                });

            // Compose a module finder with the module path and
            // the modular JAR being created or updated
            this.finder = ModuleFinder.compose(moduleFinder,
                new ModuleFinder() {
                    @Override
                    public Optional<ModuleReference> find(String name) {
                        if (descriptor.name().equals(name))
                            return Optional.of(mref);
                        else
                            return Optional.empty();
                    }

                    @Override
                    public Set<ModuleReference> findAll() {
                        return Collections.singleton(mref);
                    }
                });

            // Determine the modules that matches the modulesToHash pattern
            this.modules = moduleFinder.findAll().stream()
                .map(moduleReference -> moduleReference.descriptor().name())
                .filter(mn -> modulesToHash.matcher(mn).find())
                .collect(Collectors.toSet());

            // a map from a module name to Path of the modular JAR
            this.moduleNameToPath = moduleFinder.findAll().stream()
                .map(ModuleReference::descriptor)
                .map(ModuleDescriptor::name)
                .collect(Collectors.toMap(Function.identity(), mn -> moduleToPath(mn)));

            Configuration config = null;
            try {
                config = Configuration.empty()
                    .resolveRequires(ModuleFinder.ofSystem(), finder, modules);
            } catch (ResolutionException e) {
                // should it throw an error?  or emit a warning
                System.out.println("warning: " + e.getMessage());
            }
            this.configuration = config;
        }

        /**
         * Compute hashes of the modules that depend upon the specified
         * module directly or indirectly.
         */
        ModuleHashes computeHashes(String name) {
            // the transposed graph includes all modules in the resolved graph
            Map<String, Set<String>> graph = transpose();

            // find the modules that transitively depend upon the specified name
            Deque<String> deque = new ArrayDeque<>();
            deque.add(name);
            Set<String> mods = visitNodes(graph, deque);

            // filter modules matching the pattern specified in --hash-modules,
            // as well as the modular jar file that is being created / updated
            Map<String, Path> modulesForHash = mods.stream()
                .filter(mn -> !mn.equals(name) && modules.contains(mn))
                .collect(Collectors.toMap(Function.identity(), moduleNameToPath::get));

            if (modulesForHash.isEmpty())
                return null;

            return ModuleHashes.generate(modulesForHash, "SHA-256");
        }

        /**
         * Returns all nodes traversed from the given roots.
         */
        private Set<String> visitNodes(Map<String, Set<String>> graph,
                                       Deque<String> roots) {
            Set<String> visited = new HashSet<>();
            while (!roots.isEmpty()) {
                String mn = roots.pop();
                if (!visited.contains(mn)) {
                    visited.add(mn);

                    // the given roots may not be part of the graph
                    if (graph.containsKey(mn)) {
                        for (String dm : graph.get(mn)) {
                            if (!visited.contains(dm))
                                roots.push(dm);
                        }
                    }
                }
            }
            return visited;
        }

        /**
         * Returns a transposed graph from the resolved module graph.
         */
        private Map<String, Set<String>> transpose() {
            Map<String, Set<String>> transposedGraph = new HashMap<>();
            Deque<String> deque = new ArrayDeque<>(modules);

            Set<String> visited = new HashSet<>();
            while (!deque.isEmpty()) {
                String mn = deque.pop();
                if (!visited.contains(mn)) {
                    visited.add(mn);

                    // add an empty set
                    transposedGraph.computeIfAbsent(mn, _k -> new HashSet<>());

                    ResolvedModule resolvedModule = configuration.findModule(mn).get();
                    for (ResolvedModule dm : resolvedModule.reads()) {
                        String name = dm.name();
                        if (!visited.contains(name)) {
                            deque.push(name);
                        }
                        // reverse edge
                        transposedGraph.computeIfAbsent(name, _k -> new HashSet<>())
                                       .add(mn);
                    }
                }
            }
            return transposedGraph;
        }

        private Path moduleToPath(String name) {
            ModuleReference mref = moduleFinder.find(name).orElseThrow(
                () -> new InternalError(formatMsg2("error.hash.dep",name , name)));

            URI uri = mref.location().get();
            Path path = Paths.get(uri);
            String fn = path.getFileName().toString();
            if (!fn.endsWith(".jar")) {
                throw new UnsupportedOperationException(path + " is not a modular JAR");
            }
            return path;
        }
    }
}
