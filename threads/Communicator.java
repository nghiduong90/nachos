package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	
	private static int message;
	private static Lock conditionLock;
	private static Condition2 speakWait;
	private static Condition2 listenWait;
	private static Condition2 endOfTransfered;
	private static int receive;
	private static int copied;
	private static boolean isRecieved;
	public Communicator() {
		conditionLock = new Lock();
		speakWait = new Condition2(conditionLock);
		listenWait = new Condition2(conditionLock);
		endOfTransfered = new Condition2 (conditionLock);
		receive = 0;
		copied = 0;
		isRecieved = false;
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word the integer to transfer.
	 */
	public void speak(int word) {
	    conditionLock.acquire();
	    
	    while (receive >= 1 || copied == 1)
	    	speakWait.sleep();
	    
	    receive++;
	    copied = 1;
	    
	    message = word;
	       
	    listenWait.wake();
	    if (isRecieved == false) 
	    	endOfTransfered.sleep();
	    
	    conditionLock.release();
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
        conditionLock.acquire();      
        speakWait.wake();
	    
        while(copied != 1)
        	listenWait.sleep();
        
        copied = 0;
        receive--;

        endOfTransfered.wake();
	    conditionLock.release();
		return message;
	}
	
	public static void selfTest(){
	    final Communicator com = new Communicator();
	    final long times[] = new long[4];
	    final int words[] = new int[2];
	    KThread speaker1 = new KThread( new Runnable () {
	        public void run() {
	            com.speak(4);
	            times[0] = Machine.timer().getTime();
	        }
	    });
	    speaker1.setName("S1");
	    KThread speaker2 = new KThread( new Runnable () {
	        public void run() {
	            com.speak(7);
	            times[1] = Machine.timer().getTime();
	        }
	    });
	    speaker2.setName("S2");
	    KThread listener1 = new KThread( new Runnable () {
	        public void run() {
	        	ThreadedKernel.alarm.waitUntil(5000);
	            times[2] = Machine.timer().getTime();
	            words[0] = com.listen();
	        }
	    });
	    listener1.setName("L1");
	    KThread listener2 = new KThread( new Runnable () {
	        public void run() {
	            times[3] = Machine.timer().getTime();
	            words[1] = com.listen();
	        }
	    });
	    listener2.setName("L2");
	    
	    speaker1.fork(); 
	    speaker2.fork(); 
	    listener1.fork();
	    listener2.fork();
	    
	    speaker1.join(); 
	    speaker2.join(); 
	    listener1.join();
	    listener2.join();
	    
	    if( (words[0] == 4 && words[1] == 7) || (words[0] == 7 || words[1] == 4) ){
	    	System.out.println ("**Assert is true for both SUCCESSFUL WORDS**");
	    }
	    else{
	    	Lib.assertTrue(false, "Words not matching");
	    }
	    /*
	    Lib.assertTrue(words[0] == 4, "Returned [" + words[0] + "] Expected [4]"); 
	    Lib.assertTrue(words[1] == 7, "Returned [" + words[0] + "] Expected [7]");
	    System.out.println ("**Assert is true for both SUCCESSFUL WORDS**");
	    Lib.assertTrue(times[0] > times[2], "1st speak() returned before listen() called.");
	    Lib.assertTrue(times[1] > times[3], "2nd speak() returned before listen() called.");
	    System.out.println ("**Assert is true for both SUCCESSFUL TIMES**");
	    */
	}
}
