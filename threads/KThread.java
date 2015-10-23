package nachos.threads;

import nachos.machine.*;

/**
 * A KThread is a thread that can be used to execute Nachos kernel code. Nachos
 * allows multiple threads to run concurrently.
 * 
 * To create a new thread of execution, first declare a class that implements
 * the <tt>Runnable</tt> interface. That class then implements the <tt>run</tt>
 * method. An instance of the class can then be allocated, passed as an argument
 * when creating <tt>KThread</tt>, and forked. For example, a thread that
 * computes pi could be written as follows:
 * 
 * <p>
 * <blockquote>
 * 
 * <pre>
 * class PiRun implements Runnable {
 * 	public void run() {
 *         // compute pi
 *         ...
 *     }
 * }
 * </pre>
 * 
 * </blockquote>
 * <p>
 * The following code would then create a thread and start it running:
 * 
 * <p>
 * <blockquote>
 * 
 * <pre>
 * PiRun p = new PiRun();
 * new KThread(p).fork();
 * </pre>
 * 
 * </blockquote>
 */
public class KThread {
	/**
	 * Get the current thread.
	 * 
	 * @return the current thread.
	 */
	public static KThread currentThread() {
		Lib.assertTrue(currentThread != null);
		return currentThread;
	}

	/**
	 * Allocate a new <tt>KThread</tt>. If this is the first <tt>KThread</tt>,
	 * create an idle thread as well.
	 */
	public KThread() {
		if (currentThread != null) {
			tcb = new TCB();
		}
		else {
			readyQueue = ThreadedKernel.scheduler.newThreadQueue(false);
			readyQueue.acquire(this);

			currentThread = this;
			parentThread = null;	//Created for join
			tcb = TCB.currentTCB();
			name = "main";
			restoreState();

			createIdleThread();
		}
	}

	/**
	 * Allocate a new KThread.
	 * 
	 * @param target the object whose <tt>run</tt> method is called.
	 */
	public KThread(Runnable target) {
		this();
		this.target = target;
	}

	/**
	 * Set the target of this thread.
	 * 
	 * @param target the object whose <tt>run</tt> method is called.
	 * @return this thread.
	 */
	public KThread setTarget(Runnable target) {
		Lib.assertTrue(status == statusNew);

		this.target = target;
		return this;
	}

	/**
	 * Set the name of this thread. This name is used for debugging purposes
	 * only.
	 * 
	 * @param name the name to give to this thread.
	 * @return this thread.
	 */
	public KThread setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Get the name of this thread. This name is used for debugging purposes
	 * only.
	 * 
	 * @return the name given to this thread.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the full name of this thread. This includes its name along with its
	 * numerical ID. This name is used for debugging purposes only.
	 * 
	 * @return the full name given to this thread.
	 */
	public String toString() {
		String statusString = "";
		if(status == 1)
			statusString = "ready";
		else if(status == 2)
			statusString = "running";
		else if(status == 3)
			statusString = "blocked";
		else if(status == 4)
			statusString = "finished";
		else
			statusString = "new";
		return (name + " (#" + id + ") is " + statusString);
	}

	/**
	 * Deterministically and consistently compare this thread to another thread.
	 */
	public int compareTo(Object o) {
		KThread thread = (KThread) o;

		if (id < thread.id)
			return -1;
		else if (id > thread.id)
			return 1;
		else
			return 0;
	}

	/**
	 * Causes this thread to begin execution. The result is that two threads are
	 * running concurrently: the current thread (which returns from the call to
	 * the <tt>fork</tt> method) and the other thread (which executes its
	 * target's <tt>run</tt> method).
	 */
	public void fork() {
		Lib.assertTrue(status == statusNew);
		Lib.assertTrue(target != null);

		Lib.debug(dbgThread, "Forking thread: " + toString() + " Runnable: "
				+ target);

		boolean intStatus = Machine.interrupt().disable();

		tcb.start(new Runnable() {
			public void run() {
				runThread();
			}
		});

		ready();

		Machine.interrupt().restore(intStatus);
	}

	private void runThread() {
		begin();
		target.run();
		finish();
	}

	private void begin() {
		Lib.debug(dbgThread, "Beginning thread: " + toString());

		Lib.assertTrue(this == currentThread);

		restoreState();

		Machine.interrupt().enable();
	}

