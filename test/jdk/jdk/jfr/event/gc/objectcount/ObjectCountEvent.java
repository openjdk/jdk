package jdk.jfr.event.gc.objectcount;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

public class ObjectCountEvent {
    private static final String objectCountEventPath = EventNames.ObjectCount;
    private static final String gcEventPath = EventNames.GarbageCollection;
    private static final String heapSummaryEventPath = EventNames.GCHeapSummary;

    public static void test(String gcName) throws Exception {
        Recording recording = new Recording();
        recording.enable(objectCountEventPath);
        recording.enable(gcEventPath);
        recording.enable(heapSummaryEventPath);

        ObjectCountEventVerifier.createTestData();
        recording.start();
        System.gc();
        recording.stop();

        System.out.println("gcName=" + gcName);
        List<RecordedEvent> events = Events.fromRecording(recording);
        for (RecordedEvent event : events) {
            System.out.println("Event: " + event);
        }

        Optional<RecordedEvent> gcEvent = events.stream()
                .filter(e -> isMySystemGc(e, gcName))
                .findFirst();

        List<RecordedEvent> objCountEvents = events.stream()
                .filter(e -> Events.isEventType(e, objectCountEventPath))
                .collect(Collectors.toList());
        Asserts.assertFalse(objCountEvents.isEmpty(), "No objCountEvents");

        Optional<RecordedEvent> heapSummaryEvent = events.stream()
                .filter(e -> Events.isEventType(e, heapSummaryEventPath))
                .filter(e -> "After GC".equals(Events.assertField(e, "when").getValue()))
                .findFirst();
        Asserts.assertTrue(heapSummaryEvent.isPresent(), "No heapSummary");
        System.out.println("Found heapSummaryEvent: " + heapSummaryEvent.get());

        Events.assertField(heapSummaryEvent.get(), "heapUsed").atLeast(0L).getValue();
        ObjectCountEventVerifier.verify(objCountEvents);
    }

    private static boolean isMySystemGc(RecordedEvent event, String gcName) {
        return Events.isEventType(event, gcEventPath) &&
                gcName.equals(Events.assertField(event, "name").getValue()) &&
                "System.gc()".equals(Events.assertField(event, "cause").getValue());
    }
}
