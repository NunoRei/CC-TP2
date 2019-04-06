import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class Receiver implements Runnable {

    int port;

    public Receiver(int port) {
        this.port = port;
    }

    public void run()
    {
        // Step 1 : Create a socket to listen at port 1234
        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        byte[] areceber = new byte[1024];

        DatagramPacket pedido = null;
        while (true)
        {

            // Step 2 : create a DatagramPacket to receive the data.
            pedido = new DatagramPacket(areceber, areceber.length);

            // Step 3 : receive the data in byte buffer.
            try {
                ds.receive(pedido);
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("Client:-" + data(areceber));

            // Exit the server if the client sends "bye"
            if (data(areceber).toString().equals("bye"))
            {
                System.out.println("Client sent bye.....EXITING");
                break;
            }

            // Clear the buffer after every message.
            areceber = new byte[1024];
        }
    }

    // A utility method to convert the byte array
    // data into a string representation.
    public static StringBuilder data(byte[] a)
    {
        if (a == null)
            return null;
        StringBuilder ret = new StringBuilder();
        int i = 0;
        while (a[i] != 0)
        {
            ret.append((char) a[i]);
            i++;
        }
        return ret;
    }
}
