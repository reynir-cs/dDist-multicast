public class Echoer extends Thread {
    MulticastQueueImpl<String> queue;

    public Echoer(MulticastQueueImpl queue) {
        this.queue = queue;
        start();
    }

    public void run() {
        while (true) {
            String msg = queue.poll();
            if (msg == null)
                return;
            System.out.println(msg);
        }
    }
}
