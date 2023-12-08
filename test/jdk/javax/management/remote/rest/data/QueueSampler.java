package javax.management.remote.rest.test.data;

/*
 * QueueSampler.java - MXBean implementation for the QueueSampler MXBean.
 * This class must implement all the Java methods declared in the
 * QueueSamplerMXBean interface, with the appropriate behavior for each one.
 */


import java.util.Date;
import java.util.List;
import java.util.Queue;

public class QueueSampler implements QueueSamplerMXBean {

    private Queue<String> queue;
    private QueueSample sample;
    private String name;

    public QueueSampler(Queue<String> queue) {
        this.queue = queue;
        synchronized (queue) {
            sample = new QueueSample(new Date(), queue.size(), queue.peek());
        }
        name = "BoogeyMan";
    }

    public QueueSample getQueueSample() {
        return sample;
    }
    
    public void clearQueue() {
        synchronized (queue) {
            queue.clear();
        }
    }

    @Override
    public String[] testMethod1(int[] param2, String param1, int sd34, String[] param3, QueueSample[] param4, List<QueueSample> param5) {
        System.out.println("########## Invoke TestMethod1");
        return new String[]{"1","2","3","4","5"};
    }

    @Override
    public void setQueueSample(QueueSample sample) {
        this.sample = sample;
    }

    @Override
    public String getQueueName() {
        return name;
    }

    @Override
    public void setQueueName(String name) {
        this.name = name;
    }
}
