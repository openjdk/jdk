/*
 * Copyright (c) 2000, 2006, Oracle and/or its affiliates. All rights reserved.
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

package java.util.logging;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.*;

/**
 * Simple file logging <tt>Handler</tt>.
 * <p>
 * The <tt>FileHandler</tt> can either write to a specified file,
 * or it can write to a rotating set of files.
 * <p>
 * For a rotating set of files, as each file reaches a given size
 * limit, it is closed, rotated out, and a new file opened.
 * Successively older files are named by adding "0", "1", "2",
 * etc. into the base filename.
 * <p>
 * By default buffering is enabled in the IO libraries but each log
 * record is flushed out when it is complete.
 * <p>
 * By default the <tt>XMLFormatter</tt> class is used for formatting.
 * <p>
 * <b>Configuration:</b>
 * By default each <tt>FileHandler</tt> is initialized using the following
 * <tt>LogManager</tt> configuration properties.  If properties are not defined
 * (or have invalid values) then the specified default values are used.
 * <ul>
 * <li>   java.util.logging.FileHandler.level
 *        specifies the default level for the <tt>Handler</tt>
 *        (defaults to <tt>Level.ALL</tt>).
 * <li>   java.util.logging.FileHandler.filter
 *        specifies the name of a <tt>Filter</tt> class to use
 *        (defaults to no <tt>Filter</tt>).
 * <li>   java.util.logging.FileHandler.formatter
 *        specifies the name of a <tt>Formatter</tt> class to use
 *        (defaults to <tt>java.util.logging.XMLFormatter</tt>)
 * <li>   java.util.logging.FileHandler.encoding
 *        the name of the character set encoding to use (defaults to
 *        the default platform encoding).
 * <li>   java.util.logging.FileHandler.limit
 *        specifies an approximate maximum amount to write (in bytes)
 *        to any one file.  If this is zero, then there is no limit.
 *        (Defaults to no limit).
 * <li>   java.util.logging.FileHandler.count
 *        specifies how many output files to cycle through (defaults to 1).
 * <li>   java.util.logging.FileHandler.pattern
 *        specifies a pattern for generating the output file name.  See
 *        below for details. (Defaults to "%h/java%u.log").
 * <li>   java.util.logging.FileHandler.append
 *        specifies whether the FileHandler should append onto
 *        any existing files (defaults to false).
 * </ul>
 * <p>
 * <p>
 * A pattern consists of a string that includes the following special
 * components that will be replaced at runtime:
 * <ul>
 * <li>    "/"    the local pathname separator
 * <li>     "%t"   the system temporary directory
 * <li>     "%h"   the value of the "user.home" system property
 * <li>     "%g"   the generation number to distinguish rotated logs
 * <li>     "%u"   a unique number to resolve conflicts
 * <li>     "%%"   translates to a single percent sign "%"
 * </ul>
 * If no "%g" field has been specified and the file count is greater
 * than one, then the generation number will be added to the end of
 * the generated filename, after a dot.
 * <p>
 * Thus for example a pattern of "%t/java%g.log" with a count of 2
 * would typically cause log files to be written on Solaris to
 * /var/tmp/java0.log and /var/tmp/java1.log whereas on Windows 95 they
 * would be typically written to C:\TEMP\java0.log and C:\TEMP\java1.log
 * <p>
 * Generation numbers follow the sequence 0, 1, 2, etc.
 * <p>
 * Normally the "%u" unique field is set to 0.  However, if the <tt>FileHandler</tt>
 * tries to open the filename and finds the file is currently in use by
 * another process it will increment the unique number field and try
 * again.  This will be repeated until <tt>FileHandler</tt> finds a file name that
 * is  not currently in use. If there is a conflict and no "%u" field has
 * been specified, it will be added at the end of the filename after a dot.
 * (This will be after any automatically added generation number.)
 * <p>
 * Thus if three processes were all trying to log to fred%u.%g.txt then
 * they  might end up using fred0.0.txt, fred1.0.txt, fred2.0.txt as
 * the first file in their rotating sequences.
 * <p>
 * Note that the use of unique ids to avoid conflicts is only guaranteed
 * to work reliably when using a local disk file system.
 *
 * @since 1.4
 */

public class FileHandler extends StreamHandler {
    private MeteredStream meter;
    private boolean append;
    private int limit;       // zero => no limit.
    private int count;
    private String pattern;
    private String lockFileName;
    private FileOutputStream lockStream;
    private File files[];
    private static final int MAX_LOCKS = 100;
    private static java.util.HashMap<String, String> locks = new java.util.HashMap<>();

