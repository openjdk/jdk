import org.testng.annotations.Test;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.test.lib.Utils;

/* @test
 * @summary Performance test for rest adapter
 * @library /test/lib
 * @modules jdk.management.rest/jdk.internal.management.remote.rest.http
 *          jdk.management.rest/jdk.internal.management.remote.rest.json
 *          jdk.management.rest/jdk.internal.management.remote.rest.json.parser
 *          jdk.management.rest/jdk.internal.management.remote.rest.mapper
 *          jdk.management.rest/jdk.internal.management.remote.rest
 * @build RestAdapterPerformanceTest RestAdapterTest
 * @run testng/othervm RestAdapterPerformanceTest
 */

@Test
public class RestAdapterPerformanceTest {

    private static Random random = Utils.getRandomInstance();
    private static AtomicInteger count = new AtomicInteger(1);

    @Test
    public void testMultipleClients() throws Exception {
        RestAdapterTest test = new RestAdapterTest();
        List<Runnable> tasks = Stream.of(RestAdapterTest.class.getMethods())
                .filter(m -> m.getName().startsWith("test")).map(m -> (Runnable)() -> {
                    try {
                        m.invoke(test);
                    } catch (IllegalAccessException e) {
                    } catch (InvocationTargetException e) {
                    }
                }).collect(Collectors.toList());

        ThreadPoolExecutor es = (ThreadPoolExecutor) Executors.newFixedThreadPool(20);
        es.setThreadFactory((Runnable R) -> new Thread(R, "perf-" + count.getAndIncrement()));
        long current = System.currentTimeMillis();
        test.setupServers();
        for (int i = 0; i < 200; i++) {
            Runnable task = tasks.get(random.nextInt(tasks.size()));
            es.execute(task);
        }

        System.out.println("Submitted 200 tasks");
        es.shutdown();
        es.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        float v = (float) (System.currentTimeMillis() - current) / (float) 1000;
        System.out.println("Total time = " + v + "s");
        test.tearDownServers();
    }
}
