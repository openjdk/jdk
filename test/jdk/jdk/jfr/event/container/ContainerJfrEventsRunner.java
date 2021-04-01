
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

public class ContainerJfrEventsRunner {
    public static void main(String[] args) throws Exception {
        Recording recording = new Recording();
        
        recording.enable(EventNames.ContainerConfiguration);
        recording.start();
        recording.stop();
        List<RecordedEvent> events = Events.fromRecording(recording);
        Asserts.assertFalse(events.isEmpty(), "No events in recording");
        RecordedEvent event = events.get(0);
        System.out.println("===> " + event);
    }
}