	/**
	 * Finish the current thread and schedule it to be destroyed when it is safe
	 * to do so. This method is automatically called when a thread's
	 * <tt>run</tt> method returns, but it may also be called directly.
	 * 
	 * The current thread cannot be immediately destroyed because its stack and
	 * other execution state are still in use. Instead, this thread will be
	 * destroyed automatically by the next thread to run, when it is safe to
	 * delete this thread.
	 * 
	 * This thread also handles freeing the parent thread from its dependency
	 * on the child to finish to run
	 */
	
	public static void finish() {
		Lib.debug(dbgThread, "Finishing thread: " + currentThread.toString());
		Machine.interrupt().disable();

		Machine.autoGrader().finishingCurrentThread();
		
		Lib.assertTrue(toBeDestroyed == null);
		toBeDestroyed = currentThread;

		currentThread.status = statusFinished;

		//Freeing the parent thread 
		if(currentThread.parentThread != null){
			//System.out.println("\tFree Parent: " + currentThread.parentThread.toString() + " from: " + currentThread.toString());
			currentThread.parentThread.waitingOnChild = false;	
			currentThread.parentThread.ready();
		}
				
		System.out.println("\tFinished thread: " + currentThread.toString());
		sleep();
	}

	/**
	 * Relinquish the CPU if any other thread is ready to run. If so, put the
	 * current thread on the ready queue, so that it will eventually be
	 * rescheuled.
	 * 
	 * <p>
	 * Returns immediately if no other thread is ready to run. Otherwise returns
	 * when the current thread is chosen to run again by
	 * <tt>readyQueue.nextThread()</tt>.
	 * 
	 * <p>
	 * Interrupts are disabled, so that the current thread can atomically add
	 * itself to the ready queue and switch to the next thread. On return,
	 * restores interrupts to the previous state, in case <tt>yield()</tt> was
	 * called with interrupts disabled.
	 */
	public static void yield() {
		Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());

		Lib.assertTrue(currentThread.status == statusRunning);

		//Interrupt current queue, so it won't be touched during yielding process
		boolean intStatus = Machine.interrupt().disable();

		//Ready that current queue that was just canceled (putting it back at the 
		//end of the queue (but keep it untouchable)
		currentThread.ready();

		//Go to the next thread/task of the queue
		runNextThread();

		//Restore the status of that original queue to run (touchable)
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Relinquish the CPU, because the current thread has either finished or it
	 * is blocked. This thread must be the current thread.
	 * 
	 * <p>
	 * If the current thread is blocked (on a synchronization primitive, i.e. a
	 * <tt>Semaphore</tt>, <tt>Lock</tt>, or <tt>Condition</tt>), eventually
	 * some thread will wake this thread up, putting it back on the ready queue
	 * so that it can be rescheduled. Otherwise, <tt>finish()</tt> should have
	 * scheduled this thread to be destroyed by the next thread to run.
	 */
	//Interrupts must be disabled, sets unfinished threads to blocked, run next thread
	public static void sleep() {
		Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());

		if (currentThread.status != statusFinished)
			currentThread.status = statusBlocked;

