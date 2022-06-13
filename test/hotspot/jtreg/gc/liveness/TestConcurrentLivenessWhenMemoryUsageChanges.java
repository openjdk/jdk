import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.TimeUnit;
import java.util.List;
import com.sun.management.MemoryMXBean;
import java.lang.Math;

/*
 * @test
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseConcLivenessEstimate -XX:ConcLivenessEstimateSeconds=3 -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=100 TestConcurrentLivenessWhenMemoryUsageChanges
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseConcLivenessEstimate -XX:ConcLivenessEstimateSeconds=3 -XX:+UseShenandoahGC -XX:ShenandoahGuaranteedGCInterval=100000 TestConcurrentLivenessWhenMemoryUsageChanges
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseConcLivenessEstimate -XX:ConcLivenessEstimateSeconds=3 -XX:+UseZGC -XX:ZCollectionInterval=100 TestConcurrentLivenessWhenMemoryUsageChanges
 */
public class TestConcurrentLivenessWhenMemoryUsageChanges {
    
    // 100 MB
    private static final int allocationSize = 1024 * 1024 * 100;
    
    // Error we give to estimator
    private static final double epsilon = 0.05;

    // MemoryMXBean contains the liveness estimation
    private static final MemoryMXBean memBean = (MemoryMXBean) ManagementFactory.getMemoryMXBean();

    public static void main(String[] args) throws Exception {

        TimeUnit.SECONDS.sleep(10);

        MemoryUsage initHeap = memBean.getLiveHeapUsage();

        if (initHeap.getUsed() == 0) {
            throw new RuntimeException("Expected ConcLivenessEstimate to produce a liveHeapUsage metric, but instead it is 0");
        }

        byte[] tempArr = new byte[allocationSize];

        TimeUnit.SECONDS.sleep(10);

        MemoryUsage incHeap = memBean.getLiveHeapUsage();

        if (getError(incHeap.getUsed(), initHeap.getUsed() + allocationSize) > epsilon) {
            throw new RuntimeException(
                "Expected ConcLivenessEstimate to increase but error of " + 
                getError(incHeap.getUsed(), initHeap.getUsed() + allocationSize) +
                " is not within allowed error of " + epsilon
            );
        }

        tempArr = null;

        TimeUnit.SECONDS.sleep(10);

        MemoryUsage decHeap = memBean.getLiveHeapUsage();

        if (getError(decHeap.getUsed(), incHeap.getUsed() - allocationSize) > epsilon) {
            throw new RuntimeException(
                "Expected ConcLivenessEstimate to decrease but error of " + 
                getError(incHeap.getUsed(), initHeap.getUsed() + allocationSize) +
                " is not within allowed error of " + epsilon
            );
        }
    }

    private static double getError(long num1, long num2) {
        return Math.abs(num1 - num2) / ((num1 + num2) / 2.0);
    }
}
