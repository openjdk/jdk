import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.TimeUnit;
import java.util.List;
import com.sun.management.MemoryMXBean;

/*
 * @test
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseConcLivenessEstimate -XX:ConcLivenessEstimateSeconds=1 -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=0 -XX:+ExplicitGCInvokesConcurrent TestConcurrentLivenessWhenGCAlwaysRuns
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseConcLivenessEstimate -XX:ConcLivenessEstimateSeconds=1 -XX:+UseShenandoahGC -XX:+ExplicitGCInvokesConcurrent TestConcurrentLivenessWhenGCAlwaysRuns
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseConcLivenessEstimate -XX:ConcLivenessEstimateSeconds=1 -XX:+UseZGC -XX:+ExplicitGCInvokesConcurrent TestConcurrentLivenessWhenGCAlwaysRuns
 */
public class TestConcurrentLivenessWhenGCAlwaysRuns {

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
        while(System.currentTimeMillis() < start + 3000) {
            MemoryMXBean memBean = (MemoryMXBean) ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memBean.getLiveHeapUsage();

            if (heap.getUsed() != 0) {
                throw new RuntimeException("Concurrent liveness ran during concurrent GC. Estimate should be zero but is " + heap.getUsed());
            }
            
            Thread.sleep(100);
        }
    }
}
