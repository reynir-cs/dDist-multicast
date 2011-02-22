import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.*;
import java.net.*;

/**
 *
 * Non-robust implementation of the receiving end of a distributed queue of objects 
 * of class E. The class E must be Serializable, as the objects are moved using 
 * ObjectOutputStream and ObjectInputStream. The sending end is implemented by 
 * ObjectQueueSenderEndNonRobust.
 * 
 * @author Jesper Buus Nielsen, Aarhus University, 2011.
 *
 */

public class PointToPointQueueReceiverEndNonRobust<E extends Serializable> extends Thread implements PointToPointQueueReceiverEnd<E> {

	PointToPointQueueReceiverEndNonRobust() {
		this.pendingObjects = new ConcurrentLinkedQueue<E>();
	}

	/**
	 * Specifies the port on which this receiving end is listening.
	 * 
	 * @param port The port number on which this receiving end is waiting for connections.
	 * @throws IOException when it cannot open the server socket on the given port.
	 */
	public void listenOnPort(int port) throws IOException {
		this.serverSocket = new ServerSocket(port);
		this.start();
	}
	
	/**
	 * Calling this method will make the queue stop receiving incoming messages.
	 * Should only be done when the sending ends no longer try to send messages
	 * to this queue. If the sending end try to send to this queue after a call 
	 * to shutdown() they will get a connection error.
	 */
	public void shutdown() {
		synchronized (pendingObjects) {
			shutdown = true;
			pendingObjects.notifyAll();
		}
	}
		
	/**
	 * Will return the next object in this incoming queue. If the queue is empty, then 
	 * the method blocks until incoming objects arrive. Removes the object from the queue.
	 * If the queue is dead (shut down and has delivered all objects), then poll() will
	 * return null.
	 * 
	 * @return The front of the queue, null if the queue is dead.
	 */
	public E poll() {		
		waitForObjectsToBePendingOrAShutdown();
		synchronized (pendingObjects) {
			if (pendingObjects.isEmpty()) {
				return null;
			} else {
				return pendingObjects.poll();
			}
		}
	}
	
 	/**
 	 * Starts a thread which waits for incoming objects and adds them to this queue,
 	 * so they can be retrieved using poll(). Stops after shutdown() is called.
 	 * 
 	 */
	public void run() {
		while (!shutdown) {
			pullObject();
		}
		try {
			serverSocket.close();
		} catch (IOException _) {
			// IGNORE AND CLOSE
		}
	}

	/*
	 * The queueu of received objects which were not yet delivered.
	 */
	final private ConcurrentLinkedQueue<E> pendingObjects; 

	/*
	 * The serverSocket on which this receiving end is listening for incoming
	 * connections.
	 */
	private ServerSocket serverSocket;

	/*
	 * Used to signal that the queue should stop taking incoming messages.
	 */
	private boolean shutdown;
	
	/**
	 * Internal method for pulling an object from the sending end(s) of the queue.
	 */
	private void pullObject() {
		Socket socket = null;
		ObjectInputStream forReceivingObjects = null;		
		try {
			serverSocket.setSoTimeout(1000); // To come back to live if listening after a shutdown
			while (socket==null && !shutdown) {
				try {
					socket = serverSocket.accept();
				} catch (SocketTimeoutException _) {
					// Ignore
				}
			}
		} catch (IOException e) {
			System.err.println("Problems accepting incoming connections!");
			System.err.println(e);
			return;
		}
		// Check if the loop returned because we are shutting down
		if (socket == null) {
			return;
		}
		try {
			forReceivingObjects = new ObjectInputStream(socket.getInputStream());			
		} catch (IOException e) {
			System.err.println("Problems accepting incoming connections!");
			System.err.println(e);
			try {
				socket.close();
			} catch (IOException ee) {
				System.err.println(ee);
			}
			return;
		}
		E object = null;
		try {
			Object incomingObject = forReceivingObjects.readObject();
			object = (E)(incomingObject); 
		} catch (ClassCastException e) {
			System.err.println("The peer sent object of unknown type on " + socket);
			System.err.println(e);
			object = null;
		} catch (IOException e) {
			System.err.println("Problems receiving object on " + socket);
			object = null;
		} catch (ClassNotFoundException e) {
			System.err.println("The peer sent object of unknown type on " + socket);
			System.err.println(e);
			object = null;
		} 
		if (object != null) {
			synchronized (pendingObjects) {
				pendingObjects.add(object);
				pendingObjects.notify();
			}
		} 
		try {
			forReceivingObjects.close();
		} catch (IOException ee) {
			System.err.println(ee);
		}
		try {
			socket.close();
		} catch (IOException ee) {
			System.err.println(ee);
		}
	}

	/**
	 * Used by callers to wait for objects to enter the queue of pending
	 * deliveries. When the method returns, then either the queue of pending
	 * deliveries is non empty, or the queue is shutting down. Both might be 
	 * the case.
	 */
 	private void waitForObjectsToBePendingOrAShutdown() {
 		synchronized (pendingObjects) {
 			while (pendingObjects.isEmpty() && !shutdown) {
 				try {
 					/* We will be woken up if an object arrives or the 
 					 * queue is shut down.
 					 */
 					pendingObjects.wait();
 				} catch (InterruptedException e) {
 					/* Probably shutting down. The while condition will
 					 * ensure proper behavior in case of some other 
 					 * interruption.
 					 */
 				}
 			}
 			// Now: pendingObjects is non empty or we are shutting down
 		}
 	}
}
