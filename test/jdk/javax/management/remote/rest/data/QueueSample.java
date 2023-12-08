package javax.management.remote.rest.test.data;

/*
 * QueueSample.java - Java type representing a snapshot of a given queue.
 * It bundles together the instant time the snapshot was taken, the queue
 * size and the queue head.
 */



import java.beans.ConstructorProperties;
import java.util.Date;

public class QueueSample {
    
    private final Date date;
    private final int size;
    private final String head;

    @ConstructorProperties({"date", "size", "head"})
    public QueueSample(Date date, int size, String head) {
        this.date = date;
        this.size = size;
        this.head = head;
    }

    public Date getDate() {
        return date;
    }

    public int getSize() {
        return size;
    }

    public String getHead() {
        return head;
    }
}
