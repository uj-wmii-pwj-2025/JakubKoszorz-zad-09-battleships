import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class Boards {
    private Boards() {}

    public static final class OwnBoard {
        private static final int N = 10;
        private final char[][] ships = new char[N][N];      // '.' '#'
        private final boolean[][] hitByEnemy = new boolean[N][N];
        private final boolean[][] missByEnemy = new boolean[N][N];
        private int remainingShipSegments;

        public enum ShotResult { MISS, HIT, HIT_SUNK, LAST_SUNK }

        private OwnBoard() {}

        public static OwnBoard fromMapFileOrRandom(Path path) throws IOException {
            String line = Files.readString(path).strip();
            if (line.length() != 100) throw new IllegalArgumentException("Mapa musi mieć 100 znaków w 1 linii");
            for (char c : line.toCharArray()) if (c != '.' && c != '#')
                throw new IllegalArgumentException("Mapa ma niedozwolone znaki");
            return fromMapString(line);
        }

        public static OwnBoard fromMapString(String map100) {
            if (map100 == null || map100.length() != 100) throw new IllegalArgumentException("Bad map");
            OwnBoard b = new OwnBoard();
            int idx = 0, count = 0;
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    char c = map100.charAt(idx++);
                    b.ships[y][x] = c;
                    if (c == '#') count++;
                }
            }
            b.remainingShipSegments = count;
            return b;
        }

        public String toMap100() {
            StringBuilder sb = new StringBuilder(100);
            for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) sb.append(ships[y][x]);
            return sb.toString();
        }

        public ShotResult applyEnemyShot(Protocol.Coord c) {
            int x = c.x(), y = c.y();
            if (ships[y][x] == '#') {
                boolean wasHit = hitByEnemy[y][x];
                hitByEnemy[y][x] = true;
                if (!wasHit) remainingShipSegments--;

                List<Protocol.Coord> ship = ShipTracker.collectShipCells(ships, x, y);
                boolean sunk = ShipTracker.isSunk(ship, hitByEnemy);

                if (remainingShipSegments == 0) return ShotResult.LAST_SUNK;
                if (sunk) return ShotResult.HIT_SUNK;
                return ShotResult.HIT;
            } else {
                missByEnemy[y][x] = true;
                return ShotResult.MISS;
            }
        }

        public String toPrintableString() {
            StringBuilder sb = new StringBuilder();
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) sb.append(ships[y][x]);
                sb.append('\n');
            }
            return sb.toString();
        }

        public String toPrintableStringAfterGame() {
            StringBuilder sb = new StringBuilder();
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    if (hitByEnemy[y][x]) sb.append('@');
                    else if (missByEnemy[y][x]) sb.append('~');
                    else sb.append(ships[y][x]);
                }
                sb.append('\n');
            }
            return sb.toString();
        }
    }

    public static final class EnemyKnowledge {
        private static final int N = 10;
        private final char[][] view = new char[N][N];

        public EnemyKnowledge() {
            for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) view[y][x] = '?';
        }

        public void noteMiss(Protocol.Coord c) { view[c.y()][c.x()] = '.'; }
        public void noteHit(Protocol.Coord c) { view[c.y()][c.x()] = '#'; }

        public void noteSunkAndRevealNeighborhood(Protocol.Coord c) {
            noteHit(c);

            boolean[][] ship = new boolean[N][N];
            markConnectedShipFrom(c.x(), c.y(), ship);

            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    if (!ship[y][x]) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || nx >= N || ny < 0 || ny >= N) continue;
                            if (view[ny][nx] == '?') view[ny][nx] = '.';
                        }
                    }
                }
            }
        }

        private void markConnectedShipFrom(int x, int y, boolean[][] mark) {
            if (x < 0 || x >= N || y < 0 || y >= N) return;
            if (mark[y][x]) return;
            if (view[y][x] != '#') return;
            mark[y][x] = true;
            markConnectedShipFrom(x + 1, y, mark);
            markConnectedShipFrom(x - 1, y, mark);
            markConnectedShipFrom(x, y + 1, mark);
            markConnectedShipFrom(x, y - 1, mark);
        }

        public void revealFullMap100(String map100) {
            int idx = 0;
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    view[y][x] = map100.charAt(idx++);
                }
            }
        }

        public String toPrintableString() {
            StringBuilder sb = new StringBuilder();
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) sb.append(view[y][x]);
                sb.append('\n');
            }
            return sb.toString();
        }
    }

    private static final class ShipTracker {
        private ShipTracker() {}

        static List<Protocol.Coord> collectShipCells(char[][] ships, int sx, int sy) {
            int n = 10;
            boolean[][] visited = new boolean[n][n];
            Deque<Protocol.Coord> dq = new ArrayDeque<>();
            List<Protocol.Coord> out = new ArrayList<>();

            dq.add(new Protocol.Coord(sx, sy));
            visited[sy][sx] = true;

            while (!dq.isEmpty()) {
                Protocol.Coord c = dq.removeFirst();
                out.add(c);

                int x = c.x(), y = c.y();
                int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    if (nx < 0 || nx >= n || ny < 0 || ny >= n) continue;
                    if (visited[ny][nx]) continue;
                    if (ships[ny][nx] != '#') continue;
                    visited[ny][nx] = true;
                    dq.add(new Protocol.Coord(nx, ny));
                }
            }
            return out;
        }

        static boolean isSunk(List<Protocol.Coord> shipCells, boolean[][] hit) {
            for (Protocol.Coord c : shipCells) if (!hit[c.y()][c.x()]) return false;
            return true;
        }
    }
}