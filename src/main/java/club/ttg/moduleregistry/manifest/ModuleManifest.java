package club.ttg.moduleregistry.manifest;

import java.util.List;

/**
 * Проверенный {@code module.json}: поля, которые нужны реестру, и сам
 * документ как пришёл — его видит модератор.
 *
 * @param compatibleSystems пустой список — модуль универсальный
 */
public record ModuleManifest(
        String id,
        String name,
        String version,
        String author,
        String icon,
        String download,
        List<String> compatibleSystems,
        String json
) {
}
