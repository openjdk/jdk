import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import com.oracle.java.testlibrary.OutputAnalyzer;
import com.oracle.java.testlibrary.Platform;
import com.oracle.java.testlibrary.ProcessTools;

/*
 * @test
 * @bug 8065896
 * @summary Synchronous signals during error reporting may terminate or hang VM process
 * @library /testlibrary
 * @author Thomas Stuefe (SAP)
 */

public class SecondaryErrorTest {


  public static void main(String[] args) throws Exception {

    // Do not execute for windows, nor for non-debug builds
    if (Platform.isWindows()) {
      return;
    }

    if (!Platform.isDebugBuild()) {
      return;
    }

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
        "-XX:+UnlockDiagnosticVMOptions",
        "-Xmx100M",
        "-XX:ErrorHandlerTest=15",
        "-XX:TestCrashInErrorHandler=14",
        "-version");

    OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());

    // we should have crashed with a SIGFPE
    output_detail.shouldMatch("# A fatal error has been detected by the Java Runtime Environment:.*");
    output_detail.shouldMatch("# +SIGFPE.*");

    // extract hs-err file
    String hs_err_file = output_detail.firstMatch("# *(\\S*hs_err_pid\\d+\\.log)", 1);
    if (hs_err_file == null) {
      throw new RuntimeException("Did not find hs-err file in output.\n");
    }

    // scan hs-err file: File should contain the "[error occurred during error reporting..]"
    // markers which show that the secondary error handling kicked in and handled the
    // error successfully. As an added test, we check that the last line contains "END.",
    // which is an end marker written in the last step and proves that hs-err file was
    // completely written.
    File f = new File(hs_err_file);
    if (!f.exists()) {
      throw new RuntimeException("hs-err file missing at "
          + f.getAbsolutePath() + ".\n");
    }

    System.out.println("Found hs_err file. Scanning...");

    FileInputStream fis = new FileInputStream(f);
    BufferedReader br = new BufferedReader(new InputStreamReader(fis));
    String line = null;

    Pattern [] pattern = new Pattern[] {
        Pattern.compile("Will crash now \\(TestCrashInErrorHandler=14\\)..."),
        Pattern.compile("\\[error occurred during error reporting \\(test secondary crash 1\\).*\\]"),
        Pattern.compile("Will crash now \\(TestCrashInErrorHandler=14\\)..."),
        Pattern.compile("\\[error occurred during error reporting \\(test secondary crash 2\\).*\\]"),
    };
    int currentPattern = 0;

    String lastLine = null;
    while ((line = br.readLine()) != null) {
      if (currentPattern < pattern.length) {
        if (pattern[currentPattern].matcher(line).matches()) {
          System.out.println("Found: " + line + ".");
          currentPattern ++;
        }
      }
      lastLine = line;
    }
    br.close();

    if (currentPattern < pattern.length) {
      throw new RuntimeException("hs-err file incomplete (first missing pattern: " +  currentPattern + ")");
    }

    if (!lastLine.equals("END.")) {
      throw new RuntimeException("hs-err file incomplete (missing END marker.)");
    } else {
      System.out.println("End marker found.");
    }

    System.out.println("OK.");

  }

}


