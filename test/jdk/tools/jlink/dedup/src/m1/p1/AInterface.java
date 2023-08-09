package m1.p1;


import m3.p3.ServiceInterface;

public class AInterface implements ServiceInterface {

    public String getString() {
        return "A1_A2";
    }

    public String getServiceName() {
        return "AService";
    }
}
