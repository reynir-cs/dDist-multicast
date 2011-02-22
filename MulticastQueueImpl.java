import java.io.IOException;
import java.io.Serializable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.Queue;
import java.util.Random;

public class MulticastQueueImpl<E extends Serializable> extends Thread implements MulticastQueue<E> {
    private InetSocketAddress next, prev, thisPeer;
    private DeliveryGuarantee deliveryGuarantee;
    private PointToPointQueueReceiverEnd<Message<E>> recvQueue;
    private PointToPointQueueSenderEnd<Message<E>> sendQueue;
    private BlockingQueue<Message<E>> dataQueue;
    private Integer port;
    private boolean dead;
    private Thread manager;
    private long counter;

    public MulticastQueueImpl() {
	this(null);
    }

    public MulticastQueueImpl(Integer port) {
	sendQueue = new PointToPointQueueSenderEndNonRobust<Message<E>>();
	recvQueue = new PointToPointQueueReceiverEndNonRobust<Message<E>>();
	dataQueue = new PriorityBlockingQueue<Message<E>>();
	this.port = port;
	dead = false;
	manager = null;
	counter = 0;
    }

    public void createGroup(int port, DeliveryGuarantee deliveryGuarantee) throws IOException {
        next = prev = thisPeer = new InetSocketAddress(InetAddress.getLocalHost(), port);
        this.deliveryGuarantee = deliveryGuarantee;
	recvQueue.listenOnPort(port);
	sendQueue.setReceiver(next);
    }

    public void joinGroup(InetSocketAddress knownPeer, DeliveryGuarantee deliveryGuarantee) {
	if (port == null)
	    port = knownPeer.getPort();
	try {
	    recvQueue.listenOnPort(port);
	    thisPeer = new InetSocketAddress(InetAddress.getLocalHost(), port);
	} catch (IOException ex) {
	    throw new RuntimeException("Canno't open server socket on port " + port);
	}


	next = knownPeer;
	sendQueue.setReceiver(next);
	prev = getPrev(knownPeer);
	setPrev(next, thisPeer);
	setNext(prev, thisPeer);

	start();
    }

    public void put(E object) {
	counter++;
	Message<E> msg = new Message<E>(thisPeer, Message.Type.DATA, null, object);
	msg.setTimestamp(counter);
	sendQueue.put(msg);
    }

    public synchronized E poll() {
	while (!dead) {
	    Message<E> msg, ack;
	    try {
		msg = dataQueue.peek();
		if (msg == null) { wait(); continue; }
		dataQueue.remove();
		ack = dataQueue.peek();
		if (ack == null) { dataQueue.put(msg); wait(); continue; }
		dataQueue.remove();
	    } catch (InterruptedException ex) {
		throw new RuntimeException("DataQueue interrupted with message: " + ex.getMessage());
	    }

	    if (msg.hasAck(ack))
		return msg.getData();
	    try {
		dataQueue.put(msg);
		dataQueue.put(ack);
	    } catch (InterruptedException ex) {
		throw new RuntimeException("Unable to put to dataQueue");
	    }
	    try {
		this.wait();
	    } catch (InterruptedException ex) {
		throw new RuntimeException("this.wait() interrupted");
	    }
	}
	return null;
    }

    public void leaveGroup() {
	InetSocketAddress first=next, second=prev;
	if (prev.toString().compareTo(next.toString()) < 0) {
	    first = prev;
	    second = next;
	}
	dead = true;
	setPrev(next, prev);
	setNext(prev, next);
	sendQueue.shutdown();
	recvQueue.shutdown();
    }

    public boolean areTherePendingSends() {
	return !sendQueue.isEmpty();
    }

