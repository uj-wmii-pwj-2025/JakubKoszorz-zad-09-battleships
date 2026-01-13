
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GenerateMap {
    public static void main(String[] args) throws IOException {
        String map = BattleshipGenerator.defaultInstance().generateMap();

        if (args.length == 0) {
            System.out.println(map);
            return;
        }

        Path out = Path.of(args[0]);
        Files.writeString(out, map + "\n");
        System.out.println("Zapisano mapę do: " + out.toAbsolutePath());
    }
}