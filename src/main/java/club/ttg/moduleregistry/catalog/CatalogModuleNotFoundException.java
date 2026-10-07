package club.ttg.moduleregistry.catalog;

public class CatalogModuleNotFoundException extends RuntimeException {

    public CatalogModuleNotFoundException(String moduleId) {
        super("Модуль «" + moduleId + "» не найден в каталоге");
    }
}
