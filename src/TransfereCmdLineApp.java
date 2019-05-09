import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Scanner;

public class TransfereCmdLineApp implements Runnable {

    private static ReliableTransfer rtrans;
    private static int portaorigem;

    public TransfereCmdLineApp(int po)
    {
        this.rtrans = new ReliableTransfer();
        this.portaorigem = po;
        new Thread(new Receiver(rtrans,portaorigem)).start();
    }

    public static void main(String[] args) {
        //Listen at port 7777
        int pdestino = 0;
        new Thread(new TransfereCmdLineApp(Integer.parseInt(args[0]))).start();
        // CommandLineApp
        //rtrans = new AgenteUDP(Integer.parseInt(args[0]), Integer.parseInt(args[1])); //"127.0.0.1"
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
                        int response = rtrans.estabeleceConexao(ip,pdestino,porigem);
                        if (response == 0) System.out.println("Conexao Estabelecida!");
                        break;
                    }
                    catch (UnknownHostException e) {
                        System.out.println("Host Invalido.");
                        break;
                    }
                case "GET":
                    if (pdestino != 0)
                        rtrans.fileRequest(a[1],pdestino);
                    else System.out.println("Conexao nao estabelecida.");
                    break;
                case "PUT":
                    if (pdestino != 0)
                        rtrans.requestToSend(a[1],pdestino);
                    else System.out.println("Conexao nao estabelecida.");
                    break;
                case "CLOSE":
                    rtrans.terminaConexao(pdestino);
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
            pacote = rtrans.recebeuPacote();

            int tipo = rtrans.getTipo(pacote);

            switch (tipo) // tipo da mensagem
            {
                case 1: // (SYN)
                    int response = rtrans.aceitaConexao(pacote,portaorigem);
                    if (response==0) System.out.println("Conexao Estabelecida!");
                    break;
                case 2: // (SYN+ACK)
                    rtrans.recebeSYNACK(pacote);
                    break;
                case 3: // ACK
                    rtrans.recebeuACK(pacote);
                    break;
                case 5:
                    rtrans.sendResponse(rtrans.getDados(pacote),rtrans.getPortaOrigem(pacote));
                    break;
                case 6:
                    rtrans.readyToReceive(pacote);
                    break;
                case 8:
                    rtrans.recebeuFINRequest(pacote);
                    break;
                default:
                    break;
            }
        }
    }
}
