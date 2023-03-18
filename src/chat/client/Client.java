package chat.client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static String username;
    private static Socket socket;
    private static BufferedReader reader;
    private static BufferedWriter writer;
    private static Scanner in;
    private static boolean isConnected = false;

    private static void connectClient() {
        String response;

        while (!isConnected) {
            try {
                System.out.println("Введите имя пользователя:");
                username = in.nextLine();

                socket = new Socket("localhost", 9999);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                writer.write(username);
                writer.newLine();
                writer.flush();
                response = reader.readLine();
            } catch (IOException e) {
                // Handle exception
                closeClient();
                continue;
            }

            if (response.equals("success")) {
                isConnected = true;
                System.out.println("Вы присоединились к чату.");
            } else {
                System.out.println(response);
                closeClient();
            }
        }
    }

    private static void closeClient() {
        isConnected = false;
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

    private static void listenForMessages() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!socket.isClosed()) {
                    try {
                        System.out.println(reader.readLine());
                    } catch (IOException e) {
                        closeClient();
                    }
                }
            }
        }).start();
    }

    private static void handleInput() {
        while (!socket.isClosed()) {
            try {
                writer.write(in.nextLine());
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                // Handle exception
                closeClient();
            }
        }
    }

    public static void main(String[] args) {
        in = new Scanner(System.in);

        connectClient();

        listenForMessages();
        handleInput();

        in.close();
    }
}
