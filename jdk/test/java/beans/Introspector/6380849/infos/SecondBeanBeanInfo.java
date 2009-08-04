package infos;

import beans.SecondBean;

import java.beans.BeanDescriptor;
import java.beans.SimpleBeanInfo;

public class SecondBeanBeanInfo extends SimpleBeanInfo {
    @Override
    public BeanDescriptor getBeanDescriptor() {
        return new BeanDescriptor(SecondBean.class);
    }
}
