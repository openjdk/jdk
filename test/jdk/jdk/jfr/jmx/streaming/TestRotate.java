package jdk.jfr.jmx.streaming;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

import javax.management.MBeanServerConnection;

import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;
import jdk.management.jfr.RemoteRecordingStream;

/**
 * @test
 * @key jfr
 * @summary Tests that streaming can work over chunk rotations
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.jmx.streaming.TestRotate
 */
public class TestRotate {

    @StackTrace(false)
    static class TestRotateEvent extends Event {
        int value;
    }

    public static void main(String... args) throws Exception {
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        Path p = Files.createDirectory(Paths.get("test-stream-rotate-" + System.currentTimeMillis()));
        System.out.println(p.toAbsolutePath());
        System.out.println("Go");
        CountDownLatch latch = new CountDownLatch(100);
        try (RemoteRecordingStream r = new RemoteRecordingStream(conn, p)) {
            r.onEvent(e -> {
                System.out.println(e);
                latch.countDown();
            });
            r.startAsync();
            for (int i = 1; i <= 100; i++) {
                TestRotateEvent e = new TestRotateEvent();
                e.value = i;
                e.commit();
                if (i % 30 == 0) {
                    rotate();
                }
                Thread.sleep(10);
            }
            latch.await();
        }
    }

    private static void rotate() {
        try (Recording r = new Recording()) {
            r.start();
        }
    }
}
