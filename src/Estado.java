import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Estado {

    private Lock l = new ReentrantLock();
    private Condition notEmpty = l.newCondition();

    private Queue<DatagramPacket> pacotesAenviar = new LinkedList<>();
    private Queue<DatagramPacket> pacotesAreceber = new LinkedList<>();
    private int qsize = 0;
    public DatagramPacket[] enviar;
    public DatagramPacket[] receber;
    public HashMap<Integer,Integer> ACKS = new HashMap<>(); // <SQN,Ocorrencias>
    private int portaorigem;
    private int portadestino;
    private InetAddress ip; //para ja so um ip por estar a testar com localhost
    private int nofpackets;
    private String filename;
    private long fsize;
    public int recebidos = 0;
    public int janela; // Tamanho da janela
    public int enviados = 0;
    private boolean conectado = false;
    private boolean prontoAtransferir = false;
    private int esperaACK = 0;
    private int recebeuACK = 0;
    private int SQN = 1;

    public Estado (int po, int pd, InetAddress ip)
    {
       this.portaorigem = po;
       this.portadestino = pd;
       this.ip = ip;
    }

    public void poeACK(int key, int value) 
    {
        l.lock();
        try {
            ACKS.put(key,value);
        }
        finally {
            l.unlock();
        }
    }
    
    public int getACK(int key)
    {
        l.lock();
        try {
            return ACKS.get(key);
        }
        finally {
            l.unlock();
        }
    }

    public boolean existeACK (int key) 
    {
        l.lock();
        try {
            return ACKS.containsKey(key);
        }
        finally {
            l.unlock();
        }
    }

    public void removeACK(int key) 
    {
        l.lock();
        try {
            ACKS.remove(key);
        }
        finally {
            l.unlock();
        }
    }

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

    public boolean isConectado() {
        return conectado;
    }

    public void setConectado(boolean conectado) {
        this.conectado = conectado;
    }

    public int getPortaorigem() {
        return portaorigem;
    }

    public void setPortaorigem(int portaorigem) {
        this.portaorigem = portaorigem;
    }

    public InetAddress getIp() {
        return ip;
    }

    public void setIp(InetAddress ip) {
        this.ip = ip;
    }

    public int getEsperaACK() {
        return esperaACK;
    }

    public void setEsperaACK(int esperaACK) {
        this.esperaACK = esperaACK;
    }

    public int getRecebeuACK() {
        return recebeuACK;
    }

    public void setRecebeuACK(int recebeuACK) {
        this.recebeuACK = recebeuACK;
    }

    public int getSQN() {
        return SQN;
    }

    public void setSQN(int SQN) {
        this.SQN = SQN;
    }

    public boolean isProntoAtransferir() {
        return prontoAtransferir;
    }

    public void setProntoAtransferir(boolean prontoAtransferir) {
        this.prontoAtransferir = prontoAtransferir;
    }

    public int getNofpackets() {
        return nofpackets;
    }

    public void setNofpackets(int nofpackets) {
        this.nofpackets = nofpackets;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getFsize() {
        return fsize;
    }

    public void setFsize(long fsize) {
        this.fsize = fsize;
    }

    public int getPortadestino() {
        return portadestino;
    }

    public void setPortadestino(int portadestino) {
        this.portadestino = portadestino;
    }
}
