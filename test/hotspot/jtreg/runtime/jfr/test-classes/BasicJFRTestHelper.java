
import jdk.jfr.Event;
import jdk.jfr.Description;
import jdk.jfr.Label;

public class BasicJFRTestHelper {

    @Label("Event Test Class")
    @Description("A sample JFR Event Class")
    private static class TestEvent extends Event {
        @Label("Event Emitter Message")
        String _message;

        TestEvent(String message) {
            _message = message;
        }
    }

    public static void main(String[] args) {
        TestEvent event = new TestEvent("New TestEvent");

        if (!event.shouldCommit()) {
            throw new RuntimeException("shouldCommit returns false. Is JFR running?");
        }
        event.commit();
        System.out.println("TestEvent: after commit");
    }
}
