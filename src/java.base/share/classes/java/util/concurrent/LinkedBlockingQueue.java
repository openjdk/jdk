package java.util.concurrent;

import java.io.Serializable;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An optionally-bounded {@linkplain BlockingQueue blocking queue} based on
 * linked nodes.
 * This queue orders elements FIFO (first-in-first-out).
 * The <em>head</em> of the queue is that element that has been on the
 * queue the longest time.
 * The <em>tail</em> of the queue is that element that has been on the
 * queue the shortest time. New elements
 * are inserted at the tail of the queue, and the queue retrieval
 * operations obtain elements at the head of the queue.
 * Linked queues typically have higher throughput than array-based queues but
 * less predictable performance in most concurrent applications.
 *
 * <p>The optional capacity bound constructor argument serves as a
 * way to prevent excessive queue expansion. The capacity, if unspecified,
 * is equal to {@link Integer#MAX_VALUE}.  Linked nodes are
 * dynamically created upon each insertion unless this would bring the
 * queue above capacity.
 *
 * <p>This class and its iterator implement all of the <em>optional</em>
 * methods of the {@link Collection} and {@link Iterator} interfaces.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework">
 * Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <E> the type of elements held in this queue
 */
public class LinkedBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E>, Serializable {

    private static final long serialVersionUID = -6903933977591709194L;

    /*
     * "two lock queue" 算法的变体：有一个put锁和一个take锁来保证插入和获取
     * 数据的原子性，并且分别有自己的条件谓语来辅助进行操作的唤醒和等待。当前
     * 大小使用的是原子类型。
     *
     * A variant of the "two lock queue" algorithm.  The putLock gates
     * entry to put (and offer), and has an associated condition for
     * waiting puts.  Similarly for the takeLock.  The "count" field
     * that they both rely on is maintained as an atomic to avoid
     * needing to get both locks in most cases. Also, to minimize need
     * for puts to get takeLock and vice-versa, cascading notifies are
     * used. When a put notices that it has enabled at least one take,
     * it signals taker. That taker in turn signals others if more
     * items have been entered since the signal. And symmetrically for
     * takes signalling puts. Operations such as remove(Object) and
     * iterators acquire both locks.
     *
     * Visibility between writers and readers is provided as follows:
     *
     * Whenever an element is enqueued, the putLock is acquired and
     * count updated.  A subsequent reader guarantees visibility to the
     * enqueued Node by either acquiring the putLock (via fullyLock)
     * or by acquiring the takeLock, and then reading n = count.get();
     * this gives visibility to the first n items.
     *
     * To implement weakly consistent iterators, it appears we need to
     * keep all Nodes GC-reachable from a predecessor dequeued Node.
     * That would cause two problems:
     * - allow a rogue Iterator to cause unbounded memory retention
     * - cause cross-generational linking of old Nodes to new Nodes if
     *   a Node was tenured while live, which generational GCs have a
     *   hard time dealing with, causing repeated major collections.
     * However, only non-deleted Nodes need to be reachable from
     * dequeued Nodes, and reachability does not necessarily have to
     * be of the kind understood by the GC.  We use the trick of
     * linking a Node that has just been dequeued to itself.  Such a
     * self-link implicitly means to advance to head.next.
     */



    /** Linked list node class.
     *  链表节点类
     *
     * @param <E> 范型，节点类型
     */
    static class Node<E> {
        E item;

        /**
         * One of:
         * - the real successor Node 真正的next节点
         * - this Node, meaning the successor is head.next
         * - null, meaning there is no successor (this is the last node) null：最后一个节点；
         */
        Node<E> next;

        // 使用指定值初始化一个节点
        Node(E x) {
            item = x;
        }
    }

    // The capacity bound, or Integer.MAX_VALUE if none
    // 链表大小限制、如果没有则为 int 最大值
    private final int capacity;

    // Current number of elements
    // 链表当前元素数量
    private final AtomicInteger count = new AtomicInteger();

    /**
     * Head of linked list. Invariant: head.item == null
     *
     * 头节点： fixme： 头节点的数据为null、不是真正的数据指针
     */
    transient Node<E> head;

    /**
     * Tail of linked list. Invariant: last.next == null
     *
     * 尾节点： fixme: 尾节点的next为null；
     */
    private transient Node<E> last;

    /**
     *  =====================  两个锁都是写锁，数量使用  =============================
     */
    // Lock held by take, poll, etc
    // 获取数据的锁，take, poll等
    private final ReentrantLock takeLock = new ReentrantLock();

    // Wait queue for waiting takes
    // 在该锁上想要执行获取操作的等待队列
    // 在队列为空的时候、会调用 notEmpty.await()，将当前线程加入到该锁的 take等待队列；
    // 执行put的时候，如果判定到之前的容量为0、则执行notEmpty.signal() 方法、唤醒等待队列的一个线程；
    @SuppressWarnings("serial") // Classes implementing Condition may be serializable.
    private final Condition notEmpty = takeLock.newCondition();

    /** Lock held by put, offer, etc */
    private final ReentrantLock putLock = new ReentrantLock();

    /** Wait queue for waiting puts */
    @SuppressWarnings("serial") // Classes implementing Condition may be serializable.
    private final Condition notFull = putLock.newCondition();

    /**
     * 可能有取数的线程在等待 队列不为空 的条件谓词，这个方法专门唤醒这些线程：
     * 加锁 -> 唤醒其他线程 -> 释放锁
     *
     * Signals a waiting take. Called only from put/offer (which do not otherwise ordinarily lock takeLock.)
     */
    private void signalNotEmpty() {
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
    }

    /**
     * 唤醒等待 "队列不满"条件谓词 的线程
     *
     * Signals a waiting put. Called only from take/poll.
     */
    private void signalNotFull() {
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            notFull.signal();
        } finally {
            putLock.unlock();
        }
    }

    /**
     * 将当前节点入队队尾。
     * Links node at end of queue.
     *
     * @param node the node
     */
    private void enqueue(Node<E> node) {
        // assert putLock.isHeldByCurrentThread();
        // assert last.next == null;
        // fixme 从右到左赋值，最后所有的数据都为 node的值
        last = last.next = node;
    }

    /** 从队列中移除一个节点、并返回节点值：
     *  移除head节点、将head.next节点作为新的head节点，并移除、返回节点值。
     *
     * Removes a node from head of queue.
     *
     * @return the node
     */
    private E dequeue() {
        // assert takeLock.isHeldByCurrentThread();
        // assert head.item == null;
        Node<E> h = head;
        Node<E> first = h.next;
        // 节点指向自己？
        h.next = h; // help GC
        head = first;
        E x = first.item;
        first.item = null;
        return x;
    }

    /** 既不让插入、也不让移除
     *
     * Locks to prevent both puts and takes.
     */
    void fullyLock() {
        putLock.lock();
        takeLock.lock();
    }

    /** 释放"全锁"
     *
     * Unlocks to allow both puts and takes.
     */
    void fullyUnlock() {
        takeLock.unlock();
        putLock.unlock();
    }

    /** 链表默认容量为Integer.MAX_VALUE
     *
     * Creates a {@code LinkedBlockingQueue} with a capacity of {@link Integer#MAX_VALUE}.
     */
    public LinkedBlockingQueue() {
        this(Integer.MAX_VALUE);
    }

    /** 使用指定的容量创建队列.
     *
     * Creates a {@code LinkedBlockingQueue} with the given (fixed) capacity.
     *
     * @param capacity the capacity of this queue
     * @throws IllegalArgumentException if {@code capacity} is not greater
     *         than zero
     */
    public LinkedBlockingQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException();
        }
        this.capacity = capacity;
        last = head = new Node<E>(null);
    }

    /**
     * Creates a {@code LinkedBlockingQueue} with a capacity of
     * {@link Integer#MAX_VALUE}, initially containing the elements of the
     * given collection,
     * added in traversal order of the collection's iterator.
     *
     * @param collection the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any of its elements are null
     *                              如果队列为空、或者队列元素为空的时候、抛空指针异常
     */
    public LinkedBlockingQueue(Collection<? extends E> collection) {
        // 默认容量为最大
        this(Integer.MAX_VALUE);

        final ReentrantLock putLock = this.putLock;
        // Never contended, but necessary for visibility
        // todo 虽然不会竞争、但是提供了可见性保证
        putLock.lock();
        try {
            int n = 0;
            for (E e : collection) {
                if (e == null)
                    throw new NullPointerException();
                // todo 怎么会队列为full呢？
                if (n == capacity)
                    throw new IllegalStateException("Queue full");
                // 节点入队
                enqueue(new Node<E>(e));
                ++n;
            }
            count.set(n);
        } finally {
            putLock.unlock();
        }
    }

    // this doc comment is overridden to remove the reference to collections
    // greater in size than Integer.MAX_VALUE
    /**
     * 返回队列中元素的数量，原子类保证。
     *
     * @return the number of elements in this queue
     */
    public int size() {
        return count.get();
    }

    // this doc comment is a modified copy of the inherited doc comment,
    // without the reference to unlimited queues.
    /**
     * Returns the number of additional elements that this queue can ideally
     * (in the absence of memory or resource constraints) accept without
     * blocking. This is always equal to the initial capacity of this queue
     * less the current {@code size} of this queue.
     *
     * <p>Note that you <em>cannot</em> always tell if an attempt to insert
     * an element will succeed by inspecting {@code remainingCapacity}
     * because it may be the case that another thread is about to
     * insert or remove an element.
     */
    // 还能存放多少节点
    public int remainingCapacity() {
        return capacity - count.get();
    }

    /**
     * 将指定的数据插入队尾，
     * fixme 如果队列已经满了、则加入到等待"队列不为空/notFull"条件谓词为真的条件队列
     * Inserts the specified element at the tail of this queue,
     * waiting if necessary for space to become available.
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) throws InterruptedException {
        // 边界条件
        if (e == null){
            throw new NullPointerException();
        }

        // 插入数据前队列大小
        final int preCount;
        // 使用指定数据创建节点
        final Node<E> node = new Node<E>(e);
        // 队列大小
        final AtomicInteger count = this.count;
        // 获取put锁，如果线程中断、则抛异常
        final ReentrantLock putLock = this.putLock;
        putLock.lockInterruptibly();
        try {
            /* 队列满了、则将当前线程加入到等待 队列不为空 条件谓词为真的条件队列；
             * Note that count is used in wait guard even though it is
             * not protected by lock. This works because count can
             * only decrease at this point (all other puts are shut
             * out by lock), and we (or some other waiting put) are
             * signalled if it ever changes from capacity. Similarly
             * for all other uses of count in other wait guards.
             */
            // fixme while(!条件谓词){ 加入条件队列 }
            while (count.get() == capacity) {
                notFull.await();
            }
            // 将当前节点加入到队尾
            enqueue(node);
            // 容量递增、并获取之前的大小
            preCount = count.getAndIncrement();
            // 如果当前大小小于容量，可能有take操作了
            if (preCount + 1 < capacity)
                notFull.signal();
        } finally {
            putLock.unlock();
        }
        // 如果加入数据之前大小为0，则唤醒等待 队列不为空 条件谓词的条件队列
        if (preCount == 0)
            signalNotEmpty();
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting if
     * necessary up to the specified wait time for space to become available.
     *
     * @return {@code true} if successful, or {@code false} if
     *         the specified waiting time elapses before space is available
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        // 空指针
        if (e == null){
            throw new NullPointerException();
        }

        // 等待的毫秒数
        long nanos = unit.toNanos(timeout);
        // 插入数据之前的容量
        final int preCount;
        // 当前容量引用
        final AtomicInteger count = this.count;
        // 插入锁
        final ReentrantLock putLock = this.putLock;
        putLock.lockInterruptibly();
        try {
             // 容量满了的时候,fixme 带有超时时间的写法
            while (count.get() == capacity) {
                // 如果超时、则返回false标识插入失败
                if (nanos <= 0L)
                    return false;
                // 剩余的可等待时间 = 总体时间 - 花去的时间
                nanos = notFull.awaitNanos(nanos);
            }
            // 创建节点、并入队
            enqueue(new Node<E>(e));
            // 递增容量
            preCount = count.getAndIncrement();
            // 当前队列没有满、则唤醒所有等待put的线程
            if (preCount + 1 < capacity)
                notFull.signal();
        } finally {
            putLock.unlock();
        }
        // 之前大小为0、可能有队列等待不为空取数
        // 则调用方法专门 signal一下
        if (preCount == 0)
            signalNotEmpty();
        return true;
    }

    /**
     * Inserts the specified element at the tail of this queue if it is
     * possible to do so immediately without exceeding the queue's capacity,
     * returning {@code true} upon success and {@code false} if this queue
     * is full.
     * When using a capacity-restricted queue, this method is generally
     * preferable to method {@link BlockingQueue#add add}, which can fail to
     * insert an element only by throwing an exception.
     *
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        if (e == null){
            throw new NullPointerException();
        }

        // 队列已经满了、则立即返回false
        final AtomicInteger count = this.count;
        if (count.get() == capacity){
            return false;
        }

        final int preCount;
        final Node<E> node = new Node<E>(e);

        // 获取put锁
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            // 容量满后返回true
            // offer插入不了会返回false、put则一直阻塞；
            if (count.get() == capacity)
                return false;
            // 入队
            enqueue(node);

            // 如果队没有满、则唤醒 notFull 条件队列中的线程
            preCount = count.getAndIncrement();
            if (preCount + 1 < capacity)
                notFull.signal();
        } finally {
            putLock.unlock();
        }
        // 之前队列为空、则唤醒可能等待的取数线程
        if (preCount == 0)
            signalNotEmpty();
        return true;
    }

    public E take() throws InterruptedException {
        final E x;
        final int c;
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                notEmpty.await();
            }
            x = dequeue();
            c = count.getAndDecrement();
            if (c > 1)
                notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        if (c == capacity)
            signalNotFull();
        return x;
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        final E x;
        final int c;
        long nanos = unit.toNanos(timeout);
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                if (nanos <= 0L)
                    return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
            x = dequeue();
            c = count.getAndDecrement();
            if (c > 1)
                notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        if (c == capacity)
            signalNotFull();
        return x;
    }

    // 移除数据
    public E poll() {
        final AtomicInteger count = this.count;
        if (count.get() == 0)
            return null;
        final E x;
        final int c;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            if (count.get() == 0)
                return null;
            x = dequeue();
            c = count.getAndDecrement();
            if (c > 1)
                notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        if (c == capacity)
            signalNotFull();
        return x;
    }

    // 不移除
    public E peek() {
        // 如果队列为空、则返回数据
        final AtomicInteger count = this.count;
        if (count.get() == 0)
            return null;

        // 获取take锁、如果这里是读取锁更好
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            return (count.get() > 0) ? head.next.item : null;
        } finally {
            takeLock.unlock();
        }
    }

    /**
     * Unlinks interior Node p with predecessor pred.
     */
    void unlink(Node<E> p, Node<E> pred) {
        // assert putLock.isHeldByCurrentThread();
        // assert takeLock.isHeldByCurrentThread();
        // p.next is not changed, to allow iterators that are
        // traversing p to maintain their weak-consistency guarantee.
        p.item = null;
        pred.next = p.next;
        if (last == p)
            last = pred;
        if (count.getAndDecrement() == capacity)
            notFull.signal();
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.
     * Returns {@code true} if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
        if (o == null) return false;
        fullyLock();
        try {
            for (Node<E> pred = head, p = pred.next;
                 p != null;
                 pred = p, p = p.next) {
                if (o.equals(p.item)) {
                    unlink(p, pred);
                    return true;
                }
            }
            return false;
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Returns {@code true} if this queue contains the specified element.
     * More formally, returns {@code true} if and only if this queue contains
     * at least one element {@code e} such that {@code o.equals(e)}.
     *
     * @param o object to be checked for containment in this queue
     * @return {@code true} if this queue contains the specified element
     */
    public boolean contains(Object o) {
        if (o == null) return false;
        fullyLock();
        try {
            for (Node<E> p = head.next; p != null; p = p.next)
                if (o.equals(p.item))
                    return true;
            return false;
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence.
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this queue.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this queue
     */
    public Object[] toArray() {
        fullyLock();
        try {
            int size = count.get();
            Object[] a = new Object[size];
            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next)
                a[k++] = p.item;
            return a;
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence; the runtime type of the returned array is that of
     * the specified array.  If the queue fits in the specified array, it
     * is returned therein.  Otherwise, a new array is allocated with the
     * runtime type of the specified array and the size of this queue.
     *
     * <p>If this queue fits in the specified array with room to spare
     * (i.e., the array has more elements than this queue), the element in
     * the array immediately following the end of the queue is set to
     * {@code null}.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose {@code x} is a queue known to contain only strings.
     * The following code can be used to dump the queue into a newly
     * allocated array of {@code String}:
     *
     * <pre> {@code String[] y = x.toArray(new String[0]);}</pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a the array into which the elements of the queue are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this queue
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        fullyLock();
        try {
            int size = count.get();
            if (a.length < size)
                a = (T[])java.lang.reflect.Array.newInstance
                    (a.getClass().getComponentType(), size);

            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next)
                a[k++] = (T)p.item;
            if (a.length > k)
                a[k] = null;
            return a;
        } finally {
            fullyUnlock();
        }
    }

    public String toString() {
        return Helpers.collectionToString(this);
    }

    /**
     * Atomically removes all of the elements from this queue.
     * The queue will be empty after this call returns.
     */
    public void clear() {
        fullyLock();
        try {
            for (Node<E> p, h = head; (p = h.next) != null; h = p) {
                h.next = h;
                p.item = null;
            }
            head = last;
            // assert head.item == null && head.next == null;
            if (count.getAndSet(0) == capacity)
                notFull.signal();
        } finally {
            fullyUnlock();
        }
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        Objects.requireNonNull(c);
        if (c == this)
            throw new IllegalArgumentException();
        if (maxElements <= 0)
            return 0;
        boolean signalNotFull = false;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            int n = Math.min(maxElements, count.get());
            // count.get provides visibility to first n Nodes
            Node<E> h = head;
            int i = 0;
            try {
                while (i < n) {
                    Node<E> p = h.next;
                    c.add(p.item);
                    p.item = null;
                    h.next = h;
                    h = p;
                    ++i;
                }
                return n;
            } finally {
                // Restore invariants even if c.add() threw
                if (i > 0) {
                    // assert h.item == null;
                    head = h;
                    signalNotFull = (count.getAndAdd(-i) == capacity);
                }
            }
        } finally {
            takeLock.unlock();
            if (signalNotFull)
                signalNotFull();
        }
    }

    /**
     * Used for any element traversal that is not entirely under lock.
     * Such traversals must handle both:
     * - dequeued nodes (p.next == p)
     * - (possibly multiple) interior removed nodes (p.item == null)
     */
    Node<E> succ(Node<E> p) {
        if (p == (p = p.next))
            p = head.next;
        return p;
    }

    /**
     * Returns an iterator over the elements in this queue in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    /**
     * Weakly-consistent iterator.
     *
     * Lazily updated ancestor field provides expected O(1) remove(),
     * but still O(n) in the worst case, whenever the saved ancestor
     * is concurrently deleted.
     */
    private class Itr implements Iterator<E> {
        private Node<E> next;           // Node holding nextItem
        private E nextItem;             // next item to hand out
        private Node<E> lastRet;
        private Node<E> ancestor;       // Helps unlink lastRet on remove()

        Itr() {
            fullyLock();
            try {
                if ((next = head.next) != null)
                    nextItem = next.item;
            } finally {
                fullyUnlock();
            }
        }

        public boolean hasNext() {
            return next != null;
        }

        public E next() {
            Node<E> p;
            if ((p = next) == null)
                throw new NoSuchElementException();
            lastRet = p;
            E x = nextItem;
            fullyLock();
            try {
                E e = null;
                for (p = p.next; p != null && (e = p.item) == null; )
                    p = succ(p);
                next = p;
                nextItem = e;
            } finally {
                fullyUnlock();
            }
            return x;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            // A variant of forEachFrom
            Objects.requireNonNull(action);
            Node<E> p;
            if ((p = next) == null) return;
            lastRet = p;
            next = null;
            final int batchSize = 64;
            Object[] es = null;
            int n, len = 1;
            do {
                fullyLock();
                try {
                    if (es == null) {
                        p = p.next;
                        for (Node<E> q = p; q != null; q = succ(q))
                            if (q.item != null && ++len == batchSize)
                                break;
                        es = new Object[len];
                        es[0] = nextItem;
                        nextItem = null;
                        n = 1;
                    } else
                        n = 0;
                    for (; p != null && n < len; p = succ(p))
                        if ((es[n] = p.item) != null) {
                            lastRet = p;
                            n++;
                        }
                } finally {
                    fullyUnlock();
                }
                for (int i = 0; i < n; i++) {
                    @SuppressWarnings("unchecked") E e = (E) es[i];
                    action.accept(e);
                }
            } while (n > 0 && p != null);
        }

        public void remove() {
            Node<E> p = lastRet;
            if (p == null)
                throw new IllegalStateException();
            lastRet = null;
            fullyLock();
            try {
                if (p.item != null) {
                    if (ancestor == null)
                        ancestor = head;
                    ancestor = findPred(p, ancestor);
                    unlink(p, ancestor);
                }
            } finally {
                fullyUnlock();
            }
        }
    }

    /**
     * A customized variant of Spliterators.IteratorSpliterator.
     * Keep this class in sync with (very similar) LBDSpliterator.
     */
    private final class LBQSpliterator implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        Node<E> current;    // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes
        long est = size();  // size estimate

        LBQSpliterator() {}

        public long estimateSize() { return est; }

        public Spliterator<E> trySplit() {
            Node<E> h;
            if (!exhausted &&
                ((h = current) != null || (h = head.next) != null)
                && h.next != null) {
                int n = batch = Math.min(batch + 1, MAX_BATCH);
                Object[] a = new Object[n];
                int i = 0;
                Node<E> p = current;
                fullyLock();
                try {
                    if (p != null || (p = head.next) != null)
                        for (; p != null && i < n; p = succ(p))
                            if ((a[i] = p.item) != null)
                                i++;
                } finally {
                    fullyUnlock();
                }
                if ((current = p) == null) {
                    est = 0L;
                    exhausted = true;
                }
                else if ((est -= i) < 0L)
                    est = 0L;
                if (i > 0)
                    return Spliterators.spliterator
                        (a, 0, i, (Spliterator.ORDERED |
                                   Spliterator.NONNULL |
                                   Spliterator.CONCURRENT));
            }
            return null;
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            if (!exhausted) {
                E e = null;
                fullyLock();
                try {
                    Node<E> p;
                    if ((p = current) != null || (p = head.next) != null)
                        do {
                            e = p.item;
                            p = succ(p);
                        } while (e == null && p != null);
                    if ((current = p) == null)
                        exhausted = true;
                } finally {
                    fullyUnlock();
                }
                if (e != null) {
                    action.accept(e);
                    return true;
                }
            }
            return false;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            if (!exhausted) {
                exhausted = true;
                Node<E> p = current;
                current = null;
                forEachFrom(action, p);
            }
        }

        public int characteristics() {
            return (Spliterator.ORDERED |
                    Spliterator.NONNULL |
                    Spliterator.CONCURRENT);
        }
    }

    /**
     * Returns a {@link Spliterator} over the elements in this queue.
     *
     * <p>The returned spliterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#CONCURRENT},
     * {@link Spliterator#ORDERED}, and {@link Spliterator#NONNULL}.
     *
     * @implNote
     * The {@code Spliterator} implements {@code trySplit} to permit limited
     * parallelism.
     *
     * @return a {@code Spliterator} over the elements in this queue
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return new LBQSpliterator();
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        forEachFrom(action, null);
    }

    /**
     * Runs action on each element found during a traversal starting at p.
     * If p is null, traversal starts at head.
     */
    void forEachFrom(Consumer<? super E> action, Node<E> p) {
        // Extract batches of elements while holding the lock; then
        // run the action on the elements while not
        final int batchSize = 64;       // max number of elements per batch
        Object[] es = null;             // container for batch of elements
        int n, len = 0;
        do {
            fullyLock();
            try {
                if (es == null) {
                    if (p == null) p = head.next;
                    for (Node<E> q = p; q != null; q = succ(q))
                        if (q.item != null && ++len == batchSize)
                            break;
                    es = new Object[len];
                }
                for (n = 0; p != null && n < len; p = succ(p))
                    if ((es[n] = p.item) != null)
                        n++;
            } finally {
                fullyUnlock();
            }
            for (int i = 0; i < n; i++) {
                @SuppressWarnings("unchecked") E e = (E) es[i];
                action.accept(e);
            }
        } while (n > 0 && p != null);
    }

    /**
     * 使用Predicate筛选节点后进行批量删除
     *
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        return bulkRemove(filter);
    }

    /**
     * 移除所有在指定节点的数据，包含关系
     *
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> c.contains(e));
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> !c.contains(e));
    }

    /**
     * Returns the predecessor of live node p, given a node that was
     * once a live ancestor of p (or head); allows unlinking of p.
     */
    Node<E> findPred(Node<E> p, Node<E> ancestor) {
        // assert p.item != null;
        if (ancestor.item == null)
            ancestor = head;
        // Fails with NPE if precondition not satisfied
        for (Node<E> q; (q = ancestor.next) != p; )
            ancestor = q;
        return ancestor;
    }

    // 批量删除
    /** Implementation of bulk(大量、整体) remove methods. */
    @SuppressWarnings("unchecked")
    private boolean bulkRemove(Predicate<? super E> filter) {
        boolean removed = false;
        Node<E> p = null, ancestor = head;
        Node<E>[] nodes = null;
        int n, len = 0;
        do {
            // 1. Extract batch of up to 64 elements while holding the lock.
            fullyLock();
            try {
                if (nodes == null) {  // first batch; initialize
                    p = head.next;
                    for (Node<E> q = p; q != null; q = succ(q))
                        if (q.item != null && ++len == 64)
                            break;
                    nodes = (Node<E>[]) new Node<?>[len];
                }
                for (n = 0; p != null && n < len; p = succ(p))
                    nodes[n++] = p;
            } finally {
                fullyUnlock();
            }

            // 2. Run the filter on the elements while lock is free.
            long deathRow = 0L;       // "bitset" of size 64
            for (int i = 0; i < n; i++) {
                final E e;
                if ((e = nodes[i].item) != null && filter.test(e))
                    deathRow |= 1L << i;
            }

            // 3. Remove any filtered elements while holding the lock.
            if (deathRow != 0) {
                fullyLock();
                try {
                    for (int i = 0; i < n; i++) {
                        final Node<E> q;
                        if ((deathRow & (1L << i)) != 0L
                            && (q = nodes[i]).item != null) {
                            ancestor = findPred(q, ancestor);
                            unlink(q, ancestor);
                            removed = true;
                        }
                        nodes[i] = null; // help GC
                    }
                } finally {
                    fullyUnlock();
                }
            }
        } while (n > 0 && p != null);
        return removed;
    }

    /**
     * Saves this queue to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData The capacity is emitted (int), followed by all of
     * its elements (each an {@code Object}) in the proper order,
     * followed by a null
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {

        fullyLock();
        try {
            // Write out any hidden stuff, plus capacity
            s.defaultWriteObject();

            // Write out all elements in the proper order.
            for (Node<E> p = head.next; p != null; p = p.next)
                s.writeObject(p.item);

            // Use trailing null as sentinel
            s.writeObject(null);
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Reconstitutes(重新设立) this queue from a stream (that is, deserializes(反序列化) it).
     * 从流中获取一个数据，重新设置队列。
     *
     * @param s the stream
     *          流
     *
     * @throws ClassNotFoundException if the class of a serialized object could not be found
     *                                如果找不到被序列化的对象
     *
     * @throws java.io.IOException if an I/O error occurs
     *                             如果出现IO错误
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in capacity, and any hidden stuff
        s.defaultReadObject();

        // 计数器设置为0
        count.set(0);
        // 头尾指针都指向哑节点
        last = head = new Node<E>(null);

        // Read in all elements and place in queue
        // 读取所有元素，然后放到队列中
        for (;;) {
            // 从流中解析元素
            @SuppressWarnings("unchecked")
            E item = (E)s.readObject();
            if (item == null)
                break;
            add(item);
        }
    }
}
