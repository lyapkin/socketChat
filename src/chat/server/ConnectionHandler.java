package chat.server;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.regex.Pattern;

public class ConnectionHandler implements Runnable {
    static private final Map<String, ConnectionHandler> connectionsList = new LinkedHashMap<>();
    static private final Pattern pattern = Pattern.compile("^[a-zA-Z0-9](_(?![_.])|\\.(?![_.])|[a-zA-Z0-9]){2,18}[a-zA-Z0-9]$");
    private String username;
    private final Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean isValid = false;

    ConnectionHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            initConnection();
        } catch (IOException e) {
            closeConnection();
            return;
        }

        try {
            String message;
            while (!socket.isClosed()) {
                message = username + ": " + reader.readLine();
                broadcast(message);
            }
        } catch (IOException e) {
            // Handle exception
            removeConnection();
        }
    }

    private void initConnection() throws IOException {
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        username = reader.readLine();

        ConnectionResponse response = new ConnectionResponse();

        validate(response);
        if (!isValid()) {
            closeConnection(); // Or throw an exception to close the connection
            throw new IOException();
        }

        writer.write("success");
        writer.newLine();
        writer.flush();

        addConnection();
    }

    private void validate(ConnectionResponse response) throws IOException {
        isValid = true;
        response.status = "succeeded";
        if (!isValidUsername()) {
            response.status = "failed";
            response.errorsList.add("Имя " + username + " недопустимой формы.");
            writer.write("Это имя занято. Введите другое имя.");
            writer.newLine();
            writer.flush();
            isValid = false;
        }
        if (connectionsList.containsKey(username)) {
            response.status = "failed";
            response.errorsList.add("Имя " + username + " сейчас занято.");

            isValid = false;
        }
    }

    public boolean isValid() {
        return isValid;
    }

    private boolean isValidUsername() {
        if (username != null) {
            return pattern.matcher(username).matches();
        }

        return false;
    }

    private void broadcast(String message) {
        for (ConnectionHandler connection : connectionsList.values()) {
            if (connection != null && connection != this) {
                try {
                    connection.writer.write(message);
                    connection.writer.newLine();
                    connection.writer.flush();
                } catch (IOException e) {
                    connection.removeConnection();
                }
            }
        }
    }

    private void addConnection() {
        connectionsList.put(username, this);
        System.out.println(username + " присоединился к чату.");
        broadcast(username + " присоединился к чату.");
    }

    private void removeConnection() {
        connectionsList.remove(username);
        System.out.println(username + " покинул чат.");
        broadcast(username + " покинул чат.");
        closeConnection();
    }

    private void closeConnection() {
        try {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            // Handle exception
        }
    }

    public boolean isClosed() {
        return socket.isClosed();
    }

    private static class ConnectionResponse {
        ArrayList<String> errorsList = new ArrayList<>();
        String status;
    }

}
