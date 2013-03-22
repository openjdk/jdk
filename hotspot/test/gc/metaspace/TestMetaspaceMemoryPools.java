import java.util.List;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

/* @test TestMetaspaceMemoryPools
 * @bug 8000754
 * @summary Tests that two MemoryPoolMXBeans are created, one for metaspace and
 *          one for class metaspace, is created and that a MemoryManagerMXBean
 *          is created.
 * @run main/othervm TestMetaspaceMemoryPools defined undefined
 * @run main/othervm -XX:-UseCompressedKlassPointers TestMetaspaceMemoryPools undefined undefined
 * @run main/othervm -XX:-UseCompressedKlassPointers -XX:MaxMetaspaceSize=60m TestMetaspaceMemoryPools undefined defined
 */
public class TestMetaspaceMemoryPools {
    public static void main(String[] args) {
        boolean isClassMetaspaceMaxDefined = args[0].equals("defined");
        boolean isMetaspaceMaxDefined = args[1].equals("defined");

        verifyThatMetaspaceMemoryManagerExists();

        verifyMemoryPool(getMemoryPool("Class Metaspace"), isClassMetaspaceMaxDefined);
        verifyMemoryPool(getMemoryPool("Metaspace"), isMetaspaceMaxDefined);
    }

    private static void verifyThatMetaspaceMemoryManagerExists() {
        List<MemoryManagerMXBean> managers = ManagementFactory.getMemoryManagerMXBeans();
        for (MemoryManagerMXBean manager : managers) {
            if (manager.getName().equals("MetaspaceManager")) {
                return;
            }
        }

        throw new RuntimeException("Expected to find a metaspace memory manager");
    }

    private static MemoryPoolMXBean getMemoryPool(String name) {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean pool : pools) {
            if (pool.getName().equals(name)) {
                return pool;
            }
        }

        throw new RuntimeException("Expected to find a memory pool with name " + name);
    }

    private static void verifyMemoryPool(MemoryPoolMXBean pool, boolean isMaxDefined) {
        MemoryUsage mu = pool.getUsage();
        assertDefined(mu.getInit(), "init");
        assertDefined(mu.getUsed(), "used");
        assertDefined(mu.getCommitted(), "committed");

        if (isMaxDefined) {
            assertDefined(mu.getMax(), "max");
        } else {
            assertUndefined(mu.getMax(), "max");
        }
    }

    private static void assertDefined(long value, String name) {
        assertTrue(value != -1, "Expected " + name + " to be defined");
    }

    private static void assertUndefined(long value, String name) {
        assertTrue(value == -1, "Expected " + name + " to be undefined");
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new RuntimeException(msg);
        }
    }
}
