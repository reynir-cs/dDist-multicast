import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.*;
import java.net.*;

/**
 *
 * Non-robust implementation of the sending end of a distributed queue of objects 
 * of class E. The class E must be Serializable, as the objects are moved using 
 * ObjectOutputStream and ObjectInputStream. The receiver end is implemented in 
 * ObjectQueueReceiverEnd.
 * 
 * @author Jesper Buus Nielsen, Aarhus University, 2011.
 *
 */

public class PointToPointQueueSenderEndNonRobust<E extends Serializable> extends Thread implements PointToPointQueueSenderEnd<E>  {

	/**
	 * 
	 * @param serverAddress The IP address and port of the receiver
	 */
	public PointToPointQueueSenderEndNonRobust() {
		this.pendingObjects = new ConcurrentLinkedQueue<E>();
	}

	/**
	 * 
	 * @param serverAddress The IP address and port of the receiver end.
	 */
	public void setReceiver(InetSocketAddress serverAddress) {
		if (this.receiverAddress==null && serverAddress!=null) {
			this.receiverAddress = serverAddress;
			this.start();
		}
	}
	
	/**
	 * 
	 * Puts a message in this queue. The call is asynchronous, i.e., it returns 
	 * immediately. In particular, it returns before the object is delivered at
	 * the receiver end. The manager of the queue will later take care of moving 
	 * the object to the receiving side. 
	 * 
	 * @param object The message to be added to the queue.
	 */
	public void put(E object) {
		if (object == null) {
			throw new NullPointerException("Cannot send null's");
		}
		synchronized(pendingObjects) {
			boolean wasEmpty = pendingObjects.isEmpty();
			pendingObjects.add(object);	
			if (wasEmpty) {
				// We wake up the manager if it waits for a new message to send.
				pendingObjects.notify();
			}
		}
	}

	/**
	 * Shuts down the queue. After this no more messages can be added to the
	 * queue at the sending end. The queue will try to deliver the objects
	 * already scheduled for delivery, but will terminate on the first failed
	 * attempt. The safe way to use shutdown() is therefore to call it only
	 * when no objects are pending!
	 */
	public void shutdown() {
		synchronized (pendingObjects) {
			shutdown = true;
			pendingObjects.notifyAll();
		}
	}

	/**
	 * 
	 * @return Whether all messages have been delivered.
	 */
	public boolean isEmpty() {
		synchronized (pendingObjects) {
			return pendingObjects.isEmpty();
		}
	}
	
	/*
	 * The address of the receiving end of the queue.
	 */
	private InetSocketAddress receiverAddress;
	
	/*
	 * The objects not yet delivered.
	 */
	final private ConcurrentLinkedQueue<E> pendingObjects; 
	
	/*
	 * Used to signal that the queue should shut down.
	 */
	private boolean shutdown = false;

	/**
	 * 
	 * Takes the next pending message and tries to move it to the queue of the
	 * receiving end. This is an extremely inefficient implementation as it
	 * opens a new connection for each object to be sent! This, however, is 
	 * also a simple way to get some rudimentary robustness: a dropped connection,
	 * or other IOException, is handled simply by calling pushOneObject again. 
	 * Should only be called if there are objects to be sent.
	 * 
	 * @return whether an object was transfered to the receiver end of the queue
	 */
	private boolean pushOneObject() {
		if (receiverAddress==null) {
			return false;
		}
		E object = pendingObjects.peek();
		if (object == null) {
			return false;
		}
		Socket socket = null;
		ObjectOutputStream forSendingObjects = null;		
		try {
			socket = new Socket(receiverAddress.getAddress(),receiverAddress.getPort());
			forSendingObjects = new ObjectOutputStream(socket.getOutputStream());
		} catch (UnknownHostException e) {
			System.err.println("Problems looking up " + receiverAddress);
			System.err.println(e);
			return false;
		} catch (IOException e) {
			System.err.println("Problems opening socket to " + receiverAddress);
			System.err.println(e);
			return false;
		}
		try {
			forSendingObjects.writeObject(object); 
		} catch (IOException e) {
			System.err.println("Could not push object to host " + receiverAddress);
			System.err.println(e);
			return false;
		} finally {
			try {
				forSendingObjects.close();
				socket.close();
			} catch (IOException e) {
				System.err.println(e);
			}
		}
		/* When we make it here the object was pushed to the other side, 
		 * so we can remove it from the queue of pending pushes.
		 */
		pendingObjects.poll();
		return true;
	}

	/**
	 * Internal method for waiting until one or more objects are
	 * pending to be pushed. 
	 */
 	private void waitForObjectsToBePendingOrShutdown() {
 		synchronized (pendingObjects) {
 			while (pendingObjects.isEmpty() && !shutdown) {
 				try {
 					/*
 					 * The put method will wake us up if messages arrive.
 					 * The shutdown method will wake us up if we are to shut down.
 					 */
 					pendingObjects.wait();
 				} catch (InterruptedException e) {
 					// Ignore. The while condition ensures proper behavior in case of interrupts.
 				}
 			}
 			// Now objects are pending send or we are shutting down
 		}
 	}
	
 	/**
 	 * Starts a thread which pushes objects in this queue to the receiver side.
 	 */
	public void run() {

		while (!shutdown) {
			waitForObjectsToBePendingOrShutdown(); 
			if (!shutdown) {
				/*
				 * We might have come out of waitForObjectsToBePendingOrShutdown() 
				 * because of a shutdown. If not, then a message is ready to be sent.
				 */
				pushOneObject();
			}
		}
		
		/*
		 * We are shutting down. Will try to send the remaining messages.
		 * However, on the first delivery error, I will terminate. Here we go.
		 */
		boolean allOkSoFar = true;
		while (!pendingObjects.isEmpty() && allOkSoFar) {
			allOkSoFar = pushOneObject();
		}

		if (!pendingObjects.isEmpty()) {
			System.err.println("Warning: PointToPointQueueSendingEnd shutting down with " + pendingObjects.size() + " pending messages.");
		}
	}
}
