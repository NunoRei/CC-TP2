public class AgenteUDP {
    public static void main(String[] args)
    {
        Thread recebe = new Thread(new Receiver(Integer.parseInt(args[0])));
        recebe.start();
        Thread envia = new Thread(new Sender(Integer.parseInt(args[1])));
        envia.start();
    }
}
