import java.io.IOException;
import java.io.Serializable;


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

public interface PointToPointQueueReceiverEnd<E extends Serializable> extends Runnable, Pollable<E> {

	/**
	 * Specifies the port on which this receiving end is listening.
	 * 
	 * @param port The port number on which this receiving end is waiting for connections.
	 * @throws IOException in case there are problems with getting that port.
	 */
	public void listenOnPort(int port) throws IOException;
	
	/**
	 * Calling this method will make the queue stop receiving incoming messages.
	 * Should only be done when the sending ends no longer try to send messages
	 * to this queue. If the sending end try to send to this queue after a call 
	 * to shutdown() all bets are off.
	 */
	public void shutdown();
	
	/**
	 * Will return the next object in this incoming queue. If the queue is empty, then 
	 * the method blocks until incoming objects arrive. Removes the object from the queue.
	 * Must only be called when no listener is registered on the queue. It returns null if
	 * this queue is empty, i.e., if it is shutdown and has delivered all received objects.
	 * 
	 * @return The front of the queue.
	 */
	public E poll();
	
 	/**
 	 * Starts the queue manager which receives incoming objects and adds them to this 
 	 * queue, so they can be retrieved using poll(). 
 	 * 
 	 */
	public void run();
	
}

