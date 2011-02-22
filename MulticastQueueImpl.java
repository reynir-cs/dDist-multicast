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

public class MulticastQueueImpl<E extends Serializable> extends Thread
        implements MulticastQueue<E> {
    private InetSocketAddress next, prev, thisPeer;
    private DeliveryGuarantee deliveryGuarantee;
    private PointToPointQueueReceiverEnd<Message<E>> recvQueue;
    private PointToPointQueueSenderEnd<Message<E>> sendQueue;
    private BlockingQueue<Message<E>> msgQueue;
    private BlockingQueue<E> dataQueue;
    private Integer port;
    private boolean dead;
    private long counter;

    public MulticastQueueImpl() {
        this(null);
    }

    public MulticastQueueImpl(Integer port) {
        sendQueue = new PointToPointQueueSenderEndNonRobust<Message<E>>();
        recvQueue = new PointToPointQueueReceiverEndNonRobust<Message<E>>();
        msgQueue = new PriorityBlockingQueue<Message<E>>();
        dataQueue = new LinkedBlockingQueue<E>();
        this.port = port;
        dead = false;
        counter = 0;
    }

    public void createGroup(int port, DeliveryGuarantee deliveryGuarantee)
            throws IOException {
        next = prev = thisPeer = 
            new InetSocketAddress(InetAddress.getLocalHost(), port);
        this.deliveryGuarantee = deliveryGuarantee;
        recvQueue.listenOnPort(port);
        sendQueue.setReceiver(next);
    }

    public void joinGroup(InetSocketAddress knownPeer, 
            DeliveryGuarantee deliveryGuarantee) {
        if (port == null)
            port = knownPeer.getPort();
        try {
            recvQueue.listenOnPort(port);
            thisPeer = new InetSocketAddress(InetAddress.getLocalHost(), port);
        } catch (IOException ex) {
            throw new RuntimeException("Cannot open server socket on port " +
                    port);
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
        Message<E> msg = new Message<E>(thisPeer, Message.Type.DATA, null,
                object);
        msg.setTimestamp(counter);
        sendQueue.put(msg);
    }

    public synchronized E poll() {
        try {
            return dataQueue.take();
        }catch(InterruptedException e) {
            return null;
        }
    }

    public void leaveGroup() {
        dead = true;
        setPrev(next, prev);
        setNext(prev, next);
        sendQueue.shutdown();
        recvQueue.shutdown();
    }

    public boolean areTherePendingSends() {
        return !sendQueue.isEmpty();
    }

    private void handleData(Message<E> msg) {
        counter = Math.max(msg.getTimestamp(), counter) + 1;

        // The mesage is from us
        if (msg.getPeer().equals(thisPeer)) {
            sendQueue.put(msg.makeAck());
        } else { // Not from us
            try {
                msgQueue.put(msg);
            } catch(InterruptedException ex) {
                throw new RuntimeException("DataQueue interrupted in put"
                        +"with message: " + ex.getMessage());
            }
            // Pass it on if not from self
            sendQueue.put(msg);
        }
    }

    public void handleAck(Message<E> msg) {
        if (msg.getPeer().equals(thisPeer)) 
            return;
        try {
            Message<E> ackedMsg = msgQueue.take();
            if (!msg.hasAck(ackedMsg)) { // This should never happen.
                throw new RuntimeException("Invarianten er brudt!11");
            }
            dataQueue.put(ackedMsg.getData());
        } catch(InterruptedException e) {
            System.err.println(e);
        }
        sendQueue.put(msg); // Pass it on if not from self
    }


    public void run() {
        while (!dead) {
            Message<E> msg = recvQueue.poll();
            if (msg == null)
                return;
            switch(msg.getType()){
                case GET_PREV:
                    PointToPointQueueSenderEnd<Message<E>> sendq = 
                        new PointToPointQueueSenderEndNonRobust<Message<E>>();
                    sendq.setReceiver(msg.getPeerData());
                    Message<E> answer = new Message<E>(msg.getPeerData(),
                            Message.Type.GET_PREV_ANSWER, prev);
                    sendq.put(answer);
                    sendq.shutdown();
                    break;
                case SET_PREV:
                    prev = msg.getPeerData();
                    break;
                case SET_NEXT:
                    next = msg.getPeerData();
                    sendQueue.setReceiver(next);
                    break;
                case DATA:
                    handleData(msg);
                    break;
                case ACK:
                    handleAck(msg);
                    break;
                default:
                    throw new RuntimeException("Wrong message type: "
                            + msg.getType());
            }
        }
    }

    private InetSocketAddress getPrev(InetSocketAddress peer) {
        Message<E> msg = new Message<E>(peer, Message.Type.GET_PREV, thisPeer);
        sendQueue.put(msg);
        Message<E> answer = recvQueue.poll();
        if (answer.getType() != Message.Type.GET_PREV_ANSWER)
            throw new RuntimeException("Got message of type "
                    + answer.getType() + " while joining");
        return answer.getPeerData();
    }

    private void setPrev(InetSocketAddress who, InetSocketAddress what) {
        Message<E> msg = new Message<E>(who, Message.Type.SET_PREV, what);
        sendQueue.put(msg);
    }

    private void setNext(InetSocketAddress who, InetSocketAddress what) {
        PointToPointQueueSenderEnd<Message<E>> sendq = 
            new PointToPointQueueSenderEndNonRobust<Message<E>>();
        sendq.setReceiver(who);
        Message<E> msg = new Message<E>(who, Message.Type.SET_NEXT, what);
        sendq.put(msg);
        sendq.shutdown();
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
            InetSocketAddress knownPeer = 
                new InetSocketAddress(knownPeerIP, port);
            queue = new MulticastQueueImpl<String>(portToUse);
            queue.joinGroup(knownPeer, DeliveryGuarantee.TOTAL);
        }
        System.out.print("Your name: ");
        System.out.flush();
        BufferedReader in = 
            new BufferedReader(new InputStreamReader(System.in));
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
