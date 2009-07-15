package infos;

import java.beans.BeanDescriptor;
import java.beans.SimpleBeanInfo;

public class ThirdBeanBeanInfo extends SimpleBeanInfo {
    @Override
    public BeanDescriptor getBeanDescriptor() {
        return new BeanDescriptor(ThirdBeanBeanInfo.class);
    }
}
