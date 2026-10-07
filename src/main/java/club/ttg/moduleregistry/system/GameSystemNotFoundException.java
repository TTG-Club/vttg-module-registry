package club.ttg.moduleregistry.system;

public class GameSystemNotFoundException extends RuntimeException {

    public GameSystemNotFoundException(String id) {
        super("Игровая система \"" + id + "\" не найдена");
    }
}
