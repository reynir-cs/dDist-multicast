import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.io.*;
import java.net.*;
import java.util.List;
import java.util.ArrayList;

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

public class PointToPointQueueSenderEndNonRobust<E extends Serializable> implements PointToPointQueueSenderEnd<E>, Runnable  {
	/*
	 * The address of the receiving end of the queue.
	 */
	private InetSocketAddress receiverAddress;

        /* TODO: List of listeners */
        private List<SendFaultListener> listeners;

        private Thread currThread;
	
	/*
	 * The objects not yet delivered.
	 */
	final private ConcurrentLinkedQueue<E> pendingObjects; 
	
	/*
	 * Used to signal that the queue should shut down.
	 */
	private boolean shutdown = false;
	private boolean running;
        private Semaphore isShutdown = new Semaphore(0);

	/**
	 * 
	 * @param serverAddress The IP address and port of the receiver
	 */
	public PointToPointQueueSenderEndNonRobust() {
		this.pendingObjects = new ConcurrentLinkedQueue<E>();
                listeners = new ArrayList<SendFaultListener>();
	}

	/**
	 * 
	 * @param serverAddress The IP address and port of the receiver end.
	 */
	public void setReceiver(InetSocketAddress serverAddress) {
		if (serverAddress!=null) {
			if (running) {
				shutdown();
			}
			this.receiverAddress = serverAddress;
                        currThread = new Thread(this);
                        currThread.start();
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
		/* TODO: Signal running thread to stop. */
                shutdown = true;
                synchronized (pendingObjects) {
			pendingObjects.notifyAll();
                }
                currThread.interrupt();
                try {
                        isShutdown.acquire();
                } catch(InterruptedException e) {}
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
		if (receiverAddress==null) { // This should be impossible
			return false;
		}
		E object = pendingObjects.peek();
		if (object == null) {
			return false;
		}
		Socket socket = null;
		ObjectOutputStream forSendingObjects = null;		
		try {
			socket = new Socket(receiverAddress.getAddress(),
                                        receiverAddress.getPort());
			forSendingObjects = 
                                new ObjectOutputStream(socket.getOutputStream());
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
                /* TODO: Signalling and stuff */
                synchronized (pendingObjects) {
                        try {
                                while (pendingObjects.isEmpty() &&
                                                !Thread.currentThread().isInterrupted())
                                {
                                        pendingObjects.wait();
                                }
                        } catch(InterruptedException ignore) {
                                // Will be handled by caller
                        }
                }
        }
	
 	/**
 	 * Starts a thread which pushes objects in this queue to the receiver side.
 	 */
	public void run() {
		/* TODO: Signalling and stuff */
		running = true;

		while (!Thread.currentThread().isInterrupted()) {
			waitForObjectsToBePendingOrShutdown(); 
			if (!Thread.currentThread().isInterrupted()) {
				pushOneObject();
			}
		}
		
                /* Send the rest of the messages */
                if (shutdown) {
                        boolean allOkSoFar = true;
                        while (!pendingObjects.isEmpty() && allOkSoFar) {
                                allOkSoFar = pushOneObject();
                        }
                }

		if (!pendingObjects.isEmpty()) {
			System.err.printf("Warning: PointToPointQueueSendingEnd"
					+"shutting down with %d pending"
					+"messages.",
					pendingObjects.size());
		}
                isShutdown.release();
	}

        public void subscribe(SendFaultListener listener) {
                listeners.add(listener);
        }
}
