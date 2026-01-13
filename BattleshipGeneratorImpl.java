
import java.util.Random;

public class BattleshipGeneratorImpl implements BattleshipGenerator {

    private static final int BOARD_SIZE = 10;
    private static final char WATER = '.';
    private static final char SHIP = '#';

    private static final int[] SHIP_SIZES = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};

    private Random random;
    private char[][] board;

    @Override
    public String generateMap() {
        random = new Random();
        board = new char[BOARD_SIZE][BOARD_SIZE];

        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                board[i][j] = WATER;
            }
        }

        for (int shipSize : SHIP_SIZES) {
            placeShip(shipSize);
        }

        return convertToString();
    }

    private String convertToString() {
        StringBuilder sb = new StringBuilder(BOARD_SIZE * BOARD_SIZE);
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                sb.append(board[i][j]);
            }
        }
        return sb.toString();
    }

    private void placeShip(int size) {
        while (true) {
            int startX = random.nextInt(BOARD_SIZE);
            int startY = random.nextInt(BOARD_SIZE);
            int direction = random.nextInt(4);

            if (canPlaceShip(startX, startY, size, direction)) {
                placeShipOnBoard(startX, startY, size, direction);
                return;
            }
        }
    }

    private boolean canPlaceShip(int startX, int startY, int size, int direction) {
        for (int i = 0; i < size; i++) {
            Position pos = calculatePosition(startX, startY, direction, i);

            if (pos.x < 0 || pos.x >= BOARD_SIZE || pos.y < 0 || pos.y >= BOARD_SIZE) {
                return false;
            }

            if (board[pos.y][pos.x] == SHIP) {
                return false;
            }

            if (!isValidNeighborhood(pos.x, pos.y)) {
                return false;
            }
        }

        return true;
    }

    private void placeShipOnBoard(int startX, int startY, int size, int direction) {
        for (int i = 0; i < size; i++) {
            Position pos = calculatePosition(startX, startY, direction, i);
            board[pos.y][pos.x] = SHIP;
        }
    }

    private Position calculatePosition(int startX, int startY, int direction, int step) {
        int newX = startX;
        int newY = startY;

        switch (direction) {
            case 0:
                newX = startX + step;
                break;
            case 1:
                newY = startY + step;
                break;
            case 2:
                newX = startX - step;
                break;
            case 3:
                newY = startY - step;
                break;
        }

        return new Position(newX, newY);
    }

    private boolean isValidNeighborhood(int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int checkX = x + dx;
                int checkY = y + dy;

                if (dx == 0 && dy == 0) continue;

                if (checkX >= 0 && checkX < BOARD_SIZE && checkY >= 0 && checkY < BOARD_SIZE) {
                    if (board[checkY][checkX] == SHIP) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static class Position {
        final int x;
        final int y;

        Position(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static BattleshipGenerator defaultInstance() {
        return new BattleshipGeneratorImpl();
    }
}