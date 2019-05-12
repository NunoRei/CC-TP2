import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tabela que vai atualizando o estado de uma conexão e transferência.
 */
public class Estado {


    private Lock l = new ReentrantLock();
    private Condition notEmpty = l.newCondition();

    /** Fila de Pacotes a serem enviados pela Thread Sender */
    private Queue<DatagramPacket> pacotesAenviar = new LinkedList<>();
    /** Quantidade de elementos na fila de pacotes para envio */
    private int qsize = 0;
    /** Pacotes recebidos com dados para serem escritos para ficheiro */
    private Map<Integer,DatagramPacket> pacotesDoFicheiro = new HashMap<>();
    /** Porta de Origem */
    private int portaorigem;
    /** Porta de Destino */
    private int portadestino;
    /** Ip do destinatário */
    private InetAddress ip;
    /** Número de pacotes em que está dividido o ficheiro */
    private int nofpackets;
    /** Nome do ficheiro */
    private String filename;
    /** Tamanho do ficheiro */
    private long fsize;
    /** Pacotes que foram recebidos */
    private int recebidos = 0;
    /** Tamanho da janela */
    private int janela = 1;
    /** Conexão estabelecida */
    private boolean conectado = false;
    /** Pronto para receber e transferir ficheiro */
    private boolean prontoAtransferir = false;
    /** Valor esperado no próximo ACK */
    private int esperaACK = 0;
    /** Valor do ACK recebido */
    private int recebeuACK = 0;
    /** Número de Sequência atual */
    private int SQN = 1;
    /** Round Trip Time */
    private long RTT;

    /**
     * Construtor parametrizado
     * @param po Porta de Origem
     * @param pd Porta de Destino
     * @param ip Ip de destino
     */
    public Estado (int po, int pd, InetAddress ip)
    {
       this.portaorigem = po;
       this.portadestino = pd;
       this.ip = ip;
    }

    /**
     * Obter pacote de dados a enviar.
     * @return Pacote a ser enviado.
     */
    public DatagramPacket getPacoteAenviar() {
        l.lock();
        DatagramPacket dp = null;
        try {
            if (qsize == 0)
                notEmpty.await();
            dp = pacotesAenviar.remove();
            qsize -= 1;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            l.unlock();
            return dp;
        }
    }

    /**
     * Coloca um pacote na fila para envio.
     * @param p Pacote pronto para ser enviado.
     */
    public void colocaParaEnvio(DatagramPacket p) {
        l.lock();
        try {
            this.pacotesAenviar.add(p);
            qsize += 1;
            notEmpty.signalAll();
        } finally {
            l.unlock();
        }
    }

    /**
     * Retorna se tem uma conexão estabelecida ou não.
     * @return True se está conectado, False caso contrário.
     */
    public boolean isConectado() {
        return conectado;
    }

    /**
     * Atualiza a variável de conexão.
     * @param conectado
     */
    public void setConectado(boolean conectado) {
        this.conectado = conectado;
    }

    /**
     * Obter Porta de Origem.
     * @return Porta de Origem
     */
    public int getPortaorigem() {
        return portaorigem;
    }

    /**
     * Obter ip a que está conectado.
     * @return Ip da conexão.
     */
    public InetAddress getIp() {
        return ip;
    }

    /**
     * Obter valor do ACK esperado.
     * @return Valor do ACK esperado.
     */
    public int getEsperaACK() {
        return esperaACK;
    }

    /**
     * Atualiza o ACK esperado.
     * @param esperaACK ACK que espera receber.
     */
    public void setEsperaACK(int esperaACK) {
        this.esperaACK = esperaACK;
    }

    /**
     * Obter valor do ACK recebido.
     * @return valor do ACK recebido.
     */
    public int getRecebeuACK() {
        return recebeuACK;
    }

    /**
     * Atualiza o ACK recebido.
     * @param recebeuACK ACK que recebeu.
     */
    public void setRecebeuACK(int recebeuACK) {
        this.recebeuACK = recebeuACK;
    }

