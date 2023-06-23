/*
 * Copyright (c) 2002-2020, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl.jna.linux;

import java.io.FileDescriptor;
import java.io.IOException;

//import com.sun.jna.LastErrorException;
//import com.sun.jna.Native;
//import com.sun.jna.Platform;
import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.impl.jna.JnaNativePty;
import jdk.internal.org.jline.terminal.impl.jna.LastErrorException;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;

import static jdk.internal.org.jline.terminal.impl.jna.linux.CLibrary.TCSADRAIN;
import static jdk.internal.org.jline.terminal.impl.jna.linux.CLibrary.TIOCGWINSZ;
import static jdk.internal.org.jline.terminal.impl.jna.linux.CLibrary.TIOCSWINSZ;
import static jdk.internal.org.jline.terminal.impl.jna.linux.CLibrary.termios;
import static jdk.internal.org.jline.terminal.impl.jna.linux.CLibrary.winsize;

public class LinuxNativePty extends JnaNativePty {

//    private static final CLibrary C_LIBRARY = Native.load(Platform.C_LIBRARY_NAME, CLibrary.class);
    private static final CLibrary C_LIBRARY = new CLibraryImpl();

    public interface UtilLibrary {// extends com.sun.jna.Library {

        void openpty(int[] master, int[] slave, byte[] name, CLibrary.termios t, CLibrary.winsize s) throws LastErrorException;

//        UtilLibrary INSTANCE = Native.load("util", UtilLibrary.class);
        UtilLibrary INSTANCE = new UtilLibraryImpl();
    }

    public static LinuxNativePty current(TerminalProvider.Stream consoleStream) throws IOException {
        switch (consoleStream) {
            case Output:
                return new LinuxNativePty(-1, null, 0, FileDescriptor.in, 1, FileDescriptor.out, ttyname(0));
            case Error:
                return new LinuxNativePty(-1, null, 0, FileDescriptor.in, 2, FileDescriptor.err, ttyname(0));
            default:
                throw new IllegalArgumentException("Unsupport stream for console: " + consoleStream);
        }
    }

    public static LinuxNativePty open(Attributes attr, Size size) throws IOException {
        int[] master = new int[1];
        int[] slave = new int[1];
        byte[] buf = new byte[64];
        UtilLibrary.INSTANCE.openpty(master, slave, buf,
                attr != null ? new termios(attr) : null,
                size != null ? new winsize(size) : null);
        int len = 0;
        while (buf[len] != 0) {
            len++;
        }
        String name = new String(buf, 0, len);
        return new LinuxNativePty(master[0], newDescriptor(master[0]), slave[0], newDescriptor(slave[0]), name);
    }

    public LinuxNativePty(int master, FileDescriptor masterFD, int slave, FileDescriptor slaveFD, String name) {
        super(master, masterFD, slave, slaveFD, name);
    }

    public LinuxNativePty(int master, FileDescriptor masterFD, int slave, FileDescriptor slaveFD, int slaveOut, FileDescriptor slaveOutFD, String name) {
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
        termios org = new termios();
        C_LIBRARY.tcgetattr(getSlave(), org);
        org.c_iflag = termios.c_iflag;
        org.c_oflag = termios.c_oflag;
        org.c_lflag = termios.c_lflag;
        System.arraycopy(termios.c_cc, 0, org.c_cc, 0, termios.c_cc.length);
        C_LIBRARY.tcsetattr(getSlave(), TCSADRAIN, org);
    }

    @Override
    public Size getSize() throws IOException {
        winsize sz = new winsize();
        C_LIBRARY.ioctl(getSlave(), TIOCGWINSZ, sz);
        return sz.toSize();
    }

    @Override
    public void setSize(Size size) throws IOException {
        winsize sz = new winsize(size);
        C_LIBRARY.ioctl(getSlave(), TIOCSWINSZ, sz);
    }

    public static int isatty(int fd) {
        return C_LIBRARY.isatty(fd);
    }

    public static String ttyname(int slave) {
        byte[] buf = new byte[64];
        C_LIBRARY.ttyname_r(slave, buf, buf.length);
        int len = 0;
        while (buf[len] != 0) {
            len++;
        }
        return new String(buf, 0, len);
    }

}
