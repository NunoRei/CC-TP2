import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32;

/**
 * Classe que implementa os métodos e algoritmos necessários para a transferência fiável de ficheiros sobre o UDP.
 * Contém Map de conexões, variáveis de condição para esperar mensagens e lista de pacotes recebidos.
 * Tem ainda uma classe privada PDU que adiciona o cabecalho, e forma o DatagramPacket com este e com os dados a serem enviados.
 */

public class ReliableTransfer {

        /** Porta, Thread que envia. A thread contém a tabela de estado */
        private Map<Integer,Sender> conns = new HashMap<>();
        /** Array de ips conectados */
        private ArrayList<InetAddress> ipsconn = new ArrayList<>();
        /** Lock */
        private Lock l = new ReentrantLock();
        /** Variáveis de Condição  */
        private Condition notEmpty = l.newCondition();
        private Condition notInWindowSize = l.newCondition();
        private Condition noSYNACK = l.newCondition();
        private Condition noACK = l.newCondition();
        private Condition noResponse = l.newCondition();
        private Condition noFIN = l.newCondition();
        /** checksum CRC32 */
        private static CRC32 checksum = new CRC32();
        /** Tamanho da janela */
        private int qsize = 0;
        /** Tamanho definido para payload */
        private static int packetsize = 1476;
        /** Tamanho máximo da janela */
        private static int MAX_Wsize = 150000;
        /** Fila de pacotes recebidos, a ser preenchida pela Thread Receiver */
        private Queue<DatagramPacket> pacotesrecebidos = new LinkedList<>();

        /**
         * Classe que forma pacotes.
         * Adiciona número de sequência, porta de origem, tipo, checksum (algoritmo CRC), tamanho da payload e payload.
         */
        private class PDU {

            /* PDU [ SQN | portaorigem | tipo | checksum | length | dados ] */
            /** Número de sequência do pacote */
            private int sequencenumber; // 4 bytes
            /** Porta de Origem */
            private int portaorigem; // 4 bytes
            /** Ip de destino */
            private InetAddress ipdestino;
            /** Porta de destino */
            private int portadestino; // 4bytes
            /** Payload */
            private byte[] dados;
            /** Tamanho da Payload */
            private int length; // 4bytes
            /** Tipo do Pacote */
            private int tipo; // 1-SYN 2-SYN+ACK 3-ACK 4-Data 5-GET 6-PUT 7-RESPONSE(GET) 8-FIN 9-RESPONSE(FIN) // 4 bytes

            /**
             * Construtor parametrizado da Classe PDU.
             * @param sq Número de Sequência.
             * @param po Porta de Origem.
             * @param pd Porta de Destino.
             * @param data Dados do pacote.
             * @param leng Tamanho dos dados.
             * @param tipo Tipo do pacote.
             * @param ip ip do destino.
             */
            public PDU(int sq, int po, int pd, byte[] data, int leng, int tipo, InetAddress ip) {
                this.sequencenumber = sq;
                this.portaorigem = po;
                this.portadestino = pd;
                this.dados = new byte[length];
                if(data == null)
                    this.dados = "".getBytes();
                else this.dados = data;
                this.length = leng;
                this.tipo = tipo;
                this.ipdestino = ip;
            }

