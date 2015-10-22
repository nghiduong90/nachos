package nachos.threads;

import nachos.machine.*;

import java.util.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	
	private static final int SPEAK = 1;
	private static final int LISTEN = -1;
	private int counter;
	private boolean messageTransmit;
	
	private static int message;
	
	private Lock commLock;
	private Condition listenCondition;
	private Condition speakCondition;
	private Condition commCondition;
	
	
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		message = -1;
		messageTransmit = false;
		counter = 0;	// Positive speakers, negative listeners, in queue
		commLock = new Lock();
		speakCondition = new Condition(commLock);
		listenCondition = new Condition(commLock);
		commCondition = new Condition(commLock);
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
		
		//Message Pairing Synchronization
		commLock.acquire();
		//If there are listeners
		if(counter < 0 && messageTransmit == false){
			commCondition.wake();
		}
		else{
			//Go to sleep and wait for one to wake you up
			if(counter >= 0){
				counter++;
				commCondition.sleep();
			}
		}
		commLock.release();
		
		//Message Transmit Synchronization		
		commLock.acquire();
		if(messageTransmit == false){
			message = word;
			messageTransmit = true;
			listenCondition.wake();
		}
		commLock.release();
		
		System.out.println("Speaker Ending, setting: " + word);
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		int wordToReturn = -1;
		
		//Message Pairing Synchornization
		commLock.acquire();
		System.out.println("counter: " + counter);
		//If there are speakers
		if(counter > 0){
			commCondition.wake();
		}
		else{
			//Go to sleep and wait for one to wake you up
			if(counter >= 0){
				counter--;
				commCondition.sleep();
			}
		}
		
		//Message Transmit Synchronization
		if(messageTransmit == false){
			listenCondition.sleep();
			wordToReturn = message;
			message = -1;
			messageTransmit = false;
		}
		else{		
			wordToReturn = message;
			message = -1;
			messageTransmit = false;
			
		}
		commLock.release();
		System.out.println("Listen Ending, returning: " + wordToReturn + " (Diag -1,F) " + message +" "+ messageTransmit);
		
		return wordToReturn;
	}
	
	// Place this function inside Communicator. And make sure Communicator.selfTest() is called inside ThreadedKernel.selfTest() method.

	public static void selfTest(){
	    final Communicator com = new Communicator();
	    final long times[] = new long[4];
	    final int words[] = new int[2];
	    
	    //Speaker 1 is saying 4
	    KThread speaker1 = new KThread( new Runnable () {
	        public void run() {
	            com.speak(4);
	            times[0] = Machine.timer().getTime();
	        }
	    });
	    speaker1.setName("S1");
	    
	    /*
	    //Speaker 2 is saying 7
	    KThread speaker2 = new KThread( new Runnable () {
	        public void run() {
	            com.speak(7);
	            times[1] = Machine.timer().getTime();
	        }
	    });
	    speaker2.setName("S2");
	    */
	    KThread listener1 = new KThread( new Runnable () {
	        public void run() {
	            words[0] = com.listen();
	            times[2] = Machine.timer().getTime();
	        }
	    });
	    listener1.setName("L1");
	     
	    KThread listener2 = new KThread( new Runnable () {
	        public void run() {
	            words[1] = com.listen();
	            times[3] = Machine.timer().getTime();
	        }
	    });
	    listener2.setName("L2");
	    System.out.println("All Made");
	    
	    speaker1.fork(); 
	    //speaker2.fork(); 
	    listener1.fork(); 
	    listener2.fork();
	    
	    System.out.println("All Forked");
	    
	    speaker1.join(); 
	    //speaker2.join(); 
	    listener1.join(); 
	    listener2.join();
	    
	    System.out.println("All Joined");
	    
	    Lib.assertTrue(words[0] == 4, "Didn't listen back spoken word."); 
	    //Lib.assertTrue(words[1] == 7, "Didn't listen back spoken word.");
	    Lib.assertTrue(times[0] < times[2], "speak returned before listen.");
	    //Lib.assertTrue(times[1] < times[3], "speak returned before listen.");
	}
	
}
