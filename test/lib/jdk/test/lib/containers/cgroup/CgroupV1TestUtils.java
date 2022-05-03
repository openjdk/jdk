package jdk.test.lib.containers.cgroup;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;


public class CgroupV1TestUtils {


    // Specifies how many lines to copy from child STDOUT to main test output.
    // Having too many lines in the main test output will result
    // in JT harness trimming the output, and can lead to loss of useful
    // diagnostic information.
    private static final int MAX_LINES_TO_COPY_FOR_CHILD_STDOUT = 100;

    // Path to a JDK under test.
    // This may be useful when developing tests on non-Linux platforms.
    public static final String JDK_UNDER_TEST =
            System.getProperty("jdk.test.cgroupv1.jdk", Utils.TEST_JDK);

    /**
     * Execute a specified command in a process, report diagnostic info.
     *
     * @param command to be executed
     * @return The output from the process
     * @throws Exception
     */
    public static OutputAnalyzer execute(String... command) throws Exception {
        return CommandUtils.execute("cgroupv1-stdout-%d.log", MAX_LINES_TO_COPY_FOR_CHILD_STDOUT, command);
    }


    public static void createSubSystem(String subSystemName) throws Exception {
        execute("cgcreate", "-g", subSystemName)
                .shouldHaveExitValue(0);
    }


    public static void initSubSystem(String subSystemName,String info) throws Exception {
        execute("cgset", "-r", info, subSystemName)
                .shouldHaveExitValue(0);
    }

    public static void deleteSubSystem(String subSystemName) throws Exception {
        execute("cgdelete", subSystemName)
                .shouldHaveExitValue(0);
    }

    public static OutputAnalyzer runJavaApp(List<String> subSystemList,String command) throws Exception{
        Path jdkSrcDir = Paths.get(JDK_UNDER_TEST);
        List<String> cmd = new ArrayList<>();
        cmd.add("cgexec");
        for (String subSystemName : subSystemList) {
            cmd.add("-g");
            cmd.add(subSystemName);
        }
        cmd.add(jdkSrcDir.toString());
        cmd.add(command);

        return execute(cmd.toArray(new String[0]));
    }
}
