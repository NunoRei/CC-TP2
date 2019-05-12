import java.io.IOException;
import java.net.*;

/**
 * Classe que envia Pacotes.
 */
public class Sender implements Runnable {
    /** Estado da transferência e conexão */
    private Estado e;

    /**
     * Construtor parametrizado
     * @param e
     */
    public Sender(Estado e) {
        this.e = e;
    }

    /**
     * Obter estado da transferência.
     * @return Estado.
     */
    public Estado getEstado() {
        return this.e;
    }

    /**
     * Thread que está à espera para enviar pacotes.
     */
    public void run()
    {

        DatagramSocket ds = null;

        try {
            ds = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }

        while (true)
        {
            try {
                DatagramPacket dp = e.getPacoteAenviar();
                if (dp != null) {
                    ds.send(dp);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