            /**
             * Forma o pacote a ser enviado através das variáveis da classe.
             * @return DatagramPacket pronto a ser enviado.
             */
            public DatagramPacket formaPacote() {

                DatagramPacket dpacket = null;

                long checksumValue = calculateCRC(this.dados);

                byte[] bsqn = ByteBuffer.allocate(4).putInt(this.sequencenumber).array();
                byte[] bpo = ByteBuffer.allocate(4).putInt(this.portaorigem).array();
                byte[] btipo =  ByteBuffer.allocate(4).putInt(this.tipo).array();
                byte[] datalength =  ByteBuffer.allocate(4).putInt(this.length).array();
                byte[] checksumBytes = ByteBuffer.allocate(8).putLong(checksumValue).array();

                byte[] pacote;

                try {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    os.write(bsqn);
                    os.write(bpo);
                    os.write(btipo);
                    os.write(checksumBytes);
                    os.write(datalength);
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

        /**
        * Obter o número de sequência de um DatagramPacket.
        * @param pacote DatagramPacket.
        * @return número de seqência do pacote DatagramPacket.
        */
        public int getSQN(DatagramPacket pacote) {
             return ByteBuffer.wrap(Arrays.copyOfRange(pacote.getData(), 0, 4)).getInt();
        }

        /**
        * Obter porta de origem de um DatagramPacket.
        * @param pacote DatagramPacket.
        * @return porta de origem do pacote DatagramPacket.
        */
        public int getPortaOrigem(DatagramPacket pacote) {
            return ByteBuffer.wrap(Arrays.copyOfRange(pacote.getData(), 4, 8)).getInt();
        }

        /**
        * Obter o tipo do pacote.
        * @param pacote DatagramPacket.
        * @return tipo do pacote.
        */
        public int getTipo(DatagramPacket pacote) {
            return ByteBuffer.wrap(Arrays.copyOfRange(pacote.getData(), 8, 12)).getInt();
        }

        /**
        * Obter o checksum do pacote em array de bytes.
        * @param pacote DatagramPacket.
        * @return array de bytes do checksum.
        */
        public byte[] getCheckSumValue(DatagramPacket pacote) {
            return ByteBuffer.wrap(Arrays.copyOfRange(pacote.getData(), 12, 20)).array();
        }

        /**
        * Obter o tamanho da payload do pacote.
        * @param pacote DatagramPacket.
        * @return tamanho da payload.
        */
        public int getTamanho(DatagramPacket pacote) {
            return ByteBuffer.wrap(Arrays.copyOfRange(pacote.getData(), 20, 24)).getInt();
        }

        /**
        * Obter Payload do pacote em bytes.
        * @param pacote DatagramPacket.
        * @return array de bytes com a payload do pacote.
        */
        public byte[] getDataBytes(DatagramPacket pacote) {
            int tamanho = getTamanho(pacote);
            return ByteBuffer.wrap(Arrays.copyOfRange(pacote.getData(), 24, tamanho+24)).array();
        }

        /**
        * Obter payload do pacote em formato String.
        * @param pacote DatagramPacket.
        * @return String com payload do pacote.
        */
        public String getDados(DatagramPacket pacote) {
            try {
                String dados = new String(Arrays.copyOfRange(pacote.getData(), 24, pacote.getData().length), "UTF-8");
                dados = dados.replace("\0", "");
                return dados;
            } catch (Exception e) {
                return "";
            }
        }

    /**
     * Calcula o checksum a ser inserido no pacote, e a ser verificado na receção do pacote com o auxílio da biblioteca CRC32.
     * @param payload bytes com os dados a ser enviados no pacote.
     * @return Valor calculado para o checksum.
     */
    private long calculateCRC(byte[] payload)
        {
            l.lock();
            try {
                checksum.update(payload);
                return checksum.getValue();
            }
            finally {
                checksum.reset();
                l.unlock();
            }
        }

    /**
     * Envia SYN e espera SYN+ACK para estabelecer a conexao.
     * @param ip do destino
     * @param portadestino porta do destino.
     * @param portaorigem porta da origem.
     * @return 0 caso se tenha conectado correctamente.
     */
        public int estabeleceConexao(InetAddress ip, int portadestino, int portaorigem)
        {
            l.lock();
            try {
                Estado e = new Estado(portaorigem, portadestino, ip);
                Sender snd = new Sender(e);
                long timebefore, timeafter;
                conns.put(portadestino, snd);
                new Thread(snd).start();
                // Enviar SYN
                DatagramPacket p = (new PDU(e.getSQN(), portaorigem, portadestino, null, 0, 1, ip)).formaPacote();
                timebefore = System.currentTimeMillis();
                e.colocaParaEnvio(p);
                // Esperar SYNACK
                try {
                    while (!(e.isConectado())) {
                        noSYNACK.await();
                    }
                    timeafter = System.currentTimeMillis();
                    e.setRTT(timeafter-timebefore);
                }
                catch (Exception exc) {

                }
                return 0;
            }
            finally {
                l.unlock();
            }
        }

    /**
     * Depois de receber SYN, envia SYNACK e espera pelo ACK .
     * @param pacote DaatagramPacket com o SYN.
     * @param po porta de origem.
     * @return 0 caso tenha aceite a conexao com sucesso.
     */
        public int aceitaConexao(DatagramPacket pacote, int po)
        {
            l.lock();
            try {
                int portadestino = getPortaOrigem(pacote);
                int portaorigem = po;
                InetAddress ip = pacote.getAddress();
                int sqn = getSQN(pacote)+1;
                long tbefore,tafter;
                Estado e = new Estado(portaorigem, portadestino, ip);
                e.setSQN(sqn);
                Sender snd = new Sender(e);
                conns.put(portadestino, snd);
                new Thread(snd).start();
                // enviar SYNACK
                e.setEsperaACK(sqn+1);
                DatagramPacket p = (new PDU(sqn, portaorigem, portadestino, null, 0, 2, ip)).formaPacote();
                tbefore=System.currentTimeMillis();
                e.colocaParaEnvio(p);
                // esperar ACK
                while ((e.getEsperaACK())!=(e.getRecebeuACK())) {
                    try {
                        noACK.await();
                    } catch (InterruptedException ex) { }
                }
                tafter= System.currentTimeMillis();
                e.setRTT(tafter-tbefore);
                e.setRecebeuACK(0);
                e.setEsperaACK(0);
                return 0;
            }
            finally {
                l.unlock();
            }
        }

    /**
     * Responsável por enviar um ACK caso tenha recebido o SYNACK.
     * @param synack Pacote com o SYNACK.
     */
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
                DatagramPacket p = (new PDU(e.getSQN(), e.getPortaorigem(), portadestino, null, 0, 3, e.getIp())).formaPacote();
                e.colocaParaEnvio(p);
            }
            finally {
                l.unlock();
            }
        }

