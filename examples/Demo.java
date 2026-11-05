class BaseService {
    protected String serviceName() {
        return "CoreDatabaseService";
    }
}

public singleobject class Demo extends BaseService implements Runnable {
    private int connectionCount = 0;

    public void connect(String url) {
        connectionCount++;
        System.out.println("[" + serviceName() + "] Connected to " + url + " (count=" + connectionCount + ")");
    }

    @Override
    public void run() {
        System.out.println("Running singleton task on instance: " + System.identityHashCode(this));
    }

    public static void main(String[] args) {
        // 1. Contextual keyword test: 'singleobject' can still be used as a variable name!
        int singleobject = 100;
        System.out.println("Contextual keyword backward-compatibility check: singleobject = " + singleobject);

        // 2. Access singleton via compiler-synthesized getInstance()
        Demo s1 = Demo.getInstance();
        Demo s2 = Demo.getInstance();

        s1.connect("jdbc:postgresql://localhost:5432/prod");
        s2.connect("jdbc:postgresql://localhost:5432/analytics");

        System.out.println("Same instance (s1 == s2)? " + (s1 == s2));

        // 3. Polymorphism test (passing singleobject instance as Runnable)
        Runnable task = Demo.getInstance();
        task.run();
    }
}

