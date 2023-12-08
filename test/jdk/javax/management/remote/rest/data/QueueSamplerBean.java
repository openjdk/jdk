/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javax.management.remote.rest.test.data;

import java.util.Date;
import java.util.List;
import java.util.Queue;

/**
 *
 */
public class QueueSamplerBean implements QueueSamplerBeanMBean {

    private Queue<String> queue;
    private QueueSample sample;
    private String name;

    public QueueSamplerBean(Queue<String> queue) {
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
