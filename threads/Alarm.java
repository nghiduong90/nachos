package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	
	private ArrayList<KThread> sleepingThreads;
	
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		sleepingThreads = new ArrayList<KThread>();
		
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * Alarm is called roughly every 500 ticks, when it does so check to
	 * see if it can wake up an threads it put to sleep. Each instance of
	 * KThread knows when it is allowed to wake up, however, since it can't
	 * wake itself up it is done here. 
	 */
	private void wakeUpThreads(){
		Machine.interrupt().disable();
		for(int i = 0; i < sleepingThreads.size(); i++){
			if(i < sleepingThreads.size() && sleepingThreads.get(i).canWake()){
				//Wake up the thread
				System.out.println("\tAlarm::Waking up " + sleepingThreads.get(i).toString());
				sleepingThreads.get(i).ready();
				sleepingThreads.remove(i);
				i--;
			}
		}
		Machine.interrupt().enable();
	}
	
	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt(){
		wakeUpThreads();
		KThread.currentThread().yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		long wakeTime = Machine.timer().getTime() + x;
		System.out.println("\tAlarm::" + KThread.currentThread().toString() + 
						   " is waiting until " + wakeTime);
		
		Machine.interrupt().disable();
		KThread.currentThread().setWakeTime(wakeTime);
		sleepingThreads.add(KThread.currentThread());
		KThread.sleep();
		Machine.interrupt().enable();
	}
	

	// Place this function inside Alarm. And make sure Alarm.selfTest() is called inside ThreadedKernel.selfTest() method.
	public static void selftest() {
	    KThread t1 = new KThread(new Runnable() {
	        public void run() {
	            long time1 = Machine.timer().getTime();
	            int waitTime = 10000;
	            ThreadedKernel.alarm.waitUntil(waitTime);
	            Lib.assertTrue((Machine.timer().getTime() - time1) >= waitTime, " thread woke up too early.");
	            System.out.println("**Alarm Successful**");
	        }
	    });
	    t1.setName("T1");
	    
	    KThread t2 = new KThread(new Runnable() {
	        public void run() {
	            long time1 = Machine.timer().getTime();
	            int waitTime = 10000;
	            ThreadedKernel.alarm.waitUntil(waitTime);
	            Lib.assertTrue((Machine.timer().getTime() - time1) >= waitTime, " thread woke up too early.");
	            System.out.println("**Alarm Successful**");
	        }
	    });
	    t2.setName("T2");
	    
	    KThread t3 = new KThread(new Runnable() {
	        public void run() {
	            long time1 = Machine.timer().getTime();
	            int waitTime = 15000;
	            ThreadedKernel.alarm.waitUntil(waitTime);
	            Lib.assertTrue((Machine.timer().getTime() - time1) >= waitTime, " thread woke up too early.");
	            System.out.println("**Alarm Successful**");
	            
	            //Do it again
	            waitTime = 25000;
	            ThreadedKernel.alarm.waitUntil(waitTime);
	            Lib.assertTrue((Machine.timer().getTime() - time1) >= waitTime, " thread woke up too early.");
	            System.out.println("**Alarm Successful**");
	        }
	    });
	    t3.setName("T3");
	    
	    t1.fork();
	    t1.join();
	    
	    t2.fork();
	    t2.join();
	    
	    t3.fork();
	    t3.join();
	}
	
}
