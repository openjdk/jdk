import java.io.*;

public class Victim implements Serializable {
    public volatile Object aaaa = "AAAA"; // must be volatile...
    private final Object aabb = new Show(this);
    public Object bbbb = "BBBB";
}
class Show implements Serializable {
    private final Victim victim;
    public Show(Victim victim) {
        this.victim = victim;
    }
    private void readObject(java.io.ObjectInputStream in)
     throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();
        Thread thread = new Thread(new Runnable() { public void run() {
            for (;;) {
                Object a = victim.aaaa;
                if (a != null) {
                    System.err.println(victim+" "+a);
                    break;
                }
            }
        }});
        thread.start();

        // Make sure we are running compiled whilst serialisation is done interpreted.
        try {
            Thread.sleep(1000);
        } catch (java.lang.InterruptedException exc) {
            Thread.currentThread().interrupt();
        }
    }
}
