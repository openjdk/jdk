/*
 * Copyright (c) 2002-2020, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl.jna.osx;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

//import com.sun.jna.LastErrorException;
//import com.sun.jna.NativeLong;
//import com.sun.jna.Structure;
import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Attributes.ControlChar;
import jdk.internal.org.jline.terminal.Attributes.ControlFlag;
import jdk.internal.org.jline.terminal.Attributes.InputFlag;
import jdk.internal.org.jline.terminal.Attributes.LocalFlag;
import jdk.internal.org.jline.terminal.Attributes.OutputFlag;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.impl.jna.LastErrorException;

public interface CLibrary {//extends com.sun.jna.Library {

    void tcgetattr(int fd, termios termios) throws LastErrorException;

    void tcsetattr(int fd, int cmd, termios termios) throws LastErrorException;

    void ioctl(int fd, NativeLong cmd, winsize data) throws LastErrorException;

    int isatty(int fd);

    void ttyname_r(int fd, byte[] buf, int len) throws LastErrorException;

    void openpty(int[] master, int[] slave, byte[] name, termios t, winsize s) throws LastErrorException;

    class winsize { //extends Structure {
        public short ws_row;
        public short ws_col;
        public short ws_xpixel;
        public short ws_ypixel;

        public winsize() {
        }

        public winsize(Size ws) {
            ws_row = (short) ws.getRows();
            ws_col = (short) ws.getColumns();
        }

        public Size toSize() {
            return new Size(ws_col, ws_row);
        }

//        @Override
//        protected List<String> getFieldOrder() {
//            return Arrays.asList(//
//                    "ws_row",//
//                    "ws_col",//
//                    "ws_xpixel",//
//                    "ws_ypixel"//
//            );
//        }

    }

    class termios { //extends Structure {

        public NativeLong c_iflag;
        public NativeLong c_oflag;
        public NativeLong c_cflag;
        public NativeLong c_lflag;
        public byte[] c_cc = new byte[20];
        public NativeLong c_ispeed;
        public NativeLong c_ospeed;

//        @Override
//        protected List<String> getFieldOrder() {
//            return Arrays.asList(//
//                    "c_iflag",//
//                    "c_oflag",//
//                    "c_cflag",//
//                    "c_lflag",//
//                    "c_cc",//
//                    "c_ispeed",//
//                    "c_ospeed"//
//            );
//        }

        {
            c_iflag  = new NativeLong(0);
            c_oflag  = new NativeLong(0);
            c_cflag  = new NativeLong(0);
            c_lflag  = new NativeLong(0);
            c_ispeed = new NativeLong(0);
            c_ospeed = new NativeLong(0);
        }

        public termios() {
        }

        public termios(Attributes t) {
            // Input flags
            setFlag(t.getInputFlag(InputFlag.IGNBRK),           IGNBRK,     c_iflag);
            setFlag(t.getInputFlag(InputFlag.BRKINT),           BRKINT,     c_iflag);
            setFlag(t.getInputFlag(InputFlag.IGNPAR),           IGNPAR,     c_iflag);
            setFlag(t.getInputFlag(InputFlag.PARMRK),           PARMRK,     c_iflag);
            setFlag(t.getInputFlag(InputFlag.INPCK),            INPCK,      c_iflag);
            setFlag(t.getInputFlag(InputFlag.ISTRIP),           ISTRIP,     c_iflag);
            setFlag(t.getInputFlag(InputFlag.INLCR),            INLCR,      c_iflag);
            setFlag(t.getInputFlag(InputFlag.IGNCR),            IGNCR,      c_iflag);
            setFlag(t.getInputFlag(InputFlag.ICRNL),            ICRNL,      c_iflag);
            setFlag(t.getInputFlag(InputFlag.IXON),             IXON,       c_iflag);
            setFlag(t.getInputFlag(InputFlag.IXOFF),            IXOFF,      c_iflag);
            setFlag(t.getInputFlag(InputFlag.IXANY),            IXANY,      c_iflag);
            setFlag(t.getInputFlag(InputFlag.IMAXBEL),          IMAXBEL,    c_iflag);
            setFlag(t.getInputFlag(InputFlag.IUTF8),            IUTF8,      c_iflag);
            // Output flags
            setFlag(t.getOutputFlag(OutputFlag.OPOST),          OPOST,      c_oflag);
            setFlag(t.getOutputFlag(OutputFlag.ONLCR),          ONLCR,      c_oflag);
            setFlag(t.getOutputFlag(OutputFlag.OXTABS),         OXTABS,     c_oflag);
            setFlag(t.getOutputFlag(OutputFlag.ONOEOT),         ONOEOT,     c_oflag);
            setFlag(t.getOutputFlag(OutputFlag.OCRNL),          OCRNL,      c_oflag);
            setFlag(t.getOutputFlag(OutputFlag.ONOCR),          ONOCR,      c_oflag);
            setFlag(t.getOutputFlag(OutputFlag.ONLRET),         ONLRET,     c_oflag);
            setFlag(t.getOutputFlag(OutputFlag.OFILL),          OFILL,      c_oflag);
            setFlag(t.getOutputFlag(OutputFlag.NLDLY),          NLDLY,      c_oflag);
            setFlag(t.getOutputFlag(OutputFlag.TABDLY),         TABDLY,     c_oflag);
            setFlag(t.getOutputFlag(OutputFlag.CRDLY),          CRDLY,      c_oflag);
            setFlag(t.getOutputFlag(OutputFlag.FFDLY),          FFDLY,      c_oflag);
            setFlag(t.getOutputFlag(OutputFlag.BSDLY),          BSDLY,      c_oflag);
            setFlag(t.getOutputFlag(OutputFlag.VTDLY),          VTDLY,      c_oflag);
            setFlag(t.getOutputFlag(OutputFlag.OFDEL),          OFDEL,      c_oflag);
            // Control flags
            setFlag(t.getControlFlag(ControlFlag.CIGNORE),      CIGNORE,    c_cflag);
            setFlag(t.getControlFlag(ControlFlag.CS5),          CS5,        c_cflag);
            setFlag(t.getControlFlag(ControlFlag.CS6),          CS6,        c_cflag);
            setFlag(t.getControlFlag(ControlFlag.CS7),          CS7,        c_cflag);
            setFlag(t.getControlFlag(ControlFlag.CS8),          CS8,        c_cflag);
            setFlag(t.getControlFlag(ControlFlag.CSTOPB),       CSTOPB,     c_cflag);
            setFlag(t.getControlFlag(ControlFlag.CREAD),        CREAD,      c_cflag);
            setFlag(t.getControlFlag(ControlFlag.PARENB),       PARENB,     c_cflag);
            setFlag(t.getControlFlag(ControlFlag.PARODD),       PARODD,     c_cflag);
            setFlag(t.getControlFlag(ControlFlag.HUPCL),        HUPCL,      c_cflag);
            setFlag(t.getControlFlag(ControlFlag.CLOCAL),       CLOCAL,     c_cflag);
            setFlag(t.getControlFlag(ControlFlag.CCTS_OFLOW),   CCTS_OFLOW, c_cflag);
            setFlag(t.getControlFlag(ControlFlag.CRTS_IFLOW),   CRTS_IFLOW, c_cflag);
            setFlag(t.getControlFlag(ControlFlag.CDTR_IFLOW),   CDTR_IFLOW, c_cflag);
            setFlag(t.getControlFlag(ControlFlag.CDSR_OFLOW),   CDSR_OFLOW, c_cflag);
            setFlag(t.getControlFlag(ControlFlag.CCAR_OFLOW),   CCAR_OFLOW, c_cflag);
            // Local flags
            setFlag(t.getLocalFlag(LocalFlag.ECHOKE),           ECHOKE,     c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.ECHOE),            ECHOE,      c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.ECHOK),            ECHOK,      c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.ECHO),             ECHO,       c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.ECHONL),           ECHONL,     c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.ECHOPRT),          ECHOPRT,    c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.ECHOCTL),          ECHOCTL,    c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.ISIG),             ISIG,       c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.ICANON),           ICANON,     c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.ALTWERASE),        ALTWERASE,  c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.IEXTEN),           IEXTEN,     c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.EXTPROC),          EXTPROC,    c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.TOSTOP),           TOSTOP,     c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.FLUSHO),           FLUSHO,     c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.NOKERNINFO),       NOKERNINFO, c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.PENDIN),           PENDIN,     c_lflag);
            setFlag(t.getLocalFlag(LocalFlag.NOFLSH),           NOFLSH,     c_lflag);
            // Control chars
            c_cc[VEOF]      = (byte) t.getControlChar(ControlChar.VEOF);
            c_cc[VEOL]      = (byte) t.getControlChar(ControlChar.VEOL);
            c_cc[VEOL2]     = (byte) t.getControlChar(ControlChar.VEOL2);
            c_cc[VERASE]    = (byte) t.getControlChar(ControlChar.VERASE);
            c_cc[VWERASE]   = (byte) t.getControlChar(ControlChar.VWERASE);
            c_cc[VKILL]     = (byte) t.getControlChar(ControlChar.VKILL);
            c_cc[VREPRINT]  = (byte) t.getControlChar(ControlChar.VREPRINT);
            c_cc[VINTR]     = (byte) t.getControlChar(ControlChar.VINTR);
            c_cc[VQUIT]     = (byte) t.getControlChar(ControlChar.VQUIT);
            c_cc[VSUSP]     = (byte) t.getControlChar(ControlChar.VSUSP);
            c_cc[VDSUSP]    = (byte) t.getControlChar(ControlChar.VDSUSP);
            c_cc[VSTART]    = (byte) t.getControlChar(ControlChar.VSTART);
            c_cc[VSTOP]     = (byte) t.getControlChar(ControlChar.VSTOP);
            c_cc[VLNEXT]    = (byte) t.getControlChar(ControlChar.VLNEXT);
            c_cc[VDISCARD]  = (byte) t.getControlChar(ControlChar.VDISCARD);
            c_cc[VMIN]      = (byte) t.getControlChar(ControlChar.VMIN);
            c_cc[VTIME]     = (byte) t.getControlChar(ControlChar.VTIME);
            c_cc[VSTATUS]   = (byte) t.getControlChar(ControlChar.VSTATUS);
        }

        private void setFlag(boolean flag, long value, NativeLong org) {
            org.setValue(flag ? org.longValue() | value : org.longValue());
        }

        public Attributes toAttributes() {
            Attributes attr = new Attributes();
            // Input flags
            EnumSet<InputFlag> iflag = attr.getInputFlags();
            addFlag(c_iflag.longValue(), iflag, InputFlag.IGNBRK,   IGNBRK);
            addFlag(c_iflag.longValue(), iflag, InputFlag.IGNBRK, IGNBRK);
            addFlag(c_iflag.longValue(), iflag, InputFlag.BRKINT, BRKINT);
            addFlag(c_iflag.longValue(), iflag, InputFlag.IGNPAR, IGNPAR);
            addFlag(c_iflag.longValue(), iflag, InputFlag.PARMRK, PARMRK);
            addFlag(c_iflag.longValue(), iflag, InputFlag.INPCK, INPCK);
            addFlag(c_iflag.longValue(), iflag, InputFlag.ISTRIP, ISTRIP);
            addFlag(c_iflag.longValue(), iflag, InputFlag.INLCR, INLCR);
            addFlag(c_iflag.longValue(), iflag, InputFlag.IGNCR, IGNCR);
            addFlag(c_iflag.longValue(), iflag, InputFlag.ICRNL, ICRNL);
            addFlag(c_iflag.longValue(), iflag, InputFlag.IXON, IXON);
            addFlag(c_iflag.longValue(), iflag, InputFlag.IXOFF, IXOFF);
            addFlag(c_iflag.longValue(), iflag, InputFlag.IXANY, IXANY);
            addFlag(c_iflag.longValue(), iflag, InputFlag.IMAXBEL, IMAXBEL);
            addFlag(c_iflag.longValue(), iflag, InputFlag.IUTF8, IUTF8);
            // Output flags
            EnumSet<OutputFlag> oflag = attr.getOutputFlags();
            addFlag(c_oflag.longValue(), oflag, OutputFlag.OPOST, OPOST);
            addFlag(c_oflag.longValue(), oflag, OutputFlag.ONLCR, ONLCR);
            addFlag(c_oflag.longValue(), oflag, OutputFlag.OXTABS, OXTABS);
            addFlag(c_oflag.longValue(), oflag, OutputFlag.ONOEOT, ONOEOT);
            addFlag(c_oflag.longValue(), oflag, OutputFlag.OCRNL, OCRNL);
            addFlag(c_oflag.longValue(), oflag, OutputFlag.ONOCR, ONOCR);
            addFlag(c_oflag.longValue(), oflag, OutputFlag.ONLRET, ONLRET);
            addFlag(c_oflag.longValue(), oflag, OutputFlag.OFILL, OFILL);
            addFlag(c_oflag.longValue(), oflag, OutputFlag.NLDLY, NLDLY);
            addFlag(c_oflag.longValue(), oflag, OutputFlag.TABDLY, TABDLY);
            addFlag(c_oflag.longValue(), oflag, OutputFlag.CRDLY, CRDLY);
            addFlag(c_oflag.longValue(), oflag, OutputFlag.FFDLY, FFDLY);
            addFlag(c_oflag.longValue(), oflag, OutputFlag.BSDLY, BSDLY);
            addFlag(c_oflag.longValue(), oflag, OutputFlag.VTDLY, VTDLY);
            addFlag(c_oflag.longValue(), oflag, OutputFlag.OFDEL, OFDEL);
            // Control flags
            EnumSet<ControlFlag> cflag = attr.getControlFlags();
            addFlag(c_cflag.longValue(), cflag, ControlFlag.CIGNORE, CIGNORE);
            addFlag(c_cflag.longValue(), cflag, ControlFlag.CS5, CS5);
            addFlag(c_cflag.longValue(), cflag, ControlFlag.CS6, CS6);
            addFlag(c_cflag.longValue(), cflag, ControlFlag.CS7, CS7);
            addFlag(c_cflag.longValue(), cflag, ControlFlag.CS8, CS8);
            addFlag(c_cflag.longValue(), cflag, ControlFlag.CSTOPB, CSTOPB);
            addFlag(c_cflag.longValue(), cflag, ControlFlag.CREAD, CREAD);
            addFlag(c_cflag.longValue(), cflag, ControlFlag.PARENB, PARENB);
            addFlag(c_cflag.longValue(), cflag, ControlFlag.PARODD, PARODD);
            addFlag(c_cflag.longValue(), cflag, ControlFlag.HUPCL, HUPCL);
            addFlag(c_cflag.longValue(), cflag, ControlFlag.CLOCAL, CLOCAL);
            addFlag(c_cflag.longValue(), cflag, ControlFlag.CCTS_OFLOW, CCTS_OFLOW);
            addFlag(c_cflag.longValue(), cflag, ControlFlag.CRTS_IFLOW, CRTS_IFLOW);
            addFlag(c_cflag.longValue(), cflag, ControlFlag.CDSR_OFLOW, CDSR_OFLOW);
            addFlag(c_cflag.longValue(), cflag, ControlFlag.CCAR_OFLOW, CCAR_OFLOW);
            // Local flags
            EnumSet<LocalFlag> lflag = attr.getLocalFlags();
            addFlag(c_lflag.longValue(), lflag, LocalFlag.ECHOKE, ECHOKE);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.ECHOE, ECHOE);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.ECHOK, ECHOK);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.ECHO, ECHO);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.ECHONL, ECHONL);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.ECHOPRT, ECHOPRT);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.ECHOCTL, ECHOCTL);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.ISIG, ISIG);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.ICANON, ICANON);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.ALTWERASE, ALTWERASE);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.IEXTEN, IEXTEN);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.EXTPROC, EXTPROC);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.TOSTOP, TOSTOP);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.FLUSHO, FLUSHO);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.NOKERNINFO, NOKERNINFO);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.PENDIN, PENDIN);
            addFlag(c_lflag.longValue(), lflag, LocalFlag.NOFLSH, NOFLSH);
            // Control chars
            EnumMap<ControlChar, Integer> cc = attr.getControlChars();
            cc.put(ControlChar.VEOF,        (int) c_cc[VEOF]);
            cc.put(ControlChar.VEOL,        (int) c_cc[VEOL]);
            cc.put(ControlChar.VEOL2,       (int) c_cc[VEOL2]);
            cc.put(ControlChar.VERASE,      (int) c_cc[VERASE]);
            cc.put(ControlChar.VWERASE,     (int) c_cc[VWERASE]);
            cc.put(ControlChar.VKILL,       (int) c_cc[VKILL]);
            cc.put(ControlChar.VREPRINT,    (int) c_cc[VREPRINT]);
            cc.put(ControlChar.VINTR,       (int) c_cc[VINTR]);
            cc.put(ControlChar.VQUIT,       (int) c_cc[VQUIT]);
            cc.put(ControlChar.VSUSP,       (int) c_cc[VSUSP]);
            cc.put(ControlChar.VDSUSP,      (int) c_cc[VDSUSP]);
            cc.put(ControlChar.VSTART,      (int) c_cc[VSTART]);
            cc.put(ControlChar.VSTOP,       (int) c_cc[VSTOP]);
            cc.put(ControlChar.VLNEXT,      (int) c_cc[VLNEXT]);
            cc.put(ControlChar.VDISCARD,    (int) c_cc[VDISCARD]);
            cc.put(ControlChar.VMIN,        (int) c_cc[VMIN]);
            cc.put(ControlChar.VTIME,       (int) c_cc[VTIME]);
            cc.put(ControlChar.VSTATUS,     (int) c_cc[VSTATUS]);
            // Return
            return attr;
        }

        private <T extends Enum<T>> void addFlag(long value, EnumSet<T> flags, T flag, int v) {
            if ((value & v) != 0) {
                flags.add(flag);
            }
        }
    }

    // CONSTANTS

     long TIOCGWINSZ = 0x40087468L;
     long TIOCSWINSZ = 0x80087467L;

     int TCSANOW     = 0x00000000;

     int VEOF        = 0;
     int VEOL        = 1;
     int VEOL2       = 2;
     int VERASE      = 3;
     int VWERASE     = 4;
     int VKILL       = 5;
     int VREPRINT    = 6;
     int VINTR       = 8;
     int VQUIT       = 9;
     int VSUSP       = 10;
     int VDSUSP      = 11;
     int VSTART      = 12;
     int VSTOP       = 13;
     int VLNEXT      = 14;
     int VDISCARD    = 15;
     int VMIN        = 16;
     int VTIME       = 17;
     int VSTATUS     = 18;

     int IGNBRK      = 0x00000001;
     int BRKINT      = 0x00000002;
     int IGNPAR      = 0x00000004;
     int PARMRK      = 0x00000008;
     int INPCK       = 0x00000010;
     int ISTRIP      = 0x00000020;
     int INLCR       = 0x00000040;
     int IGNCR       = 0x00000080;
     int ICRNL       = 0x00000100;
     int IXON        = 0x00000200;
     int IXOFF       = 0x00000400;
     int IXANY       = 0x00000800;
     int IMAXBEL     = 0x00002000;
     int IUTF8       = 0x00004000;

     int OPOST       = 0x00000001;
     int ONLCR       = 0x00000002;
     int OXTABS      = 0x00000004;
     int ONOEOT      = 0x00000008;
     int OCRNL       = 0x00000010;
     int ONOCR       = 0x00000020;
     int ONLRET      = 0x00000040;
     int OFILL       = 0x00000080;
     int NLDLY       = 0x00000300;
     int TABDLY      = 0x00000c04;
     int CRDLY       = 0x00003000;
     int FFDLY       = 0x00004000;
     int BSDLY       = 0x00008000;
     int VTDLY       = 0x00010000;
     int OFDEL       = 0x00020000;

     int CIGNORE     = 0x00000001;
     int CS5         = 0x00000000;
     int CS6         = 0x00000100;
     int CS7         = 0x00000200;
     int CS8         = 0x00000300;
     int CSTOPB      = 0x00000400;
     int CREAD       = 0x00000800;
     int PARENB      = 0x00001000;
     int PARODD      = 0x00002000;
     int HUPCL       = 0x00004000;
     int CLOCAL      = 0x00008000;
     int CCTS_OFLOW  = 0x00010000;
     int CRTS_IFLOW  = 0x00020000;
     int CDTR_IFLOW  = 0x00040000;
     int CDSR_OFLOW  = 0x00080000;
     int CCAR_OFLOW  = 0x00100000;

     int ECHOKE      = 0x00000001;
     int ECHOE       = 0x00000002;
     int ECHOK       = 0x00000004;
     int ECHO        = 0x00000008;
     int ECHONL      = 0x00000010;
     int ECHOPRT     = 0x00000020;
     int ECHOCTL     = 0x00000040;
     int ISIG        = 0x00000080;
     int ICANON      = 0x00000100;
     int ALTWERASE   = 0x00000200;
     int IEXTEN      = 0x00000400;
     int EXTPROC     = 0x00000800;
     int TOSTOP      = 0x00400000;
     int FLUSHO      = 0x00800000;
     int NOKERNINFO  = 0x02000000;
     int PENDIN      = 0x20000000;
     int NOFLSH      = 0x80000000;

}
