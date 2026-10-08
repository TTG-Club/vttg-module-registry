package club.ttg.moduleregistry.manifest;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Разбирает {@code module.json} по контракту VTTG ({@code docs/MODULES.md}).
 *
 * VTTG сам требует только {@code id}, {@code name} и {@code version}; реестру
 * нужны ещё {@code download} — архив, который VTTG скачает при установке, как
 * у систем, устанавливаемых по ссылке на манифест, — и {@code description}:
 * каталог показывает его как описание модуля.
 */
@Component
public class ManifestParser {

    static final Pattern MODULE_ID = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");

    private static final Pattern VERSION =
            Pattern.compile("^\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.+-]+)?$");

    private static final int MAX_SYSTEMS = 20;
    private static final int MAX_SYSTEM_ID_LENGTH = 64;

    private final ObjectMapper objectMapper;
    private final UrlPolicy urlPolicy;

    public ManifestParser(ObjectMapper objectMapper, UrlPolicy urlPolicy) {
        this.objectMapper = objectMapper;
        this.urlPolicy = urlPolicy;
    }

    public ModuleManifest parse(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new InvalidManifestException("module.json не разобран как JSON");
        }
        if (root == null || !root.isObject()) {
            throw new InvalidManifestException("module.json должен быть JSON-объектом");
        }

        String id = requiredString(root, "id", 64);
        if (!MODULE_ID.matcher(id).matches()) {
            throw new InvalidManifestException(
                    "id: только строчные латинские буквы, цифры, «-» и «_», до 64 символов");
        }

        // `title` — алиас `name`, как в манифестах систем.
        String name = optionalString(root, "name", 150);
        if (name == null) {
            name = optionalString(root, "title", 150);
        }
        if (name == null) {
            throw new InvalidManifestException("name: обязательное поле");
        }

        String version = requiredString(root, "version", 50);
        if (!VERSION.matcher(version).matches()) {
            throw new InvalidManifestException("version: нужна семантическая версия, например 1.0.0");
        }

        String description = requiredString(root, "description", 1000);

        String download = requiredString(root, "download", 2048);
        urlPolicy.require(download, "download");

        return new ModuleManifest(
                id,
                name,
                version,
                description,
                optionalString(root, "author", 150),
                optionalString(root, "icon", 100),
                download,
                compatibleSystems(root),
                json
        );
    }

    private static List<String> compatibleSystems(JsonNode root) {
        JsonNode node = root.get("compatibleSystems");
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new InvalidManifestException("compatibleSystems: нужен массив строк");
        }

        List<String> systems = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isString()) {
                throw new InvalidManifestException("compatibleSystems: нужен массив строк");
            }
            String system = item.stringValue().strip();
            // `*` и пустой массив значат одно и то же — «все системы».
            if (system.equals("*")) {
                return List.of();
            }
            if (system.length() > MAX_SYSTEM_ID_LENGTH) {
                throw new InvalidManifestException(
                        "compatibleSystems: id системы длиннее " + MAX_SYSTEM_ID_LENGTH + " символов");
            }
            if (!system.isEmpty() && !systems.contains(system)) {
                systems.add(system);
            }
        }
        if (systems.size() > MAX_SYSTEMS) {
            throw new InvalidManifestException("compatibleSystems: не больше " + MAX_SYSTEMS + " систем");
        }
        return List.copyOf(systems);
    }

    private static String requiredString(JsonNode root, String field, int maxLength) {
        String value = optionalString(root, field, maxLength);
        if (value == null) {
            throw new InvalidManifestException(field + ": обязательное поле");
        }
        return value;
    }

    private static String optionalString(JsonNode root, String field, int maxLength) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isString()) {
            throw new InvalidManifestException(field + ": нужна строка");
        }

        String value = node.stringValue().strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new InvalidManifestException(field + ": длиннее " + maxLength + " символов");
        }
        return value;
    }
}