    // A metered stream is a subclass of OutputStream that
    //   (a) forwards all its output to a target stream
    //   (b) keeps track of how many bytes have been written
    private class MeteredStream extends OutputStream {
        OutputStream out;
        int written;

        MeteredStream(OutputStream out, int written) {
            this.out = out;
            this.written = written;
        }

        public void write(int b) throws IOException {
            out.write(b);
            written++;
        }

        public void write(byte buff[]) throws IOException {
            out.write(buff);
            written += buff.length;
        }

        public void write(byte buff[], int off, int len) throws IOException {
            out.write(buff,off,len);
            written += len;
        }

        public void flush() throws IOException {
            out.flush();
        }

        public void close() throws IOException {
            out.close();
        }
    }

    private void open(File fname, boolean append) throws IOException {
        int len = 0;
        if (append) {
            len = (int)fname.length();
        }
        FileOutputStream fout = new FileOutputStream(fname.toString(), append);
        BufferedOutputStream bout = new BufferedOutputStream(fout);
        meter = new MeteredStream(bout, len);
        setOutputStream(meter);
    }

    // Private method to configure a FileHandler from LogManager
    // properties and/or default values as specified in the class
    // javadoc.
    private void configure() {
        LogManager manager = LogManager.getLogManager();

        String cname = getClass().getName();

        pattern = manager.getStringProperty(cname + ".pattern", "%h/java%u.log");
        limit = manager.getIntProperty(cname + ".limit", 0);
        if (limit < 0) {
            limit = 0;
        }
        count = manager.getIntProperty(cname + ".count", 1);
        if (count <= 0) {
            count = 1;
        }
        append = manager.getBooleanProperty(cname + ".append", false);
        setLevel(manager.getLevelProperty(cname + ".level", Level.ALL));
        setFilter(manager.getFilterProperty(cname + ".filter", null));
        setFormatter(manager.getFormatterProperty(cname + ".formatter", new XMLFormatter()));
        try {
            setEncoding(manager.getStringProperty(cname +".encoding", null));
        } catch (Exception ex) {
            try {
                setEncoding(null);
            } catch (Exception ex2) {
                // doing a setEncoding with null should always work.
                // assert false;
            }
        }
    }


    /**
     * Construct a default <tt>FileHandler</tt>.  This will be configured
     * entirely from <tt>LogManager</tt> properties (or their default values).
     * <p>
     * @exception  IOException if there are IO problems opening the files.
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control"))</tt>.
     * @exception  NullPointerException if pattern property is an empty String.
     */
    public FileHandler() throws IOException, SecurityException {
        checkAccess();
        configure();
        openFiles();
    }

    /**
     * Initialize a <tt>FileHandler</tt> to write to the given filename.
     * <p>
     * The <tt>FileHandler</tt> is configured based on <tt>LogManager</tt>
     * properties (or their default values) except that the given pattern
     * argument is used as the filename pattern, the file limit is
     * set to no limit, and the file count is set to one.
     * <p>
     * There is no limit on the amount of data that may be written,
     * so use this with care.
     *
     * @param pattern  the name of the output file
     * @exception  IOException if there are IO problems opening the files.
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     * @exception  IllegalArgumentException if pattern is an empty string
     */
    public FileHandler(String pattern) throws IOException, SecurityException {
        if (pattern.length() < 1 ) {
            throw new IllegalArgumentException();
        }
        checkAccess();
        configure();
        this.pattern = pattern;
        this.limit = 0;
        this.count = 1;
        openFiles();
    }

    /**
     * Initialize a <tt>FileHandler</tt> to write to the given filename,
     * with optional append.
     * <p>
     * The <tt>FileHandler</tt> is configured based on <tt>LogManager</tt>
     * properties (or their default values) except that the given pattern
     * argument is used as the filename pattern, the file limit is
     * set to no limit, the file count is set to one, and the append
     * mode is set to the given <tt>append</tt> argument.
     * <p>
     * There is no limit on the amount of data that may be written,
     * so use this with care.
     *
     * @param pattern  the name of the output file
     * @param append  specifies append mode
     * @exception  IOException if there are IO problems opening the files.
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     * @exception  IllegalArgumentException if pattern is an empty string
     */
    public FileHandler(String pattern, boolean append) throws IOException, SecurityException {
        if (pattern.length() < 1 ) {
            throw new IllegalArgumentException();
        }
        checkAccess();
        configure();
        this.pattern = pattern;
        this.limit = 0;
        this.count = 1;
        this.append = append;
        openFiles();
    }

