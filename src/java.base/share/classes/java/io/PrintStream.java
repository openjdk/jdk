/*
 * Copyright (c) 1996, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

import java.util.Formatter;
import java.util.Locale;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import jdk.internal.access.JavaIOPrintStreamAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.InternalLock;

/**
 * A {@code PrintStream} adds functionality to another output stream,
 * namely the ability to print representations of various data values
 * conveniently.  Two other features are provided as well.  Unlike other output
 * streams, a {@code PrintStream} never throws an
 * {@code IOException}; instead, exceptional situations merely set an
 * internal flag that can be tested via the {@code checkError} method.
 * Optionally, a {@code PrintStream} can be created so as to flush
 * automatically; this means that the {@code flush} method of the underlying
 * output stream is automatically invoked after a byte array is written, one
 * of the {@code println} methods is invoked, or a newline character or byte
 * ({@code '\n'}) is written.
 *
 * <p> All characters printed by a {@code PrintStream} are converted into
 * bytes using the given encoding or charset, or the default charset if not
 * specified.
 * The {@link PrintWriter} class should be used in situations that require
 * writing characters rather than bytes.
 *
 * <p> This class always replaces malformed and unmappable character sequences
 * with the charset's default replacement string.
 * The {@linkplain java.nio.charset.CharsetEncoder} class should be used when more
 * control over the encoding process is required.
 *
 * @author     Frank Yellin
 * @author     Mark Reinhold
 * @since      1.0
 * @see Charset#defaultCharset()
 */

