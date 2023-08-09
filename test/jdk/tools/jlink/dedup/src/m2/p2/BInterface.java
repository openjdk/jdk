package m2.p2;

import m3.p3.ServiceInterface;
public class BInterface implements ServiceInterface {

    public String getString() {
        return "B1_B2";
    }

    public String getServiceName() {
        return "BService";
    }
}
