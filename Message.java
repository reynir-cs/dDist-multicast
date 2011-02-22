import java.io.Serializable;
import java.net.InetSocketAddress;

public class Message<E extends Serializable> implements Serializable, Comparable<Message<E>> {
    public enum Type {GET_PREV, SET_PREV, SET_NEXT, DATA, ACK, GET_PREV_ANSWER};

    private InetSocketAddress peer;
    private Type type;
    private E data;
    private InetSocketAddress peerData;
    private long timestamp;

    public Message(InetSocketAddress peer, Type type) {
	this(peer, type, null);
    }



    public Message(InetSocketAddress peer, Type type, InetSocketAddress peerData) {
	this(peer, type, peerData, null);
    }

    public Message(InetSocketAddress peer, Type type, InetSocketAddress peerData, E data) {
	this.peer = peer;
	this.type = type;
	this.peerData = peerData;
	this.data = data;
	timestamp = 0;
    }

    public InetSocketAddress getPeer() {
	return peer;
    }

    public Type getType() {
	return type;
    }

    public E getData() {
	return data;
    }

    public InetSocketAddress getPeerData() {
	return peerData;
    }

    public String toString() {
	return "Message:[peer=" + peer + ",type=" + type +",data=" + data + ",peerData=" + peerData +",timestamp="+timestamp+"]";
    }

    public void setTimestamp(long val) {
	timestamp = val;
    }

    public long getTimestamp() {
	return timestamp;
    }

    public Message<E> makeAck() {
        Message<E> msg = new Message<E>(peer, Type.ACK);
        msg.setTimestamp(timestamp);
        return msg;
    }

    public int compareTo(Message<E> msg) {
	long res = timestamp - msg.getTimestamp();
	if (res == 0)
	    res = peer.toString().compareTo(msg.getPeer().toString());
	if (res == 0)
	    res = (type == Type.ACK) ? -1 : 1;
	return (res > 0) ? 1 : ((res < 0) ? -1 : 0);
    }

    public boolean hasAck(Message<E> msg) {
	return timestamp == msg.getTimestamp() && peer.equals(msg.getPeer());
    }
}
