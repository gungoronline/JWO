package com.gungoronline.jwo.Webserver;

import com.gungoronline.jwo.Helper.DatabaseHelper;

import java.io.*;
import java.net.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ClientThread extends Thread {
    private Socket socket;
    private boolean isStop;
    private BufferedReader in;
    private PrintWriter out;
    final static String CRLF = "\r\n";
    private static Map<String, String> config = new HashMap<>();
    private static String rootDirectory;

    public ClientThread(Socket clientSocket) {
        this.socket = clientSocket;
        this.isStop = false;
        loadConfig();
        DatabaseHelper.initializeDatabase(); // Veritabanını başlat
    }

    public void run() {
        try {
            while (!isStop) {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                String line;
                String httpHeader = "";
                String requestedFile = "";
                String method = ""; // POST veya GET olacak
                String postData = ""; // POST verisi
                while ((line = in.readLine()) != null) {
                    if (line.isEmpty()) {
                        break;
                    }
                    httpHeader += line + "\n";
                    if (line.startsWith("GET") || line.startsWith("POST")) {
                        int beginIndex = line.indexOf(" ") + 1;
                        int endIndex = line.indexOf(" ", beginIndex);
                        method = line.substring(0, beginIndex - 1);
                        requestedFile = line.substring(beginIndex, endIndex);
                    }
                }

                if ("POST".equals(method)) {
                    postData = readPostData(in);
                }

                System.out.println(httpHeader);

                if (requestedFile.isEmpty() || requestedFile.equals("/")) {
                    requestedFile = config.getOrDefault("default.page", "index.html");
                } else if (requestedFile.startsWith("/")) {
                    requestedFile = requestedFile.substring(1);
                }

                // Köklü dizin ile birleştirerek tam yolu belirle
                if (rootDirectory.equals(rootDirectory) && requestedFile.startsWith(rootDirectory)) {
                    requestedFile = requestedFile.substring(7); // "/public" ifadesini kaldır
                } else {
                    requestedFile = rootDirectory + File.separator + requestedFile;
                }

                // processRequest metodunu çağırırken istek türü ve POST verisini de iletiyoruz
                processRequest(requestedFile, method, postData);
                closeConnection();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader(".jwoconf"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue; // Yorum satırlarını ve boş satırları atla
                }
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    config.put(parts[0].trim(), parts[1].trim());
                }
            }
            rootDirectory = config.getOrDefault("root.directory", "public");
        } catch (IOException e) {
            System.out.println("Failed to load configuration file: " + e.getMessage());
        }
    }

    private void sendForbiddenResponse(String method, String requestedFile) {
        out.print("HTTP/1.1 403 Forbidden" + CRLF);
        Date date = new Date();
        out.print("Date: " + date.toString() + CRLF);
        out.print("Server: " + config.getOrDefault("server.name", "java tiny web server") + CRLF);
        out.print("Connection: close" + CRLF);
        out.println("Content-Type: text/html; charset=iso-8859-1" + CRLF);

        out.println("<html><head>");
        out.println("<title>403 Forbidden</title>");
        out.println("</head><body>");
        out.println("<h1>Forbidden</h1>");
        out.println("<p>You don't have permission to access this resource.</p>");
        out.println("</body></html>");
        out.println(CRLF);

        DatabaseHelper.logRequest(method, requestedFile, 403); // 403 hatasını logla
    }

    // POST verisini okumak için yardımcı bir metot
    private String readPostData(BufferedReader in) throws IOException {
        StringBuilder postData = new StringBuilder();
        String line;
        int contentLength = 0;

        while (!(line = in.readLine()).isEmpty()) {
            if (line.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(line.substring(16).trim());
            }
        }

        for (int i = 0; i < contentLength; i++) {
            postData.append((char) in.read());
        }
        return postData.toString();
    }

    public void processRequest(String requestedFile) throws Exception {
        File file = new File(requestedFile);

        if (requestedFile.equals(".jwoconf") || requestedFile.endsWith(".conf")) {
            sendForbiddenResponse("GET",requestedFile);
            return;
        }

        // Eğer talep edilen dosya bir klasör ise, içeriğini listele
        if (file.isDirectory()) {
            listDirectoryContents(file, requestedFile);
            return; // İşlemi sonlandır, listeleme yapıldı
        }

        // Check if requestedFile is a .jwo file
        String[] possibleFiles;
        if (requestedFile.endsWith(".html")) {
            possibleFiles = new String[]{requestedFile, requestedFile.replace(".html", ".jwo")};
        } else if (requestedFile.endsWith(".jwo")) {
            possibleFiles = new String[]{requestedFile};
        } else {
            possibleFiles = new String[]{requestedFile, requestedFile + ".html", requestedFile + ".jwo"};
        }

        for (String possibleFile : possibleFiles) {
            file = new File(possibleFile);
            if (file.exists()) {
                break;
            }
        }

        if (file.exists()) {
            BufferedReader reader = new BufferedReader(new FileReader(file));

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();

            String parsedContent = file.getName().endsWith(".jwo") ? parseScript(content.toString()) : content.toString();
            int contentLength = parsedContent.getBytes().length;

            out.print("HTTP/1.0 200 OK" + CRLF);
            Date date = new Date();
            out.print("Date: " + date.toString() + CRLF);
            out.print("Server: " + config.getOrDefault("server.name", "java tiny web server") + CRLF);
            out.print("Content-Type: text/html" + CRLF);
            out.print("Content-Length: " + contentLength + CRLF);
            out.print("Connection: close" + CRLF);
            out.println(CRLF);
            out.println(parsedContent);
        } else {
            sendNotFoundResponse(requestedFile);
        }
    }

    // İkinci bir processRequest metodu, POST isteklerini işler
    public void processRequest(String requestedFile, String method, String postData) throws Exception {
        // Eğer POST isteği ise, postData değişkeniyle birlikte işlem yapabiliriz
        if ("POST".equals(method)) {
            // Burada POST verilerini işleyebilirsiniz
            out.print("HTTP/1.0 200 OK" + CRLF);
            Date date = new Date();
            out.print("Date: " + date.toString() + CRLF);
            out.print("Server: " + config.getOrDefault("server.name", "java tiny web server") + CRLF);
            out.print("Content-Type: text/html" + CRLF);
            out.print("Content-Length: " + postData.length() + CRLF);
            out.print("Connection: close" + CRLF);
            out.println(CRLF);
            out.println(postData); // Post verilerini dön
        } else {
            // POST isteği değilse, orijinal processRequest metodunu çağırıyoruz (GET)
            processRequest(requestedFile);
        }

    }

    private void listDirectoryContents(File directory, String relativePath) {
        File[] files = directory.listFiles();
        if (files == null) {
            sendNotFoundResponse(directory.getName());
            return;
        }

        out.print("HTTP/1.0 200 OK" + CRLF);
        Date date = new Date();
        out.print("Date: " + date.toString() + CRLF);
        out.print("Server: " + config.getOrDefault("server.name", "java tiny web server") + CRLF);
        out.print("Content-Type: text/html" + CRLF);
        out.print("Connection: close" + CRLF);
        out.println(CRLF);

        String title = relativePath;
        if (rootDirectory.equals("public") && relativePath.startsWith("public")) {
            title = relativePath.substring(7); // "/public" ifadesini kaldır
        }
        out.println("<html><head>");
        out.println("<title>Directory listing for " + title + "</title>");
        out.println("</head><body>");
        out.println("<h1>Directory listing for " + title + "</h1>");
        out.println("<ul>");
        for (File file : files) {
            String fileName = file.getName();
            if (file.isDirectory()) {
                fileName += "/";
            }
            String fileUrl = fileName;
            if (rootDirectory.equals("public") && relativePath.startsWith("public")) {
                fileUrl = relativePath.substring(7) + "/" + fileName; // "/public" ifadesini kaldır
            } else {
                fileUrl = "/" + relativePath + "/" + fileName; // Kök dizine göre normal URL oluştur
            }
            out.println("<li><a href=\"" + fileUrl + "\">" + fileName + "</a></li>");
        }
        out.println("</ul>");
        out.println("</body></html>");
        out.println(CRLF);
    }

    private void sendNotFoundResponse(String requestedFile) {
        out.print("HTTP/1.1 404 Not Found" + CRLF);
        Date date = new Date();
        out.print("Date: " + date.toString() + CRLF);
        out.print("Server: " + config.getOrDefault("server.name", "java tiny web server") + CRLF);
        out.print("Connection: close" + CRLF);
        out.println("Content-Type: text/html; charset=iso-8859-1" + CRLF);

        out.println("<html><head>");
        out.println("<title>404 Not Found</title>");
        out.println("</head><body>");
        out.println("<h1>Not Found</h1>");
        out.println("<p>The requested URL /" + requestedFile + " was not found on this server.</p>");
        out.println("</body></html>");
        out.println(CRLF);

        DatabaseHelper.logRequest("GET", requestedFile, 404); // 404 hatasını logla
    }

    private String parseScript(String content) {
        StringBuilder parsedContent = new StringBuilder();
        int lastPos = 0;
        while (true) {
            int start = content.indexOf("<?jwo", lastPos);
            if (start == -1) {
                parsedContent.append(content.substring(lastPos));
                break;
            }
            int end = content.indexOf("?>", start);
            if (end == -1) {
                parsedContent.append(content.substring(lastPos));
                break;
            }
            parsedContent.append(content.substring(lastPos, start));

            String scriptContent = content.substring(start + 5, end).trim();
            String scriptResult = evaluateScript(scriptContent);

            parsedContent.append(scriptResult);

            lastPos = end + 2;
        }
        return parsedContent.toString();
    }

    private String evaluateScript(String script) {
        if (script.startsWith("echo ")) {
            String result = script.substring(5).trim();
            if (result.endsWith(";")) {
                result = result.substring(0, result.length() - 1);
            }
            if (result.startsWith("\"") && result.endsWith("\"")) {
                result = result.substring(1, result.length() - 1);
            }
            return result;
        } else {
            return "Unsupported script command.";
        }
    }

    private void closeConnection() {
        try {
            out.close();
            in.close();
            socket.close();
            isStop = true;
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }
}