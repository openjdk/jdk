/*
 * Copyright (c) 2022-2023, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl.ffm;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.impl.AbstractPty;
import jdk.internal.org.jline.terminal.spi.SystemStream;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;

class FfmNativePty extends AbstractPty {
    private final int master;
    private final int slave;
    private final int slaveOut;
    private final String name;
    private final FileDescriptor masterFD;
    private final FileDescriptor slaveFD;
    private final FileDescriptor slaveOutFD;

    public FfmNativePty(TerminalProvider provider, SystemStream systemStream, int master, int slave, String name) {
        this(
                provider,
                systemStream,
                master,
                newDescriptor(master),
                slave,
                newDescriptor(slave),
                slave,
                newDescriptor(slave),
                name);
    }

    public FfmNativePty(
            TerminalProvider provider,
            SystemStream systemStream,
            int master,
            FileDescriptor masterFD,
            int slave,
            FileDescriptor slaveFD,
            int slaveOut,
            FileDescriptor slaveOutFD,
            String name) {
        super(provider, systemStream);
        this.master = master;
        this.slave = slave;
        this.slaveOut = slaveOut;
        this.name = name;
        this.masterFD = masterFD;
        this.slaveFD = slaveFD;
        this.slaveOutFD = slaveOutFD;
    }

    @Override
    public void close() throws IOException {
        if (master > 0) {
            getMasterInput().close();
        }
        if (slave > 0) {
            getSlaveInput().close();
        }
    }

    public int getMaster() {
        return master;
    }

    public int getSlave() {
        return slave;
    }

    public int getSlaveOut() {
        return slaveOut;
    }

    public String getName() {
        return name;
    }

    public FileDescriptor getMasterFD() {
        return masterFD;
    }

    public FileDescriptor getSlaveFD() {
        return slaveFD;
    }

    public FileDescriptor getSlaveOutFD() {
        return slaveOutFD;
    }

    public InputStream getMasterInput() {
        return new FileInputStream(getMasterFD());
    }

    public OutputStream getMasterOutput() {
        return new FileOutputStream(getMasterFD());
    }

    protected InputStream doGetSlaveInput() {
        return new FileInputStream(getSlaveFD());
    }

    public OutputStream getSlaveOutput() {
        return new FileOutputStream(getSlaveOutFD());
    }

    @Override
    public Attributes getAttr() throws IOException {
        return CLibrary.getAttributes(slave);
    }

    @Override
    protected void doSetAttr(Attributes attr) throws IOException {
        CLibrary.setAttributes(slave, attr);
    }

    @Override
    public Size getSize() throws IOException {
        return CLibrary.getTerminalSize(slave);
    }

    @Override
    public void setSize(Size size) throws IOException {
        CLibrary.setTerminalSize(slave, size);
    }

    @Override
    public String toString() {
        return "FfmNativePty[" + getName() + "]";
    }

    public static boolean isPosixSystemStream(SystemStream stream) {
        switch (stream) {
            case Input:
                return CLibrary.isTty(0);
            case Output:
                return CLibrary.isTty(1);
            case Error:
                return CLibrary.isTty(2);
            default:
                throw new IllegalArgumentException();
        }
    }

    public static String posixSystemStreamName(SystemStream stream) {
        switch (stream) {
            case Input:
                return CLibrary.ttyName(0);
            case Output:
                return CLibrary.ttyName(1);
            case Error:
                return CLibrary.ttyName(2);
            default:
                throw new IllegalArgumentException();
        }
    }

    public static int systemStreamWidth(SystemStream systemStream) {
        int fd = systemStream == SystemStream.Output ? 1 : 2;
        return CLibrary.getTerminalSize(fd).getColumns();
    }
}
