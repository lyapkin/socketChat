package chat.client;

import chat.JSONToSend;
import chat.exchngMsgTypes;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

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
    private static final JSONParser parser = new JSONParser();

    private static void connectClient() {
        in = new Scanner(System.in);

        JSONObject response;
        ConnectionRequest request = new ConnectionRequest();

        while (!isConnected) {
            try {
                System.out.println("Введите имя пользователя:");
                username = in.nextLine();
                request.setUsername(username);

                socket = new Socket("127.0.0.1", 9999);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                send(request.prepareToSend());
                response = (JSONObject) parser.parse(getMsg());

                if ((long) response.get("type") != exchngMsgTypes.CONNECTION_RESPONSE) {
                    System.out.println("Тип сообщения отличается от CONNECTION_RESPONSE: " + response.get("type"));
                }

                if (response.get("status").equals("succeeded")) {
                    isConnected = true;
                    System.out.println("Вы присоединились к чату.");
                } else if (response.get("status").equals("failed")) {
                    for (Object error : (JSONArray) response.get("errorsList")) {
                        System.out.println((String) error);
                    }
                } else {
                    System.out.println(response);
                    closeClient();
                }
            } catch (IOException e) {
                e.printStackTrace();
                if (!socket.isClosed()) {
                    closeClient();
                }
            } catch (ParseException e) {
                System.out.println("Проблема с парсингом сообщения.");
                e.printStackTrace();
            }


        }
    }

    private static void closeClient() {
        isConnected = false;
        try {
            if (socket != null) {
                socket.close();
            }
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void listenForMessages() {
        new Thread(() -> {
            while (isConnected) {
                try {
                    JSONObject message = (JSONObject) parser.parse(getMsg());
                    if ((long) message.get("type") == exchngMsgTypes.MESSAGE) {
                        System.out.println(message.get("sender") + ": " + message.get("message"));
                    } else if ((long) message.get("type") == exchngMsgTypes.SERVER_MESSAGE) {
                        System.out.println(message.get("message"));
                    }
                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        e.printStackTrace();
                        closeClient();
                    }
                } catch (ParseException e) {
                    System.out.println("Проблема с парсингом сообщения.");
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static void handleInput() {
        String message;
        while (isConnected) {
            try {
                message = in.nextLine().trim();
                if (message.startsWith("/")) {
                    handleCommand(message.substring(1));
                } else {
                    send(new Message(username, message).prepareToSend());
                    System.out.print("\033[1A");
                    System.out.print("\033[2K");
                    System.out.println(username + " (ты): " + message);
                }
            } catch (IOException e) {
                e.printStackTrace();
                if (!socket.isClosed()) {
                    closeClient();
                }
            }
        }
    }

    static void handleCommand(String command) throws IOException {
        switch (command) {
            case "exit":
                send(new Command(command).prepareToSend());
                closeClient();
                break;
            default:
                send(new Command(command).prepareToSend());
        }
    }

    static void send(String pack) throws IOException {
        writer.write(pack);
        writer.newLine();
        writer.flush();
    }

    static String getMsg() throws IOException {
        String msg = reader.readLine();
        if (msg == null) {
            throw new IOException();
        }
        return msg;
    }

    private static class ConnectionRequest implements JSONToSend {
        private String username;

        void setUsername(String username) {
            this.username = username;
        }
        @Override
        @SuppressWarnings("unchecked")
        public String prepareToSend() {
            JSONObject obj = new JSONObject();

            obj.put("username", username);
            obj.put("type", exchngMsgTypes.CONNECTION_REQUEST);

            return obj.toJSONString();
        }
    }

    private static class Message implements JSONToSend {
        private final String sender;
        private final String message;

        Message(String username, String msg) {
            sender = username;
            message = msg;
        }
        @Override
        @SuppressWarnings("unchecked")
        public String prepareToSend() {
            JSONObject obj = new JSONObject();

            obj.put("sender", sender);
            obj.put("message", message);
            obj.put("type", exchngMsgTypes.MESSAGE);

            return obj.toJSONString();
        }
    }

    private static class Command implements JSONToSend {
        private final String command;

        Command(String command) {
            this.command = command;
        }

        @Override
        @SuppressWarnings("unchecked")
        public String prepareToSend() {
            JSONObject obj = new JSONObject();

            obj.put("command", command);
            obj.put("type", exchngMsgTypes.COMMAND);

            return obj.toJSONString();
        }
    }

    public static void main(String[] args) {

        connectClient();

        if (isConnected) {
            listenForMessages();
            handleInput();
        }

    }
}
