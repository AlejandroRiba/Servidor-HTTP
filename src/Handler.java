import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.StringTokenizer;

public class Handler extends Thread {

    // Declaramos los objetos
    protected Socket socket;
    protected BufferedOutputStream bos;
    protected InputStream is;
    protected OutputStream os;

    protected DataInputStream dis;
    protected BufferedReader reader;

    protected String absolute_path = "/home/alejandroriba/IdeaProjects/Servidor HTTP/resources";//Ruta provisional
    //Cambiar para windows

    // Declaramos el constructor
    public Handler(Socket socket){
        this.socket = socket;
    }

    public void run() {

        try {
            is = socket.getInputStream();
            os = socket.getOutputStream();
            reader = new BufferedReader(new InputStreamReader(is));
            bos = new BufferedOutputStream(os);
            dis = new DataInputStream(socket.getInputStream());

            byte[] b = new byte[26*1024];
            int request_size = dis.read(b);

            if(request_size != -1) {
                String fullRequest = new String(b, 0, request_size);
                int endHeader = fullRequest.indexOf("\r\n\r\n");

                if (endHeader >= 0) {
                    endHeader += 4; // aumentamos 4 para pasar "\r\n\r\n"
                    String header = fullRequest.substring(0, endHeader - 2);
                    System.out.println(header);

                    // Obtenemos el request
                    String request = header.split("\n")[0].trim();
                    System.out.println(request);

                    // Extraer el valor de Content-Length si existe
                    int contentLength = 0;
                    String[] lines = header.split("\r\n");
                    for (String line : lines) {
                        if (line.startsWith("Content-Length:")) {
                            contentLength = Integer.parseInt(line.split(":")[1].trim());
                            break;
                        }
                    }

                    // Leer el cuerpo de la solicitud si existe Content-Length
                    String body = "";
                    byte[] bodyBytes = new byte[contentLength];
                    if (contentLength > 0) {
                        System.arraycopy(b, endHeader, bodyBytes, 0, request_size - endHeader);
                        int bytesRead = request_size - endHeader;
                        while (bytesRead < contentLength) {
                            int read = dis.read(bodyBytes, bytesRead, contentLength - bytesRead);
                            if (read == -1) break; // End of stream
                            bytesRead += read;
                        }
                        body = new String(bodyBytes, StandardCharsets.UTF_8);
                    }

                    // GET
                    if (request.contains("GET")) {
                        getHandler(request);
                    }

                    // POST
                    else if (request.contains("POST")) {
                        postHandler(body);
                    }

                    // PUT
                    else if (request.contains("PUT")) {

                        if (bodyBytes.length <= 25000000)
                            putHandler(bodyBytes, request);

                        else
                            sendError("413", "Request entity too large");
                    }

                    // HEAD
                    else if (request.contains("HEAD")) {

                        System.out.println("Respuesta\nCodigo: 200 Ok\n");
                        String head = "";
                        head = head + "HTTP/1.0 200 Ok\n";
                        head = head + "Server: Server RV :)\n";
                        head = head + "Date: " + new Date() + "\n";
                        head = head + "Location: http://localhost:8000/\n";
                        head = head + "Keep-Alive: timeout=5, max=100\n";
                        head = head + "Connection: Keep-Alive\n";
                        head = head + "\n";
                        os.write(head.getBytes());
                        os.flush();

                    } else {

                        String httpResponse = "HTTP/1.1 501 Not implemented\r\n\r\n";
                        os.write(httpResponse.getBytes());
                        os.flush();
                    }
                } else {
                    System.err.println("Invalid request format");
                    sendError("400", "Bad Request");
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    // Función GET
    private void getHandler(String request) throws IOException {

        // Contiene parámetros
        if (request.contains("?")) {

            int index_params = request.indexOf("?") + 1;
            String params = request.substring(index_params, request.indexOf(" ", index_params));

            System.out.println("Los parámetros dados son: " + params);

            // Divimos la cadena por medio de Tokens
            StringTokenizer tokens_params = new StringTokenizer(params, "&");
            sendParams(tokens_params, tokens_params.countTokens());

        } else {

            String fileName = getFileName(request);
            sendFile(fileName);
        }

    }

    // Función POST
    private void postHandler(String body) throws IOException {
        System.out.println("El cuerpo es: " + body);

        // Divimos la cadena por medio de Tokens
        StringTokenizer tokens_params = new StringTokenizer(body, "&");
        sendParams(tokens_params, tokens_params.countTokens());
    }

    // Función PUT
    private void putHandler(byte[] body_bytes, String request) throws IOException {
        String fileName = getFileName(request);

        if(!fileName.isEmpty()) {

            File file = new File(absolute_path, fileName);

            String httpResponse;
            if (file.exists())
                httpResponse = "HTTP/1.1 201 Actualizado\r\n\r\n";

            else
                httpResponse = "HTTP/1.1 202 Creado\r\n\r\n";

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(body_bytes);
            }

            System.out.println("Termino de enviarse el archivo");

            bos.write(httpResponse.getBytes(StandardCharsets.UTF_8));
            bos.flush();

        }else{

            sendError("404", "Not found");
        }
    }

    // Función para obtener el nombre del archivo
    private String getFileName(String statusLine) {

        String file = statusLine.substring(5);
        String source = file.substring(0, file.indexOf(" "));
        if (source.compareTo("") == 0 && statusLine.contains("GET"))
            source = "index.html";


        return source;
    }

    // Función que se encarga de enviar archivos
    public void sendFile(String file_) {

        // Asegura que el archivo se busca en el directorio correcto
        File file = new File(absolute_path, file_);
        if (!file.exists()) {
            System.err.println("Archivo no encontrado: " + file.getAbsolutePath());
            sendError("404", "Not found");
            return;
        }

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {

            long fileSize = file.length();
            String mime = Files.probeContentType(file.toPath());

            // Construcción de los encabezados HTTP y respuesta de la petición
            String responseHeaders = "HTTP/1.1 202 OK \r\n" +
                    "Server: Server RV :)\r\n" +
                    "Date: " + new Date() + "\r\n" +
                    "Content-Type: " + mime + "\r\n" +
                    "Content-Length: " + fileSize + "\r\n" +
                    "\r\n";

            System.out.println(responseHeaders);
            bos.write(responseHeaders.getBytes());
            bos.flush();

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            bos.flush();
        } catch (Exception e) {
            e.printStackTrace();
            sendError("403", "Forbidden");
        }
    }


    // Función que se encarga de enviar errores
    private void sendError(String code, String status) {

        File file = new File(absolute_path, code+".html");

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {

            long fileSize = file.length();
            String mime = Files.probeContentType(file.toPath());

            // Construcción de los encabezados HTTP y respuesta de la petición
            String responseHeaders = "HTTP/1.1 " + code + " " + status + "\r\n" +
                    "Server: Server RV :)\r\n" +
                    "Date: " + new Date() + "\r\n" +
                    "Content-Type: " + mime + "\r\n" +
                    "Content-Length: " + fileSize + "\r\n" +
                    "\r\n";


            bos.write(responseHeaders.getBytes());
            bos.flush();

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            bos.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Función para imprimir los parámetros
    private void sendParams(StringTokenizer tokens, int tokens_size) throws IOException {

        // Construcción de los encabezados HTTP y respuesta de la petición
        StringBuilder responseHeaders = new StringBuilder("HTTP/1.1 202 Parámetros enviados\r\n" +
                "Server: Server RV :)\r\n" +
                "Date: " + new Date() + "\r\n" +
                "\r\n" +
                "<html><head><title>SERVIDOR WEB</title></head><body>");

        //System.out.println(tokens.countTokens()+" afg");
        for (int i = 0; i < tokens_size; i++) {
            String param = tokens.nextToken();
            responseHeaders.append("<h3><b>").append(param).append("</b></h3>");
        }

        responseHeaders.append("</body></html>");

        bos.write(responseHeaders.toString().getBytes());
        bos.flush();

    }

}



