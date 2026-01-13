public final class Protocol {
    private Protocol() {}

    public enum Command {
        START("start"),
        PUDLO("pudło"),
        TRAFIONY("trafiony"),
        TRAFIONY_ZATOPIONY("trafiony zatopiony"),
        OSTATNI_ZATOPIONY("ostatni zatopiony");

        private final String wire;
        Command(String wire) { this.wire = wire; }
        public String wire() { return wire; }

        public static Command fromWire(String s) {
            for (Command c : values()) if (c.wire.equals(s)) return c;
            return null;
        }
    }

    public record Coord(int x, int y) {
        public static Coord parse(String s) {
            if (s == null) throw new IllegalArgumentException("null");
            s = s.strip().toUpperCase();
            if (!s.matches("[A-J]([1-9]|10)")) throw new IllegalArgumentException("Bad coord: " + s);
            int x = s.charAt(0) - 'A';
            int row = Integer.parseInt(s.substring(1));
            return new Coord(x, row - 1);
        }
        public String toWire() { return "" + (char)('A' + x) + (y + 1); }
    }

    public record Message(Command command, Coord coord) {
        public static Message parseLine(String line) {
            if (line == null) return null;
            line = line.strip();
            if (line.isEmpty()) return null;

            if (line.equals(Command.OSTATNI_ZATOPIONY.wire())) {
                return new Message(Command.OSTATNI_ZATOPIONY, null);
            }

            int sep = line.indexOf(';');
            if (sep < 0) return null;

            String cmdStr = line.substring(0, sep).strip();
            String coordStr = line.substring(sep + 1).strip();

            Command cmd = Command.fromWire(cmdStr);
            if (cmd == null) return null;

            try {
                Coord c = Coord.parse(coordStr);
                return new Message(cmd, c);
            } catch (Exception e) {
                return null;
            }
        }

        public String toWireLine() {
            if (command == Command.OSTATNI_ZATOPIONY) return command.wire() + "\n";
            if (coord == null) throw new IllegalStateException("Komenda wymaga współrzędnych: " + command);
            return command.wire() + ";" + coord.toWire() + "\n";
        }
    }
}