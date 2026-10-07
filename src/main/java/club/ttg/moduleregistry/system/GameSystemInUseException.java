package club.ttg.moduleregistry.system;

public class GameSystemInUseException extends RuntimeException {

    public GameSystemInUseException(String id) {
        super("Игровую систему \"" + id + "\" указали в заявках модулей — её нельзя удалить");
    }
}
