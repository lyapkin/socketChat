package chat.server;

import chat.JSONToSend;
import chat.exchngMsgTypes;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.regex.Pattern;

public class ConnectionHandler implements Runnable {
    static private final Object lock = new Object();
    static private final Map<String, ConnectionHandler> connectionsList = new LinkedHashMap<>();
    static private final Pattern pattern = Pattern.compile(
            "^[a-zA-Z0-9](_(?![_.])|\\.(?![_.])|[a-zA-Z0-9]){1,18}[a-zA-Z0-9]$"
    );
    static private final int roomSize = 30;
    private final JSONParser parser = new JSONParser();
    private String username;
    private String mapUsername;
    private final Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean isValid = false;
    private boolean isConnected = false;

    ConnectionHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            initConnection();
            if (!isConnected) {
                return;
            }
        } catch (IOException e) {
            closeConnection();
            return;
        } catch (ParseException e) {
            System.out.println("Проблема с парсингом сообщения");
            e.printStackTrace();
            closeConnection();
            return;
        }

        try {
            while (isConnected) {
                manageMessage(getMsg());
            }
        } catch (IOException e) {
            removeConnection();
        }
    }

    private void initConnection() throws IOException, ParseException {
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

        JSONObject request = (JSONObject) new JSONParser().parse(getMsg());

        if ((long) request.get("type") != exchngMsgTypes.CONNECTION_REQUEST) {
            System.out.println("Тип сообщенияотличается от CONNECTION_REQUEST: " + request.get("type"));
            ConnectionResponse response = new ConnectionResponse();
            response.failRequest();
            response.addError("Тип запроса от клиента не соответствует ожидаемому.");
            send(response.prepareToSend());
            closeConnection();
            return;
        }

        username = (String) request.get("username");

        ConnectionResponse response = validate();
        if (!isValid()) {
            send(response.prepareToSend());

            closeConnection();
            return;
        }

        send(response.prepareToSend());

        addConnection();
    }

    private ConnectionResponse validate() {
        ConnectionResponse response = new ConnectionResponse();
        isValid = true;

        if (connectionsList.size() >= roomSize) {
            response.failRequest();
            response.addError("В чате нет места.");
            isValid = false;
        }

        if (!isValidUsername()) {
            response.failRequest();
            response.addError("Имя " + username + " недопустимой формы.");

            isValid = false;
        } else if (connectionsList.containsKey(username.toLowerCase())) {
            response.failRequest();
            response.addError("Имя " + username + " сейчас занято.");

            isValid = false;
        }

        return response;
    }

    private boolean isValid() {
        return isValid;
    }

    private boolean isValidUsername() {
        if (username != null) {
            return pattern.matcher(username).matches();
        }

        return false;
    }

    private void manageMessage(String msg) throws IOException {
        try {
            JSONObject message = (JSONObject) parser.parse(msg);
            long type = (long) message.get("type");
            if (type == exchngMsgTypes.COMMAND) {
                handleCommand((String) message.get("command"));
            } else if (type == exchngMsgTypes.MESSAGE) {
                synchronized (lock) {
                    send(msg);
                    broadcast(msg);
                }
            }
        } catch (ParseException e) {
            System.out.println("Проблема с парсингом ссобщения.");
            e.printStackTrace();
        }
    }

    private void handleCommand(String command) throws IOException {
        switch (command) {
            case ("exit"):
                removeConnection();
                break;
            case ("list"):
                send(new ServerMessage(
                    connectionsList.values().stream().reduce(
                        "Список участников:",
                        (obj1, obj2) -> obj1 + "\n" +obj2.username,
                        (obj1, obj2) -> obj1 + "\n" + obj2
                )).prepareToSend());
                break;
            default:
                send(new ServerMessage("Нераспознанная команда " + "/" + command).prepareToSend());
        }
    }

    private void broadcast(String message) {
        for (ConnectionHandler connection : connectionsList.values()) {
            if (connection != null && connection != this) {
                try {
                    connection.send(message);
                } catch (IOException e) {
                    connection.removeConnection();
                }
            }
        }
    }

    private void send(String pack) throws IOException {
        writer.write(pack);
        writer.newLine();
        writer.flush();
    }

    private String getMsg() throws IOException {
        String msg = reader.readLine();
        if (msg == null) {
            throw new IOException();
        }
        return msg;
    }

    private void addConnection() {
        synchronized (lock) {
            mapUsername = username.toLowerCase();
            connectionsList.put(mapUsername, this);
            isConnected = true;
            System.out.println(username + " присоединился к чату.");
            broadcast(new ServerMessage(username + " присоединился к чату.").prepareToSend());
        }
    }

    private void removeConnection() {
        synchronized (lock) {
            connectionsList.remove(mapUsername);
            isConnected = false;
            closeConnection();
            System.out.println(username + " покинул чат.");
            broadcast(new ServerMessage(username + " покинул чат.").prepareToSend());
        }
    }

    private void closeConnection() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isClosed() {
        return socket.isClosed();
    }

    private static class ConnectionResponse implements JSONToSend {
        private final JSONArray errorsList = new JSONArray();
        private String status;

        ConnectionResponse() {
            succeedRequest();
        }

        @SuppressWarnings("unchecked")
        void addError(String err) {
            errorsList.add(err);
        }

        void failRequest() {
            this.status = "failed";
        }

        void succeedRequest() {
            this.status = "succeeded";
        }

        @Override
        @SuppressWarnings("unchecked")
        public String prepareToSend() {
            JSONObject obj = new JSONObject();

            obj.put("errorsList", errorsList);
            obj.put("status", status);
            obj.put("type", exchngMsgTypes.CONNECTION_RESPONSE);

            return obj.toJSONString();
        }
    }

    private static class ServerMessage implements JSONToSend {
        String message;

        ServerMessage(String msg) {
            setMessage(msg);
        }

        void setMessage(String msg) {
            message = msg;
        }

        @Override
        @SuppressWarnings("unchecked")
        public String prepareToSend() {
            JSONObject obj = new JSONObject();

            obj.put("message", message);
            obj.put("type", exchngMsgTypes.SERVER_MESSAGE);

            return obj.toJSONString();
        }
    }

}
