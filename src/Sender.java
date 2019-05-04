import java.io.IOException;
import java.net.*;

public class Sender implements Runnable {

    private Estado e;

    public Sender(Estado e) {
        this.e = e;
    }

    public Estado getEstado() {
        return this.e;
    }

    public void run()
    {

        DatagramSocket ds = null;

        try {
            ds = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }

        //ip = InetAddress.getByName("192.168.1.74");

        while (true)
        {
            try {
                //System.out.println("A obter pacotes para enviar...");
                DatagramPacket dp = e.getPacoteAenviar();
                //System.out.println("Obteve um pacote");
                if (dp != null) {
                    ds.send(dp);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