    public void run() {
	while (!dead) {
	    Message<E> msg = recvQueue.poll();
	    if (msg == null) return;
	    if (msg.getType() == Message.Type.DATA) {
		counter = Math.max(msg.getTimestamp(), counter) + 1;
		try {
		    dataQueue.put(msg);
		    synchronized(this) {
			notifyAll();
		    }
		} catch(InterruptedException ex) {
		    throw new RuntimeException("DataQueue interrupted in put with message: " + ex.getMessage());
		}
		
		if (msg.getPeer().equals(thisPeer)) {
		    if (!msg.isAck()) {
			msg.setIsAck(true);
			sendQueue.put(msg);
		    }
		    continue;
		}
		else {
		    sendQueue.put(msg);
		}
	    }
	    switch(msg.getType()){
	    case GET_PREV:
		PointToPointQueueSenderEnd<Message<E>> sendq = new PointToPointQueueSenderEndNonRobust<Message<E>>();
		sendq.setReceiver(msg.getPeerData());
		Message<E> answer = new Message<E>(msg.getPeerData(), Message.Type.GET_PREV_ANSWER, prev);
		sendq.put(answer);
		sendq.shutdown();
		break;
	    case SET_PREV:
		prev = msg.getPeerData();
		break;
	    case SET_NEXT:
		next = msg.getPeerData();
		sendQueue.shutdown();
		sendQueue = new PointToPointQueueSenderEndNonRobust<Message<E>>();
		sendQueue.setReceiver(next);
		break;
	    case DATA:
		break;
	    default:
		throw new RuntimeException("Wrong message type: " + msg.getType());
	    }
	}
    }

    private InetSocketAddress getPrev(InetSocketAddress peer) {
	Message<E> msg = new Message<E>(peer, Message.Type.GET_PREV, thisPeer);
	sendQueue.put(msg);
	Message<E> answer = recvQueue.poll();
	if (answer.getType() != Message.Type.GET_PREV_ANSWER)
	    throw new RuntimeException("Got message of type " + answer.getType() + " while joining");
	return answer.getPeerData();
    }

    private void setPrev(InetSocketAddress who, InetSocketAddress what) {
	Message<E> msg = new Message<E>(who, Message.Type.SET_PREV, what);
	sendQueue.put(msg);
    }

    private void setNext(InetSocketAddress who, InetSocketAddress what) {
	PointToPointQueueSenderEnd<Message<E>> sendq = new PointToPointQueueSenderEndNonRobust<Message<E>>();
	sendq.setReceiver(who);
	Message<E> msg = new Message<E>(who, Message.Type.SET_NEXT, what);
	sendq.put(msg);
	sendq.shutdown();
    }

    private static class Echoer extends Thread {
	MulticastQueueImpl<String> queue;
	public Echoer(MulticastQueueImpl queue) { this.queue = queue; start(); }
	public void run() { while (true) { String msg = queue.poll(); if (msg == null) return; System.out.println(msg); } }
    }

    public static void main(String... args) throws Exception {
	if (args.length < 1 || args.length > 3) {
	    System.err.println("Usage: ./command port [knownPeerIP [portToUse]]");
	    System.exit(1);
	}
	int port = Integer.parseInt(args[0]);
	MulticastQueueImpl<String> queue;
	if (args.length == 1) {
	    queue = new MulticastQueueImpl<String>(port);
	    queue.start();
	    queue.createGroup(port, DeliveryGuarantee.TOTAL);
	} else {
	    String knownPeerIP = args[1];
	    Integer portToUse = null;
	    if (args.length == 3)
		portToUse = Integer.parseInt(args[2]);
	    InetSocketAddress knownPeer = new InetSocketAddress(knownPeerIP, port);
	    queue = new MulticastQueueImpl<String>(portToUse);
	    queue.joinGroup(knownPeer, DeliveryGuarantee.TOTAL);
	}
	System.out.print("Your name: ");
	System.out.flush();
	BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
	String name = in.readLine();
	queue.put(name + " joined the ring");
	new Echoer(queue);
	while (!queue.dead) {
	    String message = in.readLine();
	    if (message == null || message.equals("quit")) {
		queue.put(name + " left the ring");
		System.out.println("Quitting in 5 seconds...");
		Thread.sleep(5000);
		queue.leaveGroup();
		System.exit(0);
	    }
	    queue.put(name + ": " + message);
	}
    }
}