    /**
     * Retorna o Número de Sequência atual.
     * @return valor do Número de sequência atual.
     */
    public int getSQN() {
        return SQN;
    }

    /**
     * Atualizar o Número de Sequência.
     * @param SQN Número de Sequência.
     */
    public void setSQN(int SQN) {
        this.SQN = SQN;
    }

    /**
     * Retorna se está pronto a transferir ou receber ficheiro.
     * @return True ou False.
     */
    public boolean isProntoAtransferir() {
        return prontoAtransferir;
    }

    /**
     * Atualiza se está pronto para transferir ou receber ficheiro.
     * @param prontoAtransferir
     */
    public void setProntoAtransferir(boolean prontoAtransferir) {
        this.prontoAtransferir = prontoAtransferir;
    }

    /**
     * Retorna o número de pacotes em que o ficheiro vai ser enviado ou recebido.
     * @return Número de Pacotes.
     */
    public int getNofpackets() {
        return nofpackets;
    }

    /**
     * Coloca o número de pacotes que vão ser enviados ou recebidos na transferência do ficheiro.
     * @param nofpackets
     */
    public void setNofpackets(int nofpackets) {
        this.nofpackets = nofpackets;
    }

    /**
     * Obter nome do ficheiro.
     * @return Nome do ficheiro.
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Coloca o nome do ficheiro.
     * @param filename
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /**
     * Obter tamanho do ficheiro.
     * @return Tamanho do ficheiro.
     */
    public long getFsize() {
        return fsize;
    }

    /**
     * Coloca tamanho do ficheiro.
     * @param fsize
     */
    public void setFsize(long fsize) {
        this.fsize = fsize;
    }

    /**
     * Obter porta de destino
     * @return porta de destino
     */
    public int getPortadestino() {
        return portadestino;
    }

    /**
     * Obter valor da janela.
     * @return Valor da janela.
     */
    public int getJanela() {
        return janela;
    }

    /**
     * Obter quantidade de pacotes recebidos.
     * @return número de pacotes recebidos.
     */
    public int getRecebidos() {
        return recebidos;
    }

    /**
     * Coloca pacotes recebidos
     * @param recebidos
     */
    public void setRecebidos(int recebidos) {
        this.recebidos = recebidos;
    }

    /**
     * Obtém e retira pacote de dados do ficheiro do buffer com o valor passado.
     * @param sqn
     * @return Pacote
     */
    public DatagramPacket takePacoteDoFicheiro(int sqn)
    {
        l.lock();
        try {
            DatagramPacket p = this.pacotesDoFicheiro.get(sqn);
            this.pacotesDoFicheiro.remove(p);
            return p;
        }
        finally {
            l.unlock();
        }
    }

    /**
     * Verificar se o buffer contém o pacote com o valor passado.
     * @param sqn
     * @return Se existe o pacote ou não.
     */
    public boolean containsPacote(int sqn)
    {
        l.lock();
        try {
            return this.pacotesDoFicheiro.containsKey(sqn);
        }
        finally {
            l.unlock();
        }
    }

    /**
     * Poe pacote no buffer para ser escrito para ficheiro.
     * @param p
     * @param sqn
     */
    public void putPacoteDoFicheiro(DatagramPacket p, int sqn)
    {
        l.lock();
        try {
            this.pacotesDoFicheiro.put(sqn,p);
        }
        finally {
            l.unlock();
        }
    }

    /**
     * Obter o valor do Round Trip Time.
     * @return Round Trip Time.
     */
    public long getRTT() {
        return RTT;
    }

    /**
     * Colocar valor do Round Trip Time
     * @param RTT
     */
    public void setRTT(long RTT) {
        this.RTT = RTT;
    }

    /**
     * Limpar o estado da transferência quando terminada.
     */
    public void clearEstado()
    {
        prontoAtransferir=false;
        janela=1;
        fsize=0;
        filename=null;
        pacotesDoFicheiro = new HashMap<>();
        recebidos = 0;
    }
}
