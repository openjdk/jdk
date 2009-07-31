package beans;

import java.beans.BeanDescriptor;
import java.beans.SimpleBeanInfo;

public class FirstBeanBeanInfo extends SimpleBeanInfo {
    @Override
    public BeanDescriptor getBeanDescriptor() {
        return new BeanDescriptor(FirstBean.class);
    }
}
