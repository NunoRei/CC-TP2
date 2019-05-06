import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AgenteUDP {

    private Map<Integer,Sender> conns = new HashMap<>(); // <Porta, Threads Sender(que tem a tabela de estado)>
    private ArrayList<InetAddress> ipsconn = new ArrayList<>();
    private Lock l = new ReentrantLock();
    private Condition notEmpty = l.newCondition();
    private Condition notInWindowSize = l.newCondition();
    private Condition noSYNACK = l.newCondition();
    private Condition noACK = l.newCondition();
    private Condition noResponse = l.newCondition();
    private Condition notCompleted = l.newCondition();
    private int qsize = 0;
    private static int packetsize = 1480;
    private Queue<DatagramPacket> pacotesrecebidos = new LinkedList<>();

    private class PDU {

        // PDU [ numerodepacote | portaorigem | tipo | nofpackets | dados ]

        private int sequencenumber; // 4 bytes
        private int portaorigem; // 4 bytes
        private InetAddress ipdestino;
        private int portadestino; // 4bytes
        private int nofpackets; // 4 bytes
        private byte[] dados;
        private int length; // 4bytes
        private int tipo; // 1-SYN 2-SYN+ACK 3-ACK 4-Data 5-GET 6-PUT 7-RESPONSE(GET) //4bytes

        public PDU(int sq, int po, int pd, int np, byte[] data, int leng, int tipo, InetAddress ip) {
            this.sequencenumber = sq;
            this.portaorigem = po;
            this.portadestino = pd;
            this.nofpackets = np;
            this.dados = new byte[length];
            this.dados = data;
            this.length = leng;
            this.tipo = tipo;
            this.ipdestino = ip;
        }

        public DatagramPacket formaPacote() {

            DatagramPacket dpacket = null;
            byte[] bsqn = myIntToByteArray4(this.sequencenumber);
            byte[] bpo = myIntToByteArray4(this.portaorigem);
            byte[] btipo = myIntToByteArray4(this.tipo);
            byte[] bnp = myIntToByteArray4(this.nofpackets);
            byte[] blengt = myIntToByteArray4(this.length);

            byte[] pacote;

            try {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                os.write(bsqn);
                os.write(bpo);
                os.write(btipo);
                os.write(bnp);
                os.write(blengt);
                if (this.dados != null) os.write(this.dados);
                os.flush();
                pacote = os.toByteArray();
                dpacket = new DatagramPacket(pacote, pacote.length, ipdestino, portadestino);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return dpacket;
        }
    }

    public int getSQN(DatagramPacket pacote) {
        return ByteBuffer.wrap(Arrays.copyOfRange(pacote.getData(), 0, 4)).getInt();
    }

    public int getPortaOrigem(DatagramPacket pacote) {
        return ByteBuffer.wrap(Arrays.copyOfRange(pacote.getData(), 4, 8)).getInt();
    }

    public int getTipo(DatagramPacket pacote) {
        return ByteBuffer.wrap(Arrays.copyOfRange(pacote.getData(), 8, 12)).getInt();
    }

    public int getNumeroPacotes(DatagramPacket pacote) {
        return ByteBuffer.wrap(Arrays.copyOfRange(pacote.getData(), 12, 16)).getInt();
    }

    public int getTamanho(DatagramPacket pacote) {
        return ByteBuffer.wrap(Arrays.copyOfRange(pacote.getData(), 16, 20)).getInt();
    }

    public String getDados(DatagramPacket pacote) {
        try {
            String dados = new String(Arrays.copyOfRange(pacote.getData(), 20, pacote.getData().length), "UTF-8");
            dados = dados.replace("\0", "");
            return dados;
        } catch (Exception e) {
            return "";
        }
    }

    public int estabeleceConexao(InetAddress ip, int portadestino, int portaorigem)
    {
        l.lock();
        try {
            Estado e = new Estado(portaorigem, portadestino, ip);
            Sender snd = new Sender(e);
            conns.put(portadestino, snd);
            new Thread(snd).start();
            // Enviar SYN
            DatagramPacket p = (new PDU(e.getSQN(), portaorigem, portadestino, 1, null, 0, 1, ip)).formaPacote();
            e.colocaParaEnvio(p);
            // Esperar SYNACK
            try {
                while (!(e.isConectado())) {
                noSYNACK.await();               //AINDA NAO ESTA BEM, se o SYNACK nunca chegar o programa fica bloqueado, e preciso fazer timeout e reenviar o SYN
                }
            }
            catch (Exception exc) {

            }
            return 0;
        }
        finally {
            l.unlock();
        }
    }

    public int aceitaConexao(DatagramPacket pacote, int po)
    {
        l.lock();
        try {
            int portadestino = getPortaOrigem(pacote);
            int portaorigem = po;
            InetAddress ip = pacote.getAddress();
            int sqn = getSQN(pacote)+1;
            Estado e = new Estado(portaorigem, portadestino, ip);
            e.setSQN(sqn);
            Sender snd = new Sender(e);
            conns.put(portadestino, snd);
            new Thread(snd).start();
            // enviar SYNACK
            e.setEsperaACK(sqn+1);
            DatagramPacket p = (new PDU(sqn, portaorigem, portadestino, 1, null, 0, 2, ip)).formaPacote();
            e.colocaParaEnvio(p);
            // esperar ACK
            while ((e.getEsperaACK())!=(e.getRecebeuACK())) {
                try {
                    noACK.await();
                } catch (InterruptedException ex) { }
            }
            e.setRecebeuACK(0);
            e.setEsperaACK(0);
            return 0;
        }
        finally {
            l.unlock();
        }
    }

    public void recebeSYNACK(DatagramPacket synack)
    {
        l.lock();
        try {
            int portadestino = getPortaOrigem(synack);
            Sender snd = this.conns.get(portadestino);
            Estado e = snd.getEstado();
            e.setSQN(getSQN(synack)+1);
            e.setConectado(true);
            noSYNACK.signalAll();
            // enviar ACK
            DatagramPacket p = (new PDU(e.getSQN(), e.getPortaorigem(), portadestino, 1, null, 0, 3, e.getIp())).formaPacote();
            e.colocaParaEnvio(p);
        }
        finally {
            l.unlock();
        }
    }

    public void filtraPacote(DatagramPacket p)
    {
        l.lock();
        try {
        int tipo = getTipo(p);
        if (tipo==2) recebeSYNACK(p);
        else if (tipo==3) recebeuACK(p);
        else if (tipo==7) respostaAoGET(p);
        else colocaPacote(p);
        }
        finally {
            l.unlock();
        }
    }

    public void fileRequest(String request,int pdestino) {
        l.lock();
        try {
            Estado e = (this.conns.get(pdestino)).getEstado();
            byte dados[] = request.getBytes();
            e.setSQN(e.getSQN()+1);
            PDU pacote = new PDU(e.getSQN(), e.getPortaorigem(), pdestino, 1, dados, dados.length, 5, e.getIp());
            DatagramPacket p = pacote.formaPacote();
            e.colocaParaEnvio(p);
            // Esperar Response
            while (!(e.isProntoAtransferir())) {
                try {
                    noResponse.await();
                } catch (InterruptedException ex) { }
            }
            // Enviar ACK e Esperar dados do ficheiro
            p = (new PDU(e.getSQN(), e.getPortaorigem(), pdestino, 1, null, 0, 3, e.getIp())).formaPacote();
            e.colocaParaEnvio(p);
            receiveFile(e);
            System.out.println("Transferencia Concluida!");
        }
        finally {
            l.unlock();
        }
    }

    public void respostaAoGET(DatagramPacket p)
    {
        l.lock();
        try {
            int portadestino = getPortaOrigem(p);
            String[] dados = getDados(p).split(";");
            String filename = dados[0];
            long size = Integer.parseInt(dados[1]);
            int npacotes = Integer.parseInt(dados[2]);
            Sender snd = this.conns.get(portadestino);
            Estado e = snd.getEstado();
            e.setSQN(getSQN(p)+1);
            e.setProntoAtransferir(true);
            e.setFilename(filename);
            e.setFsize(size);
            e.setNofpackets(npacotes);
            noResponse.signalAll();
        }
        finally {
            l.unlock();
        }
    }

    public void sendResponse(DatagramPacket p) {
        l.lock();
        try {
            String path = getDados(p);
            int portadestino = getPortaOrigem(p);
            Estado e = (this.conns.get(portadestino)).getEstado();
            preparetransferFile(path,e);
            StringBuilder conteudo = new StringBuilder();
            String s = (conteudo.append(e.getFilename()).append(";").append(e.getFsize()).append(";").append(e.getNofpackets())).toString();
            byte dados[] = s.getBytes();
            e.setSQN(e.getSQN()+1);
            PDU pacote = new PDU(e.getSQN(), e.getPortaorigem(), portadestino,e.getNofpackets(), dados, dados.length, 7, e.getIp());
            DatagramPacket response = pacote.formaPacote();
            e.setEsperaACK(e.getSQN()+1);
            e.colocaParaEnvio(response);
            // Espera pela ACK para iniciar transferencia
            while ((e.getEsperaACK())!=(e.getRecebeuACK())) {
                try {
                    noACK.await();
                } catch (InterruptedException ex) { }
            }
            e.setRecebeuACK(0);
            e.setEsperaACK(0);
            transferFile(path,e);
            System.out.println("Transferencia Concluida!");
        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            l.unlock();
        }
    }

    public DatagramPacket recebeuPacote() {
        l.lock();
        DatagramPacket dp = null;
        try {
            while (qsize == 0)
                notEmpty.await();
            dp = pacotesrecebidos.remove();
            qsize -= 1;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            l.unlock();
            return dp;
        }
    }

    public void colocaPacote(DatagramPacket p) {
        l.lock();
        try {
            this.pacotesrecebidos.add(p);
            qsize += 1;
            notEmpty.signalAll();
        } finally {
            l.unlock();
        }
    }

    public void recebeuACK(DatagramPacket pacote)
    {
        l.lock();
        try {
            int sqn = getSQN(pacote);
            Estado e = (this.conns.get(getPortaOrigem(pacote))).getEstado();
            e.setSQN(sqn+1);
            e.setRecebeuACK(sqn);
            noACK.signalAll();
        }
        finally {
            l.unlock();
        }
    }

    public static byte[] myIntToByteArray4(int value)
    {
        return new byte[] {
                (byte)(value >> 24),
                (byte)(value >> 16),
                (byte)(value >> 8),
                (byte)value};
    }

    public void preparetransferFile(String path, Estado e) {
        l.lock();
        try {
            File f = new File(path);
            long fsize = f.length();
            String dir[] = path.split("\\\\");
            String filename = dir[dir.length - 1];
            int nofpackets = (int)(Math.ceil(((int) fsize) / packetsize)) + 1;
            e.setNofpackets(nofpackets);
            e.setFilename(filename);
            e.setFsize(fsize);
        }
        finally {
            l.unlock();
        }
    }

    public void transferFile(String s, Estado e) throws IOException {
        l.lock();
        File f = new File(s);
        BufferedInputStream bis = null;
        try {
            long fsize = e.getFsize();
            int nofpackets = e.getNofpackets();
            int wsize = e.getJanela();
            int ack;
            int read = 0;
            ArrayList<DatagramPacket> buffpacotes = new ArrayList<>();
            bis = new BufferedInputStream(new FileInputStream(f));
            int sqn = e.getSQN();
            int sqnPacote = sqn;
            int i=0;
            while(i<nofpackets) {
                /* Le e guarda os pacotes criados se necessario */
                while (buffpacotes.size()<wsize && i<nofpackets) {
                    byte[] buff;
                    if (i == (nofpackets - 1)) {
                        int tam = (int) fsize - read;
                        buff = new byte[tam];
                    } else
                        buff = new byte[packetsize];
                    read += bis.read(buff, 0, buff.length);
                    PDU pacote = new PDU(sqnPacote, e.getPortaorigem(), e.getPortadestino(), e.getNofpackets(), buff, buff.length,
                            4, e.getIp());
                    DatagramPacket p = pacote.formaPacote();
                    buffpacotes.add(p);
                    sqnPacote+=1;
                    i++;
                }
                e.setRecebeuACK(0);
                /* Envia os pacotes */
                for (int j = 0; j < wsize && j<buffpacotes.size(); j++) {
                    DatagramPacket p = buffpacotes.get(j);
                    e.colocaParaEnvio(p);
                    e.setEsperaACK(getSQN(p)+1);
                }
                /* Espera ACK */
                while ((e.getRecebeuACK())==0) {
                    try {
                        noACK.await();
                    } catch (InterruptedException ex) { }
                }
                /* Verifica valor do ACK */
                ack = e.getRecebeuACK();
                if (ack==e.getEsperaACK()) {
                    buffpacotes = new ArrayList<>();
                    wsize += 3;
                }
                else {
                    /* Remover os pacotes que foram enviados com sucesso */
                    for(int k=0; k<buffpacotes.size();k++) {
                        DatagramPacket p = buffpacotes.get(k);
                        if (getSQN(p)==ack) break;
                        else buffpacotes.remove(k);
                    }
                    wsize = 1;
                }
                //i = ack-sqn;
            }
        }
        finally {
            if (bis != null)
                bis.close();
            l.unlock();
        }
    }

    public void guardaDados(DatagramPacket p)
    {
        l.lock();
        try {
            Estado e = this.conns.get(getPortaOrigem(p)).getEstado();
            e.putPacoteDoFicheiro(p,getSQN(p));
            e.setRecebidos(e.getRecebidos()+1);
            notInWindowSize.signalAll();
        }
        finally{
            l.unlock();
        }

    }

    public void receiveFile(Estado e) {
        l.lock();
        FileOutputStream fos = null;
        try {
            int nofpackets = e.getNofpackets();
            int sqn = e.getSQN()+1;
            int wsize = e.getJanela();
            int i = 0;
            fos = new FileOutputStream(e.getFilename());
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            while(i < nofpackets) {
                /* Esperar receber pacotes */
                while(e.getRecebidos()<wsize) {
                    if (!(notInWindowSize.await(1, TimeUnit.SECONDS))) break;
                }
                /* Buscar os pacotes recebidos e escrever para ficheiro */
                int iteracoes = 0;
                while (e.containsPacote(sqn)) {
                    byte data[] = e.takePacoteDoFicheiro(sqn).getData();
                    int dataSize = ByteBuffer.wrap(Arrays.copyOfRange(data,16,20)).getInt();
                    byte conteudo[] = Arrays.copyOfRange(data,20,dataSize+20);
                    bos.write(conteudo,0,conteudo.length);
                    bos.flush();
                    sqn += 1;
                    iteracoes++;
                    i++;
                }
                /* Ajustar a janela */
                if (e.getRecebidos() == wsize && iteracoes == wsize) wsize += 3;
                else wsize = 1;
                /* Enviar ACK */
                e.setRecebidos(0);
                DatagramPacket p = (new PDU(sqn, e.getPortaorigem(), e.getPortadestino(), 1, null, 0, 3, e.getIp())).formaPacote();
                e.colocaParaEnvio(p);
            }
            bos.close();
            fos.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {
            l.unlock();
        }
    }
}
