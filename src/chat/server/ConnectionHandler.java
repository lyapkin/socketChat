package chat.server;

import java.io.*;
import java.net.Socket;
import java.util.LinkedList;

public class ConnectionHandler implements Runnable {
    static private LinkedList<ConnectionHandler> connectionsList = new LinkedList<>();
    private String username;
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean isValid = false;

    ConnectionHandler(Socket socket) {
        try {
            this.socket = socket;
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            username = reader.readLine();
        } catch (IOException e) {
            // Handle exception
            closeConnection();
        }
    }

    @Override
    public void run() {
        try {
            validate();
            if (!isValid()) {
                closeConnection(); // Or throw an exception to close the connection
                return;
            }
            writer.write("success");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            closeConnection();
        }

        addConnection();

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

    private void validate() throws IOException {
        if (!isValidUsername(username)) {
            writer.write("Это имя занято. Введите другое имя.");
            writer.newLine();
            writer.flush();
            isValid = false;
        } else {
            isValid = true;
        }
    }

    public boolean isValid() {
        return isValid;
    }

    private boolean isValidUsername(String username) {
        // Implement method
        return true;
    }

    private void broadcast(String message) {
        for (ConnectionHandler connection : connectionsList) {
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
        connectionsList.add(this);
        System.out.println(username + " присоединился к чату.");
        broadcast(username + " присоединился к чату.");
    }

    private void removeConnection() {
        connectionsList.remove(this);
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

}
