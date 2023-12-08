/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javax.management.remote.rest.test.data;

import java.util.List;

/**
 *
 */
public interface QueueSamplerBeanMBean {
        public QueueSample getQueueSample();
    
    public void setQueueSample(QueueSample sample);

    public String getQueueName();
    
    public void setQueueName(String name);
    
    public void clearQueue();

    public String[] testMethod1(int[] param2, String param1, int sd34, String[] param3, QueueSample[] param4, List<QueueSample> param5);
}
