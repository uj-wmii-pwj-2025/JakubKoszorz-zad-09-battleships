import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Game {
    enum Role { SERVER, CLIENT }

    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final BufferedReader console;

    private final Boards.OwnBoard own;
    private final Boards.EnemyKnowledge enemy;
    private final Role role;

    private Protocol.Message lastSent = null;
    private int failures = 0;

    public Game(Socket socket, Boards.OwnBoard own, Boards.EnemyKnowledge enemy, Role role) throws IOException {
        this.socket = socket;
        this.socket.setSoTimeout(0);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        this.own = own;
        this.enemy = enemy;
        this.role = role;
    }

    public void play() throws IOException {
        if (role == Role.CLIENT) {
            Protocol.Coord myFirst = readShotFromUser("<---- TWOJA TURA (STRZAŁ) ---->");
            sendAndLog(new Protocol.Message(Protocol.Command.START, myFirst));

            Protocol.Message ans = receiveAnswerFor(myFirst);
            logIncoming(ans);
            if (ans.command() == Protocol.Command.OSTATNI_ZATOPIONY) {
                printEndOfGame(true);
                return;
            }
            applyAnswerToEnemyKnowledge(ans);
        } else {
            Protocol.Message firstShot = receiveShotBlocking("<--- TURA PRZECIWNIKA (STRZAŁ) --->");
            logIncoming(firstShot);

            Protocol.Command resp = applyIncomingShotToOwnBoard(firstShot.coord());
            if (resp == Protocol.Command.OSTATNI_ZATOPIONY) {
                sendAndLog(new Protocol.Message(Protocol.Command.OSTATNI_ZATOPIONY, null));
                printEndOfGame(false);
                return;
            }
            sendAndLog(new Protocol.Message(resp, firstShot.coord()));

            Protocol.Coord myShot = readShotFromUser("<---- TWOJA TURA (STRZAŁ) ---->");
            sendAndLog(new Protocol.Message(Protocol.Command.PUDLO, myShot));

            Protocol.Message ans = receiveAnswerFor(myShot);
            logIncoming(ans);
            if (ans.command() == Protocol.Command.OSTATNI_ZATOPIONY) {
                printEndOfGame(true);
                return;
            }
            applyAnswerToEnemyKnowledge(ans);
        }

        while (true) {
            Protocol.Message enemyShot = receiveShotBlocking("<--- TURA PRZECIWNIKA (STRZAŁ) --->");
            logIncoming(enemyShot);

            Protocol.Command resp = applyIncomingShotToOwnBoard(enemyShot.coord());
            if (resp == Protocol.Command.OSTATNI_ZATOPIONY) {
                sendAndLog(new Protocol.Message(Protocol.Command.OSTATNI_ZATOPIONY, null));
                printEndOfGame(false);
                return;
            }
            sendAndLog(new Protocol.Message(resp, enemyShot.coord()));

            Protocol.Coord myShot = readShotFromUser("<---- TWOJA TURA (STRZAŁ) ---->");
            sendAndLog(new Protocol.Message(Protocol.Command.PUDLO, myShot));

            Protocol.Message ans = receiveAnswerFor(myShot);
            logIncoming(ans);
            if (ans.command() == Protocol.Command.OSTATNI_ZATOPIONY) {
                printEndOfGame(true);
                return;
            }
            applyAnswerToEnemyKnowledge(ans);
        }
    }

    private Protocol.Message receiveShotBlocking(String banner) throws IOException {
        System.out.println(banner);
        while (true) {
            Protocol.Message m = receiveBlocking();
            if (m == null) die();

            if (m.command() == Protocol.Command.OSTATNI_ZATOPIONY) {
                printEndOfGame(true);
                System.exit(0);
            }

            if (m.coord() == null) continue;

            return m;
        }
    }

    private Protocol.Message receiveAnswerFor(Protocol.Coord expected) throws IOException {
        System.out.println("<--- OCZEKIWANIE NA ODPOWIEDŹ --->");
        socket.setSoTimeout(1000);
        failures = 0;
        try {
            while (true) {
                Protocol.Message m = receiveWithRetries();
                if (m == null) die();

                if (m.command() == Protocol.Command.OSTATNI_ZATOPIONY) return m;

                if (m.coord() == null) {
                    if (!retry()) die();
                    continue;
                }

                if (m.command() == Protocol.Command.START) {
                    if (!retry()) die();
                    continue;
                }

                if (m.coord().x() != expected.x() || m.coord().y() != expected.y()) {
                    if (!retry()) die();
                    continue;
                }

                return m;
            }
        } finally {
            socket.setSoTimeout(0);
        }
    }

    private Protocol.Command applyIncomingShotToOwnBoard(Protocol.Coord enemyShot) {
        System.out.println("Przeciwnik strzela w: " + enemyShot.toWire());

        Boards.OwnBoard.ShotResult r = own.applyEnemyShot(enemyShot);
        if (r == Boards.OwnBoard.ShotResult.LAST_SUNK) return Protocol.Command.OSTATNI_ZATOPIONY;

        return switch (r) {
            case MISS -> Protocol.Command.PUDLO;
            case HIT -> Protocol.Command.TRAFIONY;
            case HIT_SUNK -> Protocol.Command.TRAFIONY_ZATOPIONY;
            case LAST_SUNK -> throw new IllegalStateException();
        };
    }

    private void applyAnswerToEnemyKnowledge(Protocol.Message answer) {
        Protocol.Coord c = answer.coord();
        switch (answer.command()) {
            case PUDLO -> enemy.noteMiss(c);
            case TRAFIONY -> enemy.noteHit(c);
            case TRAFIONY_ZATOPIONY -> enemy.noteSunkAndRevealNeighborhood(c);
            default -> { }
        }
    }

    private Protocol.Coord readShotFromUser(String banner) throws IOException {
        System.out.println(banner);
        while (true) {
            System.out.print("[CELUJ] (A1..J10): ");
            System.out.flush();
            String s = console.readLine();
            if (s == null) return new Protocol.Coord(0, 0);
            s = s.strip();
            try {
                return Protocol.Coord.parse(s);
            } catch (Exception e) {
                System.out.println("Błędny format, użyj A1..J10");
            }
        }
    }

    private void printEndOfGame(boolean won) {
        if (won) System.out.print("Wygrana\n");
        else System.out.print("Przegrana\n");

        System.out.print(enemy.toPrintableString());
        System.out.print("\n");
        System.out.print(own.toPrintableStringAfterGame());
    }

    private void sendAndLog(Protocol.Message m) throws IOException {
        String wire = m.toWireLine();
        System.out.print(wire);
        out.write(wire);
        out.flush();
        lastSent = m;
        failures = 0;
    }

    private void logIncoming(Protocol.Message m) {
        System.out.print(m.toWireLine());
    }

    private Protocol.Message receiveBlocking() throws IOException {
        String line = in.readLine();
        if (line == null) return null;
        return Protocol.Message.parseLine(line);
    }

    private Protocol.Message receiveWithRetries() throws IOException {
        String line;
        try {
            line = in.readLine();
        } catch (IOException e) {
            if (!retry()) return null;
            return receiveWithRetries();
        }
        if (line == null) return null;

        Protocol.Message m = Protocol.Message.parseLine(line);
        if (m == null) {
            if (!retry()) return null;
            return receiveWithRetries();
        }
        return m;
    }

    private boolean retry() throws IOException {
        if (lastSent == null) return true;
        failures++;
        if (failures >= 3) return false;
        String wire = lastSent.toWireLine();
        System.out.print(wire);
        out.write(wire);
        out.flush();
        return true;
    }

    private void die() throws IOException {
        System.err.println("Błąd komunikacji");
        socket.close();
        System.exit(1);
    }
}