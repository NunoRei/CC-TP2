import java.net.DatagramPacket;
import java.net.DatagramSocket;


public class Receiver implements Runnable {

    private ReliableTransfer rtrans;
    private int portaorigem;

    public Receiver(ReliableTransfer a, int po) {
        this.rtrans = a;
        this.portaorigem = po;
    }

    public void run()
    {
        try {
            DatagramSocket ds;

            ds = new DatagramSocket(portaorigem);

            byte[] areceber = new byte[1500];

            DatagramPacket pedido = null;

            while (true)
            {
                pedido = new DatagramPacket(areceber, areceber.length);
                //Listening
                //System.out.println("Porta "+this.portaorigem+" a espera de pacotes....");
                ds.receive(pedido);
                //System.out.println("Recebeu um pacote");
                rtrans.filtraPacote(pedido);
                areceber = new byte[1500];
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
