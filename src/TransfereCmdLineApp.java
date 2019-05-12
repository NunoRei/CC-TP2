import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

/**
 * Aplicação da Linha de Comandos para transferência de ficheiros.
 * Aceita os comandos:
 * CONNECT ip, para estabelecer uma conexão com o ip passado como argumento.
 * GET caminho do ficheiro, para fazer download de um ficheiro.
 * PUT caminho do ficheiro, para fazer upload de um ficheiro.
 * CLOSE para fechar uma conexão estabelecida.
 */
public class TransfereCmdLineApp implements Runnable {
    /** Instância da classe ReliableTransfer que implementa a transferência fiável de ficheiros */
    private static ReliableTransfer rtrans;
    /** Porta de origem */
    private static int portaorigem = 7777;

    /**
     * Construtor da classe
     */
    public TransfereCmdLineApp()
    {
        this.rtrans = new ReliableTransfer();
        new Thread(new Receiver(rtrans,portaorigem)).start();
    }

    /**
     * Main
     * @param args
     */
    public static void main(String[] args) {
        //Listen at port 7777
        int pdestino = 0;
        new Thread(new TransfereCmdLineApp()).start();
        while (true) {
            Scanner sc = new Scanner(System.in);
            String cmd = sc.nextLine();
            String[] a = cmd.split(" ");
            switch (a[0])
            {
                case "CONNECT":
                    try {
                        InetAddress ip = InetAddress.getByName(a[1]);
                        pdestino = 7777;
                        int response = rtrans.estabeleceConexao(ip,pdestino,portaorigem);
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
                case 1:
                    int response = rtrans.aceitaConexao(pacote,portaorigem);
                    if (response==0) System.out.println("Conexao Estabelecida!");
                    break;
                case 2:
                    rtrans.recebeSYNACK(pacote);
                    break;
                case 3:
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
