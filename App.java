import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

public class App {

    enum Mode { SERVER, CLIENT }
    record Args(Mode mode, int port, Path mapFile, String host) {}

    public static void main(String[] args) {
        try {
            Args a = parseArgs(args);

            Boards.OwnBoard own = Boards.OwnBoard.fromMapFileOrRandom(a.mapFile);
            System.out.print(own.toPrintableString());
            System.out.flush();

            Boards.EnemyKnowledge enemy = new Boards.EnemyKnowledge();

            if (a.mode == Mode.SERVER) {
                try (ServerSocket ss = new ServerSocket(a.port);
                     Socket s = ss.accept()) {
                    new Game(s, own, enemy, Game.Role.SERVER).play();
                }
            } else {
                try (Socket s = new Socket(a.host, a.port)) {
                    new Game(s, own, enemy, Game.Role.CLIENT).play();
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Błąd komunikacji");
            System.exit(1);
        }
    }

    private static Args parseArgs(String[] argv) {
        Mode mode = null;
        Integer port = null;
        Path map = null;
        String host = null;

        for (int i = 0; i < argv.length; i++) {
            switch (argv[i]) {
                case "-mode" -> {
                    requireNext(argv, i, "-mode");
                    String v = argv[++i];
                    if ("server".equalsIgnoreCase(v)) mode = Mode.SERVER;
                    else if ("client".equalsIgnoreCase(v)) mode = Mode.CLIENT;
                    else throw new IllegalArgumentException("Niepoprawny -mode: " + v);
                }
                case "-port" -> {
                    requireNext(argv, i, "-port");
                    port = Integer.parseInt(argv[++i]);
                }
                case "-map" -> {
                    requireNext(argv, i, "-map");
                    map = Path.of(argv[++i]);
                }
                case "-host" -> {
                    requireNext(argv, i, "-host");
                    host = argv[++i];
                }
                default -> throw new IllegalArgumentException("Nieznany argument: " + argv[i]);
            }
        }

        if (mode == null) throw new IllegalArgumentException("Brak -mode");
        if (port == null) throw new IllegalArgumentException("Brak -port");
        if (map == null) throw new IllegalArgumentException("Brak -map");
        if (mode == Mode.CLIENT && (host == null || host.isBlank()))
            throw new IllegalArgumentException("Brak -host w trybie client");
        if (mode == Mode.SERVER && host != null)
            throw new IllegalArgumentException("-host tylko w trybie client");

        return new Args(mode, port, map, host);
    }

    private static void requireNext(String[] argv, int i, String flag) {
        if (i + 1 >= argv.length) throw new IllegalArgumentException("Brak wartości po " + flag);
    }
}