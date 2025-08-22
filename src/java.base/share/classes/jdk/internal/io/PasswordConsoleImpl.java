package jdk.internal.io;

import jdk.internal.access.SharedSecrets;
import jdk.internal.util.StaticProperty;
import sun.nio.cs.UTF_8;

import java.nio.charset.Charset;
import java.util.Locale;

public class PasswordConsoleImpl extends JdkConsoleImpl {
    public PasswordConsoleImpl(Charset inCharset, Charset outCharset) {
        super(inCharset, outCharset);
    }

    public static Object console() {
        var sysc = System.console();
        var istty = SharedSecrets.getJavaIOAccess().istty();

        if (sysc != null) {
            return sysc;
        } else if ((istty & 0x00000002) != 0){
            return new PasswordConsoleImpl(
                Charset.forName(StaticProperty.stdinEncoding(), UTF_8.INSTANCE),
                Charset.forName(StaticProperty.stdoutEncoding(), UTF_8.INSTANCE));
        } else {
            return null;
        }
    }

    public char[] readPasswordNoNewLine() {
        return readPassword0(true, Locale.getDefault(Locale.Category.FORMAT), "");
    }
}
