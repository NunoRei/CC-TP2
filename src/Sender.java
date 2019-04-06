import java.io.IOException;
import java.net.*;
import java.util.Scanner;

public class Sender implements Runnable {

    int port;

    public Sender(int port) {
        this.port = port;
    }

    public void run()
    {
        Scanner sc = new Scanner(System.in);

        // Step 1:Create the socket object for
        // carrying the data.
        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }

        InetAddress ip = null;
        try {
            //ip = InetAddress.getLocalHost();
            ip = InetAddress.getByName("192.168.1.64");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        byte buf[] = null;

        // loop while user not enters "bye"
        while (true)
        {
            String inp = sc.nextLine();

            // convert the String input into the byte array.
            buf = inp.getBytes();

            // Step 2 : Create the datagramPacket for sending
            // the data.
            DatagramPacket DpSend =
                    new DatagramPacket(buf, buf.length, ip, port);

            // Step 3 : invoke the send call to actually send
            // the data.
            try {
                ds.send(DpSend);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // break the loop if user enters "bye"
            if (inp.equals("bye"))
                break;
        }
    }
}

