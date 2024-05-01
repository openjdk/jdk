/*
 * Copyright (c) 2022-2023, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl.ffm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
//import java.util.logging.Level;
//import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.spi.Pty;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;
import jdk.internal.org.jline.utils.OSUtils;

@SuppressWarnings("restricted")
class CLibrary {

//    private static final Logger logger = Logger.getLogger("org.jline");

    // Window sizes.
    // @see <a href="http://man7.org/linux/man-pages/man4/tty_ioctl.4.html">IOCTL_TTY(2) man-page</a>
    static class winsize {
        static final GroupLayout LAYOUT;
        private static final VarHandle ws_col;
        private static final VarHandle ws_row;

        static {
            LAYOUT = MemoryLayout.structLayout(
                    ValueLayout.JAVA_SHORT.withName("ws_row"),
                    ValueLayout.JAVA_SHORT.withName("ws_col"),
                    ValueLayout.JAVA_SHORT,
                    ValueLayout.JAVA_SHORT);
            ws_row = FfmTerminalProvider.lookupVarHandle(LAYOUT, MemoryLayout.PathElement.groupElement("ws_row"));
            ws_col = FfmTerminalProvider.lookupVarHandle(LAYOUT, MemoryLayout.PathElement.groupElement("ws_col"));
        }

        private final java.lang.foreign.MemorySegment seg;

        winsize() {
            seg = java.lang.foreign.Arena.ofAuto().allocate(LAYOUT);
        }

        winsize(short ws_col, short ws_row) {
            this();
            ws_col(ws_col);
            ws_row(ws_row);
        }

        java.lang.foreign.MemorySegment segment() {
            return seg;
        }

        short ws_col() {
            return (short) ws_col.get(seg);
        }

        void ws_col(short col) {
            ws_col.set(seg, col);
        }

        short ws_row() {
            return (short) ws_row.get(seg);
        }

        void ws_row(short row) {
            ws_row.set(seg, row);
        }
    }

    // termios structure for termios functions, describing a general terminal interface that is
    // provided to control asynchronous communications ports
    // @see <a href="http://man7.org/linux/man-pages/man3/termios.3.html">TERMIOS(3) man-page</a>
    static class termios {
        static final GroupLayout LAYOUT;
        private static final VarHandle c_iflag;
        private static final VarHandle c_oflag;
        private static final VarHandle c_cflag;
        private static final VarHandle c_lflag;
        private static final long c_cc_offset;
        private static final VarHandle c_ispeed;
        private static final VarHandle c_ospeed;

        static {
            if (OSUtils.IS_OSX) {
                LAYOUT = MemoryLayout.structLayout(
                        ValueLayout.JAVA_LONG.withName("c_iflag"),
                        ValueLayout.JAVA_LONG.withName("c_oflag"),
                        ValueLayout.JAVA_LONG.withName("c_cflag"),
                        ValueLayout.JAVA_LONG.withName("c_lflag"),
                        MemoryLayout.sequenceLayout(32, ValueLayout.JAVA_BYTE).withName("c_cc"),
                        ValueLayout.JAVA_LONG.withName("c_ispeed"),
                        ValueLayout.JAVA_LONG.withName("c_ospeed"));
            } else if (OSUtils.IS_LINUX) {
                LAYOUT = MemoryLayout.structLayout(
                        ValueLayout.JAVA_INT.withName("c_iflag"),
                        ValueLayout.JAVA_INT.withName("c_oflag"),
                        ValueLayout.JAVA_INT.withName("c_cflag"),
                        ValueLayout.JAVA_INT.withName("c_lflag"),
                        ValueLayout.JAVA_BYTE.withName("c_line"),
                        MemoryLayout.sequenceLayout(32, ValueLayout.JAVA_BYTE).withName("c_cc"),
                        MemoryLayout.paddingLayout(3),
                        ValueLayout.JAVA_INT.withName("c_ispeed"),
                        ValueLayout.JAVA_INT.withName("c_ospeed"));
            } else {
                throw new IllegalStateException("Unsupported system!");
            }
            c_iflag = adjust2LinuxHandle(
                    FfmTerminalProvider.lookupVarHandle(LAYOUT, MemoryLayout.PathElement.groupElement("c_iflag")));
            c_oflag = adjust2LinuxHandle(
                    FfmTerminalProvider.lookupVarHandle(LAYOUT, MemoryLayout.PathElement.groupElement("c_oflag")));
            c_cflag = adjust2LinuxHandle(
                    FfmTerminalProvider.lookupVarHandle(LAYOUT, MemoryLayout.PathElement.groupElement("c_cflag")));
            c_lflag = adjust2LinuxHandle(
                    FfmTerminalProvider.lookupVarHandle(LAYOUT, MemoryLayout.PathElement.groupElement("c_lflag")));
            c_cc_offset = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("c_cc"));
            c_ispeed = adjust2LinuxHandle(
                    FfmTerminalProvider.lookupVarHandle(LAYOUT, MemoryLayout.PathElement.groupElement("c_ispeed")));
            c_ospeed = adjust2LinuxHandle(
                    FfmTerminalProvider.lookupVarHandle(LAYOUT, MemoryLayout.PathElement.groupElement("c_ospeed")));
        }

        private static VarHandle adjust2LinuxHandle(VarHandle v) {
            if (OSUtils.IS_LINUX) {
                MethodHandle id = MethodHandles.identity(int.class);
                v = MethodHandles.filterValue(
                        v,
                        MethodHandles.explicitCastArguments(id, MethodType.methodType(int.class, long.class)),
                        MethodHandles.explicitCastArguments(id, MethodType.methodType(long.class, int.class)));
            }

            return v;
        }

        private final java.lang.foreign.MemorySegment seg;

        termios() {
            seg = java.lang.foreign.Arena.ofAuto().allocate(LAYOUT);
        }

        termios(Attributes t) {
            this();
            // Input flags
            long c_iflag = 0;
            c_iflag = setFlag(t.getInputFlag(Attributes.InputFlag.IGNBRK), IGNBRK, c_iflag);
            c_iflag = setFlag(t.getInputFlag(Attributes.InputFlag.BRKINT), BRKINT, c_iflag);
            c_iflag = setFlag(t.getInputFlag(Attributes.InputFlag.IGNPAR), IGNPAR, c_iflag);
            c_iflag = setFlag(t.getInputFlag(Attributes.InputFlag.PARMRK), PARMRK, c_iflag);
            c_iflag = setFlag(t.getInputFlag(Attributes.InputFlag.INPCK), INPCK, c_iflag);
            c_iflag = setFlag(t.getInputFlag(Attributes.InputFlag.ISTRIP), ISTRIP, c_iflag);
            c_iflag = setFlag(t.getInputFlag(Attributes.InputFlag.INLCR), INLCR, c_iflag);
            c_iflag = setFlag(t.getInputFlag(Attributes.InputFlag.IGNCR), IGNCR, c_iflag);
            c_iflag = setFlag(t.getInputFlag(Attributes.InputFlag.ICRNL), ICRNL, c_iflag);
            c_iflag = setFlag(t.getInputFlag(Attributes.InputFlag.IXON), IXON, c_iflag);
            c_iflag = setFlag(t.getInputFlag(Attributes.InputFlag.IXOFF), IXOFF, c_iflag);
            c_iflag = setFlag(t.getInputFlag(Attributes.InputFlag.IXANY), IXANY, c_iflag);
            c_iflag = setFlag(t.getInputFlag(Attributes.InputFlag.IMAXBEL), IMAXBEL, c_iflag);
            c_iflag = setFlag(t.getInputFlag(Attributes.InputFlag.IUTF8), IUTF8, c_iflag);
            c_iflag(c_iflag);
            // Output flags
            long c_oflag = 0;
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.OPOST), OPOST, c_oflag);
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.ONLCR), ONLCR, c_oflag);
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.OXTABS), OXTABS, c_oflag);
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.ONOEOT), ONOEOT, c_oflag);
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.OCRNL), OCRNL, c_oflag);
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.ONOCR), ONOCR, c_oflag);
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.ONLRET), ONLRET, c_oflag);
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.OFILL), OFILL, c_oflag);
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.NLDLY), NLDLY, c_oflag);
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.TABDLY), TABDLY, c_oflag);
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.CRDLY), CRDLY, c_oflag);
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.FFDLY), FFDLY, c_oflag);
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.BSDLY), BSDLY, c_oflag);
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.VTDLY), VTDLY, c_oflag);
            c_oflag = setFlag(t.getOutputFlag(Attributes.OutputFlag.OFDEL), OFDEL, c_oflag);
            c_oflag(c_oflag);
            // Control flags
            long c_cflag = 0;
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.CIGNORE), CIGNORE, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.CS5), CS5, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.CS6), CS6, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.CS7), CS7, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.CS8), CS8, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.CSTOPB), CSTOPB, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.CREAD), CREAD, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.PARENB), PARENB, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.PARODD), PARODD, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.HUPCL), HUPCL, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.CLOCAL), CLOCAL, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.CCTS_OFLOW), CCTS_OFLOW, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.CRTS_IFLOW), CRTS_IFLOW, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.CDTR_IFLOW), CDTR_IFLOW, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.CDSR_OFLOW), CDSR_OFLOW, c_cflag);
            c_cflag = setFlag(t.getControlFlag(Attributes.ControlFlag.CCAR_OFLOW), CCAR_OFLOW, c_cflag);
            c_cflag(c_cflag);
            // Local flags
            long c_lflag = 0;
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.ECHOKE), ECHOKE, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.ECHOE), ECHOE, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.ECHOK), ECHOK, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.ECHO), ECHO, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.ECHONL), ECHONL, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.ECHOPRT), ECHOPRT, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.ECHOCTL), ECHOCTL, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.ISIG), ISIG, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.ICANON), ICANON, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.ALTWERASE), ALTWERASE, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.IEXTEN), IEXTEN, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.EXTPROC), EXTPROC, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.TOSTOP), TOSTOP, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.FLUSHO), FLUSHO, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.NOKERNINFO), NOKERNINFO, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.PENDIN), PENDIN, c_lflag);
            c_lflag = setFlag(t.getLocalFlag(Attributes.LocalFlag.NOFLSH), NOFLSH, c_lflag);
            c_lflag(c_lflag);
            // Control chars
            byte[] c_cc = new byte[20];
            c_cc[VEOF] = (byte) t.getControlChar(Attributes.ControlChar.VEOF);
            c_cc[VEOL] = (byte) t.getControlChar(Attributes.ControlChar.VEOL);
            c_cc[VEOL2] = (byte) t.getControlChar(Attributes.ControlChar.VEOL2);
            c_cc[VERASE] = (byte) t.getControlChar(Attributes.ControlChar.VERASE);
            c_cc[VWERASE] = (byte) t.getControlChar(Attributes.ControlChar.VWERASE);
            c_cc[VKILL] = (byte) t.getControlChar(Attributes.ControlChar.VKILL);
            c_cc[VREPRINT] = (byte) t.getControlChar(Attributes.ControlChar.VREPRINT);
            c_cc[VINTR] = (byte) t.getControlChar(Attributes.ControlChar.VINTR);
            c_cc[VQUIT] = (byte) t.getControlChar(Attributes.ControlChar.VQUIT);
            c_cc[VSUSP] = (byte) t.getControlChar(Attributes.ControlChar.VSUSP);
            if (VDSUSP != (-1)) {
                c_cc[VDSUSP] = (byte) t.getControlChar(Attributes.ControlChar.VDSUSP);
            }
            c_cc[VSTART] = (byte) t.getControlChar(Attributes.ControlChar.VSTART);
            c_cc[VSTOP] = (byte) t.getControlChar(Attributes.ControlChar.VSTOP);
            c_cc[VLNEXT] = (byte) t.getControlChar(Attributes.ControlChar.VLNEXT);
            c_cc[VDISCARD] = (byte) t.getControlChar(Attributes.ControlChar.VDISCARD);
            c_cc[VMIN] = (byte) t.getControlChar(Attributes.ControlChar.VMIN);
            c_cc[VTIME] = (byte) t.getControlChar(Attributes.ControlChar.VTIME);
            if (VSTATUS != (-1)) {
                c_cc[VSTATUS] = (byte) t.getControlChar(Attributes.ControlChar.VSTATUS);
            }
            c_cc().copyFrom(java.lang.foreign.MemorySegment.ofArray(c_cc));
        }

        java.lang.foreign.MemorySegment segment() {
            return seg;
        }

        long c_iflag() {
            return (long) c_iflag.get(seg);
        }

        void c_iflag(long f) {
            c_iflag.set(seg, f);
        }

        long c_oflag() {
            return (long) c_oflag.get(seg);
        }

        void c_oflag(long f) {
            c_oflag.set(seg, f);
        }

        long c_cflag() {
            return (long) c_cflag.get(seg);
        }

        void c_cflag(long f) {
            c_cflag.set(seg, f);
        }

        long c_lflag() {
            return (long) c_lflag.get(seg);
        }

        void c_lflag(long f) {
            c_lflag.set(seg, f);
        }

        java.lang.foreign.MemorySegment c_cc() {
            return seg.asSlice(c_cc_offset, 20);
        }

        long c_ispeed() {
            return (long) c_ispeed.get(seg);
        }

        void c_ispeed(long f) {
            c_ispeed.set(seg, f);
        }

        long c_ospeed() {
            return (long) c_ospeed.get(seg);
        }

        void c_ospeed(long f) {
            c_ospeed.set(seg, f);
        }

        private static long setFlag(boolean flag, long value, long org) {
            return flag ? org | value : org;
        }

        private static <T extends Enum<T>> void addFlag(long value, EnumSet<T> flags, T flag, int v) {
            if ((value & v) != 0) {
                flags.add(flag);
            }
        }

        public Attributes asAttributes() {
            Attributes attr = new Attributes();
            // Input flags
            long c_iflag = c_iflag();
            EnumSet<Attributes.InputFlag> iflag = attr.getInputFlags();
            addFlag(c_iflag, iflag, Attributes.InputFlag.IGNBRK, IGNBRK);
            addFlag(c_iflag, iflag, Attributes.InputFlag.IGNBRK, IGNBRK);
            addFlag(c_iflag, iflag, Attributes.InputFlag.BRKINT, BRKINT);
            addFlag(c_iflag, iflag, Attributes.InputFlag.IGNPAR, IGNPAR);
            addFlag(c_iflag, iflag, Attributes.InputFlag.PARMRK, PARMRK);
            addFlag(c_iflag, iflag, Attributes.InputFlag.INPCK, INPCK);
            addFlag(c_iflag, iflag, Attributes.InputFlag.ISTRIP, ISTRIP);
            addFlag(c_iflag, iflag, Attributes.InputFlag.INLCR, INLCR);
            addFlag(c_iflag, iflag, Attributes.InputFlag.IGNCR, IGNCR);
            addFlag(c_iflag, iflag, Attributes.InputFlag.ICRNL, ICRNL);
            addFlag(c_iflag, iflag, Attributes.InputFlag.IXON, IXON);
            addFlag(c_iflag, iflag, Attributes.InputFlag.IXOFF, IXOFF);
            addFlag(c_iflag, iflag, Attributes.InputFlag.IXANY, IXANY);
            addFlag(c_iflag, iflag, Attributes.InputFlag.IMAXBEL, IMAXBEL);
            addFlag(c_iflag, iflag, Attributes.InputFlag.IUTF8, IUTF8);
            // Output flags
            long c_oflag = c_oflag();
            EnumSet<Attributes.OutputFlag> oflag = attr.getOutputFlags();
            addFlag(c_oflag, oflag, Attributes.OutputFlag.OPOST, OPOST);
            addFlag(c_oflag, oflag, Attributes.OutputFlag.ONLCR, ONLCR);
            addFlag(c_oflag, oflag, Attributes.OutputFlag.OXTABS, OXTABS);
            addFlag(c_oflag, oflag, Attributes.OutputFlag.ONOEOT, ONOEOT);
            addFlag(c_oflag, oflag, Attributes.OutputFlag.OCRNL, OCRNL);
            addFlag(c_oflag, oflag, Attributes.OutputFlag.ONOCR, ONOCR);
            addFlag(c_oflag, oflag, Attributes.OutputFlag.ONLRET, ONLRET);
            addFlag(c_oflag, oflag, Attributes.OutputFlag.OFILL, OFILL);
            addFlag(c_oflag, oflag, Attributes.OutputFlag.NLDLY, NLDLY);
            addFlag(c_oflag, oflag, Attributes.OutputFlag.TABDLY, TABDLY);
            addFlag(c_oflag, oflag, Attributes.OutputFlag.CRDLY, CRDLY);
            addFlag(c_oflag, oflag, Attributes.OutputFlag.FFDLY, FFDLY);
            addFlag(c_oflag, oflag, Attributes.OutputFlag.BSDLY, BSDLY);
            addFlag(c_oflag, oflag, Attributes.OutputFlag.VTDLY, VTDLY);
            addFlag(c_oflag, oflag, Attributes.OutputFlag.OFDEL, OFDEL);
            // Control flags
            long c_cflag = c_cflag();
            EnumSet<Attributes.ControlFlag> cflag = attr.getControlFlags();
            addFlag(c_cflag, cflag, Attributes.ControlFlag.CIGNORE, CIGNORE);
            addFlag(c_cflag, cflag, Attributes.ControlFlag.CS5, CS5);
            addFlag(c_cflag, cflag, Attributes.ControlFlag.CS6, CS6);
            addFlag(c_cflag, cflag, Attributes.ControlFlag.CS7, CS7);
            addFlag(c_cflag, cflag, Attributes.ControlFlag.CS8, CS8);
            addFlag(c_cflag, cflag, Attributes.ControlFlag.CSTOPB, CSTOPB);
            addFlag(c_cflag, cflag, Attributes.ControlFlag.CREAD, CREAD);
            addFlag(c_cflag, cflag, Attributes.ControlFlag.PARENB, PARENB);
            addFlag(c_cflag, cflag, Attributes.ControlFlag.PARODD, PARODD);
            addFlag(c_cflag, cflag, Attributes.ControlFlag.HUPCL, HUPCL);
            addFlag(c_cflag, cflag, Attributes.ControlFlag.CLOCAL, CLOCAL);
            addFlag(c_cflag, cflag, Attributes.ControlFlag.CCTS_OFLOW, CCTS_OFLOW);
            addFlag(c_cflag, cflag, Attributes.ControlFlag.CRTS_IFLOW, CRTS_IFLOW);
            addFlag(c_cflag, cflag, Attributes.ControlFlag.CDSR_OFLOW, CDSR_OFLOW);
            addFlag(c_cflag, cflag, Attributes.ControlFlag.CCAR_OFLOW, CCAR_OFLOW);
            // Local flags
            long c_lflag = c_lflag();
            EnumSet<Attributes.LocalFlag> lflag = attr.getLocalFlags();
            addFlag(c_lflag, lflag, Attributes.LocalFlag.ECHOKE, ECHOKE);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.ECHOE, ECHOE);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.ECHOK, ECHOK);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.ECHO, ECHO);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.ECHONL, ECHONL);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.ECHOPRT, ECHOPRT);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.ECHOCTL, ECHOCTL);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.ISIG, ISIG);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.ICANON, ICANON);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.ALTWERASE, ALTWERASE);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.IEXTEN, IEXTEN);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.EXTPROC, EXTPROC);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.TOSTOP, TOSTOP);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.FLUSHO, FLUSHO);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.NOKERNINFO, NOKERNINFO);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.PENDIN, PENDIN);
            addFlag(c_lflag, lflag, Attributes.LocalFlag.NOFLSH, NOFLSH);
            // Control chars
            byte[] c_cc = c_cc().toArray(ValueLayout.JAVA_BYTE);
            EnumMap<Attributes.ControlChar, Integer> cc = attr.getControlChars();
            cc.put(Attributes.ControlChar.VEOF, (int) c_cc[VEOF]);
            cc.put(Attributes.ControlChar.VEOL, (int) c_cc[VEOL]);
            cc.put(Attributes.ControlChar.VEOL2, (int) c_cc[VEOL2]);
            cc.put(Attributes.ControlChar.VERASE, (int) c_cc[VERASE]);
            cc.put(Attributes.ControlChar.VWERASE, (int) c_cc[VWERASE]);
            cc.put(Attributes.ControlChar.VKILL, (int) c_cc[VKILL]);
            cc.put(Attributes.ControlChar.VREPRINT, (int) c_cc[VREPRINT]);
            cc.put(Attributes.ControlChar.VINTR, (int) c_cc[VINTR]);
            cc.put(Attributes.ControlChar.VQUIT, (int) c_cc[VQUIT]);
            cc.put(Attributes.ControlChar.VSUSP, (int) c_cc[VSUSP]);
            if (VDSUSP != (-1)) {
                cc.put(Attributes.ControlChar.VDSUSP, (int) c_cc[VDSUSP]);
            }
            cc.put(Attributes.ControlChar.VSTART, (int) c_cc[VSTART]);
            cc.put(Attributes.ControlChar.VSTOP, (int) c_cc[VSTOP]);
            cc.put(Attributes.ControlChar.VLNEXT, (int) c_cc[VLNEXT]);
            cc.put(Attributes.ControlChar.VDISCARD, (int) c_cc[VDISCARD]);
            cc.put(Attributes.ControlChar.VMIN, (int) c_cc[VMIN]);
            cc.put(Attributes.ControlChar.VTIME, (int) c_cc[VTIME]);
            if (VSTATUS != (-1)) {
                cc.put(Attributes.ControlChar.VSTATUS, (int) c_cc[VSTATUS]);
            }
            // Return
            return attr;
        }
    }

    static MethodHandle ioctl;
    static MethodHandle isatty;
    static MethodHandle openpty;
    static MethodHandle tcsetattr;
    static MethodHandle tcgetattr;
    static MethodHandle ttyname_r;
    static LinkageError openptyError;

    static {
        // methods
        Linker linker = Linker.nativeLinker();
        SymbolLookup lookup = SymbolLookup.loaderLookup().or(linker.defaultLookup());
        // https://man7.org/linux/man-pages/man2/ioctl.2.html
        ioctl = linker.downcallHandle(
                lookup.find("ioctl").get(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS),
                Linker.Option.firstVariadicArg(2));
        // https://www.man7.org/linux/man-pages/man3/isatty.3.html
        isatty = linker.downcallHandle(
                lookup.find("isatty").get(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        // https://man7.org/linux/man-pages/man3/tcsetattr.3p.html
        tcsetattr = linker.downcallHandle(
                lookup.find("tcsetattr").get(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        // https://man7.org/linux/man-pages/man3/tcgetattr.3p.html
        tcgetattr = linker.downcallHandle(
                lookup.find("tcgetattr").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        // https://man7.org/linux/man-pages/man3/ttyname.3.html
        ttyname_r = linker.downcallHandle(
                lookup.find("ttyname_r").get(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        // https://man7.org/linux/man-pages/man3/openpty.3.html
        LinkageError error = null;
        Optional<MemorySegment> openPtyAddr = lookup.find("openpty");
        if (openPtyAddr.isPresent()) {
            openpty = linker.downcallHandle(
                    openPtyAddr.get(),
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS));
            openptyError = null;
        } else {
            openpty = null;
            openptyError = error;
        }
    }

    private static String readFully(InputStream in) throws IOException {
        int readLen = 0;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[32];
        while ((readLen = in.read(buf, 0, buf.length)) >= 0) {
            b.write(buf, 0, readLen);
        }
        return b.toString();
    }

    static Size getTerminalSize(int fd) {
        try {
            winsize ws = new winsize();
            int res = (int) ioctl.invoke(fd, (long) TIOCGWINSZ, ws.segment());
            return new Size(ws.ws_col(), ws.ws_row());
        } catch (Throwable e) {
            throw new RuntimeException("Unable to call ioctl(TIOCGWINSZ)", e);
        }
    }

    static void setTerminalSize(int fd, Size size) {
        try {
            winsize ws = new winsize();
            ws.ws_row((short) size.getRows());
            ws.ws_col((short) size.getColumns());
            int res = (int) ioctl.invoke(fd, TIOCSWINSZ, ws.segment());
        } catch (Throwable e) {
            throw new RuntimeException("Unable to call ioctl(TIOCSWINSZ)", e);
        }
    }

    static Attributes getAttributes(int fd) {
        try {
            termios t = new termios();
            int res = (int) tcgetattr.invoke(fd, t.segment());
            return t.asAttributes();
        } catch (Throwable e) {
            throw new RuntimeException("Unable to call tcgetattr()", e);
        }
    }

    static void setAttributes(int fd, Attributes attr) {
        try {
            termios t = new termios(attr);
            int res = (int) tcsetattr.invoke(fd, TCSANOW, t.segment());
        } catch (Throwable e) {
            throw new RuntimeException("Unable to call tcsetattr()", e);
        }
    }

    static boolean isTty(int fd) {
        try {
            return (int) isatty.invoke(fd) == 1;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to call isatty()", e);
        }
    }

    static String ttyName(int fd) {
        try {
            java.lang.foreign.MemorySegment buf =
                    java.lang.foreign.Arena.ofAuto().allocate(64);
            int res = (int) ttyname_r.invoke(fd, buf, buf.byteSize());
            byte[] data = buf.toArray(ValueLayout.JAVA_BYTE);
            int len = 0;
            while (data[len] != 0) {
                len++;
            }
            return new String(data, 0, len);
        } catch (Throwable e) {
            throw new RuntimeException("Unable to call ttyname_r()", e);
        }
    }

    static Pty openpty(TerminalProvider provider, Attributes attr, Size size) {
        if (openptyError != null) {
            throw openptyError;
        }
        try {
            java.lang.foreign.MemorySegment buf =
                    java.lang.foreign.Arena.ofAuto().allocate(64);
            java.lang.foreign.MemorySegment master =
                    java.lang.foreign.Arena.ofAuto().allocate(ValueLayout.JAVA_INT);
            java.lang.foreign.MemorySegment slave =
                    java.lang.foreign.Arena.ofAuto().allocate(ValueLayout.JAVA_INT);
            int res = (int) openpty.invoke(
                    master,
                    slave,
                    buf,
                    attr != null ? new termios(attr).segment() : java.lang.foreign.MemorySegment.NULL,
                    size != null
                            ? new winsize((short) size.getRows(), (short) size.getColumns()).segment()
                            : java.lang.foreign.MemorySegment.NULL);
            byte[] str = buf.toArray(ValueLayout.JAVA_BYTE);
            int len = 0;
            while (str[len] != 0) {
                len++;
            }
            String device = new String(str, 0, len);
            return new FfmNativePty(
                    provider, null, master.get(ValueLayout.JAVA_INT, 0), slave.get(ValueLayout.JAVA_INT, 0), device);
        } catch (Throwable e) {
            throw new RuntimeException("Unable to call openpty()", e);
        }
    }

    // CONSTANTS

    private static final int TIOCGWINSZ;
    private static final int TIOCSWINSZ;

    private static final int TCSANOW;
    private static int TCSADRAIN;
    private static int TCSAFLUSH;

    private static final int VEOF;
    private static final int VEOL;
    private static final int VEOL2;
    private static final int VERASE;
    private static final int VWERASE;
    private static final int VKILL;
    private static final int VREPRINT;
    private static final int VERASE2;
    private static final int VINTR;
    private static final int VQUIT;
    private static final int VSUSP;
    private static final int VDSUSP;
    private static final int VSTART;
    private static final int VSTOP;
    private static final int VLNEXT;
    private static final int VDISCARD;
    private static final int VMIN;
    private static final int VSWTC;
    private static final int VTIME;
    private static final int VSTATUS;

    private static final int IGNBRK;
    private static final int BRKINT;
    private static final int IGNPAR;
    private static final int PARMRK;
    private static final int INPCK;
    private static final int ISTRIP;
    private static final int INLCR;
    private static final int IGNCR;
    private static final int ICRNL;
    private static int IUCLC;
    private static final int IXON;
    private static final int IXOFF;
    private static final int IXANY;
    private static final int IMAXBEL;
    private static int IUTF8;

    private static final int OPOST;
    private static int OLCUC;
    private static final int ONLCR;
    private static int OXTABS;
    private static int NLDLY;
    private static int NL0;
    private static int NL1;
    private static final int TABDLY;
    private static int TAB0;
    private static int TAB1;
    private static int TAB2;
    private static int TAB3;
    private static int CRDLY;
    private static int CR0;
    private static int CR1;
    private static int CR2;
    private static int CR3;
    private static int FFDLY;
    private static int FF0;
    private static int FF1;
    private static int XTABS;
    private static int BSDLY;
    private static int BS0;
    private static int BS1;
    private static int VTDLY;
    private static int VT0;
    private static int VT1;
    private static int CBAUD;
    private static int B0;
    private static int B50;
    private static int B75;
    private static int B110;
    private static int B134;
    private static int B150;
    private static int B200;
    private static int B300;
    private static int B600;
    private static int B1200;
    private static int B1800;
    private static int B2400;
    private static int B4800;
    private static int B9600;
    private static int B19200;
    private static int B38400;
    private static int EXTA;
    private static int EXTB;
    private static int OFDEL;
    private static int ONOEOT;
    private static final int OCRNL;
    private static int ONOCR;
    private static final int ONLRET;
    private static int OFILL;

    private static int CIGNORE;
    private static int CSIZE;
    private static final int CS5;
    private static final int CS6;
    private static final int CS7;
    private static final int CS8;
    private static final int CSTOPB;
    private static final int CREAD;
    private static final int PARENB;
    private static final int PARODD;
    private static final int HUPCL;
    private static final int CLOCAL;
    private static int CCTS_OFLOW;
    private static int CRTS_IFLOW;
    private static int CDTR_IFLOW;
    private static int CDSR_OFLOW;
    private static int CCAR_OFLOW;

    private static final int ECHOKE;
    private static final int ECHOE;
    private static final int ECHOK;
    private static final int ECHO;
    private static final int ECHONL;
    private static final int ECHOPRT;
    private static final int ECHOCTL;
    private static final int ISIG;
    private static final int ICANON;
    private static int XCASE;
    private static int ALTWERASE;
    private static final int IEXTEN;
    private static final int EXTPROC;
    private static final int TOSTOP;
    private static final int FLUSHO;
    private static int NOKERNINFO;
    private static final int PENDIN;
    private static final int NOFLSH;

    static {
        String osName = System.getProperty("os.name");
        if (osName.startsWith("Linux")) {
            String arch = System.getProperty("os.arch");
            boolean isMipsPpcOrSparc = arch.equals("mips")
                    || arch.equals("mips64")
                    || arch.equals("mipsel")
                    || arch.equals("mips64el")
                    || arch.startsWith("ppc")
                    || arch.startsWith("sparc");
            TIOCGWINSZ = isMipsPpcOrSparc ? 0x40087468 : 0x00005413;
            TIOCSWINSZ = isMipsPpcOrSparc ? 0x80087467 : 0x00005414;

            TCSANOW = 0x0;
            TCSADRAIN = 0x1;
            TCSAFLUSH = 0x2;

            VINTR = 0;
            VQUIT = 1;
            VERASE = 2;
            VKILL = 3;
            VEOF = 4;
            VTIME = 5;
            VMIN = 6;
            VSWTC = 7;
            VSTART = 8;
            VSTOP = 9;
            VSUSP = 10;
            VEOL = 11;
            VREPRINT = 12;
            VDISCARD = 13;
            VWERASE = 14;
            VLNEXT = 15;
            VEOL2 = 16;
            VERASE2 = -1;
            VDSUSP = -1;
            VSTATUS = -1;

            IGNBRK = 0x0000001;
            BRKINT = 0x0000002;
            IGNPAR = 0x0000004;
            PARMRK = 0x0000008;
            INPCK = 0x0000010;
            ISTRIP = 0x0000020;
            INLCR = 0x0000040;
            IGNCR = 0x0000080;
            ICRNL = 0x0000100;
            IUCLC = 0x0000200;
            IXON = 0x0000400;
            IXANY = 0x0000800;
            IXOFF = 0x0001000;
            IMAXBEL = 0x0002000;
            IUTF8 = 0x0004000;

            OPOST = 0x0000001;
            OLCUC = 0x0000002;
            ONLCR = 0x0000004;
            OCRNL = 0x0000008;
            ONOCR = 0x0000010;
            ONLRET = 0x0000020;
            OFILL = 0x0000040;
            OFDEL = 0x0000080;
            NLDLY = 0x0000100;
            NL0 = 0x0000000;
            NL1 = 0x0000100;
            CRDLY = 0x0000600;
            CR0 = 0x0000000;
            CR1 = 0x0000200;
            CR2 = 0x0000400;
            CR3 = 0x0000600;
            TABDLY = 0x0001800;
            TAB0 = 0x0000000;
            TAB1 = 0x0000800;
            TAB2 = 0x0001000;
            TAB3 = 0x0001800;
            XTABS = 0x0001800;
            BSDLY = 0x0002000;
            BS0 = 0x0000000;
            BS1 = 0x0002000;
            VTDLY = 0x0004000;
            VT0 = 0x0000000;
            VT1 = 0x0004000;
            FFDLY = 0x0008000;
            FF0 = 0x0000000;
            FF1 = 0x0008000;

            CBAUD = 0x000100f;
            B0 = 0x0000000;
            B50 = 0x0000001;
            B75 = 0x0000002;
            B110 = 0x0000003;
            B134 = 0x0000004;
            B150 = 0x0000005;
            B200 = 0x0000006;
            B300 = 0x0000007;
            B600 = 0x0000008;
            B1200 = 0x0000009;
            B1800 = 0x000000a;
            B2400 = 0x000000b;
            B4800 = 0x000000c;
            B9600 = 0x000000d;
            B19200 = 0x000000e;
            B38400 = 0x000000f;
            EXTA = B19200;
            EXTB = B38400;
            CSIZE = 0x0000030;
            CS5 = 0x0000000;
            CS6 = 0x0000010;
            CS7 = 0x0000020;
            CS8 = 0x0000030;
            CSTOPB = 0x0000040;
            CREAD = 0x0000080;
            PARENB = 0x0000100;
            PARODD = 0x0000200;
            HUPCL = 0x0000400;
            CLOCAL = 0x0000800;

            ISIG = 0x0000001;
            ICANON = 0x0000002;
            XCASE = 0x0000004;
            ECHO = 0x0000008;
            ECHOE = 0x0000010;
            ECHOK = 0x0000020;
            ECHONL = 0x0000040;
            NOFLSH = 0x0000080;
            TOSTOP = 0x0000100;
            ECHOCTL = 0x0000200;
            ECHOPRT = 0x0000400;
            ECHOKE = 0x0000800;
            FLUSHO = 0x0001000;
            PENDIN = 0x0002000;
            IEXTEN = 0x0008000;
            EXTPROC = 0x0010000;
        } else if (osName.startsWith("Solaris") || osName.startsWith("SunOS")) {
            int _TIOC = ('T' << 8);
            TIOCGWINSZ = (_TIOC | 104);
            TIOCSWINSZ = (_TIOC | 103);

            TCSANOW = 0x0;
            TCSADRAIN = 0x1;
            TCSAFLUSH = 0x2;

            VINTR = 0;
            VQUIT = 1;
            VERASE = 2;
            VKILL = 3;
            VEOF = 4;
            VTIME = 5;
            VMIN = 6;
            VSWTC = 7;
            VSTART = 8;
            VSTOP = 9;
            VSUSP = 10;
            VEOL = 11;
            VREPRINT = 12;
            VDISCARD = 13;
            VWERASE = 14;
            VLNEXT = 15;
            VEOL2 = 16;
            VERASE2 = -1;
            VDSUSP = -1;
            VSTATUS = -1;

            IGNBRK = 0x0000001;
            BRKINT = 0x0000002;
            IGNPAR = 0x0000004;
            PARMRK = 0x0000010;
            INPCK = 0x0000020;
            ISTRIP = 0x0000040;
            INLCR = 0x0000100;
            IGNCR = 0x0000200;
            ICRNL = 0x0000400;
            IUCLC = 0x0001000;
            IXON = 0x0002000;
            IXANY = 0x0004000;
            IXOFF = 0x0010000;
            IMAXBEL = 0x0020000;
            IUTF8 = 0x0040000;

            OPOST = 0x0000001;
            OLCUC = 0x0000002;
            ONLCR = 0x0000004;
            OCRNL = 0x0000010;
            ONOCR = 0x0000020;
            ONLRET = 0x0000040;
            OFILL = 0x0000100;
            OFDEL = 0x0000200;
            NLDLY = 0x0000400;
            NL0 = 0x0000000;
            NL1 = 0x0000400;
            CRDLY = 0x0003000;
            CR0 = 0x0000000;
            CR1 = 0x0001000;
            CR2 = 0x0002000;
            CR3 = 0x0003000;
            TABDLY = 0x0014000;
            TAB0 = 0x0000000;
            TAB1 = 0x0004000;
            TAB2 = 0x0010000;
            TAB3 = 0x0014000;
            XTABS = 0x0014000;
            BSDLY = 0x0020000;
            BS0 = 0x0000000;
            BS1 = 0x0020000;
            VTDLY = 0x0040000;
            VT0 = 0x0000000;
            VT1 = 0x0040000;
            FFDLY = 0x0100000;
            FF0 = 0x0000000;
            FF1 = 0x0100000;

            CBAUD = 0x0010017;
            B0 = 0x0000000;
            B50 = 0x0000001;
            B75 = 0x0000002;
            B110 = 0x0000003;
            B134 = 0x0000004;
            B150 = 0x0000005;
            B200 = 0x0000006;
            B300 = 0x0000007;
            B600 = 0x0000010;
            B1200 = 0x0000011;
            B1800 = 0x0000012;
            B2400 = 0x0000013;
            B4800 = 0x0000014;
            B9600 = 0x0000015;
            B19200 = 0x0000016;
            B38400 = 0x0000017;
            EXTA = 0xB19200;
            EXTB = 0xB38400;
            CSIZE = 0x0000060;
            CS5 = 0x0000000;
            CS6 = 0x0000020;
            CS7 = 0x0000040;
            CS8 = 0x0000060;
            CSTOPB = 0x0000100;
            CREAD = 0x0000200;
            PARENB = 0x0000400;
            PARODD = 0x0001000;
            HUPCL = 0x0002000;
            CLOCAL = 0x0004000;

            ISIG = 0x0000001;
            ICANON = 0x0000002;
            XCASE = 0x0000004;
            ECHO = 0x0000010;
            ECHOE = 0x0000020;
            ECHOK = 0x0000040;
            ECHONL = 0x0000100;
            NOFLSH = 0x0000200;
            TOSTOP = 0x0000400;
            ECHOCTL = 0x0001000;
            ECHOPRT = 0x0002000;
            ECHOKE = 0x0004000;
            FLUSHO = 0x0010000;
            PENDIN = 0x0040000;
            IEXTEN = 0x0100000;
            EXTPROC = 0x0200000;
        } else if (osName.startsWith("Mac") || osName.startsWith("Darwin")) {
            TIOCGWINSZ = 0x40087468;
            TIOCSWINSZ = 0x80087467;

            TCSANOW = 0x00000000;

            VEOF = 0;
            VEOL = 1;
            VEOL2 = 2;
            VERASE = 3;
            VWERASE = 4;
            VKILL = 5;
            VREPRINT = 6;
            VINTR = 8;
            VQUIT = 9;
            VSUSP = 10;
            VDSUSP = 11;
            VSTART = 12;
            VSTOP = 13;
            VLNEXT = 14;
            VDISCARD = 15;
            VMIN = 16;
            VTIME = 17;
            VSTATUS = 18;
            VERASE2 = -1;
            VSWTC = -1;

            IGNBRK = 0x00000001;
            BRKINT = 0x00000002;
            IGNPAR = 0x00000004;
            PARMRK = 0x00000008;
            INPCK = 0x00000010;
            ISTRIP = 0x00000020;
            INLCR = 0x00000040;
            IGNCR = 0x00000080;
            ICRNL = 0x00000100;
            IXON = 0x00000200;
            IXOFF = 0x00000400;
            IXANY = 0x00000800;
            IMAXBEL = 0x00002000;
            IUTF8 = 0x00004000;

            OPOST = 0x00000001;
            ONLCR = 0x00000002;
            OXTABS = 0x00000004;
            ONOEOT = 0x00000008;
            OCRNL = 0x00000010;
            ONOCR = 0x00000020;
            ONLRET = 0x00000040;
            OFILL = 0x00000080;
            NLDLY = 0x00000300;
            TABDLY = 0x00000c04;
            CRDLY = 0x00003000;
            FFDLY = 0x00004000;
            BSDLY = 0x00008000;
            VTDLY = 0x00010000;
            OFDEL = 0x00020000;

            CIGNORE = 0x00000001;
            CS5 = 0x00000000;
            CS6 = 0x00000100;
            CS7 = 0x00000200;
            CS8 = 0x00000300;
            CSTOPB = 0x00000400;
            CREAD = 0x00000800;
            PARENB = 0x00001000;
            PARODD = 0x00002000;
            HUPCL = 0x00004000;
            CLOCAL = 0x00008000;
            CCTS_OFLOW = 0x00010000;
            CRTS_IFLOW = 0x00020000;
            CDTR_IFLOW = 0x00040000;
            CDSR_OFLOW = 0x00080000;
            CCAR_OFLOW = 0x00100000;

            ECHOKE = 0x00000001;
            ECHOE = 0x00000002;
            ECHOK = 0x00000004;
            ECHO = 0x00000008;
            ECHONL = 0x00000010;
            ECHOPRT = 0x00000020;
            ECHOCTL = 0x00000040;
            ISIG = 0x00000080;
            ICANON = 0x00000100;
            ALTWERASE = 0x00000200;
            IEXTEN = 0x00000400;
            EXTPROC = 0x00000800;
            TOSTOP = 0x00400000;
            FLUSHO = 0x00800000;
            NOKERNINFO = 0x02000000;
            PENDIN = 0x20000000;
            NOFLSH = 0x80000000;
        } else if (osName.startsWith("FreeBSD")) {
            TIOCGWINSZ = 0x40087468;
            TIOCSWINSZ = 0x80087467;

            TCSANOW = 0x0;
            TCSADRAIN = 0x1;
            TCSAFLUSH = 0x2;

            VEOF = 0;
            VEOL = 1;
            VEOL2 = 2;
            VERASE = 3;
            VWERASE = 4;
            VKILL = 5;
            VREPRINT = 6;
            VERASE2 = 7;
            VINTR = 8;
            VQUIT = 9;
            VSUSP = 10;
            VDSUSP = 11;
            VSTART = 12;
            VSTOP = 13;
            VLNEXT = 14;
            VDISCARD = 15;
            VMIN = 16;
            VTIME = 17;
            VSTATUS = 18;
            VSWTC = -1;

            IGNBRK = 0x0000001;
            BRKINT = 0x0000002;
            IGNPAR = 0x0000004;
            PARMRK = 0x0000008;
            INPCK = 0x0000010;
            ISTRIP = 0x0000020;
            INLCR = 0x0000040;
            IGNCR = 0x0000080;
            ICRNL = 0x0000100;
            IXON = 0x0000200;
            IXOFF = 0x0000400;
            IXANY = 0x0000800;
            IMAXBEL = 0x0002000;

            OPOST = 0x0000001;
            ONLCR = 0x0000002;
            TABDLY = 0x0000004;
            TAB0 = 0x0000000;
            TAB3 = 0x0000004;
            ONOEOT = 0x0000008;
            OCRNL = 0x0000010;
            ONLRET = 0x0000040;

            CIGNORE = 0x0000001;
            CSIZE = 0x0000300;
            CS5 = 0x0000000;
            CS6 = 0x0000100;
            CS7 = 0x0000200;
            CS8 = 0x0000300;
            CSTOPB = 0x0000400;
            CREAD = 0x0000800;
            PARENB = 0x0001000;
            PARODD = 0x0002000;
            HUPCL = 0x0004000;
            CLOCAL = 0x0008000;

            ECHOKE = 0x0000001;
            ECHOE = 0x0000002;
            ECHOK = 0x0000004;
            ECHO = 0x0000008;
            ECHONL = 0x0000010;
            ECHOPRT = 0x0000020;
            ECHOCTL = 0x0000040;
            ISIG = 0x0000080;
            ICANON = 0x0000100;
            ALTWERASE = 0x000200;
            IEXTEN = 0x0000400;
            EXTPROC = 0x0000800;
            TOSTOP = 0x0400000;
            FLUSHO = 0x0800000;
            PENDIN = 0x2000000;
            NOFLSH = 0x8000000;
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
