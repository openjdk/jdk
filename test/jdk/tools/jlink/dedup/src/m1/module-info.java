import m1.p1.AInterface;
import m3.service.ServiceInterface;

module m1 {
    exports m1.p1;

    requires transitive java.desktop;
    requires m3;

    provides ServiceInterface
            with AInterface;
}
