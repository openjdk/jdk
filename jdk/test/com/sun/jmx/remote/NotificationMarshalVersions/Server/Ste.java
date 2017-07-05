
import javax.management.NotificationBroadcasterSupport;

public class Ste extends NotificationBroadcasterSupport implements SteMBean {
    private long count = 0;

    public void foo() {
        sendNotification(new TestNotification("test", this, count++));
    }
}