    /**
     * Initialize a <tt>FileHandler</tt> to write to a set of files.  When
     * (approximately) the given limit has been written to one file,
     * another file will be opened.  The output will cycle through a set
     * of count files.
     * <p>
     * The <tt>FileHandler</tt> is configured based on <tt>LogManager</tt>
     * properties (or their default values) except that the given pattern
     * argument is used as the filename pattern, the file limit is
     * set to the limit argument, and the file count is set to the
     * given count argument.
     * <p>
     * The count must be at least 1.
     *
     * @param pattern  the pattern for naming the output file
     * @param limit  the maximum number of bytes to write to any one file
     * @param count  the number of files to use
     * @exception  IOException if there are IO problems opening the files.
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     * @exception IllegalArgumentException if limit < 0, or count < 1.
     * @exception  IllegalArgumentException if pattern is an empty string
     */
    public FileHandler(String pattern, int limit, int count)
                                        throws IOException, SecurityException {
        if (limit < 0 || count < 1 || pattern.length() < 1) {
            throw new IllegalArgumentException();
        }
        checkAccess();
        configure();
        this.pattern = pattern;
        this.limit = limit;
        this.count = count;
        openFiles();
    }

    /**
     * Initialize a <tt>FileHandler</tt> to write to a set of files
     * with optional append.  When (approximately) the given limit has
     * been written to one file, another file will be opened.  The
     * output will cycle through a set of count files.
     * <p>
     * The <tt>FileHandler</tt> is configured based on <tt>LogManager</tt>
     * properties (or their default values) except that the given pattern
     * argument is used as the filename pattern, the file limit is
     * set to the limit argument, and the file count is set to the
     * given count argument, and the append mode is set to the given
     * <tt>append</tt> argument.
     * <p>
     * The count must be at least 1.
     *
     * @param pattern  the pattern for naming the output file
     * @param limit  the maximum number of bytes to write to any one file
     * @param count  the number of files to use
     * @param append  specifies append mode
     * @exception  IOException if there are IO problems opening the files.
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     * @exception IllegalArgumentException if limit < 0, or count < 1.
     * @exception  IllegalArgumentException if pattern is an empty string
     *
     */
    public FileHandler(String pattern, int limit, int count, boolean append)
                                        throws IOException, SecurityException {
        if (limit < 0 || count < 1 || pattern.length() < 1) {
            throw new IllegalArgumentException();
        }
        checkAccess();
        configure();
        this.pattern = pattern;
        this.limit = limit;
        this.count = count;
        this.append = append;
        openFiles();
    }

    // Private method to open the set of output files, based on the
    // configured instance variables.
    private void openFiles() throws IOException {
        LogManager manager = LogManager.getLogManager();
        manager.checkAccess();
        if (count < 1) {
           throw new IllegalArgumentException("file count = " + count);
        }
        if (limit < 0) {
            limit = 0;
        }

        // We register our own ErrorManager during initialization
        // so we can record exceptions.
        InitializationErrorManager em = new InitializationErrorManager();
        setErrorManager(em);

        // Create a lock file.  This grants us exclusive access
        // to our set of output files, as long as we are alive.
        int unique = -1;
        for (;;) {
            unique++;
            if (unique > MAX_LOCKS) {
                throw new IOException("Couldn't get lock for " + pattern);
            }
            // Generate a lock file name from the "unique" int.
            lockFileName = generate(pattern, 0, unique).toString() + ".lck";
            // Now try to lock that filename.
            // Because some systems (e.g., Solaris) can only do file locks
            // between processes (and not within a process), we first check
            // if we ourself already have the file locked.
            synchronized(locks) {
                if (locks.get(lockFileName) != null) {
                    // We already own this lock, for a different FileHandler
                    // object.  Try again.
                    continue;
                }
                FileChannel fc;
                try {
                    lockStream = new FileOutputStream(lockFileName);
                    fc = lockStream.getChannel();
                } catch (IOException ix) {
                    // We got an IOException while trying to open the file.
                    // Try the next file.
                    continue;
                }
                try {
                    FileLock fl = fc.tryLock();
                    if (fl == null) {
                        // We failed to get the lock.  Try next file.
                        continue;
                    }
                    // We got the lock OK.
                } catch (IOException ix) {
                    // We got an IOException while trying to get the lock.
                    // This normally indicates that locking is not supported
                    // on the target directory.  We have to proceed without
                    // getting a lock.   Drop through.
                }
                // We got the lock.  Remember it.
                locks.put(lockFileName, lockFileName);
                break;
            }
        }

        files = new File[count];
        for (int i = 0; i < count; i++) {
            files[i] = generate(pattern, i, unique);
        }

        // Create the initial log file.
        if (append) {
            open(files[0], true);
        } else {
            rotate();
        }

        // Did we detect any exceptions during initialization?
        Exception ex = em.lastException;
        if (ex != null) {
            if (ex instanceof IOException) {
                throw (IOException) ex;
            } else if (ex instanceof SecurityException) {
                throw (SecurityException) ex;
            } else {
                throw new IOException("Exception: " + ex);
            }
        }

        // Install the normal default ErrorManager.
        setErrorManager(new ErrorManager());
    }

