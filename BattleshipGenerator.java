
public interface BattleshipGenerator {

    String generateMap();

    static BattleshipGenerator defaultInstance() {
        return new BattleshipGenerator() {
            @Override
            public String generateMap() {
                return new BattleshipGeneratorImpl().generateMap();
            }
        };
    }
}
