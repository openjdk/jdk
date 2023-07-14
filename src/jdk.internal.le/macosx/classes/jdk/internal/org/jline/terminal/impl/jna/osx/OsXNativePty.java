/*
 * Copyright (c) 2002-2020, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl.jna.osx;

import java.io.FileDescriptor;
import java.io.IOException;

//import com.sun.jna.Native;
//import com.sun.jna.NativeLong;
//import com.sun.jna.Platform;
import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.impl.jna.JnaNativePty;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;

import static jdk.internal.org.jline.terminal.impl.jna.osx.CLibrary.TCSANOW;
import static jdk.internal.org.jline.terminal.impl.jna.osx.CLibrary.TIOCGWINSZ;
import static jdk.internal.org.jline.terminal.impl.jna.osx.CLibrary.TIOCSWINSZ;
import static jdk.internal.org.jline.terminal.impl.jna.osx.CLibrary.termios;
import static jdk.internal.org.jline.terminal.impl.jna.osx.CLibrary.winsize;

public class OsXNativePty extends JnaNativePty {

//    private static final CLibrary C_LIBRARY = Native.load(Platform.C_LIBRARY_NAME, CLibrary.class);
    private static final CLibrary C_LIBRARY = new CLibraryImpl();//Native.load(Platform.C_LIBRARY_NAME, CLibrary.class);

    public static OsXNativePty current(TerminalProvider.Stream consoleStream) throws IOException {
        switch (consoleStream) {
            case Output:
                return new OsXNativePty(-1, null, 0, FileDescriptor.in, 1, FileDescriptor.out, ttyname(0));
            case Error:
                return new OsXNativePty(-1, null, 0, FileDescriptor.in, 2, FileDescriptor.err, ttyname(0));
            default:
                throw new IllegalArgumentException("Unsupport stream for console: " + consoleStream);
        }
    }

    public static OsXNativePty open(Attributes attr, Size size) throws IOException {
        int[] master = new int[1];
        int[] slave = new int[1];
        byte[] buf = new byte[64];
        C_LIBRARY.openpty(master, slave, buf,
                attr != null ? new termios(attr) : null,
                size != null ? new winsize(size) : null);
        int len = 0;
        while (buf[len] != 0) {
            len++;
        }
        String name = new String(buf, 0, len);
        return new OsXNativePty(master[0], newDescriptor(master[0]), slave[0], newDescriptor(slave[0]), name);
    }

    public OsXNativePty(int master, FileDescriptor masterFD, int slave, FileDescriptor slaveFD, String name) {
        super(master, masterFD, slave, slaveFD, name);
    }

    public OsXNativePty(int master, FileDescriptor masterFD, int slave, FileDescriptor slaveFD, int slaveOut, FileDescriptor slaveOutFD, String name) {
        super(master, masterFD, slave, slaveFD, slaveOut, slaveOutFD, name);
    }

    @Override
    public Attributes getAttr() throws IOException {
        termios termios = new termios();
        C_LIBRARY.tcgetattr(getSlave(), termios);
        return termios.toAttributes();
    }

    @Override
    protected void doSetAttr(Attributes attr) throws IOException {
        termios termios = new termios(attr);
        C_LIBRARY.tcsetattr(getSlave(), TCSANOW, termios);
    }

    @Override
    public Size getSize() throws IOException {
        winsize sz = new winsize();
        C_LIBRARY.ioctl(getSlave(), new NativeLong(TIOCGWINSZ), sz);
        return sz.toSize();
    }

    @Override
    public void setSize(Size size) throws IOException {
        winsize sz = new winsize(size);
        C_LIBRARY.ioctl(getSlave(), new NativeLong(TIOCSWINSZ), sz);
    }

    public static int isatty(int fd) {
        return C_LIBRARY.isatty(fd);
    }

    public static String ttyname(int fd) {
        byte[] buf = new byte[64];
        C_LIBRARY.ttyname_r(fd, buf, buf.length);
        int len = 0;
        while (buf[len] != 0) {
            len++;
        }
        return new String(buf, 0, len);
    }
}
