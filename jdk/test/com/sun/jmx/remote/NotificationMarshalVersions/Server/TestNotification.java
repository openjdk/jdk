
import javax.management.Notification;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Jaroslav Bachorik <jaroslav.bachorik at oracle.com>
 */
public class TestNotification extends Notification {
    private ConfigKey key;

    public TestNotification(String type, Object source, long sequenceNumber) {
        super(type, source, sequenceNumber);
        key = ConfigKey.CONSTANT1;
    }

    @Override
    public String toString() {
        return "TestNotification{" + "key=" + key + '}';
    }
}