    /**
     * Depois de recebido um pacote, este método é invocado. Verifica o checksum e rejeita o pacote caso esteja em erro.
     * Se o pacote for do tipo SYNACK, ACK, Resposta a um GET ou resposta a um FIN, esta thread toma acção imediata para
     * de forma a que os métodos que esperavam estas respostas possam retomar execução.
     * @param p pacote recebido pela Thread Receiver.
     */
    public void filtraPacote(DatagramPacket p)
        {
            l.lock();
            try {

                byte[] checksumReceived = getCheckSumValue(p);
                long valueReceived = ByteBuffer.wrap(checksumReceived).getLong();
                long valueCalculated = calculateCRC(getDataBytes(p));

                if (valueCalculated==valueReceived)
                {
                    int tipo = getTipo(p);
                    if (tipo == 2) recebeSYNACK(p);
                    else if (tipo == 3) recebeuACK(p);
                    else if (tipo == 4) guardaDados(p);
                    else if (tipo == 7) respostaAoGET(p);
                    else if (tipo==9) recebeuFINResponse(p);
                    else colocaPacote(p);
                }
                else {
                    System.out.println("Pacote Rejeitado");
                }
            }
            finally {
                l.unlock();
            }
        }

    /**
     * Envia um GET para um ficheiro, e espera pela resposta.
     * Quando recebida a resposta, envia um ACK e o processo de receção do ficheiro começa com a invocação do método receiveFile.
     * @param request diretoria do ficheiro completa.
     * @param pdestino porta do destinatário.
     */
        public void fileRequest(String request,int pdestino) {
            l.lock();
            try {
                Estado e = (this.conns.get(pdestino)).getEstado();
                byte dados[] = request.getBytes();
                e.setSQN(e.getSQN()+1);
                PDU pacote = new PDU(e.getSQN(), e.getPortaorigem(), pdestino, dados, dados.length, 5, e.getIp());
                DatagramPacket p = pacote.formaPacote();
                e.colocaParaEnvio(p);
                /* Esperar Response */
                while (!(e.isProntoAtransferir())) {
                    try {
                        noResponse.await();
                    } catch (InterruptedException ex) { }
                }
                /* Enviar ACK e Esperar dados do ficheiro */
                p = (new PDU(e.getSQN(), e.getPortaorigem(), pdestino, null, 0, 3, e.getIp())).formaPacote();
                e.colocaParaEnvio(p);
                receiveFile(e);
                e.clearEstado();
                System.out.println("Transferencia Concluida!");
            }
            finally {
                l.unlock();
            }
        }

