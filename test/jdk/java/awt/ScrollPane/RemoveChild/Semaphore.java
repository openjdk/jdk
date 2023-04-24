public class Semaphore {
    volatile boolean state = false;
    Object lock = new Object();
    volatile int waiting = 0;
    public Semaphore() {
    }
    public void doWait() throws InterruptedException {
        synchronized(lock) {
            if (state) {
                return;
            }
            waiting++;
            synchronized(this) {
                wait();
            }
            waiting--;
        }
    }
    public void doWait(int timeout) throws InterruptedException {
        synchronized(lock) {
            if (state) {
                return;
            }
            waiting++;
            synchronized(this) {
                wait(timeout);
            }
            waiting--;
        }
    }
    public void raise() {
        synchronized(lock) {
            state = true;
            if (waiting > 0) {
                synchronized(this) {
                    notifyAll();
                }
            }
        }
    }
    public synchronized boolean getState() {
        return state;
    }
}
