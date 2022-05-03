package jdk.internal.platform.cgroup;

import java.util.ArrayList;
import java.util.List;
import jdk.internal.platform.Metrics;
import jdk.test.lib.containers.cgroup.CgroupV1TestUtils;
import jdk.test.lib.process.OutputAnalyzer;

/*
 * @test
 * @key cgroups
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.platform
 * @library /test/lib
 * @run main TestCgroupV1Memory
 */
public class TestCgroupV1Memory {

    private static final String SUB_SYSTEM_PRE = "memory:";
    private static final String SUB_SYSTEM_NAME = "memorytest";

    public static void main(String[] args) throws Exception {
        // If cgroups is not configured, report success.
        Metrics metrics = Metrics.systemMetrics();
        if (metrics == null) {
            System.out.println("TEST PASSED!!!");
            return;
        }
        if("cgroupv1".equals(metrics.getProvider())){
            CgroupV1TestUtils.createSubSystem(SUB_SYSTEM_PRE + SUB_SYSTEM_NAME);

            try {
                CgroupV1TestUtils.initSubSystem(SUB_SYSTEM_NAME,"memory.swappiness=0");
                CgroupV1TestUtils.initSubSystem(SUB_SYSTEM_NAME,"memory.memsw.limit_in_bytes=104857600");
                CgroupV1TestUtils.initSubSystem(SUB_SYSTEM_NAME,"memory.max_usage_in_bytes=52428800");

                List<String> subSystems = new ArrayList<>();
                subSystems.add(SUB_SYSTEM_PRE + SUB_SYSTEM_NAME);
                OutputAnalyzer outputAnalyzer = CgroupV1TestUtils
                        .runJavaApp(subSystems, "-XshowSettings:system  -Xlog:os+container=trace -version");

                System.out.println(outputAnalyzer.getOutput());

            }finally {
                CgroupV1TestUtils.deleteSubSystem(SUB_SYSTEM_PRE + SUB_SYSTEM_NAME);
            }
        }
        System.out.println("TEST PASSED!!!");
    }

}
