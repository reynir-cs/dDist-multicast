import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;

/**
 *
 * The sending and receiving end of a distributed queue of objects of class E. 
 * It is part of a peer group of other MulticastQueue objects. An object sent 
 * by a member of the peer group should be seen by all members of the peer group.
 * The class E must be Serializable, to allow to use Java's serialization to move 
 * the objects.
 * 
 * @author Jesper Buus Nielsen, Aarhus University, 2011.
 *
 */

public interface MulticastQueue<E extends Serializable> extends Runnable, Pollable<E> {

	/**
	 * Specifies the message delivery guarantee of the queue. 
	 * NONE:   All messages which are put will eventually arrive at 
	 *         all peers, including the putter.
	 * FIFO:   As NONE, plus, messages put by the same peer are 
	 *         delivered in the order they were sent by that peer.
	 * CAUSAL: As FIFO, plus, all messages are delivered in a causal       
	 *         order at all peers. 
	 * TOTAL:  AS FIFO, plus, all messages are delivered in the same
	 *         order at all peers. 
	 */
	public enum DeliveryGuarantee { NONE, FIFO, CAUSAL, TOTAL };
	
	/**
	 * Used by the first group member.
	 * Specifies the port on which this founding peer is listening for peers.
	 * 
	 * @param port The port number on which this founding peer is waiting for peers.
	 * @param deliveryGuarantee The deliveryGuarantee of the queue.
	 * @throws IOException in case there are problems with getting that port.
	 */
	public void createGroup(int port, DeliveryGuarantee deliveryGuarantee) throws IOException;
	
	/**
	 * Used to join a peer group. This takes place by contacting one
	 * of the existing peers of the peer group. After this call the
	 * current instance of MulticastQueue is considered part of the 
	 * peer group. 
	 * 
	 * @param deliveryGuarantee The deliveryGuarantee of the queue.
	 * @param serverAddress The IP address and port of the known peer.
	 */
	public void joinGroup(InetSocketAddress knownPeer, DeliveryGuarantee deliveryGuarantee);

	/**
	 * 
	 * Puts a message in the queue. The call should be asynchronous, i.e., 
	 * it returns immediately. In particular, it returns before the object 
	 * is delivered to any peer. The manager of the queue should take 
	 * care of moving the object to all peers in the peer group.
	 * 
	 * @param object The message to be added to the queue.
	 */
	public void put(E object);

	/**
	 * Will return the next object in the incoming queue. If no object is 
	 * ready for delivery, then the method blocks until incoming objects arrive. 
	 * Removes the object from the queue. It returns null if this queue is dead, 
	 * i.e., this peer has officially left the peer group and all incoming 
	 * objects have been delivered via poll().
	 * 
	 * @return The front of the queue, null if the queue is dead.
	 */
	public E poll();
	
	/**
	 * Makes this instance of MulticastQueue leave the peer group. The other 
	 * peers should be informed of this. This instance of MulticastQueue should
	 * keep receiving messages until it has officially left the group and should,
	 * if needed, keep participating in implementing the distributed queue until 
	 * it has officially left the group.
	 */
	public void leaveGroup();

	/**
	 * Checks if all objects put at this peer have been delivered at all other 
	 * peers.
	 * 
	 * @return Whether all objects have been delivered to the receiving end.
	 */
	public boolean areTherePendingSends();

	
 	/**
 	 * Starts the thread manager which pushes objects to the other peers 
 	 * and receives object from the other peers.
 	 */
	public void run();
	
}
