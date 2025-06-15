package jdk.jfr.event.tracing;

import java.nio.file.Path;

import jdk.jfr.Configuration;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/**
 * @test
 * @summary Tests that java.lang.String can be traced.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.tracing.TestTracedString
 **/
public class TestTracedString {
    private static long SEED = System.currentTimeMillis();

    @Name("Message")
    static class MessageEvent extends jdk.jfr.Event {
        String message;
        long checkSum;
    }

    public static void main(String[] args) throws Exception {
        Configuration c = Configuration.getConfiguration("default");
        Path file = Path.of("recording.jfr");
        try (Recording r = new Recording(c)) {
            r.enable("jdk.MethodTrace").with("filter", "java.lang.String");
            r.start();
            emit(100, "");
            emit(100, "short");
            emit(100, "medium medium medium medium medium medium 1");
            emit(100, "medium medium medium medium medium medium 2");
            emit(100, "long".repeat(100));
            r.stop();
            r.dump(file);
            int count = 0;
            for (RecordedEvent e : RecordingFile.readAllEvents(file)) {
                if (e.getEventType().getName().equals("Message")) {
                    String text = e.getString("message");
                    long checkSum = e.getLong("checkSum");
                    if (checkSum(text) != checkSum) {
                        throw new Exception("Incorrect checksum for text " + text);
                    }
                    count++;
                }
            }
            if (count != 500) {
                throw new Exception("Expected 500 Message events. Got " + count);
            }
        }
    }

    private static void emit(int count, String text) {
        long checkSum = checkSum(text);
        for (int i = 0; i < count; i++) {
            MessageEvent m = new MessageEvent();
            m.message = text;
            m.checkSum = checkSum;
            m.commit();
        }
    }

    private static long checkSum(String text) {
        long checkSum = SEED;
        for (int i = 0; i < text.length(); i++) {
            checkSum += 17 * text.charAt(i);
        }
        return checkSum;
    }
}
