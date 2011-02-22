import java.io.Serializable;
import java.net.InetSocketAddress;

/**
 *
 * The sending end of a distributed queue of objects of class E. The class E must 
 * be Serializable, to allow to use Java's serialization to move the objects.  
 * The receiver end is specified in ObjectQueueReceiverEnd.
 * 
 * @author Jesper Buus Nielsen, Aarhus University, 2011.
 *
 */

public interface PointToPointQueueSenderEnd<E extends Serializable> extends Runnable {

	/**
	 * Specified the address of the receiving end.
	 * 
	 * @param serverAddress The IP address and port of the receiver
	 */
	public void setReceiver(InetSocketAddress serverAddress);

	/**
	 * 
	 * Puts a message in this queue. The call should be asynchronous, i.e., 
	 * it returns immediately. In particular, it returns before the object 
	 * is delivered at the receiver end. The manager of the queue should take 
	 * care of moving the object to the receiving side. 
	 * 
	 * @param object The message to be added to the queue.
	 */
	public void put(E object);

	/**
	 * Shuts down the queue. After this no more messages can be added to the
	 * queue at the sending end. The queue is free to still deliver the objects
	 * already scheduled for delivery, but is not required to. The safe way to 
	 * use shutdown() is therefore to call it only when no objects are pending!
	 */
	public void shutdown();

	/**
	 * 
	 * @return Whether all objects have been delivered to the receiving end.
	 */ boolean isEmpty();
	
 	/**
 	 * Starts the thread manager which pushes objects to the queue of the receiving end.
 	 */
	public void run();
	
        // public void subscribe(SendFaultListener listener);
}
