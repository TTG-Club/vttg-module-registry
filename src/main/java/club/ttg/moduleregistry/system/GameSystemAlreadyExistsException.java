package club.ttg.moduleregistry.system;

public class GameSystemAlreadyExistsException extends RuntimeException {

    public GameSystemAlreadyExistsException(String id) {
        super("Игровая система \"" + id + "\" уже есть");
    }
}
