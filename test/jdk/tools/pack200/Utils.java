/*
 * Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.nio.file.Path;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.nio.file.StandardCopyOption.*;
import static java.nio.file.StandardOpenOption.*;


/**
 *
 * @author ksrini
 */

/*
 * This class contains the commonly used utilities.
 */
class Utils {
    static final String JavaHome = System.getProperty("test.java",
            System.getProperty("java.home"));
    static final boolean IsWindows =
            System.getProperty("os.name").startsWith("Windows");
    static final boolean Is64Bit =
            System.getProperty("sun.arch.data.model", "32").equals("64");
    static final File   JavaSDK =  new File(JavaHome);

    static final String PACK_FILE_EXT   = ".pack";
    static final String JAVA_FILE_EXT   = ".java";
    static final String CLASS_FILE_EXT  = ".class";
    static final String JAR_FILE_EXT    = ".jar";

    static final File   TEST_SRC_DIR = new File(System.getProperty("test.src"));
    static final File   TEST_CLS_DIR = new File(System.getProperty("test.classes"));
    static final String VERIFIER_DIR_NAME = "pack200-verifier";
    static final File   VerifierJar = new File(VERIFIER_DIR_NAME + JAR_FILE_EXT);
    static final File   XCLASSES = new File("xclasses");

    private Utils() {} // all static

    private static void init() throws IOException {
        if (VerifierJar.exists()) {
            return;
        }
        File srcDir = new File(getVerifierDir(), "src");
        List<File> javaFileList = findFiles(srcDir, createFilter(JAVA_FILE_EXT));
        File tmpFile = File.createTempFile("javac", ".tmp", new File("."));
        XCLASSES.mkdirs();
        FileOutputStream fos = null;
        PrintStream ps = null;
        try {
            fos = new FileOutputStream(tmpFile);
            ps = new PrintStream(fos);
            for (File f : javaFileList) {
                ps.println(f.getAbsolutePath());
            }
        } finally {
            close(ps);
            close(fos);
        }

        compiler("-d",
                XCLASSES.getName(),
                "--add-modules=jdk.jdeps",
                "--add-exports=jdk.jdeps/com.sun.tools.classfile=ALL-UNNAMED",
                "@" + tmpFile.getAbsolutePath());

        jar("cvfe",
            VerifierJar.getName(),
            "sun.tools.pack.verify.Main",
            "-C",
            XCLASSES.getName(),
            ".");
    }

    private static File getVerifierDir() {
        File srcDir = new File(TEST_SRC_DIR, VERIFIER_DIR_NAME);
        if (!srcDir.exists()) {
            // if not available try one level above
            srcDir = new File(TEST_SRC_DIR.getParentFile(), VERIFIER_DIR_NAME);
        }
        return srcDir;
    }

    static File getGoldenJar() {
        return new File(new File(getVerifierDir(), "data"), "golden.jar");
    }
    static void dirlist(File dir) {
        File[] files = dir.listFiles();
        System.out.println("--listing " + dir.getAbsolutePath() + "---");
        for (File f : files) {
            StringBuffer sb = new StringBuffer();
            sb.append(f.isDirectory() ? "d " : "- ");
            sb.append(f.getName());
            System.out.println(sb);
        }
    }
    static void doCompareVerify(File reference, File specimen) throws IOException {
        init();
        List<String> cmds = new ArrayList<String>();
        cmds.add(getJavaCmd());
        cmds.add("--add-exports=jdk.jdeps/com.sun.tools.classfile=ALL-UNNAMED");
        cmds.add("-cp");
        cmds.add(VerifierJar.getName());
        cmds.add("sun.tools.pack.verify.Main");
        cmds.add(reference.getAbsolutePath());
        cmds.add(specimen.getAbsolutePath());
        cmds.add("-O");
        runExec(cmds);
    }

    static void doCompareBitWise(File reference, File specimen)
            throws IOException {
        init();
        List<String> cmds = new ArrayList<String>();
        cmds.add(getJavaCmd());
        cmds.add("-cp");
        cmds.add(VerifierJar.getName());
        cmds.add("sun.tools.pack.verify.Main");
        cmds.add(reference.getName());
        cmds.add(specimen.getName());
        cmds.add("-O");
        cmds.add("-b");
        runExec(cmds);
    }

