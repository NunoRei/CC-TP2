import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Classe que recebe pacotes.
 */
public class Receiver implements Runnable {

    /** Classe que implementa a transferência viável */
    private ReliableTransfer rtrans;
    /** Porta de Origem */
    private int portaorigem;

    /**
     * Construtor parametrizado da Classe
     * @param a
     * @param po
     */
    public Receiver(ReliableTransfer a, int po) {
        this.rtrans = a;
        this.portaorigem = po;
    }

    /**
     * Thread que está à escuta de pacotes
     */
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
                ds.receive(pedido);
                rtrans.filtraPacote(pedido);
                areceber = new byte[1500];
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