public class PrintStream extends FilterOutputStream
    implements Appendable, Closeable
{
    // initialized to null when PrintStream is sub-classed
    private final InternalLock lock;

    private final boolean autoFlush;
    private boolean trouble = false;
    private Formatter formatter;
    private final Charset charset;

    /**
     * Track both the text- and character-output streams, so that their buffers
     * can be flushed without flushing the entire stream.
     */
    private BufferedWriter textOut;
    private OutputStreamWriter charOut;

    /**
     * requireNonNull is explicitly declared here so as not to create an extra
     * dependency on java.util.Objects.requireNonNull. PrintStream is loaded
     * early during system initialization.
     */
    private static <T> T requireNonNull(T obj, String message) {
        if (obj == null)
            throw new NullPointerException(message);
        return obj;
    }

    /**
     * Returns a charset object for the given charset name.
     * @throws NullPointerException          is csn is null
     * @throws UnsupportedEncodingException  if the charset is not supported
     */
    private static Charset toCharset(String csn)
        throws UnsupportedEncodingException
    {
        requireNonNull(csn, "charsetName");
        try {
            return Charset.forName(csn);
        } catch (IllegalCharsetNameException|UnsupportedCharsetException unused) {
            // UnsupportedEncodingException should be thrown
            throw new UnsupportedEncodingException(csn);
        }
    }

    /* Private constructors */
    private PrintStream(boolean autoFlush, OutputStream out) {
        super(out);
        this.autoFlush = autoFlush;
        this.charset = out instanceof PrintStream ps ? ps.charset() : Charset.defaultCharset();
        this.charOut = new OutputStreamWriter(this, charset);
        this.textOut = new BufferedWriter(charOut);

        // use monitors when PrintStream is sub-classed
        if (getClass() == PrintStream.class) {
            lock = InternalLock.newLockOrNull();
        } else {
            lock = null;
        }
    }

    /* Variant of the private constructor so that the given charset name
     * can be verified before evaluating the OutputStream argument. Used
     * by constructors creating a FileOutputStream that also take a
     * charset name.
     */
    private PrintStream(boolean autoFlush, Charset charset, OutputStream out) {
        this(out, autoFlush, charset);
    }

    /**
     * Creates a new print stream, without automatic line flushing, with the
     * specified OutputStream. Characters written to the stream are converted
     * to bytes using the default charset, or where {@code out} is a
     * {@code PrintStream}, the charset used by the print stream.
     *
     * @param  out        The output stream to which values and objects will be
     *                    printed
     *
     * @see java.io.PrintWriter#PrintWriter(java.io.OutputStream)
     * @see Charset#defaultCharset()
     */
    public PrintStream(OutputStream out) {
        this(out, false);
    }

    /**
     * Creates a new print stream, with the specified OutputStream and line
     * flushing. Characters written to the stream are converted to bytes using
     * the default charset, or where {@code out} is a {@code PrintStream},
     * the charset used by the print stream.
     *
     * @param  out        The output stream to which values and objects will be
     *                    printed
     * @param  autoFlush  Whether the output buffer will be flushed
     *                    whenever a byte array is written, one of the
     *                    {@code println} methods is invoked, or a newline
     *                    character or byte ({@code '\n'}) is written
     *
     * @see java.io.PrintWriter#PrintWriter(java.io.OutputStream, boolean)
     * @see Charset#defaultCharset()
     */
    @SuppressWarnings("this-escape")
    public PrintStream(OutputStream out, boolean autoFlush) {
        this(autoFlush, requireNonNull(out, "Null output stream"));
    }

    /**
     * Creates a new print stream, with the specified OutputStream, line
     * flushing, and character encoding.
     *
     * @param  out        The output stream to which values and objects will be
     *                    printed
     * @param  autoFlush  Whether the output buffer will be flushed
     *                    whenever a byte array is written, one of the
     *                    {@code println} methods is invoked, or a newline
     *                    character or byte ({@code '\n'}) is written
     * @param  encoding   The name of a supported
     *                    <a href="../lang/package-summary.html#charenc">
     *                    character encoding</a>
     *
     * @throws  UnsupportedEncodingException
     *          If the named encoding is not supported
     *
     * @since  1.4
     */
    public PrintStream(OutputStream out, boolean autoFlush, String encoding)
        throws UnsupportedEncodingException
    {
        this(requireNonNull(out, "Null output stream"), autoFlush, toCharset(encoding));
    }

    /**
     * Creates a new print stream, with the specified OutputStream, line
     * flushing and charset.  This convenience constructor creates the necessary
     * intermediate {@link java.io.OutputStreamWriter OutputStreamWriter},
     * which will encode characters using the provided charset.
     *
     * @param  out        The output stream to which values and objects will be
     *                    printed
     * @param  autoFlush  Whether the output buffer will be flushed
     *                    whenever a byte array is written, one of the
     *                    {@code println} methods is invoked, or a newline
     *                    character or byte ({@code '\n'}) is written
     * @param  charset    A {@linkplain Charset charset}
     *
     * @since  10
     */
    @SuppressWarnings("this-escape")
    public PrintStream(OutputStream out, boolean autoFlush, Charset charset) {
        super(out);
        this.autoFlush = autoFlush;
        this.charOut = new OutputStreamWriter(this, charset);
        this.textOut = new BufferedWriter(charOut);
        this.charset = charset;

        // use monitors when PrintStream is sub-classed
        if (getClass() == PrintStream.class) {
            lock = InternalLock.newLockOrNull();
        } else {
            lock = null;
        }
    }

    /**
     * Creates a new print stream, without automatic line flushing, with the
     * specified file name.  This convenience constructor creates
     * the necessary intermediate {@link java.io.OutputStreamWriter
     * OutputStreamWriter}, which will encode characters using the
     * {@linkplain Charset#defaultCharset() default charset}
     * for this instance of the Java virtual machine.
     *
     * @param  fileName
     *         The name of the file to use as the destination of this print
     *         stream.  If the file exists, then it will be truncated to
     *         zero size; otherwise, a new file will be created.  The output
     *         will be written to the file and is buffered.
     *
     * @throws  FileNotFoundException
     *          If the given file object does not denote an existing, writable
     *          regular file and a new regular file of that name cannot be
     *          created, or if some other error occurs while opening or
     *          creating the file
     *
     * @throws  SecurityException
     *          If a security manager is present and {@link
     *          SecurityManager#checkWrite checkWrite(fileName)} denies write
     *          access to the file
     * @see Charset#defaultCharset()
     *
     * @since  1.5
     */
    @SuppressWarnings("this-escape")
    public PrintStream(String fileName) throws FileNotFoundException {
        this(false, new FileOutputStream(fileName));
    }

    /**
     * Creates a new print stream, without automatic line flushing, with the
     * specified file name and charset.  This convenience constructor creates
     * the necessary intermediate {@link java.io.OutputStreamWriter
     * OutputStreamWriter}, which will encode characters using the provided
     * charset.
     *
     * @param  fileName
     *         The name of the file to use as the destination of this print
     *         stream.  If the file exists, then it will be truncated to
     *         zero size; otherwise, a new file will be created.  The output
     *         will be written to the file and is buffered.
     *
     * @param  csn
     *         The name of a supported {@linkplain Charset charset}
     *
     * @throws  FileNotFoundException
     *          If the given file object does not denote an existing, writable
     *          regular file and a new regular file of that name cannot be
     *          created, or if some other error occurs while opening or
     *          creating the file
     *
     * @throws  SecurityException
     *          If a security manager is present and {@link
     *          SecurityManager#checkWrite checkWrite(fileName)} denies write
     *          access to the file
     *
     * @throws  UnsupportedEncodingException
     *          If the named charset is not supported
     *
     * @since  1.5
     */
    public PrintStream(String fileName, String csn)
        throws FileNotFoundException, UnsupportedEncodingException
    {
        // ensure charset is checked before the file is opened
        this(false, toCharset(csn), new FileOutputStream(fileName));
    }

    /**
     * Creates a new print stream, without automatic line flushing, with the
     * specified file name and charset.  This convenience constructor creates
     * the necessary intermediate {@link java.io.OutputStreamWriter
     * OutputStreamWriter}, which will encode characters using the provided
     * charset.
     *
     * @param  fileName
     *         The name of the file to use as the destination of this print
     *         stream.  If the file exists, then it will be truncated to
     *         zero size; otherwise, a new file will be created.  The output
     *         will be written to the file and is buffered.
     *
     * @param  charset
     *         A {@linkplain Charset charset}
     *
     * @throws  IOException
     *          if an I/O error occurs while opening or creating the file
     *
     * @throws  SecurityException
     *          If a security manager is present and {@link
     *          SecurityManager#checkWrite checkWrite(fileName)} denies write
     *          access to the file
     *
     * @since  10
     */
    public PrintStream(String fileName, Charset charset) throws IOException {
        this(false, requireNonNull(charset, "charset"), new FileOutputStream(fileName));
    }

    /**
     * Creates a new print stream, without automatic line flushing, with the
     * specified file.  This convenience constructor creates the necessary
     * intermediate {@link java.io.OutputStreamWriter OutputStreamWriter},
     * which will encode characters using the {@linkplain
     * Charset#defaultCharset() default charset} for this
     * instance of the Java virtual machine.
     *
     * @param  file
     *         The file to use as the destination of this print stream.  If the
     *         file exists, then it will be truncated to zero size; otherwise,
     *         a new file will be created.  The output will be written to the
     *         file and is buffered.
     *
     * @throws  FileNotFoundException
     *          If the given file object does not denote an existing, writable
     *          regular file and a new regular file of that name cannot be
     *          created, or if some other error occurs while opening or
     *          creating the file
     *
     * @throws  SecurityException
     *          If a security manager is present and {@link
     *          SecurityManager#checkWrite checkWrite(file.getPath())}
     *          denies write access to the file
     * @see Charset#defaultCharset()
     *
     * @since  1.5
     */
    @SuppressWarnings("this-escape")
    public PrintStream(File file) throws FileNotFoundException {
        this(false, new FileOutputStream(file));
    }

    /**
     * Creates a new print stream, without automatic line flushing, with the
     * specified file and charset.  This convenience constructor creates
     * the necessary intermediate {@link java.io.OutputStreamWriter
     * OutputStreamWriter}, which will encode characters using the provided
     * charset.
     *
     * @param  file
     *         The file to use as the destination of this print stream.  If the
     *         file exists, then it will be truncated to zero size; otherwise,
     *         a new file will be created.  The output will be written to the
     *         file and is buffered.
     *
     * @param  csn
     *         The name of a supported {@linkplain Charset charset}
     *
     * @throws  FileNotFoundException
     *          If the given file object does not denote an existing, writable
     *          regular file and a new regular file of that name cannot be
     *          created, or if some other error occurs while opening or
     *          creating the file
     *
     * @throws  SecurityException
     *          If a security manager is present and {@link
     *          SecurityManager#checkWrite checkWrite(file.getPath())}
     *          denies write access to the file
     *
     * @throws  UnsupportedEncodingException
     *          If the named charset is not supported
     *
     * @since  1.5
     */
    public PrintStream(File file, String csn)
        throws FileNotFoundException, UnsupportedEncodingException
    {
        // ensure charset is checked before the file is opened
        this(false, toCharset(csn), new FileOutputStream(file));
    }


    /**
     * Creates a new print stream, without automatic line flushing, with the
     * specified file and charset.  This convenience constructor creates
     * the necessary intermediate {@link java.io.OutputStreamWriter
     * OutputStreamWriter}, which will encode characters using the provided
     * charset.
     *
     * @param  file
     *         The file to use as the destination of this print stream.  If the
     *         file exists, then it will be truncated to zero size; otherwise,
     *         a new file will be created.  The output will be written to the
     *         file and is buffered.
     *
     * @param  charset
     *         A {@linkplain Charset charset}
     *
     * @throws  IOException
     *          if an I/O error occurs while opening or creating the file
     *
     * @throws  SecurityException
     *          If a security manager is present and {@link
     *          SecurityManager#checkWrite checkWrite(file.getPath())}
     *          denies write access to the file
     *
     * @since  10
     */
    public PrintStream(File file, Charset charset) throws IOException {
        this(false, requireNonNull(charset, "charset"), new FileOutputStream(file));
    }

    /** Check to make sure that the stream has not been closed */
    private void ensureOpen() throws IOException {
        if (out == null)
            throw new IOException("Stream closed");
    }

    /**
     * Flushes the stream.  This is done by writing any buffered output bytes to
     * the underlying output stream and then flushing that stream.
     *
     * @see        java.io.OutputStream#flush()
     */
    @Override
    public void flush() {
        if (lock != null) {
            lock.lock();
            try {
                implFlush();
            } finally {
                lock.unlock();
            }
        } else {
            synchronized (this) {
                implFlush();
            }
        }
    }

    private void implFlush() {
        try {
            ensureOpen();
            out.flush();
        }
        catch (IOException x) {
            trouble = true;
        }
    }

    private boolean closing = false; /* To avoid recursive closing */

    /**
     * Closes the stream.  This is done by flushing the stream and then closing
     * the underlying output stream.
     *
     * @see        java.io.OutputStream#close()
     */
    @Override
    public void close() {
        if (lock != null) {
            lock.lock();
            try {
                implClose();
            } finally {
                lock.unlock();
            }
        } else {
            synchronized (this) {
                implClose();
            }
        }
    }

    private void implClose() {
        if (!closing) {
            closing = true;
            try {
                textOut.close();
                out.close();
            }
            catch (IOException x) {
                trouble = true;
            }
            textOut = null;
            charOut = null;
            out = null;
        }
    }

    /**
     * Flushes the stream if it's not closed and checks its error state.
     *
     * @return {@code true} if and only if this stream has encountered an
     *         {@code IOException}, or the {@code setError} method has been
     *         invoked
     */
    public boolean checkError() {
        if (out != null)
            flush();
        if (out instanceof PrintStream ps) {
            return ps.checkError();
        }
        return trouble;
    }

    /**
     * Sets the error state of the stream to {@code true}.
     *
     * <p> This method will cause subsequent invocations of {@link
     * #checkError()} to return {@code true} until
     * {@link #clearError()} is invoked.
     *
     * @since 1.1
     */
    protected void setError() {
        trouble = true;
    }

    /**
     * Clears the error state of this stream.
     *
     * <p> This method will cause subsequent invocations of {@link
     * #checkError()} to return {@code false} until another write
     * operation fails and invokes {@link #setError()}.
     *
     * @since 1.6
     */
    protected void clearError() {
        trouble = false;
    }

    /*
     * Exception-catching, synchronized output operations,
     * which also implement the write() methods of OutputStream
     */

    /**
     * Writes the specified byte to this stream.  If the byte is a newline and
     * automatic flushing is enabled then the {@code flush} method will be
     * invoked on the underlying output stream.
     *
     * <p> Note that the byte is written as given; to write a character that
     * will be translated according to the default charset, use the
     * {@code print(char)} or {@code println(char)} methods.
     *
     * @param  b  The byte to be written
     * @see #print(char)
     * @see #println(char)
     */
    @Override
    public void write(int b) {
        try {
            if (lock != null) {
                lock.lock();
                try {
                    implWrite(b);
                } finally {
                    lock.unlock();
                }
            } else {
                synchronized (this) {
                    implWrite(b);
                }
            }
        }
        catch (InterruptedIOException x) {
            Thread.currentThread().interrupt();
        }
        catch (IOException x) {
            trouble = true;
        }
    }

    private void implWrite(int b) throws IOException {
        ensureOpen();
        out.write(b);
        if ((b == '\n') && autoFlush)
            out.flush();
    }

    /**
     * Writes {@code len} bytes from the specified byte array starting at
     * offset {@code off} to this stream.  If automatic flushing is
     * enabled then the {@code flush} method will be invoked on the underlying
     * output stream.
     *
     * <p> Note that the bytes will be written as given; to write characters
     * that will be translated according to the default charset, use the
     * {@code print(char)} or {@code println(char)} methods.
     *
     * @param  buf   A byte array
     * @param  off   Offset from which to start taking bytes
     * @param  len   Number of bytes to write
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    @Override
    public void write(byte[] buf, int off, int len) {
        try {
            if (lock != null) {
                lock.lock();
                try {
                    implWrite(buf, off, len);
                } finally {
                    lock.unlock();
                }
            } else {
                synchronized (this) {
                    implWrite(buf, off, len);
                }
            }
        }
        catch (InterruptedIOException x) {
            Thread.currentThread().interrupt();
        }
        catch (IOException x) {
            trouble = true;
        }
    }

    private void implWrite(byte[] buf, int off, int len) throws IOException {
        ensureOpen();
        out.write(buf, off, len);
        if (autoFlush)
            out.flush();
    }


    /**
     * Writes all bytes from the specified byte array to this stream. If
     * automatic flushing is enabled then the {@code flush} method will be
     * invoked on the underlying output stream.
     *
     * <p> Note that the bytes will be written as given; to write characters
     * that will be translated according to the default charset, use the
     * {@code print(char[])} or {@code println(char[])} methods.
     *
     * @apiNote
     * Although declared to throw {@code IOException}, this method never
     * actually does so. Instead, like other methods that this class
     * overrides, it sets an internal flag which may be tested via the
     * {@link #checkError()} method. To write an array of bytes without having
     * to write a {@code catch} block for the {@code IOException}, use either
     * {@link #writeBytes(byte[] buf) writeBytes(buf)} or
     * {@link #write(byte[], int, int) write(buf, 0, buf.length)}.
     *
     * @implSpec
     * This method is equivalent to
     * {@link java.io.PrintStream#write(byte[],int,int)
     * this.write(buf, 0, buf.length)}.
     *
     * @param  buf   A byte array
     *
     * @throws IOException If an I/O error occurs.
     *
     * @see #writeBytes(byte[])
     * @see #write(byte[],int,int)
     *
     * @since 14
     */
    @Override
    public void write(byte[] buf) throws IOException {
        this.write(buf, 0, buf.length);
    }

    /**
     * Writes all bytes from the specified byte array to this stream.
     * If automatic flushing is enabled then the {@code flush} method
     * will be invoked.
     *
     * <p> Note that the bytes will be written as given; to write characters
     * that will be translated according to the default charset, use the
     * {@code print(char[])} or {@code println(char[])} methods.
     *
     * @implSpec
     * This method is equivalent to
     * {@link #write(byte[], int, int) this.write(buf, 0, buf.length)}.
     *
     * @param  buf   A byte array
     *
     * @since 14
     */
    public void writeBytes(byte[] buf) {
        this.write(buf, 0, buf.length);
    }

    /*
     * The following private methods on the text- and character-output streams
     * always flush the stream buffers, so that writes to the underlying byte
     * stream occur as promptly as with the original PrintStream.
     */

    private void write(char[] buf) {
        try {
            if (lock != null) {
                lock.lock();
                try {
                    implWrite(buf);
                } finally {
                    lock.unlock();
                }
            } else {
                synchronized (this) {
                    implWrite(buf);
                }
            }
        } catch (InterruptedIOException x) {
            Thread.currentThread().interrupt();
        } catch (IOException x) {
            trouble = true;
        }
    }

    private void implWrite(char[] buf) throws IOException {
        ensureOpen();
        textOut.write(buf);
        textOut.flushBuffer();
        charOut.flushBuffer();
        if (autoFlush) {
            for (int i = 0; i < buf.length; i++)
                if (buf[i] == '\n') {
                    out.flush();
                    break;
                }
        }
    }

    // Used to optimize away back-to-back flushing and synchronization when
    // using println, but since subclasses could exist which depend on
    // observing a call to print followed by newLine() we only use this if
    // getClass() == PrintStream.class to avoid compatibility issues.
    private void writeln(char[] buf) {
        try {
            if (lock != null) {
                lock.lock();
                try {
                    implWriteln(buf);
                } finally {
                    lock.unlock();
                }
            } else {
                synchronized (this) {
                    implWriteln(buf);
                }
            }
        }
        catch (InterruptedIOException x) {
            Thread.currentThread().interrupt();
        }
        catch (IOException x) {
            trouble = true;
        }
    }

    private void implWriteln(char[] buf) throws IOException {
        ensureOpen();
        textOut.write(buf);
        textOut.newLine();
        textOut.flushBuffer();
        charOut.flushBuffer();
        if (autoFlush)
            out.flush();
    }

    private void write(String s) {
        try {
            if (lock != null) {
                lock.lock();
                try {
                    implWrite(s);
                } finally {
                    lock.unlock();
                }
            } else {
                synchronized (this) {
                    implWrite(s);
                }
            }
        }
        catch (InterruptedIOException x) {
            Thread.currentThread().interrupt();
        }
        catch (IOException x) {
            trouble = true;
        }
    }

    private void implWrite(String s) throws IOException {
        ensureOpen();
        textOut.write(s);
        textOut.flushBuffer();
        charOut.flushBuffer();
        if (autoFlush && (s.indexOf('\n') >= 0))
            out.flush();
    }

    // Used to optimize away back-to-back flushing and synchronization when
    // using println, but since subclasses could exist which depend on
    // observing a call to print followed by newLine we only use this if
    // getClass() == PrintStream.class to avoid compatibility issues.
    private void writeln(String s) {
        try {
            if (lock != null) {
                lock.lock();
                try {
                    implWriteln(s);
                } finally {
                    lock.unlock();
                }
            } else {
                synchronized (this) {
                    implWriteln(s);
                }
            }
        }
        catch (InterruptedIOException x) {
            Thread.currentThread().interrupt();
        }
        catch (IOException x) {
            trouble = true;
        }
    }

    private void implWriteln(String s) throws IOException {
        ensureOpen();
        textOut.write(s);
        textOut.newLine();
        textOut.flushBuffer();
        charOut.flushBuffer();
        if (autoFlush)
            out.flush();
    }

    private void newLine() {
        try {
            if (lock != null) {
                lock.lock();
                try {
                    implNewLine();
                } finally {
                    lock.unlock();
                }
            } else {
                synchronized (this) {
                    implNewLine();
                }
            }
        }
        catch (InterruptedIOException x) {
            Thread.currentThread().interrupt();
        }
        catch (IOException x) {
            trouble = true;
        }
    }

    private void implNewLine() throws IOException {
        ensureOpen();
        textOut.newLine();
        textOut.flushBuffer();
        charOut.flushBuffer();
        if (autoFlush)
            out.flush();
    }

    /* Methods that do not terminate lines */

    /**
     * Prints a boolean value.  The string produced by {@link
     * java.lang.String#valueOf(boolean)} is translated into bytes
     * according to the default charset, and these bytes
     * are written in exactly the manner of the
     * {@link #write(int)} method.
     *
     * @param      b   The {@code boolean} to be printed
     * @see Charset#defaultCharset()
     */
    public void print(boolean b) {
        write(String.valueOf(b));
    }

    /**
     * Prints a character.  The character is translated into one or more bytes
     * according to the character encoding given to the constructor, or the
     * default charset if none specified. These bytes
     * are written in exactly the manner of the {@link #write(int)} method.
     *
     * @param      c   The {@code char} to be printed
     * @see Charset#defaultCharset()
     */
    public void print(char c) {
        write(String.valueOf(c));
    }

    /**
     * Prints an integer.  The string produced by {@link
     * java.lang.String#valueOf(int)} is translated into bytes
     * according to the default charset, and these bytes
     * are written in exactly the manner of the
     * {@link #write(int)} method.
     *
     * @param      i   The {@code int} to be printed
     * @see        java.lang.Integer#toString(int)
     * @see Charset#defaultCharset()
     */
    public void print(int i) {
        write(String.valueOf(i));
    }

    /**
     * Prints a long integer.  The string produced by {@link
     * java.lang.String#valueOf(long)} is translated into bytes
     * according to the default charset, and these bytes
     * are written in exactly the manner of the
     * {@link #write(int)} method.
     *
     * @param      l   The {@code long} to be printed
     * @see        java.lang.Long#toString(long)
     * @see Charset#defaultCharset()
     */
    public void print(long l) {
        write(String.valueOf(l));
    }

    /**
     * Prints a floating-point number.  The string produced by {@link
     * java.lang.String#valueOf(float)} is translated into bytes
     * according to the default charset, and these bytes
     * are written in exactly the manner of the
     * {@link #write(int)} method.
     *
     * @param      f   The {@code float} to be printed
     * @see        java.lang.Float#toString(float)
     * @see Charset#defaultCharset()
     */
    public void print(float f) {
        write(String.valueOf(f));
    }

    /**
     * Prints a double-precision floating-point number.  The string produced by
     * {@link java.lang.String#valueOf(double)} is translated into
     * bytes according to the default charset, and these
     * bytes are written in exactly the manner of the {@link
     * #write(int)} method.
     *
     * @param      d   The {@code double} to be printed
     * @see        java.lang.Double#toString(double)
     * @see Charset#defaultCharset()
     */
    public void print(double d) {
        write(String.valueOf(d));
    }

    /**
     * Prints an array of characters.  The characters are converted into bytes
     * according to the character encoding given to the constructor, or the
     * default charset if none specified. These bytes
     * are written in exactly the manner of the {@link #write(int)} method.
     *
     * @param      s   The array of chars to be printed
     * @see Charset#defaultCharset()
     *
     * @throws  NullPointerException  If {@code s} is {@code null}
     */
    public void print(char[] s) {
        write(s);
    }

    /**
     * Prints a string.  If the argument is {@code null} then the string
     * {@code "null"} is printed.  Otherwise, the string's characters are
     * converted into bytes according to the character encoding given to the
     * constructor, or the default charset if none
     * specified. These bytes are written in exactly the manner of the
     * {@link #write(int)} method.
     *
     * @param      s   The {@code String} to be printed
     * @see Charset#defaultCharset()
     */
    public void print(String s) {
        write(String.valueOf(s));
    }

    /**
     * Prints an object.  The string produced by the {@link
     * java.lang.String#valueOf(Object)} method is translated into bytes
     * according to the default charset, and these bytes
     * are written in exactly the manner of the
     * {@link #write(int)} method.
     *
     * @param      obj   The {@code Object} to be printed
     * @see        java.lang.Object#toString()
     * @see Charset#defaultCharset()
     */
    public void print(Object obj) {
        write(String.valueOf(obj));
    }


    /* Methods that do terminate lines */

    /**
     * Terminates the current line by writing the line separator string.  The
     * line separator string is defined by the system property
     * {@code line.separator}, and is not necessarily a single newline
     * character ({@code '\n'}).
     */
    public void println() {
        newLine();
    }

    /**
     * Prints a boolean and then terminates the line.  This method behaves as
     * though it invokes {@link #print(boolean)} and then
     * {@link #println()}.
     *
     * @param x  The {@code boolean} to be printed
     */
    public void println(boolean x) {
        if (getClass() == PrintStream.class) {
            writeln(String.valueOf(x));
        } else {
            synchronized (this) {
                print(x);
                newLine();
            }
        }
    }

    /**
     * Prints a character and then terminates the line.  This method behaves as
     * though it invokes {@link #print(char)} and then
     * {@link #println()}.
     *
     * @param x  The {@code char} to be printed.
     */
    public void println(char x) {
        if (getClass() == PrintStream.class) {
            writeln(String.valueOf(x));
        } else {
            synchronized (this) {
                print(x);
                newLine();
            }
        }
    }

    /**
     * Prints an integer and then terminates the line.  This method behaves as
     * though it invokes {@link #print(int)} and then
     * {@link #println()}.
     *
     * @param x  The {@code int} to be printed.
     */
    public void println(int x) {
        if (getClass() == PrintStream.class) {
            writeln(String.valueOf(x));
        } else {
            synchronized (this) {
                print(x);
                newLine();
            }
        }
    }

    /**
     * Prints a long and then terminates the line.  This method behaves as
     * though it invokes {@link #print(long)} and then
     * {@link #println()}.
     *
     * @param x  a The {@code long} to be printed.
     */
    public void println(long x) {
        if (getClass() == PrintStream.class) {
            writeln(String.valueOf(x));
        } else {
            synchronized (this) {
                print(x);
                newLine();
            }
        }
    }

    /**
     * Prints a float and then terminates the line.  This method behaves as
     * though it invokes {@link #print(float)} and then
     * {@link #println()}.
     *
     * @param x  The {@code float} to be printed.
     */
    public void println(float x) {
        if (getClass() == PrintStream.class) {
            writeln(String.valueOf(x));
        } else {
            synchronized (this) {
                print(x);
                newLine();
            }
        }
    }

    /**
     * Prints a double and then terminates the line.  This method behaves as
     * though it invokes {@link #print(double)} and then
     * {@link #println()}.
     *
     * @param x  The {@code double} to be printed.
     */
    public void println(double x) {
        if (getClass() == PrintStream.class) {
            writeln(String.valueOf(x));
        } else {
            synchronized (this) {
                print(x);
                newLine();
            }
        }
    }

    /**
     * Prints an array of characters and then terminates the line.  This method
     * behaves as though it invokes {@link #print(char[])} and
     * then {@link #println()}.
     *
     * @param x  an array of chars to print.
     */
    public void println(char[] x) {
        if (getClass() == PrintStream.class) {
            writeln(x);
        } else {
            synchronized (this) {
                print(x);
                newLine();
            }
        }
    }

    /**
     * Prints a String and then terminates the line.  This method behaves as
     * though it invokes {@link #print(String)} and then
     * {@link #println()}.
     *
     * @param x  The {@code String} to be printed.
     */
    public void println(String x) {
        if (getClass() == PrintStream.class) {
            writeln(String.valueOf(x));
        } else {
            synchronized (this) {
                print(x);
                newLine();
            }
        }
    }

    /**
     * Prints an Object and then terminates the line.  This method calls
     * at first String.valueOf(x) to get the printed object's string value,
     * then behaves as
     * though it invokes {@link #print(String)} and then
     * {@link #println()}.
     *
     * @param x  The {@code Object} to be printed.
     */
    public void println(Object x) {
        String s = String.valueOf(x);
        if (getClass() == PrintStream.class) {
            // need to apply String.valueOf again since first invocation
            // might return null
            writeln(String.valueOf(s));
        } else {
            synchronized (this) {
                print(s);
                newLine();
            }
        }
    }


    /**
     * A convenience method to write a formatted string to this output stream
     * using the specified format string and arguments.
     *
     * <p> An invocation of this method of the form
     * {@code out.printf(format, args)} behaves
     * in exactly the same way as the invocation
     *
     * {@snippet lang=java :
     *     out.format(format, args)
     * }
     *
     * @param  format
     *         A format string as described in <a
     *         href="../util/Formatter.html#syntax">Format string syntax</a>
     *
     * @param  args
     *         Arguments referenced by the format specifiers in the format
     *         string.  If there are more arguments than format specifiers, the
     *         extra arguments are ignored.  The number of arguments is
     *         variable and may be zero.  The maximum number of arguments is
     *         limited by the maximum dimension of a Java array as defined by
     *         <cite>The Java Virtual Machine Specification</cite>.
     *         The behaviour on a
     *         {@code null} argument depends on the <a
     *         href="../util/Formatter.html#syntax">conversion</a>.
     *
     * @throws  java.util.IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a
     *          href="../util/Formatter.html#detail">Details</a> section of the
     *          formatter class specification.
     *
     * @throws  NullPointerException
     *          If the {@code format} is {@code null}
     *
     * @return  This output stream
     *
     * @since  1.5
     */
    public PrintStream printf(String format, Object ... args) {
        return format(format, args);
    }

    /**
     * A convenience method to write a formatted string to this output stream
     * using the specified format string and arguments.
     *
     * <p> An invocation of this method of the form
     * {@code out.printf(l, format, args)} behaves
     * in exactly the same way as the invocation
     *
     * {@snippet lang=java :
     *     out.format(l, format, args)
     * }
     *
     * @param  l
     *         The {@linkplain java.util.Locale locale} to apply during
     *         formatting.  If {@code l} is {@code null} then no localization
     *         is applied.
     *
     * @param  format
     *         A format string as described in <a
     *         href="../util/Formatter.html#syntax">Format string syntax</a>
     *
     * @param  args
     *         Arguments referenced by the format specifiers in the format
     *         string.  If there are more arguments than format specifiers, the
     *         extra arguments are ignored.  The number of arguments is
     *         variable and may be zero.  The maximum number of arguments is
     *         limited by the maximum dimension of a Java array as defined by
     *         <cite>The Java Virtual Machine Specification</cite>.
     *         The behaviour on a
     *         {@code null} argument depends on the <a
     *         href="../util/Formatter.html#syntax">conversion</a>.
     *
     * @throws  java.util.IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a
     *          href="../util/Formatter.html#detail">Details</a> section of the
     *          formatter class specification.
     *
     * @throws  NullPointerException
     *          If the {@code format} is {@code null}
     *
     * @return  This output stream
     *
     * @since  1.5
     */
    public PrintStream printf(Locale l, String format, Object ... args) {
        return format(l, format, args);
    }

    /**
     * Writes a formatted string to this output stream using the specified
     * format string and arguments.
     *
     * <p> The locale always used is the one returned by {@link
     * java.util.Locale#getDefault(Locale.Category)} with
     * {@link java.util.Locale.Category#FORMAT FORMAT} category specified,
     * regardless of any previous invocations of other formatting methods on
     * this object.
     *
     * @param  format
     *         A format string as described in <a
     *         href="../util/Formatter.html#syntax">Format string syntax</a>
     *
     * @param  args
     *         Arguments referenced by the format specifiers in the format
     *         string.  If there are more arguments than format specifiers, the
     *         extra arguments are ignored.  The number of arguments is
     *         variable and may be zero.  The maximum number of arguments is
     *         limited by the maximum dimension of a Java array as defined by
     *         <cite>The Java Virtual Machine Specification</cite>.
     *         The behaviour on a
     *         {@code null} argument depends on the <a
     *         href="../util/Formatter.html#syntax">conversion</a>.
     *
     * @throws  java.util.IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a
     *          href="../util/Formatter.html#detail">Details</a> section of the
     *          formatter class specification.
     *
     * @throws  NullPointerException
     *          If the {@code format} is {@code null}
     *
     * @return  This output stream
     *
     * @since  1.5
     */
    public PrintStream format(String format, Object ... args) {
        try {
            if (lock != null) {
                lock.lock();
                try {
                    implFormat(format, args);
                } finally {
                    lock.unlock();
                }
            } else {
                synchronized (this) {
                    implFormat(format, args);
                }
            }
        } catch (InterruptedIOException x) {
            Thread.currentThread().interrupt();
        } catch (IOException x) {
            trouble = true;
        }
        return this;
    }

    private void implFormat(String format, Object ... args) throws IOException {
        ensureOpen();
        if ((formatter == null) || (formatter.locale() != Locale.getDefault(Locale.Category.FORMAT)))
            formatter = new Formatter((Appendable) this);
        formatter.format(Locale.getDefault(Locale.Category.FORMAT), format, args);
    }

    /**
     * Writes a formatted string to this output stream using the specified
     * format string and arguments.
     *
     * @param  l
     *         The {@linkplain java.util.Locale locale} to apply during
     *         formatting.  If {@code l} is {@code null} then no localization
     *         is applied.
     *
     * @param  format
     *         A format string as described in <a
     *         href="../util/Formatter.html#syntax">Format string syntax</a>
     *
     * @param  args
     *         Arguments referenced by the format specifiers in the format
     *         string.  If there are more arguments than format specifiers, the
     *         extra arguments are ignored.  The number of arguments is
     *         variable and may be zero.  The maximum number of arguments is
     *         limited by the maximum dimension of a Java array as defined by
     *         <cite>The Java Virtual Machine Specification</cite>.
     *         The behaviour on a
     *         {@code null} argument depends on the <a
     *         href="../util/Formatter.html#syntax">conversion</a>.
     *
     * @throws  java.util.IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a
     *          href="../util/Formatter.html#detail">Details</a> section of the
     *          formatter class specification.
     *
     * @throws  NullPointerException
     *          If the {@code format} is {@code null}
     *
     * @return  This output stream
     *
     * @since  1.5
     */
    public PrintStream format(Locale l, String format, Object ... args) {
        try {
            if (lock != null) {
                lock.lock();
                try {
                    implFormat(l, format, args);
                } finally {
                    lock.unlock();
                }
            } else {
                synchronized (this) {
                    implFormat(l, format, args);
                }
            }
        } catch (InterruptedIOException x) {
            Thread.currentThread().interrupt();
        } catch (IOException x) {
            trouble = true;
        }
        return this;
    }

    private void implFormat(Locale l, String format, Object ... args) throws IOException {
        ensureOpen();
        if ((formatter == null) || (formatter.locale() != l))
            formatter = new Formatter(this, l);
        formatter.format(l, format, args);
    }

    /**
     * Appends the specified character sequence to this output stream.
     *
     * <p> An invocation of this method of the form {@code out.append(csq)}
     * when {@code csq} is not {@code null}, behaves in exactly the same way
     * as the invocation
     *
     * {@snippet lang=java :
     *     out.print(csq.toString())
     * }
     *
     * <p> Depending on the specification of {@code toString} for the
     * character sequence {@code csq}, the entire sequence may not be
     * appended.  For instance, invoking the {@code toString} method of a
     * character buffer will return a subsequence whose content depends upon
     * the buffer's position and limit.
     *
     * @param  csq
     *         The character sequence to append.  If {@code csq} is
     *         {@code null}, then the four characters {@code "null"} are
     *         appended to this output stream.
     *
     * @return  This output stream
     *
     * @since  1.5
     */
    public PrintStream append(CharSequence csq) {
        print(String.valueOf(csq));
        return this;
    }

    /**
     * Appends a subsequence of the specified character sequence to this output
     * stream.
     *
     * <p> An invocation of this method of the form
     * {@code out.append(csq, start, end)} when
     * {@code csq} is not {@code null}, behaves in
     * exactly the same way as the invocation
     *
     * {@snippet lang=java :
     *     out.print(csq.subSequence(start, end).toString())
     * }
     *
     * @param  csq
     *         The character sequence from which a subsequence will be
     *         appended.  If {@code csq} is {@code null}, then characters
     *         will be appended as if {@code csq} contained the four
     *         characters {@code "null"}.
     *
     * @param  start
     *         The index of the first character in the subsequence
     *
     * @param  end
     *         The index of the character following the last character in the
     *         subsequence
     *
     * @return  This output stream
     *
     * @throws  IndexOutOfBoundsException
     *          If {@code start} or {@code end} are negative, {@code start}
     *          is greater than {@code end}, or {@code end} is greater than
     *          {@code csq.length()}
     *
     * @since  1.5
     */
    public PrintStream append(CharSequence csq, int start, int end) {
        if (csq == null) csq = "null";
        return append(csq.subSequence(start, end));
    }

    /**
     * Appends the specified character to this output stream.
     *
     * <p> An invocation of this method of the form {@code out.append(c)}
     * behaves in exactly the same way as the invocation
     *
     * {@snippet lang=java :
     *     out.print(c)
     * }
     *
     * @param  c
     *         The 16-bit character to append
     *
     * @return  This output stream
     *
     * @since  1.5
     */
    public PrintStream append(char c) {
        print(c);
        return this;
    }

    /**
     * {@return the charset used in this {@code PrintStream} instance}
     *
     * @since 18
     */
    public Charset charset() {
        return charset;
    }

    static {
        SharedSecrets.setJavaIOCPrintStreamAccess(new JavaIOPrintStreamAccess() {
            public Object lock(PrintStream ps) {
                Object lock = ps.lock;
                return (lock != null) ? lock : ps;
            }
        });
    }
}