    /**
     * Envia um PUT e espera pelo ACK para começar o upload do ficheiro, através da invocação do método transferFile.
     * @param request caminho completo do ficheiro.
     * @param pdestino porta do destinatário.
     */
        public void requestToSend(String request,int pdestino)
        {
            l.lock();
            try {
                Estado e = (this.conns.get(pdestino)).getEstado();
                preparetransferFile(request,e);
                StringBuilder conteudo = new StringBuilder();
                String s = (conteudo.append(e.getFilename()).append(";").append(e.getFsize()).append(";").append(e.getNofpackets())).toString();
                byte dados[] = s.getBytes();
                e.setSQN(e.getSQN()+1);
                PDU pacote = new PDU(e.getSQN(), e.getPortaorigem(), pdestino, dados, dados.length, 6, e.getIp());
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
                transferFile(request,e);
                e.clearEstado();
                System.out.println("Transferencia Concluida!");
            }
            catch (Exception ex) {

            }
            finally {
                l.unlock();
            }
        }

    /**
     * Método que envia ACK indicado que está pronto a receber ficheiro, e invoca o método receiveFile para o começar a receber.
     * @param p Pacote com o PUT.
     */
    public void readyToReceive(DatagramPacket p)
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
                // Enviar ACK e Esperar dados do ficheiro
                p = (new PDU(e.getSQN(), e.getPortaorigem(), portadestino, null, 0, 3, e.getIp())).formaPacote();
                e.colocaParaEnvio(p);
                receiveFile(e);
                e.clearEstado();
                System.out.println("Transferencia Concluida!");
            }
            finally {
                l.unlock();
            }
        }

    /**
     * Atualiza os campos do estado com a informação do ficheiro que será recebido, e sinaliza que está pronto a receber.
     * @param p Pacote com o GET.
     */
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

    /**
     * Envia resposta ao GET com a informação do ficheiro que lhe irá enviar, tamanho, nome e número de pacotes.
     * Espera ACK para iniciar transferência, e depois invoca o método transferFile para começar a transferência.
     * @param path Caminho completo do ficheiro.
     * @param portadestino porta do destinatário.
     */
        public void sendResponse(String path, int portadestino) {
            l.lock();
            try {
                Estado e = (this.conns.get(portadestino)).getEstado();
                preparetransferFile(path,e);
                StringBuilder conteudo = new StringBuilder();
                String s = (conteudo.append(e.getFilename()).append(";").append(e.getFsize()).append(";").append(e.getNofpackets())).toString();
                byte dados[] = s.getBytes();
                e.setSQN(e.getSQN()+1);
                PDU pacote = new PDU(e.getSQN(), e.getPortaorigem(), portadestino, dados, dados.length, 7, e.getIp());
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
                e.clearEstado();
                System.out.println("Transferencia Concluida!");
            } catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                l.unlock();
            }
        }

    /**
     * Método que obtém pacotes da fila de pacotes recebidos.
     * Se a fila estiver vazia espera pelo signal.
     * @return pacote retirado da primeira posição da fila de pacotes recebidos.
     */
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

    /**
     * Coloca pacote na fila de pacotes recebidos.
     * Sinaliza a thread que está a espera de pacotes.
     * @param p Pacote a ser colocado na fila de pacotes recebidos.
     */
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

    /**
     * Sinaliza a thread que estava a espera do ack que o recebeu.
     * Coloca o valor do ack no estado para posteriormente ser comparado com o valor do ack que seria esperado.
     * @param pacote com o ACK.
     */
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

    /**
     * Atualiza os campos nome do ficheiro, tamanho e número de pacotes no estado para preparar a transferência do ficheiro.
     * @param path caminho completo do ficheiro.
     * @param e Estado da transferência.
     */
        public void preparetransferFile(String path, Estado e) {
            l.lock();
            try {
                File f = new File(path);
                long fsize = f.length();
                String dir[] = path.split("/");
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

    /**
     * Responsável pelo algoritmo de transferência de ficheiro.
     * Lê e cria pacotes adicionando-os ao buffer de envio para serem enviados consoante o tamanho da janela.
     * Consoante o ack recebido ajusta a janela e remove pacotes que foram enviados com sucesso.
     * A primeira posição do buffer de envio é alterada consoante o valor do ack recebido por isso um pacote que estivesse em erro
     * ou perdido seria reenviado pois a janela cai para 1, e o buffer é atualizado de forma a manter na primeira posição o pacote que
     * tem de ser retransmitido.
     * @param s caminho completo do ficheiro.
     * @param e Estado da transferência.
     * @throws IOException
     */
        public void transferFile(String s, Estado e) throws IOException {
            l.lock();
            File f = new File(s);
            BufferedInputStream bis = null;
            try {
                long fsize = e.getFsize();
                int nofpackets = e.getNofpackets();
                int additive = 0;
                int wsize = e.getJanela();
                int ack;
                int read = 0;
                ArrayList<DatagramPacket> buffpacotes = new ArrayList<>();
                bis = new BufferedInputStream(new FileInputStream(f));
                int sqn = e.getSQN();
                int sqnPacote = sqn;
                int i=0;
                while(i<nofpackets) {
                    /* Le se necessario e guarda os pacotes criados */
                    while (buffpacotes.size()<wsize && i<nofpackets) {
                        byte[] buff;
                        if (i == (nofpackets - 1)) {
                            int tam = (int) fsize - read;
                            buff = new byte[tam];
                        } else
                            buff = new byte[packetsize];
                        read += bis.read(buff, 0, buff.length);
                        PDU pacote = new PDU(sqnPacote, e.getPortaorigem(), e.getPortadestino(), buff, buff.length,
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
                        e.setEsperaACK(getSQN(p)+1);
                        e.colocaParaEnvio(p);
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
                        if(wsize<MAX_Wsize) {
                            additive+=1;
                            wsize+=additive;
                        }
                    }
                    else {
                        additive=0;
                        wsize = 1;
                    }
                    /* Remover os pacotes que foram enviados com sucesso */
                    buffpacotes = removerJaEnviados(buffpacotes,ack);
                }
            }
            finally {
                if (bis != null)
                    bis.close();
                l.unlock();
            }
        }

    /**
     * Remove do buffer de pacotes a enviar todos os pacotes com número menor do que o ack que tinha sido recebido.
     * Desta forma na primeira posição encontrar-se à o pacote que tem que ser reenviado, ou caso não tudo tenha corrido bem
     * resultará um buffer vazio.
     * @param pacotes buffer de pacotes para envio.
     * @param sqn número do ack que foi recebido.
     * @return Buffer atualizado.
     */
        private ArrayList<DatagramPacket> removerJaEnviados(ArrayList<DatagramPacket> pacotes, int sqn)
        {
            ArrayList<DatagramPacket> result = new ArrayList<>();
            for (DatagramPacket p : pacotes) {
                if (getSQN(p)>=sqn) result.add(p);
            }
            return result;
        }

    /**
     * Adiciona pacote de dados de ficheiro ao buffer de pacotes recebidos na transferência.
     * @param p Pacote com dados para serem escritos em ficheiro.
     */
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

    /**
     * Responsável pelo algoritmo de receção de ficheiro.
     * Vai esperar receber pacotes com dados do ficheiro consoante o tamanho da janela antes de enviar um ACK, mas dá timeout caso
     * espera 3 vezes o RTT calculado no inicio da conexão.
     * Depois de recebidos pacotes vai verficar a sua ordenação através do número que está a espera, verificando se tem nos pacotes
     * recebidos o pacote com o número que está a espera. Se encontrar escreve para ficheiro, remove do buffer e verifica se tem o proximo, e assim sucessivamente.
     * Caso não tenha recebido, envia ACK com o número do pacote que estava à espera receber.
     * A janela é ajustada consoante tenha recebido todos os pacotes que esperava ou não. Se sim incrementa através do additive increase, senão
     * cai para 1 e recomeça o slow start.
     * @param e
     */
    public void receiveFile(Estado e) {
            l.lock();
            FileOutputStream fos = null;
            try {
                int nofpackets = e.getNofpackets();
                int sqn = e.getSQN()+1;
                int additive = 0;
                int wsize = e.getJanela();
                int i = 0;
                fos = new FileOutputStream(e.getFilename());
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                while(i < nofpackets) {
                    /* Esperar receber pacotes */
                    while(e.getRecebidos()<wsize) {
                        if (!(notInWindowSize.await(3*e.getRTT(), TimeUnit.MILLISECONDS))) break;
                    }
                    /* Busca os pacotes recebidos e escrever para ficheiro */
                    int iteracoes = 0;
                    while (e.containsPacote(sqn)) {
                        byte data[] = e.takePacoteDoFicheiro(sqn).getData();
                        int dataSize = ByteBuffer.wrap(Arrays.copyOfRange(data,20,24)).getInt();
                        byte conteudo[] = Arrays.copyOfRange(data,24,dataSize+24);
                        bos.write(conteudo,0,conteudo.length);
                        bos.flush();
                        sqn += 1;
                        iteracoes++;
                        i++;
                    }
                    /* Ajustar a janela */
                    if (e.getRecebidos() == wsize && iteracoes == wsize) {
                        if (wsize < MAX_Wsize)
                            additive+=1;
                            wsize+=additive;
                    }
                    else {
                        additive=0;
                        wsize = 1;
                    }
                    /* Enviar ACK */
                    e.setRecebidos(0);
                    DatagramPacket p = (new PDU(sqn, e.getPortaorigem(), e.getPortadestino(), null, 0, 3, e.getIp())).formaPacote();
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

    /**
     * Terminar uma conexão, enviando FIN e esperando o ACK e o FIN para enviar o último ack e remover a thread sender das conexões.
     * @param pdestino porta de destino
     */
    public void terminaConexao(int pdestino)
        {
            l.lock();
            try {
                Estado e = this.conns.get(pdestino).getEstado();
                /* Enviar FIN */
                int sqn = e.getSQN()+1;
                e.setSQN(sqn+1);
                DatagramPacket p = (new PDU(e.getSQN(),e.getPortaorigem(),e.getPortadestino(),null,0,8,e.getIp())).formaPacote();
                e.setEsperaACK(e.getSQN());
                e.colocaParaEnvio(p);
                /* Esperar ACK */
                while(e.getRecebeuACK()==0) {
                    if(!(noACK.await(3*e.getRTT(),TimeUnit.MILLISECONDS))) break;
                    /* Reenviar FIN */
                }
                /* Wait FIN */
                int timeout = 0;
                while(e.isConectado()) {
                    if (!(noFIN.await(3*e.getRTT(),TimeUnit.MILLISECONDS))) timeout+=1;
                    if(timeout==3) break;
                }
                /* Enviar ACK */
                p = (new PDU(e.getSQN(),e.getPortaorigem(),e.getPortadestino(),null,0,3,e.getIp())).formaPacote();
                e.colocaParaEnvio(p);
                conns.remove(pdestino);
                System.out.println("Conexao Terminada.");
            }
            catch (Exception ex) {}
            finally {
                l.unlock();
            }
        }

    /**
     * Sinaliza que recebeu a resposta ao FIN.
     * @param p pacote com resposta ao FIN.
     */
    public void recebeuFINResponse(DatagramPacket p) {
            l.lock();
            try {
                Estado e = this.conns.get(getPortaOrigem(p)).getEstado();
                int sqn = getSQN(p);
                e.setSQN(sqn+1);
                sqn = e.getSQN();
                e.setConectado(false);
                noFIN.signalAll();
            }
            finally {
                l.unlock();
            }
        }

    /**
     * Responde ao FIN, enviando ACK, desconectando-se e enviando um FIN, e esperando o último ACK.
     * @param p Pacote com FIN.
     */
    public void recebeuFINRequest(DatagramPacket p) {
            l.lock();
            try {
                Estado e = this.conns.get(getPortaOrigem(p)).getEstado();
                int sqn = getSQN(p);
                e.setSQN(sqn+1);
                sqn = e.getSQN();
                /* Enviar ACK */
                DatagramPacket packet = (new PDU(sqn, e.getPortaorigem(), e.getPortadestino(), null, 0, 3, e.getIp())).formaPacote();
                e.colocaParaEnvio(packet);
                /* Enviar FIN */
                e.setSQN(sqn+1);
                sqn = e.getSQN();
                packet = (new PDU(sqn, e.getPortaorigem(), e.getPortadestino(), null, 0, 9, e.getIp())).formaPacote();
                /* Espera ACK */
                e.setEsperaACK(sqn+1);
                while(e.getRecebeuACK()==0) {
                    if(!(noACK.await(3*e.getRTT(),TimeUnit.MILLISECONDS))) break;
                    /* Reenviar FIN */
                    e.colocaParaEnvio(packet);
                }
                System.out.println("Conexao Terminada");
            }
            catch (Exception ex) {}
            finally {
                l.unlock();
            }
        }
}
