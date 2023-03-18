package chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private final ServerSocket serverSocket;

    Server(ServerSocket socket) {
        serverSocket = socket;
    }

    public void startServer() {
        try {
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                ConnectionHandler connection = new ConnectionHandler(socket);
                if (!connection.isClosed()) {
                    new Thread(connection).start();
                }
            }
        } catch (IOException e) {
            closeServer();
        }
    }

    private void closeServer() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // Handle exception
        }
    }

    public static void main(String[] args) throws IOException {
        Server server = new Server(new ServerSocket(9999));
        server.startServer();
    }
}
