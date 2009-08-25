import java.io.*;
import java.util.*;

import sun.misc.*;

/*
 * @test /nodynamiccopyright/
 * @bug 6873845
 * @summary refine access to symbol file
 */

public class T6873845 {
    public static void main(String... args) throws Exception {
        new T6873845().run();
    }

    public void run() throws Exception {
        String out = compile(Arrays.asList("-XDrawDiagnostics", "-X"));
        if (out.contains("sunapi"))
            throw new Exception("unexpected output for -X");

        String warn1 = "T6873845.java:72:9: compiler.warn.sun.proprietary: sun.misc.Unsafe" + newline;
        String warn2 = "T6873845.java:77:9: compiler.warn.sun.proprietary: sun.misc.Unsafe" + newline;
        String note1 = "- compiler.note.sunapi.filename: T6873845.java" + newline;
        String note2 = "- compiler.note.sunapi.recompile" + newline;

        test(opts(),
                warn1 + warn2 + "2 warnings" + newline);
        test(opts("-XDenableSunApiLintControl"),
                note1 + note2);
        test(opts("-XDenableSunApiLintControl", "-XDsuppressNotes"),
                "");
        test(opts("-XDenableSunApiLintControl", "-Xlint:sunapi"),
                warn1 + "1 warning" + newline);
        test(opts("-XDenableSunApiLintControl", "-Xlint:all"),
                warn1 + "1 warning" + newline);
        test(opts("-XDenableSunApiLintControl", "-Xlint:all,-sunapi"),
                note1 + note2);
    }

    List<String> opts(String... opts) {
        return Arrays.asList(opts);
    }

    void test(List<String> opts, String expect) throws Exception {
        List<String> args = new ArrayList<String>();
        args.addAll(opts);
        args.add("-d");
        args.add(testClasses.getPath());
        args.add(new File(testSrc, "T6873845.java").getPath());
        compile(args); // to verify resource strings exist
        args.add(0, "-XDrawDiagnostics");
        String out = compile(args);
        if (!out.equals(expect))
            throw new Exception("unexpected output from compiler");
    }

    String compile(List<String> args) throws Exception{
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        System.out.println("compile: " + args);
        int rc = com.sun.tools.javac.Main.compile(args.toArray(new String[args.size()]), pw);
        pw.close();
        String out = sw.toString();
        System.out.println(out);
        if (rc != 0)
            throw new Exception("compilation failed unexpectedly");
        return out;
    }

    void m1() {
        Unsafe.getUnsafe();
    }

    @SuppressWarnings("sunapi")
    void m2() {
        Unsafe.getUnsafe();
    }

    private File testSrc = new File(System.getProperty("test.src", "."));
    private File testClasses = new File(System.getProperty("test.classes", "."));
    private String newline = System.getProperty("line.separator");
}

