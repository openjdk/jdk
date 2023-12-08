package javax.management.remote.rest.test.data;


import java.util.List;

public interface QueueSamplerMXBean {
    
    public QueueSample getQueueSample();
    
    public void setQueueSample(QueueSample sample);

    public String getQueueName();
    
    public void setQueueName(String name);
    
    public void clearQueue();

    public String[] testMethod1(int[] param2, String param1, int sd34, String[] param3, QueueSample[] param4, List<QueueSample> param5);
}
