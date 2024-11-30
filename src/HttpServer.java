import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpServer {

    // Definimos el puerto
    public static final int port = 8000;
    // Definimos el socket
    ServerSocket serverSocket;

    // Creamos el constructor
    public HttpServer() throws Exception{

        System.out.println("----- Servidor HTTP -----");
        System.out.println("Iniciando el Servidor...");
        this.serverSocket = new ServerSocket(port);
        System.out.println("Servidor iniciado en el puerto "+serverSocket.getLocalPort());
        System.out.println("Esperando conexión de cliente...");

        while(!Thread.currentThread().isInterrupted()){

            Socket accept = serverSocket.accept();
            // Definimos la alberca de hilos

            ExecutorService threadPool = Executors.newFixedThreadPool(20);
            threadPool.execute(new Handler(accept));
        }
    }


    // Función principal y creamos una instancia del server
    public static void main(String[] args) throws Exception {

        // Creamos una instancia del objeto
        HttpServer server = new HttpServer();
    }



}
