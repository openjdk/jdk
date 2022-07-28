import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.TimeUnit;
import java.util.List;
import com.sun.management.MemoryMXBean;

/*
 * @test
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseConcLivenessEstimate -XX:ConcLivenessEstimateSeconds=7 -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=0 -XX:+ExplicitGCInvokesConcurrent -XX:ConcLivenessThreads=5 TestConcurrentLivenessWhenGCAlwaysRunsMultipleWorkers
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseConcLivenessEstimate -XX:ConcLivenessEstimateSeconds=7 -XX:+UseShenandoahGC -XX:+ExplicitGCInvokesConcurrent -XX:ConcLivenessThreads=5 TestConcurrentLivenessWhenGCAlwaysRunsMultipleWorkers
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseConcLivenessEstimate -XX:ConcLivenessEstimateSeconds=7 -XX:+UseZGC -XX:+ExplicitGCInvokesConcurrent -XX:ConcLivenessThreads=5 TestConcurrentLivenessWhenGCAlwaysRunsMultipleWorkers
 */
public class TestConcurrentLivenessWhenGCAlwaysRunsMultipleWorkers {

    public static void main(String[] args) throws Exception {

        Runnable runnable = () -> {
            while (true) {
                System.gc(); 
            }
        };

        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.start();

        long start = System.currentTimeMillis();
        while(System.currentTimeMillis() < start + 25000) {
            MemoryMXBean memBean = (MemoryMXBean) ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memBean.getLiveHeapUsage();

            if (heap.getUsed() != 0) {
                throw new RuntimeException("Concurrent liveness ran during concurrent GC. Estimate should be zero but is " + heap.getUsed());
            }
            
            Thread.sleep(100);
        }
    }
}