		runNextThread();
	}

	/**
	 * Moves this thread to the ready state and adds this to the scheduler's
	 * ready queue.
	 */
	//Interrupts must be disabled thread must be not ready, set to ready and waiting
	public void ready() {
		Lib.debug(dbgThread, "Ready thread: " + toString());

		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(status != statusReady);

		status = statusReady;
		if (this != idleThread)
			readyQueue.waitForAccess(this);

		Machine.autoGrader().readyThread(this);
	}

	/**
	 * Waits for "this" thread to finish. If this thread is already finished,
	 * return immediately. This method must only be called once; the second call
	 * is not guaranteed to return. This thread must not be the current thread.
	 * 
	 * Parent (currentThread)
	 * Child (this)
	 */
	public void join() {
		//Joining threads, checking to make sure you aren't joining yourself
		Lib.debug(dbgThread, "Joining to thread: " + toString());
		Lib.assertTrue(this != currentThread);
	
		/*
		 * Disable interrupts so no context switches can occur, allowing the desired
		 * thread to run its course. Once done re-enable this thread
		 */
		Machine.interrupt().disable();
		
		//If the thread you will be waiting on is already done, return immediately
		//If the parent thread already has a child ignore call
		if(this.status == KThread.statusFinished || currentThread.waitingOnChild){
			//System.out.println("\t\tJoin: Kill Fast");
			Machine.interrupt().enable();
		}
		else{
			this.parentThread = currentThread;
			//System.out.println("\tJoining:: Parent: " + currentThread.toString() + " , Child: " + this.toString());
			//Block the parent thread from running and yield the CPU now
			currentThread.waitingOnChild = true;
			currentThread.sleep();
			Machine.interrupt().enable();
		}
		
	}
	
	/**
	 * Create the idle thread. Whenever there are no threads ready to be run,
	 * and <tt>runNextThread()</tt> is called, it will run the idle thread. The
	 * idle thread must never block, and it will only be allowed to run when all
	 * other threads are blocked.
	 * 
	 * <p>
	 * Note that <tt>ready()</tt> never adds the idle thread to the ready set.
	 */
	private static void createIdleThread() {
		Lib.assertTrue(idleThread == null);

		idleThread = new KThread(new Runnable() {
			public void run() {
				while (true)
					yield();
			}
		});
		idleThread.setName("idle");

		Machine.autoGrader().setIdleThread(idleThread);

		idleThread.fork();
	}

	/**
	 * Determine the next thread to run, then dispatch the CPU to the thread
	 * using <tt>run()</tt>.
	 */
	private static void runNextThread() {
		KThread nextThread = readyQueue.nextThread();
		if (nextThread == null)
			nextThread = idleThread;

		nextThread.run();
	}

	/**
	 * Dispatch the CPU to this thread. Save the state of the current thread,
	 * switch to the new thread by calling <tt>TCB.contextSwitch()</tt>, and
	 * load the state of the new thread. The new thread becomes the current
	 * thread.
	 * 
	 * <p>
	 * If the new thread and the old thread are the same, this method must still
	 * call <tt>saveState()</tt>, <tt>contextSwitch()</tt>, and
	 * <tt>restoreState()</tt>.
	 * 
	 * <p>
	 * The state of the previously running thread must already have been changed
	 * from running to blocked or ready (depending on whether the thread is
	 * sleeping or yielding).
	 * 
	 * @param finishing <tt>true</tt> if the current thread is finished, and
	 * should be destroyed by the new thread.
	 */
	private void run() {		
		//Assert that it is touchable (interrupts disabled)
		Lib.assertTrue(Machine.interrupt().disabled());
		
		Machine.yield();

		//Soon to be old thread
		currentThread.saveState();
		Lib.debug(dbgThread, "Switching from: " + currentThread.toString()
				+ " to: " + toString());

		//The new thread is this one
		currentThread = this;
		tcb.contextSwitch();		
		currentThread.restoreState();
	}

	/**
	 * Prepare this thread to be run. Set <tt>status</tt> to
	 * <tt>statusRunning</tt> and check <tt>toBeDestroyed</tt>.
	 */
	protected void restoreState() {
		Lib.debug(dbgThread, "Running thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
		Lib.assertTrue(tcb == TCB.currentTCB());

		Machine.autoGrader().runningThread(this);

		status = statusRunning;

		if (toBeDestroyed != null) {
			toBeDestroyed.tcb.destroy();
			toBeDestroyed.tcb = null;
			toBeDestroyed = null;
		}
	}

	/**
	 * Prepare this thread to give up the processor. Kernel threads do not need
	 * to do anything here.
	 */
	protected void saveState() {
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
	}

	/**
	 * Set the wake time, called through alarm, ThreadedKernel.alarm
	 * Runs of nachos ticks, not system time. 
	 */
	protected void setWakeTime(long time){
		this.wakeTime = time;
	}
	
	/**
	 * Check to see if this thread is allowed to wake up
	 */
	protected boolean canWake(){
		return Machine.timer().getTime() > wakeTime;
	}
	
		
	private static final char dbgThread = 't';

	/**
	 * Additional state used by schedulers.
	 * 
	 * @see nachos.threads.PriorityScheduler.ThreadState
	 */
	public Object schedulingState = null;

	private static final int statusNew = 0;

	private static final int statusReady = 1;

	private static final int statusRunning = 2;

	private static final int statusBlocked = 3;

	private static final int statusFinished = 4;

	/**
	 * The status of this thread. A thread can either be new (not yet forked),
	 * ready (on the ready queue but not running), running, or blocked (not on
	 * the ready queue and not running).
	 */
	private int status = statusNew;

	private String name = "(unnamed thread)";
	
	private Runnable target;

	private TCB tcb;

	/**
	 * Unique identifer for this thread. Used to deterministically compare
	 * threads.
	 */
	private int id = numCreated++;

	/** Number of times the KThread constructor was called. */
	private static int numCreated = 0;

	/**
	 * Parent thread is used with join to retain the thread that the child 
	 * thread must free when finished.
	 */	
	private KThread parentThread = null;
	private boolean waitingOnChild = false;
	
	//wakeTime if set is the (time) ticks to wait until to run
	private long wakeTime = 0;
	
	private static ThreadQueue readyQueue = null;

	private static KThread currentThread = null;

	private static KThread toBeDestroyed = null;

	private static KThread idleThread = null;


	
	
	/*
	 * 
	 * 
	 * 
	 * Tests Test
	 * 
	 *
	 * 
	 */
	
	static KThread thread0, thread1, thread2, thread3;	//Test 1
	static KThread threadA, threadB, threadC;			//Test 2
	static KThread threadI, threadJ, threadK;			//Test 3
	
	//Ping Test (Piazza)
	private static class PingTest implements Runnable 
	{
		PingTest(int which) 
		{
			this.which = which;
		}

		public void run() 
		{
			//Thread 1 is running thread0.join, thread 1 wants to be the parent
			if ( which == 1 )
				thread0.join();
			
			if ( which == 2 )
				thread1.join();	

			if ( which == 3)
				thread2.join();
			
			for (int i = 0; i < 5; i++) 
			{
				System.out.println("TEST 1: Thread: " + which + " - " + i);
				KThread.yield();
			}
		}

		private int which;
	}

	private static class PingTest2 implements Runnable 
	{
		PingTest2(int which) 
		{
			this.which = which;
		}

		//Have threads A,B,C.  C joins A, B sleeps until wake, C joins B
		//Should print in 0, 1, 2
		public void run() 
		{
			if ( which == 2 ){
				threadA.join();
				threadB.join();
			}
		
			if ( which == 1 ){
				ThreadedKernel.alarm.waitUntil(1500);
			}
				
			for (int i = 0; i < 5; i++) 
			{
				System.out.println("\tTEST 2: Thread: " + which + " - " + i);
				KThread.yield();
			}
		}

		private int which;
	}
	
	private static class PingTest3 implements Runnable 
	{
		PingTest3(int which) 
		{
			this.which = which;
		}

		//Have threads I,J,K.  J and K joins I
		//Should print in 0, then: undefined with 2, 1
		public void run() 
		{
			if (which == 2 ){
				threadI.join();
				threadJ.join();
			}
			
			if ( which == 1 ){
				//threadI.join();
			}
			
			if (which == 1){
				for (int i = 0; i < 3; i++) 
				{
					System.out.println("\tTEST 3: Thread: " + which + " - " + i);
					KThread.yield();
				}	
			}
			else{
				for (int i = 0; i < 5; i++) 
				{
					System.out.println("\tTEST 3: Thread: " + which + " - " + i);
					KThread.yield();
				}
			}
			
			
		}

		private int which;
	}

	
	
	public static void selfTest() 
	{
		Lib.debug(dbgThread, "Enter KThread.selfTest");
		thread0 = new KThread(new PingTest(0)).setName("forked thread 0");
		thread1 = new KThread(new PingTest(1)).setName("forked thread 1");
		thread2 = new KThread(new PingTest(2)).setName("forked thread 2");
		thread3 = new KThread(new PingTest(3)).setName("forked thread 3");
		thread0.fork();
		thread1.fork();
		thread2.fork();
		thread3.fork();
	}
	
	public static void selfTest2(){
		Lib.debug(dbgThread, "Enter KThread.selfTest2");
		threadA = new KThread(new PingTest2(0)).setName("forked thread A");
		threadB = new KThread(new PingTest2(1)).setName("forked thread B");
		threadC = new KThread(new PingTest2(2)).setName("forked thread C");
		threadA.fork();
		threadB.fork();
		threadC.fork();
	}

	public static void selfTest3(){
		Lib.debug(dbgThread,  "Enter KThread.selfTest3");
		threadI = new KThread(new PingTest3(0)).setName("forked thread I");
		threadJ = new KThread(new PingTest3(1)).setName("forked thread J");
		threadK = new KThread(new PingTest3(2)).setName("forked thread K");
		threadI.fork();
		threadJ.fork();
		threadK.fork();
	}
}