    static FileFilter createFilter(final String extension) {
        return new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String name = pathname.getName();
                if (name.endsWith(extension)) {
                    return true;
                }
                return false;
            }
        };
    }

    /*
     * clean up all the usual suspects
     */
    static void cleanup() throws IOException {
        recursiveDelete(XCLASSES);
        List<File> toDelete = new ArrayList<>();
        toDelete.addAll(Utils.findFiles(new File("."),
                Utils.createFilter(".out")));
        toDelete.addAll(Utils.findFiles(new File("."),
                Utils.createFilter(".bak")));
        toDelete.addAll(Utils.findFiles(new File("."),
                Utils.createFilter(".jar")));
        toDelete.addAll(Utils.findFiles(new File("."),
                Utils.createFilter(".pack")));
        toDelete.addAll(Utils.findFiles(new File("."),
                Utils.createFilter(".bnd")));
        toDelete.addAll(Utils.findFiles(new File("."),
                Utils.createFilter(".txt")));
        toDelete.addAll(Utils.findFiles(new File("."),
                Utils.createFilter(".idx")));
        toDelete.addAll(Utils.findFiles(new File("."),
                Utils.createFilter(".gidx")));
        toDelete.addAll(Utils.findFiles(new File("."),
                Utils.createFilter(".tmp")));
        toDelete.addAll(Utils.findFiles(new File("."),
                Utils.createFilter(".class")));
        for (File f : toDelete) {
            f.delete();
        }
    }

    static final FileFilter DIR_FILTER = new FileFilter() {
        public boolean accept(File pathname) {
            if (pathname.isDirectory()) {
                return true;
            }
            return false;
        }
    };

    static final FileFilter FILE_FILTER = new FileFilter() {
        public boolean accept(File pathname) {
            if (pathname.isFile()) {
                return true;
            }
            return false;
        }
    };

    static void copyFile(File src, File dst) throws IOException {
        Path parent = dst.toPath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(src.toPath(), dst.toPath(), COPY_ATTRIBUTES, REPLACE_EXISTING);
        if (dst.isDirectory() && !dst.canWrite()) {
            dst.setWritable(true);
        }
    }

    static String baseName(File file, String extension) {
        return baseName(file.getAbsolutePath(), extension);
    }

    static String baseName(String name, String extension) {
        int cut = name.length() - extension.length();
        return name.lastIndexOf(extension) == cut
                ? name.substring(0, cut)
                : name;

    }
   static void createFile(File outFile, List<String> content) throws IOException {
        Files.write(outFile.getAbsoluteFile().toPath(), content,
                Charset.defaultCharset(), CREATE_NEW, TRUNCATE_EXISTING);
    }

    /*
     * Suppose a path is provided which consists of a full path
     * this method returns the sub path for a full path ex: /foo/bar/baz/foobar.z
     * and the base path is /foo/bar it will will return baz/foobar.z.
     */
    private static String getEntryPath(String basePath, String fullPath) {
        if (!fullPath.startsWith(basePath)) {
            return null;
        }
        return fullPath.substring(basePath.length());
    }

    static String getEntryPath(File basePathFile, File fullPathFile) {
        return getEntryPath(basePathFile.toString(), fullPathFile.toString());
    }

    public static void recursiveCopy(File src, File dest) throws IOException {
        if (!src.exists() || !src.canRead()) {
            throw new IOException("file not found or readable: " + src);
        }
        if (dest.exists() && !dest.isDirectory() && !dest.canWrite()) {
            throw new IOException("file not found or writeable: " + dest);
        }
        if (!dest.exists()) {
            dest.mkdirs();
        }
        List<File> a = directoryList(src);
        for (File f : a) {
            copyFile(f, new File(dest, getEntryPath(src, f)));
        }
    }

    static List<File> directoryList(File dirname) {
        List<File>  dirList = new ArrayList<File>();
        return directoryList(dirname, dirList, null);
    }

    private static List<File> directoryList(File dirname, List<File> dirList,
            File[] dirs) {
        dirList.addAll(Arrays.asList(dirname.listFiles(FILE_FILTER)));
        dirs = dirname.listFiles(DIR_FILTER);
        for (File f : dirs) {
            if (f.isDirectory() && !f.equals(dirname)) {
                dirList.add(f);
                directoryList(f, dirList, dirs);
            }
        }
        return dirList;
    }

    static void recursiveDelete(File dir) throws IOException {
        if (dir.isFile()) {
            dir.delete();
        } else if (dir.isDirectory()) {
            File[] entries = dir.listFiles();
            for (int i = 0; i < entries.length; i++) {
                if (entries[i].isDirectory()) {
                    recursiveDelete(entries[i]);
                }
                entries[i].delete();
            }
            dir.delete();
        }
    }

    static List<File> findFiles(File startDir, FileFilter filter)
            throws IOException {
        List<File> list = new ArrayList<File>();
        findFiles0(startDir, list, filter);
        return list;
    }
    /*
     * finds files in the start directory using the the filter, appends
     * the files to the dirList.
     */
    private static void findFiles0(File startDir, List<File> list,
                                    FileFilter filter) throws IOException {
        File[] foundFiles = startDir.listFiles(filter);
        if (foundFiles == null) {
            return;
        }
        list.addAll(Arrays.asList(foundFiles));
        File[] dirs = startDir.listFiles(DIR_FILTER);
        for (File dir : dirs) {
            findFiles0(dir, list, filter);
        }
    }

    static void close(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException ignore) {
        }
    }

    static void compiler(String... javacCmds) {
        List<String> cmdList = new ArrayList<>();
        cmdList.add(getJavacCmd());
        for (String x : javacCmds) {
            cmdList.add(x);
        }
        runExec(cmdList);
    }

    static void jar(String... jargs) {
        List<String> cmdList = new ArrayList<>();
        cmdList.add(getJarCmd());
        for (String x : jargs) {
            cmdList.add(x);
        }
        runExec(cmdList);
    }

    static void testWithRepack(File inFile, String... repackOpts) throws IOException {
        File cwd = new File(".");
        // pack using --repack in native mode
        File nativejarFile = new File(cwd, "out-n" + Utils.JAR_FILE_EXT);
        repack(inFile, nativejarFile, false, repackOpts);
        doCompareVerify(inFile, nativejarFile);

        // ensure bit compatibility between the unpacker variants
        File javajarFile = new File(cwd, "out-j" + Utils.JAR_FILE_EXT);
        repack(inFile, javajarFile, true, repackOpts);
        doCompareBitWise(javajarFile, nativejarFile);
    }

    static List<String> repack(File inFile, File outFile,
            boolean disableNative, String... extraOpts) {
        List<String> cmdList = new ArrayList<>();
        cmdList.clear();
        cmdList.add(Utils.getJavaCmd());
        cmdList.add("-ea");
        cmdList.add("-esa");
        if (disableNative) {
            cmdList.add("-Dcom.sun.java.util.jar.pack.disable.native=true");
        }
        cmdList.add("com.sun.java.util.jar.pack.Driver");
        cmdList.add("--repack");
        if (extraOpts != null) {
           for (String opt: extraOpts) {
               cmdList.add(opt);
           }
        }
        cmdList.add(outFile.getName());
        cmdList.add(inFile.getName());
        return Utils.runExec(cmdList);
    }

    // given a jar file foo.jar will write to foo.pack
    static void pack(JarFile jarFile, File packFile) throws IOException {
        Pack200.Packer packer = Pack200.newPacker();
        Map<String, String> p = packer.properties();
        // Take the time optimization vs. space
        p.put(packer.EFFORT, "1");  // CAUTION: do not use 0.
        // Make the memory consumption as effective as possible
        p.put(packer.SEGMENT_LIMIT, "10000");
        // ignore all JAR deflation requests to save time
        p.put(packer.DEFLATE_HINT, packer.FALSE);
        // save the file ordering of the original JAR
        p.put(packer.KEEP_FILE_ORDER, packer.TRUE);
        FileOutputStream fos = null;
        try {
            // Write out to a jtreg scratch area
            fos = new FileOutputStream(packFile);
            // Call the packer
            packer.pack(jarFile, fos);
        } finally {
            close(fos);
        }
    }

    // uses java unpacker, slow but useful to discover issues with the packer
    static void unpackj(File inFile, JarOutputStream jarStream)
            throws IOException {
        unpack0(inFile, jarStream, true);

    }

    // uses native unpacker using the java APIs
    static void unpackn(File inFile, JarOutputStream jarStream)
            throws IOException {
        unpack0(inFile, jarStream, false);
    }

    // given a packed file, create the jar file in the current directory.
    private static void unpack0(File inFile, JarOutputStream jarStream,
            boolean useJavaUnpack) throws IOException {
        // Unpack the files
        Pack200.Unpacker unpacker = Pack200.newUnpacker();
        Map<String, String> props = unpacker.properties();
        if (useJavaUnpack) {
            props.put("com.sun.java.util.jar.pack.disable.native", "true");
        }
        // Call the unpacker
        unpacker.unpack(inFile, jarStream);
    }

    static byte[] getBuffer(ZipFile zf, ZipEntry ze) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte buf[] = new byte[8192];
        InputStream is = null;
        try {
            is = zf.getInputStream(ze);
            int n = is.read(buf);
            while (n > 0) {
                baos.write(buf, 0, n);
                n = is.read(buf);
            }
            return baos.toByteArray();
        } finally {
            close(is);
        }
    }

    static ArrayList<String> getZipFileEntryNames(ZipFile z) {
        ArrayList<String> out = new ArrayList<String>();
        for (ZipEntry ze : Collections.list(z.entries())) {
            out.add(ze.getName());
        }
        return out;
    }

    static List<String> runExec(String... cmds) {
        return runExec(Arrays.asList(cmds));
    }

    static List<String> runExec(List<String> cmdsList) {
        return runExec(cmdsList, null);
    }

    static List<String> runExec(List<String> cmdsList, Map<String, String> penv) {
        return runExec(cmdsList, penv, false);
    }

    static List<String> runExec(List<String> cmdsList, Map<String, String> penv, boolean ignoreReturnValue) {
        ArrayList<String> alist = new ArrayList<String>();
        ProcessBuilder pb =
                new ProcessBuilder(cmdsList);
        Map<String, String> env = pb.environment();
        if (penv != null && !penv.isEmpty()) {
            env.putAll(penv);
        }
        pb.directory(new File("."));
        dirlist(new File("."));
        for (String x : cmdsList) {
            System.out.print(x + " ");
        }
        System.out.println("");
        int retval = 0;
        Process p = null;
        InputStreamReader ir = null;
        BufferedReader rd = null;
        InputStream is = null;
        try {
            pb.redirectErrorStream(true);
            p = pb.start();
            is = p.getInputStream();
            ir = new InputStreamReader(is);
            rd = new BufferedReader(ir, 8192);

            String in = rd.readLine();
            while (in != null) {
                alist.add(in);
                System.out.println(in);
                in = rd.readLine();
            }
            retval = p.waitFor();
            if (!ignoreReturnValue && retval != 0) {
                throw new RuntimeException("process failed with non-zero exit");
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        } finally {
            close(rd);
            close(ir);
            close(is);
            if (p != null) {
                p.destroy();
            }
        }
        return alist;
    }

    static String getUnpack200Cmd() {
        return getAjavaCmd("unpack200");
    }

    static String getPack200Cmd() {
        return getAjavaCmd("pack200");
    }

    static String getJavaCmd() {
        return getAjavaCmd("java");
    }

    static String getJavacCmd() {
        return getAjavaCmd("javac");
    }

    static String getJarCmd() {
        return getAjavaCmd("jar");
    }

    static String getAjavaCmd(String cmdStr) {
        File binDir = new File(JavaHome, "bin");
        File unpack200File = IsWindows
                ? new File(binDir, cmdStr + ".exe")
                : new File(binDir, cmdStr);

        String cmd = unpack200File.getAbsolutePath();
        if (!unpack200File.canExecute()) {
            throw new RuntimeException("please check" +
                    cmd + " exists and is executable");
        }
        return cmd;
    }

    // used to get all classes
    static File createRtJar() throws Exception {
        File rtJar = new File("rt.jar");
        new JrtToZip(".*\\.class", rtJar).run();
        return rtJar;
    }

    // used to select the contents
    static File createRtJar(String pattern) throws Exception {
        File rtJar = new File("rt.jar");
        new JrtToZip(pattern, rtJar).run();
        return rtJar;
    }

    /*
     * A helper class to create a pseudo rt.jar.
     */
    static class JrtToZip {

        final File outFile;
        final Pattern pattern;

        public static void main(String[] args) throws Exception {
            new JrtToZip(args[0], new File(args[1])).run();
        }

        JrtToZip(String pattern, File outFile) throws Exception {
            this.pattern = Pattern.compile(pattern);
            this.outFile = outFile;
        }

        void run() throws Exception {
            URI uri = URI.create("jar:" + outFile.toURI());
            Map<String, String> env = new HashMap<>();
            env.put("create", "true");
            try (FileSystem zipfs = FileSystems.newFileSystem(uri, env)) {
                toZipfs(zipfs);
            }
        }

        void toZipfs(FileSystem zipfs) throws Exception {
            FileSystem jrtfs = FileSystems.getFileSystem(URI.create("jrt:/"));
            for (Path root : jrtfs.getRootDirectories()) {
                Files.walkFileTree(root, new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir,
                            BasicFileAttributes attrs) throws IOException {
                        // ignore unneeded directory
                        if (dir.startsWith("/packages"))
                            return FileVisitResult.SKIP_SUBTREE;

                        // pre-create required directories
                        Path zpath = zipfs.getPath(dir.toString());
                        Files.createDirectories(zpath);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file,
                            BasicFileAttributes attrs) throws IOException {
                        Matcher matcher = pattern.matcher(file.toString());
                        if (matcher.matches()) {
                            // System.out.println("x: " + file);
                            Path zpath = zipfs.getPath(file.toString());
                            Files.copy(file, zpath, REPLACE_EXISTING);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file,
                            IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir,
                            IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }
}
