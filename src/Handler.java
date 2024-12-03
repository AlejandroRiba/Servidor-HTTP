import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.StringTokenizer;

public class Handler extends Thread {

    // Códigos de escape ANSI
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_GREEN = "\u001B[32m";

    // Declaramos los objetos
    protected Socket socket;
    protected BufferedOutputStream bos;
    protected InputStream is;
    protected OutputStream os;

    protected DataInputStream dis;
    protected BufferedReader reader;

    protected String absolute_path = "/home/alejandroriba/IdeaProjects/Servidor HTTP/resources";//Ruta provisional
    //Cambiar para windows

    //Variable auxiliar
    final static String CRLF = "\r\n";

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
                String fullRequest = new String(b, 0, request_size); //Convierte los bytes de b a String desde index = 0 a indez = request.size
                int endHeader = fullRequest.indexOf(CRLF+CRLF);  //Los headers deben cerrarse con doble CRLF '\r\n'

                if (endHeader >= 0) {
                    //System.out.println("Indice de endHeader " + endHeader);
                    endHeader += 4; // aumentamos 4 para matchear el indice final correcto "\r\n\r\n"
                    //System.out.println("Indice actualizado " + endHeader);
                    String header = fullRequest.substring(0, endHeader - 2); //Saca la subcadena con un solo CRLF
                    System.out.println("Encabezados: \n" + ANSI_GREEN + header + ANSI_RESET); //Encabezado actualizado - Imprime solicitud en otro color

                    // Obtenemos el request
                    String request = header.split(CRLF)[0].trim(); //Obtiene la primera línea separando las lineas en base a su CRLF
                    //System.out.println("request : " + request); //Imprime la solicitud aislada

                    // Extraer el valor de Content-Length si existe
                    int contentLength = 0;
                    String[] lines = header.split(CRLF); //Obtenemos todas las líneas
                    for (String line : lines) {
                        if (line.startsWith("Content-Length:")) {
                            contentLength = Integer.parseInt(line.split(":")[1].trim());
                            //System.out.println("contentLength encontrado: " + contentLength);
                            break;
                        }
                    }

                    // Leer el cuerpo de la solicitud si existe Content-Length
                    String body = "";
                    byte[] bodyBytes = new byte[contentLength]; //Arreglo de bytes de tamaño contentLength
                    if (contentLength > 0) {
                        System.arraycopy(b, endHeader, bodyBytes, 0, request_size - endHeader);
                        int bytesRead = request_size - endHeader;
                        while (bytesRead < contentLength) {
                            int read = dis.read(bodyBytes, bytesRead, contentLength - bytesRead);
                            if (read == -1) break; // End of stream
                            bytesRead += read;
                        }
                        body = new String(bodyBytes, StandardCharsets.UTF_8); //Se convierte a una cadena de texto usando la codificación UTF-8
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
                        headHandler(request);
                    }

                    //DELETE
                    else if (request.contains("DELETE")) {
                        deleteHandler(request);
                    }

                    //Método HTTP no reconocido
                    else {
                        String httpResponse = "HTTP/1.1 501 Not implemented" + CRLF + CRLF;
                        os.write(httpResponse.getBytes());
                        os.flush();
                    }
                } else { //En caso de formato invalido
                    System.err.println("Invalid request format");
                    sendError("400", "Bad Request");
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally { //liberación de recursos
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
            String mime = Files.probeContentType(file.toPath());

            String httpResponse;
            if (file.exists())
                httpResponse = "HTTP/1.1 201 Actualizado";

            else
                httpResponse = "HTTP/1.1 202 Creado";


            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(body_bytes);
            }

            System.out.println("Termino de enviarse el archivo");

            //Construyo la cabecera de respuesta hasta que termina de escribirse
            String responseHeaders = constructHeader(httpResponse, mime, file.length());

            bos.write(responseHeaders.getBytes(StandardCharsets.UTF_8));
            bos.flush();

        }else{
            sendError("404", "Not found");
        }
    }

    //Función HEAD
    private void headHandler(String request) throws IOException {
        // Contiene parámetros
        if (request.contains("?")) {

            int index_params = request.indexOf("?") + 1;
            String params = request.substring(index_params, request.indexOf(" ", index_params));

            // Divimos la cadena por medio de Tokens
            StringTokenizer tokens_params = new StringTokenizer(params, "&");
            String responseBody = construiCuerpoParam(tokens_params, tokens_params.countTokens());
            int contentLength = responseBody.getBytes().length;

            //Mandamos a crear la cabecera
            String responseHeaders = constructHeader("HTTP/1.1 202 Parámetros enviados", "text/html", contentLength);
            System.out.println("Response headers:\n" + ANSI_BLUE + responseHeaders + ANSI_RESET);
            bos.write(responseHeaders.getBytes());
            bos.flush();
        } else {

            String fileName = getFileName(request);
            // Asegura que el archivo se busca en el directorio correcto
            File file = new File(absolute_path, fileName);
            if (!file.exists()) {
                System.err.println("Archivo no encontrado: " + file.getAbsolutePath());
                sendError("404", "Not found");
                return;
            }

            long fileSize = file.length();
            String mime = Files.probeContentType(file.toPath());

            //Mandamos a crear la cabecera
            String responseHeaders = constructHeader("HTTP/1.1 202 OK", mime, fileSize);
            System.out.println("Response headers:\n" + ANSI_BLUE + responseHeaders + ANSI_RESET);
            bos.write(responseHeaders.getBytes());
            bos.flush();
        }
    }

    // Función DELETE
    private void deleteHandler(String request) throws IOException {
        String fileName = getFileName(request);
    }

    // Función para obtener el nombre del archivo
    private String getFileName(String statusLine) {
        // Divide la línea de estado en palabras (método, archivo, protocolo)
        //EJ. HEAD_/_HTTP/1.1  (LOS GUIONES BAJOS SON LOS ESPACIOS)
        String[] parts = statusLine.split(" ");

        // El archivo solicitado está en la segunda palabra (índice 1)
        String file = parts[1]; // Ejemplo: "/archivo.html"
        //System.out.println("statusline: " + statusLine);

        // Si no se especifica archivo o es "/", sirve "index.html"
        if (file.equals("/") && (statusLine.contains("GET") || statusLine.contains("HEAD"))) {
            file = "index.html";
        }

        // Devuelve solo el nombre del archivo (sin el prefijo '/')
        return file.startsWith("/") ? file.substring(1) : file;
    }

    // Función que se encarga de enviar archivos
    public void sendFile(String file_) {

        // Asegura que el archivo se busca en el directorio correcto
        File file = new File(absolute_path, file_);
        if (!file.exists()) {
            //System.err.println("Archivo no encontrado: " + file.getAbsolutePath());
            sendError("404", "Not found");
            return;
        }

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {

            long fileSize = file.length();
            String mime = Files.probeContentType(file.toPath());

            // Construcción de los encabezados HTTP y respuesta de la petición
            //Mandamos a crear la cabecera
            String responseHeaders = constructHeader("HTTP/1.1 202 OK", mime, fileSize);

            returnHeadersandBody(bis, responseHeaders);
        } catch (Exception e) {
            e.printStackTrace();
            sendError("403", "Forbidden"); //EL usuario no tiene acceso al archivo
        }
    }

    // Función que se encarga de enviar errores
    private void sendError(String code, String status) {

        File file = new File(absolute_path, code+".html");

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {

            long fileSize = file.length();
            String mime = Files.probeContentType(file.toPath()); //Función ya definida para definir el tipo MIME

            // Construcción de los encabezados HTTP y respuesta de la petición
            String response = "HTTP/1.1 " + code + " " + status;
            //Mandamos a crear la cabecera
            String responseHeaders = constructHeader(response, mime, fileSize);

            returnHeadersandBody(bis, responseHeaders);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Función que regresa los encabezados y el cuerpo como respuesta
    private void returnHeadersandBody(BufferedInputStream bis, String responseHeaders) throws IOException {
        System.out.println("Response headers:\n" + ANSI_BLUE + responseHeaders + ANSI_RESET);
        bos.write(responseHeaders.getBytes());
        bos.flush();

        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = bis.read(buffer)) != -1) {
            bos.write(buffer, 0, bytesRead);
        }
        bos.flush();
    }

    // Función para imprimir los parámetros
    private void sendParams(StringTokenizer tokens, int tokens_size) throws IOException {
            // Calcular el Content-Length con el tamaño real del cuerpo
        String responseBodyString = construiCuerpoParam(tokens, tokens_size);
        int contentLength = responseBodyString.getBytes().length;

        //Mandamos a crear la cabecera
        String responseHeaders = constructHeader("HTTP/1.1 202 Parámetros enviados", "text/html", contentLength);

        System.out.println("Response headers:\n" + ANSI_BLUE + responseHeaders + ANSI_RESET);
        bos.write(responseHeaders.getBytes());
        bos.write(responseBodyString.getBytes());
        bos.flush();

    }

    //Función para construir el body y devolver el contentlength
    private String construiCuerpoParam(StringTokenizer tokens, int tokens_size){
        //Generar el contenido HTML
        StringBuilder responseBody = new StringBuilder("<html><head><title>SERVIDOR HTTP</title></head><body>");

        //System.out.println(tokens.countTokens()+" afg");
        for (int i = 0; i < tokens_size; i++) {
            String param = tokens.nextToken();
            responseBody.append("<h3><b>").append(param).append("</b></h3>");
        }

        responseBody.append("</body></html>");

        return responseBody.toString();
    }

    //Función para contruir cabecera
    private String constructHeader(String response, String mime, long contentLength){
        //Construcción de los encabezados HTTP
        return response + CRLF +
                "Server: Server RV" + CRLF +
                "Date: " + new Date() + CRLF +
                "Keep-Alive: timeout=5, max=100" + CRLF +
                "Connection: Keep-Alive" + CRLF +
                "Content-Type: " + mime + CRLF +
                "Content-Length: " + contentLength + CRLF +
                CRLF;
    }


}



