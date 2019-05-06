import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Scanner;

public class TransfereCC implements Runnable {

    private static AgenteUDP aUDP;
    private static int portaorigem;

    public TransfereCC(int po)
    {
        this.aUDP = new AgenteUDP();
        this.portaorigem = po;
        new Thread(new Receiver(aUDP,portaorigem)).start();
    }

    public static void main(String[] args) {
        //Listen at port 7777
        int pdestino = 0;
        new Thread(new TransfereCC(Integer.parseInt(args[0]))).start();
        // CommandLineApp
        //aUDP = new AgenteUDP(Integer.parseInt(args[0]), Integer.parseInt(args[1])); //"127.0.0.1"
        while (true) {
            Scanner sc = new Scanner(System.in);
            String cmd = sc.nextLine();
            String[] a = cmd.split(" ");
            switch (a[0])
            {
                case "CONNECT":
                    try {
                        InetAddress ip = InetAddress.getByName(a[1]);
                        pdestino = Integer.parseInt(a[2]);
                        int porigem = Integer.parseInt(a[3]);
                        int response = aUDP.estabeleceConexao(ip,pdestino,porigem);
                        if (response == 0) System.out.println("Conexao Estabelecida!");
                        break;
                    }
                    catch (UnknownHostException e) {
                        System.out.println("Host Invalido.");
                        break;
                    }
                case "GET":
                    if (pdestino != 0)
                        aUDP.fileRequest(a[1],pdestino);
                    else System.out.println("Conexao nao estabelecida.");
                    break;
                default:
                    break;
            }
        }
    }

    /* Thread que vai tratar Pacotes recebidos */
    @Override
    public void run()
    {
        DatagramPacket pacote;

        while(true)
        {
            pacote = aUDP.recebeuPacote();

            int tipo = aUDP.getTipo(pacote);

            // Sera necessario verificar sequence number dos PDUs

            switch (tipo) // tipo da mensagem
            {
                case 1: // (SYN)
                    int response = aUDP.aceitaConexao(pacote,portaorigem);
                    if (response==0) System.out.println("Conexao Estabelecida!");
                    break;
                case 2: // (SYN+ACK)
                    aUDP.recebeSYNACK(pacote);
                    break;
                case 3: // ACK
                    aUDP.recebeuACK(pacote);
                    break;
                case 4: // Data
                    aUDP.guardaDados(pacote);
                    break;
                case 5:
                    aUDP.sendResponse(pacote);
                default:
                    break;
            }
        }
    }
}