    // Generate a filename from a pattern.
    private File generate(String pattern, int generation, int unique) throws IOException {
        File file = null;
        String word = "";
        int ix = 0;
        boolean sawg = false;
        boolean sawu = false;
        while (ix < pattern.length()) {
            char ch = pattern.charAt(ix);
            ix++;
            char ch2 = 0;
            if (ix < pattern.length()) {
                ch2 = Character.toLowerCase(pattern.charAt(ix));
            }
            if (ch == '/') {
                if (file == null) {
                    file = new File(word);
                } else {
                    file = new File(file, word);
                }
                word = "";
                continue;
            } else  if (ch == '%') {
                if (ch2 == 't') {
                    String tmpDir = System.getProperty("java.io.tmpdir");
                    if (tmpDir == null) {
                        tmpDir = System.getProperty("user.home");
                    }
                    file = new File(tmpDir);
                    ix++;
                    word = "";
                    continue;
                } else if (ch2 == 'h') {
                    file = new File(System.getProperty("user.home"));
                    if (isSetUID()) {
                        // Ok, we are in a set UID program.  For safety's sake
                        // we disallow attempts to open files relative to %h.
                        throw new IOException("can't use %h in set UID program");
                    }
                    ix++;
                    word = "";
                    continue;
                } else if (ch2 == 'g') {
                    word = word + generation;
                    sawg = true;
                    ix++;
                    continue;
                } else if (ch2 == 'u') {
                    word = word + unique;
                    sawu = true;
                    ix++;
                    continue;
                } else if (ch2 == '%') {
                    word = word + "%";
                    ix++;
                    continue;
                }
            }
            word = word + ch;
        }
        if (count > 1 && !sawg) {
            word = word + "." + generation;
        }
        if (unique > 0 && !sawu) {
            word = word + "." + unique;
        }
        if (word.length() > 0) {
            if (file == null) {
                file = new File(word);
            } else {
                file = new File(file, word);
            }
        }
        return file;
    }

    // Rotate the set of output files
    private synchronized void rotate() {
        Level oldLevel = getLevel();
        setLevel(Level.OFF);

        super.close();
        for (int i = count-2; i >= 0; i--) {
            File f1 = files[i];
            File f2 = files[i+1];
            if (f1.exists()) {
                if (f2.exists()) {
                    f2.delete();
                }
                f1.renameTo(f2);
            }
        }
        try {
            open(files[0], false);
        } catch (IOException ix) {
            // We don't want to throw an exception here, but we
            // report the exception to any registered ErrorManager.
            reportError(null, ix, ErrorManager.OPEN_FAILURE);

        }
        setLevel(oldLevel);
    }

    /**
     * Format and publish a <tt>LogRecord</tt>.
     *
     * @param  record  description of the log event. A null record is
     *                 silently ignored and is not published
     */
    public synchronized void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        super.publish(record);
        flush();
        if (limit > 0 && meter.written >= limit) {
            // We performed access checks in the "init" method to make sure
            // we are only initialized from trusted code.  So we assume
            // it is OK to write the target files, even if we are
            // currently being called from untrusted code.
            // So it is safe to raise privilege here.
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    rotate();
                    return null;
                }
            });
        }
    }

    /**
     * Close all the files.
     *
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     */
    public synchronized void close() throws SecurityException {
        super.close();
        // Unlock any lock file.
        if (lockFileName == null) {
            return;
        }
        try {
            // Closing the lock file's FileOutputStream will close
            // the underlying channel and free any locks.
            lockStream.close();
        } catch (Exception ex) {
            // Problems closing the stream.  Punt.
        }
        synchronized(locks) {
            locks.remove(lockFileName);
        }
        new File(lockFileName).delete();
        lockFileName = null;
        lockStream = null;
    }

    private static class InitializationErrorManager extends ErrorManager {
        Exception lastException;
        public void error(String msg, Exception ex, int code) {
            lastException = ex;
        }
    }

    // Private native method to check if we are in a set UID program.
    private static native boolean isSetUID();
}
